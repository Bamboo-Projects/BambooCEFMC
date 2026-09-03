package dev.devpanda.bamboocef.web.api;

import dev.devpanda.bamboocef.web.BrowserManager;
import dev.devpanda.bamboocef.web.WebRuntimeStatus;
import dev.devpanda.bamboocef.web.WebSupport;
import dev.devpanda.bamboocef.web.runtime.BrowserSession;

/**
 * The entry point into the web runtime.
 *
 * <p>With this class, mod code creates web surfaces: for a screen, for an
 * overlay above the image, for a surface in the world. What lies beneath —
 * Chromium, its sessions, its textures — stays inside.
 *
 * <pre>
 * WebSurface fläche = FnWeb.open(
 *         SurfaceSpec.of("https://example.org", 512, 512)
 *                 .named("Schnellmenü")
 *                 .keys(KeyFilter.only(GLFW.GLFW_KEY_ESCAPE)));
 * if (fläche != null) {
 *     // draw via fläche.textureLocation(), close with close()
 * }
 * </pre>
 *
 * <p><b>Three guarantees.</b>
 *
 * <ol>
 *   <li><b>No type from {@code org.cef} in a signature.</b> Whoever uses this
 *       interface does not compile against Chromium — and the implementation
 *       underneath may change without breaking foreign code.</li>
 *   <li><b>Nothing throws.</b> If the runtime is missing, {@code null} comes
 *       back and {@link #status()} says why. A missing browser costs a
 *       surface, not the client.</li>
 *   <li><b>Everything belongs to the render thread.</b> Chromium's sessions
 *       live there. A call from elsewhere is not a chance error.</li>
 * </ol>
 *
 * <p><b>Version 1.</b> What is public here stays public; new options arrive
 * as another method on {@link SurfaceSpec} and not as another parameter.
 */
public final class FnWeb {

    private FnWeb() {
    }

    /**
     * Opens a surface.
     *
     * <p>Boots the runtime if it is still down. The first call takes a few
     * hundred milliseconds — so call it on first need and not at game start.
     *
     * @return the surface, or {@code null} if none can exist right now. The
     *         reason is in {@link #status()}.
     */
    public static WebSurface open(SurfaceSpec spec) {
        if (!available()) {
            return null;
        }
        try {
            BrowserSession session = BrowserSession.open(spec.url(), spec.transparent(),
                    spec.width(), spec.height(), spec.visibility(), spec.name());
            return new SessionSurface(session, spec.keys());
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Die Fläche {} kam nicht zustande", spec.name(), broken);
            return null;
        }
    }

    /**
     * Can a surface come into being right now?
     *
     * <p>Boots the runtime in the process if it is not yet running.
     */
    /**
     * Opens a surface above the image.
     *
     * <p>The blueprint's width and height count here as <b>screen points</b>,
     * not pixels — the same unit as {@code x} and {@code y}. The surface
     * converts to browser pixels using the GUI scale and re-adjusts on a
     * change. Without focus it is a picture; see
     * {@link WebOverlay#focus(OverlayFocus)}.
     *
     * @param x left edge in screen points
     * @param y top edge in screen points
     * @return the overlay, or {@code null} if there is no browser
     */
    public static WebOverlay openOverlay(SurfaceSpec spec, int x, int y) {
        if (!available()) {
            return null;
        }
        try {
            double guiScale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
            dev.devpanda.bamboocef.web.view.BrowserView view =
                    new dev.devpanda.bamboocef.web.view.BrowserView(
                            x, y, spec.width(), spec.height(), guiScale);
            BrowserSession session = BrowserSession.open(spec.url(), spec.transparent(),
                    view.browserWidth(), view.browserHeight(), spec.visibility(), spec.name());
            OverlayImpl overlay = new OverlayImpl(new SessionSurface(session, spec.keys()), view);
            Overlays.add(overlay);
            return overlay;
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Das Overlay {} kam nicht zustande", spec.name(), broken);
            return null;
        }
    }

    /**
     * Opens a surface in the world.
     *
     * <p>The location is the center in world coordinates, the yaw follows
     * Minecraft's convention (see {@link WorldSurface}), width and height are
     * in blocks. The page's resolution is given in the {@code spec} in pixels
     * and is independent of that.
     *
     * @return the surface, or {@code null} if there is no browser
     */
    public static WorldSurface openInWorld(SurfaceSpec spec, double x, double y, double z,
                                           float yaw, float widthBlocks, float heightBlocks) {
        if (!available()) {
            return null;
        }
        try {
            BrowserSession session = BrowserSession.open(spec.url(), spec.transparent(),
                    spec.width(), spec.height(), spec.visibility(), spec.name());
            WorldSurfaceImpl surface = new WorldSurfaceImpl(
                    new SessionSurface(session, spec.keys()),
                    x, y, z, yaw, widthBlocks, heightBlocks);
            WorldSurfaces.add(surface);
            return surface;
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Die Weltfläche {} kam nicht zustande", spec.name(), broken);
            return null;
        }
    }

    /**
     * Opens a surface as a screen over the whole image.
     *
     * <p>The size comes from the window, not from the blueprint — a screen
     * fills it and re-adjusts on a scale change. The background shows through
     * when the blueprint is {@code transparent}; behind it lies Minecraft's
     * blurred world.
     *
     * <p>If the blueprint leaves the keys open, the screen gets them all — an
     * editor without keys would be pointless, and the game rests anyway as
     * long as a screen is open.
     *
     * <p>Two presses of Escape close it, F10 in any case. The caller attaches
     * its {@code onMessage} to the returned surface and checks {@code alive()};
     * the closing is handled by the screen.
     *
     * @return the surface, or {@code null} if there is no browser
     */
    public static WebSurface openScreen(SurfaceSpec spec) {
        if (!available()) {
            return null;
        }
        try {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            var window = client.getWindow();
            dev.devpanda.bamboocef.web.view.BrowserView view =
                    dev.devpanda.bamboocef.web.view.BrowserView.fullscreen(
                            window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                            window.getGuiScale());
            KeyFilter keys = spec.keys() == KeyFilter.NONE ? KeyFilter.ALL : spec.keys();
            BrowserSession session = BrowserSession.open(spec.url(), spec.transparent(),
                    view.browserWidth(), view.browserHeight(),
                    dev.devpanda.bamboocef.web.BrowserVisibility.FOREGROUND, spec.name());
            SessionSurface surface = new SessionSurface(session, keys);
            client.setScreen(new WebScreenImpl(surface, view, spec.transparent()));
            return surface;
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Der Web-Bildschirm {} kam nicht zustande", spec.name(), broken);
            return null;
        }
    }

    /**
     * Asks for the runtime ahead of the first surface.
     *
     * <p>Chromium comes up lazily with the first surface, and that opener
     * pays the cold start. A mod that knows it will need the web calls this
     * once, early — at client setup, say. The library then starts the runtime
     * a few seconds after the client is up, at the title screen, and fetches
     * the runtime first if it is missing. Idempotent; without this call the
     * library never starts a browser on its own.
     */
    public static void prepare() {
        dev.devpanda.bamboocef.web.runtime.WebWarmup.request();
    }

    public static boolean available() {
        return WebSupport.ensureStarted().usable();
    }

    /**
     * Why it works or does not work.
     *
     * <p>Five states and not a boolean: whoever is missing the runtime needs a
     * different message than someone sitting behind a proxy.
     */
    public static WebRuntimeStatus status() {
        return dev.devpanda.bamboocef.web.WebRuntime.status();
    }

    /**
     * How many browsers are alive right now — across all surfaces.
     *
     * <p>For inspection, not for control: whoever needs their own cap counts
     * their own surfaces. This number includes those of others too.
     */
    public static int liveCount() {
        return BrowserManager.count();
    }
}
