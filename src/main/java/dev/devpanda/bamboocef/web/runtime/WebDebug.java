package dev.devpanda.bamboocef.web.runtime;

import org.cef.CefApp;
import org.cef.callback.CefCommandLine;
import org.cef.handler.CefAppHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens Chromium's own tools — on request and only for ourselves.
 *
 * <p><b>Why this is the only path to real numbers.</b> What happens between a
 * keystroke and the finished frame inside Chromium, only Chromium knows. From
 * outside, a single number is left — the total span. Whoever wants to know how
 * much of it is layout and how much is painting has to ask Chromium itself, and
 * that is what the remote-debugging protocol is for.
 *
 * <p><b>Why there is no other way.</b> MCEF builds Chromium's settings itself
 * and sets no debug port; the switch list is hard-wired. It can be changed in
 * exactly one place: before Chromium comes up, it asks Java whether there is
 * anything else to add to the command line. That is where we hook in.
 *
 * <p><b>The timing is tight.</b> MCEF starts Chromium on the very first
 * {@code setScreen} — that is, at the transition into the main menu. The
 * registration has to happen before that, and that means: during mod setup.
 * Afterwards {@code addAppHandler} throws.
 *
 * <p><b>Only on request, only local.</b> An open debug port is a door into the
 * browser: whoever reaches it can read pages, run scripts and send input. It
 * therefore only opens when {@code bamboocef.devtools} is set, and Chromium binds it
 * of its own accord only to the local machine.
 */
public final class WebDebug {

    private static final Logger LOG = LoggerFactory.getLogger("BambooCEF/WebDebug");

    /** The port, if it does open. */
    public static final int PORT = 9222;

    private static boolean requested;

    private WebDebug() {
    }

    /**
     * Registers the wish for a debug port.
     *
     * <p>Must be called from mod setup — later Chromium has already started and
     * no longer accepts anything.
     */
    public static void requestIfEnabled() {
        if (!Boolean.getBoolean("bamboocef.devtools") || requested) {
            return;
        }
        try {
            CefApp.addAppHandler(new CefAppHandlerAdapter(null) {
                @Override
                public void onBeforeCommandLineProcessing(String processType,
                                                          CefCommandLine commandLine) {
                    // Only the main process gets the port. The helper processes
                    // inherit the command line in part, and a second listener on
                    // the same port would be a failed start.
                    if (processType == null || processType.isEmpty()) {
                        commandLine.appendSwitchWithValue(
                                "remote-debugging-port", String.valueOf(PORT));
                        // Without this line Chromium binds, depending on the
                        // version, to all interfaces. The port belongs on this
                        // machine and nowhere else.
                        commandLine.appendSwitchWithValue(
                                "remote-debugging-address", "127.0.0.1");
                    }
                }
            });
            requested = true;
            LOG.info("Chromiums Fernwartung wird auf 127.0.0.1:{} geöffnet — "
                    + "nur für diese Sitzung und nur, weil bamboocef.devtools gesetzt ist", PORT);
        } catch (IllegalStateException tooLate) {
            LOG.warn("Zu spät für die Fernwartung: Chromium läuft schon. "
                    + "Der Wunsch muss im Aufbau der Mod angemeldet werden.", tooLate);
        } catch (Throwable broken) {
            LOG.warn("Die Fernwartung ließ sich nicht anmelden", broken);
        }
    }

    public static boolean isRequested() {
        return requested;
    }
}
