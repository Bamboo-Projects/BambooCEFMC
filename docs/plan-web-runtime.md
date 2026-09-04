# Web-Runtime — Umsetzungsplan, Schritt 1

**Entschieden am 30.08.:** Eine eigene CEF-basierte Web-Runtime für 1.21.1 +
NeoForge. Die IDE ist ihr erster Abnehmer, nicht ihr Zweck.

Dieses Dokument analysiert den Bestand und leitet den ersten Spike ab. Es
baut nichts.

---

## 0. Eine Annahme aus dem Auftrag stimmt nicht

> „Bitte prüfe kritisch bestehende Projekte wie MCEF, **MCEF Modern**,
> HydroChrome"

**MCEF Modern (DimasKama) ist für uns nicht verwendbar.** Aus seiner
`gradle.properties`:

```
minecraft_version=26.2
loader_version=0.19.3
fabric_version=0.152.2+26.2
loom_version=1.17-SNAPSHOT
```

Es ist eine **Fabric**-Mod für **Minecraft 26.2**. Kein NeoForge, keine
1.21.x. Ein Rückport wäre ein eigenes Projekt.

**HydroChrome finde ich nicht** — weder auf GitHub (einziger Namenstreffer ist
ein unbezogenes Repo ohne Sterne von 2024) noch auf Modrinth (0 Treffer). Wenn
du eine Quelle hast, sieh sie dir an; meine Bewertung schließt es nicht ein.

**Und eine Korrektur an mir selbst:** In der vorigen Review habe ich MCEF als
„dünn gepflegt" mit „acht Sternen beim gepflegtesten Fork" dargestellt. Das
war falsch gemessen — ich hatte nur die Forks gezählt. Das Original
`CinemaMod/mcef` hat **149 Sterne**, Branches bis 1.21.5 und einen eigenen
`1.21.1`-Zweig mit NeoForge-Unterstützung. Letzter Push: 11. August 2025.

---

## 1. Der Bestand, und was davon zählt

| Was | Wo | Bedeutung für die Runtime |
|---|---|---|
| Single-Modul-Gradle, ModDevGradle 2.0.144 | `build.gradle` | Multi-Modul wäre neu; siehe § 2 |
| NeoForge 21.1.248, Java 21, Parchment | `gradle.properties` | passt zu MCEF 1.21.1 |
| Client-Einstieg mit `@SubscribeEvent` | `client/FnClient.java` | dort wird die Runtime hochgefahren |
| Screens als `AbstractContainerScreen` / `Screen` | `client/screen/` | Muster für den Browser-Screen |
| Eingabe über `keyPressed`, `charTyped`, `mouseClicked`, `mouseScrolled` | `CodeScreen:445-555` | genau die Punkte, die an CEF gehen |
| BlockEntityRenderer für Blockflächen | `client/render/DisplayRenderer` | Vorlage für In-World-Browser, **später** |
| Sieben optionale Fremdmods, Muster `compileOnly`+`runtimeOnly` | `build.gradle` | MCEF fügt sich exakt so ein |
| **Keine** `DynamicTexture`, **kein** `NativeImage`, **kein** `RenderSystem`-Texturcode | gemessen: 0 Treffer | Das Texturbackend ist komplett neu |

**Der letzte Punkt ist der wichtigste Befund:** Das Projekt hat bisher *keine
einzige* dynamisch erzeugte Textur. Alles ist geladenes PNG oder gezeichnete
Geometrie. Der Upload-Pfad ist also nicht „vorhandene Infrastruktur
wiederverwenden", sondern Neuland — und damit die Stelle, an der der Spike
scheitern kann.

---

## 2. Modulgrenzen: erst Pakete, dann Module

Multi-Modul mit ModDevGradle bedeutet: `neoForge { }`-Konfiguration je Modul,
gemeinsame Runs, geteilte Ressourcen. Das ist machbar, aber es kostet einen
halben Tag Buildarbeit, **bevor die erste Zeile Runtime existiert**.

**Vorschlag: dieselben Grenzen als Pakete, mit einer Regel, die ein Prüflauf
durchsetzt.**

```
dev.devpanda.factorynetwork.web            ← die Runtime
  ├─ WebRuntime.java                       Hochfahren, Herunterfahren, Zustand
  ├─ BrowserManager.java                   Erzeugen, Finden, Schließen
  ├─ BrowserInstance.java                  ein Browser samt Ziel
  ├─ frame/
  │   ├─ BrowserFrame.java                 ein Bild + Dirty-Rects
  │   ├─ BrowserFrameSource.java           woher Bilder kommen
  │   └─ FrameSlot.java                    latest-frame-wins
  ├─ texture/
  │   ├─ BrowserTextureBackend.java        wohin Bilder gehen
  │   └─ GlTextureBackend.java             die CPU-Fassung
  ├─ input/
  │   ├─ FocusOwner.java                   Minecraft oder Browser
  │   └─ InputForwarder.java               Maus, Tasten, Zeichen, Rad
  ├─ scheme/                               Phase 2
  └─ bridge/                               Phase 3

dev.devpanda.factorynetwork.client.screen.web
  └─ BrowserScreen.java                    der erste Abnehmer
```

**Die Regel:** Nichts unter `web` darf etwas aus `lang`, `press`, `network`,
`storage`, `runtime` oder `terminal` importieren. Das ist mit einem
gewöhnlichen Test prüfbar — Quelldateien lesen, Importe prüfen, fertig. Damit
ist die Grenze nicht Absicht, sondern Zusicherung, und der spätere Schnitt in
ein eigenes Modul ist eine Buildfrage statt einer Aufräumaktion.

Der Test kommt **zuerst**, vor der ersten Runtime-Klasse.

---

## 3. Die Basis: MCEF 2.1.6-1.21.1

Es bleibt genau ein Kandidat, und er passt:

- **Multi-Loader**, mit `neoforge/`-Verzeichnis im `1.21.1`-Branch
- Auf Modrinth als `mcef-neoforge-2.1.6-1.21.1.jar`, 706 727 Downloads
- Eigener JCEF-Fork (`CinemaMod/java-cef`) mit fertigen Binärpaketen je
  Plattform
- LGPL-2.1

**Warum nicht JCEF direkt:** Man müsste die Plattform-Binärpakete selbst
bauen, hosten, versionieren und den Ladepfad in Minecrafts Klassenlader
einhängen. Genau diese drei Wochen Arbeit ist MCEF.

**Was MCEF nicht ist:** ein GPU-Pfad. Es nutzt `onPaint(ByteBuffer)`
(nachgesehen in `CustomCefBrowserOsr`). Das ist für den Spike ausdrücklich in
Ordnung — und der Grund für die Kapselung aus § 5.

---

## 4. Abhängigkeiten, Laufzeit, Verteilung, Lizenz

**Gradle:**

```gradle
repositories {
    exclusiveContent {
        forRepository {
            maven { url = 'https://mcef-download.cinemamod.com/repositories/releases' }
        }
        filter { includeGroup 'com.cinemamod' }
    }
}

dependencies {
    compileOnly 'com.cinemamod:mcef:2.1.6-1.21.1'
    runtimeOnly 'com.cinemamod:mcef-neoforge:2.1.6-1.21.1'
}
```

Dazu ein Eintrag in `neoforge.mods.toml` als `optional`, wie bei Jade und JEI.

**Die Laufzeit kommt nicht aus dem Jar.** MCEF lädt sie beim ersten Start:

```java
JAVA_CEF_DOWNLOAD_URL = "${host}/java-cef-builds/${commit}/${platform}.tar.gz"
downloadMirror = "https://mcef-download.cinemamod.com"
```

Abgelegt wird unter `Minecraft.getInstance().gameDirectory`. Der Spiegel ist
über `MCEFSettings` konfigurierbar.

**Daraus folgen drei Dinge, die niemand später überraschen darf:**

1. **Der erste Start dauert.** Wie lange und wie viele MB — das misst der
   Spike (§ 6). Ich habe die Paketgröße nicht gemessen und rate sie nicht.
2. **Ohne Netz gibt es keinen Browser.** Hinter Firmen-Proxys, in Offline-
   Installationen und in strengen Modpack-Umgebungen scheitert der Download.
   Der Client muss ohne CEF **normal weiterlaufen** — das ist eine harte
   Anforderung an den Spike, kein Nice-to-have.
3. **Wir hängen an einem fremden Server.** Fällt `mcef-download.cinemamod.com`
   aus, funktioniert die Runtime nicht mehr. Ein eigener Spiegel ist möglich,
   aber dann hosten wir Chromium-Binärpakete — mit allem, was das an Bandbreite
   und Sorgfaltspflicht bedeutet.

**Lizenz:** MCEF ist LGPL-2.1, unsere Mod MIT. Das ist verträglich, solange
**MCEF eine eigenständige Mod bleibt und nicht in unser Jar geschattet wird**.
`runtimeOnly` statt `implementation`, kein Shadow-Jar für `com.cinemamod`. In
`compileOnly` gegen die API zu übersetzen ist unkritisch. CEF selbst ist BSD,
Chromium eine Sammlung — beides wird nicht von uns verteilt, sondern
nachgeladen.

---

## 5. Die Kapselung, die den GPU-Pfad offenhält

Genau wie im Auftrag skizziert, mit einer Ergänzung:

```java
/** Woher Bilder kommen — heute JCEF, später vielleicht eine GPU-Textur. */
public interface BrowserFrameSource {
    /** Das neueste Bild, oder null, wenn seit dem letzten Abruf keines kam. */
    BrowserFrame poll();
}

/** Wohin sie gehen. */
public interface BrowserTextureBackend {
    void upload(BrowserFrame frame);
    int textureId();
    void close();
}
```

`BrowserFrame` trägt **keinen** `ByteBuffer` in der Signatur, sondern bleibt
abstrakt — sonst ist die GPU-Variante beim ersten Versuch ein Bruch. Der
CPU-Fall ist eine Implementierung davon, die einen Puffer und die Dirty-Rects
hält.

**Die Thread-Regel steht in der Klasse, nicht in der Doku:** `FrameSlot` ist
ein Ein-Element-Postfach mit *latest wins*. Der CEF-Thread legt ab, der
Render-Thread nimmt. Ein voller Slot wird überschrieben, nicht gestaut — genau
wie du es willst. Kein `glTexSubImage2D` außerhalb des Render-Threads; das ist
die Regel, deren Verletzung sonst als sporadischer Absturz zurückkommt.

---

## 6. Der minimale Spike

**Was drin ist:**

1. `WebRuntime` — fährt MCEF hoch, wenn es da ist; meldet sauber, wenn nicht.
   **Minecraft läuft in beiden Fällen normal weiter.**
2. Ein Debug-Befehl oder eine Taste öffnet `BrowserScreen`.
3. Der Screen erzeugt beim ersten Öffnen einen Browser (lazy) und lädt eine
   lokale HTML-Datei aus den Mod-Ressourcen — über `file://` oder `data:`.
   **Noch kein eigenes Schema** (das ist Phase 2).
4. `onPaint` → `FrameSlot` → im Render-Thread → `DynamicTexture` →
   `graphics.blit`.
5. Maus (Bewegung, Klick, Rad), Tasten und Zeichen gehen an CEF, solange der
   Screen offen ist.
6. Größenänderung und Schließen geben alles frei.
7. Transparenz an, damit die Backdrop-Frage später überhaupt prüfbar ist.

**Was ausdrücklich nicht drin ist:** Monaco, Schemata, Brücke, Capabilities,
GPU-Interop, In-World, mehrere Browser, Public API.

**Die Messpunkte, als schlichte Zähler im Log — kein Framework:**

| Messwert | wie |
|---|---|
| Zeit bis MCEF bereit | Zeitstempel um den Start |
| Zeit bis erstes Bild | Zeitstempel Öffnen → erster `onPaint` |
| `onPaint`-Aufrufe je Sekunde | Zähler |
| Bytes je Sekunde | Summe aus Breite×Höhe×4, bzw. Dirty-Fläche |
| Uploads je Sekunde, Dauer | `System.nanoTime` um `glTexSubImage2D` |
| Minecraft-Bildrate | vorhandener Debug-Bildschirm, drei Fälle: ohne Browser, statische Seite, animierte Seite |
| Speicher | `Runtime.totalMemory - freeMemory` vor und nach dem ersten Browser |

**Das Abbruchkriterium steht vor der Messung fest** (aus der vorigen Review):
Kostet der offene Browser mehr als ein Fünftel der Bildrate, oder dauert der
erste Browser über drei Sekunden, ist der Weg für dieses Projekt zu teuer —
dann reden wir über den GPU-Pfad oder über die native Fläche.

---

## 7. Reihenfolge der Arbeit

1. **Der Grenz-Prüflauf** (`web` importiert nichts aus der Mod) — vor allem
   anderen, damit die Regel nie gebrochen wird.
2. **MCEF einbinden**, Client startet mit und ohne die Mod.
3. **`FrameSlot`, `BrowserFrame`, die zwei Schnittstellen** — reine Logik,
   in gewöhnlichen Tests prüfbar (Postfach-Verhalten, Dirty-Rect-Rechnung).
4. **`WebRuntime` + `BrowserManager`** — Start, Fehlerfall, Herunterfahren.
5. **`GlTextureBackend`** — zuerst voller Upload, Dirty-Rects danach.
6. **`BrowserScreen`** — Zeichnen, dann Maus, dann Tastatur.
7. **Messen.**

Schritt 1 bis 3 sind ohne Minecraft prüfbar. Schritt 4 bis 6 nicht — dort
zählt der Client.
