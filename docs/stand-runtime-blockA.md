# Block A ist gebaut und vermessen, der Eingabe-Adapter steht

> **Weitergegangen seit dem 1. September 2026.** B4 und B3e stehen: Der Mod
> läuft im Spiel auf der eigenen Laufzeitumgebung. Der aktuelle Bericht ist
> **`stand-runtime-b4.md`**; hier stehen weiterhin Block A und der Adapter.

Stand: 1. September 2026. Was in `plan-v1-blockA-und-input.md` als Plan steht,
läuft jetzt: A1, A2, A3, A4a, B3a, A4b, B3b, B3c und B3d sind grün. Dieses
Dokument ist der Bericht dazu — die Zahlen, die Abweichungen vom Plan und das,
was daraus für B4 folgt.

---

## In einem Satz

Die eigene Laufzeitumgebung baut reproduzierbar aus upstream java-cef auf
CEF 146, liefert sechzig Bilder je Sekunde im eigenen Renderthread, räumt ohne
Waisenprozesse ab, und der Tastatur-Prüfstand belegt für jede Referenztaste,
was Chromium wirklich empfängt.

---

## Die Zahlen

Gemessen am 1. September 2026, `tools/runtime/probe/`, gegen die von der
Pipeline gebauten Artefakte.

### A4a — Takt und Lebenslauf, 20 s bei 1920×1080

| Größe | Gemessen | Sollwert | |
|---|---|---|---|
| `onPaint`-Abstand p50 | **16,57 ms** (60,4/s) | 16,7 ± 1 | erfüllt |
| `onPaint`-Abstand p95 | 19,08 ms | — | |
| `onPaint`-Abstand p99 | 20,49 ms | — | |
| Pumpe p50 | 1,73 ms | — | |
| `onPaint` im Pumpthread | **immer** | immer | erfüllt |
| `jcef_helper` nach `dispose()` | **0** | 0 | erfüllt |

Die letzte Zeile ist die, die im Proof-of-Concept **acht** war.

### A4b — Tastatur und Rad, 40 Fälle

**40 grün.** Einer davon ist eine Gegenprobe: Sie erwartet, dass ein Zeichen
mit Strg+Alt *nicht* ankommt — käme es durch, wäre die Gegenmaßnahme in
`AwtModifiers` überflüssig, und der Fall würde es sagen.

```text
Pfeile         ArrowUp/Down/Left/Right     38 / 40 / 37 / 39
Ziffernblock   Numpad8 / Numpad2 / Numpad0 104 / 98 / 96
Navigation     Home 36  End 35  PageUp 33  PageDown 34
               Insert 45  Delete 46
Eingabe        Enter 13   NumpadEnter 13
Modifikatoren  Strg 17   Alt 18   Umschalt 16   (links wie rechts)
Steuerung      Rücktaste 8  Esc 27  Tab 9  F1 112  F12 123
Kürzel         Strg+A/C/V/S/F  keydown mit ctrlKey, kein keypress
Text           abcXYZ012  zeichengenau, keine Dopplungen
Sonderzeichen  äöüß@€\|~  zeichengenau
```

**Das Erweiterungs-Bit trägt.** Belegt an einem Paar, dessen beide Bedeutungen
weit auseinanderliegen und nicht vom Tastaturlayout abhängen:

```text
Scancode 0x37 mit Erweiterungs-Bit   → 44   VK_SNAPSHOT  (Druck)
Scancode 0x37 ohne Erweiterungs-Bit  → 106  VK_MULTIPLY  (Ziffernblock-Stern)
Scancode 0x35 mit Erweiterungs-Bit   → 111  VK_DIVIDE
Scancode 0x45 ohne Erweiterungs-Bit  → 144  VK_NUMLOCK
```

---

## Vier Abweichungen vom Plan

Alle vier entstanden beim Nachsehen im Quelltext oder beim Messen; keine ist
eine Bequemlichkeit. Ausführlich stehen sie in
`tools/runtime/patches/LIESMICH.md`.

### 1. Der Sichtbarkeits-Patch trifft `CefBrowser_N`, nicht `CefBrowserOsr`

Upstreams `CefBrowserOsr` ist nicht die zusammengestrichene Klasse des
CinemaMod-Forks. Sie hat 669 statt 180 Zeilen, und ihr Konstruktor baut eine
AWT-`GLCanvas` über JOGL auf. Sie öffentlich zu machen wären zwei Zeilen, die
eine Nutzbarkeit vorspiegeln, die die Klasse nicht hat.

Eine Ebene tiefer liegt alles Nötige: `CefBrowser_N` öffnen ist **eine Zeile**,
und alles, was der Fork in seinem `CefBrowserOsr` tut, lässt sich von außerhalb
des Pakets nachbauen — belegt und im LIESMICH aufgeführt.

**Folge für B4:** `FnBrowser` erbt künftig von `CefBrowser_N` und setzt
`CefRenderHandler` selbst um. Die Vorlage steht schon:
`tools/runtime/probe/OsrBrowser.java`, rund 220 Zeilen, im Prüfstand gelaufen.

### 2. Ein vierter Patch: CEF muss in unseren Thread

`CefApp` führt Initialisierung, Nachrichtenschleife und Herunterfahren in AWTs
Ereignisthread aus und hängt an die Schleife einen Swing-Timer mit dreißig
Runden je Sekunde. Gemessen ohne den Patch: **alle 451 Bilder im falschen
Thread**. Der Renderpfad und die Freigabe der Textur hängen daran, dass
`onPaint` dort ankommt, wo gepumpt wird.

`useCallingThread()` schaltet das um. Ohne den Schalter bleibt upstreams
Verhalten unverändert.

**Die Falle darin, einmal voll hineingetreten:** Der erste Entwurf ließ
`doMessageLoopWork(delay)` sofort pumpen. CEF ruft diese Methode aber selbst
zurück — die erste Bitte kommt mitten aus `CefInitialize`. Wer sie annimmt,
verschachtelt `CefDoMessageLoopWork` in sich selbst, und der Prozess endet an
einer Meldung über einen Ausnahmefilter, die nichts mit der Ursache zu tun hat.
Jetzt ist `doMessageLoopWork` mit eigenem Thread ein Nichtstun, und gepumpt
wird über `doMessageLoopWorkNow()`.

### 3. `clang-format` scheitert auch unter Python 3.12

Der Plan begründet den Pin auf 3.12 damit, `gsutil` sterbe erst unter 3.13.
Gemessen: Es scheitert unter 3.12 genauso, nur an anderer Stelle
(`No module named 'six.moves'`). Patch 0003 ist damit **Voraussetzung**, nicht
Vorsichtsmaßnahme — ein nackter Bau ohne Patches ist nicht möglich.

Der Pin auf 3.12 bleibt trotzdem: Ein Bauwerkzeug, das die Python-Fassung des
Rechners nimmt, tut auf zwei Rechnern zwei verschiedene Dinge.

### 4. Der Rückweg aus der Prüfseite ist die Konsole, nicht CDP

Geplant war, das Protokoll über den Debug-Port auszulesen.
`CefDisplayHandler.onConsoleMessage` kann dasselbe, kostet zehn Zeilen statt
hundert und liefert den Text **im Pumpthread**. CDP hätte einen offenen Port,
einen WebSocket und einen zweiten Thread gebraucht, der blockierend liest — und
der dürfte der Pumpthread nicht sein, sonst misst der Prüfstand Stillstand.

Der Debug-Port bleibt für das, wofür er gedacht war: Zusehen von außen.

---

## Drei Funde, die Block B betreffen

### `code` ist über diesen Weg immer leer

Für **jede** Taste meldet die Seite `code: ""` und `location: 0`. Das ist keine
Eigenart unseres Patches: CEFs eigener Beispielcode füllt `native_key_code`
genauso (`osr_window_win.cc`: `event.native_key_code = lParam`), und Chromium
leitet daraus keinen physischen Tastencode ab, wenn das Ereignis nicht aus
einer echten Fensternachricht stammt.

**Folgen, ehrlich benannt:**

- Für den Editor folgenlos. Monaco unterscheidet Pfeil-hoch von der Acht des
  Ziffernblocks über `keyCode` (38 gegen 104) — und der kommt korrekt an.
- **Rechtes Strg ist von linkem Strg in der Seite nicht unterscheidbar**, ebenso
  wenig Ziffernblock-Eingabe von Eingabe. Beide tragen denselben `keyCode`, und
  `code` ist leer. Für Tastenkürzel ist das folgenlos; wer eines Tages die
  Seiten unterscheiden will, braucht dafür einen anderen Weg.
- Der Prüfstand urteilt deshalb über `keyCode` und schreibt `code` und
  `location` mit. Was heute leer ist, soll auffallen, wenn es das eines Tages
  nicht mehr ist.

### AltGr: das Zeichen wird von Strg+Alt verschluckt

Gemessen, beide Richtungen:

```text
KEY_TYPED '@' mit Strg+Alt am Ereignis   → nichts kommt an
KEY_TYPED '@' ohne Modifikatoren         → '@' steht im Feld
```

Damit ist die Gegenmaßnahme für **B3c** keine Vermutung mehr: Am `KEY_TYPED`
gehören die Modifikatoren weg. Das `KEY_PRESSED` darf sie behalten — die
Kürzelprüfung (Strg+A/C/V/S/F) läuft mit ihnen korrekt.

### `getUIComponent()` darf nicht `null` liefern

`CefClient.onTakeFocus` ruft darauf `getParent()`, ohne zu prüfen. Der erste
Tabulator im Prüfstand endete in einer `NullPointerException`. Eine peerlose
Unterklasse von `Component` genügt — sie zieht kein Toolkit heran, und
`getParent()` ist null, worauf die Fokuswanderung still endet.

Dieselbe Komponente kann später die Absenderrolle für die Maus übernehmen
(`AwtEventSource` aus B3d). Ein Patch ist dafür nicht nötig.

---

## Der Zustand der Maschine

```text
C:\fnjcef\src       Quellbaum, wird von build-jcef.ps1 verworfen und neu geholt
C:\fnjcef\cache     die CEF-Distribution als Archiv (338 MB), SHA-1-geprüft
```

`build-jcef.ps1 -Clean` löscht den Quellbaum und behält den Cache;
`-Purge` löscht beides. **Der Cache ist keine Abkürzung:** CMake prüft das
Archiv über seine SHA-1, es ist also derselbe Eingang wie ein frischer
Download — nur ohne die 338 MB.

Werkzeuge auf diesem Rechner:

```text
Visual Studio 18 2026 Community    Generator wird erkannt, nicht fest verdrahtet
Python 3.12.10 (Program Files)     über PYTHON_EXECUTABLE gesetzt
CMake 4.2.3, JDK 21.0.12.1
```

Der Generator wird über `vswhere` bestimmt. Der Plan nennt „Visual Studio 17
2022"; auf diesem Rechner gibt es nur 18. Das Skript kennt 16, 17 und 18.

---

## Was offen bleibt

- **Der Zweitrechner.** Reproduzierbar heißt bisher: zweimal auf **diesem**
  Rechner, gleiche Artefaktliste. Bitgleichheit ist nicht das Ziel für
  Version 1 (`jcef.dll` trägt Zeitstempel und Baupfad); ein zweiter Rechner
  wäre trotzdem der ehrlichere Beleg.
- **Linux und macOS.** `files-windows.txt` ist als eigene Datei angelegt, damit
  ein neuer Zweig nur eine Liste mitbringt. Der Windows-Zweig von
  `sendKeyEventRaw` ist ausgearbeitet, der für Linux und macOS bewusst dünn.
- **Die `url` im Manifest** steht ohne `-BaseUrl` nur als Dateiname. Sie wird
  gefüllt, wenn es einen Ort gibt, an dem die Laufzeitumgebung liegt.

---

## B3b, B3c und B3d sind auch schon da

Fünf Klassen unter `web/input`, dreizehn Prüfläufe, alle grün. Jede Erwartung
darin stammt aus einer der beiden Messungen und nicht aus einer Ableitung.

```text
GlfwScancodes    base() und extended(), belegt durch ScanProbe
GlfwKeys         Rechnung nur bei Buchstaben und Ziffern, sonst Tabelle
AwtModifiers     forKey() vollständig, forCharacter() ohne Strg und Alt
AwtMouseEvents   rechts = BUTTON3, kein eigenes MOUSE_CLICKED
AwtEventSource   die eine peerlose Komponente
```

**Das Rad-Vorzeichen ist gemessen, und es fällt anders aus als vermutet.** Der
Plan rechnete mit einem Vorzeichenwechsel zwischen GLFW und AWT. Gemessen im
Prüfstand:

```text
wheelRotation +1  →  deltaY -2,0   in der Seite: hinauf
wheelRotation -1  →  deltaY +2,0   in der Seite: hinunter
```

Minecrafts Delta ist beim Drehen nach oben positiv. Es wandert deshalb
**unverändert** in `wheelRotation` — zwei Vorzeichenwechsel, die sich
aufheben. Ohne die Messung wäre hier ein Dreher eingebaut worden, und Scrollen
liefe verkehrt herum.

**Eine Annahme, die der Entscheidung des Lesers bedarf:** Der Ziffernblock ist
auf die NumLock-an-Lesart festgelegt — Scancode 0x48 ohne Erweiterungs-Bit
wird `VK_NUMPAD8`, nicht `VK_UP`. Ohne das hätte das Erweiterungs-Bit für diese
Tasten keine Bedeutung mehr. GLFW liefert seit 3.3 aber `GLFW_MOD_NUM_LOCK` in
den Modifikator-Bits; wer es genauer will, könnte je Zustand entscheiden. Für
Version 1 bleibt es bei der einfachen Lesart.

---

## Womit weitermachen

```text
B4   FnBrowser auf CefBrowser_N umstellen — Vorlage: probe/OsrBrowser.java
B3e  Testmatrix, automatisch und von Hand in Monaco
```

**B4 braucht vorher eine Entscheidung, die der Plan nicht trifft:** Wie kommt
die gebaute Laufzeitumgebung auf den Klassenpfad des Mods? Heute liefert MCEF
`jcef.jar` und die Bibliotheken. Drei Wege stehen offen — mitliefern, über das
Manifest nachladen, oder MCEFs Ladeweg übernehmen und nur den Inhalt
austauschen —, und sie unterscheiden sich in Auslieferungsgröße,
Startverhalten und darin, wie schwer ein Rückweg ist.

MCEF wird erst entfernt, wenn B4 und B3e grün sind.
