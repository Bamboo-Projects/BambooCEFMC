package dev.devpanda.bamboocef.web.frame;

/**
 * A rectangle that has changed.
 *
 * <p>Chromium reports them with every image. Whoever ignores them uploads eight
 * and a half megabytes for a blinking cursor; whoever heeds them, a few hundred
 * bytes. That is why the information is in the image from the start and is not
 * retrofitted later.
 *
 * @param x      left edge in pixels
 * @param y      top edge
 * @param width  width
 * @param height height
 */
public record DirtyRegion(int x, int y, int width, int height) {

    public DirtyRegion {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Kein Bereich: " + x + "," + y + " " + width + "x" + height);
        }
    }

    /** How many pixels lie within it. */
    public long pixels() {
        return (long) width * height;
    }

    /** The region that covers a whole image of this size. */
    public static DirtyRegion whole(int width, int height) {
        return new DirtyRegion(0, 0, width, height);
    }
}
