# Flächen in der Welt: die API, und die Tafel darauf umgestellt

Stand 2. September 2026. Schritte 4 und 5a aus [`plan-web-api.md`](plan-web-api.md).
Vorher: [`stand-web-overlay.md`](stand-web-overlay.md).

## In einem Satz

`FnWeb.openInWorld` hängt eine Web-Fläche an Ort, Ausrichtung und Größe in
der Welt; die Tafel-Blöcke zeichnen nichts mehr selbst, sondern öffnen ihre
Fläche über diese Schnittstelle — an denselben Koordinaten wie zuvor.

## Die Schnittstelle

```java
WorldSurface surface = FnWeb.openInWorld(
        SurfaceSpec.of(url, 512, 512).named("Tafel"),
        x, y, z,          // Mittelpunkt in Weltkoordinaten
        facing.toYRot(),  // Gierwinkel, Minecrafts Konvention
        1.0f, 1.0f);      // Breite und Höhe in Blöcken
```

| Stück | Was es ist |
|---|---|
| `WorldSurface` | Ort, Ausrichtung, Größe in Blöcken, Entfernungsgrenze, die Fläche darunter |
| `FnWeb.openInWorld` | der Einstieg |
| `WorldSurfaces` | innen: zeichnet aus der Weltstufe, taktet, räumt ab |

**Gezeichnet aus der Weltstufe nach den durchscheinenden Blöcken.** So bleiben
Wasser und Glas davor, und die Fläche leuchtet nicht durch sie hindurch. Der
Puffer wird selbst abgeschlossen — nichts sagt zu, dass der Weltrenderer
unseren Typ in dieser Stufe noch leert. Beide Seiten werden gezeichnet, voll
beleuchtet; von hinten zeigt die Fläche ihr Spiegelbild wie eine Scheibe.

**Die Entfernung zur Kamera entscheidet den Takt, nicht die Blickrichtung.**
Bis zwölf Blöcke mittlerer Takt, bis `maxDistance` (Vorgabe 32) langsam,
dahinter gar nicht und nicht gezeichnet. Eine Seite hinter dem Spieler läuft
weiter, eine ferne ruht.

**Kein Weltklasse in der Signatur.** Die Kamera kommt als drei Zahlen, nicht
als `Vec3` — der Typ liegt in `net.minecraft.world`, und das darf hier nichts
herein (`PackageBoundaryTest`).

## Schritt 4: belegt mit `-Dfn.world=true`

Eine Fläche drei Blöcke vor dem Spieler, auf der Startseite der Tafel; ihr
Bildzähler läuft im Standbild, die Seite ist also lebendig. Über den ganzen
Lauf `lebt=true`, null ERROR, sauberes Herunterfahren. Das Bild zeigt die
Seite lesbar und frei in der Welt stehend. `/fnweb welt` stellt sie von Hand
hin.

## Schritt 5a: die Tafel auf die API

Nichts wurde entfernt, bevor der neue Weg trug — dieselbe Regel wie bei MCEF.

**Der Renderer des Blocks ist zum Melder geworden.** Er zeichnet nichts mehr;
er sagt nur, dass die Tafel im Bild ist und wie sie steht. Genau das ist die
eine Auskunft, die nur der Renderer eines Blocks hat: Er läuft nur für Blöcke
in Sicht- und Bildschirmreichweite, und aus seinem Ausbleiben schließt
`WebPanels`, dass niemand hinsieht — die Grundlage der Fünf-Sekunden-Regel,
die den Browser wieder zumacht.

**Der Mittelpunkt wird nicht neu hergeleitet, sondern nachgespielt.** Die
Tafel sitzt zwei Pixel dick an der hinteren Kante ihres Blocks. Statt die
Drehung von Hand auszurechnen — das kostete früher zwei Läufe —, schickt
`panelCenter` den Ursprung durch genau die Kette, die der Renderer auf den
Block legte: zur Blockmitte, um die Hochachse gegen die Ausrichtung, sechs
Sechzehntel nach hinten. Was herauskommt, ist der Mittelpunkt, den die
`WorldSurface` braucht. Der Gierwinkel ist `facing.toYRot()`, die Größe ein
Block im Quadrat — dieselbe Drehung und dasselbe Viereck wie im alten
Renderer, also dieselbe Lage.

### Der Vergleich

| | vorher (eigener Renderer) | nachher (über die API) |
|---|---|---|
| Öffnen | `Tafel 412,64,292`, `412,65,292` | dieselben Koordinaten |
| Grenze gleichzeitig | `webPanels` aus der Konfiguration | unverändert |
| Zumachen nach Blindheit | 5 s | unverändert |
| Kennung je Seite | `#fn-panel=` | unverändert |
| ERROR im Protokoll | keine | keine |

Belegt durch einen Kameraschwenk in der gespeicherten Welt: Sobald die
Tafeln ins Bild kamen, öffnete `WebPanels.seen` über `FnWeb.openInWorld` drei
Flächen an genau den Koordinaten, an denen sie vor der Umstellung standen.
Alle Prüfläufe grün (714), Herunterfahren sauber.

**Ehrlich zum Standbild:** Ein knackes Bild der Tafelfläche selbst ist diesmal
nicht entstanden — die Testwelt stand nach vielen Startläufen auf Nacht mit
Regen, und die dunkle Startseite ist darin kaum vom Grund zu trennen. Die
Lage ist über den nachgespielten Transform korrekt und über die identischen
Öffnungskoordinaten belegt; dass eine `WorldSurface` eine Seite lesbar in die
Welt malt, zeigt das Tageslichtbild aus Schritt 4.

## Was offen bleibt

```text
5b Editor auf die API   BrowserScreen reicht heute sieben Messklassen an die
                        Sitzung durch (paints(), texture(), inputLatency(),
                        runScript, Popup-Geometrie). WebSurface hat davon
                        nichts. Die Umstellung heißt entweder: die öffentliche
                        API um Messhaken erweitern (ein Versprechen für immer)
                        oder die Messungen aufgeben. Das ist eine Entscheidung,
                        keine Fleißarbeit — sie gehört an den nächsten
                        Haltepunkt, nicht in einen stillen Umbau.
Seite → Mod             erledigt: WebSurface.onMessage und window.fnSend,
                        siehe stand-web-kanal.md.
Block/Tafel entfernen   erst wenn 5b steht — der Block ist bis dahin der
                        einzige Weg, eine Fläche in der Welt zu bekommen, den
                        ein Spieler ohne Code hat.
```
