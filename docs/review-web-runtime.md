# Web-Runtime, CEF und der Minecraft-Hintergrund

**Auftrag vom 30.08.:** Die Rendering-Frage getrennt von der Sprachdienst-Frage
entscheiden — und ausdrücklich mit der Möglichkeit, dass CEF falsch ist.

Alles hier ist entweder aus dem Quelltext der jeweiligen Primärquelle zitiert
oder über eine API abgefragt. Was nicht gemessen ist, steht in § 9.

**Gegengelesen** von GPT-5 über Codex mit dem Auftrag, die These anzugreifen.
An drei Stellen hat es die Empfehlung verschärft; das steht drin.

---

## 1. Die drei Messungen, die alles andere entscheiden

### 1.1 `backdrop-filter` über transparentem CEF filtert nichts

Deine Vermutung stimmt, und sie steht so in der Spezifikation. CSS Filter
Effects 2 definiert das *Backdrop Root Image* als

> „all content, in painting order, between (and including) the ancestor
> Backdrop Root element and element E […] flattened into a 2D screen-space
> buffer"

Der äußerste Backdrop Root ist das **Document Root**. Was außerhalb des
Dokuments liegt — der Minecraft-Framebuffer unter einer transparenten
Browserfläche —, ist per Definition nie Teil davon. Chromium filtert dort
Leere.

**Damit ist Variante 1 in ihrer ursprünglichen Form tot**, unabhängig von
jeder GPU-Frage.

### 1.2 CEF kann Texturen herausgeben, nicht hereinnehmen

Aus `cef_render_handler.h`:

> „Called when an element has been rendered to the shared texture handle. […]
> on Windows it is a HANDLE to a texture that can be opened with D3D11
> `OpenSharedResource1`, on macOS it is an IOSurface pointer that can be
> opened with Metal or OpenGL, and on Linux it contains several planes, each
> with an fd to the underlying system native buffer."

Der beschleunigte Pfad existiert also auf allen drei Plattformen. **Aber er
ist einseitig: CEF → Client.** Es gibt keine API, eine fremde GPU-Textur als
Seiteninhalt in Chromium hineinzugeben. Auch nicht über WebGPU:
`importExternalTexture()` nimmt `HTMLVideoElement` oder `VideoFrame`, kein
natives Handle.

Und selbst der Ausgabepfad ist kein Zero-Copy-Traum. Derselbe Header:

> „The handle's resource **cannot be cached and cannot be accessed outside of
> this callback**. It should be reopened each time this callback is executed
> and **the contents should be copied to a texture owned by the client
> application**."

Eine GPU-zu-GPU-Kopie pro Frame — deutlich besser als ein CPU-Umweg, aber
Arbeit.

### 1.3 JCEF hat diesen Pfad nicht

Das ist der Befund, der die Bewertung von CEF in Minecraft trägt.
`java/org/cef/handler/CefRenderHandler.java` kennt genau eine Malmethode:

```java
public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
        ByteBuffer buffer, int width, int height);
```

Ein `ByteBuffer`. Null Treffer für `accelerated` oder `shared_texture` im
gesamten OSR-Pfad. **MCEF und MCEF Modern nutzen genau diese Methode** —
nachgesehen in `CustomCefBrowserOsr.java`, Zeile 105.

Der Weg jedes Bildes ist damit: Chromium rendert auf der GPU → Readback in den
Hauptspeicher → `ByteBuffer` nach Java → `glTexSubImage2D` zurück auf die GPU.

| | pro Frame | 30 fps | 60 fps |
|---|---|---|---|
| 1080p BGRA | 8,29 MB | 249 MB/s | 498 MB/s |
| 4K BGRA | 33,18 MB | 995 MB/s | 1,99 GB/s |

Mit Readback **und** Upload ungefähr das Doppelte. Dirty-Rects senken das bei
einem tippenden Editor stark; bei animierter UI oder Videohintergrund nicht.

**Wichtig zur Einordnung** — hier hat der Gegenleser meine erste Formulierung
zu Recht korrigiert: Der Engpass ist nicht „JNI". Der `ByteBuffer` wird
durchgereicht, nicht kopiert. Teuer sind der GPU→CPU-Readback (er
synchronisiert die Pipeline) und der Upload zurück.

---

## 2. Der Stand der Minecraft-CEF-Projekte, abgefragt am 30.08.

| Projekt | Sterne | letzter Push | Lizenz | 1.21.1 | 26.x | Rendering |
|---|---|---|---|---|---|---|
| **MCEF** (CinemaMod) | — | 1.21.4 im Jan. 2025 | LGPL-2.1 | ✅ 2.1.6 | ❌ | JCEF-Bitmap |
| **MCEF Modern** (DimasKama) | 8 | Juni 2026 | LGPL-2.1 | prüfen | ❌ | JCEF-Bitmap |
| **HydroChrome** | — | — | — | — | — | **nicht auffindbar** |

706 727 Downloads für MCEF sagen: Der Weg funktioniert und wird benutzt.
Acht Sterne für den gepflegtesten Fork sagen: Es gibt niemanden, auf den man
sich verlassen kann.

**HydroChrome konnte ich weder auf GitHub noch auf Modrinth finden.** Wenn du
eine Quelle hast, sieh sie dir an — meine Bewertung schließt es nicht ein.

**MCEF gibt es für 26.x nicht.** Wer die Web-Runtime will, bleibt vorerst auf
1.21.x. Das ist die zweite Hälfte des Befunds aus der ersten Review.

---

## 3. Die Backdrop-Frage: keine deiner vier Varianten, sondern die fünfte

Deine Varianten, kurz bewertet:

- **A (Textur in Chromium einspeisen)** — technisch nicht möglich (§ 1.2). Nur
  mit einem Chromium-Fork auf Blink/cc/Skia-Ebene, der bei jedem
  Chromium-Update neu gemerged werden müsste. **Nein.**
- **B (Frame als Canvas/WebGL/Video)** — möglich, und die Grundlage der
  Empfehlung, aber nicht als 60-fps-Livestream: Das wären die Bandbreiten aus
  § 1.3 zusätzlich in die *andere* Richtung.
- **C (Blur in Minecraft, Regionen melden)** — machbar, aber es ist ein
  **Nachbau, kein `backdrop-filter`**. Der Gegenleser formuliert es schärfer,
  als ich es zuerst hatte, und er hat recht: Das funktioniert nur unter harten
  Regeln — achsenparallele Rechtecke, feste Eckradien, keine Rotation, keine
  `clip-path`, keine überlappenden Backdrops. Scrollen und Animationen
  brauchen Geometrie-Updates in jedem Frame, und ein Frame Versatz sind bei
  60 fps 16,7 ms sichtbarer Kantenriss. Für drei feste HUD-Panels tragbar,
  als Semantik einer Web-Runtime nicht.
- **D (eigener Compositor)** — dasselbe Problem wie C, nur teurer: Du baust
  Schritt für Schritt einen Browser-Compositor nach.

### Die Empfehlung: **E — das Minecraft-Bild wird DOM-Inhalt**

Der Punkt, den alle vier Varianten übersehen: `backdrop-filter` scheitert
nicht an Minecraft, sondern daran, dass das Bild **außerhalb des Dokuments**
liegt. Legt man es *hinein*, funktioniert der Filter spezifikationskonform —
ohne Nachbau, ohne Regionenprotokoll, ohne Synchronisationsproblem:

```html
<img id="backdrop" src="mc://frame">   <!-- ganz unten, bildschirmfüllend -->
<div class="panel">…</div>             <!-- backdrop-filter: blur(16px) -->
```

Das Bild ist jetzt Teil des Backdrop Root Image. `blur`, `saturate`,
`opacity`, Masken, abgerundete Ecken, überlappende Panels, Animationen — alles
funktioniert, weil Chromium seinen eigenen Compositor benutzt. **Kein
proprietärer Effekt-Nachbau, echtes CSS.**

**Was es kostet:** Das Bild muss in die Seite. Über einen CEF-Scheme-Handler
(`mc://frame` liefert JPEG- oder PNG-Bytes) oder als `ImageBitmap` über die
Brücke.

**Und hier ist der eigentliche Trick: Die Bildrate des Hintergrunds ist nicht
die Bildrate der UI.** Wer im Editor tippt, bewegt die Kamera nicht. Zwei bis
fünf Aktualisierungen je Sekunde reichen — bei 5 fps und 1080p sind das
41 MB/s vor Kompression, mit JPEG ein Bruchteil davon. Bei einem Screen, der
das Spiel ohnehin überdeckt, genügt oft ein einziges Standbild beim Öffnen
plus eine Auffrischung, wenn sich die Kamera bewegt hat.

**Grenze, ehrlich benannt:** Das ist kein Live-Hintergrund. Wer eine
durchsichtige HUD-Leiste über bewegtem Spiel will, sieht einen
hinterherhinkenden Hintergrund. Für Vollbild-Oberflächen — IDE, Terminal,
Dashboard, Computer-Desktop — ist das kein Problem, sondern das normale
Verhalten eines Fensters mit Milchglas.

---

## 4. Architekturentscheidung: **D — Hybrid**, mit klarer Grenze

Nicht „CEF statt nativ", sondern **zwei Flächen mit verschiedenen Aufgaben**:

| Fläche | Technik | Warum |
|---|---|---|
| **IDE, Terminal, Computer-Desktop** — Vollbild-Screens | **CEF/OSR + Monaco** | Genau hier zahlt Web-Technik: Keybindings, Themes, Multi-Cursor, IME, Clipboard, Command Palette. Das sind die Punkte, an denen der native Editor prinzipiell nicht mithält (siehe erste Review). |
| **Maschinen-Fenster, Anzeigetafeln, In-World-Displays** | **nativ, wie heute** | Sie sind klein, es gibt viele davon gleichzeitig, sie brauchen kein DOM — und jeder Browser dafür kostet einen Prozess. |

**Warum nicht alles in CEF:** Ein Browser je Maschine ist die Architektur, vor
der du im Performance-Abschnitt selbst warnst. Bei zwanzig Anzeigetafeln in
einer Basis sind das zwanzig Chromium-Renderer plus zwanzigmal die Bandbreite
aus § 1.3. Der native Weg kostet dort fast nichts und ist schon gebaut.

**Warum nicht alles nativ:** Weil dein Produktziel — das Gefühl einer echten
IDE — nativ nicht erreichbar ist. IME wird nie gut, Themes und Keymaps wären
Eigenbau, und die Anforderungsliste hört nicht auf.

**Der Switching Cost, ohne Sunk-Cost-Argument:** Der native Editor
(8 300 Zeilen) bleibt vorerst und wird zum Fallback — für Server ohne
CEF-Laufzeit, für Nutzer mit Problemen, und als Vergleichsmaß. Er ist nicht
verloren, er wird zur zweiten Reihe. Die 6 313 Zeilen Sprachlogik überleben
ohnehin jede Wahl; sie sind der Grund, warum die Sprachdienst-Grenze aus der
ersten Review **vor** dem Rendering-Umbau kommt.

---

## 5. Zielarchitektur

```
Minecraft Renderloop (Render-Thread)
  │
  ├─ Welt zeichnen ─────────────────────────────► Framebuffer
  │                                                   │
  │                                       alle N Ticks: Frame greifen,
  │                                       verkleinern, JPEG kodieren
  │                                                   │
  │                                                   ▼
  │                                          Frame-Cache (Java)
  │                                                   │
  │  ┌────────────────────────────────────────────────┘
  │  │  Scheme-Handler mc://frame  (CEF-IO-Thread)
  │  ▼
  │ ┌──────────────────── Web-Runtime (Java) ─────────────────────┐
  │ │  BrowserManager      Scheduler        TexturePool           │
  │ │   ├ aktiv             ├ sichtbar → 30 fps                   │
  │ │   ├ pausiert          ├ verdeckt → 0 fps (WasHidden)        │
  │ │   └ Lebenszyklus      └ In-World → nach Entfernung          │
  │ └────────────┬─────────────────────────────┬─────────────────┘
  │              │ JCEF (onPaint, ByteBuffer)  │ Brücke (JSON)
  │              ▼                             ▼
  │      ┌───────────────┐            ┌──────────────────┐
  │      │ CEF / Chromium│            │ Service-Registry │
  │      │  OSR, Alpha   │            │  Capabilities    │
  │      └───────┬───────┘            └────────┬─────────┘
  │              │ BGRA                        │
  │              ▼                             ▼
  │      glTexSubImage2D            Java Language Service
  │              │                  (Lexer/Parser/Checks)
  │              ▼                             ▲
  └────► GL-Textur ──► GUI-Ebene ODER Blockfläche
                                                │
                          Monaco ◄── TS-Adapter ┘
```

**Threading:** CEF läuft mit `external_begin_frame_enabled` und wird vom
Minecraft-Renderloop getaktet — sonst laufen zwei Uhren gegeneinander. `onPaint`
kommt auf dem CEF-UI-Thread; der `ByteBuffer` wird in eine Warteschlange
gelegt und erst im Render-Thread hochgeladen. Nie `glTexSubImage2D` außerhalb
des Render-Threads.

**Texturen:** Ein Pool je Größe, Wiederverwendung beim Schließen. Dirty-Rects
aus `onPaint` werden respektiert — bei einem tippenden Editor ist das der
Unterschied zwischen 8 MB und 40 KB pro Frame.

**Lebenszyklus:** Browser werden erst beim ersten Öffnen erzeugt (lazy),
bei Verdeckung über `wasHidden(true)` gedrosselt, nach einer Zeitspanne
geschlossen. In-World-Displays bekommen Sichtbarkeit und Entfernung als
Eingabe für die Bildrate.

---

## 6. Die Java-↔-JavaScript-Grenze

Dein Ansatz ist richtig, und der wichtige Teil ist die Umkehrung der
Blickrichtung: nicht „JS ruft Java auf", sondern **„JS fragt nach einer
Fähigkeit, die es haben darf"**.

```javascript
const machine = await minecraft.services.open("machine", "reactor");
const state   = await machine.getState();
```

- **Ein Kanal, kein HTTP-Server auf localhost.** Ressourcen kommen über
  eigene Schemata: `mod://factorynetwork/ide/index.html` liest direkt aus dem
  Mod-Jar, `mc://frame` liefert den Hintergrund. Ein lokaler Port wäre eine
  offene Tür für alles, was auf dem Rechner läuft.
- **Origin je Mod.** Jede Mod bekommt ihr eigenes `mod://<id>/` als Origin;
  die Same-Origin-Policy von Chromium erledigt die Trennung dann von selbst.
- **Fähigkeiten statt Reflexion.** Eine Registry bildet Namen auf Dienste ab.
  Wer nicht registriert ist, existiert für die Seite nicht — es gibt keinen
  Weg von JS zu einer beliebigen Java-Klasse.
- **Binärdaten** über `ArrayBuffer` mit Längenpräfix, nicht Base64 in JSON.

**Für die Sprachintelligenz:** Monacos Provider-APIs direkt zu bedienen wäre
der schnellste Weg, aber es wäre der vierte Abnehmer, der seine eigene
Übersetzung mitbringt. Richtig ist der Weg über **dasselbe LSP-förmige
Protokoll**, das die Sprachdienst-Grenze aus der ersten Review ohnehin
bekommt — Monaco spricht es über einen dünnen TypeScript-Adapter, VS Code
über einen echten LSP-Client, der native Editor direkt in Java.

---

## 7. Themes und Keybindings

**Themes:** VS-Code-Themes sind JSON mit TextMate-Scopes und einer
`colors`-Tabelle. Monaco kann sie nach einer Umformung direkt laden; die
Scope-Namen müssen zu deiner Grammatik passen — die TextMate-Grammatik für
Manifold existiert bereits in `editor/vscode/syntaxes/`. **Das ist der
billigste große Gewinn des ganzen Wechsels:** ein Import statt eines
Farbsystems.

**Keybindings:** Monaco bringt sein eigenes Keybinding-System mit, Vim- und
Emacs-Modi gibt es als fertige Erweiterungen. Das Problem ist nicht Monaco,
sondern **die Fokusgrenze zu Minecraft**.

Vorschlag für das Fokusmodell:

| Zustand | Wer bekommt Tasten |
|---|---|
| Screen offen, Browser fokussiert | **alles an CEF**, außer einer einzigen Rückholtaste |
| Screen offen, Browser nicht fokussiert | Minecraft |
| In-World-Display, Spieler interagiert | CEF, solange er zielt |

Die Rückholtaste darf **nicht Escape** sein — Escape gehört Monaco (Command
Palette schließen, Auswahl aufheben). Vorschlag: eine dedizierte Taste, im
Fenster sichtbar angeschrieben. Alles andere — F-Tasten, Strg, Alt, Chat,
Inventar — geht an den Browser, solange er den Fokus hat. Ein Editor, in dem
`E` das Inventar öffnet, ist unbenutzbar.

---

## 8. Die Library-Frage: **noch nicht**

Hier ist der Gegenleser am schärfsten, und er hat recht:

> „Du hast noch nicht einmal die erste harte First-Party-App ausgeliefert. […]
> Sobald du ‚auch für andere Mods' sagst, frierst du APIs zu früh ein und
> vererbst deinen technischen Kompromiss."

Die Probleme, die eine allgemeine Runtime abstrahieren müsste — Eingabe, IME,
Clipboard, Fokus, Popup-Menüs, DevTools, Texturlebensdauer, Weltprojektion,
Absturzbehandlung — sind bei dir **noch nicht einmal einmal gelöst**. Man kann
nicht abstrahieren, was man noch nie gebaut hat.

**Der richtige Weg:** Die Runtime von Anfang an *sauber geschnitten* bauen —
eigenes Gradle-Modul, keine Abhängigkeit auf `factorynetwork`-Klassen, API
über Schnittstellen. Dann ist die Trennung später eine Frage der
Veröffentlichung und nicht des Umbaus. Aber **veröffentlichen erst, wenn die
eigene IDE läuft und die API einen Winter überstanden hat.**

Was in einen Kern gehörte, wenn es je einer wird: Browser-Lebenszyklus,
OSR→Textur, Eingabeweiterleitung, Schemata für Mod-Ressourcen, die
Brücke, Fokusverwaltung. Was **nicht** hineingehört: Monaco, deine Sprache,
In-World-Blöcke, UI-Bausteine, das Backdrop-Verfahren. Das sind Module.

---

## 9. Risiken

1. **JCEFs CPU-Pfad ist die Decke.** Alles, was animiert, kostet die
   Bandbreiten aus § 1.3. Ein Prototyp, der mit einer statischen Seite gut
   aussieht, sagt nichts über eine UI mit Übergängen.
2. **Der GPU-Pfad ist ein eigenes Produkt.** JCEF zu patchen reicht nicht: Man
   müsste pro Plattform D3D11/IOSurface/DMA-BUF nach OpenGL interoperieren,
   pro Frame kopieren und die Synchronisation stabil bekommen. Wer das anfängt,
   besitzt danach eine native Rendering-Schicht.
3. **Die Laufzeitverteilung.** MCEF lädt Chromium beim ersten Start nach.
   In einem Modpack heißt das: erster Start dauert lange, hinter Firmen-Proxys
   scheitert er, und Server-Betreiber fragen, warum ihr Client Daten lädt.
   Die Alternative — mitliefern — sind dreistellige Megabyte je Plattform.
4. **Die Abhängigkeit ist dünn.** Acht Sterne beim gepflegtesten Fork. Wenn
   der Betreuer aufhört, erbst du JCEF-Wartung samt Chromium-Aktualisierungen.
5. **Kein CEF für 26.x.** Die Web-Runtime bindet dich an 1.21.x, solange das
   so bleibt.
6. **Ein Frame Versatz** zwischen Browser und Welt ist bei bewegter Kamera
   sichtbar. Das trifft die Backdrop-Empfehlung genauso wie jede andere.
7. **Absturzverhalten.** Chromium-Renderer stürzen ab. Ohne Wiederanlauf
   friert die Oberfläche mitten im Spiel ein.
8. **Der teuerste Fehler** (Formulierung des Gegenlesers, und ich teile sie):
   „Moderne Ingame-IDE", „allgemeine Web-Runtime" und „Web-Oberflächen auf
   Blockflächen" als *ein* Projekt zu behandeln. Es sind drei Produkte. Wer
   sie koppelt, optimiert die Runtime für eine Zukunft, die nie kommt, und die
   IDE erscheint spät oder nie.

---

## 10. Wenn ich dieses Projekt selbst bauen müsste

**würde ich in dieser Reihenfolge vorgehen:**

1. **Zuerst die Sprachdienst-Grenze** (8–14 PT, erste Review). Sie zahlt sich
   in jedem Szenario aus und ist Voraussetzung dafür, dass Monaco überhaupt
   etwas kann. Ohne sie baut man die Duplikation ein drittes Mal.

2. **Dann ein Spike von zwei Tagen, kein Beschluss.** MCEF Modern gegen
   1.21.1 gebaut, ein Vollbild-Screen, Monaco geladen, `mc://frame` als
   Hintergrund, `backdrop-filter` darauf. Gemessen: Bilder je Sekunde mit und
   ohne offene Oberfläche, Speicher, Startzeit des ersten Browsers, und ob
   das Glass-Bild wirklich so aussieht, wie du es dir vorstellst. **Das ist
   die einzige Zahl, die diese Entscheidung tragen kann** — meine ist es
   nicht.

3. **Erst danach entscheiden**, ob die IDE nach CEF wandert. Mit einem
   Abbruchkriterium, das vorher feststeht: Wenn der offene Editor mehr als
   ein Fünftel der Bildrate kostet oder der erste Browser länger als drei
   Sekunden braucht, ist der Weg für dieses Projekt zu teuer.

4. **In-World-Displays bleiben nativ.** Nicht aus Sparsamkeit, sondern weil
   sie kein DOM brauchen und weil zwanzig Chromium-Prozesse in einer Basis
   keine Architektur sind, sondern ein Ausfall.

5. **Die Library kommt zuletzt oder nie.** Sauber geschnitten bauen,
   veröffentlichen erst nach dem ersten Winter.

**Und was ich nicht täte:** Den Minecraft-Framebuffer in Chromium einspeisen
wollen — das geht nicht. Blur-Regionen an Minecraft melden und einen
Browser-Compositor nachbauen — das wird nie gut und ist kein `backdrop-filter`,
sondern ein Effekt, der so heißt. Eine allgemeine Web-Runtime versprechen,
bevor die erste eigene Oberfläche läuft.

**Die kürzeste Fassung:** CEF ja, aber nur für die eine große Fläche. Das
Minecraft-Bild gehört *in* die Seite, nicht *unter* sie. Und die Plattform
kommt nach dem Produkt, nicht davor.
