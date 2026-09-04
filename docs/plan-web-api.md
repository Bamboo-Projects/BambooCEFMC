# Die Web-API der Runtime

Festgelegt am 1. September 2026. **Die Runtime bekommt eine Java-Schnittstelle,
mit der Mod-Code Web-Flächen erzeugt** — für den Editor, für Overlays im
Bild, für Flächen in der Welt. Für uns und für fremde Mods.

Ein Missverständnis davor gehört zur Vorgeschichte: „per Code" hieß **Mod-API**
und nicht die Manifold-Sprache. Ein `webview`-Schlüsselwort war eine
Viertelstunde lang gebaut und ist zurückgenommen — FactoryNetwork muss keine
Web-Flächen kennen.

---

## Was die Schnittstelle können muss

```text
öffnen und zeichnen     Adresse, Größe, Name → eine Fläche mit Textur
Overlay im Bild         über dem Spiel, in Bildschirmpunkten
Eingaben weiterreichen  Maus, Tastatur, Zeiger, Fokus
   mit Filter           welche Tasten die Fläche bekommt und welche das Spiel
Fläche in der Welt      an Ort, Größe und Ausrichtung gehängt
```

**Der Filter ist die Anforderung mit dem meisten Gewicht.** Ein Schnellmenü
braucht Escape und die Pfeiltasten; W, A, S und D gehören dem Spieler. Eine
Fläche, die alles oder nichts bekommt, ist für diesen Fall unbrauchbar — und
er ist der häufigere.

```java
KeyFilter.ALL                     alles, wie der Editor es braucht
KeyFilter.NONE                    nur ansehen
KeyFilter.only(ESCAPE, UP, DOWN)  ein Schnellmenü
```

---

## Für fremde Mods, und was das kostet

Die Schnittstelle ist **öffentlich zugesagt**: benannte Fassung, kein Bruch
ohne Ankündigung. Das bindet, und zwar an drei Stellen:

1. **Kein Typ aus `org.cef` in einer Signatur.** Wer die API benutzt, soll
   nicht gegen Chromium übersetzen müssen — und wir wollen die Fassung
   wechseln können, ohne fremden Code zu brechen.
2. **Kein Typ aus `net.minecraft.world`.** Die Paketgrenze verbietet es
   ohnehin (`PackageBoundaryTest`), und für die Welt-Fläche reichen drei
   Fließkommazahlen.
3. **Was einmal öffentlich ist, bleibt es.** Jede Methode, die hinauskommt,
   ist ein Versprechen; alles andere bleibt paketprivat.

---

## Der Zuschnitt

| Stück | Was es ist |
|---|---|
| `WebSurface` | eine lebende Fläche: Textur, Größe, Eingaben, `onMessage`, Schließen |
| `SurfaceSpec` | der Bauplan: Adresse, Größe, Name, Filter, Sichtbarkeitsstufe |
| `KeyFilter` | welche Tasten durchgehen |
| `FnWeb` | der Einstieg: öffnen, Zustand erfragen, Grenzen |
| `WebOverlay` | eine Fläche über dem Bild, in Bildschirmpunkten |
| `WorldSurface` | eine Fläche in der Welt, an Ort und Ausrichtung |

Alles unter `dev.devpanda.factorynetwork.web.api`. Was darunter liegt —
`BrowserSession`, `FnBrowser`, `SessionTexture` — bleibt innen und darf sich
weiter ändern.

---

## Reihenfolge

```text
1. WebSurface, SurfaceSpec, KeyFilter, FnWeb      der Kern
2. Eingaben samt Filter                            und ein Prüflauf dafür
3. WebOverlay                                      HUD
4. WorldSurface                                    Welt, ersetzt WebPanels
5. Editor und Tafel auf die API umstellen          erst danach fällt Altes
```

Schritt 5 folgt derselben Regel wie bei MCEF: Nichts wird entfernt, bevor der
neue Weg trägt.
