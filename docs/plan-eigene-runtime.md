# Migrationsplan — eine eigene Web-Laufzeitumgebung

Stand: 31. August 2026. Planung, kein Code. Die Reihenfolge ist aus dem
vorhandenen Quelltext abgeleitet, nicht aus einer Wunschliste.

---

## Die Antwort auf die Abschlussfrage, vorweg

> Soll unsere langfristige Web-Laufzeitumgebung auf upstream JCEF mit einem
> kleinen eigenen Fork beruhen, oder ist eine andere native Architektur
> sinnvoller?

**Upstream java-cef mit einem kleinen eigenen Aufsatz — die Vermutung trifft
zu.** Die Begründung ist messbar und nicht Geschmackssache:

Alles, was uns heute fehlt, fehlt **in der Java-Bindung**, nie in CEF. Am
Kopfdateisatz von CEF 116 nachgesehen — der Stand, den wir bereits auf der
Platte haben:

```text
ImeSetComposition, ImeCommitText,
ImeFinishComposingText, ImeCancelComposition    cef_browser.h  767–803
cef_composition_underline_t (mit style)         cef_types.h   3019–3044
SendExternalBeginFrame()                        cef_browser.h  660–663
OnAcceleratedPaint(browser, type, rects, handle) cef_render_handler.h 161
```

**Vier Fähigkeiten, alle vorhanden, keine davon gebunden.** Eine eigene native
Schicht müsste nicht diese vier Dinge bauen — sie müsste zuerst alles
nachbauen, was java-cef bereits kann: Nachrichtenschleife, Lebenszyklus,
Rückrufe, Frames, Requests, Schemes, Kontexte. Das sind die 2.600 Zeilen des
CinemaMod-Forks als Untergrenze, und wir hätten sie ohne einen einzigen
Vorteil gegenüber dem Original.

Der Gegenbeweis liegt in dieser Sitzung: Der 60-Hz-Patch waren **44 Zeilen**
für eine Fähigkeit, die upstream längst hat. Bei einem Wechsel auf aktuelles
java-cef entfällt er ersatzlos.

---

# 1 — Lifecycle

Abgeschlossen, siehe `stand-lifecycle.md`. Kurzfassung: Kein Leck beim
Schließen, Aufräumen in unter einer Sekunde, kein Wachstum über drei Zyklen.
Das frühere Wachstum kam vom Navigieren.

**Folge für den Entwurf:** Die Oberfläche wird eine Anwendung, die geladen
bleibt und ihren Inhalt austauscht — kein `location.href` zwischen internen
Ansichten. Das ist damit eine Vorgabe für den Oberflächenteil, nicht für die
Laufzeitumgebung.

# 2 — Cleanup-Vertrag

Abgeschlossen, siehe `stand-lifecycle.md`. Der vorhandene Zustandsautomat
reicht; was fehlt, ist eine Bestätigung, dass `onBeforeClose` kam. Ein
`CefLifeSpanHandler` in der neuen Laufzeitumgebung liefert sie.

Ein Punkt wandert von dort in die Anforderungsliste: **Hilfsprozesse müssen
den Absturz des Elternprozesses nicht überleben.** Beim harten Abbruch blieben
acht Chromium-Prozesse stehen. Unter Windows löst das ein Job-Objekt mit
`JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`.

# 3 und 4 — Zielstruktur

Die vorgeschlagene Gliederung passt zu dem, was schon steht. Wichtig ist die
Richtung der Abhängigkeit: Die Laufzeitumgebung kennt weder Monaco noch die
Technik-Mod noch den Sprachdienst.

```text
Minecraft / NeoForge
└── fn-web-runtime                    (eigenes Modul, kennt nichts darüber)
    ├── bootstrap/      Manifest, Download, Prüfsumme, Entpacken, Laden
    ├── jcef/           gepinnter upstream-Checkout + unsere .patch-Dateien
    ├── binaries/       CEF je Plattform, versioniert abgelegt
    ├── browser/        BrowserManager, Sitzungen, Lebenszyklus
    ├── osr/            onPaint, Texturen, Popups
    ├── input/          Maus, Tastatur, Fokus, später IME
    ├── scheme/         Ressourcenschemata
    ├── ipc/            Brücke zwischen Seite und Java
    └── texture/        GL-Rückseite
```

**Vier dieser neun Bausteine gibt es bereits** und sie hängen nicht an MCEF:
`osr/`, `input/`, `texture/`, `scheme/` entsprechen unserem heutigen
`web/`-Paket.

# 5 — Welche CEF-Fassung

An der Quelle abgelesen (`cef-builds.spotifycdn.com/index.json`, 9,8 MB), nicht
aus dritter Hand:

```text
stable   CEF 151.3.24+g2384915   Chromium 151.0.7922.174
beta     CEF 152.0.5+gb129680    Chromium 152.0.7977.54
```

**Für alle vier Plattformen identisch verfügbar:** windows64, linux64,
macosarm64, macosx64.

**Korrektur zum vorigen Bericht:** Dort stand „CEF 143, Dezember 2025, 27
Hauptfassungen Abstand". Diese Zahl stammte aus einer Enzyklopädie und war
acht Monate alt. Richtig ist: **151 gegen unsere 116 — fünfunddreißig
Chromium-Hauptfassungen.**

**Aber upstream java-cef pinnt seine eigene Fassung**, und die ist nicht 151:

```text
java-cef master, CMakeLists.txt:
  set(CEF_VERSION "146.0.10+g8219561+chromium-146.0.7680.179")
```

Das verschiebt die Empfehlung. **Fassung 1 baut auf dem Pin, den upstream
mitbringt — CEF 146 / Chromium 146.** Gründe: Es ist die Fassung, gegen die
upstream tatsächlich baut und testet; sie bringt uns von Chromium 116 auf 146,
also **dreißig Hauptfassungen** und damit den ganz überwiegenden Teil des
Gewinns; und sie kostet keine Zeile eigenen Code.

Die Anhebung auf 151 ist danach eine Zeile in `CMakeLists.txt` — aber eben
unsere Zeile, mit unserem Risiko: Zwischen 146 und 151 können sich
CEF-Schnittstellen geändert haben, die java-cef bindet. Das ist ein eigener,
später Schritt mit eigenem Testlauf, kein Teil von Fassung 1.

Für später gilt: **die jeweils aktuelle stable-Fassung pinnen**, nicht die Beta
und nicht einen Commit. Ein Grund kommt hinzu, der in die Versionsstrategie
gehört: Chromium wechselt **ab September 2026 auf einen Zwei-Wochen-Takt**;
CEF stellt seine stabilen Bauten deshalb auf den erweiterten Kanal um, dessen
Meilenstein alle acht Wochen wechselt. Für uns ist das die richtige Kadenz —
ein Runtime-Update alle acht Wochen ist tragbar, alle zwei wären es nicht.

Kriterien im Einzelnen:

| | Stand bei CEF 146 und 151 gleichermaßen |
|---|---|
| Windows/Linux x86_64 | vorhanden |
| macOS (arm64 und x64) | vorhanden |
| Sicherheitsstand | aktuell |
| OSR | stabil, unser Hauptpfad |
| `SetWindowlessFrameRate` | vorhanden (in CEF, siehe Punkt 6) |
| `CefBrowserSettings` | vorhanden |
| DevTools | vorhanden, in dieser Sitzung genutzt |
| Ressourcen-Handler | vorhanden, nutzen wir für `mc://` |
| transparente Browser | vorhanden, nutzen wir |

**Lizenz und Codecs:** Die Spotify-Bauten kommen ohne proprietäre Codecs
(H.264, AAC). Für einen Editor ist das ohne Bedeutung; für eingebettete
Videos wäre es eins. Wer die braucht, muss CEF selbst bauen — ein anderes
Vorhaben.

# 6 — JCEF-Strategie

Geprüft wurden alle drei Wege.

**A — upstream direkt.** Deckt heute schon ab, was uns diese Sitzung gekostet
hat: `CefBrowserSettings` (upstream vorhanden, mit genau einem Feld:
`windowless_frame_rate`, Bereich 1–60) und `CefBrowser.setWindowlessFrameRate(int)`
samt `getWindowlessFrameRate()`. Es fehlen IME und Accelerated Paint.

**B — upstream plus kleiner eigener Aufsatz.** Wie A, zusätzlich unsere
Patches als Dateien über einem gepinnten Checkout.

**C — eigene dünne JNI-Schicht.** Müsste Nachrichtenschleife, Lebenszyklus und
sämtliche Rückrufe neu bauen. Der CinemaMod-Fork zeigt die Untergrenze: 2.600
Zeilen, und er ist bereits eine Verdünnung.

**Empfehlung: B.**

### Wie viel eigener JCEF-Code realistisch

| | Umfang | Vorlage |
|---|---|---|
| Windowless frame rate | **0 Zeilen** | upstream hat es |
| `CefBrowserSettings` | **0 Zeilen** | upstream hat es |
| Sichtbarkeit `CefBrowserOsr` | **2 Zeilen** | Vorlage: der CinemaMod-Fork |
| Eingabe GLFW→AWT (unser Code) | ~150–250 Zeilen | betrifft uns, nicht JCEF |
| IME-Bindung | ~150–250 Zeilen | keine; Eigenarbeit (Punkt 12) |
| Accelerated Paint | ~400–600 Zeilen | PR #524 übernehmen (Punkt 11) |
| External Begin Frame | ~40 Zeilen | Muster wie unser 60-Hz-Patch |
| Minecraft-Eigenheiten | vermutlich 0 | betrifft unsere Seite, nicht JCEF |

**Für Fassung 1: ein Sichtbarkeits-Patch von zwei Zeilen.**

> Nachtrag vom selben Tag: Hier stand „null Zeilen eigener JCEF-Code". Das war
> falsch, und der Umsetzungsplan `plan-runtime-v1.md` führt es aus. Upstream
> hält `CefBrowserOsr` **paketprivat**; der CinemaMod-Fork hat sie öffentlich
> gemacht, und unser `FnBrowser` erbt davon. Erben ist unumgänglich, weil
> `sendKeyEvent` und die beiden Mausmethoden auf `CefBrowser_N`
> `protected final` sind und upstream keine öffentliche Alternative bietet.
>
> Zweiter Fund: Upstream nutzt für Eingaben die **AWT-Typen**
> (`java.awt.event.KeyEvent`), der Fork eigene (`CefKeyEvent`). Unser
> Eingabe-Übersetzer muss deshalb neu geschrieben werden — geschätzt 150 bis
> 250 Zeilen, und der dokumentierte Sonderfall des Forks gilt dort nicht.
> Der Renderpfad zieht dagegen unverändert um: `onPaint`, `onPopupShow` und
> `onPopupSize` sind in beiden Fassungen signaturgleich.

Wollen wir statt 146 die Fassung 151, ist die Pin-Anhebung ein weiterer
eigener Patch: eine Zeile, aber mit dem Risiko geänderter Schnittstellen
zwischen den beiden Fassungen. Der eigene Anteil beginnt erst bei IME und Accelerated
Paint, und beides ist ausdrücklich Phase 2 und 3.

Der JetBrains-Fork (`JetBrains/jcef`, aktiv, 1.235 Commits im
Entwicklungszweig) fährt OSR produktiv in einer IDE und wäre die nächste
Vorlage, falls dort IME-Arbeit liegt. Das ist vor Phase 3 zu prüfen, nicht
jetzt.

# 7 — Patch-Strategie

Der heutige Weg über `JCEF_WINDOWLESS_FRAME_RATE` war für den A/B-Test
richtig — eine Bibliothek, beide Seiten des Vergleichs, keine Java-Änderung.
**Als Dauerlösung ist er falsch**, aus zwei Gründen: Er ist beim Erzeugen des
Browsers festgelegt, und er hängt an der Prozessumgebung, die jeder Aufrufer
anders setzt.

In der eigenen Laufzeitumgebung wird daraus eine Methode auf der Sitzung. Die
Form steht noch nicht fest; der Zweck schon:

```text
aktive Oberfläche    60
Dashboard            30
im Hintergrund       10
nicht sichtbar       gar nicht
```

Ein Hinweis zur letzten Zeile: **„nicht sichtbar" ist keine Bildrate.** CEF
kennt dafür `CefBrowserHost::WasHidden(true)` — dann malt der Browser
überhaupt nicht, statt einmal je Sekunde. Das ist der richtige Zustand für
eine Oberfläche, die außer Sicht ist, und er spart mehr als jede niedrige
Rate.

Unser vorhandener `FramePacer` deckelt bereits nach Sichtbarkeit; er wird von
einer Obergrenze, die nur bremsen kann, zu einer echten Vorgabe, sobald die
Bildrate stellbar ist.

# 8 — Über 60 Hz

Geprüft, mit klarem Ergebnis:

- **Die 60er-Grenze besteht weiterhin.** `windowless_frame_rate` nimmt 1 bis
  60, Voreinstellung 30. Das gilt für `CefBrowserSettings` beim Erzeugen wie
  für `SetWindowlessFrameRate` zur Laufzeit.
- **Darüber geht es nur über External Begin Frame.**
  `CefWindowInfo::external_begin_frame_enabled` einschalten, dann taktet der
  Host mit `SendExternalBeginFrame()` (CEF 116, `cef_browser.h` 660–663).
  Zusätzlich nötig: `--disable-frame-rate-limit`.
- Die Berichte dazu sind uneinheitlich; mehrere Nutzer beschreiben, dass es
  je nach Fassung nicht oder nur teilweise arbeitet.

**Für Fassung 1: 60 Hz, keine externe Taktung.** Die Verlockung ist groß —
Minecrafts Bild taktet Chromium, alles läuft synchron — aber sie koppelt
zwei Systeme, die heute unabhängig sind, und sie tauscht einen gemessenen
Gewinn gegen ein unerprobtes Verhalten. Die Schnittstelle wird so entworfen,
dass eine Zielrate über 60 später ausdrückbar ist, ohne Aufrufer zu ändern.

# 9 — Verteilung der Binärdateien

Der vorgeschlagene Ablauf ist richtig und deckt sich mit dem, was MCEF heute
tut — nur ohne fremden Server.

```json
{
  "runtime": "fn-web-runtime/1",
  "cef": "151.3.24+g2384915",
  "chromium": "151.0.7922.174",
  "jcef": "<commit>",
  "platforms": {
    "windows-x86_64": { "url": "...", "size": 0, "sha256": "..." },
    "linux-x86_64":   { "url": "...", "size": 0, "sha256": "..." },
    "macos-aarch64":  { "url": "...", "size": 0, "sha256": "..." }
  }
}
```

Ablauf: Manifest holen → in einen Zwischenordner laden → SHA-256 prüfen → in
einen Zwischenordner entpacken → **umbenennen** → starten. Bei falscher
Prüfsumme nichts ausführen und den Zwischenstand löschen.

Zwei Dinge, die MCEF heute anders macht und die wir übernehmen sollten:

- MCEF prüft die Prüfsumme **des Archivs**, nicht der entpackten Dateien. In
  dieser Sitzung war das unser Glück — die getauschte Bibliothek blieb über
  drei Client-Starts liegen. Für eine ausgelieferte Laufzeitumgebung ist es
  eine Lücke: Wer die entpackten Dateien austauscht, wird nicht bemerkt.
- Unter Windows lassen sich Bibliotheken eines laufenden Prozesses nicht
  ersetzen. **Die Installation muss vor der Initialisierung von CEF
  abgeschlossen sein**, sonst schlägt sie beim Aktualisieren im laufenden
  Spiel fehl.

# 10 — Fassungen nebeneinander

**Ja, und aus einem Grund mehr, als im Auftrag steht.**

```text
.minecraft/
  fn-web-runtime/
    cef-151.3.24/
    cef-152.0.5/
    aktuell -> cef-151.3.24
```

Die genannten Vorteile gelten alle: atomare Aktualisierung, Rückkehr zur
vorigen Fassung, verschiedene Mod-Fassungen mit verschiedenen
Laufzeitumgebungen, und ein misslungenes Update zerstört nicht den letzten
funktionierenden Stand.

Der zusätzliche Grund steht in unserem eigenen Bericht: MCEF legt seine
Dateien heute unter `build/mcef-libraries/` ab — **und `gradlew clean` löscht
das**. In dieser Sitzung ist genau das die Falle, vor der der 60-Hz-Bericht
warnt. Eine Laufzeitumgebung gehört nicht in ein Verzeichnis, das ein
Bauwerkzeug aufräumen darf.

# 11 — Accelerated Paint als Phase 2

Bleibt Phase 2, und die Begründung ist gemessen: `CopyOutput` kostet 0,22 ms,
unser Upload rund 10 ms. **Das Ziel ist Minecrafts Renderthread, nicht das
Tippgefühl.**

Stand der Vorlage: Der Pull Request **#524** in java-cef (seit 29. Januar
2026, nicht zusammengeführt) bindet `OnAcceleratedPaint` — Windows über
D3D11-Texturhandles in OpenGL, Linux über dmabuf und EGL, macOS über
IOSurface. Marshall Greenblatt hat zugestimmt und wartet auf die macOS-Seite.
Für uns heißt das: Wenn wir ihn brauchen, tragen wir ihn selbst, und die
Windows-Seite ist die fertigste.

Vorgezogen wird das nur, wenn echte Messungen am Produkt zeigen, dass die
10 ms Bildzeit kosten, die auffallen.

# 12 — IME

**Das ist die interessantere Baustelle, und sie hat zwei Hälften mit sehr
verschiedenem Aufwand.**

**Die CEF-Hälfte ist einfach.** Alle vier Methoden sind da
(`cef_browser.h` 767–803), dazu die Struktur für Unterstreichungen
(`cef_composition_underline_t` mit `style`, `cef_types.h` 3019–3044). Zu
transportieren sind: der Text der Komposition, eine Liste von
Unterstreichungen, der zu ersetzende Bereich und die Position der Schreibmarke
— alles einfache Strukturen. Das ist ein Bindungs-Patch im Zuschnitt unserer
44 Zeilen, nur mit mehr Feldern. Schätzung: 150 bis 250 Zeilen.

**Die schwierige Hälfte ist, woher die Ereignisse kommen.** Minecraft läuft
auf GLFW, und **GLFW hat bis heute keine IME-Unterstützung in einer stabilen
Fassung**. Der Pull Request dafür (glfw#2130, eine Neuauflage von #2117) liegt
seit 2022 offen; er bringt `glfwSetPreeditCallback` und eine Verwaltung des
Kandidatenfensters, ausgeschaltet per Voreinstellung.

Es gibt also unter Minecraft keine Kompositionsereignisse, die man
weiterreichen könnte.

**Die ehrliche Einschätzung, um die gebeten wurde:**

```text
CEF-Seite         einfacher Bindungs-Patch
Ereignisquelle    native Eingabe-Hooks je Plattform oder ein GLFW-Fork
```

Unter Windows hieße das, die Fensterprozedur zu ergänzen und `WM_IME_*`
selbst zu behandeln; unter macOS `NSTextInputClient`; unter Linux ibus oder
fcitx über deren eigene Schnittstellen. Das ist **drei plattformspezifische
Eingabewege**, und es ist deutlich mehr Arbeit als Accelerated Paint — aber es
ist auch das Feature, das über die Benutzbarkeit für einen ganzen Teil der
Welt entscheidet.

Empfehlung: **CEF-Bindung früh mitnehmen** (sie ist billig und blockiert
nichts), die Ereignisquelle als eigenes Vorhaben mit Windows zuerst.

# 13 — Reproduzierbarer Bau

Die beiden Stolpersteine dieser Sitzung sind keine Zufälle und gehören
verhindert:

- **Das mitgelieferte `gsutil` startet unter Python 3.13 und neuer nicht mehr**
  (es scheitert an seiner eigenen Enum-Prüfung). In der Pipeline daher die
  Python-Fassung festnageln, nicht die des Rechners nehmen.
- **Windows' Pfadlängengrenze.** Der längste Pfad der CEF-Distribution kam im
  Zwischenordner auf 264 Zeichen, vier über der Grenze; das Entpacken blieb
  unvollständig, und der Bau scheiterte an fehlenden Kopfdateien. Also: kurzer
  Arbeitspfad oder ausdrücklich lange Pfade einschalten.

```text
tools/runtime/
  patches/            unsere Änderungen als .patch über einem gepinnten Checkout
  build-jcef.<ps1|sh> Checkout, Patches anwenden, CMake, Bau
  package.<ps1|sh>    CEF-Binärdateien + jcef.dll + Helfer einpacken
  manifest.<ps1|sh>   SHA-256 bilden, Manifest schreiben
```

**Patches als Dateien über einem gepinnten Upstream-Stand, nicht als eigener
Klon.** Ein Klon driftet und verbirgt, was eigentlich unser Anteil ist; eine
`.patch`-Datei zeigt es in jeder Zeile und lässt sich gegen eine neue
Upstream-Fassung erneut anwenden — mit Konflikt statt mit stillem Fehler.

Ablauf in der Pipeline: Commit → Bau je Plattform → Paket → Prüfsumme →
Manifest → Ablage. Nichts davon wird jetzt hochgeladen.

# 14 — Plattformen

**Windows zuerst.** Dort läuft die Entwicklung, dort ist die
Accelerated-Paint-Vorlage am weitesten, dort sitzt die Mehrheit der Spieler.

**Linux danach**, weil es billig ist: Der Bau ist derselbe, die Bibliotheken
liegen nur anders, und der einzige echte Unterschied ist das Ausführbar-Setzen
der Hilfsprogramme — was MCEF heute schon tut.

**macOS zuletzt.** Nicht wegen des Aufwands beim Bau, sondern wegen der Bündel:
Die Hilfsprogramme liegen dort in vier verschachtelten `.app`-Verzeichnissen,
und Signierung sowie Notarisierung sind ein eigenes Thema.

Plattformabhängig sind: die nativen Bibliotheken, Accelerated Paint (D3D11 /
dmabuf / IOSurface), IME, Zwischenablage, Mauszeiger, Fenstereinbindung und
die Grafik-Handles.

Plattformunabhängig bleiben: Browser-Verwaltung, Schemata, die Brücke zur
Seite, die Java-Schnittstelle, Monaco und der spätere Sprachdienst. **Das
Manifest ist von Anfang an plattformneutral**, auch wenn zunächst nur ein
Eintrag gefüllt ist.

# 15 — Was von MCEF übrig bleibt

Das ist die entscheidende Zahl für den Aufwand, und sie fällt klein aus.

### Was wir von MCEF tatsächlich aufrufen

```text
MCEF.getClient()        BrowserSession, WebConsole
MCEF.getApp()           FrameSchemes
MCEF.isInitialized()    McefBackend
MCEF.shutdown()         McefBackend
MCEFPlatform            McefBackend
```

**Fünf Berührungspunkte.** Alles andere — onPaint, Texturen, Popups, Eingabe,
Schemata, Zeiger — haben wir bereits selbst, weil Schritt A bis D genau das
zum Ziel hatten.

### Was MCEF für uns tut und wir übernehmen müssen

| MCEF heute | Umfang | bei uns |
|---|---|---|
| CEF starten (`CefUtil.init`) | ~140 Zeilen | nachbauen: Schalter, `CefSettings`, `CefApp.startup` |
| Nachrichtenschleife pumpen | Mixin auf `GameRenderer.render` | nachbauen, gleiche Stelle |
| Start beim ersten `setScreen` | Mixin auf `Minecraft.setScreen` | eigener Bootstrap |
| Beenden | Mixin auf `Minecraft.close` | nachbauen, plus Job-Objekt |
| Herunterladen | ~190 Zeilen + Mixin | ersetzt durch Punkt 9 |
| Ausführbar-Bits (Linux/macOS) | ~20 Zeilen | übernehmen |
| Schema-Anmeldung | `ModScheme`, 138 Zeilen | haben wir selbst (`FrameSchemes`) |
| Browser, Renderer, Eingabe | ~700 Zeilen | **brauchen wir nicht** |
| Absturzbehandlung | keine | neu, siehe Punkt 2 |

**MCEF ist insgesamt 2.612 Zeilen. Zu ersetzen sind davon rund 500** — der
Rest ist Browser- und Rendercode, den wir bewusst nicht benutzen.

**Vier Mixins**, das ist der gesamte Eingriff in Minecraft:
`Minecraft.setScreen` (Start), `GameRenderer.render` (Schleife),
`Minecraft.close` (Ende), `ClientPackSource.<clinit>` (Download).

# 16 — Kein Sprachdienst

Einverstanden, und aus dem eigenen Befund gestützt: Die Brücke zwischen Seite
und Java sitzt genau auf den Schnittstellen, die dieser Umbau anfasst. Sie
vorher zu bauen hieße, sie zweimal zu bauen.

---

## Empfohlene Reihenfolge

Aus dem Quelltext abgeleitet, nicht aus dem Vorschlag übernommen — die
Unterschiede sind begründet.

**1. Aktuelles java-cef auf seinem eigenen Pin bauen (CEF 146), ohne
Minecraft.**
Ein Programm, das einen fensterlosen Browser öffnet und `onPaint` zählt. Klärt
die Bau-Pipeline (Punkt 13) und beweist die Fassung, bevor irgendetwas an der
Mod hängt. *Hier entfällt unser 60-Hz-Patch ersatzlos.*

**2. Bootstrap und Nachrichtenschleife ersetzen.**
Die vier Mixins nachbauen, `CefUtil.init` übernehmen, `CefApp` selbst starten.
Ab hier läuft unsere Laufzeitumgebung, MCEF ist nur noch im Klassenpfad.
*Das ist der eigentliche Umzug; alles davor und danach ist klein.*

**3. Die fünf Berührungspunkte umhängen.**
`MCEF.getClient()` und `getApp()` zeigen auf unseren eigenen `CefApp`.
*Wenige Zeilen — deshalb steht dieser Punkt hier und nicht am Ende.*

**4. Eigene Verteilung.**
Manifest, Prüfsumme, versionierte Ablage außerhalb von `build/` (Punkt 9, 10).
*Ersetzt MCEFs Downloader und löst die `clean`-Falle.*

**5. MCEF aus den Abhängigkeiten entfernen.**
Erst jetzt, und dann fällt es von selbst heraus.

**6. IME, CEF-Seite.** Bindungs-Patch, billig, blockiert nichts.

**7. IME, Ereignisquelle.** Windows zuerst. Eigenes Vorhaben.

**8. Accelerated Paint.** PR #524 übernehmen, Windows zuerst — sofern
Messungen am Produkt es rechtfertigen.

**9. External Begin Frame.** Nur, wenn über 60 Hz nachweislich etwas bringt.

Gegenüber dem Vorschlag im Auftrag verschieben sich drei Dinge: Browser,
OSR, Schemata und Eingabe **entfallen als eigene Schritte** — sie sind schon
migriert. Die eigene Verteilung rückt **vor** das Entfernen von MCEF, weil sie
dessen Downloader ersetzt. Und ein Bauschritt ohne Minecraft steht ganz vorn,
weil beide Stolpersteine dieser Sitzung dort auffallen, wo sie billig sind.

## Was bewusst offenbleibt

Die Form der künftigen Schnittstelle für die Bildrate. Ob der JetBrains-Fork
eine bessere Vorlage für IME ist. Ob externe Taktung je in Frage kommt. Alles
drei sind Entscheidungen, die belastbarer werden, sobald Schritt 1 gelaufen
ist — und keine davon blockiert ihn.
