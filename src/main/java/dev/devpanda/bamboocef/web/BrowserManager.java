package dev.devpanda.bamboocef.web;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The one place that knows all open browsers.
 *
 * <p><b>Why it is needed.</b> Until now each screen knew about its own browser
 * and no one knew about any. When the game quit, that meant: what was open
 * closed when its screen did — and what no longer had a screen stayed put until
 * Chromium cleaned it up itself. For an orderly shutdown, someone has to hold
 * the list.
 *
 * <p><b>Two lists, not one.</b> A session is not yet closed just because
 * someone called {@code close()}: {@code CefBrowser.close(true)} is a request,
 * and the acknowledgement — {@code onBeforeClose} — comes later and out of
 * Chromium's message loop. Whoever keeps only one list, on shutdown, either
 * treats it as finished too early or waits for something it just threw away
 * itself. That is why a session moves from {@code offen} to {@code wartet} and
 * only disappears with the acknowledgement.
 *
 * <p><b>The {@code CefClient} does not belong here.</b> It belongs to the
 * runtime and is shared by all browsers; releasing it per browser would pull
 * the ground out from under the next one.
 *
 * <p><b>Thread:</b> Everything here runs on the render thread — that is where
 * the sessions arise, where the pump runs, where the acknowledgements arrive.
 * The collections are synchronized all the same, because {@code onBeforeClose}
 * could, on an error, also come from a foreign thread, and a manager that tore
 * the list apart in the process would double the error.
 */
public final class BrowserManager {

    private static final Logger LOG = LogUtils.getLogger();

    /** What is open. */
    private static final Set<ManagedBrowser> open =
            Collections.synchronizedSet(new LinkedHashSet<>());

    /** What was closed and is still waiting for Chromium's acknowledgement. */
    private static final Set<ManagedBrowser> awaiting =
            Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * A session has come into being.
     *
     * <p>To be called as soon as the browser is there — not before: a browser
     * that fails on creation should not count as open.
     */
    public static void register(ManagedBrowser session) {
        open.add(session);
        LOG.info("Browser registriert: {} — offen: {}", session.describe(), count());
    }

    /**
     * A session wants to close.
     *
     * <p>From now on it no longer counts as open, but it is not yet finished:
     * Chromium acknowledges the close only later.
     */
    public static void closing(ManagedBrowser session) {
        if (open.remove(session)) {
            awaiting.add(session);
            LOG.info("Browser schließt: {} — offen: {}, warten auf Bestätigung: {}",
                    session.describe(), count(), pending());
        }
    }

    /**
     * Chromium has acknowledged the close.
     *
     * <p>This is {@code onBeforeClose}, and only with it is the session really
     * gone.
     */
    public static void closed(ManagedBrowser session) {
        boolean waited = awaiting.remove(session);
        boolean wasOpen = open.remove(session);
        if (waited || wasOpen) {
            LOG.info("Browser entfernt: {} — offen: {}, warten auf Bestätigung: {}",
                    session.describe(), count(), pending());
        }
    }

    /**
     * Takes a session out of both lists, without waiting for anything.
     *
     * <p>For the case that a session disappears by a route other than
     * {@code close()} — for instance because its creation failed.
     */
    public static void unregister(ManagedBrowser session) {
        boolean removed = open.remove(session) | awaiting.remove(session);
        if (removed) {
            LOG.info("Browser abgemeldet: {} — offen: {}", session.describe(), count());
        }
    }

    /** How many sessions are open. */
    public static int count() {
        return open.size();
    }

    /** How many are waiting for Chromium's acknowledgement. */
    public static int pending() {
        return awaiting.size();
    }

    /** What is currently open and what is waiting — for measurements and debugging. */
    public static List<String> snapshot() {
        List<String> lines = new ArrayList<>();
        synchronized (open) {
            for (ManagedBrowser session : open) {
                lines.add("offen: " + session.describe());
            }
        }
        synchronized (awaiting) {
            for (ManagedBrowser session : awaiting) {
                lines.add("wartet: " + session.describe());
            }
        }
        return lines;
    }

    /**
     * Closes everything that is open.
     *
     * <p><b>Over a copy.</b> Every {@code close()} deregisters the session in
     * the middle of the loop; whoever ran over the live collection would get a
     * {@code ConcurrentModificationException} — and precisely when it helps
     * least, during shutdown.
     *
     * <p>Calling it more than once is allowed and does nothing the second time.
     */
    public static void closeAll() {
        List<ManagedBrowser> victims;
        synchronized (open) {
            victims = List.copyOf(open);
        }
        if (victims.isEmpty()) {
            return;
        }
        LOG.info("closeAll: {} offene Sitzungen", victims.size());
        for (ManagedBrowser session : victims) {
            try {
                session.close();
            } catch (Throwable broken) {
                // A session that acts up while closing must not hold up the
                // others — during shutdown that is the only rule that counts.
                LOG.warn("Beim Schließen von {} ging etwas schief",
                        session.describe(), broken);
                unregister(session);
            }
        }
    }

    /**
     * Waits until Chromium has acknowledged all closes.
     *
     * <p><b>To be called from the thread that pumps.</b> {@code onBeforeClose}
     * comes out of Chromium's message loop, and that turns only as long as
     * someone turns it. Whoever waits from elsewhere waits for an event that
     * only they themselves could trigger — and that is not waiting but hanging.
     *
     * @param timeoutMillis how long at most
     * @param pump          one round of the message loop
     * @return true if all have acknowledged
     */
    public static boolean awaitClosed(long timeoutMillis, Runnable pump) {
        LOG.info("Warte auf {} Bestätigungen, höchstens {} ms — im Thread {}",
                pending(), timeoutMillis, Thread.currentThread().getName());
        long until = System.currentTimeMillis() + timeoutMillis;
        while (pending() > 0 && System.currentTimeMillis() < until) {
            try {
                pump.run();
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable broken) {
                LOG.warn("Beim Pumpen während des Schließens ging etwas schief", broken);
                break;
            }
        }
        if (pending() == 0) {
            LOG.info("Alle Schließungen bestätigt");
            return true;
        }
        // The deadline has expired. Whoever is missing belongs in the log: next
        // time that is the only clue.
        LOG.warn("Frist abgelaufen — {} Sitzungen ohne Bestätigung: {}",
                pending(), snapshot());
        return false;
    }

    /** Forgets everything. For test runs only. */
    static void forgetAll() {
        open.clear();
        awaiting.clear();
    }

    private BrowserManager() {
    }
}
