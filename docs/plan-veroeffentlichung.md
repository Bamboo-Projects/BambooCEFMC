# Veröffentlichung, Versionierung und mehrere Minecraft-Versionen

Geplant am 2. September 2026. Noch nichts davon läuft — die Trennung ist nicht
fertig, es gibt keinen lauffähigen Stand zu releasen. Dieser Plan legt die
Struktur fest, damit von Anfang an richtig gearbeitet wird.

## Branches

```
master   stabile Release-Linie der aktuellen MC-Version, geschützt
dev      hier wird gearbeitet; alles läuft über dev, dann nach master
1.21.1   Release-Linie je MC-Version (eine Branch pro unterstützter Version)
1.21.4   …
```

- **Gearbeitet wird auf `dev`**, nie direkt auf `master` oder einer
  Versions-Branch.
- Ist etwas stabil, wandert es von `dev` in die Versions-Branch der aktuellen
  MC-Version und wird dort getaggt.
- Eine **neue MC-Version** bekommt eine eigene Branch (`1.21.4`), abgezweigt von
  der bisherigen; nur was die NeoForge-API dort ändert, wird angepasst.

Warum Branch-pro-MC-Version und nicht ein Zweig mit Schalter: Die NeoForge-API
verschiebt sich zwischen MC-Versionen (Events, Renderpfad), und genau daran
hängt die Runtime. Parallele Branches sind ehrlicher als Präprozessor-Kommentare.
**Stonecutter** (ein Werkzeug für Ein-Branch-Mehrversionen) bleibt eine Option,
falls die Unterschiede klein bleiben — bei dieser dünnen Java-Schicht denkbar,
aber erst zu prüfen, wenn es zwei Versionen wirklich gibt.

## Versionsnummer

- Mod-Version nach **SemVer**: `MAJOR.MINOR.PATCH` (Stand: `0.1.0`).
- Die MC-Version steht **nicht** in der SemVer-Zahl, sondern im Dateinamen und
  in den Modrinth-Angaben: `bamboocef-0.1.0+mc1.21.1.jar`.
- Tag je Release-Linie: `v0.1.0+mc1.21.1`. So kollidieren Tags über
  MC-Versionen hinweg nicht.

## Der Kniff bei mehreren MC-Versionen

**Die native CEF-Runtime ist MC-versionsunabhängig.** Die jcef-Binärdateien
(~173 MB, im R2 hinter `factorynetwork.bamboo-srv.de`) kennen kein Minecraft;
sie hängen nur an der CEF-Fassung (146.0.10). Also:

- **Ein R2-Runtime-Paket bedient alle MC-Versionen.** Geschlüsselt nach
  CEF-Fassung, nicht nach MC.
- Pro MC-Version unterscheidet sich nur die **dünne Java-Mod** — die paar
  Klassen, die gegen die jeweilige NeoForge-API übersetzen.

Das macht Mehrversionen hier billig: ein großes Paket einmal, kleine Jars je
Version.

## Zwei Pipelines

### 1. Mod-Jar (oft, schnell)

GitHub Actions, ausgelöst durch einen Tag `v*+mc*` (oder von Hand):

```yaml
# .github/workflows/publish.yml  (Entwurf — braucht Secrets + Minotaur im build.gradle)
on:
  push:
    tags: ['v*+mc*']
  workflow_dispatch:
jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - run: ./gradlew build
      - run: ./gradlew modrinth curseforge   # com.modrinth.minotaur
        env:
          MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}
          CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}
      - uses: softprops/action-gh-release@v2
        with: { files: build/libs/*.jar }
```

- Veröffentlicht die Jar in **GitHub Releases**, **Modrinth** und
  **CurseForge**.
- Modrinth/CurseForge über das Gradle-Plugin **`com.modrinth.minotaur`**: dort
  stehen `gameVersions`, `loaders = ['neoforge']`, `dependencies` und die
  Änderungsnotiz.
- Die eigentliche Release schneidet die `/release`-Skill (bauen, taggen,
  Release-Notes) — der Tag stößt dann diese Pipeline an.

### 2. Native Runtime (selten, schwer)

Nur bei einem CEF-Fassungswechsel. Eigene Workflow-Datei, Matrix über die
Betriebssysteme (windows/macos/linux-Runner):

- `tools/runtime/build-jcef.ps1` bzw. das Pendant je OS baut die Natives.
- `package-runtime.ps1` packt reproduzierbar, schreibt Prüfsumme + Fassung.
- Hochladen ins R2 (per `wrangler`, Secret `CLOUDFLARE_API_TOKEN` mit
  R2-Schreibrecht), `dist.properties` aktualisieren und in `dev` committen.

Getrennt von Pipeline 1, weil sie Stunden statt Minuten braucht und andere
Runner verlangt.

## Secrets (GitHub → Settings → Secrets)

```text
MODRINTH_TOKEN        Veröffentlichung auf Modrinth
CURSEFORGE_TOKEN      Veröffentlichung auf CurseForge
CLOUDFLARE_API_TOKEN  nur Pipeline 2: R2-Schreibrecht (Workers R2 Storage: Edit)
```

GitHubs eingebautes `GITHUB_TOKEN` reicht für GitHub Releases.

## Reihenfolge

```
1. Trennung fertig (Runtime in BambooCEFMC, FactoryNetwork hängt dran)   ← zuerst
2. Minotaur ins build.gradle, publish.yml scharf schalten, Secrets setzen
3. Erste Vorab-Release 0.1.0+mc1.21.1 über /release auf der 1.21.1-Branch
4. Weitere MC-Version: Branch 1.21.x, anpassen, taggen
```

## Stand 4. September 2026 — was jetzt wirklich läuft

Die Trennung ist fertig und abgenommen; dieser Abschnitt ersetzt, wo er
abweicht, die Planung oben.

- **Zuerst nur BambooCEF.** FactoryNetwork ist noch nicht so weit und folgt
  später mit derselben Pipeline.
- **Ziele:** GitHub Releases (Quelle der Wahrheit, über `/release`), dann
  **Modrinth** (Spieler und, kostenlos dazu, das Maven
  `maven.modrinth:bamboocef:<version>` für fremde Modder). **CurseForge**
  ist vorbereitet und schaltet sich ein, sobald Projekt und Token existieren.
  Ein eigenes Maven für die Mods braucht es nicht.
- **Werkzeug:** die GitHub-Action `mc-publish` (`.github/workflows/publish.yml`)
  statt Minotaur — ein Workflow für Modrinth und CurseForge, ausgelöst vom
  veröffentlichten GitHub-Release; der Release-Text ist der Changelog. Alle
  0.x-Versionen gehen als *beta* auf die Plattformen. Nötig sind die
  Variablen `MODRINTH_PROJECT_ID`/`CURSEFORGE_PROJECT_ID` und die Secrets
  `MODRINTH_TOKEN`/`CURSEFORGE_TOKEN` in der Org.
- **CI** (`build.yml`): baut und testet auf Ubuntu bei jedem Push nach `dev`
  und `master` — ohne nativen Runtime-Build, weil `org.cef:jcef` aus dem
  Bucket-Maven kommt (siehe unten).
- **jcef reist in der Mod-Jar.** `jcef.jar` (194 KB) ist per Jar-in-Jar
  (`jarJar`) in `bamboocef-<version>.jar` geschachtelt; Koordinaten
  `org.cef:jcef:<cef>` aus dem statischen Maven im Bucket
  (`https://bamboocef.bamboo-srv.de/maven`, Unterordner `maven/`). Nur die
  Natives kommen per Download. Versionsquelle: `jcef_version` in
  `gradle.properties` = `runtime.version` in `tools/runtime/dist.properties`.
- **Runtime-Verteilung:** Bucket `bamboocef`, Unterordner je Plattform
  (`minecraft/neoforge/`, später `unreal/`, `unity/`), Domain
  `bamboocef.bamboo-srv.de`.
- **Tag-Format:** das `/release`-Skill taggt `v{version}`; die Planung oben
  wollte `v0.1.0+mc1.21.1`. Solange es eine MC-Version gibt, reicht
  `v0.1.0`; das Suffix kommt mit der zweiten Versions-Branch. `publish.yml`
  nimmt beide Formen. Der Jar-Name bleibt vorerst `bamboocef-0.1.0.jar`.
- **Plattformtext:** Entwurf in `docs/modrinth.md` — mit den zwei Pflicht-
  angaben „Windows x64" und dem Runtime-Download (Adresse, Größe, Prüfsumme).
- **Vor dem ersten Release noch:** Modrinth-Projekt anlegen (Slug
  `bamboocef`), Variable und Secret setzen, `mod_version` prüfen, `/release`.
