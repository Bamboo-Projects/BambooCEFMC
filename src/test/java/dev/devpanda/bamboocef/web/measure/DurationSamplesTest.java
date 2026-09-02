package dev.devpanda.bamboocef.web.measure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The distribution must be right, otherwise the measurement measures nothing.
 *
 * <p>The most important case is the last: a single outlier may sink into the
 * median, but nowhere else. Exactly for that the class is
 * needed.
 */
class DurationSamplesTest {

    @Test
    @DisplayName("With no measurement there is nothing to report")
    void nothingMeasuredIsNothingReported() {
        DurationSamples samples = new DurationSamples(16);

        assertEquals(0, samples.count());
        assertEquals(0.0, samples.percentile(50));
        assertEquals(0.0, samples.average());
    }

    @Test
    @DisplayName("A single measurement is at once median, p95 and maximum")
    void oneSampleIsEverything() {
        DurationSamples samples = new DurationSamples(16);
        samples.record(5_000);           // 5 µs

        assertEquals(5.0, samples.percentile(50));
        assertEquals(5.0, samples.percentile(95));
        assertEquals(5.0, samples.slowest());
    }

    @Test
    @DisplayName("With a hundred evenly spaced values the percentiles land where they should")
    void percentilesLandWhereTheyBelong() {
        DurationSamples samples = new DurationSamples(128);
        for (int each = 1; each <= 100; each++) {
            samples.record(each * 1000L);   // 1 µs to 100 µs
        }

        assertEquals(50.0, samples.percentile(50));
        assertEquals(95.0, samples.percentile(95));
        assertEquals(100.0, samples.percentile(100));
    }

    @Test
    @DisplayName("A single outlier does not shift the median, but the maximum yes")
    void oneOutlierMovesTheMaximumOnly() {
        DurationSamples samples = new DurationSamples(128);
        for (int each = 0; each < 99; each++) {
            samples.record(100_000);        // 100 µs, ninety-nine times
        }
        samples.record(500_000_000);        // a half-second stutter

        assertEquals(100.0, samples.percentile(50),
                "sonst versteckte ein Mittelwert genau den Fall, der auffällt");
        assertEquals(500_000.0, samples.slowest());
        assertTrue(samples.average() > 100.0,
                "das Mittel wandert mit — deshalb reicht es allein nicht");
    }

    @Test
    @DisplayName("The window forgets the oldest values, the count does not")
    void theWindowForgetsButTheCountDoesNot() {
        DurationSamples samples = new DurationSamples(4);
        samples.record(1_000_000);          // 1000 µs, falls out right away
        for (int each = 0; each < 4; each++) {
            samples.record(10_000);         // 10 µs
        }

        assertEquals(5, samples.count(), "gezählt wird alles");
        assertEquals(10.0, samples.percentile(100),
                "im Fenster steht der alte Wert nicht mehr");
        assertEquals(1000.0, samples.slowest(),
                "der schlimmste Fall bleibt trotzdem in Erinnerung");
    }

    @Test
    @DisplayName("A percentile beyond the scale is rejected")
    void nonsenseIsRefused() {
        DurationSamples samples = new DurationSamples(4);
        samples.record(1000);

        assertThrows(IllegalArgumentException.class, () -> samples.percentile(101));
        assertThrows(IllegalArgumentException.class, () -> new DurationSamples(0));
    }
}
