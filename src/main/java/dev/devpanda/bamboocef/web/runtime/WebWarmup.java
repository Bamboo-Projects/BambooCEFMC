package dev.devpanda.bamboocef.web.runtime;

import com.mojang.logging.LogUtils;
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
 * call kicks off its download.
 *
 * <p>Waits the ten seconds the self-test waits: right after start the game
 * is still loading resources, and a Chromium start on top of that reads as a
 * hang.
 */
public final class WebWarmup {

    private static final Logger LOG = LogUtils.getLogger();

    /** Ticks between the request and the start; about ten seconds. */
    public static final int DELAY_TICKS = 200;

    private static volatile boolean requested;
    private static boolean done;
    private static int ticksWaited;

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
     * runtime — starting is idempotent.
     */
    public static void tick() {
        if (!requested || done) {
            return;
        }
        ticksWaited++;
        if (ticksWaited < DELAY_TICKS) {
            return;
        }
        done = true;
        WebRuntimeStatus status = WebSupport.ensureStarted();
        LOG.info("Runtime prepared ahead of the first surface: {}", status);
    }

    /** Back to the initial state; for tests only. */
    public static void resetForTests() {
        requested = false;
        done = false;
        ticksWaited = 0;
    }
}
