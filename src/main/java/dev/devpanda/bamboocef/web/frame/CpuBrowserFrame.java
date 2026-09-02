package dev.devpanda.bamboocef.web.frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An image in main memory that owns its pixels.
 *
 * <p><b>Why it copies.</b> JCEF's {@code onPaint} gets its {@code ByteBuffer}
 * from {@code NewDirectByteBuffer} — it points directly at Chromium's memory,
 * without a copy. After the call returns, Chromium may reuse it. Whoever holds
 * on to it reads someone else's data later.
 *
 * <p><b>When you need this — and when not.</b> Whoever uploads within the call
 * itself does not need this image: as long as you are inside {@code onPaint},
 * the data is valid, and a copy would be eight megabytes of work for nothing.
 * It is needed as soon as an image outlives the call — for a message tick of
 * one's own, for decoupling the frame rates, for reuse. This class is for that
 * case and not for every case.
 *
 * <p><b>The whole image is always copied, even though only parts are dirty.</b>
 * That looks like waste and is the only version that is correct: if only the
 * changed regions were copied, the target buffer would have to already carry
 * the rest — and as soon as two images are in rotation alternately, it carries
 * the state from two rounds ago. The changed regions are carried along anyway:
 * on an <i>upload</i> they are right, because there the other side is exactly
 * one texture.
 *
 * <p><b>On releasing.</b> A direct buffer lives outside the Java heap, and
 * {@code close()} cannot hand it back — nobody in Java can, without detours.
 * What {@code close()} does is let go of the reference. The real path is
 * therefore not throwing it away but {@link #reuse}: an image that comes back
 * is refilled instead of freshly allocated. At 1080p that is eight and a half
 * megabytes per image that would otherwise be incurred per image.
 */
public final class CpuBrowserFrame implements BrowserFrame {

    /** Four bytes per pixel: BGRA, as Chromium delivers them. */
    public static final int BYTES_PER_PIXEL = 4;

    private ByteBuffer pixels;
    private int width;
    private int height;
    private List<DirtyRegion> dirty = List.of();
    private boolean closed;

    private CpuBrowserFrame(ByteBuffer pixels, int width, int height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    /** An empty image of this size, with a buffer of its own. */
    public static CpuBrowserFrame allocate(int width, int height) {
        check(width, height);
        return new CpuBrowserFrame(newBuffer(width, height), width, height);
    }

    private static ByteBuffer newBuffer(int width, int height) {
        return ByteBuffer
                .allocateDirect(width * height * BYTES_PER_PIXEL)
                .order(ByteOrder.nativeOrder());
    }

    private static void check(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Kein Bild: " + width + "x" + height);
        }
    }

    /**
     * Takes over the content of an external buffer.
     *
     * <p>After the call returns, this image holds nothing more of the source —
     * it may be reused immediately. That is exactly the contract Chromium
     * needs.
     *
     * @param source  external pixels, BGRA, width × height × 4 bytes
     * @param regions what has changed; empty means: everything
     */
    public void copyFrom(ByteBuffer source, int width, int height,
                         List<DirtyRegion> regions) {
        if (closed) {
            throw new IllegalStateException("Dieses Bild ist geschlossen");
        }
        check(width, height);
        int needed = width * height * BYTES_PER_PIXEL;
        if (source.remaining() < needed) {
            throw new IllegalArgumentException("Die Quelle trägt nur "
                    + source.remaining() + " von " + needed + " Byte");
        }
        if (pixels == null || pixels.capacity() < needed) {
            pixels = newBuffer(width, height);
        }
        this.width = width;
        this.height = height;

        // Work on views: neither the source nor our own buffer should have a
        // shifted position afterwards. A call that changes the position of an
        // external buffer is a bug that only the next call notices.
        ByteBuffer from = source.duplicate();
        from.limit(from.position() + needed);
        ByteBuffer into = pixels.duplicate();
        into.limit(needed);
        into.put(from);

        this.dirty = regions == null || regions.isEmpty()
                ? List.of(DirtyRegion.whole(width, height))
                : Collections.unmodifiableList(new ArrayList<>(regions));
    }

    /**
     * Prepares this image for a new round.
     *
     * <p>The buffer stays if it is big enough. That is the reason why
     * {@link FrameSlot#offer} returns the displaced image instead of clearing
     * it away: it comes back here and is refilled.
     */
    public CpuBrowserFrame reuse() {
        if (closed) {
            throw new IllegalStateException("Ein geschlossenes Bild kehrt nicht zurück");
        }
        dirty = List.of();
        return this;
    }

    /**
     * The pixels, as a read-only view.
     *
     * <p>Only for whoever uploads. A view and not the buffer itself: otherwise
     * the first reader shifts the position and the second reads off the mark.
     */
    public ByteBuffer pixels() {
        if (closed || pixels == null) {
            throw new IllegalStateException("Dieses Bild hat keine Bildpunkte mehr");
        }
        ByteBuffer view = pixels.duplicate();
        view.limit(width * height * BYTES_PER_PIXEL);
        return view.asReadOnlyBuffer();
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public List<DirtyRegion> dirty() {
        return dirty;
    }

    @Override
    public void close() {
        closed = true;
        pixels = null;
        dirty = List.of();
    }

    /** Whether this image has already been closed. */
    public boolean isClosed() {
        return closed;
    }
}
