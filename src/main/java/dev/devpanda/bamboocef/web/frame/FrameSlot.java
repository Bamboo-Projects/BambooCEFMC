package dev.devpanda.bamboocef.web.frame;

/**
 * A mailbox for exactly one image: the newest wins.
 *
 * <p><b>No queue.</b> When the browser paints faster than Minecraft draws, the
 * second-to-last image is worthless — it shows a past that nobody wants to see
 * any more. A growing queue turns that lead into lag: the player types, and the
 * cursor arrives three frames later. That is why every new image displaces the
 * waiting one.
 *
 * <p><b>Ownership moves visibly.</b> {@link #offer} returns what it displaced,
 * and {@link #poll} hands over what it takes out. After both calls the mailbox
 * holds nothing more of it; whoever holds an image closes it or reuses it. That
 * is more cumbersome than a silent {@code close()} inside the mailbox and the
 * only way to attach image recycling later without a rebuild — and without
 * recycling one allocates eight and a half megabytes per image at 1080p.
 *
 * <p><b>On concurrency:</b> {@code onPaint} today arrives on the render thread
 * — there {@code WebPump} pumps Chromium's message loop, once per frame. A
 * mailbox across thread boundaries is therefore <i>not necessary</i>. It is
 * here anyway, for the two cases that are foreseeable: a message tick of one's
 * own, once we hook Chromium to our render loop with {@code external_begin_frame},
 * and the GPU path, where the call does not arrive where the texture is. The
 * synchronization costs one uncontended monitor per frame.
 */
public final class FrameSlot implements AutoCloseable {

    private BrowserFrame pending;
    private boolean closed;

    /** How many images were discarded because the next one was faster. */
    private long dropped;

    /**
     * Deposits an image.
     *
     * @param frame the new image, not {@code null}
     * @return the image that was displaced in the process, or {@code null}.
     *         <b>The caller owns it afterwards</b> and must close it or reuse
     *         it.
     */
    public synchronized BrowserFrame offer(BrowserFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("Ein Bild ohne Bild");
        }
        if (closed) {
            // After closing, the mailbox accepts nothing more. The image still
            // belongs to the caller — closing it silently here would be an
            // ownership transfer that nobody sees.
            return frame;
        }
        BrowserFrame displaced = pending;
        pending = frame;
        if (displaced != null) {
            dropped++;
        }
        return displaced;
    }

    /**
     * Takes out the waiting image.
     *
     * @return the image, or {@code null} if none arrived since the last
     *         retrieval. <b>The caller owns it afterwards.</b>
     */
    public synchronized BrowserFrame poll() {
        BrowserFrame found = pending;
        pending = null;
        return found;
    }

    /** Is an image waiting right now? Only for display — it can be stale at once. */
    public synchronized boolean hasFrame() {
        return pending != null;
    }

    /** How many images were displaced. A measure of how far the browser runs ahead. */
    public synchronized long dropped() {
        return dropped;
    }

    /**
     * Closes the mailbox and the image inside it.
     *
     * <p>Nobody else holds the waiting image — clearing it away here is not a
     * hidden ownership transfer but the last one there is.
     */
    @Override
    public synchronized void close() {
        closed = true;
        if (pending != null) {
            pending.close();
            pending = null;
        }
    }
}
