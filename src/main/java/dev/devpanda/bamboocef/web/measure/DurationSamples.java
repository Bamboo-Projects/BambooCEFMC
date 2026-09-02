package dev.devpanda.bamboocef.web.measure;

import java.util.Arrays;

/**
 * Measured durations from which percentiles can be drawn.
 *
 * <p><b>Why not just an average.</b> A mean of 700 µs can mean: every upload
 * takes 700 µs. It can equally mean: nine hundred take 100 µs and one takes
 * 500 ms. The first is harmless, the second is a stutter that everyone sees.
 * Only the distribution tells the two cases apart.
 *
 * <p><b>Why a window and not a complete memory.</b> At thirty frames per
 * second, over a hundred thousand values would accumulate in an hour. The last
 * few thousand say everything of interest and cost nothing noticeable. What
 * falls out of the window still counts in {@link #count()} and
 * {@link #average()}, but no longer in the percentiles — those therefore
 * describe the recent past, not the whole run.
 */
public final class DurationSamples {

    /** Enough for a good four minutes at thirty frames per second. */
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

    /** Records a duration, in nanoseconds. */
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

    /** Mean over all values, in microseconds. */
    public double average() {
        return count == 0 ? 0.0 : total / (double) count / 1000.0;
    }

    /** The slowest value of all, in microseconds. */
    public double slowest() {
        return slowest / 1000.0;
    }

    /**
     * A percentile over the window, in microseconds.
     *
     * <p>{@code percentile(50)} is the median, {@code percentile(95)} the value
     * that nineteen out of twenty fall below.
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
        // Nearest-rank: the smallest value that at least `wanted` percent of
        // the measurements do not exceed. Without interpolation, because an
        // averaged duration between two real ones is no longer a measured one.
        int rank = (int) Math.ceil(wanted / 100.0 * filled);
        int index = Math.max(0, Math.min(filled - 1, rank - 1));
        return sorted[index] / 1000.0;
    }

    /**
     * The distribution as a text block, in buckets of {@code bucketMillis}
     * each.
     *
     * <p><b>What for, when there are already percentiles.</b> Percentiles
     * reveal the location, not the shape. Two series of measurements with the
     * same median look alike as long as nobody looks closely: one spreads
     * evenly across its range, the other has two sharp clusters and almost
     * nothing between them. That is the difference between "sometimes faster,
     * sometimes slower" and "waiting for a clock tick" — and no single number
     * decides it, only the picture of the whole distribution.
     *
     * <p>What lies above the last bucket does not fall through the cracks but
     * goes into a line of its own. An outlier bucket one does not see shifts the
     * shape quietly.
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

    /** Short form for the log: median, p95 and the worst case. */
    public String summary() {
        return String.format(java.util.Locale.GERMANY,
                "p50 %.1f µs, p95 %.1f µs, max %.1f µs, Mittel %.1f µs (%d Messungen)",
                percentile(50), percentile(95), slowest(), average(), count);
    }
}
