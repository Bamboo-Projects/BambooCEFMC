package dev.devpanda.bamboocef.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * The web runtime: bring up, report state, shut down.
 *
 * <p><b>This class does not touch Chromium.</b> No import, no field, no
 * signature from {@code org.cef} — the backend comes in as a
 * {@link WebBackend}. The reason is not taste: a class that names such a type
 * demands it at load time. Whoever is missing the runtime would get a
 * {@code NoClassDefFoundError} at mod startup — and a missing browser that
 * takes the client apart is more than a missing browser.
 *
 * <p><b>Nothing here throws.</b> Every path ends in a
 * {@link WebRuntimeStatus}, even the one where something is broken. That is the
 * assurance under which the runtime is allowed into this mod at all: a browser
 * that does not start costs a user interface and not the game.
 *
 * <p>The state is determined and remembered <b>once</b>. Whoever wants to try
 * again — after a caught-up download, say — calls {@link #retry(Supplier)};
 * whether it is worthwhile is told by {@link WebRuntimeState#worthRetrying()}.
 */
public final class WebRuntime {

    private static final Logger LOG = LoggerFactory.getLogger("BambooCEF/Web");

    private static WebRuntimeStatus status = WebRuntimeStatus.of(WebRuntimeState.NOT_STARTED);
    private static WebBackend backend;

    private WebRuntime() {
    }

    /**
     * Brings up, if not already done.
     *
     * <p>The caller passes in how a backend is built. This class does not know
     * what stands behind it — and is not supposed to know.
     *
     * @param start builds the backend, or throws
     */
    public static synchronized WebRuntimeStatus start(Supplier<WebBackend> start) {
        if (status.state() != WebRuntimeState.NOT_STARTED) {
            return status;
        }
        return attempt(start);
    }

    /**
     * Once more, after something may have changed.
     *
     * <p>Only sensible if the last state was worth a second attempt. A missing
     * platform will not turn up on the tenth try either.
     */
    public static synchronized WebRuntimeStatus retry(Supplier<WebBackend> start) {
        if (!status.state().worthRetrying()) {
            return status;
        }
        return attempt(start);
    }

    private static WebRuntimeStatus attempt(Supplier<WebBackend> start) {
        try {
            WebBackend built = start.get();
            if (built == null) {
                return remember(WebRuntimeStatus.of(WebRuntimeState.FAILED,
                        "Der Unterbau kam ohne Grund nicht zustande"));
            }
            backend = built;
            return remember(WebRuntimeStatus.of(WebRuntimeState.READY, built.name()));
        } catch (WebRuntimeUnavailable known) {
            // A state that someone foresaw — the usual case.
            return remember(known.status());
        } catch (Throwable broken) {
            // Everything else: a missing system library, an archive that could
            // not be unpacked, a NoClassDefFoundError from org.cef. Throwable
            // and not Exception — precisely the errors that would otherwise take
            // the client down are not exceptions.
            LOG.warn("Die Web-Runtime ist nicht hochgekommen; das Spiel läuft ohne sie", broken);
            return remember(WebRuntimeStatus.of(WebRuntimeState.FAILED,
                    broken.getClass().getSimpleName() + ": " + broken.getMessage()));
        }
    }

    private static WebRuntimeStatus remember(WebRuntimeStatus found) {
        status = found;
        if (found.usable()) {
            LOG.info("Web-Runtime bereit ({})", found.reason());
        } else {
            LOG.info("Keine Web-Runtime — {}", found);
        }
        return found;
    }

    /** Where things stand. */
    public static synchronized WebRuntimeStatus status() {
        return status;
    }

    /** Can a browser be created right now? */
    public static synchronized boolean isAvailable() {
        return status.usable() && backend != null;
    }

    /** The backend, or {@code null}. Only for those who really need it. */
    public static synchronized WebBackend backend() {
        return isAvailable() ? backend : null;
    }

    /**
     * Shuts down.
     *
     * <p>This too must not throw: when the game is quitting, an exception out of
     * a browser is the last thing anyone wants to see.
     */
    /**
     * How long Chromium's acknowledgements are waited for.
     *
     * <p>Generous enough for the normal case — on the test bench the
     * acknowledgement came after a few rounds — and short enough that no one
     * thinks the game is hanging. Without a deadline, a single browser that
     * never acknowledges could hold up the quit forever.
     */
    private static final long CLOSE_TIMEOUT_MILLIS = 2000;

    public static synchronized void shutdown() {
        // <b>Reset first, then close.</b> The other way round, after a failed
        // start the old reason would stand forever: there would be no backend to
        // close, and the method would return without touching the state. A
        // second attempt would be impossible after that.
        WebBackend closing = backend;
        backend = null;
        status = WebRuntimeStatus.of(WebRuntimeState.NOT_STARTED);

        // <b>The order is the whole content of this method.</b>
        //
        //   1. ask all browsers to close
        //   2. keep pumping until Chromium has acknowledged every close
        //   3. only then tear down the backend
        //
        // Whoever starts at 3 tears CEF down while it still wanted to close
        // browsers — and that is exactly what produces the helper processes
        // that stay put. close(true) is a request, not a deed.
        //
        // <b>This runs on the render thread</b>, and it has to: the pump runs
        // there, and only whoever pumps gets to see the acknowledgements.
        LOG.info("Web-Runtime fährt herunter — im Thread {}, offen: {}",
                Thread.currentThread().getName(), BrowserManager.count());
        try {
            BrowserManager.closeAll();
            if (BrowserManager.pending() > 0) {
                BrowserManager.awaitClosed(CLOSE_TIMEOUT_MILLIS,
                        dev.devpanda.bamboocef.web.runtime.WebPump::frame);
            }
        } catch (Throwable broken) {
            LOG.warn("Beim Schließen der Browser ging etwas schief", broken);
        }

        if (closing == null) {
            return;
        }
        try {
            closing.close();
        } catch (Throwable broken) {
            LOG.warn("Beim Herunterfahren der Web-Runtime ging etwas schief", broken);
        }
        LOG.info("Web-Runtime ist unten — offen: {}, ohne Bestätigung: {}",
                BrowserManager.count(), BrowserManager.pending());
    }
}
