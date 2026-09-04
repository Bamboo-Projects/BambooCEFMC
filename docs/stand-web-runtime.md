# Web-Runtime: Stand nach Schritt 1 bis 3

**30.08., nachts.** Schritte ① bis ③ sind fertig und geprüft. Dazu eine
Messung, die vorher gefehlt hat: **MCEF läuft in diesem Projekt.**

362 GameTests grün, alle Unit-Tests grün, Baum sauber.

---

## Die Messung: MCEF kommt hoch

Client gestartet, Protokoll ausgewertet. Aus `run/logs/latest.log`:

```
23:11:30.944  java-cef commit: a78e832f9f13c2c688caea3d04d8b84fcd238d94
23:11:30.988  …/windows_amd64.tar.gz.sha256 -> build/mcef-libraries/…
23:11:32.721  WARN  Failed to download JCEF hash.
23:11:32.723  …/windows_amd64.tar.gz -> build/mcef-libraries/…
23:11:47.133  Initializing CEF on windows_amd64...
23:11:47.538  Chromium Embedded Framework initialized
```

| Was | Wert |
|---|---|
| Laufzeit auf der Platte | **269 MB** entpackt (`libcef.dll`, `jcef.dll`, `jcef_helper.exe`, Locales, .pak-Dateien) |
| Erstinstallation gesamt | **~16 s** (Download plus Entpacken, an schneller Leitung) |
| CEF-Initialisierung selbst | **405 ms** |
| Ort | `build/mcef-libraries/` in der Entwicklungsumgebung; im Spiel unter dem Spielordner |

**Ein Sicherheitsbefund, ungefragt:** Der Hash-Download schlug fehl
(`Failed to download JCEF hash`), und MCEF lud trotzdem weiter. Die abgelegte
`.sha256` enthält keinen Server-Hash, sondern lokale PowerShell-Ausgabe
(`Algorithm  Hash  Path`). **269 MB ausführbarer Code kamen ohne
Integritätsprüfung von einem fremden Server und wurden entpackt.** Das
funktioniert und ist trotzdem etwas, das man wissen sollte — erst recht, wenn
aus der Runtime einmal eine Mod für andere werden soll.

---

## Was gebaut ist

```
dev.devpanda.factorynetwork.web
  ├─ WebRuntime            Zustand, Start, Herunterfahren — kennt MCEF nicht
  ├─ WebRuntimeState       fünf Zustände statt eines boolean
  ├─ WebRuntimeStatus      Zustand plus Begründung
  ├─ WebRuntimeUnavailable ein vorhergesehener Grund
  ├─ WebBackend            was die Runtime von ihrem Unterbau braucht
  ├─ WebSupport            der einzige Ort, der weiß, dass es MCEF ist
  ├─ BrowserVisibility     Sichtbarkeit → Zielbildrate
  ├─ FramePacer            wann ein Bild fällig ist (reine Zeitrechnung)
  ├─ frame/
  │   ├─ BrowserFrame        Maß und Änderung, ohne Puffertyp
  │   ├─ CpuBrowserFrame     die Fassung, die kopiert und besitzt
  │   ├─ DirtyRegion         geänderter Bereich
  │   ├─ FrameSlot           ein Postfach, das neueste gewinnt
  │   └─ BrowserFrameSource  woher Bilder kommen
  ├─ texture/
  │   └─ BrowserTextureBackend   wohin sie gehen
  └─ mcef/
      └─ McefBackend       die einzige Klasse, die MCEF anfasst
```

**Abhängigkeitsrichtung:** Die Mod darf die Runtime benutzen, die Runtime die
Mod nicht. `FnClient` ruft `WebRuntime.shutdown()` beim Verlassen der Welt —
das ist die einzige Verbindung, und sie zeigt nach innen.

**Vorgezogen und offengelegt:** `BrowserVisibility` und `FramePacer` gehören
eigentlich zu Schritt ④. Sie sind hier, weil sie reine Rechnung ohne Minecraft
sind — also genau die Sorte Teil, die du nach ③ prüfbar haben wolltest. Der
Browser-Manager selbst ist **nicht** gebaut.

---

## Der Grenz-Prüflauf

`PackageBoundaryTest` prüft vier Dinge:

1. Das Paket existiert — sonst wäre der Lauf grün und wertlos.
2. Kein Import aus `dev.devpanda.factorynetwork.*` außer `…web.*`.
3. Auch kein voll ausgeschriebener Name mitten im Code, am Import vorbei.
4. Kein `net.minecraft.world.*`, kein `net.minecraft.server.*` — was die
   Runtime von Minecraft braucht, ist der Client, nicht die Welt.

**Keine Ausnahmen.** Auch nicht für den Modnamen: Was die Runtime von ihrem
Wirt wissen muss, bekommt sie übergeben. `util` enthält nur `NameDistance` und
wurde nicht gebraucht — es gab also keinen Fall, in dem eine Ausnahme nötig
gewesen wäre.

---

## Das Frame-Lifetime-Modell — geprüft, nicht geraten

Deine Frage war die wichtigste, und die Antwort steht im Quelltext von JCEF.
`native/render_handler.cpp:265`:

```cpp
env->NewDirectByteBuffer(const_cast<void*>(buffer), width * height * 4)
```

Der Java-Puffer zeigt **ohne Kopie direkt auf Chromiums Speicher**. CEFs
eigener Vertrag (`cef_render_handler.h`) sagt dazu, dass der Puffer dem
Browser gehört. Nach der Rückkehr aus `onPaint` darf Chromium ihn
wiederverwenden.

**Daraus folgen drei Dinge:**

1. Ein Bild, das den Aufruf überlebt, **muss kopieren**. Das ist keine
   Optimierungsfrage.
2. Wer **im** Aufruf hochlädt, braucht keine Kopie. Genau das tut MCEF heute —
   und deshalb ist `CpuBrowserFrame` für diesen Weg nicht nötig, sondern für
   den, bei dem wir entkoppeln.
3. `CpuBrowserFrame` kopiert **das ganze Bild**, obwohl nur Teile schmutzig
   sind. Anders wäre es falsch: Kopierte man nur die geänderten Bereiche,
   müsste der Zielpuffer den Rest schon tragen — und sobald zwei Bilder
   abwechselnd umlaufen, trägt er den Stand von vorletzter Runde. Die
   Dirty-Rects werden trotzdem mitgeführt; beim **Hochladen** sind sie
   richtig, denn dort steht genau eine Textur auf der anderen Seite.

---

## Eine Annahme, die sich als falsch erwiesen hat

**`FrameSlot` löst heute kein Thread-Problem.** MCEF hängt per Mixin in
`GameRenderer.render` und ruft dort `N_DoMessageLoopWork()` — Chromiums
Nachrichtenschleife läuft also **im Minecraft-Render-Thread**. `onPaint` kommt
synchron dort an; GL-Aufrufe im Aufruf sind gültig, und ein Erzeuger-
Verbraucher-Paar über Threadgrenzen existiert gar nicht.

Das Postfach bleibt trotzdem, und die Begründung steht in seiner Doku: Es wird
gebraucht, sobald wir Chromium mit `external_begin_frame` an unseren eigenen
Takt hängen, und beim GPU-Pfad, bei dem der Aufruf nicht dort ankommt, wo die
Textur liegt. Heute ist es Vorbereitung, kein Fix — und es kostet einen
unbestrittenen Monitor je Bild.

---

## Ein Fehler, den ein Prüflauf gefunden hat

`WebRuntime.shutdown()` kehrte bei fehlendem Unterbau sofort zurück:

```java
if (backend == null) {
    return;          // ← und der Zustand blieb stehen
}
```

Nach einem gescheiterten Start blieb der Grund damit **für immer** stehen —
ein zweiter Versuch wäre unmöglich gewesen. Jetzt wird erst zurückgesetzt,
dann geschlossen.

Der Prüflauf hat das gefunden, nicht ich beim Lesen.

---

## Tests

| Datei | prüft |
|---|---|
| `PackageBoundaryTest` | die Grenze, in vier Richtungen |
| `WebRuntimeTest` | alle Zustände, auch `NoClassDefFoundError`; dass nichts wirft; zweiter Versuch nur wo sinnvoll; Herunterfahren mit werfendem `close` |
| `WebSupportTest` | **die Lage des Spielers ohne MCEF** — im Prüfstand liegt die Mod nicht auf dem Klassenpfad, `compileOnly` reicht zum Übersetzen und nicht zum Laufen |
| `FrameSlotTest` | neuestes gewinnt, Besitzübergabe, Verdrängungszähler, Schließen, Nebenläufigkeit über 2000 Runden |
| `CpuBrowserFrameTest` | dass das Bild überlebt, wenn die Quelle überschrieben wird; dass die Quelle unangetastet bleibt; Wachsen, Wiederverwenden, Schreibschutz |
| `FramePacerTest` | Takt, Aufwachen, kein Nachholen |

---

## Offene Risiken

1. **Der Texturupload ist ungebaut und ungeprüft.** Das Projekt hatte bis
   heute keine einzige dynamische Textur — kein `NativeImage`, keine
   `DynamicTexture`. Das ist der nächste Spike und die Stelle, an der es
   schiefgehen kann.
2. **Ich weiß nicht, was ein offener Browser an Bildrate kostet.** Dafür
   braucht es einen Screen, und den gibt es absichtlich noch nicht.
3. **Der Download ist ungeprüft** (siehe oben). Bei einer eigenen Verteilung
   müsste man das lösen — eigener Spiegel mit Hash, oder Prüfung nachrüsten.
4. **MCEF gehört uns nicht.** `McefBackend.close()` tut absichtlich nichts:
   MCEF hängt seinen eigenen Abschalthaken ein, und andere Mods im selben Pack
   benutzen es mit. Wer dort `MCEF.shutdown()` ruft, nimmt ihnen den Browser.
5. **Die Zustände `NOT_DOWNLOADED` und `FAILED` sind heute nicht sicher zu
   trennen.** MCEF meldet mit einem einzigen `boolean`, ob es hochkam, und
   nennt keinen Grund. Der Verdacht steht im Text, geraten wird nicht.
6. **MCEF registriert bereits ein `mod://`-Schema** (`ModScheme`). Für Phase 2
   heißt das: Vielleicht ist die Arbeit schon getan, vielleicht ist sie im Weg.
   Das ist zu prüfen, bevor wir ein eigenes Schema bauen.

---

## Was als Nächstes ansteht

Der **Texturupload als eigener Spike**, wie du es wolltest:
`onPaint` → `DynamicTexture` → `blit`, zuerst voller Upload, Dirty-Rects
danach. Dann erst ein Screen, dann die Messung.

Zwei Fragen, die dabei zuerst zu beantworten sind:

- **MCEFs `MCEFBrowser` benutzen oder einen eigenen `CefRenderHandler`?**
  MCEF bringt Renderer, Eingabe-Abbildung und Cursor mit — das ist viel
  geschenkte Arbeit. Es bindet uns aber an seinen Renderpfad, und der ist
  genau der, den wir später austauschen wollen.
- **Im Aufruf hochladen oder über das Postfach?** Im Aufruf ist heute
  richtig und braucht keine Kopie. Über das Postfach kostet eine Kopie und
  hält den Weg zum GPU-Pfad offen.

Beides sind Entscheidungen für den nächsten Spike, keine Nebensachen.

---

## Nachtrag vom 31.08.

Der Texturupload-Spike ist gelaufen und beantwortet die offenen Risiken 1 und 2
aus der Liste oben mit Zahlen. Beide Fragen dieses Abschnitts sind entschieden:
**eigener Renderpfad über `CefBrowserOsr`**, und **im Aufruf hochladen**. Was
dabei herauskam — auch das vormultiplizierte Alpha und die feste Bildrate von
CEF — steht in [stand-texturupload.md](stand-texturupload.md).
