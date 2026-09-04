# Modrinth project text (draft)

Project: **BambooCEF** · slug `bamboocef` · category Library · loader NeoForge ·
game version 1.21.1 · client-side required, server-side unsupported ·
license MIT · source https://github.com/Bamboo-Projects/BambooCEFMC

---

## Summary (one line)

A Chromium web runtime for NeoForge mods: pages rendered straight into
Minecraft as screens, HUD overlays and surfaces standing in the world.

## Description

**BambooCEF is a library.** It does nothing on its own; other mods use it to
show web pages inside Minecraft — a full-screen interface, a HUD panel, or a
page standing in the world that you click with the crosshair. Pages talk
back to the mod, so a web page can be a real control surface.

Under the hood it is CEF (the Chromium Embedded Framework, the same engine
as Chrome) rendering off-screen into Minecraft textures.

### What you need to know before installing

- **Windows x64 only for now.** On other systems the mod loads and reports
  the web features as unavailable; mods built on it keep running without
  them. Linux and macOS are planned.
- **First use downloads a runtime of about 175 MB** (Chromium plus the
  bindings) from `https://bamboocef.bamboo-srv.de` into
  `<game>/bamboocef/runtime/`. The download is checked against a SHA-256
  before it is unpacked. Nothing else is fetched, and nothing is sent.
- The runtime is built from CEF and java-cef (BSD 3-Clause); the license
  texts ship inside the downloaded archive and in the repository's
  `NOTICE.md`.

### For mod developers

Depend on it through the Modrinth Maven and use only the public API in
`dev.devpanda.bamboocef.web.api`:

```groovy
repositories { maven { url = "https://api.modrinth.com/maven" } }
dependencies { implementation "maven.modrinth:bamboocef:<version>" }
```

```java
FnWeb.prepare();                       // at client setup: warm Chromium up early
WebSurface page = FnWeb.openScreen(
        SurfaceSpec.of(url, 1280, 720).named("My Menu").keys(Keys.EDITOR));
page.onMessage(msg -> ...);            // the page calls window.fnSend(...)
```

Overlays: `FnWeb.openOverlay(spec, x, y)`. Surfaces in the world:
`FnWeb.openInWorld(spec, x, y, z, yaw, widthBlocks, heightBlocks)`. The
README in the repository has the full example and the development switches.

### Status

Early (0.x). The API is small and may still move before 1.0. Issues and
pull requests: GitHub.
