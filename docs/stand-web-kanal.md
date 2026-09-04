# Der Kanal von der Seite zur Mod

Stand 2. September 2026. Der wichtigste Punkt, der nach den Overlays und den
Weltflächen offen war. Vorher: [`stand-web-weltflaeche.md`](stand-web-weltflaeche.md).

## In einem Satz

Eine Seite ruft `window.fnSend("...")` oder `window.fnSend({...})`, und der
Aufrufer der Fläche bekommt die Nachricht über `WebSurface.onMessage` — ohne
einen `org.cef`-Typ in der Signatur.

## Warum es das braucht

Ein Schnellmenü konnte Tasten bekommen, seine Wahl aber niemandem sagen. Eine
Fläche, die nur zuhört, ist eine Sackgasse: Der halbe Sinn eines Menüs ist die
Antwort, die es zurückgibt.

## Wie es gebaut ist

```java
overlay.surface().onMessage(message -> LOG.info("Overlay meldet: {}", message));
```

```js
// in der Seite
window.fnSend({ gewaehlt: "Maschinen", nummer: 1 });
```

| Stück | Was es ist |
|---|---|
| `WebSurface.onMessage(Consumer<String>)` | der Empfänger, im Renderthread |
| `window.fnSend` | in jede Seite eingespritzt: Zeichenkette oder Objekt (JSON) |
| `window.fnQuery` | Chromiums rohe Form, mit `onSuccess`/`onFailure` |
| `MessageRouting` | innen: welche Sitzung welche Nachricht bekommt |

**Ein Router am geteilten Client.** Alle Browser teilen sich einen
`CefClient`; ein einziger `CefMessageRouter` daran bedient jeden. Chromium
reicht bei einer Anfrage den Browser mit, der sie stellte — daraus findet
`MessageRouting` über die Kennung des Browsers (nicht seine Gleichheit) die
richtige Sitzung.

**Der Shim kommt beim Ladebeginn und am Ladeende.** So hat eine Seite
`fnSend` schon, wenn ein Skript im Kopf es ruft, und behält es nach dem
Laden. Das Einspritzen ist idempotent; doppelt schadet nicht.

**Der Empfänger läuft im Renderthread**, dort, wo `onPaint` pumpt — dieselbe
Zusage wie beim Rest der Fläche.

## Was geprüft ist

- **`MessageRouting` ohne Chromium**, sieben Prüfläufe: welche Sitzung wen
  bekommt, zwei Browser mit eigenen Empfängern, Identität statt Gleichheit,
  nach dem Abmelden nichts mehr, leere Nachricht ja und fehlende nein.
- **Der eingespritzte Text ist heil**: Ein Prüflauf zieht `SEND_SHIM` aus dem
  kompilierten Konstantenwert, prüft die Klammerung und legt ihn nach
  `build/shim-dump.js`. node prüft an genau diesem Wert, dass er als
  JavaScript parst und `fnSend` eine Zeichenkette wie ein Objekt (als JSON)
  an `fnQuery` reicht.
- Alle 722 Prüfläufe grün.

**Noch nicht im Spiel gefahren** — auf Wunsch: Die Läufe im laufenden Spiel
kapern das Fenster und schicken Tasten hinein. Der Weg dorthin steht bereit.

## Zum Ausprobieren, wenn der Moment passt

```text
/fnweb overlay        Schnellmenü öffnen, mit den Pfeilen wählen, Enter
                      → im Protokoll: Overlay meldet: {"gewaehlt":"…","nummer":…}
```

Der Nachweis mit echten Tasten über das Fenster läuft wie bei den Overlays;
gesucht wird die Zeile `Overlay meldet:` mit der Wahl als JSON.

## Was offen bleibt

```text
Antwort zurück        Heute ist der Rückweg leer (fnSend kümmert sich nicht um
                      onSuccess). Wer aus der Mod in die Seite antworten will,
                      nimmt runScript — ein sauberer Weg dafür wäre ein
                      eigener Punkt, wenn ein Fall ihn braucht.
5b Editor auf die API  unverändert offen, siehe stand-web-weltflaeche.md.
```
