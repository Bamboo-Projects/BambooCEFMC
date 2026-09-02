package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.WebBackend;
import dev.devpanda.bamboocef.web.WebRuntimeState;
import dev.devpanda.bamboocef.web.WebRuntimeUnavailable;

/**
 * The backend on our own runtime.
 *
 * <p><b>The counterpart to {@code McefBackend}, and deliberately slimmer.</b>
 * MCEF starts itself up on the side; here we start it ourselves. That is a
 * difference that reaches all the way into the error messages: MCEF reports
 * with a single boolean whether it came up, and names no reason — our route
 * can say what is missing.
 *
 * <p><b>For the test run, not for shipping.</b> There is no download, no
 * checksums and no watchdog over helper processes left standing. The directory
 * must be present, otherwise it stops here. What this once becomes —
 * distribution, an orderly shutdown, a watchdog — is a step of its own and not
 * this one.
 */
public final class FnRuntimeBackend implements WebBackend {

    private FnRuntimeBackend() {
    }

    /**
     * Starts the runtime up.
     *
     * <p><b>To be called from the render thread.</b> CEF demands that
     * initialization, message loop and shutdown lie on the same thread, and
     * our render path demands that it be the render thread.
     *
     * @throws WebRuntimeUnavailable with the reason, when it does not work
     */
    public static WebBackend create() {
        try {
            FnCefRuntime.ensureStarted();
        } catch (WebRuntimeUnavailable known) {
            // <b>Onward unchanged.</b> Reaching for the runtime tells what is
            // missing — not present, being downloaded, no binaries, already
            // shut down. Repacked into FAILED only the text would survive, and
            // the state that the UI and the retry hang off would be gone.
            throw known;
        } catch (Throwable broken) {
            String reason = broken.getMessage() == null ? broken.toString() : broken.getMessage();
            throw new WebRuntimeUnavailable(WebRuntimeState.FAILED, reason);
        }
        // Chromium's console into the log — for every page, not only the ones
        // with a backdrop. Previously that hung off registering the image
        // scheme, and overlays and panels ran silent.
        WebConsole.attach();
        WebMessages.attach();
        return new FnRuntimeBackend();
    }

    @Override
    public String name() {
        Thread owner = FnCefRuntime.owner();
        return "eigene Laufzeitumgebung (Thread " + (owner == null ? "?" : owner.getName()) + ")";
    }

    @Override
    public void close() {
        // <b>Unlike with MCEF, this backend belongs to us.</b> There, doing
        // nothing was right, because other mods share the same browser. Here
        // there is no one else — and a CEF that is not shut down leaves helper
        // processes standing.
        FnCefRuntime.shutdown();
    }
}
