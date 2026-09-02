package dev.devpanda.bamboocef.web.frame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Postfach: das neueste Bild gewinnt, und der Besitz ist sichtbar.
 *
 * <p>Beides lässt sich ohne Minecraft prüfen, und beides ist der Grund, warum
 * es das Postfach gibt. Ein verdrängtes Bild, das niemand zurückbekommt, ist
 * ein Speicherleck von achteinhalb Megabyte je Bild.
 */
class FrameSlotTest {

    /** Ein Bild, das nur zählt, ob es geschlossen wurde. */
    private static final class CountingFrame implements BrowserFrame {
        private final int mark;
        private int closes;

        CountingFrame(int mark) {
            this.mark = mark;
        }

        @Override
        public int width() {
            return 4;
        }

        @Override
        public int height() {
            return 4;
        }

        @Override
        public List<DirtyRegion> dirty() {
            return List.of(DirtyRegion.whole(4, 4));
        }

        @Override
        public void close() {
            closes++;
        }
    }

    @Test
    @DisplayName("Ein leeres Postfach gibt nichts heraus")
    void emptySlotYieldsNothing() {
        try (FrameSlot slot = new FrameSlot()) {
            assertNull(slot.poll());
            assertFalse(slot.hasFrame());
        }
    }

    @Test
    @DisplayName("Was abgelegt wurde, kommt heraus — genau einmal")
    void whatGoesInComesOutOnce() {
        CountingFrame frame = new CountingFrame(1);
        try (FrameSlot slot = new FrameSlot()) {
            assertNull(slot.offer(frame), "das erste Bild verdrängt nichts");
            assertTrue(slot.hasFrame());
            assertSame(frame, slot.poll());
            assertNull(slot.poll(), "ein Bild wird nicht zweimal ausgegeben");
        }
    }

    @Test
    @DisplayName("Das neueste Bild gewinnt, und das alte kommt zurück")
    void theNewestFrameWins() {
        CountingFrame first = new CountingFrame(1);
        CountingFrame second = new CountingFrame(2);
        try (FrameSlot slot = new FrameSlot()) {
            slot.offer(first);
            BrowserFrame displaced = slot.offer(second);

            assertSame(first, displaced,
                    "das verdrängte Bild muss zurückkommen — sonst ist es verloren");
            assertSame(second, slot.poll(), "herauskommen muss das neueste");
            assertEquals(0, first.closes,
                    "das Postfach schließt es nicht selbst: Der Aufrufer soll es "
                            + "wiederverwenden können");
        }
    }

    @Test
    @DisplayName("Drei Bilder ohne Abholung: zwei werden verdrängt")
    void everySkippedFrameIsCounted() {
        try (FrameSlot slot = new FrameSlot()) {
            slot.offer(new CountingFrame(1));
            slot.offer(new CountingFrame(2));
            slot.offer(new CountingFrame(3));

            assertEquals(2, slot.dropped(),
                    "wie weit der Browser vorausläuft, muss messbar sein");
            assertEquals(3, ((CountingFrame) slot.poll()).mark);
        }
    }

    @Test
    @DisplayName("Schließen räumt das wartende Bild ab")
    void closingReleasesThePendingFrame() {
        CountingFrame waiting = new CountingFrame(1);
        FrameSlot slot = new FrameSlot();
        slot.offer(waiting);
        slot.close();

        assertEquals(1, waiting.closes,
                "das wartende Bild hat niemand sonst — es muss hier freigegeben werden");
        assertNull(slot.poll());
    }

    @Test
    @DisplayName("Nach dem Schließen behält der Aufrufer sein Bild")
    void afterClosingTheCallerKeepsOwnership() {
        FrameSlot slot = new FrameSlot();
        slot.close();

        CountingFrame late = new CountingFrame(1);
        assertSame(late, slot.offer(late),
                "es zurückzugeben ist die einzige Antwort, bei der niemand rät, "
                        + "wem es gehört");
        assertEquals(0, late.closes, "und geschlossen wird es hier nicht");
    }

    @Test
    @DisplayName("Zweimal schließen tut nichts Zweites")
    void closingTwiceIsHarmless() {
        CountingFrame frame = new CountingFrame(1);
        FrameSlot slot = new FrameSlot();
        slot.offer(frame);
        slot.close();
        slot.close();

        assertEquals(1, frame.closes);
    }

    @Test
    @DisplayName("Unter Nebenläufigkeit geht kein Bild verloren und keines doppelt heraus")
    void nothingIsLostOrDuplicatedUnderConcurrency() throws Exception {
        // Nicht als Beweis der Nebenläufigkeit — den führt kein Test —, sondern
        // gegen den groben Fehler: ein Bild, das zweimal herauskommt, oder eins,
        // das weder ausgegeben noch verdrängt wurde. Beides wäre ein Leck.
        final int rounds = 2_000;
        FrameSlot slot = new FrameSlot();
        AtomicInteger consumed = new AtomicInteger();
        AtomicInteger displaced = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            while (done.getCount() > 0 || slot.hasFrame()) {
                if (slot.poll() != null) {
                    consumed.incrementAndGet();
                }
            }
        });
        consumer.start();

        for (int i = 0; i < rounds; i++) {
            if (slot.offer(new CountingFrame(i)) != null) {
                displaced.incrementAndGet();
            }
        }
        done.countDown();
        consumer.join(TimeUnit.SECONDS.toMillis(10));

        assertEquals(rounds, consumed.get() + displaced.get(),
                "jedes Bild ist entweder verbraucht oder verdrängt worden — "
                        + "ein drittes Schicksal wäre ein Leck");
        slot.close();
    }

    @Test
    @DisplayName("Ohne Bild kein Ablegen")
    void nullIsNotAFrame() {
        try (FrameSlot slot = new FrameSlot()) {
            assertNotNull(org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class, () -> slot.offer(null)));
        }
    }
}
