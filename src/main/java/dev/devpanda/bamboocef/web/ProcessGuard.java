package dev.devpanda.bamboocef.web;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Sorgt dafür, dass Chromiums Hilfsprozesse den Spielprozess nicht überleben.
 *
 * <p><b>Warum es das braucht.</b> Chromium startet für Darstellung, Grafik und
 * Hilfsdienste eigene Prozesse. Beim geordneten Beenden räumt CEF sie selbst
 * ab. Bei einem harten Abbruch — Taskmanager, abgestürzter Client, ein
 * Launcher, der zuschlägt — kommt CEF nicht mehr dazu, und die Helfer bleiben
 * stehen. Gemessen im Proof-of-Concept: acht Waisen. Nach der Umstellung
 * einmal eine von drei Messungen.
 *
 * <p><b>Was hier hilft, ist nichts, was der Prozess tut, sondern etwas, was
 * das Betriebssystem für ihn tut.</b> Windows kennt Job Objects: eine Klammer
 * um eine Gruppe von Prozessen. Wird die letzte Handhabe darauf geschlossen —
 * und das passiert beim Sterben des Prozesses immer, auch beim härtesten
 * Abbruch —, beendet Windows alles, was in der Klammer steckt. Kindprozesse
 * erben die Zugehörigkeit.
 *
 * <p><b>Deshalb muss das vor {@code CefApp.startup()} passieren.</b> Wer erst
 * danach klammert, klammert die schon gestarteten Helfer nicht mehr ein.
 *
 * <p>Diese Klasse kennt keine Plattform-Einzelheiten; sie sucht sie erst, wenn
 * sie gebraucht werden. Auf allem außer Windows ist sie ein Nichtstun mit
 * einer Zeile im Protokoll — Linux hat mit {@code prctl(PR_SET_PDEATHSIG)}
 * etwas Ähnliches, aber das ist nicht Version 1.
 */
public final class ProcessGuard {

    private static final Logger LOG = LogUtils.getLogger();

    private static boolean installed;
    private static String state = "nicht angefordert";

    /**
     * Spannt die Klammer auf, einmal je Prozess.
     *
     * <p><b>Wirft nicht.</b> Ein Wächter, der den Start verhindert, ist
     * schlimmer als ein fehlender Wächter: Ohne ihn bleibt im schlimmsten Fall
     * ein Prozess stehen, mit ihm startete das Spiel nicht mehr.
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

    /** Was daraus geworden ist, in einem Satz. Für Berichte und Protokolle. */
    public static synchronized String state() {
        return state;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private ProcessGuard() {
    }
}
