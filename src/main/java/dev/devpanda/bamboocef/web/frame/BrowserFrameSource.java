package dev.devpanda.bamboocef.web.frame;

/**
 * Where images come from.
 *
 * <p>Today that is a browser whose {@code onPaint} deposits into a
 * {@link FrameSlot}. Later it can be the same source with a texture on the
 * graphics card, without anything downstream noticing — which is why there is a
 * {@link BrowserFrame} here and not a buffer.
 */
public interface BrowserFrameSource {

    /**
     * The latest image, or {@code null}.
     *
     * <p><b>The caller owns it</b> and must close it or return it. Called twice
     * in a row without anything new having been painted, the source answers
     * {@code null} the second time — an image is not handed out twice.
     */
    BrowserFrame poll();
}
