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
 * The borrowed frame — and the boundary the compiler draws.
 *
 * <p>The most important test run of this file is one you cannot write:
 * {@code slot.offer(borrowed)} does not compile, because
 * {@link BorrowedFrame} is not a {@link BrowserFrame}. That is exactly what the
 * separation is for — a borrowed buffer must not be able to land anywhere it would
 * have to outlive the call.
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
    @DisplayName("With no value given the whole frame counts as changed")
    void noRegionsMeansEverything() {
        BorrowedFrame frame = new BorrowedFrame(bufferOf(4, 4, (byte) 1), 4, 4, List.of());

        assertEquals(List.of(DirtyRegion.whole(4, 4)), frame.dirty());
        assertTrue(frame.full());
    }

    @Test
    @DisplayName("A cursor is not the whole frame")
    void aCursorIsNotEverything() {
        BorrowedFrame frame = new BorrowedFrame(bufferOf(8, 8, (byte) 1), 8, 8,
                List.of(new DirtyRegion(3, 3, 1, 1)));

        assertFalse(frame.full(),
                "sonst lädt ein blinkender Cursor jedes Mal das ganze Bild hoch");
    }

    @Test
    @DisplayName("Every reader gets its own view")
    void everyReaderGetsItsOwnView() {
        BorrowedFrame frame = new BorrowedFrame(bufferOf(2, 2, (byte) 7), 2, 2, List.of());

        ByteBuffer first = frame.pixels();
        first.position(8);
        assertEquals(0, frame.pixels().position(),
                "zwei Leser dürfen sich nicht die Position verschieben");
    }

    @Test
    @DisplayName("The path to ownership is a copy, and it survives the source")
    void becomingOwnedSurvivesTheSource() {
        ByteBuffer chromium = bufferOf(2, 2, (byte) 0x22);
        BorrowedFrame borrowed = new BorrowedFrame(chromium, 2, 2, List.of());

        CpuBrowserFrame owned = borrowed.toOwned(null);

        // Chromium takes its memory back.
        chromium.clear();
        while (chromium.hasRemaining()) {
            chromium.put((byte) 0x99);
        }

        assertEquals((byte) 0x22, owned.pixels().get(0),
                "hätte toOwned nur den Zeiger gehalten, stünde hier 0x99");
    }

    @Test
    @DisplayName("The copy may reuse an existing buffer")
    void becomingOwnedCanReuseABuffer() {
        CpuBrowserFrame recycled = CpuBrowserFrame.allocate(2, 2);
        BorrowedFrame borrowed = new BorrowedFrame(bufferOf(2, 2, (byte) 5), 2, 2, List.of());

        CpuBrowserFrame owned = borrowed.toOwned(recycled);

        assertEquals(recycled, owned, "sonst entsteht je Bild ein neuer Puffer");
        assertEquals((byte) 5, owned.pixels().get(0));
    }

    @Test
    @DisplayName("No frame without pixels and without dimensions")
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new BorrowedFrame(null, 2, 2, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new BorrowedFrame(bufferOf(2, 2, (byte) 1), 0, 2, List.of()));
    }
}
