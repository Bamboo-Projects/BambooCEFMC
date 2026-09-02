# Patches

## Version 1 — gegen upstream java-cef auf dem Pin

Angewendet von `build-jcef.ps1`, in dieser Reihenfolge, jeder mit
`git apply --check` vorab. Schlägt einer fehl, endet der Bau sofort, statt
halb gepatchten Code zu übersetzen.

| Datei | Zeilen | Zweck |
|---|---|---|
| `0001-cefbrowser-n-public.patch` | 1 | `CefBrowser_N` öffentlich, damit ein eigener Browser davon erben kann |
| `0002-send-key-event-raw.patch` | 189 | `sendKeyEventRaw`: Tastenereignisse aus Werten statt aus einem `KeyEvent` |
| `0003-clang-format-nicht-fatal.patch` | 6 | Der Bau bricht nicht mehr ab, wenn `clang-format` nicht geladen werden kann |
| `0004-cef-im-eigenen-thread.patch` | 63 | `useCallingThread()`: CEF arbeitet im aufrufenden Thread statt in AWTs Ereignisthread |
| `0005-bibliotheken-aus-genanntem-ordner.patch` | 26 | `jcef.library.path`: die nativen Bibliotheken aus einem zur Laufzeit genannten Ordner laden |

Jeder Patch trägt seine Begründung im Kopf, und jeder sagt dort, was ihn
überflüssig machen würde. Erzeugt werden sie mit `git format-patch` gegen den
Pin aus `pin.properties`.

---

## Drei Abweichungen vom Plan, alle beim Nachsehen im Quelltext entstanden

### 1. Der Sichtbarkeits-Patch trifft `CefBrowser_N`, nicht `CefBrowserOsr`

Geplant war, `CefBrowserOsr` samt Konstruktor öffentlich zu machen — zwei
Zeilen. Das geht, nützt aber nichts.

**Upstreams `CefBrowserOsr` ist nicht dieselbe Klasse wie die des
CinemaMod-Forks.** Der Fork hat sie auf 180 Zeilen zusammengestrichen und JOGL
vollständig entfernt. Upstream hat 669 Zeilen, und der Konstruktor ruft
`createGLCanvas()`:

```java
private void createGLCanvas() {
    GLProfile glprofile = GLProfile.getMaxFixedFunc(true);
    canvas_ = new GLCanvas(new GLCapabilities(glprofile)) { ... };
    ...
}
```

Damit hängt an jeder Erzeugung eine AWT-`GLCanvas` mit eigenem GL-Kontext über
JOGL. In einem Prozess, der seinen eigenen GL-Kontext führt und JOGL nicht
mitliefert, ist das kein gangbarer Weg. Dazu kommt: `browser_rect_` ist dort
privat, und `onPaint` malt in den GL-Kontext, statt nur zu melden.

Drei Wege, einer davon gewählt:

| Weg | Urteil |
|---|---|
| `CefBrowserOsr` öffentlich machen | **verworfen.** Zwei Zeilen, die eine Nutzbarkeit vorspiegeln, die die Klasse nicht hat. |
| `CefBrowserOsr` von JOGL befreien | **verworfen.** Kein Patch mehr, sondern ein Ersatz für fünfhundert Zeilen fremden Code — und er kollidiert bei jedem Anheben des Pins. |
| **`CefBrowser_N` öffnen** | **gewählt.** Eine Zeile. Die Klasse bleibt abstrakt, ihr Konstruktor protected; neu erreichbar sind allein die protected-Mitglieder, und genau die brauchen wir. |

Nachgesehen und belegt: Alles, was der Fork in seinem `CefBrowserOsr` tut,
lässt sich von außerhalb des Pakets nachbauen. `getClient()`,
`getRequestContext()`, `getNativeRef()`, `setFocus()` und
`CefClient.onAfterParentChanged()` sind öffentlich; `createBrowser`,
`createDevTools`, `getUrl`, `getParentBrowser`, `getInspectAt`, `wasResized`,
`sendKeyEvent`, `sendMouseEvent`, `sendMouseWheelEvent` sind protected.

**Was das für `FnBrowser` bedeutet:** Er erbt künftig von `CefBrowser_N` und
setzt `CefRenderHandler` selbst um — rund 150 Zeilen, die heute in fremdem
Code stehen und dann in unserem. Der Preis eines kleinen Patch-Satzes.

### 2. `clang-format` scheitert auch unter Python 3.12

Der Plan nagelt Python 3.12 fest mit der Begründung, `gsutil` sterbe erst
unter 3.13 und neuer. Gemessen: Es scheitert unter 3.12 genauso, nur an einer
anderen Stelle — `ModuleNotFoundError: No module named 'six.moves'`.

Patch 0003 ist damit keine Vorsichtsmaßnahme, sondern die Voraussetzung dafür,
dass überhaupt gebaut werden kann. Ein „nackter" Lauf ohne Patches ist auf
diesem Rechner nicht möglich.

Die Festlegung auf 3.12 bleibt trotzdem: Ein Bauwerkzeug, das die
Python-Fassung des Rechners nimmt, tut auf zwei Rechnern zwei verschiedene
Dinge. Nur die Begründung im Plan stimmt nicht mehr.

### 3. Ein vierter Patch kam dazu: CEF muss in unseren Thread

Geplant waren drei. Der vierte ist keine Bequemlichkeit, sondern die
Voraussetzung dafür, dass der Renderpfad überhaupt trägt.

`CefApp` führt alles, was CEFs Hauptthread gehört, in AWTs Ereignisthread aus:
`N_PreInitialize`, `N_Initialize`, `N_Shutdown` und jede Runde der
Nachrichtenschleife. An die Schleife hängt sie zusätzlich einen Swing-Timer:

```java
final long kMaxTimerDelay = 1000 / 30; // 30fps
```

Für eine AWT-Anwendung ist das richtig. Für uns sind es zwei Fehler:

- **Der Thread.** `onPaint` kommt dort an, wo gepumpt wird. Gemessen, bevor der
  Patch da war: 451 von 451 Bildern im AWT-Thread statt im Renderthread. Die
  Freigabe der Textur hängt daran, dass beides derselbe Thread ist.
- **Der Takt.** Wer sechzig Bilder einstellt und über einen Timer mit dreißig
  Runden pumpt, bekommt dreißig — ohne Fehlermeldung.

Der CinemaMod-Fork hat dasselbe gelöst, nur radikaler: Dort ist
`doMessageLoopWork` ersatzlos ein Nichtstun und `N_DoMessageLoopWork` öffentlich.
Unser Patch lässt upstreams Verhalten stehen und legt einen Schalter daneben.

**Die Falle darin, einmal voll hineingetreten:** Der erste Entwurf ließ
`doMessageLoopWork(delay)` sofort pumpen. Das stirbt — und zwar an einer
Meldung, die woandershin zeigt:

```text
Internal Error (os_windows_x86.cpp:144)
guarantee(result == EXCEPTION_CONTINUE_EXECUTION) failed:
Unexpected result from topLevelExceptionFilter
```

Die Ursache steht in `CefAppHandlerAdapter`:

```java
public void onScheduleMessagePumpWork(long delay_ms) {
    CefApp.getInstance().doMessageLoopWork(delay_ms);
}
```

**CEF ruft die Methode selbst zurück — die erste Bitte kommt mitten aus
`CefInitialize` heraus.** Wer sie annimmt, verschachtelt
`CefDoMessageLoopWork` in sich selbst, und der Prozess endet.

Deshalb: Mit eigenem Thread ist `doMessageLoopWork(delay)` ein Nichtstun, und
gepumpt wird über das neue `doMessageLoopWorkNow()`. Wer seinen Takt selbst
hat, braucht CEFs Bitte nicht.

---

## Die eine Falle in Patch 0002, die kein Compiler meldet

Eine neue JNI-Funktion braucht **zwei** Stellen im nativen Teil, nicht eine:
die Definition in `CefBrowser_N.cpp` **und** die Deklaration in
`CefBrowser_N.h`.

Der Grund steht in Zeile 8 des Headers:

```c
extern "C" {
```

Nur was dort deklariert ist, bekommt C-Bindung. Eine Funktion, die allein in
der `.cpp` steht, wird als C++ übersetzt und trägt einen verzierten Namen —
der Bau läuft durch, die Bibliothek entsteht, und selbst ein `grep` nach dem
Namen findet ihn, weil er als Teilzeichenkette in der Verzierung steckt.
Erst zur Laufzeit sagt die JVM:

```text
java.lang.UnsatisfiedLinkError:
'void org.cef.browser.CefBrowser_N.N_SendKeyEventRaw(int, int, char, int, boolean, int)'
```

Der Prüfstand aus A4b hat es beim ersten Lauf gefunden. Das ist genau der
Grund, warum er vor den Übersetzungstabellen steht.

---

## `poc/` — die Patches des Proof-of-Concept

`0000-poc-*` sind die Patches des **Proof-of-Concept** gegen den
CinemaMod-Fork von java-cef mit CEF 116. Sie gehören nicht in Version 1 und
sind hier gesichert, damit der gemessene Zustand reproduzierbar bleibt.

| Datei | Zweck |
|---|---|
| `poc/0000-poc-60hz-cef116.patch` | `setWindowlessFrameRate` nachgerüstet und die Bildrate über die Umgebungsvariable `JCEF_WINDOWLESS_FRAME_RATE` einstellbar gemacht. Damit wurde der A/B-Vergleich 30 gegen 60 Hz gefahren. |
| `poc/0000-poc-clang-format-nicht-fatal.patch` | Vorläufer von `0003`, gegen den Fork. |

Nichts davon wird übernommen. Version 1 baut gegen upstream java-cef auf
dessen eigenem Pin (CEF 146), und dort gibt es `CefBrowserSettings` mit
`windowless_frame_rate` bereits. Der Umgebungsvariablen-Behelf verschwindet
ersatzlos.
