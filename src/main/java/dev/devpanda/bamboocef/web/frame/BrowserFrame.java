package dev.devpanda.bamboocef.web.frame;

import java.util.List;

/**
 * An image of the browser.
 *
 * <p><b>An image owns its pixels.</b> That is not a preference but follows from
 * JCEF: its {@code onPaint} hands in a {@code ByteBuffer} that the native side
 * places directly onto Chromium's memory with {@code NewDirectByteBuffer} —
 * without a copy. After the call returns, Chromium may reuse that memory. An
 * image that merely holds on to the buffer shows something else, or nothing, at
 * the next glance.
 *
 * <p>That is why the implementation copies within the call, and why an image
 * has a {@link #close()}: direct buffers live outside the Java heap, and the
 * collector reclaims them late or never. At eight and a half megabytes per
 * image, "late" is the same as "never".
 *
 * <p><b>This type means ownership.</b> What is only borrowed — the buffer from
 * a running {@code onPaint} — is called {@link BorrowedFrame} and is
 * deliberately <i>not</i> related to this one. That way a borrowed buffer can
 * end up nowhere that would require it to outlive the call: the compiler
 * rejects it, and nobody has to remember a rule.
 *
 * <p>What is <b>not</b> stated here is the kind of pixels. No
 * {@code ByteBuffer}, no texture name, no handle. An image is size and change;
 * what it is made of is known only to whoever produces it and whoever consumes
 * it — and precisely for that reason an image can be added later that lives on
 * the graphics card and was never in main memory.
 */
public interface BrowserFrame extends AutoCloseable {

    int width();

    int height();

    /**
     * Which regions have changed since the previous image.
     *
     * <p>Never empty: an image without a change is not an image.
     */
    List<DirtyRegion> dirty();

    /** Does the change cover the whole image? */
    default boolean full() {
        List<DirtyRegion> regions = dirty();
        return regions.size() == 1
                && regions.get(0).equals(DirtyRegion.whole(width(), height()));
    }

    /** Releases the pixels. Closing more than once is allowed. */
    @Override
    void close();
}
