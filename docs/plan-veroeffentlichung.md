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
