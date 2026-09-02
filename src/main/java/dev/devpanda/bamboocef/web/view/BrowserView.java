package dev.devpanda.bamboocef.web.view;

/**
 * Where a browser lies, how large it is — and the one conversion between them.
 *
 * <p><b>Three units that nobody may confuse.</b>
 *
 * <table border="1">
 *   <caption>The units and where they apply</caption>
 *   <tr><th>Unit</th><th>Source</th><th>Purpose</th></tr>
 *   <tr>
 *     <td><b>Framebuffer pixels</b></td>
 *     <td>{@code window.getWidth()}</td>
 *     <td>What the graphics card really has. On a 4K screen four times as
 *         many as on Full HD.</td>
 *   </tr>
 *   <tr>
 *     <td><b>GUI units</b></td>
 *     <td>{@code screen.width}, mouse coordinates</td>
 *     <td>Minecraft's own grid. At GUI scale 3 one GUI unit is three
 *         framebuffer pixels wide.</td>
 *   </tr>
 *   <tr>
 *     <td><b>Browser pixels</b></td>
 *     <td>{@code browser.resize(w, h)}</td>
 *     <td>How large Chromium paints its page. Here equal to CSS pixels,
 *         because this JCEF build sets no {@code device_scale_factor}.</td>
 *   </tr>
 * </table>
 *
 * <p><b>The decision: one browser pixel is one framebuffer pixel.</b> The
 * browser gets as many pixels as the area really occupies on screen — not as
 * many as Minecraft's grid grants it. Otherwise text at GUI scale 3 would be
 * computed at a third of the resolution and blown up again when drawn. For an
 * editor that is the difference between legible and mushy.
 *
 * <p><b>And why this lives here and not twice elsewhere.</b> Drawing and mouse
 * must use the same computation, or the player clicks next to what they see — a
 * bug that disguises itself as "the page reacts wrongly" and is hunted for
 * days. Later a ray onto a surface in the world should compute the same way: it
 * yields a fraction from 0 to 1 and goes through
 * {@link #fromFraction(double, double)} into the same plane.
 *
 * <p>Pure computation, no Minecraft. The scale comes in as a number so that it
 * can be checked.
 *
 * @param guiX      left edge in GUI units
 * @param guiY      top edge in GUI units
 * @param guiWidth  width in GUI units
 * @param guiHeight height in GUI units
 * @param guiScale  how many framebuffer pixels one GUI unit measures
 */
public record BrowserView(int guiX, int guiY, int guiWidth, int guiHeight, double guiScale) {

    public BrowserView {
        if (guiWidth <= 0 || guiHeight <= 0) {
            throw new IllegalArgumentException("Eine Fläche ohne Ausdehnung zeigt nichts");
        }
        if (guiScale <= 0.0) {
            throw new IllegalArgumentException("Eine Skalierung von null macht jede Umrechnung sinnlos");
        }
    }

    /** The whole surface, without a border. */
    public static BrowserView fullscreen(int guiWidth, int guiHeight, double guiScale) {
        return new BrowserView(0, 0, guiWidth, guiHeight, guiScale);
    }

    /**
     * How wide the browser should paint, in browser pixels.
     *
     * <p>At least one: Chromium with width zero never paints again, and a
     * window being dragged shut passes through this state.
     */
    public int browserWidth() {
        return Math.max(1, (int) Math.round(guiWidth * guiScale));
    }

    public int browserHeight() {
        return Math.max(1, (int) Math.round(guiHeight * guiScale));
    }

    /** Does this point in GUI units lie over the surface? */
    public boolean contains(double guiMouseX, double guiMouseY) {
        return guiMouseX >= guiX && guiMouseX < guiX + guiWidth
                && guiMouseY >= guiY && guiMouseY < guiY + guiHeight;
    }

    /**
     * A mouse point in GUI units becomes a browser pixel.
     *
     * <p>Clamped to the surface: feeding Chromium a coordinate outside its
     * window cancels drag operations that are still running. Whoever wants to
     * know whether the pointer is inside at all asks {@link #contains}.
     */
    public int toBrowserX(double guiMouseX) {
        return clamp((guiMouseX - guiX) * guiScale, browserWidth());
    }

    public int toBrowserY(double guiMouseY) {
        return clamp((guiMouseY - guiY) * guiScale, browserHeight());
    }

    /**
     * A fraction from 0 to 1 becomes a browser pixel.
     *
     * <p>The path for a surface in the world: a ray hits it somewhere, and
     * where exactly is most readily expressed as a fraction. That way a screen
     * on the wall lands in the same plane as this screen — and in the same
     * computation.
     *
     * @return browser pixels as {@code [x, y]}
     */
    public int[] fromFraction(double acrossFraction, double downFraction) {
        return new int[] {
                clamp(acrossFraction * browserWidth(), browserWidth()),
                clamp(downFraction * browserHeight(), browserHeight())
        };
    }

    /**
     * A browser pixel becomes a point in GUI units.
     *
     * <p>For everything Chromium reports in its own coordinates and that has to
     * go to the right spot on screen — an expanded dropdown, for example.
     *
     * @return GUI units as {@code [x, y]}
     */
    public double[] toGui(int browserX, int browserY) {
        return new double[] {guiX + browserX / guiScale, guiY + browserY / guiScale};
    }

    /** A length in browser pixels becomes a length in GUI units. */
    public double lengthToGui(int browserLength) {
        return browserLength / guiScale;
    }

    /** The same surface at a different size — when dragging the window edge. */
    public BrowserView resized(int newGuiWidth, int newGuiHeight, double newGuiScale) {
        return new BrowserView(guiX, guiY, newGuiWidth, newGuiHeight, newGuiScale);
    }

    /** Whether a message to Chromium is needed at all. */
    public boolean sameSizeAs(BrowserView other) {
        return other != null
                && browserWidth() == other.browserWidth()
                && browserHeight() == other.browserHeight();
    }

    private static int clamp(double value, int limit) {
        int rounded = (int) Math.floor(value);
        return Math.max(0, Math.min(limit - 1, rounded));
    }
}
