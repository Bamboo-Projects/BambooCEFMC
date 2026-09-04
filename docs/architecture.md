# Architecture

How BambooCEF gets a web page onto a Minecraft screen, and why it is built
the way it is. The German reports in this folder (`stand-*.md`,
`review-web-runtime.md`, `grenze-runtime-ide.md`) hold the measurements and
the decisions in detail; this page is the map.

## Two layers, one boundary

- **Public API** — `dev.devpanda.bamboocef.web.api` plus `WebPage`,
  `WebAssets` and `BrowserVisibility`. This is what other mods use; it is
  meant to stay stable.
- **Runtime** — everything else under `web`: the CEF backend, the frame path,
  input translation, the runtime download. Internal, may change.

The runtime never imports anything from a consuming mod, and a test
(`PackageBoundaryTest`) keeps it that way. That is what made it possible to
lift the runtime out of the mod it was born in.

## The backend: java-cef, off-screen

BambooCEF embeds Chromium through **CEF** (Chromium Embedded Framework) and
its Java bindings **java-cef**, built from upstream with a handful of patches
(`tools/runtime/patches/`, read `LIESMICH.md` there). Browsers run
**off-screen**: Chromium paints into memory, never into a window, and the
mod turns those paints into textures.

Three facts shape everything above it:

1. **CEF initialises once per process.** After it has been shut down it will
   not come back; the runtime then reports the terminal state `SHUT_DOWN`
   instead of trying. Chromium is shut down only when the game exits.
2. **One `CefClient`, many browsers.** Every surface is a browser on the same
   shared client, with the same handlers (paint, cursor, console, messages).
3. **Chromium has its own pace.** Off-screen rendering paints at a fixed
   cadence — 30 Hz in upstream CEF, raised to 60 Hz by one of the patches.
   That single change halved the typing latency of the editor
   (`stand-runtime-b89.md`); it was the finding that justified an own
   runtime instead of a third-party one.

The backend lives in `src/runtime/java` (`CefHost`, `FnCefRuntime`,
`FnBrowser`, `FrameHandler`, `WebPump`) and is loaded only when something
asks for it — a lambda in `WebSupport`, so a missing runtime cannot take the
mod down with it.

## The frame path

```
Chromium paint (CEF thread)
  → CpuBrowserFrame with dirty regions        web.frame
  → FrameSlot hands the frame to the game     web.frame
  → upload into a GL texture on the render
    thread, only the dirty part               web.texture, web.view.ManagedTexture
  → drawn as a quad: HUD, screen, or world    web.view.BrowserCompositor, api.WorldSurfaceImpl
```

The pump that lets Chromium do its work runs **once per frame**, before the
game renders (`RenderFrameEvent.Pre` → `WebPump.frame()`); the beat of the
whole runtime comes from that line. A page that does not change paints
nothing, so a resting page costs no upload at all.

Each browser has a **visibility tier** (`BrowserVisibility`: FOREGROUND 60,
ACTIVE 30, NEARBY 15, DISTANT 5, HIDDEN 0 frames per second) that caps how
often it is allowed to paint. Surfaces in the world drop tiers with distance.

## Input

- Keys arrive as GLFW key codes and scan codes and are translated to what
  CEF expects (`web.input`); a patch lets java-cef take raw key events, which
  is what makes umlauts, dead keys and the like come out right.
- A **`KeyFilter`** decides per surface which keys the page gets. The rule
  is *the press decides*: if a key press is routed to the page, its repeats,
  its character and its release follow; otherwise the game keeps all of them
  (`api.KeyRouting`).
- **Overlays** need keys while the game is running. Minecraft's key event is
  not cancellable, so a mixin at the head of `KeyboardHandler.keyPress` and
  `charTyped` offers every key to the overlays first (`mixin`,
  `api.Overlays`). F10 hands the keyboard back to the game.
- **Mouse focus** for an overlay opens a small screen with a cursor; NeoForge
  disables the in-game key mappings while any screen is open, which is why
  the player cannot walk with an overlay in mouse mode.
- Surfaces in the world are clicked through the crosshair: the consumer asks
  `WorldSurfaces.pick(...)` where the player looks and forwards the click.

## Page → mod

A `CefMessageRouter` on the shared client answers `window.fnQuery`; on every
page load the runtime injects a one-line shim so a page can simply call
`window.fnSend(...)` (strings as they are, objects JSON-encoded). Messages
are routed to the surface they came from (`web.runtime.MessageRouting`) and
delivered to `WebSurface.onMessage`.

## Lifecycle

`client.BambooCEFClient` owns the runtime's life in the game: the frame pump,
the ticks of overlays and world surfaces, drawing overlays after the GUI and
world surfaces after the translucent blocks, closing every browser when the
player logs out, and shutting Chromium down when the game exits.
`BrowserManager` keeps the registry of open browsers; `ProcessGuard` puts the
game into a Windows job object with *kill on close*, so Chromium's helper
processes die with the game even when the game does not exit cleanly.

The library **starts nothing on its own**. Chromium comes up with the first
surface — or earlier, at the title screen, when a mod asked for it with
`FnWeb.prepare()` (`web.runtime.WebWarmup`). A self-test that starts
Chromium right after launch exists for development
(`-Dbamboocef.selftest=true`).

## The runtime download

The Chromium binaries are not in the mod jar. What the jar carries:

- the mod, and the CEF Java bindings **nested as a jar-in-jar**
  (`org.cef:jcef`, served from the project's own static Maven at
  `https://bamboocef.bamboo-srv.de/maven`), so a player's game has `org.cef`
  without any extra step;
- a manifest, `bamboocef-runtime.properties` (from
  `tools/runtime/dist.properties`): runtime version, base address, archive
  name and SHA-256 per platform.

On first use `RuntimeInstall` fetches the archive in the background from
`<base-url>/<archive>` into `<game>/bamboocef/runtime/<version>/`, reports
progress, verifies the SHA-256, unpacks, and leaves a marker until the
unpack is complete. A partial download sits in a `.teil` file and does not
get in the way next time. `-Dbamboocef.runtime.dir=<dir>` points the game at
a local runtime instead (what the dev client does).

The runtime's state machine (`WebRuntimeState`): `NOT_STARTED` →
`READY`, or `RUNTIME_MISSING` (no runtime and no address), `UNSUPPORTED`
(no build for this platform), `NOT_DOWNLOADED` (download running or just
set off), `FAILED` (Chromium did not start), `SHUT_DOWN`. A start attempt
that only set the download off is retried by `ensureStarted` itself once the
download is over; a failed Chromium start is remembered and retried only on
request.

The archive lives in the bucket `bamboocef` under one folder per platform —
`minecraft/neoforge/` today, `unreal/` and `unity/` later — behind
`bamboocef.bamboo-srv.de`. It carries the CEF and java-cef license texts.

## Building the native runtime

`tools/runtime/build-jcef.ps1` checks out upstream java-cef (outside the
repository, `C:\fnjcef`, because of Windows path length), applies the
patches, builds against the CEF version pinned in `pin.properties`, and
copies what `files-windows.txt` lists into `build/out/<platform>/`.
`package-runtime.ps1` packs that folder into the archive and writes the
SHA-256 into `dist.properties` — run it with Windows' own `tar` (bsdtar) on
the path, not the one from Git Bash. `manifest.ps1` describes the archives;
the upload goes with `wrangler r2 object put … --remote`.

## Tests

`./gradlew test` never touches the network. Tests that reach for the runtime
use `RuntimeManifest.useForTests` with a manifest without an address, so a
start attempt ends in `RUNTIME_MISSING` instead of a download. Everything
that depends on Chromium itself is proven in the game, not in unit tests —
the German `stand-*.md` reports are those proofs.
