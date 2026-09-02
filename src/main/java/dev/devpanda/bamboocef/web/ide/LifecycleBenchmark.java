package dev.devpanda.bamboocef.web.ide;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Öffnet und schließt die Oberfläche mehrfach und schreibt mit, wann was
 * geschieht.
 *
 * <p><b>Die Frage.</b> Nach einer Messreihe mit vielen Seitenwechseln blieben
 * zehn Renderer-Prozesse stehen, zusammen rund 660 MB. Offen ist, woran das
 * lag — und die beiden möglichen Ursachen haben sehr verschiedene Folgen:
 *
 * <ul>
 *   <li><b>Beim Wechseln der Seite.</b> Jedes {@code location.href} legt einen
 *       neuen Prozess an, der alte bleibt. Dann ist es kein Fehler der
 *       Laufzeitumgebung, sondern eine Anweisung an unseren eigenen Entwurf:
 *       Die Oberfläche darf zwischen ihren Ansichten nicht vollständig
 *       navigieren, sondern muss eine Anwendung bleiben, die ihren Inhalt
 *       austauscht.</li>
 *   <li><b>Beim Schließen des Browsers.</b> Wenn ein geschlossener Browser
 *       seinen Prozess behält, wächst der Verbrauch mit jedem Öffnen, und das
 *       wäre ein Hindernis für alles Weitere.</li>
 * </ul>
 *
 * <p><b>Deshalb navigiert dieser Ablauf nie.</b> Immer dieselbe Adresse, immer
 * ein frischer Browser. Was hier wächst, wächst am Schließen — und was nicht
 * wächst, entlastet die Laufzeitumgebung vom Verdacht.
 *
 * <p><b>Und deshalb wartet er lange.</b> Chromium räumt seine Prozesse nicht
 * im selben Augenblick ab; wer sofort nach dem Schließen zählt, zählt einen
 * Zwischenzustand und nennt ihn ein Leck. Fünfzehn Sekunden je Abschnitt sind
 * genug, dass ein Aufräumen stattgefunden haben muss.
 */
public final class LifecycleBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/Lifecycle");

    /** Wie lange ein Zustand gehalten wird, in Takten zu 50 ms. */
    private static final int HOLD_TICKS = 300;

    private static final int CYCLES = 3;

    private static boolean running;
    private static boolean finished;
    private static int tick;
    private static int cycle;
    private static Phase phase = Phase.SETTLE;
    private static long phaseStartedNanos;

    private enum Phase {
        /** Vor dem ersten Öffnen: Chromium läuft, aber kein Browser. */
        SETTLE,
        /** Die Oberfläche ist offen. */
        OPEN,
        /** Die Oberfläche ist geschlossen, es wird auf das Aufräumen gewartet. */
        CLOSED,
        DONE
    }

    private LifecycleBenchmark() {
    }

    public static boolean finished() {
        return finished;
    }

    /** Beginnt den Ablauf. Zu rufen, wenn eine Welt geladen ist. */
    public static void start() {
        if (running) {
            return;
        }
        running = true;
        phaseStartedNanos = System.nanoTime();
        LOG.info("=== LIFECYCLE-LAUF {} — {} Zyklen zu je {} s offen und {} s zu ===",
                System.currentTimeMillis() / 1000, CYCLES,
                HOLD_TICKS / 20, HOLD_TICKS / 20);
        mark("START", "Chromium läuft, noch kein Browser dieser Messung");
    }

    /**
     * Ein Takt des Clients.
     *
     * <p>Der Ablauf hängt am Takt und nicht am Bild: Ein geschlossener
     * Bildschirm zeichnet nicht, und ein Ablauf, der nur beim Zeichnen
     * weiterläuft, bliebe genau an der Stelle stehen, die er messen soll.
     */
    public static void tick(Minecraft client) {
        if (!running || phase == Phase.DONE) {
            return;
        }
        tick++;
        if (tick < HOLD_TICKS) {
            return;
        }
        tick = 0;
        switch (phase) {
            case SETTLE, CLOSED -> {
                if (phase == Phase.CLOSED) {
                    mark("NACH-DEM-SCHLIESSEN",
                            "Zyklus " + cycle + " — Aufräumzeit abgelaufen");
                    if (cycle >= CYCLES) {
                        phase = Phase.DONE;
                        finished = true;
                        LOG.info("=== LIFECYCLE-LAUF FERTIG ===");
                        return;
                    }
                }
                cycle++;
                boolean opened = IdeScreen.open(client);
                phase = Phase.OPEN;
                mark("GEÖFFNET", "Zyklus " + cycle + (opened ? "" : " — ÖFFNEN FEHLGESCHLAGEN"));
            }
            case OPEN -> {
                // setScreen(null) geht denselben Weg wie die Fluchttaste und
                // ruft removed(), wo die Sitzung geschlossen wird.
                client.setScreen(null);
                phase = Phase.CLOSED;
                mark("GESCHLOSSEN", "Zyklus " + cycle);
            }
            default -> {
            }
        }
    }

    /**
     * Setzt eine Marke ins Protokoll.
     *
     * <p>Gezählt wird von außen, und außen braucht einen Zeitpunkt, an dem es
     * seine eigene Aufzeichnung festmachen kann. Die Uhrzeit steht ohnehin in
     * jeder Zeile; hier steht, was in diesem Augenblick geschehen ist.
     */
    private static void mark(String what, String detail) {
        double seconds = (System.nanoTime() - phaseStartedNanos) / 1_000_000_000.0;
        LOG.info("LIFECYCLE {} bei {} s | {}", what,
                String.format(java.util.Locale.GERMANY, "%.1f", seconds), detail);
    }
}
