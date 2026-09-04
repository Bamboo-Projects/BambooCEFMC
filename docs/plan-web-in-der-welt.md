# Web-Flächen in der Welt

Gewünscht am 1. September 2026: **Eine Web-Fläche muss sich in die Welt setzen
lassen — als Block, nicht nur als Vollbild.** Das gehört zur eigenen
CEF-Runtime und ist kein Aufsatz, den man später danebenstellt.

Dieses Dokument hält die Anforderung fest und was dafür schon da ist. Es ist
kein Entwurf; der gehört in den Block selbst.

---

## Was der Wunsch bedeutet

Heute gibt es genau eine Web-Fläche: den Editor als Vollbildschirm. Was fehlt,
ist dieselbe Fläche **an einem Block**, sichtbar im Vorbeigehen, ohne dass
jemand etwas anklickt.

Der vorhandene Anzeigeblock (`DisplayBlockEntity`, `DisplayRenderer`) zeichnet
heute Minecraft-Text auf seine Vorderseite. Er ist die naheliegende Stelle —
aber die Entscheidung, ob eine Web-Fläche derselbe Block mit anderem Inhalt
oder ein eigener ist, gehört in den Block.

---

## Was schon trägt

| Stück | Zustand |
|---|---|
| `BrowserSession.textureId()` | liefert eine rohe GL-Textur — genau das, was ein Blockrenderer braucht |
| `BrowserCompositor` | zeichnet diese Textur heute im Bildschirm; ein Viereck in der Welt ist dieselbe Arbeit an anderer Stelle |
| `BrowserVisibility`, `FramePacer` | **für diesen Fall geschrieben** — im Kopfkommentar steht „bei einem Dutzend Anzeigen in einer Basis" |
| `BrowserManager` (B5) | weiß, wie viele Browser leben; ohne ihn ließe sich keine Obergrenze durchsetzen |
| `ProcessGuard` (B6) | fängt ab, was ein Dutzend Chromium-Prozesse bei einem Absturz hinterließen |

Der Renderpfad ist also da. Was fehlt, ist alles um ihn herum.

---

## Was neu ist — und was davon wirklich Arbeit macht

**Klein.** Die Textur in einem `BlockEntityRenderer` auf eine Blockfläche
zeichnen. Der Editor macht nichts anderes, nur mit einer anderen Matrix.

**Der eigentliche Aufwand: die Eingabe.** Ein Bildschirm hat einen Fokus und
einen Mauszeiger; ein Block in der Welt hat beides nicht. Es braucht

```text
Strahl vom Spieler → Trefferpunkt auf der Blockfläche → Browser-Pixel
eine Regel, wann eine Tafel die Tastatur bekommt und wann sie sie abgibt
einen Weg zurück, der nicht Escape ist — das gehört im Spiel dem Menü
```

**Der Preis, und er ist kleiner als zuerst geschrieben.** Hier stand, jede
Fläche sei ein eigener Chromium mit fünf Hilfsprozessen. Nachgemessen an einem
laufenden Editor stimmt nur die Fünf, nicht die Zuordnung:

```text
1x gpu-process     hängt an Chromium, nicht an der Seite
2x utility         desgleichen — Netzwerk und Speicher
2x renderer        einer für die Seite, einer als Reserve
```

Drei der fünf entstehen einmal. **Drei Flächen kosten deshalb grob sieben
Prozesse und nicht fünfzehn.** Was wirklich skaliert, ist der Renderer je
Seite — und der Upload: 1920×1080 kostet 9–14 ms auf dem Renderthread, und
zwei Flächen in Vollauflösung fressen das Bildbudget.

Beherrschbar ist das über drei Regeln, und die gehören in den Entwurf:

1. **Auflösung je Tafel klein halten** — der Upload skaliert mit der Fläche.
   Eine Tafel an der Wand braucht keine 1920×1080.
2. **Sichtbarkeit entscheidet den Takt.** Was niemand ansieht, malt zwei Bilder
   je Sekunde und nicht sechzig. `BrowserVisibility` ist dafür da.
3. **Eine harte Obergrenze**, wie viele Flächen gleichzeitig leben. Was darüber
   liegt, zeigt ein Standbild oder gar nichts.

**Lebenslauf.** Chunk entlädt oder Block wird abgebaut → Browser zu. Über den
`BrowserManager` ist das jetzt eine Zeile; vorher wusste niemand, dass es ihn
gab.

---

## Die Entscheidungen, getroffen am 1. September 2026

**Drei Flächen gleichzeitig, je ein eigener Browser.** Die Frage war, ob sich
mehrere Flächen einen Browser teilen müssten — nach der Messung oben nicht:
Der teure Teil entsteht einmal. Eine gemeinsame Seite in Kacheln spart einen
Renderer und kostet dafür alles andere: Alle Flächen teilten sich eine
Auflösung, und eine hängende Seite nähme die übrigen mit. Bei Flächen, die
Spieler anlegen, ist das die falsche Bauform.

**Was darauf steht, entscheidet der Spieler, der die Fläche anlegt.** Das ist
keine Designfrage, sondern der Zweck der Sache. Die technische Frage darunter
ist eine andere: **was die Fläche an Eingaben annimmt.** Drei Stufen, in
dieser Reihenfolge:

```text
1. Anschauen    Textur auf der Blockfläche, sonst nichts        klein
2. Klicken      Strahl auf Browser-Pixel, Zeiger, Hover         mittel
3. Tippen       Fokusregel und ein Rückweg, der nicht Escape    der teure Teil
```

So steht die Fläche früh, und der teure Teil kommt, wenn der billige trägt.

---

## Ein Nebenbefund, der hier hingehört

Die Kommentare in `BrowserVisibility` und `FramePacer` behaupten noch, die
Bildrate sei gar nicht einstellbar — „gemessen 30,1 Bilder je Sekunde, CEFs
Voreinstellung". Das stammt aus der MCEF-Zeit und stimmt seit CEF 146 nicht
mehr: `CefBrowserSettings.windowless_frame_rate` gibt es, wir setzen 60, und
gemessen kommen 60,3. Die Drosselung nach unten funktioniert wie beschrieben;
nur der Satz „nach oben ist nichts zu holen" ist überholt.

Gehört korrigiert, wenn dieser Block drankommt — dann ist die Drosselung
ohnehin das Thema.

---

## Reihenfolge

Dieser Block hängt an nichts, was noch offen ist, und blockiert nichts. Er
kann vor oder nach B8/B9 kommen.

**Erledigt:** B8/B9 sind durch, MCEF ist aus dem Weg. Vor diesem Block liegt
nur noch die Auslieferung der Laufzeitumgebung — nicht als Abhängigkeit,
sondern der Reihe nach: Solange ein Spieler die Runtime nicht bekommt, sieht
er auch keine Fläche in der Welt.
