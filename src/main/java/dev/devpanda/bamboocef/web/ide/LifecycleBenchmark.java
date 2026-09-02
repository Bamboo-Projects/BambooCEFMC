package dev.devpanda.bamboocef.web.ide;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens and closes the interface repeatedly and records when what
 * happens.
 *
 * <p><b>The question.</b> After a measurement run with many page switches, ten
 * renderer processes remained, together around 660 MB. Open is what
 * caused it — and the two possible causes have very different consequences:
 *
 * <ul>
 *   <li><b>On switching the page.</b> Every {@code location.href} creates a
 *       new process, the old one stays. Then it is not a fault of the
 *       runtime environment but an instruction to our own design:
 *       the interface must not navigate fully between its
 *       views, but must remain an application that swaps out its
 *       content.</li>
 *   <li><b>On closing the browser.</b> When a closed browser
 *       keeps its process, consumption grows with every open, and that
 *       would be an obstacle to everything further.</li>
 * </ul>
 *
 * <p><b>That is why this run never navigates.</b> Always the same address, always
 * a fresh browser. What grows here grows at closing — and what does not
 * grow clears the runtime environment of suspicion.
 *
 * <p><b>And that is why it waits long.</b> Chromium does not clear its processes
 * in the same instant; whoever counts right after closing counts an
 * intermediate state and calls it a leak. Fifteen seconds per section are
 * enough that a cleanup must have taken place.
 */
public final class LifecycleBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/Lifecycle");

    /** How long a state is held, in ticks of 50 ms. */
    private static final int HOLD_TICKS = 300;

    private static final int CYCLES = 3;

    private static boolean running;
    private static boolean finished;
    private static int tick;
    private static int cycle;
    private static Phase phase = Phase.SETTLE;
    private static long phaseStartedNanos;

    private enum Phase {
        /** Before the first open: Chromium is running, but no browser. */
        SETTLE,
        /** The interface is open. */
        OPEN,
        /** The interface is closed, waiting for the cleanup. */
        CLOSED,
        DONE
    }

    private LifecycleBenchmark() {
    }

    public static boolean finished() {
        return finished;
    }

    /** Begins the run. To be called when a world is loaded. */
    public static void start() {
        if (running) {
            return;
        }
        running = true;
        phaseStartedNanos = System.nanoTime();
        LOG.info("=== LIFECYCLE-LAUF {} — {} Zyklen zu je {} s offen und {} s zu ===",
                System.currentTimeMillis() / 1000, CYCLES,
                HOLD_TICKS / 20, HOLD_TICKS / 20);
        mark("START", "Chromium läuft, noch kein Browser dieser Messung");
    }

    /**
     * One tick of the client.
     *
     * <p>The run hangs on the tick and not on the frame: a closed
     * screen does not draw, and a run that only continues while drawing
     * would stop exactly at the point it is meant to measure.
     */
    public static void tick(Minecraft client) {
        if (!running || phase == Phase.DONE) {
            return;
        }
        tick++;
        if (tick < HOLD_TICKS) {
            return;
        }
        tick = 0;
        switch (phase) {
            case SETTLE, CLOSED -> {
                if (phase == Phase.CLOSED) {
                    mark("NACH-DEM-SCHLIESSEN",
                            "Zyklus " + cycle + " — Aufräumzeit abgelaufen");
                    if (cycle >= CYCLES) {
                        phase = Phase.DONE;
                        finished = true;
                        LOG.info("=== LIFECYCLE-LAUF FERTIG ===");
                        return;
                    }
                }
                cycle++;
                boolean opened = IdeScreen.open(client);
                phase = Phase.OPEN;
                mark("GEÖFFNET", "Zyklus " + cycle + (opened ? "" : " — ÖFFNEN FEHLGESCHLAGEN"));
            }
            case OPEN -> {
                // setScreen(null) takes the same path as the escape key and
                // calls removed(), where the session is closed.
                client.setScreen(null);
                phase = Phase.CLOSED;
                mark("GESCHLOSSEN", "Zyklus " + cycle);
            }
            default -> {
            }
        }
    }

    /**
     * Sets a marker in the log.
     *
     * <p>Counting is done from outside, and the outside needs a point in time at
     * which it can anchor its own recording. The time of day is in every
     * line anyway; here stands what has happened at this moment.
     */
    private static void mark(String what, String detail) {
        double seconds = (System.nanoTime() - phaseStartedNanos) / 1_000_000_000.0;
        LOG.info("LIFECYCLE {} bei {} s | {}", what,
                String.format(java.util.Locale.GERMANY, "%.1f", seconds), detail);
    }
}
