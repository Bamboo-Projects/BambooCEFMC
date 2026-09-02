package dev.devpanda.bamboocef.web.frame;

/**
 * Ein Rechteck, das sich geändert hat.
 *
 * <p>Chromium meldet sie zu jedem Bild. Wer sie ignoriert, lädt für einen
 * blinkenden Cursor achteinhalb Megabyte hoch; wer sie beachtet, ein paar
 * hundert Byte. Deshalb steht die Angabe von Anfang an im Bild und wird nicht
 * später nachgerüstet.
 *
 * @param x      linke Kante in Bildpunkten
 * @param y      obere Kante
 * @param width  Breite
 * @param height Höhe
 */
public record DirtyRegion(int x, int y, int width, int height) {

    public DirtyRegion {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Kein Bereich: " + x + "," + y + " " + width + "x" + height);
        }
    }

    /** Wie viele Bildpunkte darin liegen. */
    public long pixels() {
        return (long) width * height;
    }

    /** Der Bereich, der ein ganzes Bild dieser Größe abdeckt. */
    public static DirtyRegion whole(int width, int height) {
        return new DirtyRegion(0, 0, width, height);
    }
}
