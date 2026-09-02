package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.BrowserVisibility;
import dev.devpanda.bamboocef.web.WebRuntimeStatus;
import dev.devpanda.bamboocef.web.WebSupport;
import dev.devpanda.bamboocef.web.measure.DurationSamples;
import dev.devpanda.bamboocef.web.texture.GlTextureBackend;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Misst, was der Bildweg kostet — in Zahlen statt in Eindrücken.
 *
 * <p><b>Warum drei Szenarien und nicht eines.</b> „Wie teuer ist ein Browser"
 * hat keine Antwort, weil die Kosten fast vollständig davon abhängen, wie viel
 * sich auf der Seite bewegt. Ein ruhender Texteditor und eine vollflächige
 * Animation liegen um mehrere Größenordnungen auseinander. Gemessen wird
 * deshalb der Bereich, nicht ein Punkt darin:
 *
 * <ol>
 *   <li><b>Ruhe</b> — eine Seite, die sich nicht ändert. Die untere Grenze.
 *       Der Fall, in dem ein Editor die meiste Zeit verbringt.</li>
 *   <li><b>Kleine Änderung</b> — ein blinkendes Kästchen. Der Fall, für den
 *       Ausschnitte gebaut sind: ein Cursor, ein Tastendruck, ein Menü.</li>
 *   <li><b>Vollflächig</b> — jedes Bild ändert alles. Die obere Grenze, unter
 *       der niemand mehr etwas sparen kann.</li>
 * </ol>
 *
 * <p>Alle drei laufen bei 1920×1080, damit die Zahlen untereinander
 * vergleichbar sind und die obere Grenze keine geschönte ist.
 *
 * <p>Läuft einmal je Sitzung, nur wenn {@code fn.benchmark} gesetzt ist —
 * eine Messung, die immer mitliefe, wäre ein Kostenposten statt eines
 * Nachweises.
 */
public final class WebBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/WebBenchmark");

    /** Die Größe, bei der gemessen wird — ein voller Bildschirm. */
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    /** Ein Tick ist eine zwanzigstel Sekunde. */
    private static final int TICKS_PER_SECOND = 20;

    /** Bevor MCEF nicht steht, misst jede Messung das Hochfahren. */
    private static final int WARMUP_TICKS = 10 * TICKS_PER_SECOND;

    /** Wie lange je Szenario gemessen wird. */
    private static final int MEASURE_TICKS = 6 * TICKS_PER_SECOND;

    /** Wie lange auf das erste Bild gewartet wird, bevor aufgegeben wird. */
    private static final int PATIENCE_TICKS = 15 * TICKS_PER_SECOND;

    /**
     * Eine Seite, die sich nie ändert.
     *
     * <p>Kein Skript, keine Animation, kein blinkender Cursor. Was hier
     * gemessen wird, ist die Frage: Kostet ein offener Browser etwas, wenn
     * niemand etwas tut?
     */
    private static final String STILL = """
            <!doctype html><html><head><style>
              html,body{margin:0;height:100%;background:#1e1e2e;color:#cdd6f4;
                        font:16px sans-serif}
              .box{padding:40px}
            </style></head><body><div class="box">
              <h1>Ruhende Seite</h1>
              <p>Hier bewegt sich nichts. Genau darum geht es.</p>
            </div></body></html>
            """;

    /**
     * Eine Seite, auf der sich ein kleines Kästchen ändert.
     *
     * <p>24×24 Bildpunkte auf 1920×1080 — ein Tausendstel der Fläche. Wenn
     * Ausschnitte funktionieren, muss sich das in den Bytes je Bild zeigen;
     * wenn nicht, kommt jedes Mal das ganze Bild.
     *
     * <p>Die Größe ist mit Absicht die eines Textcursors: Das ist der Fall,
     * der in einem Editor tatsächlich vorkommt, und zwar dauernd.
     */
    private static final String BLINKER = """
            <!doctype html><html><head><style>
              html,body{margin:0;height:100%;background:#1e1e2e}
              #dot{position:absolute;left:200px;top:300px;width:24px;height:24px;
                   background:#f38ba8}
            </style></head><body><div id="dot"></div><script>
              var on = true;
              setInterval(function () {
                on = !on;
                document.getElementById('dot').style.background =
                    on ? '#f38ba8' : '#1e1e2e';
              }, 100);
            </script></body></html>
            """;

    /**
     * Eine Seite, auf der sich jedes Bild vollständig ändert.
     *
     * <p>Der Hintergrund des ganzen Dokuments wechselt bei jedem Bild die
     * Farbe. Es gibt keinen Ausschnitt, der kleiner wäre als alles — das ist
     * der Fall, in dem der Bildweg so viel kostet, wie er überhaupt kosten
     * kann.
     */
    private static final String FLOOD = """
            <!doctype html><html><head><style>
              html,body{margin:0;height:100%}
            </style></head><body><script>
              var step = 0;
              function paint() {
                step = (step + 3) % 360;
                document.body.style.background =
                    'hsl(' + step + ',70%,50%)';
                requestAnimationFrame(paint);
              }
              paint();
            </script></body></html>
            """;

    /** Die Schritte, in dieser Reihenfolge. */
    private enum Stage {
        WARMUP, BASELINE, STILL_PAGE, BLINKER_PAGE, FLOOD_PAGE, DONE
    }

    private static final boolean enabled = Boolean.getBoolean("fn.benchmark");
    private static Stage stage = Stage.WARMUP;
    private static int ticksInStage;

    private static BrowserSession session;
    private static boolean measuring;
    private static long paintsAtStart;
    private static long measureStartNanos;

    /** Bildzeiten von Minecraft, einmal ohne und einmal mit laufendem Browser. */
    private static final DurationSamples baselineFrames = new DurationSamples();
    private static final DurationSamples loadedFrames = new DurationSamples();
    private static DurationSamples collectingFrames;
    private static long lastFrameNanos;

    private WebBenchmark() {
    }

    /** Ob die Messung durch ist — für alles, was danach kommen soll. */
    public static boolean finished() {
        return !enabled || stage == Stage.DONE;
    }

    /** Wird vom Bild des Clients gerufen — misst den Abstand zweier Bilder. */
    public static void frameRendered() {
        if (collectingFrames == null) {
            lastFrameNanos = 0;
            return;
        }
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            collectingFrames.record(now - lastFrameNanos);
        }
        lastFrameNanos = now;
    }

    /** Wird vom Takt des Clients gerufen. */
    public static void tick() {
        if (!enabled || stage == Stage.DONE) {
            return;
        }
        ticksInStage++;
        switch (stage) {
            case WARMUP -> warmup();
            case BASELINE -> baseline();
            case STILL_PAGE -> scenario(STILL, "Ruhe", Stage.BLINKER_PAGE);
            case BLINKER_PAGE -> scenario(BLINKER, "Kleine Änderung", Stage.FLOOD_PAGE);
            case FLOOD_PAGE -> scenario(FLOOD, "Vollflächig", Stage.DONE);
            case DONE -> { }
        }
    }

    private static void warmup() {
        // Die Nachweise kommen zuerst und haben ihre eigenen Browser. Zwei
        // gleichzeitig teilten sich die Bildrate, und die Messung berichtete
        // die Hälfte als Grenze.
        if (ticksInStage < WARMUP_TICKS || !WebSelfTest.finished()
                || !dev.devpanda.bamboocef.web.screen.RenderProof.finished()
                || !dev.devpanda.bamboocef.web.screen.BackdropBenchmark.finished()) {
            return;
        }
        // <b>Nicht im Hauptmenü messen.</b> Ohne geladene Welt deckelt
        // Minecraft die Bildrate hart auf sechzig — unabhängig von VSync und
        // der eingestellten Sperre. Die Bildzeit klebt dann bei 16,6 ms, und
        // ein Browser, der weniger als das kostet, sähe gratis aus. Genau so
        // ist die erste Messung ausgegangen.
        if (Minecraft.getInstance().level == null) {
            if (ticksInStage % (10 * TICKS_PER_SECOND) == 0) {
                LOG.info("Messung wartet auf eine geladene Welt — im Hauptmenü "
                        + "deckelt Minecraft auf sechzig Bilder und verfälscht die Bildzeit");
            }
            return;
        }
        WebRuntimeStatus status = WebSupport.ensureStarted();
        if (!status.usable()) {
            LOG.info("Messung übersprungen: {}", status);
            stage = Stage.DONE;
            return;
        }
        reportEnvironment();
        collectingFrames = baselineFrames;
        lastFrameNanos = 0;
        enter(Stage.BASELINE);
    }

    /**
     * Die Bildzeit ohne Browser, in derselben Sitzung und derselben Szene.
     *
     * <p><b>Ein Vergleich mit einem früheren Lauf wäre keiner.</b> Wo der
     * Spieler steht, was im Bild ist, wie warm die Grafikkarte ist — all das
     * ändert die Bildzeit stärker als ein Browser. Nur eine Messung
     * unmittelbar davor, ohne dazwischen etwas anzufassen, trägt.
     */
    private static void baseline() {
        if (ticksInStage < MEASURE_TICKS) {
            return;
        }
        LOG.info("Messung Bildzeit ohne Browser: {}", baselineFrames.summary());
        collectingFrames = null;
        enter(Stage.STILL_PAGE);
    }

    private static void scenario(String page, String name, Stage andThen) {
        if (session == null) {
            if (!open(page, name)) {
                enter(andThen);
            }
            return;
        }
        if (!measuring) {
            // <b>Erst das erste Bild abwarten, dann die Zähler leeren.</b>
            // Das erste Bild ist immer ein Vollbild — es gehört zum Aufbau
            // und nicht zur Messung. Wer es mitzählt, findet auch beim
            // Blinker einen Vollbild-Upload und schließt daraus, Ausschnitte
            // funktionierten nicht.
            if (session.paints() >= 1) {
                session.texture().resetStats();
                paintsAtStart = session.paints();
                measureStartNanos = System.nanoTime();
                measuring = true;
                if (andThen == Stage.DONE) {
                    collectingFrames = loadedFrames;
                    lastFrameNanos = 0;
                }
                ticksInStage = 0;
            } else if (ticksInStage > PATIENCE_TICKS) {
                LOG.warn("Messung „{}“: kein Bild nach {} Sekunden, URL {}",
                        name, PATIENCE_TICKS / TICKS_PER_SECOND, session.currentUrl());
                closeSession();
                enter(andThen);
            }
            return;
        }
        if (ticksInStage < MEASURE_TICKS) {
            return;
        }
        report(name);
        if (andThen == Stage.DONE) {
            LOG.info("Messung Bildzeit mit laufendem Browser: {}", loadedFrames.summary());
            reportFrameCost();
            collectingFrames = null;
        }
        closeSession();
        enter(andThen);
    }

    private static boolean open(String page, String name) {
        try {
            Path file = Files.createTempFile("fn-benchmark", ".html");
            Files.writeString(file, page, StandardCharsets.UTF_8);
            file.toFile().deleteOnExit();
            session = BrowserSession.open(file.toUri().toString(), false,
                    WIDTH, HEIGHT, BrowserVisibility.FOREGROUND);
            LOG.info("Messung „{}“ beginnt, {}x{}", name, WIDTH, HEIGHT);
            ticksInStage = 0;
            return true;
        } catch (Throwable broken) {
            LOG.warn("Messung „{}“ ließ sich nicht starten", name, broken);
            return false;
        }
    }

    private static void report(String name) {
        GlTextureBackend texture = session.texture();
        double seconds = (System.nanoTime() - measureStartNanos) / 1_000_000_000.0;
        long paints = session.paints() - paintsAtStart;
        double perSecond = paints / seconds;
        double megabytesPerSecond = texture.uploadedBytes() / seconds / (1024.0 * 1024.0);
        double fullFrameBytes = (double) WIDTH * HEIGHT * 4;

        LOG.info("Messung „{}“ über {} s: {} Bilder ({} je Sekunde)",
                name, format(seconds), paints, format(perSecond));
        LOG.info("Messung „{}“: {} Uploads, davon {} als Vollbild, {} Ausschnitte",
                name, texture.uploads(), texture.fullUploads(), texture.regions());
        LOG.info("Messung „{}“: {} KB je Bild — das wären {} Prozent eines Vollbilds",
                name, format(texture.bytesPerUpload() / 1024.0),
                format(texture.bytesPerUpload() / fullFrameBytes * 100.0));
        LOG.info("Messung „{}“: {} MB je Sekunde über den Bus", name,
                format(megabytesPerSecond));
        LOG.info("Messung „{}“: Uploadzeit {}", name, texture.uploadTimes().summary());
    }

    /**
     * Was der Browser die Bildzeit kostet — die einzige Zahl, die der Spieler
     * merkt.
     */
    private static void reportFrameCost() {
        double before = baselineFrames.percentile(50) / 1000.0;
        double after = loadedFrames.percentile(50) / 1000.0;
        double before95 = baselineFrames.percentile(95) / 1000.0;
        double after95 = loadedFrames.percentile(95) / 1000.0;
        LOG.info("Messung Bildzeit: Median {} ms ohne, {} ms mit — Unterschied {} ms",
                format(before), format(after), format(after - before));
        LOG.info("Messung Bildzeit: p95 {} ms ohne, {} ms mit — Unterschied {} ms",
                format(before95), format(after95), format(after95 - before95));
        if (before > 0) {
            LOG.info("Messung Bildzeit: entspricht {} statt {} Bildern je Sekunde",
                    format(1000.0 / after), format(1000.0 / before));
        }
    }

    /**
     * Was über die Messung gewusst werden muss, um sie zu deuten.
     *
     * <p><b>Ohne die Bildratensperre ist jede Bildzeit wertlos.</b> Steht ein
     * Limit bei sechzig, deckelt es beide Messungen gleich, und der
     * Unterschied verschwindet in der Wartezeit auf den Bildschirm — der
     * Browser sähe gratis aus.
     */
    private static void reportEnvironment() {
        try {
            Minecraft client = Minecraft.getInstance();
            LOG.info("Messung Umgebung: VSync {}, Bildratensperre {}, Fenster {}x{}",
                    client.options.enableVsync().get(),
                    client.options.framerateLimit().get(),
                    client.getWindow().getWidth(), client.getWindow().getHeight());
        } catch (Throwable unknown) {
            LOG.info("Messung Umgebung: nicht ermittelbar", unknown);
        }
        // Diese JCEF-Fassung kennt weder CefBrowserSettings noch ein
        // windowless_frame_rate. Chromiums Voreinstellung ist also die
        // Obergrenze, und sie steht nicht zur Verhandlung — was hier an
        // Bildern je Sekunde herauskommt, ist keine Wahl, sondern ein Befund.
        LOG.info("Messung Umgebung: die Bildrate von Chromium ist in dieser "
                + "JCEF-Fassung nicht einstellbar — gemessen wird die Voreinstellung");
    }

    private static void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
        measuring = false;
    }

    private static void enter(Stage next) {
        stage = next;
        ticksInStage = 0;
        if (next == Stage.DONE) {
            LOG.info("Messung fertig.");
        }
    }

    private static String format(double value) {
        return String.format(Locale.GERMANY, "%.1f", value);
    }
}
