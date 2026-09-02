package dev.devpanda.bamboocef.web.measure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Verteilung muss stimmen, sonst misst der Messwert nichts.
 *
 * <p>Der wichtigste Fall ist der letzte: Ein einzelner Ausreißer darf im
 * Median untergehen, aber nirgends sonst. Genau dafür wird die Klasse
 * gebraucht.
 */
class DurationSamplesTest {

    @Test
    @DisplayName("Ohne Messung gibt es nichts zu berichten")
    void nothingMeasuredIsNothingReported() {
        DurationSamples samples = new DurationSamples(16);

        assertEquals(0, samples.count());
        assertEquals(0.0, samples.percentile(50));
        assertEquals(0.0, samples.average());
    }

    @Test
    @DisplayName("Eine einzelne Messung ist zugleich Median, p95 und Maximum")
    void oneSampleIsEverything() {
        DurationSamples samples = new DurationSamples(16);
        samples.record(5_000);           // 5 µs

        assertEquals(5.0, samples.percentile(50));
        assertEquals(5.0, samples.percentile(95));
        assertEquals(5.0, samples.slowest());
    }

    @Test
    @DisplayName("Bei hundert gleichmäßigen Werten liegen die Perzentile, wo sie sollen")
    void percentilesLandWhereTheyBelong() {
        DurationSamples samples = new DurationSamples(128);
        for (int each = 1; each <= 100; each++) {
            samples.record(each * 1000L);   // 1 µs bis 100 µs
        }

        assertEquals(50.0, samples.percentile(50));
        assertEquals(95.0, samples.percentile(95));
        assertEquals(100.0, samples.percentile(100));
    }

    @Test
    @DisplayName("Ein einzelner Ausreißer verschiebt den Median nicht, das Maximum schon")
    void oneOutlierMovesTheMaximumOnly() {
        DurationSamples samples = new DurationSamples(128);
        for (int each = 0; each < 99; each++) {
            samples.record(100_000);        // 100 µs, neunundneunzig Mal
        }
        samples.record(500_000_000);        // ein halber Sekundenruckler

        assertEquals(100.0, samples.percentile(50),
                "sonst versteckte ein Mittelwert genau den Fall, der auffällt");
        assertEquals(500_000.0, samples.slowest());
        assertTrue(samples.average() > 100.0,
                "das Mittel wandert mit — deshalb reicht es allein nicht");
    }

    @Test
    @DisplayName("Das Fenster vergisst die ältesten Werte, die Zählung nicht")
    void theWindowForgetsButTheCountDoesNot() {
        DurationSamples samples = new DurationSamples(4);
        samples.record(1_000_000);          // 1000 µs, fällt gleich heraus
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
    @DisplayName("Ein Perzentil jenseits der Skala wird abgelehnt")
    void nonsenseIsRefused() {
        DurationSamples samples = new DurationSamples(4);
        samples.record(1000);

        assertThrows(IllegalArgumentException.class, () -> samples.percentile(101));
        assertThrows(IllegalArgumentException.class, () -> new DurationSamples(0));
    }
}
