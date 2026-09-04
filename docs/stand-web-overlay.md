# Overlays: eine Fläche über dem Bild, mit Tastenfilter

Stand 1. September 2026. Schritt 3 aus [`plan-web-api.md`](plan-web-api.md).
Vorher: [`stand-runtime-auslieferung.md`](stand-runtime-auslieferung.md).

## In einem Satz

`FnWeb.openOverlay(spec, x, y)` legt eine Web-Fläche in Bildschirmpunkten
über das Spiel; mit Tastaturfokus bekommt sie, was ihr Filter nimmt, und der
Spieler behält Maus, Bewegung und alles andere — belegt mit echten Tasten
über das Fenster, nicht mit Aufrufen im Code.

## Die Schnittstelle

```java
SurfaceSpec spec = SurfaceSpec.of(url, 240, 190)        // Bildschirmpunkte
        .named("Schnellmenü")
        .keys(Keys.CLOSE.or(Keys.ARROWS).or(Keys.CONFIRM))
        .transparent(true);
WebOverlay overlay = FnWeb.openOverlay(spec, 12, 12);
overlay.focus(OverlayFocus.KEYBOARD);
```

| Stück | Was es ist |
|---|---|
| `WebOverlay` | Lage in Bildschirmpunkten, Sichtbarkeit, Fokus, die Fläche darunter |
| `OverlayFocus` | `NONE` (nur Bild), `KEYBOARD` (Tasten nach Filter, Maus beim Spiel), `MOUSE` (Zeiger frei) |
| `FnWeb.openOverlay` | der Einstieg; Breite und Höhe des Bauplans gelten als Bildschirmpunkte |

**Bildschirmpunkte, nicht Pixel.** Dieselbe Einheit, in der Minecraft seine
Oberfläche malt. Die Fläche rechnet mit der GUI-Skalierung in Browserpixel
um (240 Punkte sind bei Skalierung 2 genau 480 Pixel) und zieht nach, wenn
sich die Skalierung ändert — geprüft je Bild, weil kein Ereignis sie meldet.

**F10 beendet jeden Fokus**, gleich, was der Filter sagt. Ein Filter mit
`KeyFilter.ALL` und ohne diesen Ausgang wäre eine Fläche, aus der niemand
herauskommt.

Nur ein Overlay hat Fokus; wer ihn bekommt, nimmt ihn dem anderen. Gezeichnet
wird nach allem, was Minecraft in der Oberflächenschicht malt, unter jedem
offenen Bildschirm — das Pausemenü legt sich darüber, samt Unschärfe.

## Warum ein Mixin

Ohne offenen Bildschirm verarbeitet `KeyboardHandler.keyPress` Escape
(Pausemenü) und die Bewegungstasten (`KeyMapping.set`) selbst, und erst danach
kommt NeoForges `InputEvent.Key` — nicht abbrechbar. Ein Bildschirm hilft
auch nicht: Er gibt die Maus frei, löst alle Tasten und lässt den Spieler
stehen. Genau das soll ein Schnellmenü nicht.

Also zwei Einhängungen am Anfang von `keyPress` und `charTyped`, je eine
Zeile, beide nur weiterreichend nach `Overlays` — dort fällt die
Entscheidung, ohne Mixin prüfbar. Das erste Mixin der Mod:
`factorynetwork.mixins.json`, angemeldet in `neoforge.mods.toml`.

## Was zusammengehört, geht denselben Weg

Der Filter wird genau einmal gefragt, beim Drücken. Das Zeichen danach, die
Wiederholung einer gehaltenen Taste und ihr Loslassen folgen dieser einen
Entscheidung (`KeyRouting`, fünf Prüfläufe). Sonst zwei Fehler, die erst im
Spiel auffallen: Das Spiel sieht W gedrückt und die Seite W losgelassen — der
Spieler läuft weiter. Und die Seite bekommt ein „w“, dessen Taste das Spiel
schon hatte.

## Der Nachweis: echte Tasten über das Fenster

`-Dfn.overlay=true` öffnet das Schnellmenü, sobald die Welt steht, und
schreibt jede Sekunde, was von außen nicht zu sehen ist: ob ein Bildschirm
offen ist, welchen Fokus das Overlay hat, wo der Spieler steht. Die Tasten
kamen von außen über `keybd_event` an das Fenster — nur die gehen durch das
Mixin. Was die Seite bekam, steht als `Seite: overlay: …` im Protokoll,
seit Chromiums Konsole am Start der Runtime hängt und nicht mehr nur am
Editor.

```text
Seite: overlay: Taste ArrowDown
Seite: overlay: Taste ArrowDown
Seite: overlay: Taste ArrowUp
Seite: overlay: Taste Enter
Seite: overlay: gewählt Maschinen
Overlay-Nachweis: Bildschirm=keiner, Fokus=KEYBOARD, Spieler=411.65/292.57
Overlay-Nachweis: Bildschirm=keiner, Fokus=KEYBOARD, Spieler=411.09/292.57   ← W, 700 ms
Seite: overlay: Taste Escape
Overlay-Nachweis: Bildschirm=keiner, Fokus=KEYBOARD                          ← kein Pausemenü
F10 — Overlay Schnellmenü 240x190 bei 12,12 (KEYBOARD) gibt die Tastatur zurück
Overlay-Nachweis: Bildschirm=keiner, Fokus=NONE
Overlay-Nachweis: Bildschirm=PauseScreen, Fokus=NONE                         ← Escape geht wieder ans Spiel
```

| Taste | Erwartet | Gesehen |
|---|---|---|
| Pfeile, Enter | Seite | Seite, Auswahl „Maschinen“ |
| W (gehalten) | Spiel | Spieler bewegt sich, Seite sieht nichts |
| Escape bei Fokus | Seite | Seite, kein Pausemenü |
| F10 | Fokus weg | Fokus `NONE` |
| Escape danach | Spiel | Pausemenü |

Kein `ERROR` im Protokoll, Herunterfahren sauber, alle Prozesse binnen einer
Sekunde weg. Ein Bild des Fensters bestätigt das Gezeichnete: die Karte
durchscheinend über der Welt, der Zähler läuft, unter dem Pausemenü liegt sie
unscharf wie alles andere.

## Mausfokus: ein Bildschirm, der nichts zeichnet

`OverlayFocus.MOUSE` öffnet einen eigenen `Screen`, weil die Maus nur so frei
wird — ohne Bildschirm greift Minecraft den Zeiger beim nächsten Klick wieder
(`MouseHandler.onPress`). Der Bildschirm malt nichts, das Overlay malt sich
weiter aus der Oberflächenschicht; er reicht Maus und Tasten an die Fläche
und schließt sich bei Klick daneben, Escape ohne Filter oder F10.

`-Dfn.overlay=mouse` gibt dem Schnellmenü fünf Sekunden nach dem Öffnen den
Mausfokus; der Zeiger kam über `SetCursorPos` und `mouse_event`:

```text
Overlay-Nachweis: Bildschirm=OverlayScreen, Fokus=MOUSE
Seite: overlay: geklickt Programme                       ← Klick auf den Eintrag
Seite: overlay: Taste Escape                             ← Escape bleibt bei der Seite
F10
Overlay-Nachweis: Bildschirm=keiner, Fokus=NONE
```

**Ein Befund, ehrlich aufgeschrieben:** Mit Mausfokus bewegt W den Spieler
nicht. Gebaut war ein Weiterreichen der nicht gefilterten Tasten über
`KeyMapping.set`, und es hat nichts bewegt — NeoForge schaltet die
Belegungen des Spiels ab, sobald ein Bildschirm offen ist. Der Weg ist
wieder raus. Mit freiem Zeiger steht der Spieler, wie in jeder Oberfläche;
wer laufen und ein Overlay bedienen will, nimmt `KEYBOARD`.


## Zum Ausprobieren

```text
/fnweb overlay        öffnet das Schnellmenü, noch einmal schließt es
                      Pfeile, Enter, Escape → Seite; F10 → zurück ans Spiel
```

## Was offen bleibt

```text
Seite → Mod           erledigt: WebSurface.onMessage und window.fnSend,
                      siehe stand-web-kanal.md.
Fokus bei Fensterwechsel   Verliert das Fenster den Fokus, öffnet Minecraft
                      das Pausemenü; das Overlay behält seinen Fokusstand und
                      bekommt Tasten wieder, sobald das Menü zu ist. Gewollt,
                      aber nirgends erklärt.
Punkte × Skalierung > 4096   SurfaceSpec deckelt Browserpixel bei 4096 je
                      Kante; ein Overlay über 2048 Punkte bei Skalierung 2
                      wird gestreckt statt geschärft. Heute kein Fall.
```

Schritt 4 (`WorldSurface`) und 5 (Editor und Tafel auf die API) folgen.
