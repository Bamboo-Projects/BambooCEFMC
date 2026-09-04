# Auslieferung: Bucket, Domain und die Abnahme ohne Gradle

Stand 1. September 2026. Vorher: [`stand-runtime-b89.md`](stand-runtime-b89.md)
(MCEF ist raus, eigene Laufzeitumgebung, Patch 0005). Danach: die Web-API nach
[`plan-web-api.md`](plan-web-api.md).

## In einem Satz

Die Laufzeitumgebung liegt in einem R2-Bucket hinter
`factorynetwork.bamboo-srv.de`, der Client lädt sie beim ersten Bedarf selbst
nach, und der Start ohne Gradle ist abgenommen — mit drei Befunden unterwegs,
die alle behoben und im Spiel gegengeprüft sind.

## Wo die Dateien liegen

| Was | Wo |
|---|---|
| Bucket | `factorynetwork` (Cloudflare R2, Konto Florian Richter) |
| Domain | `factorynetwork.bamboo-srv.de`, Zone `bamboo-srv.de`, TLS aktiv |
| Archiv | `/fn-runtime-146.0.10-windows-x86_64.tar.gz` (173.092.474 Bytes) |
| Prüfsumme | `/fn-runtime-146.0.10-windows-x86_64.tar.gz.sha256` |
| Manifest | `tools/runtime/dist.properties` → im Jar als `fn-runtime.properties` |

Die Objekte liegen flach im Wurzelverzeichnis des Buckets; das Manifest hängt
den Dateinamen an `runtime.base-url` an. Ein Abruf über die Domain lieferte das
Archiv mit 32 MB/s in 5,3 s, Prüfsumme gleich.

### Wie es angebunden wurde

Der MCP-Zugang zu Cloudflare darf R2 lesen, aber nicht schreiben — der Aufruf
auf `/r2/buckets/{name}/domains/custom` endete mit Fehler 10000. Der Weg, der
ging, ist `wrangler` mit eigener Anmeldung:

```text
npx wrangler login
npx wrangler r2 bucket domain add factorynetwork --domain factorynetwork.bamboo-srv.de --zone-id 6208841ea5666de25523c12fa1fb5f94 --force
npx wrangler r2 object put factorynetwork/<datei> --file=tools/runtime/build/dist/<datei> --content-type application/gzip --remote
```

`wrangler` legt seinen Kontocache als `.wrangler/` ins Arbeitsverzeichnis;
der Ordner steht seitdem in der `.gitignore`.

**Eine Falle bei der Namensauflösung.** Wer die Domain abfragt, bevor der
Eintrag steht, bekommt bei 1.1.1.1 ein „gibt es nicht" — und das bleibt dort
bis zu einer halben Stunde im Zwischenspeicher, während 8.8.8.8 längst
antwortet. Der Rechner fragt 1.1.1.1 zuerst, also sah er die Domain nicht.
Leeren lässt sich das ohne Warten:

```text
curl -X POST "https://1.1.1.1/api/v1/purge?domain=factorynetwork.bamboo-srv.de&type=A"
```

### Wie eine neue Fassung ausgeliefert wird

1. `pwsh -File tools/runtime/build-jcef.ps1` — baut die Laufzeitumgebung.
2. `pwsh -File tools/runtime/package-runtime.ps1` — packt reproduzierbar,
   schreibt Prüfsumme und Fassung nach `dist.properties`.
3. Beide Dateien aus `tools/runtime/build/dist/` mit `wrangler r2 object put`
   in den Bucket.
4. `dist.properties` committen.

Die Fassung steht im Pfad des Zwischenlagers
(`<spiel>/factorynetwork/runtime/<fassung>/`), eine neue Fassung landet also
in einem neuen Ordner; der alte bleibt liegen, bis ihn jemand löscht.

## Die Abnahme

Alle Läufe über ein Startskript, das aus dem erzeugten
`build/moddev/runClient.cmd` abgeleitet ist: ohne Gradle, ohne die Zeile
`set PATH=`, ohne `-Dfn.runtime.dir`, mit `-Dfn.ide=true` und
`--quickPlaySingleplayer "New World"`. Das ist der Weg, den ein Spieler geht:
Kein Entwicklungsordner, die Laufzeitumgebung muss von selbst kommen.

### Lauf A — nichts liegt da, der Client lädt

```text
21:01:17.606  Lade die Laufzeitumgebung: https://factorynetwork.bamboo-srv.de/fn-runtime-146.0.10-windows-x86_64.tar.gz
21:01:18.680  Kein Browser zu haben: FAILED: NOT_DOWNLOADED: Die Laufzeitumgebung wird geladen
21:01:18.841  Laufzeitumgebung: 20 von 165 MB
     …
21:01:23.131  Laufzeitumgebung: 160 von 165 MB
21:01:26.110  Laufzeitumgebung liegt jetzt unter .\factorynetwork\runtime\146.0.10
```

| Größe | Wert |
|---|---|
| Laden, prüfen, auspacken | 8,5 s |
| Dateien im Ordner | 238 |
| Reste (`.teil`, `.unvollstaendig`) | keine |

Die Oberfläche zeigt derweil „Die Web-Runtime steht nicht bereit." und bleibt
dabei; nach dem Download muss der Spieler sie selbst erneut öffnen. Das ist
heute so gewollt und unten unter „offen" notiert.

### Lauf B — das Zwischenlager wird gefunden

```text
21:04:24.528  Eigene Laufzeitumgebung: .\factorynetwork\runtime\146.0.10 — im Thread Render thread
21:04:24.554  ProcessGuard: aktiv — Job Object mit KILL_ON_JOB_CLOSE (schon im Job: nein)
21:04:25.266  Chromium ist da: JCEF Version = 146.0.10.1 / Chromium Version = 146.0.7680.179
21:04:29.280  Seite: IDE bereit: 8 Modelle, 800 Zeilen im größten
```

Keine Warnung der Mod im ganzen Lauf, sechs `jcef_helper` bei zwei offenen
Browsern, nach dem Fensterkreuz alle Prozesse binnen einer Sekunde weg. Damit
ist die Stufe „Start ohne Gradle" aus `stand-runtime-b567.md` bestanden — auf
der nachgeladenen Laufzeitumgebung, nicht auf der aus dem Entwicklungsordner.

**Und ein Fehler beim Beenden**, der zu Befund 1 wurde.

### Lauf C und D — nach den Fixes

Derselbe Weg, dieselben Zeilen bis „IDE bereit". Nach „Chromium ist unten"
steht statt des Fehlers mit Stapel eine Zeile:

```text
Keine Web-Runtime — SHUT_DOWN: Chromium ist heruntergefahren und startet in diesem Prozess nicht neu
```

Null `ERROR`-Zeilen im Protokoll. In Lauf C stand davor noch
`FAILED: SHUT_DOWN: …` — das war Befund 3.

## Befund 1: Nach dem Herunterfahren wollte jemand neu starten

```text
Chromium ist unten: TERMINATED
Eigene Laufzeitumgebung: .\factorynetwork\runtime\146.0.10 — im Thread Render thread
ERROR Die eigene Laufzeitumgebung kam nicht hoch
java.lang.IllegalStateException: Settings can only be passed to CEF before createClient is called the first time.
    at WebPanels.open … at WebPanelRenderer.render
```

`GameShuttingDownEvent` fährt Chromium herunter, und danach malt Minecraft
noch ein Bild. Der Tafel-Renderer sieht keine Sitzung mehr, will eine neue,
ruft `ensureStarted` — und `WebRuntime` steht nach `shutdown()` wieder auf
`NOT_STARTED`, also wird gestartet. CEF lässt sich je Prozess aber nur einmal
starten.

**Die Lösung sitzt in der CEF-Schicht, nicht in `WebRuntime`.** Dort ist die
Einmaligkeit eine Eigenschaft des Unterbaus: `FnCefRuntime` merkt sich das
Herunterfahren und antwortet auf jeden weiteren Griff mit dem neuen Zustand
`SHUT_DOWN` — nicht nutzbar, keine Wiederholung wert. `WebRuntime` bleibt
unverändert; sein Zurücksetzen auf `NOT_STARTED` ist für einen Unterbau, der
neu starten kann, richtig, und die Prüfläufe brauchen es.

## Befund 2: Die Prüfläufe griffen ins Netz

Sobald im Manifest eine Adresse stand, lud jeder `gradle test` wirklich:
`RuntimeInstallTest` über `locate` in einen Wegwerfordner,
`WebSupportTest` über den echten Griff nach der Laufzeitumgebung — in das
Arbeitsverzeichnis von Gradle. Der Beleg lag als 14 MB große `.teil`-Datei
unter `factorynetwork/runtime/` im Wurzelverzeichnis des Repositorys, vom
Daemon-Thread abgebrochen, als der Prüflauf endete.

Behoben über ein Manifest für Prüfläufe (`RuntimeManifest.useForTests`):
`WebSupportTest` und die beiden betroffenen Downloader-Tests bekommen eines
ohne Adresse, und ein neuer Test fährt den ganzen Weg über `locate` gegen den
eigenen Server — anstoßen, `NOT_DOWNLOADED`, warten, beim zweiten Blick liegt
der Ordner da. Nach dem Fix taucht „Lade die Laufzeitumgebung" nur noch in
`RuntimeInstallTest` auf, gegen `127.0.0.1`.

## Befund 3: Der Zustand ging unterwegs verloren

Im Protokoll stand `FAILED: NOT_DOWNLOADED: …` und `FAILED: RUNTIME_MISSING: …`
— der Zustand war `FAILED`, der eigentliche Grund nur noch Text.
`FnRuntimeBackend.create` packte jede Ausnahme in `FAILED` um, auch die
vorhergesehene `WebRuntimeUnavailable`, die den Zustand trägt. Und
`FnCefRuntime` merkte sich von einem endgültigen Grund nur den Wortlaut; beim
zweiten Griff kam daraus eine `IllegalStateException`.

Beides behoben: Die vorhergesehene Ausnahme geht unverändert weiter, und
gemerkt wird der Zustand selbst. `WebSupportTest` verlangt jetzt genau
`RUNTIME_MISSING`, wo vorher „eines von beiden" reichte.

Das ist mehr als Kosmetik: An dem Zustand hängt, ob ein zweiter Versuch lohnt
und welchen Satz die Oberfläche zeigt. „Wird geladen" und „kaputt" sind zwei
verschiedene Auskünfte.

## Was offen bleibt

```text
echter Launcher          Stufe 3 — ungeprüft; dort kann der Prozess schon in
                         einem Job liegen (ProcessGuard „schon im Job: ja")
Oberfläche beim Laden    zeigt „steht nicht bereit" und bleibt dabei; nach dem
                         Download muss der Spieler neu öffnen. Ein Fortschritt
                         („x von y MB", RuntimeInstall.downloading() steht
                         bereit) und ein Öffnen von selbst wären der nächste
                         Schritt — das ist Oberfläche, gehört zur IDE-Seite
drei WARN mit Stapel     wenn IdeScreen nach „Kein Browser zu haben" weiter
                         Schemata, Konsole und Hintergrund anmelden will;
                         harmlos, gehört zur IDE-Seite
andere Plattformen       gebaut ist nur windows-x86_64; das Manifest kann mehr
```

---

Die Grenze zwischen Laufzeitumgebung und IDE steht in
[`grenze-runtime-ide.md`](grenze-runtime-ide.md); die Punkte der IDE in
[`ide-offene-punkte.md`](ide-offene-punkte.md).
