package dev.devpanda.bamboocef.web.api;

/**
 * A surface above the image, in screen points.
 *
 * <p><b>Screen points, not pixels.</b> Position and size are the units in
 * which Minecraft draws its interface — the same ones the player chooses with
 * the GUI scale. The surface converts to browser pixels itself and re-sharpens
 * when the scale changes. Whoever thinks in pixels gets a different size on
 * every scale change.
 *
 * <p>It is drawn above the game, beneath any open screen, in the order of
 * opening. F1 hides the surface along with the rest of the interface.
 *
 * <p>Input hangs on {@link OverlayFocus}: without focus the surface is a
 * picture. With keyboard focus it gets what its {@link KeyFilter} takes, and
 * the player keeps the mouse and the rest. With mouse focus the pointer is
 * free. <b>F10 ends any focus</b>, no matter what the filter says — otherwise
 * there would be a surface no one can get out of.
 *
 * <p>Version 1. What is here stays.
 */
public interface WebOverlay extends AutoCloseable {

    /** The surface beneath: texture, size in pixels, input by hand. */
    WebSurface surface();

    /** Left edge, in screen points. */
    int x();

    /** Top edge, in screen points. */
    int y();

    /** Width in screen points. */
    int width();

    /** Height in screen points. */
    int height();

    void moveTo(int x, int y);

    void resize(int width, int height);

    boolean visible();

    /** Invisible means: not drawn, but open — and without focus. */
    void setVisible(boolean visible);

    OverlayFocus focus();

    /**
     * Give or take focus.
     *
     * <p>Only one overlay has focus; whoever gets it takes it from the other.
     * The change takes effect immediately, even in the middle of a key: what
     * was already pressed counts as released for the page.
     */
    void focus(OverlayFocus wanted);

    /** Open and with a live browser beneath. */
    boolean alive();

    @Override
    void close();
}
