package dev.devpanda.bamboocef.web.runtime;

import com.mojang.logging.LogUtils;
import dev.devpanda.bamboocef.web.WebRuntimeState;
import dev.devpanda.bamboocef.web.WebRuntimeStatus;
import dev.devpanda.bamboocef.web.WebSupport;
import org.slf4j.Logger;

/**
 * Brings Chromium up ahead of the first surface — but only when a mod asks.
 *
 * <p>A library must not start a browser nobody requested, so the runtime
 * comes up lazily with the first surface. That leaves the first opener with
 * the cold start (about 1.6 s measured). A mod that knows it will need the
 * web — an editor mod, say — calls {@code FnWeb.prepare()} early; this class
 * then starts the runtime a few seconds after the client is up, at the title
 * screen, where nobody feels the stall. When the runtime is missing, the same
 * call kicks off its download — and the warm-up looks again every few
 * seconds until the download is in, so that a fresh install ends the same
 * way: with Chromium running before anyone opens a page.
 *
 * <p>Waits the ten seconds the self-test waits: right after start the game
 * is still loading resources, and a Chromium start on top of that reads as a
 * hang.
 */
public final class WebWarmup {

    private static final Logger LOG = LogUtils.getLogger();

    /** Ticks between the request and the first start attempt; about ten seconds. */
    public static final int DELAY_TICKS = 200;

    /** Ticks between two looks while the runtime is still downloading; five seconds. */
    static final int RECHECK_TICKS = 100;

    /** How often to look again before giving up; five minutes in total. */
    static final int MAX_RECHECKS = 60;

    private static volatile boolean requested;
    private static boolean done;
    private static int ticksWaited;
    private static int nextAttemptAt = DELAY_TICKS;
    private static int rechecks;

    private WebWarmup() {
    }

    /** Asks for the runtime ahead of time. Idempotent, safe from any thread. */
    public static void request() {
        requested = true;
    }

    /** Whether a request is waiting for its start. */
    public static boolean pending() {
        return requested && !done;
    }

    /**
     * One client tick, on the render thread.
     *
     * <p>Starts the runtime once the delay is over. If a surface got there
     * first, {@link WebSupport#ensureStarted()} merely reports the running
     * runtime — starting is idempotent. While the runtime is still being
     * downloaded the call only reports that; then this looks again later.
     */
    public static void tick() {
        if (!requested || done) {
            return;
        }
        ticksWaited++;
        if (ticksWaited < nextAttemptAt) {
            return;
        }
        WebRuntimeStatus status = WebSupport.ensureStarted();
        if (status.state() == WebRuntimeState.NOT_DOWNLOADED && rechecks < MAX_RECHECKS) {
            // The fetch runs in the background; ensureStarted only set it off.
            // Come back in a few seconds instead of leaving the cold start to
            // the first surface.
            if (rechecks == 0) {
                LOG.info("Runtime is being fetched; it starts as soon as the download is in");
            }
            rechecks++;
            nextAttemptAt = ticksWaited + RECHECK_TICKS;
            return;
        }
        done = true;
        LOG.info("Runtime prepared ahead of the first surface: {}", status);
    }

    /** Back to the initial state; for tests only. */
    public static void resetForTests() {
        requested = false;
        done = false;
        ticksWaited = 0;
        nextAttemptAt = DELAY_TICKS;
        rechecks = 0;
    }
}
