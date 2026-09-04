package dev.devpanda.bamboocef.web;

import java.util.function.Supplier;

/**
 * The one place where it is decided what the runtime runs on.
 *
 * <p>There is exactly one backend. It still lives here and not in
 * {@link WebRuntime}, for a reason that has nothing to do with speculation:
 * {@code WebRuntime} must not name any type from {@code org.cef}, or merely
 * loading it would demand the native runtime. This class does not name it
 * either — it passes a lambda along, and only calling that lambda loads the
 * class that does.
 */
public final class WebSupport {

    private WebSupport() {
    }

    /**
     * Brings the runtime up, if it can.
     *
     * <p>To be called on first need and not at game startup: Chromium comes up
     * on the render thread and takes a few hundred milliseconds to do so.
     * Whoever asks too early takes a "not yet" for a "no".
     */
    public static WebRuntimeStatus ensureStarted() {
        WebRuntimeStatus status = WebRuntime.start(backend());
        if (status.state() == WebRuntimeState.NOT_DOWNLOADED && !RuntimeInstall.downloading()) {
            // The first attempt only set the download off and remembered
            // NOT_DOWNLOADED. Once the fetch is over — installed, or failed
            // and worth another go — the remembered state must not stand in
            // the way: nobody would ever call retry() for the player, and
            // without this the runtime stayed unusable until the next game
            // start. FAILED is deliberately left to an explicit retry: a
            // failed Chromium start is not something to hammer on.
            status = WebRuntime.retry(backend());
        }
        return status;
    }

    /** Try again, if the last reason was worth a second attempt. */
    public static WebRuntimeStatus retry() {
        return WebRuntime.retry(backend());
    }

    private static Supplier<WebBackend> backend() {
        // A lambda and not a method reference: both would be correct here, but
        // the body makes it visible that the class is loaded only on the call
        // — and that is exactly what lets the runtime be missing without
        // taking the mod down with it.
        return () -> dev.devpanda.bamboocef.web.runtime.CefHost.backend();
    }
}
