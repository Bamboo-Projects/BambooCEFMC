# BambooCEF for Minecraft

A Chromium web runtime for **NeoForge 21.1 / Minecraft 1.21.1** mods, with a
small Java API. Pages render off-screen through CEF (Chromium Embedded
Framework) straight into Minecraft textures: as a full-screen interface, as a
HUD overlay, or as a surface standing in the world. Pages talk back to the mod
over a one-line channel.

BambooCEF is a library. It starts no browser on its own; a mod that needs the
web asks for it.

**Status:** early. Windows x64 only for now; the runtime download is about
165 MB on first use. The API is small and may still move before 1.0.

## What you get

- `FnWeb.openScreen(spec)` — a full-screen, interactive page over the live
  world (an editor, a dashboard).
- `FnWeb.openOverlay(spec, x, y)` — a HUD panel; keyboard or mouse focus on
  request (`WebOverlay.focus(OverlayFocus.KEYBOARD)`), the game keeps running.
- `FnWeb.openInWorld(spec, x, y, z, yaw, widthBlocks, heightBlocks)` — a page
  standing in the world, both faces visible; the crosshair clicks it.
- `WebSurface.onMessage(...)` — the page calls `window.fnSend(...)`, the mod
  gets the string.
- `FnWeb.prepare()` — warm Chromium up at the title screen so the first page
  opens without the cold start.

Mods use only `dev.devpanda.bamboocef.web.api` (plus `WebPage`, `WebAssets`
and `BrowserVisibility`). Everything else is internal and may change.

## Example

```java
import dev.devpanda.bamboocef.web.api.FnWeb;
import dev.devpanda.bamboocef.web.api.Keys;
import dev.devpanda.bamboocef.web.api.SurfaceSpec;
import dev.devpanda.bamboocef.web.api.WebSurface;
import dev.devpanda.bamboocef.web.WebPage;

// At client setup: ask for the runtime early, the first page then opens fast.
FnWeb.prepare();

// Ship the page inside your mod jar and unpack it to a file URL.
String url = WebPage.unpack("assets/mymod/web/menu.html", "menu.html");

// A 1280x720 page that receives the editor key set and draws transparent.
SurfaceSpec spec = SurfaceSpec.of(url, 1280, 720)
        .named("My Menu")
        .keys(Keys.EDITOR)
        .transparent(true);

WebSurface page = FnWeb.openScreen(spec);
page.onMessage(message -> LOGGER.info("page says: {}", message));
```

On the page side:

```js
window.fnSend({ chosen: "machines", index: 1 });   // objects are JSON-encoded
```

## How the runtime arrives

Chromium is not inside the mod jar. On first use BambooCEF downloads the
matching runtime archive into `<game>/bamboocef/runtime/<version>/` and
verifies its SHA-256. The address and checksum come from
`bamboocef-runtime.properties` inside the jar (`tools/runtime/dist.properties`
at build time).

For development, point the game at a local build instead:
`-Dbamboocef.runtime.dir=<path to a built runtime folder>`.

## Building

- The mod: `./gradlew build` (ModDevGradle, NeoForge 21.1.248, Java 21).
  The build expects `tools/runtime/build/out/windows-x86_64/jcef.jar`.
- The native runtime: `pwsh -File tools/runtime/build-jcef.ps1` builds
  upstream java-cef with the patches in `tools/runtime/patches/` (CEF pinned in
  `pin.properties`); `package-runtime.ps1` packs the archive that
  `dist.properties` describes.
- Tests: `./gradlew test`. They never touch the network.

## Development switches

| Property | Effect |
|---|---|
| `-Dbamboocef.runtime.dir=<dir>` | use a local runtime instead of downloading |
| `-Dbamboocef.devtools=true` | open Chromium DevTools for surfaces |
| `-Dbamboocef.selftest=true` | start Chromium after launch and check the frame path |
| `-Dbamboocef.benchmark=true` | run the render/interaction proof chain |

In game, `/bamboocef` carries the runtime's dev commands (`zustand`, `probe`,
`nachweis`, `seite <url>`, `devtools`, `messung`, …).

## License

MIT, see `LICENSE`. The runtime is built from CEF and java-cef, both
BSD-licensed; their notices are in `NOTICE.md` and ship with the runtime
archive.
