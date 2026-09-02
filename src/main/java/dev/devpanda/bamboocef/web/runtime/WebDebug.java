package dev.devpanda.bamboocef.web.runtime;

import org.cef.CefApp;
import org.cef.callback.CefCommandLine;
import org.cef.handler.CefAppHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Öffnet Chromiums eigene Werkzeuge — auf Anforderung und nur für uns selbst.
 *
 * <p><b>Warum das der einzige Weg zu echten Zahlen ist.</b> Was zwischen einem
 * Tastendruck und dem fertigen Bild in Chromium geschieht, weiß nur Chromium.
 * Von außen bleibt eine einzige Zahl — die Strecke insgesamt. Wer wissen will,
 * wie viel davon Layout ist und wie viel Zeichnen, muss Chromium selbst
 * fragen, und dafür gibt es das Fernwartungsprotokoll.
 *
 * <p><b>Warum es nicht anders geht.</b> MCEF baut Chromiums Einstellungen
 * selbst und setzt keinen Debug-Port; die Schalterliste ist fest verdrahtet.
 * Änderbar ist das an genau einer Stelle: Bevor Chromium hochfährt, fragt es
 * Java, ob es an der Kommandozeile noch etwas zu ergänzen gibt. Dort hängen
 * wir uns ein.
 *
 * <p><b>Die Reihenfolge ist eng.</b> MCEF startet Chromium beim allerersten
 * {@code setScreen} — also beim Übergang ins Hauptmenü. Angemeldet werden muss
 * vorher, und das heißt: im Aufbau der Mod. Danach wirft
 * {@code addAppHandler}.
 *
 * <p><b>Nur auf Anforderung, nur örtlich.</b> Ein offener Debug-Port ist eine
 * Tür in den Browser: Wer ihn erreicht, kann Seiten lesen, Skripte ausführen
 * und Eingaben senden. Er geht deshalb nur auf, wenn {@code fn.devtools}
 * gesetzt ist, und Chromium bindet ihn von sich aus nur an die eigene
 * Maschine.
 */
public final class WebDebug {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/WebDebug");

    /** Der Port, wenn er denn aufgeht. */
    public static final int PORT = 9222;

    private static boolean requested;

    private WebDebug() {
    }

    /**
     * Meldet den Wunsch nach einem Debug-Port an.
     *
     * <p>Muss aus dem Aufbau der Mod gerufen werden — später ist Chromium
     * schon gestartet und nimmt nichts mehr entgegen.
     */
    public static void requestIfEnabled() {
        if (!Boolean.getBoolean("fn.devtools") || requested) {
            return;
        }
        try {
            CefApp.addAppHandler(new CefAppHandlerAdapter(null) {
                @Override
                public void onBeforeCommandLineProcessing(String processType,
                                                          CefCommandLine commandLine) {
                    // Nur der Hauptprozess bekommt den Port. Die Hilfsprozesse
                    // erben die Kommandozeile teilweise, und ein zweiter
                    // Lauscher auf demselben Port wäre ein Fehlstart.
                    if (processType == null || processType.isEmpty()) {
                        commandLine.appendSwitchWithValue(
                                "remote-debugging-port", String.valueOf(PORT));
                        // Ohne diese Zeile bindet Chromium je nach Fassung an
                        // alle Schnittstellen. Der Port gehört auf diese
                        // Maschine und sonst nirgendwohin.
                        commandLine.appendSwitchWithValue(
                                "remote-debugging-address", "127.0.0.1");
                    }
                }
            });
            requested = true;
            LOG.info("Chromiums Fernwartung wird auf 127.0.0.1:{} geöffnet — "
                    + "nur für diese Sitzung und nur, weil fn.devtools gesetzt ist", PORT);
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
