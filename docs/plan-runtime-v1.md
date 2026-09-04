# Version 1 der eigenen Web-Laufzeitumgebung — Umsetzungsplan

Stand: 31. August 2026. Die Architekturentscheidungen sind gefallen und werden
hier nicht wieder aufgemacht. Dieses Dokument beschreibt, was gebaut wird, in
welcher Reihenfolge, und woran der Erfolg gemessen wird.

**Leitsatz für Version 1: langweilig, klein, stabil.** Gleiche Zahlen wie
heute, anderer Unterbau. Kein neues Verhalten.

---

## Zwei Präzisierungen gegenüber den Annahmen des Auftrags

Beide beim Nachsehen im Upstream-Quelltext gefunden, beide vergrößern den
Umfang von Version 1. Keine davon berührt eine Grundentscheidung — sie
schärfen, was „kleiner Patch-Satz" konkret heißt.

### 1. „Keine eigene JCEF-Änderung" ist nicht haltbar — es wird genau eine

`FnBrowser extends CefBrowserOsr`. Im CinemaMod-Fork ist diese Klasse
öffentlich:

```java
// CinemaMod-Fork, CefBrowserOsr.java:26
public class CefBrowserOsr extends CefBrowser_N implements CefRenderHandler {
    public CefBrowserOsr(CefClient client, String url, boolean transparent,
                         CefRequestContext context) { ... }
```

**Upstream ist sie paketprivat**, und der Konstruktor hat einen Parameter
mehr:

```java
// upstream master
class CefBrowserOsr extends CefBrowser_N implements CefRenderHandler {
    CefBrowserOsr(CefClient client, String url, boolean transparent,
                  CefRequestContext context, CefBrowserSettings settings)
```

Erben ist unumgänglich: `sendKeyEvent`, `sendMouseEvent` und
`sendMouseWheelEvent` sind auf `CefBrowser_N` **`protected final`**, und
upstream bietet keine öffentliche Alternative. Wer Eingaben schicken will,
muss Unterklasse sein.

**Entschieden: ein Sichtbarkeits-Patch, zwei Zeilen.** Klasse und Konstruktor
auf `public`.

> Nachtrag: Der Patch-Satz bleibt nicht bei zwei Zeilen. Die Ausarbeitung in
> `plan-v1-blockA-und-input.md` hat eine dritte Präzisierung gefunden: Auf
> Windows berechnet upstream den virtuellen Tastencode aus einem **privaten
> Feld** `scancode` des AWT-Ereignisses, das nur der native AWT-Code füllt.
> Ein selbst gebautes `KeyEvent` trägt dort null, und damit erkennt Chromium
> **keine einzige Taste**. Der Patch bekommt deshalb eine zweite native
> Methode mit einfachen Parametern; der Satz wächst auf rund 64 Zeilen.
> Getippte Zeichen und die gesamte Maus sind davon nicht betroffen.

**Verworfen: eine eigene Klasse im Paket `org.cef.browser` unterzubringen**,
um die Paketsichtbarkeit auszunutzen. Unter NeoForge laufen Mods über
ModLauncher in Modulschichten; eine eigene Klasse neben dem java-cef-Jar im
selben Paket ist ein geteiltes Paket über zwei Module. Das ist dort kein
Schönheitsfehler, sondern ein möglicher Ladefehler. Da wir das Jar wegen des
nativen Teils ohnehin selbst bauen, kostet der Patch nichts.

**Ein Gewinn nebenbei:** Der neue fünfte Konstruktorparameter ist
`CefBrowserSettings` — genau die Klasse mit `windowless_frame_rate`. Damit
stirbt der Umgebungsvariablen-Behelf aus dem 60-Hz-Test ersatzlos:

```java
CefBrowserSettings settings = new CefBrowserSettings();
settings.windowless_frame_rate = 60;
```

### 2. Die Eingabe muss übersetzt werden — upstream spricht AWT

Der Fork hat die Ereignistypen durch eigene ersetzt, upstream ist bei den
AWT-Typen geblieben:

| | Fork (heute) | upstream |
|---|---|---|
| Tasten | `sendKeyEvent(CefKeyEvent)` | `sendKeyEvent(java.awt.event.KeyEvent)` |
| Maus | `sendMouseEvent(CefMouseEvent)` | `sendMouseEvent(java.awt.event.MouseEvent)` |
| Rad | `sendMouseWheelEvent(CefMouseWheelEvent)` | `sendMouseWheelEvent(MouseWheelEvent)` |

Unser Übersetzer geht heute von GLFW nach `CefKeyEvent`; künftig muss er von
GLFW nach AWT gehen. **Und der Sonderfall, den wir in `FnBrowser`
dokumentiert haben — dieser Fork erwartet in `keyChar` den GLFW-Tastencode
statt eines Zeichens — gilt upstream mit Sicherheit nicht.** Das Mapping ist
neu herzuleiten und neu zu vermessen.

Umfang: geschätzt 150 bis 250 Zeilen, ein eigener Arbeitspunkt, und
`TypingBenchmark` ist das Abnahmetor.

**Die gute Nachricht daneben:** Der Renderpfad zieht unverändert um. Die
Signaturen, die wir überschreiben, sind in beiden Fassungen gleich:

```java
onPaint(CefBrowser, boolean popup, Rectangle[] dirtyRects,
        ByteBuffer buffer, int width, int height)
onPopupShow(CefBrowser, boolean)
onPopupSize(CefBrowser, Rectangle)
```

Der `ByteBuffer` bleibt, die Dirty Rectangles bleiben, der Direktupload bleibt.

### 3. Der Umstieg ist ein Schnitt, kein Nebeneinander

MCEFs Jar und das neue java-cef-Jar tragen beide `org.cef.*`. Sie können nicht
gleichzeitig im Klassenpfad liegen. Es gibt deshalb **keine Zwischenstufe**, in
der der neue Bootstrap läuft und MCEF noch daneben steht. Version 1 ist ein
Zweig, in dem die Abhängigkeit hinausgeht, die Laufzeitumgebung hineinkommt
und alles gemeinsam wieder grün wird.

---

# 1 — Schritt für Schritt

Neun Schritte. Die ersten drei laufen ohne Minecraft, Schritt 4 bis 7 sind der
Schnitt, 8 und 9 sichern ab.

**S1. java-cef bauen, ohne Patch.**
Upstream auf seinem eigenen Pin (CEF 146.0.10 / Chromium 146.0.7680.179), Bau
nach dem Muster, das in dieser Sitzung schon funktioniert hat. Ergebnis:
`jcef.dll` plus Jar, beides unverändert.
*Fertig, wenn:* Der Bau reproduzierbar durchläuft, zweimal hintereinander, aus
einem leeren Arbeitsverzeichnis.

**S2. Sichtbarkeits-Patch.**
`CefBrowserOsr` und ihr Konstruktor auf `public`. Als `.patch`-Datei über dem
gepinnten Stand, nicht als Fork-Klon.
*Fertig, wenn:* Der Patch sich auf einen frischen Checkout anwenden lässt und
der Bau danach durchläuft.

**S3. Standalone-Prüfprogramm.** Siehe Abschnitt 4.
*Fertig, wenn:* 60 Hz nachgewiesen, sauberes Beenden nachgewiesen.

**S4. Bootstrap und Nachrichtenschleife.**
`CefApp` selbst starten, Schleife selbst pumpen. Siehe Abschnitt 5.
*Fertig, wenn:* Ein Browser in Minecraft malt, ohne dass MCEF beteiligt ist.

**S5. Eingabe-Adapter.**
GLFW nach AWT. Der Teil mit dem höchsten Restrisiko.
*Fertig, wenn:* `TypingBenchmark` dieselben Zahlen liefert wie heute.

**S6. Die fünf Berührungspunkte umhängen.**
`MCEF.getClient()`, `getApp()`, `isInitialized()`, `shutdown()`,
`MCEFPlatform` zeigen auf unsere eigenen Entsprechungen.
*Fertig, wenn:* Kein `com.cinemamod`-Import mehr im Quelltext.

**S7. MCEF aus den Abhängigkeiten entfernen.**
Der Schnitt. Ab hier gibt es kein Zurück im selben Zweig.
*Fertig, wenn:* `build.gradle` kennt `com.cinemamod` nicht mehr und der Client
startet.

**S8. Eigene Verteilung.** Siehe Abschnitt 6.
*Fertig, wenn:* Ein leerer Spielordner lädt, prüft, installiert und startet.

**S9. Absicherung.**
Die vorhandenen Messabläufe gegen die bekannten Zahlen fahren. Siehe „Abnahme".
*Fertig, wenn:* Alle vier Tore grün.

---

# 2 — Paket- und Modulstruktur

## Was heute steht

```text
web/            498 Zeilen   Runtime-Zustand, Sichtbarkeit, Taktgeber
web/capture/    368          Weltbild für den Hintergrund
web/frame/      476          geliehene Bildpuffer, Dirty Regions
web/ide/      1.483          IdeScreen, Auslieferung, Messabläufe
web/input/      265          Maustasten, Klickzähler, Fokus
web/mcef/     1.913          ← der Teil, der umzieht
web/measure/    154          Messreihen
web/screen/   2.410          Bildschirme und Messbildschirme
web/texture/    278          GL-Texturen
web/view/       371          Umrechnung, Compositor, Zeiger
```

## Ziel für Version 1

Nur eine Umbenennung und eine Trennung — **kein eigenes Gradle-Modul**. Ein
eigenes Modul lohnt erst mit der öffentlichen Schnittstelle für fremde Mods,
und die gehört ausdrücklich nicht in Version 1.

```text
web/runtime/            (neu, ersetzt web/mcef/)
  RuntimeBootstrap      CefApp starten, Schalter, CefSettings
  RuntimeLoop           doMessageLoopWork je Bild
  RuntimeInstall        Manifest, Prüfsumme, Installation
  RuntimePlatform       Plattformerkennung, Ausführbar-Bits
  ProcessGuard          Job-Objekt unter Windows
  BrowserManager        alle Sitzungen, Schließen beim Beenden
  BrowserSession        wie heute
  FnBrowser             wie heute, andere Basisklasse
  FrameScheme(s)        wie heute

web/input/awt/          (neu)
  AwtKeyEvents          GLFW → java.awt.event.KeyEvent
  AwtMouseEvents        GLFW → java.awt.event.MouseEvent

web/dev/                (neu, aus web/mcef/ herausgelöst)
  WebBenchmark, WebSelfTest, WebDebug, WebConsole
```

**Die eine Regel, die durchgehalten wird:** `web/runtime/` importiert nie aus
`web/ide/`, `web/screen/` oder irgendetwas, das Monaco kennt. Die Abhängigkeit
zeigt nur nach oben. Das ist die Vorbereitung auf ein eigenes Modul, ohne es
heute zu bauen.

Der Messcode (`WebBenchmark`, `WebSelfTest`, `WebDebug`, `WebConsole` — rund
900 der 1.913 Zeilen) wandert **aus** dem Laufzeitpaket heraus. Er gehört nicht
in etwas, das später ausgeliefert wird.

---

# 3 — Was von MCEF ersetzt werden muss

| MCEF heute | Zeilen | Ersatz | Aufwand |
|---|---|---|---|
| `CefUtil.init()` | ~140 | `RuntimeBootstrap` | mittel |
| Schleife (`GameRenderer.render`-Mixin) | ~30 | `RuntimeLoop`, gleiche Stelle | klein |
| Start (`Minecraft.setScreen`-Mixin) | ~110 | eigener Start, kein Mixin nötig | klein |
| Beenden (`Minecraft.close`-Mixin) | ~20 | `RuntimeLoop.shutdown` + `ProcessGuard` | klein |
| Herunterladen (`MCEFDownloader` + Mixin) | ~320 | `RuntimeInstall` (Abschnitt 6) | groß |
| Ausführbar-Bits Linux/macOS | ~25 | `RuntimePlatform` | klein |
| Plattformerkennung (`MCEFPlatform`) | ~75 | `RuntimePlatform` | klein |
| Einstellungen (`MCEFSettings`) | ~145 | entfällt — wir setzen fest | keiner |
| Absturzbehandlung | 0 | `ProcessGuard` — **neu** | mittel |
| Schema-Anmeldung (`ModScheme`) | ~138 | haben wir (`FrameSchemes`) | keiner |
| Browser/Renderer/Eingabe | ~700 | haben wir | keiner |

**Zu ersetzen: rund 620 Zeilen** (die Schätzung von 500 war knapp; der
Absturzschutz kommt hinzu und ist neu). **Nicht zu ersetzen: rund 1.000
Zeilen**, weil wir sie nie benutzt haben.

Der gesamte Eingriff in Minecraft bleibt bei **einem** Mixin:
`GameRenderer.render` für die Nachrichtenschleife. Die anderen drei brauchen
wir nicht — Start und Beenden hängen wir an unsere eigenen Mod-Ereignisse, den
Download an unsere Installation.

---

# 4 — Standalone-Prüfprogramm, vor Minecraft

Ein Java-Programm ohne Minecraft, das den ganzen Weg einmal geht. **Sein
eigentlicher Zweck ist, Signaturunterschiede zwischen Fork und upstream dort
auffallen zu lassen, wo sie billig sind** — nicht in einem Mod-Start, der aus
zwanzig anderen Gründen scheitern kann.

```text
tools/runtime/probe/
  Probe.java     ~150 Zeilen, keine Abhängigkeit außer java-cef
```

Ablauf:

1. `CefApp` mit `windowless_rendering_enabled` starten
2. Browser über `CefBrowserSettings` mit `windowless_frame_rate = 60` öffnen
3. Eine Seite laden, die in jedem Bild ein kleines Feld umfärbt — dieselbe
   `probe.html?v=takt`, die wir schon haben
4. In einer Schleife `doMessageLoopWork()` rufen und `onPaint`-Abstände messen
5. Nach 20 Sekunden `close(true)`, dann `CefApp.dispose()`
6. Prozesse zählen

**Wichtig: Die Schleife muss Minecrafts Modell nachbilden** — ein Thread, der
`doMessageLoopWork` je Durchlauf ruft, so wie MCEFs Mixin es in
`GameRenderer.render` tut. Ein Programm, das CEFs eigene Schleife laufen
lässt, prüft ein anderes Threading-Modell als das, in das wir integrieren, und
die Zusicherung aus dem Cleanup-Vertrag („onPaint kommt im selben Thread wie
close") wäre nicht mitgeprüft.

**Abnahme:**

| | Sollwert | Quelle |
|---|---|---|
| onPaint-Abstand p50 | 16,7 ms ± 1 | gemessen 16,85 ms |
| daraus Bildrate | ≈ 59–60/s | gemessen 59,4/s |
| `jcef_helper` nach `dispose()` | **0** | `tools/procwatch.ps1` |
| Laufzeit ohne Absturz | 20 s | — |

---

# 5 — Minecraft-Integration

## Bootstrap

Was `CefUtil.init()` heute tut, mit drei Änderungen:

```java
// Schalter — MCEFs drei, um zwei ergänzt
"--autoplay-policy=no-user-gesture-required"
"--disable-web-security"          // prüfen, ob wir das brauchen
"--enable-widevine-cdm"           // wir brauchen es nicht → weg

CefSettings settings = new CefSettings();
settings.windowless_rendering_enabled = true;
settings.cache_path = <außerhalb von build/>;
settings.background_color = transparent;
settings.user_agent_product = "FactoryNetwork/1";
```

`--disable-web-security` ist zu prüfen: MCEF setzt es, damit Seiten fremde
Quellen laden dürfen. Unsere Oberfläche liegt lokal und lädt nichts von außen.
**Wenn sie ohne den Schalter läuft, bleibt er weg** — er schaltet
Sicherungen ab, die wir nicht abschalten müssen.

**Zeitpunkt:** MCEF startet Chromium beim ersten `setScreen`. Wir starten es
beim ersten Öffnen einer Oberfläche — später, gezielter, und ohne Mixin. Wer
nie eine IDE öffnet, startet nie einen Browser.

## Nachrichtenschleife

Ein Mixin auf `GameRenderer.render`, HEAD, wie MCEF. Das ist die Stelle, an
der unser gesamter Renderpfad hängt: Sie sorgt dafür, dass `onPaint` im
Render-Thread ankommt, dass der geliehene Puffer gültig ist und dass wir ohne
Kopie hochladen können.

**Diese Stelle ist nicht verhandelbar.** Der ganze Cleanup-Vertrag ruht
darauf, dass `onPaint` und `close()` derselbe Thread sind.

## BrowserManager

Neu, klein, und der Grund ist das Beenden: Heute kennt niemand alle offenen
Sitzungen. Beim Herunterfahren muss jede geschlossen werden, bevor
`CefApp.dispose()` läuft.

```java
BrowserManager
  register(BrowserSession)      beim Öffnen
  unregister(BrowserSession)    beim Schließen
  closeAll()                    beim Herunterfahren
  count()                       für Messungen
```

## Lebenszyklus

Bleibt wie er ist — der Lifecycle-Test hat gezeigt, dass er trägt. Eine
Ergänzung: ein `CefLifeSpanHandler`, der `onBeforeClose` protokolliert. Heute
gibt es keine Bestätigung, dass ein Browser wirklich zu ist; ein
hängengebliebener fiele nicht auf.

## Herunterfahren

```text
Mod-Ende
  → BrowserManager.closeAll()
  → kurz weiterpumpen, damit CEF die Schließvorgänge abarbeitet
  → CefApp.dispose()
```

Der mittlere Schritt fehlt heute und ist der Grund, warum beim harten Abbruch
Prozesse stehenblieben. `dispose()` ohne vorheriges Abarbeiten lässt CEF
keine Gelegenheit, seine Kinder abzumelden.

## Job-Objekt unter Windows

Der Absturzschutz. Beim harten Beenden blieben in dieser Sitzung **acht
`jcef_helper`-Prozesse** stehen.

```text
vor CefApp.startup():
  CreateJobObject
  SetInformationJobObject(JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE)
  AssignProcessToJobObject(eigener Prozess)
```

Kindprozesse erben die Zuordnung. Stirbt der Spielclient, sterben die Helfer
mit — auch beim Absturz, auch beim Abschießen im Taskmanager.

**Der Weg dorthin ist JNA, nicht FFM.** `java.lang.foreign` wurde erst mit
Java 22 endgültig; unter Java 21 ist es Vorschau und verlangt
`--enable-preview` — ein Schalter, den NeoForge nicht setzt und den ein Mod
bei Spielern nicht erzwingen kann.

JNA liegt dagegen bereits im Laufzeitpfad, über Minecrafts eigene
Abhängigkeit:

```text
com.github.oshi:oshi-core:6.4.10
  └── net.java.dev.jna:jna:5.14.0
  └── net.java.dev.jna:jna-platform:5.14.0
```

Nachgesehen: JNAs eigenes `Kernel32`-Interface bindet die
Job-Object-Funktionen **nicht** (mit `javap` geprüft, keine Treffer). Wir
deklarieren sie selbst — das ist bei JNA ein eigenes Interface über
`Native.load("kernel32", ...)` mit drei Methoden, rund vierzig Zeilen und
**keine neue Abhängigkeit**.

**Zu verifizieren, nicht anzunehmen:** Manche Startprogramme stecken den
Spielprozess selbst schon in ein Job-Objekt. Verschachtelte Jobs gibt es seit
Windows 8, aber ob die Vererbung dann noch wie erwartet greift, gehört
gemessen — mit demselben Versuch, der den Befund erzeugt hat: Client hart
abschießen, Prozesse zählen.

---

# 6 — Verteilung der Laufzeitumgebung

## Zwei Teile mit verschiedenen Wegen

| | Weg | Grund |
|---|---|---|
| Java-Klassen (gepatchtes java-cef) | im Mod-Jar, per jarJar | wir bauen es ohnehin selbst, es ist klein, es muss zur Mod-Fassung passen |
| Native Dateien (CEF, ~200 MB) | Download über Manifest | zu groß fürs Jar, plattformabhängig |

## Manifest

```json
{
  "runtime": 1,
  "cef": "146.0.10+g8219561",
  "chromium": "146.0.7680.179",
  "jcef": "<commit des gepinnten Stands>",
  "platforms": {
    "windows-x86_64": { "url": "...", "size": 0, "sha256": "..." },
    "linux-x86_64":   { "url": "...", "size": 0, "sha256": "..." },
    "macos-aarch64":  { "url": "...", "size": 0, "sha256": "..." }
  }
}
```

Von Anfang an alle drei Plattformen im Format, auch wenn zunächst nur eine
gefüllt ist.

## Installation

```text
Manifest holen
  → in <ziel>/.tmp-<zufall>/ laden
  → SHA-256 über die geladene Datei
  → stimmt nicht: löschen, abbrechen, nichts ausführen
  → stimmt: in <ziel>/.tmp-<zufall>/entpackt/ entpacken
  → umbenennen nach <ziel>/cef-146.0.10/
  → Verweis "aktuell" umsetzen
```

Das Umbenennen ist der einzige Schritt, der etwas sichtbar macht — davor ist
alles Zwischenstand, danach ist alles fertig. Ein Abbruch mittendrin
hinterlässt einen `.tmp-`-Ordner und sonst nichts.

**Bei falscher Prüfsumme wird nichts ausgeführt.** Kein Ausweichen, kein
zweiter Versuch mit derselben Datei.

## Ablage

```text
<gameDir>/factorynetwork/runtime/
  cef-146.0.10/
  cef-151.3.24/        (später)
  aktuell -> cef-146.0.10
```

**Ausdrücklich außerhalb von `build/`.** Der 60-Hz-Bericht nennt die Falle:
MCEF legt heute unter `build/mcef-libraries/` ab, und `gradlew clean` löscht
das — die eingespielte Bibliothek verschwindet still, und die nächste Messung
zeigt kommentarlos wieder 30 Hz.

Fassungen nebeneinander bringen: atomare Aktualisierung, Rückkehr zur vorigen,
verschiedene Mod-Fassungen mit verschiedenen Laufzeitumgebungen, und ein
misslungenes Update lässt den letzten funktionierenden Stand unberührt.

**Reihenfolge im Start:** Die Installation muss **vor** der Initialisierung
von CEF abgeschlossen sein. Unter Windows lassen sich Bibliotheken eines
laufenden Prozesses nicht ersetzen.

**Und wann genau läuft der erste Download?** Das ist die Frage, die der
gestrichene `setScreen`-Mixin von MCEF beantwortet hat, und sie braucht eine
eigene Antwort. Zwei Aussagen dieses Plans ergeben zusammen sonst einen
schlechten Erststart: „CEF startet erst beim ersten Öffnen einer Oberfläche"
und „die Installation muss davor fertig sein" hieße, dass der erste Druck auf
die IDE-Taste stumm auf zweihundert Megabyte wartet.

Deshalb: **Der Download beginnt beim Laden der Mod, im Hintergrund.** Die
Initialisierung von CEF bleibt trotzdem verzögert — wer nie eine Oberfläche
öffnet, startet nie einen Browser. Wird die Oberfläche geöffnet, bevor der
Download fertig ist, wartet sie mit sichtbarem Fortschritt statt stumm. Ein
fehlgeschlagener Download meldet sich beim Öffnen mit einer verständlichen
Meldung, nicht mit einem Stapelauszug.

**Eine Lücke, die MCEF hat und wir nicht übernehmen:** MCEF prüft die
Prüfsumme des Archivs, nicht der entpackten Dateien. In dieser Sitzung war das
unser Glück — die getauschte Bibliothek blieb über drei Starts liegen. Für
etwas Ausgeliefertes ist es eine Lücke.

---

# 7 — Risiken

| Risiko | Eintritt | Wirkung | Gegenmaßnahme |
|---|---|---|---|
| **Eingabe-Adapter** (GLFW→AWT) | hoch | Tippen kaputt oder subtil falsch | `TypingBenchmark` als Tor; früh, eigener Schritt |
| **Bau-Pipeline** | mittel | blockiert alles | S1 vor allem anderen; zweimal aus leerem Verzeichnis |
| **Windows-Pfadlänge** | **eingetreten** | unvollständiges Entpacken, kryptische Fehler | kurzer Arbeitspfad; 264 Zeichen gemessen bei Grenze 260 |
| **Python-Fassung** | **eingetreten** | `gsutil` startet nicht unter 3.13+ | Fassung in der Pipeline festnageln |
| **Helferprozesse** | **eingetreten** | 8 Waisen je hartem Abbruch | Job-Objekt; verschachtelte Jobs verifizieren |
| **`org.cef` doppelt** | mittel | Absturz beim Start, wenn ein MCEF-Mod danebenliegt | Unverträglichkeit erklären; Prüfung mit klarer Meldung |
| **macOS-Bündel** | niedrig (V1) | Helfer starten nicht, Signierung fehlt | macOS ist nicht Version 1 |
| **Linux-Ausführbar-Bits** | niedrig | Helfer startet nicht | `RuntimePlatform`, MCEFs Lösung übernehmen |
| **API-Unterschiede** Fork↔upstream | mittel | Übersetzungsfehler | S3 findet sie ohne Minecraft |

Zur Zeile `org.cef` doppelt: Umbenennen der Pakete scheidet aus — die
Exportnamen der nativen Bibliothek tragen den vollen Klassennamen
(`Java_org_cef_browser_...`). Für Version 1 wird die Unverträglichkeit erklärt;
die langfristige Antwort ist, dass unsere Laufzeitumgebung das gemeinsame Mod
wird, das andere benutzen.

---

# 8 — Was nicht in Version 1 gehört

| | warum nicht |
|---|---|
| **Accelerated Paint** | `CopyOutput` kostet 0,22 ms. Ziel wäre der Upload (~10 ms) und damit Minecrafts Bildzeit, nicht die Tipplatenz. Vorlage (PR #524) ist nicht zusammengeführt. |
| **External Begin Frame** | Über 60 Hz brächte messbar nichts, solange 60 nicht ausgereizt ist. Koppelt zwei heute unabhängige Systeme. |
| **IME-Ereignisquelle** | GLFW hat keine IME-Unterstützung im Stable. Braucht native Eingabewege je Plattform — eigenes Vorhaben. |
| **CEF 151** | Upstream pinnt 146. Der Wechsel wäre unser Pin mit unserem Risiko. Erst nach Version 1. |
| **Öffentliche Schnittstelle für fremde Mods** | Friert Entscheidungen ein, die wir noch treffen. Erst wenn die eigene Nutzung steht. |
| **Sprachdienst-Brücke** | Sitzt genau auf den Schnittstellen, die dieser Umbau anfasst. Vorher gebaut heißt zweimal gebaut. |
| **Dynamische Bildrate** (60/30/10/verborgen) | Upstream schenkt uns `setWindowlessFrameRate`, aber Version 1 setzt 60 fest im Konstruktor. Umschalten ist neues Verhalten. |

---

# Abnahme — die Tore für Version 1

Die Messabläufe existieren bereits, die Sollwerte stehen in den Berichten
dieses Projekts. **Version 1 ist gelungen, wenn dieselben Läufe dieselben
Zahlen liefern.** Keine Verbesserung nötig, keine Verschlechterung erlaubt.

| Ablauf | Größe | Sollwert | Quelle |
|---|---|---|---|
| Standalone (S3) | onPaint-Abstand p50 | 16,7 ms ± 1 | 16,85 ms |
| `WebSelfTest` | Bildnachweis | unverändert bestanden | Schritt A–C |
| `ProbeBenchmark` | Takt p50 | 16,85 ms ± 1 | 16,85 ms |
| `ProbeBenchmark` | A Eingabe→Bild p50 | 30,2 ms ± 3 | 30,2 ms |
| `ProbeBenchmark` | Upload p50 | 9,9 ms ± 1,5 | 9,9 ms |
| `ProbeBenchmark` | Minecrafts Bildzeit p50 | 8,6 ms ± 1 | 8,6 ms |
| `TypingBenchmark` | Tippstrecke | wie heute | Schritt G/H |
| `LifecycleBenchmark` | 3 Zyklen | 186→190→191 MB ± 15 | Lifecycle-Bericht |
| `procwatch` nach hartem Abbruch | Waisenprozesse | **0** (heute: 8) | neu, Job-Objekt |

Die letzte Zeile ist die einzige, die besser werden muss.

---

# Aufgabenliste

Übertragbar in Vorgänge. Jede Aufgabe hat eine Abnahmebedingung.

### Block A — Bau, ohne Minecraft

**A1. java-cef auf Pin 146 bauen**
Upstream klonen, Fassung festnageln, CMake, Bau von `jcef.dll` und Jar.
*Fertig:* Zweimal aus leerem Verzeichnis reproduzierbar durchgelaufen.
*Achtung:* Kurzer Pfad (264 Zeichen sind gemessen zu lang), Python-Fassung
festnageln (`gsutil` läuft nicht unter 3.13+), `clang-format` darf fehlen.

**A2. Sichtbarkeits-Patch als Datei**
`CefBrowserOsr` und Konstruktor auf `public`, als `.patch` über dem Pin.
*Fertig:* Anwendbar auf frischen Checkout, Bau läuft danach durch.

**A3. Bau-Pipeline zusammenschreiben**
`tools/runtime/` mit Checkout, Patches, Bau, Verpacken, Prüfsumme, Manifest.
*Fertig:* Ein Aufruf erzeugt ein vollständiges Paket samt Manifest.

**A4. Standalone-Prüfprogramm**
Siehe Abschnitt 4, mit nachgebildeter Pump-Schleife.
*Fertig:* p50 16,7 ms ± 1, null Helferprozesse nach `dispose()`.

### Block B — Der Schnitt

**B1. `RuntimeBootstrap`**
`CefApp` selbst starten. Schalterliste prüfen, `--enable-widevine-cdm`
streichen, `--disable-web-security` testweise weglassen.
*Fertig:* Chromium startet ohne MCEF; Oberfläche lädt.

**B2. `RuntimeLoop`**
Mixin auf `GameRenderer.render`, `doMessageLoopWork` je Bild.
*Fertig:* `onPaint` kommt im Render-Thread an (Zusicherung aus dem
Cleanup-Vertrag gilt weiter).

**B3. Eingabe-Adapter GLFW → AWT** ← höchstes Risiko
Tasten, Zeichen, Maus, Rad, Modifikatoren. Das Mapping neu herleiten; der
Sonderfall des alten Forks (GLFW-Code in `keyChar`) gilt nicht mehr.
*Fertig:* `TypingBenchmark` liefert die Zahlen von heute; Sonderzeichen,
Umschalt- und Strg-Kombinationen von Hand geprüft.

**B4. `FnBrowser` auf die neue Basisklasse**
Konstruktor mit `CefBrowserSettings`, `windowless_frame_rate = 60`.
Umgebungsvariablen-Behelf entfernen.
*Fertig:* Takt 16,85 ms ± 1 ohne gesetzte Umgebungsvariable.

**B5. `BrowserManager`**
Sitzungen registrieren, alle schließen beim Beenden.
*Fertig:* Nach dem Beenden sind alle Sitzungen zu, bevor `dispose()` läuft.

**B6. `ProcessGuard` (Windows-Job-Objekt)**
Eigenes JNA-Interface für `CreateJobObject`, `SetInformationJobObject`,
`AssignProcessToJobObject`; vor `CefApp.startup()`. JNA 5.14 liegt über
Minecrafts oshi-Abhängigkeit bereits im Pfad — kein FFM (in Java 21 nur
Vorschau), keine neue Abhängigkeit.
*Fertig:* Client hart abschießen → **null** `jcef_helper` übrig (heute: acht).
Verschachtelte Jobs unter einem echten Startprogramm mitprüfen.

**B7. Geordnetes Herunterfahren**
`closeAll()` → weiterpumpen → `dispose()`.
*Fertig:* Kein Prozess bleibt nach normalem Spielende zurück.

**B8. Die fünf Berührungspunkte umhängen**
*Fertig:* Kein `com.cinemamod`-Import mehr im Quelltext.

**B9. MCEF entfernen**
Aus `build.gradle`, Abhängigkeit weg.
*Fertig:* Client startet ohne MCEF im Klassenpfad.

### Block C — Verteilung

**C1. `RuntimeInstall`**
Manifest, Download in Zwischenordner, SHA-256, entpacken, umbenennen. Start
des Downloads beim Laden der Mod im Hintergrund; CEF-Initialisierung bleibt
verzögert; Öffnen vor Fertigstellung zeigt Fortschritt.
*Fertig:* Leerer Spielordner installiert und startet; verfälschte Prüfsumme
führt zu Abbruch ohne Ausführung; Öffnen während des Downloads zeigt
Fortschritt statt zu blockieren.

**C2. Versionierte Ablage**
`<gameDir>/factorynetwork/runtime/cef-<fassung>/`, außerhalb von `build/`.
*Fertig:* `gradlew clean` lässt die Laufzeitumgebung unberührt.

**C3. `RuntimePlatform`**
Erkennung, Ausführbar-Bits für Linux und macOS.
*Fertig:* Unter Linux startet der Helfer.

### Block D — Absicherung

**D1. Regressionslauf**
Alle Tore aus der Abnahmetabelle.
*Fertig:* Jede Zeile innerhalb ihrer Toleranz.

**D2. Unverträglichkeit mit MCEF-Mods**
Prüfung beim Start, verständliche Meldung statt Absturz.
*Fertig:* Mit einem MCEF-Mod daneben erscheint eine Meldung, kein Stapelauszug.

**D3. Berichte nachziehen**
`stand-60hz.md` und `plan-eigene-runtime.md` auf den Stand nach dem Umbau
bringen; der Umgebungsvariablen-Behelf ist dann Geschichte.

### Reihenfolge

```text
A1 → A2 → A3 → A4        ohne Minecraft, blockiert alles Weitere
       ↓
B1 → B2 → B3 → B4        der Schnitt; B3 zuerst angehen, höchstes Risiko
       ↓
B5 → B6 → B7             Lebenszyklus und Absturzschutz
       ↓
B8 → B9                  MCEF hinaus
       ↓
C1 → C2 → C3             Verteilung
       ↓
D1 → D2 → D3             Abnahme
```

**B3 ist der Punkt, an dem Version 1 scheitern kann.** Alles andere ist
Umzug von Bekanntem. Deshalb steht das Standalone-Prüfprogramm davor: Es
findet Signaturunterschiede, bevor sie in einem Mod-Start auftauchen, wo
zwanzig andere Dinge gleichzeitig schiefgehen können.
