package dev.devpanda.bamboocef.web;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Ensures that Chromium's helper processes do not outlive the game process.
 *
 * <p><b>Why this is needed.</b> Chromium starts its own processes for display,
 * graphics and helper services. On an orderly shutdown CEF cleans them up
 * itself. On a hard abort — task manager, a crashed client, a launcher that
 * strikes — CEF no longer gets the chance, and the helpers stay put. Measured
 * in the proof of concept: eight orphans. After the change, once in three
 * measurements.
 *
 * <p><b>What helps here is nothing the process does, but something the
 * operating system does for it.</b> Windows knows job objects: a bracket around
 * a group of processes. When the last handle onto it is closed — and that
 * always happens when the process dies, even on the hardest abort — Windows
 * terminates everything held in the bracket. Child processes inherit the
 * membership.
 *
 * <p><b>That is why this has to happen before {@code CefApp.startup()}.</b>
 * Whoever only brackets afterwards no longer brackets the helpers that have
 * already started.
 *
 * <p>This class knows no platform specifics; it looks them up only when they
 * are needed. On everything but Windows it is a no-op with one line in the log
 * — Linux has something similar with {@code prctl(PR_SET_PDEATHSIG)}, but that
 * is not version 1.
 */
public final class ProcessGuard {

    private static final Logger LOG = LogUtils.getLogger();

    private static boolean installed;
    private static String state = "nicht angefordert";

    /**
     * Opens the bracket, once per process.
     *
     * <p><b>Does not throw.</b> A guard that prevents startup is worse than a
     * missing guard: without it, at worst a process stays put; with it, the
     * game would no longer start.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        if (!isWindows()) {
            state = "kein Wächter — nur für Windows gebaut";
            LOG.info("ProcessGuard: {}", state);
            return;
        }
        try {
            state = WindowsProcessGuard.install();
        } catch (Throwable broken) {
            state = "gescheitert: " + broken;
            LOG.warn("ProcessGuard ließ sich nicht aufspannen — die Helfer könnten "
                    + "einen harten Abbruch überleben", broken);
        }
        LOG.info("ProcessGuard: {}", state);
    }

    /** What came of it, in one sentence. For reports and logs. */
    public static synchronized String state() {
        return state;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private ProcessGuard() {
    }
}
