package dev.devpanda.bamboocef.web.measure;

import java.util.Arrays;

/**
 * Gemessene Dauern, aus denen sich Perzentile ziehen lassen.
 *
 * <p><b>Warum nicht nur ein Mittelwert.</b> Ein Mittel von 700 µs kann heißen:
 * jeder Upload dauert 700 µs. Es kann genauso heißen: neunhundert dauern 100 µs
 * und einer dauert 500 ms. Das erste ist harmlos, das zweite ist ein Ruckler,
 * den jeder sieht. Nur die Verteilung trennt die beiden Fälle.
 *
 * <p><b>Warum ein Fenster und kein vollständiges Gedächtnis.</b> Bei dreißig
 * Bildern in der Sekunde kämen in einer Stunde über hunderttausend Werte
 * zusammen. Die letzten paar tausend sagen alles, was interessiert, und kosten
 * nichts, was auffiele. Was aus dem Fenster fällt, zählt weiter in
 * {@link #count()} und {@link #average()}, aber nicht mehr in den Perzentilen —
 * die beschreiben also die jüngste Vergangenheit, nicht den ganzen Lauf.
 */
public final class DurationSamples {

    /** Reicht für gut vier Minuten bei dreißig Bildern je Sekunde. */
    private static final int DEFAULT_CAPACITY = 8192;

    private final long[] window;
    private int filled;
    private int next;

    private long count;
    private long total;
    private long slowest;

    public DurationSamples() {
        this(DEFAULT_CAPACITY);
    }

    public DurationSamples(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Ein Fenster ohne Platz sammelt nichts");
        }
        this.window = new long[capacity];
    }

    /** Nimmt eine Dauer auf, in Nanosekunden. */
    public void record(long nanos) {
        count++;
        total += nanos;
        if (nanos > slowest) {
            slowest = nanos;
        }
        window[next] = nanos;
        next = (next + 1) % window.length;
        if (filled < window.length) {
            filled++;
        }
    }

    public long count() {
        return count;
    }

    /** Mittelwert über alle Werte, in Mikrosekunden. */
    public double average() {
        return count == 0 ? 0.0 : total / (double) count / 1000.0;
    }

    /** Der langsamste Wert überhaupt, in Mikrosekunden. */
    public double slowest() {
        return slowest / 1000.0;
    }

    /**
     * Ein Perzentil über das Fenster, in Mikrosekunden.
     *
     * <p>{@code percentile(50)} ist der Median, {@code percentile(95)} der
     * Wert, den neunzehn von zwanzig unterschreiten.
     */
    public double percentile(int wanted) {
        if (filled == 0) {
            return 0.0;
        }
        if (wanted < 0 || wanted > 100) {
            throw new IllegalArgumentException("Ein Perzentil liegt zwischen 0 und 100");
        }
        long[] sorted = Arrays.copyOf(window, filled);
        Arrays.sort(sorted);
        // Nearest-Rank: der kleinste Wert, den mindestens `wanted` Prozent
        // der Messungen nicht überschreiten. Ohne Interpolation, weil eine
        // gemittelte Dauer zwischen zwei echten keine gemessene mehr ist.
        int rank = (int) Math.ceil(wanted / 100.0 * filled);
        int index = Math.max(0, Math.min(filled - 1, rank - 1));
        return sorted[index] / 1000.0;
    }

    /**
     * Die Verteilung als Textblock, in Fächern von je {@code bucketMillis}.
     *
     * <p><b>Wozu, wenn es schon Perzentile gibt.</b> Perzentile verraten die
     * Lage, nicht die Form. Zwei Messreihen mit demselben Median sehen gleich
     * aus, solange niemand hinsieht: die eine streut gleichmäßig über ihre
     * Spanne, die andere hat zwei scharfe Häufungen und dazwischen fast
     * nichts. Das ist der Unterschied zwischen „mal schneller, mal langsamer"
     * und „wartet auf einen Takt" — und den entscheidet keine einzelne Zahl,
     * sondern nur das Bild der ganzen Verteilung.
     *
     * <p>Was oberhalb des letzten Fachs liegt, fällt nicht unter den Tisch,
     * sondern kommt in eine eigene Zeile. Ein Ausreißerfach, das man nicht
     * sieht, verschiebt die Form still.
     */
    public String histogram(double bucketMillis, int buckets) {
        if (filled == 0) {
            return "  (keine Messungen)";
        }
        int[] counts = new int[buckets + 1];
        for (int i = 0; i < filled; i++) {
            double millis = window[i] / 1_000_000.0;
            int slot = (int) (millis / bucketMillis);
            counts[Math.min(slot, buckets)]++;
        }
        int highest = 0;
        for (int count : counts) {
            highest = Math.max(highest, count);
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i <= buckets; i++) {
            if (counts[i] == 0) {
                continue;
            }
            String range = i == buckets
                    ? String.format(java.util.Locale.GERMANY, "  ab %5.1f ms", buckets * bucketMillis)
                    : String.format(java.util.Locale.GERMANY, "%5.1f–%5.1f ms",
                            i * bucketMillis, (i + 1) * bucketMillis);
            int bar = highest == 0 ? 0 : (int) Math.round(counts[i] * 40.0 / highest);
            text.append(String.format(java.util.Locale.GERMANY, "  %s | %-40s %4d (%4.1f %%)%n",
                    range, "#".repeat(bar), counts[i], counts[i] * 100.0 / filled));
        }
        return text.toString();
    }

    public void reset() {
        filled = 0;
        next = 0;
        count = 0;
        total = 0;
        slowest = 0;
    }

    /** Kurzfassung fürs Protokoll: Median, p95 und der schlimmste Fall. */
    public String summary() {
        return String.format(java.util.Locale.GERMANY,
                "p50 %.1f µs, p95 %.1f µs, max %.1f µs, Mittel %.1f µs (%d Messungen)",
                percentile(50), percentile(95), slowest(), average(), count);
    }
}
