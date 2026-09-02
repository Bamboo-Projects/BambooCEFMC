package dev.devpanda.bamboocef.web.capture;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The latest Minecraft image at any moment, ready for the browser to fetch.
 *
 * <p><b>Two threads, one assignment.</b> Writing happens on the render thread,
 * reading on Chromium's network thread. Because a {@link CapturedFrame} is
 * immutable, a single atomic assignment is enough — no lock, no copying, and a
 * reader always gets a complete image.
 *
 * <p><b>Exactly one, no more.</b> A history would be useless: whoever requests
 * a background wants the current one. An older one would be wrong, and keeping
 * it around would cost memory in megabyte steps.
 *
 * <p>The counter keeps running even when nobody is looking — it is what goes
 * into the URL and bypasses Chromium's cache.
 */
public final class FrameStore {

    private final AtomicReference<CapturedFrame> latest = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();

    /** Stores a new image and returns its number. */
    public long put(byte[] bytes, String mimeType, int width, int height) {
        long next = generation.incrementAndGet();
        latest.set(new CapturedFrame(bytes, mimeType, width, height, next));
        return next;
    }

    /** The latest image, or {@code null} while there is none. */
    public CapturedFrame latest() {
        return latest.get();
    }

    /** The number of the last capture. */
    public long generation() {
        return generation.get();
    }

    /** Whether anything is there at all yet. */
    public boolean hasFrame() {
        return latest.get() != null;
    }

    /**
     * Forgets the image.
     *
     * <p>On close: keeping a screen full of pixels in memory after nobody is
     * looking any more is, at 1080p, a megabyte for nothing. The counter stays
     * where it is — a number that was already handed out must not come back,
     * or Chromium will show its old image.
     */
    public void clear() {
        latest.set(null);
    }
}
