package dev.devpanda.bamboocef.web.frame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das geliehene Bild — und die Grenze, die der Übersetzer zieht.
 *
 * <p>Der wichtigste Prüflauf dieser Datei ist einer, den man nicht schreiben
 * kann: {@code slot.offer(borrowed)} lässt sich nicht übersetzen, weil
 * {@link BorrowedFrame} kein {@link BrowserFrame} ist. Genau dafür ist die
 * Trennung da — ein geliehener Puffer soll nirgends landen können, wo er den
 * Aufruf überleben müsste.
 */
class BorrowedFrameTest {

    private static ByteBuffer bufferOf(int width, int height, byte fill) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4)
                .order(ByteOrder.nativeOrder());
        while (buffer.hasRemaining()) {
            buffer.put(fill);
        }
        return buffer.flip();
    }

    @Test
    @DisplayName("Ohne Angabe gilt das ganze Bild als geändert")
    void noRegionsMeansEverything() {
        BorrowedFrame frame = new BorrowedFrame(bufferOf(4, 4, (byte) 1), 4, 4, List.of());

        assertEquals(List.of(DirtyRegion.whole(4, 4)), frame.dirty());
        assertTrue(frame.full());
    }

    @Test
    @DisplayName("Ein Cursor ist nicht das ganze Bild")
    void aCursorIsNotEverything() {
        BorrowedFrame frame = new BorrowedFrame(bufferOf(8, 8, (byte) 1), 8, 8,
                List.of(new DirtyRegion(3, 3, 1, 1)));

        assertFalse(frame.full(),
                "sonst lädt ein blinkender Cursor jedes Mal das ganze Bild hoch");
    }

    @Test
    @DisplayName("Jeder Leser bekommt eine eigene Sicht")
    void everyReaderGetsItsOwnView() {
        BorrowedFrame frame = new BorrowedFrame(bufferOf(2, 2, (byte) 7), 2, 2, List.of());

        ByteBuffer first = frame.pixels();
        first.position(8);
        assertEquals(0, frame.pixels().position(),
                "zwei Leser dürfen sich nicht die Position verschieben");
    }

    @Test
    @DisplayName("Der Weg zum Besitz ist eine Kopie, und er überlebt die Quelle")
    void becomingOwnedSurvivesTheSource() {
        ByteBuffer chromium = bufferOf(2, 2, (byte) 0x22);
        BorrowedFrame borrowed = new BorrowedFrame(chromium, 2, 2, List.of());

        CpuBrowserFrame owned = borrowed.toOwned(null);

        // Chromium nimmt sich seinen Speicher zurück.
        chromium.clear();
        while (chromium.hasRemaining()) {
            chromium.put((byte) 0x99);
        }

        assertEquals((byte) 0x22, owned.pixels().get(0),
                "hätte toOwned nur den Zeiger gehalten, stünde hier 0x99");
    }

    @Test
    @DisplayName("Die Kopie darf einen vorhandenen Puffer wiederverwenden")
    void becomingOwnedCanReuseABuffer() {
        CpuBrowserFrame recycled = CpuBrowserFrame.allocate(2, 2);
        BorrowedFrame borrowed = new BorrowedFrame(bufferOf(2, 2, (byte) 5), 2, 2, List.of());

        CpuBrowserFrame owned = borrowed.toOwned(recycled);

        assertEquals(recycled, owned, "sonst entsteht je Bild ein neuer Puffer");
        assertEquals((byte) 5, owned.pixels().get(0));
    }

    @Test
    @DisplayName("Kein Bild ohne Bildpunkte und ohne Maße")
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new BorrowedFrame(null, 2, 2, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new BorrowedFrame(bufferOf(2, 2, (byte) 1), 0, 2, List.of()));
    }
}
