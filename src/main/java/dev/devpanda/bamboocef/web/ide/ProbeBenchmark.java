package dev.devpanda.bamboocef.web.ide;

import dev.devpanda.bamboocef.web.measure.DurationSamples;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Locale;

/**
 * The measurement run for pace and typing latency.
 *
 * <p><b>What it now serves.</b> All previous numbers of this project came
 * about over a remote session. A remote session can interfere with the image
 * path — window management, frame synchronization, scheduling on
 * the graphics card hang on it. This run checks locally whether the
 * key numbers hold, and expressly does <i>not</i> repeat the
 * whole old series. Two stages suffice.
 *
 * <p><b>The first stage measures the pace, not the duration.</b> A page that
 * recolors a small field in every frame forces Chromium to deliver as often
 * as it can. The gap between two frames is then the pace itself and nothing
 * else — the number that carries the whole finding after Step I and that must
 * jump to sixty frames per second after the patch.
 *
 * <p><b>The second stage measures what the product is meant to be:</b> Monaco
 * complete, with the pre-blurred background instead of real blurring.
 * Both in one run, so that the same session delivers both numbers.
 */
public final class ProbeBenchmark extends IdeScreen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/Probe");

    private static final long TYPING_NANOS = 45_000_000_000L;
    /**
     * How long a memory stage stands.
     *
     * <p>Long enough that the measurement from outside catches it: the call that
     * queries the memory itself needs a good second. On the first
     * attempt all five stages stood at the same eight hundred megabytes —
     * each time the same state was measured.
     */
    private static final long RAM_NANOS = 20_000_000_000L;
    private static final int SETTLE_FRAMES = 40;

    private static boolean finished;

    /**
     * What is measured in order.
     *
     * <p>The memory stages first: once Monaco is loaded, the
     * empty state does not come back in this session.
     */
    private enum Step {
        // The local control run, after all previous measurements ran over a
        // remote session. Two stages, no more are needed: once the
        // bare pace, once the configuration that the product is meant to be.
        // Everything in between is answered and is not repeated.
        TAKT("A Takt (kontinuierlicher Paint)", "takt", 20_000_000_000L, false),
        MONACO_VORBLUR("B Monaco + vorgeblurter Hintergrund", "monaco-vorblur",
                TYPING_NANOS, true),

        DONE("fertig", null, 0, false);

        final String label;
        final String variant;
        final long nanos;
        final boolean typing;

        Step(String label, String variant, long nanos, boolean typing) {
            this.label = label;
            this.variant = variant;
            this.nanos = nanos;
            this.typing = typing;
        }
    }

    private static final String TIPPTEXT = "    let vorrat = storage.count(x)";

    private final String pageBase;
    private Step step = Step.TAKT;
    private long stepStartedNanos;
    private long paintsAtStart;
    private int typedIndex;
    private int frameInStep;
    private int settleFrames;
    private boolean announced;

    private final DurationSamples frameTimes = new DurationSamples();
    private long lastFrameNanos;

    private ProbeBenchmark(String url, String pageBase) {
        super(url);
        this.pageBase = pageBase;
    }

    public static boolean finished() {
        return finished;
    }

    /** Opens the benchmark — it begins with an empty page. */
    public static boolean open(Minecraft client) {
        String ide = prepare(client);
        if (ide == null) {
            return false;
        }
        // The measurement surface lies next to the interface and is unpacked the same way.
        Path folder = client.gameDirectory.toPath()
                .resolve("factorynetwork-web").resolve("ide");
        Path probe = folder.resolve("probe.html");
        try (java.io.InputStream stream = ProbeBenchmark.class.getClassLoader()
                .getResourceAsStream("assets/factorynetwork/web/ide/probe.html")) {
            if (stream == null) {
                LOG.warn("Die Messfläche fehlt im Klassenpfad");
                return false;
            }
            java.nio.file.Files.copy(stream, probe,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException broken) {
            LOG.warn("Die Messfläche ließ sich nicht ablegen", broken);
            return false;
        }
        String base = probe.toUri().toString();
        client.setScreen(new ProbeBenchmark(base + "?v=takt", base));
        return true;
    }

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
        if (step == Step.DONE) {
            finishIfDue();
            return;
        }
        if (!hasSession()) {
            return;
        }
        if (!announced) {
            announced = true;
            LOG.info("=== LAUF {} — Browser {}x{} ===", System.currentTimeMillis() / 1000,
                    session().width(), session().height());
            stepStartedNanos = System.nanoTime();
            return;
        }
        if (pageLoading()) {
            return;
        }
        if (settleFrames > 0) {
            settleFrames--;
            if (settleFrames == 0) {
                stepStartedNanos = System.nanoTime();
                paintsAtStart = paints();
                texture().resetStats();
                session().resetLatency();
                frameTimes.reset();
                // frisch() instead of leeren(): it also resets the content of
                // the editor. Without that, consecutive stages type
                // into the same, ever-lengthening document — in Step H
                // exactly that made one stage look eight milliseconds slower
                // than it was.
                runScript("window.fnProbe && fnProbe.frisch()");
                if (step.typing) {
                    runScript("window.fnProbe && fnProbe.fokus()");
                    click(0.5, 0.4);
                }
            }
            return;
        }
        frameInStep++;
        if (step.typing && frameInStep % 8 == 0) {
            typeOneCharacter();
        }
        if (System.nanoTime() - stepStartedNanos < step.nanos) {
            return;
        }
        report();
        step = Step.values()[step.ordinal() + 1];
        frameInStep = 0;
        begin();
        if (step == Step.DONE) {
            // <b>Do not close immediately.</b> The page's report goes over
            // the console and needs a handful of frames until it arrives.
            // On the last run, closing overtook it, and for the
            // last stage the page's view was missing entirely.
            closingFrames = 30;
        }
    }

    /** How many frames are still to wait until closing is allowed. */
    private int closingFrames = -1;

    private void finishIfDue() {
        if (closingFrames < 0) {
            return;
        }
        if (closingFrames-- == 0) {
            LOG.info("Probemessung fertig.");
            finished = true;
            onClose();
        }
    }

    private void begin() {
        if (step == Step.DONE) {
            return;
        }
        // Reload the page instead of opening a second browser: two
        // browsers would share the frame rate, and every number would be half as good.
        runScript("location.href='" + pageBase + "?v=" + step.variant + "'");
        stepStartedNanos = System.nanoTime();
        settleFrames = SETTLE_FRAMES;
    }

    /**
     * One character on the full path, as it would come from a keyboard.
     *
     * <p><b>Sending only a character is not enough.</b> That produces in
     * Chromium an {@code input} event, but no {@code keydown} — and the
     * page measures on {@code keydown}. On the first attempt, that is why
     * every line read {@code n=0}: Java typed, the page saw nothing.
     *
     * <p>Real input comes in three parts: key down, character, key
     * up. That is exactly how it is typed here.
     */
    private void typeOneCharacter() {
        char zeichen = TIPPTEXT.charAt(typedIndex++ % TIPPTEXT.length());
        int taste = zeichen == ' ' ? GLFW.GLFW_KEY_SPACE
                : Character.isLetter(zeichen)
                    ? GLFW.GLFW_KEY_A + (Character.toUpperCase(zeichen) - 'A')
                    : GLFW.GLFW_KEY_PERIOD;
        int scanCode = GLFW.glfwGetKeyScancode(taste);
        keyPressed(taste, scanCode, 0);
        charTyped(zeichen, 0);
        keyReleased(taste, scanCode, 0);
    }

    private void click(double across, double down) {
        double x = width * across;
        double y = height * down;
        mouseMoved(x, y);
        mouseClicked(x, y, 0);
        mouseReleased(x, y, 0);
    }

    private void report() {
        double seconds = (System.nanoTime() - stepStartedNanos) / 1_000_000_000.0;
        reportPace(seconds);
        if (!step.typing) {
            return;
        }
        long paints = paints() - paintsAtStart;
        var texture = texture();
        var toPaint = session().toPaint();
        var total = session().totalLatency();
        double fullFrame = (double) session().width() * session().height() * 4;

        LOG.info("=== {} ===", step.label);
        LOG.info(String.format(Locale.GERMANY,
                "%s: %d Bilder (%.1f/s), %.1f KB je Bild (%.1f %% eines Vollbilds), "
                        + "%.2f MB/s",
                step.label, paints, paints / seconds,
                texture.bytesPerUpload() / 1024.0,
                texture.bytesPerUpload() / fullFrame * 100.0,
                texture.uploadedBytes() / seconds / (1024.0 * 1024.0)));
        LOG.info(String.format(Locale.GERMANY,
                "%s: A Eingabe→Bild p50 %.1f ms, p95 %.1f ms, max %.1f ms (%d Messungen)",
                step.label, toPaint.percentile(50) / 1000.0,
                toPaint.percentile(95) / 1000.0, toPaint.slowest() / 1000.0,
                toPaint.count()));
        LOG.info(String.format(Locale.GERMANY,
                "%s: B Upload p50 %.1f ms, p95 %.1f ms | gesamt bis sichtbar p50 %.1f ms",
                step.label, texture.uploadTimes().percentile(50) / 1000.0,
                texture.uploadTimes().percentile(95) / 1000.0,
                total.percentile(50) / 1000.0));
        LOG.info(String.format(Locale.GERMANY,
                "%s: Minecrafts Bildzeit p50 %.1f ms, p95 %.1f ms",
                step.label, frameTimes.percentile(50) / 1000.0,
                frameTimes.percentile(95) / 1000.0));
        // The shape of the distribution, not just its position. A pace of thirty
        // frames per second carries its signature visibly: clusters at
        // 33 ms and at 67 ms, almost nothing in between. Distributed compute time
        // would instead look like a single hill.
        LOG.info("{}: Verteilung von A (Fächer zu 4 ms)\n{}",
                step.label, toPaint.histogram(4.0, 25));
        // And now the page's view of the same stretch.
        runScript("window.fnProbe && fnProbe.bericht('" + step.label + "')");
    }

    /**
     * The pace: how far apart two frames from Chromium lie.
     *
     * <p><b>The number that matters on this run.</b> Not how long
     * something takes, but how often anyone gets a turn at all. For a
     * page that changes something in every frame, the gap is the pace itself
     * and nothing else.
     *
     * <p>One caveat belongs in the log: the frame arrives on the render thread,
     * so the gaps lie on Minecraft's own grid of around
     * eight and a half milliseconds. To tell thirty-three from sixteen
     * apart that is easily enough; down to the decimal place it cannot
     * be read.
     */
    private void reportPace(double seconds) {
        var gaps = session().paintGaps();
        if (gaps.count() < 5) {
            LOG.info("{}: zu wenige Bilder für einen Takt ({})", step.label, gaps.count());
            return;
        }
        LOG.info("=== {} ===", step.label);
        LOG.info(String.format(Locale.GERMANY,
                "%s: Takt onPaint→onPaint p10 %.2f ms | p50 %.2f ms | p90 %.2f ms "
                        + "= %.1f Bilder/s (%d Abstände in %.0f s)",
                step.label, gaps.percentile(10) / 1000.0, gaps.percentile(50) / 1000.0,
                gaps.percentile(90) / 1000.0, 1000.0 / (gaps.percentile(50) / 1000.0),
                gaps.count(), seconds));
        LOG.info("{}: Verteilung des Takts (Fächer zu 2 ms)\n{}",
                step.label, gaps.histogram(2.0, 30));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
