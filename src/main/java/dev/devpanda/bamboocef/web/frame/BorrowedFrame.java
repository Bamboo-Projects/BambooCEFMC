package dev.devpanda.bamboocef.web.frame;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * A borrowed image: valid only while the call is running.
 *
 * <p><b>Not a {@link BrowserFrame}.</b> That is the whole point of this class.
 * It is unrelated to the owning image, and so nobody can put it into a
 * {@link FrameSlot} — the compiler will not allow it. A rule you have to keep
 * gets broken eventually; one that cannot be broken does not.
 *
 * <p>Where the loan comes from: JCEF's {@code onPaint} hands in a
 * {@code ByteBuffer} that the native side places directly onto Chromium's
 * memory with {@code NewDirectByteBuffer}. After the call returns, Chromium may
 * reuse it. Whoever keeps it reads someone else's data later — and does not
 * notice, because what sits there is usually something plausible.
 *
 * <p><b>Exactly one thing is allowed:</b> reading it within the same call.
 * Uploading counts. Everything else — remembering it, passing it on, sending it
 * across a thread — is a bug, and {@link #toOwned} is the only way out.
 */
public final class BorrowedFrame {

    private final ByteBuffer pixels;
    private final int width;
    private final int height;
    private final List<DirtyRegion> dirty;

    public BorrowedFrame(ByteBuffer pixels, int width, int height,
                         List<DirtyRegion> dirty) {
        if (pixels == null) {
            throw new IllegalArgumentException("Ein Bild ohne Bildpunkte");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Kein Bild: " + width + "x" + height);
        }
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.dirty = dirty == null || dirty.isEmpty()
                ? List.of(DirtyRegion.whole(width, height))
                : List.copyOf(dirty);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public List<DirtyRegion> dirty() {
        return dirty;
    }

    /** Does the change cover the whole image? */
    public boolean full() {
        return dirty.size() == 1 && dirty.get(0).equals(DirtyRegion.whole(width, height));
    }

    /**
     * The borrowed pixels.
     *
     * <p>A view of its own, so that two readers do not shift each other's
     * position. The memory behind it still belongs to Chromium.
     */
    public ByteBuffer pixels() {
        return pixels.duplicate();
    }

    /**
     * Makes a copy that outlives the call.
     *
     * <p>The only way from the borrowed to the owning image — and it costs what
     * a copy costs. Whoever uploads within the call does not need it.
     *
     * @param into an image whose buffer is reused
     */
    public CpuBrowserFrame toOwned(CpuBrowserFrame into) {
        CpuBrowserFrame target = into == null
                ? CpuBrowserFrame.allocate(width, height) : into;
        target.copyFrom(pixels(), width, height, dirty);
        return target;
    }
}
