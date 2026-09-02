package dev.devpanda.bamboocef.web;

/**
 * Decides when a browser may paint a frame again.
 *
 * <p><b>An upper bound, not a drive.</b> What stands here can slow Chromium
 * down and never speed it up. The frame rate in windowless mode is not
 * adjustable at all in this JCEF version — there is neither
 * {@code CefBrowserSettings} nor a {@code windowless_frame_rate}, and the
 * native {@code createBrowser} takes no browser settings. Measured, Chromium
 * delivers <b>30.1 frames per second</b>, its default.
 *
 * <p>Whoever enters sixty here gets thirty. Whoever enters five gets five.
 * Only the second direction takes effect.
 *
 * <p><b>Pure timekeeping, without Minecraft and without Chromium</b> — and
 * therefore testable. The clock comes from outside so that a test run can set
 * it; in the game it is {@code System.nanoTime}.
 *
 * <p>The cadence does not accumulate: whoever was not asked for ten frames does
 * not then get ten at once. A browser that drops out of sight and comes back
 * should paint one frame and not catch up on the missed past.
 */
public final class FramePacer {

    private BrowserVisibility visibility;
    private long lastFrameAt = Long.MIN_VALUE;

    public FramePacer(BrowserVisibility visibility) {
        this.visibility = visibility == null ? BrowserVisibility.HIDDEN : visibility;
    }

    public BrowserVisibility visibility() {
        return visibility;
    }

    /**
     * Changes the visibility.
     *
     * <p>A browser that becomes visible may paint at once — otherwise the
     * player sees the old frame for up to a fifth of a second when it unfolds.
     */
    public void setVisibility(BrowserVisibility next) {
        BrowserVisibility wanted = next == null ? BrowserVisibility.HIDDEN : next;
        boolean wakingUp = !visibility.drawing() && wanted.drawing();
        visibility = wanted;
        if (wakingUp) {
            lastFrameAt = Long.MIN_VALUE;
        }
    }

    /**
     * Is a frame due?
     *
     * <p>Does <b>not</b> say that one was painted — Chromium decides that.
     * Whoever gets a yes here and then does nothing after all reports it with
     * {@link #skipped()}, so that the cadence does not run away.
     *
     * @param now the current time in nanoseconds
     */
    public boolean due(long now) {
        long interval = visibility.frameIntervalNanos();
        if (interval < 0) {
            return false;
        }
        if (lastFrameAt == Long.MIN_VALUE) {
            return true;
        }
        return now - lastFrameAt >= interval;
    }

    /** A frame has been produced. */
    public void drawn(long now) {
        lastFrameAt = now;
    }

    /** None was produced after all — the cadence stays put instead of catching up. */
    public void skipped() {
        lastFrameAt = Long.MIN_VALUE;
    }
}
