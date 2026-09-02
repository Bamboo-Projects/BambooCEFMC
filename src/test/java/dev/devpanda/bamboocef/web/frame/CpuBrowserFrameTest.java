package dev.devpanda.bamboocef.web.frame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame owns its pixels — tested, not asserted.
 *
 * <p>The case at hand is exactly that of JCEF: a foreign buffer
 * comes in, is overwritten right afterwards, and the frame must still
 * show what was in it at the time of the call.
 */
class CpuBrowserFrameTest {

    private static ByteBuffer bufferOf(int width, int height, byte fill) {
        ByteBuffer buffer = ByteBuffer
                .allocateDirect(width * height * CpuBrowserFrame.BYTES_PER_PIXEL)
                .order(ByteOrder.nativeOrder());
        while (buffer.hasRemaining()) {
            buffer.put(fill);
        }
        return buffer.flip();
    }

    @Test
    @DisplayName("What the foreign buffer does after the call is no concern of the frame")
    void theFrameSurvivesTheSourceBeingOverwritten() {
        ByteBuffer chromium = bufferOf(2, 2, (byte) 0x11);
        CpuBrowserFrame frame = CpuBrowserFrame.allocate(2, 2);
        frame.copyFrom(chromium, 2, 2, List.of());

        // Chromium reuses its buffer — exactly what it may do after
        // returning from onPaint.
        chromium.clear();
        while (chromium.hasRemaining()) {
            chromium.put((byte) 0x77);
        }

        ByteBuffer kept = frame.pixels();
        assertEquals((byte) 0x11, kept.get(0),
                "hätte das Bild den fremden Puffer nur festgehalten, stünde hier 0x77");
        assertEquals(2 * 2 * 4, kept.remaining());
    }

    @Test
    @DisplayName("The foreign buffer is not touched")
    void theSourceIsLeftAlone() {
        ByteBuffer chromium = bufferOf(2, 2, (byte) 0x11);
        int positionBefore = chromium.position();
        int limitBefore = chromium.limit();

        CpuBrowserFrame.allocate(2, 2).copyFrom(chromium, 2, 2, List.of());

        assertEquals(positionBefore, chromium.position(),
                "eine verschobene Position merkt erst der nächste Aufruf");
        assertEquals(limitBefore, chromium.limit());
    }

    @Test
    @DisplayName("With no value given the whole frame counts as changed")
    void noRegionsMeansEverything() {
        CpuBrowserFrame frame = CpuBrowserFrame.allocate(8, 4);
        frame.copyFrom(bufferOf(8, 4, (byte) 1), 8, 4, List.of());

        assertEquals(List.of(DirtyRegion.whole(8, 4)), frame.dirty());
        assertTrue(frame.full());
    }

    @Test
    @DisplayName("Given regions are preserved")
    void regionsAreKept() {
        CpuBrowserFrame frame = CpuBrowserFrame.allocate(8, 4);
        DirtyRegion cursor = new DirtyRegion(2, 1, 1, 1);
        frame.copyFrom(bufferOf(8, 4, (byte) 1), 8, 4, List.of(cursor));

        assertEquals(List.of(cursor), frame.dirty());
        assertEquals(false, frame.full(), "ein Cursor ist nicht das ganze Bild");
        assertEquals(1, cursor.pixels());
    }

    @Test
    @DisplayName("A source that is too small is rejected instead of half-read")
    void aShortSourceIsRefused() {
        CpuBrowserFrame frame = CpuBrowserFrame.allocate(4, 4);
        ByteBuffer tooSmall = bufferOf(2, 2, (byte) 1);

        assertThrows(IllegalArgumentException.class,
                () -> frame.copyFrom(tooSmall, 4, 4, List.of()));
    }

    @Test
    @DisplayName("When the window grows, the buffer grows with it")
    void growingReallocates() {
        CpuBrowserFrame frame = CpuBrowserFrame.allocate(2, 2);
        frame.copyFrom(bufferOf(2, 2, (byte) 1), 2, 2, List.of());
        frame.copyFrom(bufferOf(8, 8, (byte) 2), 8, 8, List.of());

        assertEquals(8, frame.width());
        assertEquals(8, frame.height());
        assertEquals(8 * 8 * 4, frame.pixels().remaining());
        assertEquals((byte) 2, frame.pixels().get(0));
    }

    @Test
    @DisplayName("Reuse keeps the buffer and forgets the regions")
    void reuseKeepsTheBufferAndForgetsTheRegions() {
        CpuBrowserFrame frame = CpuBrowserFrame.allocate(4, 4);
        frame.copyFrom(bufferOf(4, 4, (byte) 5), 4, 4, List.of(new DirtyRegion(0, 0, 1, 1)));

        frame.reuse();
        assertEquals(List.of(), frame.dirty(),
                "sonst lädt der nächste Durchgang den Bereich von vorletzter Runde hoch");

        frame.copyFrom(bufferOf(4, 4, (byte) 9), 4, 4, List.of());
        assertEquals((byte) 9, frame.pixels().get(0));
    }

    @Test
    @DisplayName("A closed frame yields nothing more")
    void aClosedFrameIsDone() {
        CpuBrowserFrame frame = CpuBrowserFrame.allocate(2, 2);
        frame.copyFrom(bufferOf(2, 2, (byte) 1), 2, 2, List.of());
        frame.close();
        frame.close();

        assertTrue(frame.isClosed());
        assertThrows(IllegalStateException.class, frame::pixels);
        assertThrows(IllegalStateException.class,
                () -> frame.copyFrom(bufferOf(2, 2, (byte) 1), 2, 2, List.of()));
        assertThrows(IllegalStateException.class, frame::reuse);
    }

    @Test
    @DisplayName("The view of the pixels is read-only and shares no position")
    void theViewIsReadOnlyAndIndependent() {
        CpuBrowserFrame frame = CpuBrowserFrame.allocate(2, 2);
        frame.copyFrom(bufferOf(2, 2, (byte) 3), 2, 2, List.of());

        ByteBuffer first = frame.pixels();
        first.position(4);
        ByteBuffer second = frame.pixels();

        assertTrue(first.isReadOnly());
        assertNotEquals(first.position(), second.position(),
                "zwei Leser dürfen sich nicht gegenseitig die Position verschieben");
        assertThrows(java.nio.ReadOnlyBufferException.class, () -> second.put(0, (byte) 0));
    }

    @Test
    @DisplayName("No frame without dimensions")
    void sizeMustMakeSense() {
        assertThrows(IllegalArgumentException.class, () -> CpuBrowserFrame.allocate(0, 4));
        assertThrows(IllegalArgumentException.class, () -> new DirtyRegion(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new DirtyRegion(-1, 0, 1, 1));
    }
}
