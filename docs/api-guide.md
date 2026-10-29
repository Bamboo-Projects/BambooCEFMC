# API guide

How a NeoForge mod puts web pages into Minecraft with BambooCEF. Everything
here is in `dev.devpanda.bamboocef.web.api`, plus `WebPage`, `WebAssets` and
`BrowserVisibility` from `dev.devpanda.bamboocef.web`. Do not reach past
that; the rest may change.

## Depending on it

```groovy
repositories { maven { url = "https://api.modrinth.com/maven" } }
dependencies { implementation "maven.modrinth:bamboocef:<version>" }
```

`neoforge.mods.toml` — BambooCEF is client-only, so the dependency is too:

```toml
[[dependencies.mymod]]
modId = "bamboocef"
type = "required"
versionRange = "[0.1.0,)"
ordering = "AFTER"
side = "CLIENT"
```

On a platform without a runtime build the library still loads; every call
below then reports the runtime as unavailable, and your mod decides what to
show instead.

## Warm-up

Chromium starts with the first page, and that opener pays a cold start of
one to two seconds. If your mod will need the web, ask early:

```java
// in your FMLClientSetupEvent handler
FnWeb.prepare();
```

BambooCEF then starts the runtime a few seconds after the client is up, at
the title screen — and downloads it first if it is missing. Without this
call the library never starts a browser by itself.

## Describing a page: `SurfaceSpec`

```java
SurfaceSpec spec = SurfaceSpec.of(url, 1280, 720)   // pixels, at most 4096 per edge
        .named("My Menu")                           // shows up in logs and dev tools
        .keys(Keys.EDITOR)                          // which keys the page receives
        .visibility(BrowserVisibility.FOREGROUND)   // frame-rate tier
        .transparent(true);                         // page background shows the game
```

The URL can be anything Chromium can load. To ship a page inside your mod
jar, unpack it to a file first:

```java
String url = WebPage.unpack("assets/mymod/web/menu.html", "menu.html");
```

It lands in `<game>/bamboocef/web/` and is copied afresh on every call, so
an updated jar always wins. `WebAssets.unpack(...)` does the same for a whole
folder listed in a manifest file (the editor ships Monaco that way).

## Four ways to show a page

### A surface you draw yourself — `FnWeb.open(spec)`

Returns a `WebSurface`: a texture (`textureId()`, `width()`, `height()`) that
you draw wherever you like, plus the input methods below. You own it: draw
it, feed it input, and `close()` it when you are done.

### A full-screen interface — `FnWeb.openScreen(spec)`

Opens a Minecraft screen that fills the window with the page, drawn over the
live world (use `transparent(true)` for that effect). Keyboard and mouse go
to the page as the key filter allows. Escape twice, or F10, closes it; so
does `close()`.

### A HUD panel — `FnWeb.openOverlay(spec, x, y)`

Returns a `WebOverlay` drawn on top of the HUD at GUI coordinates while the
game keeps running. `moveTo`, `resize`, `setVisible` as you expect. Focus is
explicit:

| `focus(...)`             | what happens                                             |
|--------------------------|----------------------------------------------------------|
| `OverlayFocus.NONE`      | display only; the game has all input                     |
| `OverlayFocus.KEYBOARD`  | keys the filter allows go to the page; the player still moves; F10 gives the keyboard back |
| `OverlayFocus.MOUSE`     | a screen with a cursor opens over the game; a click outside the panel ends it |

While a screen is open Minecraft's own key mappings are disabled, so mouse
mode is for menus, not for playing.

### A page standing in the world — `FnWeb.openInWorld(spec, x, y, z, yaw, widthBlocks, heightBlocks)`

Returns a `WorldSurface` centred at the position, facing `yaw`, sized in
blocks, visible from both sides. `moveTo`, `rotate(yaw, pitch)`, `resize`,
`maxDistance` (beyond which it is not drawn), `setVisible`. It is closed for
you when the player leaves the world; close it yourself when it should go
earlier.

Clicking is your job, because your mod decides what a click means. On the
player's use-key, ask where the crosshair points and forward the click:

```java
WorldSurfaces.Pick pick = WorldSurfaces.pick(eyeX, eyeY, eyeZ,
        lookX, lookY, lookZ, reachInBlocks);
if (pick != null) {
    pick.surface().mousePressed(pick.x(), pick.y(), 0, 0);
    pick.surface().mouseReleased(pick.x(), pick.y(), 0, 0);
    event.setCanceled(true);   // no swing at the air next to the page
}
```

Feed `mouseMoved` every tick the same way and the page gets hover effects.

## Talking to the page — `WebSurface`

Input, in page pixels: `mouseMoved`, `mouseLeft`, `mousePressed`,
`mouseReleased`, `mouseScrolled`, `keyPressed`, `keyReleased`, `charTyped`,
`setFocused`. The three key methods return whether the page took the key.
`onCursor(IntConsumer)` tells you which cursor the page wants — the
ordinal of CEF's `cef_cursor_type_t` — so you can show it; register nothing
and the game's cursor stays.

Messages from the page: the page calls

```js
window.fnSend("hello");                          // strings as they are
window.fnSend({ chosen: "machines", index: 1 }); // objects JSON-encoded
```

and your mod receives the string:

```java
surface.onMessage(message -> handle(message));
```

Messages to the page use JSON text on the render thread:

```java
surface.postMessage("{\"type\":\"document\",\"source\":\"EVERY 20 TICKS DO\\nEND\"}");
```

```js
window.fnReceive = value => {
    if (value.type === "document") editor.setValue(value.source);
};
// Signal readiness once the editor and fnSend are available.
window.fnSend({ type: "ready" });
```

Register `onMessage` immediately after opening the surface, and send initial
state in response to the page's readiness message. `postMessage` delivers
asynchronously to the active main frame. It does not buffer or acknowledge
messages: a closed surface, missing frame or missing `fnReceive` discards them.
Objects, arrays and JSON primitives are supported; malformed JSON is discarded
by `JSON.parse`, while a Java `null` argument on an open surface throws
`IllegalArgumentException`. Use the text `"null"` to send a JSON null value.
Payloads are transported as escaped string literals and are never evaluated
as code. Existing custom `WebSurface` implementations inherit a default
`postMessage` that throws `UnsupportedOperationException` until implemented.

The shim is injected on every load, so plain HTML pages work; no library on
the page side is needed. Handlers run on the render thread, from the frame
pump that drives Chromium — keep them short and never block in them.

## Keys: `KeyFilter` and `Keys`

A filter answers `routes(glfwKey, modifiers)`. **The press decides**: if a
press is routed to the page, its repeats, its character and its release
follow; if not, the game keeps all of them. Build filters from the presets
in `Keys` or from scratch:

```java
KeyFilter.only(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_ESCAPE)
Keys.TEXT_ENTRY.plus(GLFW.GLFW_KEY_TAB)
Keys.NOT_MOVEMENT                    // everything but WASD, jump, sneak
KeyFilter.ALL                        // the page gets every key (Keys.EDITOR)
KeyFilter.NONE                       // display only
```

Presets: `CLOSE`, `ARROWS`, `CONFIRM`, `NAVIGATION`, `EDITING`,
`TEXT_ENTRY`, `FUNCTION`, `HOTBAR`, `MOVEMENT`, `NOT_MOVEMENT`, `SHORTCUTS`,
`EDITOR`. Combine with `and`, `or`, `plus`, `minus`, `negate`.

## Knowing what is going on

- `FnWeb.available()` — can a page open right now? (This starts the runtime
  if it has not started yet.)
- `FnWeb.status()` — the state and a human-readable reason:
  `NOT_STARTED`, `NOT_DOWNLOADED` (fetching), `RUNTIME_MISSING`,
  `UNSUPPORTED` (this platform), `FAILED`, `SHUT_DOWN`, `READY`. Show the
  reason to the player instead of a boolean; someone behind a proxy needs a
  different message than someone on macOS.
- `FnWeb.liveCount()` — browsers alive across all mods, for inspection.

Every `open*` returns `null` when the runtime is unavailable; check the
status then.

## Rules of the road

- Client only. Never touch the API from server code.
- Close what you open. Logging out closes everything for you; a surface you
  keep across screens is your responsibility.
- Each browser is a Chromium renderer process (roughly 80 MB). A handful is
  fine; dozens are not. Lower the visibility tier of what nobody is looking at.
- Do not cache the runtime status across a game session; ask `status()`.

## Development switches

| Property                              | Effect                                         |
|---------------------------------------|------------------------------------------------|
| `-Dbamboocef.runtime.dir=<dir>`       | use a local runtime instead of downloading     |
| `-Dbamboocef.devtools=true`           | Chromium DevTools for surfaces                 |
| `-Dbamboocef.selftest=true`           | start Chromium after launch and check the frame path |
| `-Dbamboocef.benchmark=true`          | run the render/interaction proof chain         |

In game: `/bamboocef zustand` shows the runtime status; `/bamboocef probe`,
`seite <url>`, `devtools`, `messung` and friends are the runtime's own
development tools.
