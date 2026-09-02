package dev.devpanda.bamboocef.web.capture;

/**
 * A finished image, exactly as it goes to the browser.
 *
 * <p><b>Immutable, and that is not a matter of style.</b> Chromium fetches its
 * content on a thread of its own, not the one that draws. A buffer that changes
 * out from under the reader delivers half an old and half a new image — a bug
 * that only shows up sometimes and is therefore never found.
 *
 * <p>The way out is the simplest there is: every image is its own field that
 * nobody touches again. The swap is then a single assignment and needs no lock.
 *
 * @param bytes      the encoded image, as it goes over the wire
 * @param mimeType   what Chromium should make of it
 * @param width      width in pixels
 * @param height     height in pixels
 * @param generation which capture in sequence — it goes into the URL and makes
 *                   Chromium's cache ineffective
 */
public record CapturedFrame(byte[] bytes, String mimeType,
                            int width, int height, long generation) {

    public CapturedFrame {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Ein Bild ohne Inhalt ist keines");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Ein Bild ohne Maße ist keines");
        }
    }

    public int byteCount() {
        return bytes.length;
    }

    /** How many kilobytes, for output in the log. */
    public double kilobytes() {
        return bytes.length / 1024.0;
    }
}
