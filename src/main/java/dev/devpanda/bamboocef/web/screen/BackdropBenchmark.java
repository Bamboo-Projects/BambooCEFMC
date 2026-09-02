package dev.devpanda.bamboocef.web.screen;

import dev.devpanda.bamboocef.web.capture.WorldCapture;
import dev.devpanda.bamboocef.web.measure.DurationSamples;
import dev.devpanda.bamboocef.web.runtime.FrameSchemes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Measures what a Minecraft backdrop costs — at every level.
 *
 * <p><b>The question behind it.</b> From step D it is known what the direction
 * <i>browser → Minecraft</i> costs. Here the reverse direction is added, and
 * it is the less known one: <b>reading</b> an image off the graphics card is a
 * different thing from pushing one up to it.
 *
 * <p><b>And a cost item that is easy to overlook.</b> Every new backdrop
 * invalidates the whole image of the page — the image covers the surface, and
 * all the glass on top of it has to be recomputed. A backdrop at ten frames
 * per second thus produces ten <b>full-frame</b> transfers in the reverse
 * direction. The sub-regions that saved everything in step D do not help here.
 *
 * <p>That is why every section measures both directions.
 */
public final class BackdropBenchmark extends BackdropScreen {

    private static final Logger LOG =
            LoggerFactory.getLogger("FactoryNetwork/BackdropBenchmark");

    private static final long SECTION_NANOS = 5_000_000_000L;
    private static final long SETTLE_NANOS = 3_000_000_000L;

    private static boolean finished;

    /** What is played through one after another. */
    private enum Step {
        SETTLE("Aufbau", Mode.STATIC, 1.0, WorldCapture.Format.PNG),

        // First the resolutions, all as a still image: what a single frame
        // costs should not be masked by the refresh rate.
        STATIC_FULL("Standbild 1,0×  PNG", Mode.STATIC, 1.0, WorldCapture.Format.PNG),
        STATIC_HALF("Standbild 0,5×  PNG", Mode.STATIC, 0.5, WorldCapture.Format.PNG),
        STATIC_QUARTER("Standbild 0,25× PNG", Mode.STATIC, 0.25, WorldCapture.Format.PNG),

        // The same raw, for comparison: how much of the time is compression?
        STATIC_HALF_BMP("Standbild 0,5×  BMP", Mode.STATIC, 0.5, WorldCapture.Format.BMP),
        STATIC_FULL_BMP("Standbild 1,0×  BMP", Mode.STATIC, 1.0, WorldCapture.Format.BMP),

        // Then the rates, all at half edge length.
        LOW_TWO("Bewegt 2/s  0,5× PNG", Mode.LOW_2, 0.5, WorldCapture.Format.PNG),
        LOW_FIVE("Bewegt 5/s  0,5× PNG", Mode.LOW_5, 0.5, WorldCapture.Format.PNG),
        LOW_TEN("Bewegt 10/s 0,5× PNG", Mode.LOW_10, 0.5, WorldCapture.Format.PNG),

        // And once raw at ten: the case where compression cost the most,
        // without it.
        LOW_TEN_BMP("Bewegt 10/s 0,5× BMP", Mode.LOW_10, 0.5, WorldCapture.Format.BMP),

        CACHE("Zwischenspeicher-Probe", Mode.LOW_5, 0.5, WorldCapture.Format.PNG),
        DONE("fertig", Mode.STATIC, 1.0, WorldCapture.Format.PNG);

        final String label;
        final Mode mode;
        final double scale;
        final WorldCapture.Format format;

        Step(String label, Mode mode, double scale, WorldCapture.Format format) {
            this.label = label;
            this.mode = mode;
            this.scale = scale;
            this.format = format;
        }
    }

    private Step step = Step.SETTLE;
    private long stepStartedNanos;
    private long capturesAtStart;
    private long backgroundBytes;

    /** Minecraft's frame time, as long as this screen is open. */
    private final DurationSamples frameTimes = new DurationSamples();
    private long lastFrameNanos;

    /**
     * Whether the generation is left out of the address in this section.
     *
     * <p>The probe on Chromium's cache: if the backdrop stays frozen even
     * though new images come into being, then Chromium is holding on to its
     * old one — and the generation is not negotiable. If it changes anyway,
     * the {@code no-store} sent along is enough.
     */
    private boolean withoutGeneration;

    private BackdropBenchmark(String url) {
        super(Component.literal("Hintergrundmessung"), url,
                Step.SETTLE.mode, Step.SETTLE.scale, Step.SETTLE.format);
    }

    public static boolean finished() {
        return finished;
    }

    public static void open(Minecraft client) throws Exception {
        Path file = Files.createTempFile("fn-backdrop-bench", ".html");
        Files.writeString(file, PAGE_FOR_BENCHMARK, StandardCharsets.UTF_8);
        file.toFile().deleteOnExit();
        client.setScreen(new BackdropBenchmark(file.toUri().toString()));
    }

    /**
     * Four glass panes over the image, as a UI would have.
     *
     * <p>Deliberately not plainer: what is measured here is also the work
     * Chromium has to spend on the filters after each new backdrop.
     */
    private static final String PAGE_FOR_BENCHMARK = """
            <!doctype html><html lang="de"><head><meta charset="utf-8"><style>
              html,body{margin:0;width:100%;height:100%;overflow:hidden;
                        background:#0a0b10;color:#e8eaf2;
                        font:15px/1.6 "Segoe UI",system-ui,sans-serif}
              #minecraft-background{position:fixed;inset:0;width:100%;height:100%;
                                    object-fit:cover}
              #minecraft-background:not([src]){display:none}
              .desktop{position:relative;height:100%;padding:48px;
                       display:grid;gap:24px;box-sizing:border-box;
                       grid-template-columns:repeat(2,minmax(0,1fr));
                       grid-template-rows:repeat(2,minmax(0,1fr))}
              .glass{background:rgba(20,20,30,0.35);
                     border:1px solid rgba(255,255,255,0.15);
                     border-radius:14px;padding:20px;
                     box-shadow:0 8px 32px rgba(0,0,0,0.35)}
              .g1{backdrop-filter:blur(18px)}
              .g2{backdrop-filter:blur(18px) saturate(140%)}
              .g3{backdrop-filter:blur(40px) saturate(120%)}
              h2{margin:0 0 8px;font-size:16px}
              p{margin:0;font-size:13px;color:#b9bfd0}
            </style></head><body>
              <img id="minecraft-background" alt="">
              <main class="desktop">
                <section class="glass g1"><h2>blur(18px)</h2><p>Weichzeichner</p></section>
                <section class="glass g2"><h2>blur + saturate</h2><p>Sättigung</p></section>
                <section class="glass g3"><h2>blur(40px)</h2><p>stark</p></section>
                <section class="glass"><h2>ohne Filter</h2><p>zum Vergleich</p></section>
              </main>
            <script>
              var bg = document.getElementById('minecraft-background');
              // Ein Bild, das nicht lädt, sagt von sich aus nichts — Chromium
              // schreibt dafür keine Zeile in seine Konsole. Ohne diese
              // Meldung bleibt als einziger Hinweis ein Bruchsymbol in der
              // Ecke, und danach sucht man lange.
              bg.addEventListener('error', function () {
                console.error('Hintergrund lud nicht: ' + bg.getAttribute('src'));
              });
              console.log('Seite bereit');
              window.fnSetBackground = function (url) {
                console.log('Hintergrund gesetzt: ' + url);
                bg.src = url;
              };
            </script></body></html>
            """;

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            frameTimes.record(now - lastFrameNanos);
        }
        lastFrameNanos = now;

        super.render(graphics, mouseX, mouseY, partialTick);
        advance();
    }

    private void advance() {
        if (step == Step.DONE || !hasSession()) {
            return;
        }
        if (stepStartedNanos == 0) {
            stepStartedNanos = System.nanoTime();
            return;
        }
        long limit = step == Step.SETTLE ? SETTLE_NANOS : SECTION_NANOS;
        if (System.nanoTime() - stepStartedNanos < limit) {
            return;
        }
        report();
        step = Step.values()[step.ordinal() + 1];
        beginStep();
        if (step == Step.DONE) {
            LOG.info("Hintergrundmessung fertig.");
            finished = true;
            onClose();
        }
    }

    private void beginStep() {
        setMode(step.mode);
        setScale(step.scale);
        setFormat(step.format);
        withoutGeneration = step == Step.CACHE;
        stepStartedNanos = System.nanoTime();
        capturesAtStart = captures();
        backgroundBytes = 0;
        capture().resetStats();
        frameTimes.reset();
        if (hasSession()) {
            texture().resetStats();
        }
        if (step == Step.CACHE) {
            LOG.info("Zwischenspeicher-Probe: ab jetzt ohne Nummer in der Adresse. "
                    + "Bleibt der Hintergrund stehen, hält Chromium am alten Bild fest.");
        }
    }

    /**
     * Writes an image of the finished screen out.
     *
     * <p><b>Because some questions have no numbers.</b> Whether a
     * quarter-sized capture behind a blur is noticeable stands in no
     * measurement — someone has to look at it. One image per level, under a
     * speaking name, makes the comparison possible without anyone having to
     * sit beside it.
     */
    private void saveScreenshot(String label) {
        try {
            // Strip out everything that means something else in a file name.
            // The slash in "10/s" otherwise became a path separator, and the
            // save failed on a directory that does not exist.
            StringBuilder name = new StringBuilder("fn-");
            for (char each : label.toCharArray()) {
                name.append(Character.isLetterOrDigit(each) ? each : '_');
            }
            name.append(".png");
            net.minecraft.client.Screenshot.grab(
                    minecraft.gameDirectory, name.toString(),
                    minecraft.getMainRenderTarget(), message -> { });
        } catch (Throwable broken) {
            LOG.warn("Bild ließ sich nicht ablegen", broken);
        }
    }

    private void report() {
        if (step == Step.SETTLE) {
            return;
        }
        saveScreenshot(step.label);
        double seconds = (System.nanoTime() - stepStartedNanos) / 1_000_000_000.0;
        long taken = captures() - capturesAtStart;
        WorldCapture worldCapture = capture();
        var osr = texture();

        LOG.info("=== {} ===", step.label);
        LOG.info(String.format(Locale.GERMANY,
                "%s: %d Aufnahmen in %.1f s (%.1f/s), Zielgröße %dx%d",
                step.label, taken, seconds, taken / seconds,
                worldCapture.targetWidth(), worldCapture.targetHeight()));
        if (taken > 0) {
            LOG.info(String.format(Locale.GERMANY,
                    "%s: Verkleinern %.0f µs, Lesen %.0f µs, Kodieren %.0f µs "
                            + "(Mediane) — zusammen %.1f ms je Aufnahme",
                    step.label,
                    worldCapture.blitTimes().percentile(50),
                    worldCapture.readTimes().percentile(50),
                    worldCapture.encodeTimes().percentile(50),
                    (worldCapture.blitTimes().percentile(50)
                            + worldCapture.readTimes().percentile(50)
                            + worldCapture.encodeTimes().percentile(50)) / 1000.0));
            LOG.info(String.format(Locale.GERMANY,
                    "%s: p95 Verkleinern %.0f µs, Lesen %.0f µs, Kodieren %.0f µs",
                    step.label,
                    worldCapture.blitTimes().percentile(95),
                    worldCapture.readTimes().percentile(95),
                    worldCapture.encodeTimes().percentile(95)));
            LOG.info(String.format(Locale.GERMANY,
                    "%s: %.1f KB je Bild, %.2f MB/s nach Minecraft hinaus",
                    step.label, worldCapture.lastBytes() / 1024.0,
                    backgroundBytes / seconds / (1024.0 * 1024.0)));
        }
        LOG.info(String.format(Locale.GERMANY,
                "%s: zurück nach Minecraft %d Uploads (%d Vollbild), %.2f MB/s",
                step.label, osr.uploads(), osr.fullUploads(),
                osr.uploadedBytes() / seconds / (1024.0 * 1024.0)));
        LOG.info(String.format(Locale.GERMANY,
                "%s: Minecrafts Bildzeit p50 %.1f ms, p95 %.1f ms (%.0f Bilder/s)",
                step.label, frameTimes.percentile(50) / 1000.0,
                frameTimes.percentile(95) / 1000.0,
                frameTimes.percentile(50) > 0 ? 1_000_000.0 / frameTimes.percentile(50) : 0.0));
    }

    /** Keeps count of how many bytes the backdrop has cost. */
    @Override
    protected void beforeDrawing() {
        long before = captures();
        super.beforeDrawing();
        if (captures() > before) {
            backgroundBytes += capture().lastBytes();
        }
    }

    /**
     * In the cache probe the generation is left out.
     *
     * <p>This makes the address the same for Chromium every time — and only
     * the {@code no-store} sent along could still move it to reload.
     */
    @Override
    protected String backgroundUrlFor(long generation) {
        return withoutGeneration
                ? FrameSchemes.SCHEME_URL_WITHOUT_GENERATION
                : super.backgroundUrlFor(generation);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
