import org.cef.CefApp;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.CefSettings;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A4a — Takt und Lebenslauf, ohne Minecraft.
 *
 * <p><b>Was gemessen wird.</b> Der Abstand zwischen zwei {@code onPaint}. Bei
 * {@code windowless_frame_rate = 60} soll er bei 16,7 ms liegen. Der Sollwert
 * stammt aus dem Proof-of-Concept: dort 16,85 ms, also 59,4 Bilder je Sekunde.
 *
 * <p><b>Warum die Schleife aussieht wie Minecrafts.</b> Der gesamte Renderpfad
 * und der Vertrag über das Freigeben der Textur ruhen darauf, dass
 * {@code onPaint} in demselben Thread ankommt, aus dem gepumpt wird. Eine
 * Probe, die CEFs eigene Schleife laufen ließe, prüfte ein anderes Modell und
 * gäbe eine Zusicherung, die im Spiel nicht gilt. Deshalb: ein Thread, der
 * pumpt, und alles andere passiert in ihm.
 *
 * <p><b>Aufruf</b> über {@code run-probe.ps1}. Von Hand:
 * <pre>
 * java -Djava.library.path=&lt;laufzeit&gt; -cp "&lt;laufzeit&gt;/jcef.jar;&lt;klassen&gt;" \
 *      Probe &lt;url&gt; &lt;sekunden&gt; &lt;breite&gt; &lt;höhe&gt;
 * </pre>
 */
public final class Probe {

    /** Sammelt Messwerte und gibt Perzentile heraus. */
    static final class Series {
        private final List<Double> values = new ArrayList<>();

        void add(double milliseconds) {
            values.add(milliseconds);
        }

        int count() {
            return values.size();
        }

        double percentile(int p) {
            if (values.isEmpty()) {
                return Double.NaN;
            }
            List<Double> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            int index = (int) Math.round((p / 100.0) * (sorted.size() - 1));
            return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
        }
    }

    public static void main(String[] args) throws Exception {
        // Minecrafts Renderthread ist nicht der Thread, in dem main() steht.
        // Die Probe bildet das nach: Voreingestellt läuft alles in einem
        // eigenen Thread. -Dprobe.thread=main bleibt hier, -Dprobe.thread=awt
        // überlässt upstream das Feld.
        String where = System.getProperty("probe.thread", "worker");
        if ("worker".equals(where)) {
            // Der Stapel ist absichtlich groß. Chromiums Initialisierung geht
            // tief, und Javas Vorgabe von einem Megabyte ist der Wert, mit dem
            // ein Überlauf im nativen Teil endet — als Ausnahme, die durch
            // JIT-Rahmen zurückläuft und die JVM anhält, statt als Fehler, der
            // sagt, was los war. -Dprobe.stackMb stellt ihn ein.
            long stackBytes = Long.getLong("probe.stackMb", 16L) * 1024L * 1024L;
            Thread render = new Thread(null, () -> {
                try {
                    run(args, true);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(3);
                }
            }, "render", stackBytes);
            render.start();
            render.join();
            return;
        }
        if ("edt".equals(where) || "edt-awt".equals(where)) {
            final boolean own = "edt".equals(where);
            // Derselbe Patch, aber auf AWTs Ereignisthread. Trennt die Frage
            // "liegt es am Schalter" von der Frage "liegt es an diesem Thread".
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    run(args, own);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return;
        }
        run(args, !"awt".equals(where));
    }

    /**
     * Eine Runde der Nachrichtenschleife.
     *
     * <p>Mit eigenem Thread über {@code doMessageLoopWorkNow} — genau eine
     * Runde, hier und jetzt. Ohne über {@code doMessageLoopWork}, das die
     * Arbeit an AWT weiterreicht; dann kommt sie dort an und nicht hier.
     */
    private static void pump(CefApp app, boolean ownThread) {
        if (ownThread) {
            app.doMessageLoopWorkNow();
        } else {
            app.doMessageLoopWork(0);
        }
    }

    /** Sagt laut, wo wir sind — damit ein Absturz im nativen Teil eine Stelle hat. */
    private static void schritt(String was) {
        System.out.println("  ... " + was);
        System.out.flush();
    }

    private static void run(String[] args, boolean ownThread) throws Exception {
        String url = args.length > 0 ? args[0] : "about:blank";
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        int width = args.length > 2 ? Integer.parseInt(args[2]) : 1920;
        int height = args.length > 3 ? Integer.parseInt(args[3]) : 1080;

        System.out.println("Probe: " + url + ", " + seconds + " s, " + width + "x" + height);
        System.out.println("Thread: " + Thread.currentThread());

        if (!CefApp.startup(new String[] {})) {
            System.out.println("FEHLER: CefApp.startup fehlgeschlagen");
            System.exit(2);
        }

        // Vor dem ersten getInstance und aus demselben Thread, in dem danach
        // gepumpt wird. Ohne das schiebt CefApp Initialisierung und Schleife
        // auf AWTs Ereignisthread und deckelt den Takt bei dreißig.
        //
        // Der Vergleich ist kein Luxus: Wenn etwas abstürzt, sagt er, ob es am
        // eigenen Thread liegt oder an etwas anderem.
        System.out.println("CEF-Thread: " + (ownThread ? "eigener (useCallingThread)" : "AWT"));
        if (ownThread) {
            CefApp.useCallingThread();
        }

        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        settings.cache_path = null; // jeder Lauf frisch
        // -Dprobe.log=<datei> schaltet Chromiums eigenes Protokoll an. Wenn
        // etwas im nativen Teil stirbt, ist es die einzige Quelle, die sagt,
        // wie weit CEF gekommen war.
        //
        // Ohne Angabe schreibt Chromium ein debug.log in das aktuelle
        // Verzeichnis — im Zweifel mitten ins Repository. Deshalb steht hier
        // immer ein Pfad.
        String logFile = System.getProperty("probe.log");
        if (logFile != null) {
            settings.log_file = logFile;
            settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_VERBOSE;
        } else {
            settings.log_file = new java.io.File(System.getProperty("java.io.tmpdir"),
                    "fn-probe-cef.log").getAbsolutePath();
            settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        }

        schritt("getInstance");
        CefApp app = CefApp.getInstance(settings);
        schritt("createClient");
        CefClient client = app.createClient();
        schritt("createClient fertig");

        CefBrowserSettings browserSettings = new CefBrowserSettings();
        browserSettings.windowless_frame_rate = 60;

        Series paintGaps = new Series();
        Series pumpGaps = new Series();
        long[] lastPaint = {0};
        int[] paintThreadMismatches = {0};
        Thread pumpThread = Thread.currentThread();

        OsrBrowser browser = new OsrBrowser(client, url, true, browserSettings,
                new OsrBrowser.Events() {
                    @Override
                    public void frame(boolean popup, Rectangle[] dirty, ByteBuffer buffer,
                            int w, int h) {
                        if (popup) {
                            return;
                        }
                        if (Thread.currentThread() != pumpThread) {
                            paintThreadMismatches[0]++;
                        }
                        long now = System.nanoTime();
                        if (lastPaint[0] != 0) {
                            paintGaps.add((now - lastPaint[0]) / 1_000_000.0);
                        }
                        lastPaint[0] = now;
                        // Nichts hochladen. Hier zählt der Takt, nicht der Weg
                        // in die Textur.
                    }
                });

        schritt("setCloseAllowed");
        browser.setCloseAllowed();
        schritt("createImmediately");
        browser.createImmediately();
        schritt("resize");
        browser.resize(width, height);
        schritt("erste Runde der Schleife");

        // Die Pumpschleife. Alles Weitere passiert in diesem Thread.
        long lastPump = 0;
        long until = System.nanoTime() + seconds * 1_000_000_000L;
        while (System.nanoTime() < until) {
            pump(app, ownThread);
            long now = System.nanoTime();
            if (lastPump != 0) {
                pumpGaps.add((now - lastPump) / 1_000_000.0);
            }
            lastPump = now;
            Thread.sleep(1);
        }

        double p50 = paintGaps.percentile(50);
        System.out.println();
        System.out.println("== Takt");
        System.out.printf("onPaint          %d Bilder in %d s%n", paintGaps.count() + 1, seconds);
        System.out.printf("Abstand p50      %.2f ms  (%.1f/s)%n", p50, 1000.0 / p50);
        System.out.printf("Abstand p95      %.2f ms%n", paintGaps.percentile(95));
        System.out.printf("Abstand p99      %.2f ms%n", paintGaps.percentile(99));
        System.out.printf("Pumpe p50        %.2f ms%n", pumpGaps.percentile(50));
        System.out.printf("Pumpe p95        %.2f ms%n", pumpGaps.percentile(95));
        System.out.printf("onPaint im Pumpthread: %s%n",
                paintThreadMismatches[0] == 0 ? "ja, immer" : "NEIN, " + paintThreadMismatches[0]
                        + " Abweichungen");

        System.out.println();
        System.out.println("== Herunterfahren");
        browser.close(true);
        // Weiterpumpen, damit CEF das Schließen abarbeiten kann.
        for (int i = 0; i < 200; i++) {
            pump(app, ownThread);
            Thread.sleep(5);
        }
        app.dispose();
        for (int i = 0; i < 200; i++) {
            pump(app, ownThread);
            Thread.sleep(5);
        }
        System.out.println("Zustand: " + CefApp.getState());
        System.out.println("Fertig.");

        // CEF hängt an nicht-Dämon-Threads; ohne das kehrt die JVM nicht zurück.
        System.exit(0);
    }
}
