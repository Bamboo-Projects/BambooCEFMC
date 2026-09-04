# B4 und B3e: der Mod läuft auf der eigenen Laufzeitumgebung

> **Weitergegangen.** B5, B6 und B7 stehen — Verwalter, Wächter, Reihenfolge.
> Der aktuelle Bericht ist **`stand-runtime-b567.md`**; die Waise nach hartem
> Abbruch ist dort behandelt.

Stand: 1. September 2026. `FnBrowser` erbt jetzt von `CefBrowser_N` aus
upstream java-cef, der Eingabeadapter hängt daran, und der Client läuft damit
im Spiel. MCEF ist nicht entfernt — es steht weiterhin als Standardweg da und
wird über einen Schalter beiseitegelassen.

---

## In einem Satz

Mit `./gradlew runClient -Pfnruntime` läuft der Mod gegen Chromium 146 statt
gegen MCEFs Chromium 116, und er läuft **schneller als vorher** — in jeder
Stufe der Tippmessung, gemessen in derselben Sitzung auf demselben Rechner.

---

## Der Konflikt, der die Form vorgab

**MCEF und upstream java-cef können nicht nebeneinander im Klassenpfad
liegen.** Beide MCEF-Jars bringen das vollständige Paket `org.cef` mit:

```text
mcef-2.1.6-1.21.1.jar            170 Klassen unter org/cef/
mcef-neoforge-2.1.6-1.21.1.jar   170 Klassen unter org/cef/
```

Gleiche Namen, anderer Inhalt. Damit ist jede Lösung innerhalb einer
Übersetzungseinheit ausgeschlossen — auch ein `if`, auch eine Fabrik. Die
Trennung muss vor den Compiler.

**Der gewählte Dev-Schnitt** ist Weg 3 aus dem Auftrag: eine temporäre
Konfiguration, vollständig über einen Schalter rückbaubar.

```text
src/main/java      alles, was von der Aufteilung nichts wissen muss
src/mcef/java      ohne Schalter — MCEF liefert Chromium wie bisher
src/runtime/java   mit -Pfnruntime — unsere eigene Laufzeitumgebung
```

Vier Stellen wussten, dass Chromium von MCEF kommt. Sie fragen jetzt `CefHost`,
und `CefHost` gibt es je Weg einmal:

| Stelle | vorher | jetzt |
|---|---|---|
| `BrowserSession` | `new FnBrowser(MCEF.getClient()…)` | `FnBrowser.open(…)` |
| `FrameSchemes` | `MCEF.getApp().getHandle()` | `CefHost.app()` |
| `WebConsole` | `MCEF.getClient()` | `CefHost.client()` |
| `WebSupport` | `McefBackend.create()` | `CefHost.backend()` |

`src/main/java` nennt MCEF damit an keiner Stelle mehr. **Der Standardbau ohne
Schalter ist unverändert grün** — das ist die Bedingung, unter der „MCEF nicht
entfernen" nachweisbar wahr bleibt.

---

## Vier Unterschiede zwischen Fork und upstream

Jeder musste überbrückt werden, und jeder sagt etwas darüber, wie weit die
beiden auseinandergelaufen sind.

**`CefResourceHandler` ist gewachsen.** Upstream verlangt `open`, `read` und
`skip`; die Parametertypen dafür — `BoolRef`, `CefResourceReadCallback`,
`LongRef`, `CefResourceSkipCallback` — gibt es im Fork nicht. Die gemeinsame
Fassung `FrameScheme` endet deshalb eine Ableitung früher, und `FrameHandler`
nimmt je Weg den Rest. Upstream beschreibt für `open` und `read` selbst einen
Rückweg auf die älteren Methoden; genau den nimmt unsere Fassung, damit die
ganze Arbeit in der gemeinsamen Klasse bleibt.

**`CefDisplayHandler` hat `onFullscreenModeChange` dazubekommen.** Die Methode
steht jetzt **ohne `@Override`** in `WebConsole`. So erfüllt dieselbe Datei
beide Schnittstellen: Dort setzt sie eine Methode außer Kraft, hier ist sie
eine zusätzliche, die niemand ruft.

**`CefCursorType` gibt es nur im Fork.** Die Zeigertabelle gehört jetzt uns und
liegt als `CursorType` neben `BrowserCursor` — mit einem `fromId`, das eine
unbekannte Zahl zum Pfeil macht statt zum Absturz. Die Vorlage im Fork griff
mit `values()[id]` zu und flog bei jeder Zahl, die eine neuere
Chromium-Fassung hinzufügt, aus dem Rahmen.

**Ohne MCEF pumpt niemand.** MCEF hängt per Mixin in `GameRenderer.render`.
Der Takt kommt jetzt aus `RenderFrameEvent.Pre` — vor dem Zeichnen, damit ein
Bild, das Chromium gerade geliefert hat, noch in dieselbe Textur wandert, die
dieses Bild benutzt.

---

## Zwei Hürden beim ersten Lauf, beide lehrreich

### Das Jar kam nicht auf den Laufklassenpfad

`runtimeOnly files(...)` landet im `runtimeClasspath`, aber **nicht** in der
Liste, die ModDevGradle dem Client mitgibt — dort stehen nur aufgelöste
Module. Für alles andere gibt es je Lauf eine eigene Konfiguration:

```groovy
add(neoForge.runs.client.additionalRuntimeClasspathConfiguration.name,
        files(new File(ownRuntimeDir, 'jcef.jar')))
```

### Der erste Ladeweg war falsch, obwohl er die Bibliothek lud

Der Entwurf setzte `SystemBootstrap.setLoader` und lud die DLLs mit absolutem
Pfad über `System.load`. Das lädt sie auch — und nützt nichts:

```text
java.lang.UnsatisfiedLinkError: 'boolean org.cef.CefApp.N_PreInitialize()'
```

**Native Methoden findet die JVM nur in Bibliotheken, die der Klassenlader der
jeweiligen Klasse geladen hat.** Gemessen im Spiel:

```text
CefApp        cpw.mods.cl.ModuleClassLoader
FnCefRuntime  cpw.mods.modlauncher.TransformingClassLoader
```

Ein `System.load` aus unserem Code bindet an den zweiten. Die Datei steht im
Prozess, und der erste native Aufruf scheitert trotzdem.

Die Lösung ist, nichts zu tun: java-cef ruft `System.loadLibrary` aus seiner
eigenen Klasse, und dann stimmt der Lader. Gefunden wird die Datei über
`java.library.path` — auf Windows speist der sich aus dem `PATH`, und den
setzt das Buildskript für diesen Lauf.

---

## Die Messwerte

Alle in derselben Sitzung, auf demselben Rechner, 1920×1080.

### Takt — `ProbeBenchmark`, Stufe A

| Größe | Gemessen | Sollwert |
|---|---|---|
| onPaint p50 | **16,78 ms (59,6/s)** | 16,85 ms (59,4/s) |
| p10 / p90 | 2,07 / 25,37 ms | — |

### Eingabe → Bild — `ProbeBenchmark`, Stufe B

| Größe | Gemessen | Sollwert |
|---|---|---|
| A Eingabe→Bild p50 | **22,3 ms** | 30,2 ± 3 |
| p95 | **26,8 ms** | 42,2 ± 5 |
| B Upload p50 | 8,9 ms | 9,9 |
| bis sichtbar p50 | 34,4 ms | 43,7 |
| Minecrafts Bildzeit p50 | 8,5 ms | 8,6 |

### Tippen — `TypingBenchmark`, beide Wege im Vergleich

**Der eigentliche Nachweis.** Der Sollwert aus der Übergabe gehört zur
Probemessung, nicht zur Tippmessung — für die gab es keinen. Also wurde
derselbe Lauf einmal auf MCEF gefahren:

| Stufe | MCEF (CEF 116) | eigene Laufzeit (CEF 146) |
|---|---|---|
| Glas an, p50 / p95 | 49,9 / 79,7 ms | **45,7 / 65,0 ms** |
| Glas aus, p50 / p95 | 46,9 / 71,7 ms | **42,0 / 57,7 ms** |
| Deckend, p50 / p95 | 46,4 / 70,5 ms | **39,8 / 55,6 ms** |
| Bilder je Sekunde | 12,6 – 12,7 | **14,5 – 15,2** |

Der neue Weg ist in jeder Stufe schneller, bei p95 deutlich.

### Lebenslauf und Waisen

Drei Zyklen öffnen und schließen, Speicher der Chromium-Prozesse von außen
mitgeschrieben (`tools/procwatch.ps1`):

| | gemessen | alte Referenz |
|---|---|---|
| nach Zyklus 1 | 271 MB | 186 MB |
| nach Zyklus 2 | **180 MB** | 190 MB |
| nach Zyklus 3 | **177 MB** | 191 MB |
| Spitze während des Laufs | 345 MB | — |

Die Reihe fällt und steht dann — kein Wachstum über die Zyklen. Die alte
Reihe stieg leicht; unsere geht nach der ersten Spitze zurück.

```text
jcef_helper nach dispose            0
jcef_helper nach hartem Abbruch     einmal 1, zweimal 0 (drei Messungen)
```

Der Proof-of-Concept hatte acht. Die eine Waise ist nicht reproduzierbar und
gehört dem Wächter, den es noch nicht gibt (B6).

---

## Eingabe im Spiel

**Der Scancode aus Minecrafts Tastenrückruf trägt dieselbe Kodierung wie der
aus `glfwGetKeyScancode`.** Das war offen — der Prüfstand hatte nur den
zweiten gemessen. Mitgeschrieben im Spiel:

```text
Ende      glfw=269  scancode=0x14f  basis=0x4f  erweitert=true   vk=35
Pos1      glfw=268  scancode=0x147  basis=0x47  erweitert=true   vk=36
Bild ab   glfw=267  scancode=0x151  basis=0x51  erweitert=true   vk=34
Ab (Alt)  glfw=264  scancode=0x150  basis=0x50  erweitert=true   vk=40  mods=0x4
Strg+F    glfw=70   scancode=0x21   basis=0x21  erweitert=false  vk=70  mods=0x2
Esc       glfw=256  scancode=0x1    basis=0x1   erweitert=false  vk=27
Eingabe   glfw=257  scancode=0x1c   basis=0x1c  erweitert=false  vk=10
```

Das Bit 0x100 ist da, wo es sein soll. Damit hält der Adapter im Spiel, was
der Prüfstand gemessen hat.

**Was Monaco selbst dazu sagt:** Die Messreihe `-Pidebench` fährt Strg+F,
Escape, Strg+A/C/V/Z, Pos1, Ende, Bild ab, Alt+Pfeil, Mehrfachcursor und
Themenwechsel. Alle Stufen liefen durch — „Suchfeld offen",
„Vervollständigung offen", „Zehn Schreibmarken", „Große Auswahl". Das ist
automatischer Nachweis dafür, dass die Kürzel Monaco erreichen; es ersetzt
nicht das Urteil eines Menschen.

---

## Offene Punkte

### „Exception in thread" ohne Stapel

Bei jeder Browsererzeugung erscheinen einige Zeilen

```text
Exception in thread "Render thread"
```

**ohne alles dahinter.** Was dazu bekannt ist:

- Nur auf dem neuen Weg. Derselbe Lauf auf MCEF: null.
- Die Zahl schwankt zwischen eins und sieben je Lauf.
- Kein Rückruf von uns ist beteiligt. Alle Methoden von `FnBrowser` wurden
  abgesichert und protokolliert; keine hat je gemeldet.
- Der Stapel fehlt auch mit `-XX:-OmitStackTraceInFastThrow`, und die
  Standardfehlerausgabe in eine eigene Datei umzuleiten fängt nichts ab. Die
  Kopfzeile kommt also aus dem nativen Teil, und die Ausnahme trägt keinen
  Stapel.
- **Folgenlos, soweit messbar:** Selbsttest in allen vier Ecken korrekt,
  Alpha korrekt, Takt und Latenzen besser als vorher, Lebenslauf sauber.

**`-Xcheck:jni` ist als Weg zu:** Der Lauf bricht den Prozess hart ab
(Rückgabewert 0xC0000409), bevor überhaupt ein Browser entsteht — die JVM
findet vorher Verstöße in fremdem Code (JNA, Registry-Zugriffe) und beendet
sich. Der Schalter bleibt als `-Pjnicheck` erhalten, taugt hier aber nicht.

**Der Widerspruch, der die halbe Diagnose ist:** Der Mitschnitt der
Standardfehlerausgabe blieb leer. Ginge die Meldung über
`Throwable.printStackTrace` auf `System.err`, hätte er mindestens die
Stapelzeilen gesehen. Dass er *nichts* sah, heißt: Die vollständige Meldung
entsteht im nativen Teil auf dem rohen Dateizeiger 2, und die Ausnahme trägt
keinen Stapel. Das passt zu einer Meldung aus dem Aufbau der JNI-Umgebung —
nicht zu einer Ausnahme aus einem unserer Rückrufe, und es erklärt, warum
keine unserer Absicherungen je gemeldet hat.

### Der Waisenprozess nach hartem Abbruch

Einmal eins, einmal null. Ein Wächter, der Chromiums Hilfsprozesse überlebt
und aufräumt, ist ein eigener Schritt (B6) und ausdrücklich nicht dieser.

### Die Handprüfung in Monaco fehlt

Die Liste aus dem Auftrag — deutsches Layout, AltGr, Kopieren und Einfügen,
Esc-Verhalten, Scrollrichtung, Doppelklick auf ein Wort — braucht einen
Menschen an der Tastatur. Sie ist **nicht** gefahren. Was maschinell prüfbar
war, steht oben; alles über das Gefühl beim Tippen ist offen.

---

## Was ausdrücklich nicht passiert ist

```text
MCEF ist nicht entfernt          Standardbau läuft unverändert darauf
keine Verteilung gebaut          der Ordner muss dasein, sonst hört es auf
kein ProcessGuard                der Waisenprozess bleibt ein offener Punkt
kein Accelerated Paint, kein External Begin Frame, kein CEF 151
```

---

## Vorschlag für den nächsten Schritt

Nicht ausgeführt, nur vorgeschlagen:

```text
B5  BrowserManager        wer Browser hält, zählt und schließt
B6  ProcessGuard          der Waisenprozess nach hartem Abbruch
B7  geordnetes Abschalten Reihenfolge zwischen Spiel, Browsern und CEF
danach erst
B8  MCEF-Importe raus
B9  MCEF entfernen
```

Vor B8 gehört die Handprüfung in Monaco gefahren — solange sie fehlt, steht
der Nachweis auf Zahlen allein.
