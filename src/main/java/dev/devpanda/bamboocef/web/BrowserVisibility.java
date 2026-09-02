package dev.devpanda.bamboocef.web;

/**
 * How visible a browser currently is — and how many frames it gets for that.
 *
 * <p><b>No browser gets what it wants.</b> Chromium paints as fast as it can;
 * with a dozen displays in one base that is the fastest way to ruin a frame
 * rate. What a browser really needs does not depend on it but on who is looking
 * at it: an editor under the fingers needs every keystroke at once, a board at
 * the far end of the hall does not.
 *
 * <p><b>There is headroom to gain here — since CEF 146.</b> The numbers apply
 * in both directions: {@code CefBrowserSettings.windowless_frame_rate} takes
 * them, we set sixty, and measured, <b>60.3 frames per second</b> arrive. The
 * earlier JCEF version knew neither the setting nor a way to pass it; there
 * thirty was the ceiling, and these levels could only throttle.
 *
 * <p>The numbers below {@link #FOREGROUND} are a start and not a measurement.
 * They stand here so that throttling exists from the outset — introduced
 * afterwards it would be a rebuild of every call site.
 */
public enum BrowserVisibility {

    /**
     * Full frame, under the player's fingers.
     *
     * <p>The sixty is reached: measured, 60.3 frames per second arrive. This
     * level therefore throttles nothing — it is the number set when the browser
     * is built.
     */
    FOREGROUND(60),

    /** Open and visible, but not what someone is typing on. */
    ACTIVE(30),

    /** A surface in the world, near enough to read. */
    NEARBY(15),

    /** Too far away to make out text — it is enough that something stirs. */
    DISTANT(5),

    /**
     * No one is looking.
     *
     * <p>Zero and not one: a browser that shows no one anything should cost
     * nothing. Chromium is additionally reported as invisible — in this JCEF
     * version that is {@code setWindowVisibility(false)} —, then its own timers
     * stop too.
     */
    HIDDEN(0);

    private final int framesPerSecond;

    BrowserVisibility(int framesPerSecond) {
        this.framesPerSecond = framesPerSecond;
    }

    public int framesPerSecond() {
        return framesPerSecond;
    }

    /** The gap between two frames in nanoseconds, or {@code -1} for never. */
    public long frameIntervalNanos() {
        return framesPerSecond <= 0 ? -1L : 1_000_000_000L / framesPerSecond;
    }

    /** Should this browser still paint at all? */
    public boolean drawing() {
        return framesPerSecond > 0;
    }
}
