package dev.devpanda.bamboocef.web.frame;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Ein geliehenes Bild: gültig nur, solange der Aufruf läuft.
 *
 * <p><b>Kein {@link BrowserFrame}.</b> Das ist die ganze Absicht dieser
 * Klasse. Sie ist mit dem besitzenden Bild nicht verwandt, und deshalb kann
 * niemand sie in ein {@link FrameSlot} legen — der Übersetzer lässt es nicht
 * zu. Eine Regel, die man einhalten muss, wird irgendwann gebrochen; eine, die
 * sich nicht brechen lässt, nicht.
 *
 * <p>Woher die Leihe kommt: JCEFs {@code onPaint} reicht einen
 * {@code ByteBuffer} herein, den der native Teil mit
 * {@code NewDirectByteBuffer} direkt auf Chromiums Speicher legt. Nach der
 * Rückkehr aus dem Aufruf darf Chromium ihn wiederverwenden. Wer ihn
 * aufhebt, liest später fremde Daten — und merkt es nicht, weil dort meistens
 * etwas Plausibles steht.
 *
 * <p><b>Erlaubt ist genau eines:</b> ihn im selben Aufruf zu lesen. Hochladen
 * zählt dazu. Alles andere — merken, weitergeben, über einen Thread schicken —
 * ist ein Fehler, und {@link #toOwned} ist der einzige Weg daraus.
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

    /** Deckt die Änderung das ganze Bild ab? */
    public boolean full() {
        return dirty.size() == 1 && dirty.get(0).equals(DirtyRegion.whole(width, height));
    }

    /**
     * Die geliehenen Bildpunkte.
     *
     * <p>Eine eigene Sicht, damit zwei Leser sich nicht die Position
     * verschieben. Der Speicher dahinter gehört weiterhin Chromium.
     */
    public ByteBuffer pixels() {
        return pixels.duplicate();
    }

    /**
     * Macht eine Kopie, die den Aufruf überlebt.
     *
     * <p>Der einzige Weg vom geliehenen zum besitzenden Bild — und er kostet,
     * was eine Kopie kostet. Wer im Aufruf hochlädt, braucht ihn nicht.
     *
     * @param into ein Bild, dessen Puffer wiederverwendet wird
     */
    public CpuBrowserFrame toOwned(CpuBrowserFrame into) {
        CpuBrowserFrame target = into == null
                ? CpuBrowserFrame.allocate(width, height) : into;
        target.copyFrom(pixels(), width, height, dirty);
        return target;
    }
}
