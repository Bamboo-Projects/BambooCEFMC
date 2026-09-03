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
 * Measures what the frame path costs — in numbers instead of impressions.
 *
 * <p><b>Why three scenarios and not one.</b> "How expensive is a browser" has
 * no answer, because the cost depends almost entirely on how much moves on the
 * page. A resting text editor and a full-surface animation are several orders
 * of magnitude apart. What is measured is therefore the range, not one point
 * within it:
 *
 * <ol>
 *   <li><b>Idle</b> — a page that does not change. The lower bound. The case in
 *       which an editor spends most of its time.</li>
 *   <li><b>Small change</b> — a blinking box. The case regions are built for:
 *       a cursor, a keystroke, a menu.</li>
 *   <li><b>Full-surface</b> — every frame changes everything. The upper bound,
 *       below which no one can save anything more.</li>
 * </ol>
 *
 * <p>All three run at 1920×1080, so that the numbers are comparable with one
 * another and the upper bound is not a flattering one.
 *
 * <p>Runs once per session, only when {@code bamboocef.benchmark} is set — a
 * measurement that always ran along would be a cost item instead of a proof.
 */
public final class WebBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger("BambooCEF/WebBenchmark");

    /** The size at which measuring happens — a full screen. */
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    /** A tick is a twentieth of a second. */
    private static final int TICKS_PER_SECOND = 20;

    /** Until MCEF is up, every measurement measures the startup. */
    private static final int WARMUP_TICKS = 10 * TICKS_PER_SECOND;

    /** How long each scenario is measured. */
    private static final int MEASURE_TICKS = 6 * TICKS_PER_SECOND;

    /** How long the first frame is waited for before giving up. */
    private static final int PATIENCE_TICKS = 15 * TICKS_PER_SECOND;

    /**
     * A page that never changes.
     *
     * <p>No script, no animation, no blinking cursor. What is measured here is
     * the question: does an open browser cost anything when no one does
     * anything?
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
     * A page on which a small box changes.
     *
     * <p>24×24 pixels on 1920×1080 — a thousandth of the surface. If regions
     * work, that has to show in the bytes per frame; if not, the whole frame
     * comes every time.
     *
     * <p>The size is deliberately that of a text cursor: that is the case that
     * actually occurs in an editor, and constantly.
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
     * A page on which every frame changes completely.
     *
     * <p>The background of the whole document changes color on every frame.
     * There is no region smaller than everything — this is the case in which
     * the frame path costs as much as it possibly can.
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

    /** The steps, in this order. */
    private enum Stage {
        WARMUP, BASELINE, STILL_PAGE, BLINKER_PAGE, FLOOD_PAGE, DONE
    }

    private static final boolean enabled = Boolean.getBoolean("bamboocef.benchmark");
    private static Stage stage = Stage.WARMUP;
    private static int ticksInStage;

    private static BrowserSession session;
    private static boolean measuring;
    private static long paintsAtStart;
    private static long measureStartNanos;

    /** Minecraft frame times, once without and once with a running browser. */
    private static final DurationSamples baselineFrames = new DurationSamples();
    private static final DurationSamples loadedFrames = new DurationSamples();
    private static DurationSamples collectingFrames;
    private static long lastFrameNanos;

    private WebBenchmark() {
    }

    /** Whether the measurement is through — for everything that should come after. */
    public static boolean finished() {
        return !enabled || stage == Stage.DONE;
    }

    /** Called from the client's frame — measures the gap between two frames. */
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

    /** Called from the client's tick. */
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
        // The proofs come first and have their own browsers. Two at once would
        // share the frame rate, and the measurement would report half as the
        // bound.
        if (ticksInStage < WARMUP_TICKS || !WebSelfTest.finished()
                || !dev.devpanda.bamboocef.web.screen.RenderProof.finished()
                || !dev.devpanda.bamboocef.web.screen.BackdropBenchmark.finished()) {
            return;
        }
        // <b>Do not measure in the main menu.</b> Without a loaded world
        // Minecraft caps the frame rate hard at sixty — regardless of VSync and
        // the configured limit. The frame time then sticks at 16.6 ms, and a
        // browser that costs less than that would look free. That is exactly how
        // the first measurement turned out.
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
     * The frame time without a browser, in the same session and the same scene.
     *
     * <p><b>A comparison with an earlier run would be none.</b> Where the player
     * stands, what is in the frame, how warm the graphics card is — all of that
     * changes the frame time more than a browser does. Only a measurement
     * immediately before, without touching anything in between, holds.
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
            // <b>First wait for the first frame, then clear the counters.</b>
            // The first frame is always a full upload — it belongs to the
            // build-up and not to the measurement. Whoever counts it in finds a
            // full upload even for the blinker and concludes that regions do not
            // work.
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
     * What the browser costs in frame time — the only number the player
     * notices.
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
     * What must be known about the measurement in order to interpret it.
     *
     * <p><b>Without the frame-rate limit every frame time is worthless.</b> If a
     * limit sits at sixty, it caps both measurements equally, and the difference
     * disappears into the wait for the screen — the browser would look free.
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
        // This JCEF version knows neither CefBrowserSettings nor a
        // windowless_frame_rate. Chromium's default is therefore the upper
        // bound, and it is not up for negotiation — what comes out here in
        // frames per second is not a choice but a finding.
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
