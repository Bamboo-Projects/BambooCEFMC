package dev.devpanda.bamboocef.web.frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ein Bild im Hauptspeicher, das seine Bildpunkte besitzt.
 *
 * <p><b>Warum kopiert wird.</b> JCEFs {@code onPaint} bekommt seinen
 * {@code ByteBuffer} aus {@code NewDirectByteBuffer} — er zeigt ohne Kopie
 * direkt auf Chromiums Speicher. Nach der Rückkehr aus dem Aufruf darf
 * Chromium ihn wiederverwenden. Wer ihn festhält, liest später fremde Daten.
 *
 * <p><b>Wann man das braucht — und wann nicht.</b> Wer im Aufruf selbst
 * hochlädt, braucht dieses Bild nicht: Solange man im {@code onPaint} steht,
 * sind die Daten gültig, und eine Kopie wäre acht Megabyte Arbeit für nichts.
 * Gebraucht wird es, sobald ein Bild den Aufruf überlebt — beim eigenen
 * Nachrichtentakt, beim Entkoppeln der Bildraten, beim Wiederverwenden. Diese
 * Klasse ist für diesen Fall da und nicht für jeden.
 *
 * <p><b>Es wird immer das ganze Bild kopiert, obwohl nur Teile schmutzig
 * sind.</b> Das sieht nach Verschwendung aus und ist die einzige Fassung, die
 * stimmt: Kopierte man nur die geänderten Bereiche, müsste der Zielpuffer den
 * Rest schon tragen — und sobald zwei Bilder abwechselnd im Umlauf sind, trägt
 * er den Stand von vorletzter Runde. Die geänderten Bereiche werden trotzdem
 * mitgeführt: Beim <i>Hochladen</i> sind sie richtig, denn dort steht auf der
 * anderen Seite genau eine Textur.
 *
 * <p><b>Zum Freigeben.</b> Ein direkter Puffer liegt außerhalb des
 * Java-Speichers, und {@code close()} kann ihn nicht zurückgeben — das kann in
 * Java niemand ohne Umwege. Was {@code close()} tut, ist die Referenz
 * loszulassen. Der eigentliche Weg ist deshalb nicht Wegwerfen, sondern
 * {@link #reuse}: Ein Bild, das zurückkommt, wird neu gefüllt statt neu
 * angelegt. Bei 1080p sind das achteinhalb Megabyte je Bild, die sonst je
 * Bild anfielen.
 */
public final class CpuBrowserFrame implements BrowserFrame {

    /** Vier Byte je Bildpunkt: BGRA, wie Chromium sie liefert. */
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

    /** Ein leeres Bild dieser Größe, mit eigenem Puffer. */
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
     * Übernimmt den Inhalt eines fremden Puffers.
     *
     * <p>Nach der Rückkehr hält dieses Bild nichts mehr von der Quelle — sie
     * darf sofort weiterverwendet werden. Genau das ist der Vertrag, den
     * Chromium braucht.
     *
     * @param source  fremde Bildpunkte, BGRA, Breite × Höhe × 4 Byte
     * @param regions was sich geändert hat; leer bedeutet: alles
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

        // Auf Sicht arbeiten: Weder die Quelle noch der eigene Puffer sollen
        // hinterher eine verschobene Position haben. Ein Aufruf, der die
        // Position eines fremden Puffers ändert, ist ein Fehler, den erst der
        // nächste bemerkt.
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
     * Bereitet dieses Bild auf eine neue Runde vor.
     *
     * <p>Der Puffer bleibt, wenn er groß genug ist. Das ist der Grund, warum
     * {@link FrameSlot#offer} das verdrängte Bild zurückgibt statt es
     * abzuräumen: Es kommt hierher zurück und wird neu gefüllt.
     */
    public CpuBrowserFrame reuse() {
        if (closed) {
            throw new IllegalStateException("Ein geschlossenes Bild kehrt nicht zurück");
        }
        dirty = List.of();
        return this;
    }

    /**
     * Die Bildpunkte, als schreibgeschützte Sicht.
     *
     * <p>Nur für den, der hochlädt. Eine Sicht und nicht der Puffer selbst:
     * Sonst verschiebt der erste Leser die Position, und der zweite liest
     * daneben.
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

    /** Ob dieses Bild schon geschlossen wurde. */
    public boolean isClosed() {
        return closed;
    }
}
