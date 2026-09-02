package dev.devpanda.bamboocef.web.ide;

import dev.devpanda.bamboocef.web.measure.DurationSamples;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Answers the one question that stayed open after Step F: how does typing
 * feel at full resolution?
 *
 * <p><b>Measured, not extrapolated.</b> The window size comes at startup
 * via {@code --width} and {@code --height}, and the browser gets exactly as
 * many pixels. What stands here holds for the resolution in which the window
 * is currently running — it is included in every line.
 *
 * <p><b>Long sections instead of many.</b> In Step F the
 * typing scenarios ran four seconds and delivered forty measurements; a p95 from that
 * is a claim. Here each section types for three quarters of a minute, and
 * the distribution holds.
 *
 * <p><b>The delay is broken down.</b> Between key press and frame lie
 * three stretches, and only one of them is ours:
 *
 * <pre>
 *   A   input → Chromium delivers a frame        (Chromium and Monaco)
 *   B   push the frame into the texture           (our path)
 *   C   until Minecraft next draws                (waiting time)
 * </pre>
 *
 * <p>Whether we are stuck on the upload or on Chromium is decided by these three
 * numbers — and by whether their sum matches the measured total stretch. If
 * it does not, the breakdown is wrong, and that then goes into the log.
 */
public final class TypingBenchmark extends IdeScreen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/TypingBench");

    /** Long enough that a p95 means something. */
    private static final long TYPING_NANOS = 45_000_000_000L;

    /** For everything that only runs along for the sake of completeness. */
    private static final long SHORT_NANOS = 8_000_000_000L;

    /** Calm after a switch, before counting begins. */
    private static final int SETTLE_FRAMES = 30;

    private static boolean finished;

    private enum Step {
        SETTLE("Aufbau", SHORT_NANOS),
        TYPING_GLASS("Tippen, Glas an", TYPING_NANOS),
        TYPING_NO_GLASS("Tippen, Glas aus", TYPING_NANOS),
        TYPING_OPAQUE("Tippen, Glas aus und Editor deckend", TYPING_NANOS),
        SCROLL("Schnell rollen", SHORT_NANOS),
        TYPING_FAST("Schnelles Tippen, Glas an", TYPING_NANOS),
        REOPEN("Zweites Öffnen", SHORT_NANOS),
        DONE("fertig", 0);

        final String label;
        final long nanos;

        Step(String label, long nanos) {
            this.label = label;
            this.nanos = nanos;
        }
    }

    /**
     * What is typed.
     *
     * <p>Without a line break the same line grows over three quarters of a minute
     * to several thousand characters — and Monaco's handling of monster lines is
     * a cost factor of its own that has no business here. That is why every
     * forty characters a real enter key comes.
     */
    private static final String TIPPTEXT =
            "    let vorrat = storage.count(\"eisenbarren\")";

    private Step step = Step.SETTLE;
    private long stepStartedNanos;
    private long paintsAtStart;
    private int typedIndex;
    private int frameInStep;
    private int settleFrames;

    private final DurationSamples frameTimes = new DurationSamples();
    private long lastFrameNanos;

    private TypingBenchmark(String url) {
        super(url);
    }

    public static boolean finished() {
        return finished;
    }

    public static boolean open(Minecraft client) {
        String url = prepare(client);
        if (url == null) {
            return false;
        }
        client.setScreen(new TypingBenchmark(url));
        return true;
    }

    @Override
    protected void init() {
        super.init();
        if (stepStartedNanos == 0) {
            // A timestamp as an anchor, so that a run can be told apart in the
            // log from all previous ones.
            LOG.info("=== LAUF {} — Fenster {}x{}, Browser {}x{} ===",
                    System.currentTimeMillis() / 1000,
                    minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(),
                    hasSession() ? session().width() : 0,
                    hasSession() ? session().height() : 0);
        }
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
        if (step == Step.DONE || !hasSession() || pageLoading()) {
            return;
        }
        if (stepStartedNanos == 0) {
            stepStartedNanos = System.nanoTime();
            LOG.info("Browser {}x{}, Fenster {}x{}",
                    session().width(), session().height(),
                    minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
            return;
        }
        if (settleFrames > 0) {
            settle();
            return;
        }
        frameInStep++;
        act();
        if (System.nanoTime() - stepStartedNanos < step.nanos) {
            return;
        }
        report();
        step = Step.values()[step.ordinal() + 1];
        frameInStep = 0;
        begin();
        if (step == Step.DONE) {
            LOG.info("Tippmessung fertig.");
            finished = true;
            onClose();
        }
    }

    private void settle() {
        settleFrames--;
        if (settleFrames == 0) {
            stepStartedNanos = System.nanoTime();
            paintsAtStart = paints();
            texture().resetStats();
            session().resetLatency();
            frameTimes.reset();
        }
    }

    private void act() {
        switch (step) {
            case TYPING_GLASS, TYPING_NO_GLASS, TYPING_OPAQUE -> {
                if (frameInStep % 8 == 0) {
                    typeNext();
                }
            }
            case TYPING_FAST -> {
                // <b>Twenty characters per second, not a hundred.</b> On the
                // first attempt a character came on every frame — against
                // one hundred fifteen frames per second, thus faster than
                // a human ever types. Chromium at full resolution could
                // no longer keep up: ten frames in forty-five seconds, a
                // backlog of fifteen seconds, and the two following
                // sections measured only the clearing of it.
                //
                // Twenty per second is fast human typing and
                // thus the number that means something.
                if (frameInStep % 6 == 0) {
                    typeNext();
                }
            }
            case SCROLL -> mouseScrolled(width * 0.5, height * 0.45, 0.0, -3.0);
            default -> {
            }
        }
    }

    private void typeNext() {
        int at = typedIndex++ % (TIPPTEXT.length() + 1);
        if (at == TIPPTEXT.length()) {
            // A real enter key, not a character. It goes over the
            // key path and keeps the lines short.
            sendKeyCombination(GLFW.GLFW_KEY_ENTER, 0);
        } else {
            charTyped(TIPPTEXT.charAt(at), 0);
        }
    }

    private void begin() {
        switch (step) {
            // <b>A fresh document before every typing section.</b> Otherwise
            // each phase writes into what the previous ones left behind —
            // after three sections that is two and a quarter minutes of text, and
            // the measurement shows the growth of the document instead of the
            // difference that is the point. Exactly this is how the first
            // glass comparison came about, and it was worthless because of it.
            case TYPING_GLASS, TYPING_FAST -> {
                runScript("window.fnIde && fnIde.glas(true)");
                runScript("window.fnIde && fnIde.thema('fn-glas')");
                runScript("window.fnIde && fnIde.frisch()");
                click(0.45, 0.30);
            }
            case TYPING_NO_GLASS -> {
                runScript("window.fnIde && fnIde.glas(false)");
                runScript("window.fnIde && fnIde.frisch()");
                click(0.45, 0.30);
            }
            case TYPING_OPAQUE -> {
                runScript("window.fnIde && fnIde.thema('fn-deckend')");
                runScript("window.fnIde && fnIde.frisch()");
                click(0.45, 0.30);
            }
            case SCROLL -> {
                runScript("window.fnIde && fnIde.glas(true)");
                runScript("window.fnIde && fnIde.thema('fn-glas')");
                runScript("window.fnIde && fnIde.waehle('grossanlage.fn')");
                runScript("window.fnIde && fnIde.springe(1)");
            }
            case REOPEN -> {
                // The screen stays, but the page is reloaded: this
                // is the second open, as a player experiences it who
                // closes the interface and immediately opens it again. CEF is
                // then running, and the files lie unpacked and ready.
                LOG.info("Zweites Öffnen beginnt — Zeitmessung bis „IDE bereit"
                        + " im Protokoll der Seite");
                runScript("location.reload()");
            }
            default -> {
            }
        }
        stepStartedNanos = System.nanoTime();
        settleFrames = SETTLE_FRAMES;
    }

    private void click(double acrossFraction, double downFraction) {
        double x = width * acrossFraction;
        double y = height * downFraction;
        mouseMoved(x, y);
        mouseClicked(x, y, 0);
        mouseReleased(x, y, 0);
    }

    private void report() {
        if (step == Step.SETTLE) {
            return;
        }
        double seconds = (System.nanoTime() - stepStartedNanos) / 1_000_000_000.0;
        long paints = paints() - paintsAtStart;
        var texture = texture();
        var toPaint = session().toPaint();
        var toScreen = session().toScreen();
        var total = session().totalLatency();
        double fullFrame = (double) session().width() * session().height() * 4;

        LOG.info("=== {} — {}x{} ===", step.label, session().width(), session().height());
        LOG.info(String.format(Locale.GERMANY,
                "%s: %d Bilder (%.1f/s), %.1f KB je Bild (%.1f %% eines Vollbilds), "
                        + "%.2f MB/s, %d von %d als Vollbild",
                step.label, paints, paints / seconds,
                texture.bytesPerUpload() / 1024.0,
                texture.bytesPerUpload() / fullFrame * 100.0,
                texture.uploadedBytes() / seconds / (1024.0 * 1024.0),
                texture.fullUploads(), texture.uploads()));

        // The breakdown. B comes from the upload measurement, which exists anyway.
        double a = toPaint.percentile(50) / 1000.0;
        double b = texture.uploadTimes().percentile(50) / 1000.0;
        double c = toScreen.percentile(50) / 1000.0;
        double gesamt = total.percentile(50) / 1000.0;
        LOG.info(String.format(Locale.GERMANY,
                "%s: A bis Bild %.1f ms | B Upload %.1f ms | C bis Zeichnen %.1f ms "
                        + "| Summe %.1f ms | gemessen gesamt %.1f ms",
                step.label, a, b, c, a + b + c, gesamt));
        // If the sum misses the total stretch, the breakdown is broken
        // — and that should stand out, not land in the report.
        if (gesamt > 0 && Math.abs(a + b + c - gesamt) > gesamt * 0.35) {
            LOG.warn("{}: Die Zerlegung geht nicht auf — Summe {} ms gegen {} ms gemessen. "
                            + "Die Einzelwerte sind dann mit Vorsicht zu lesen.",
                    step.label, String.format(Locale.GERMANY, "%.1f", a + b + c),
                    String.format(Locale.GERMANY, "%.1f", gesamt));
        }
        LOG.info(String.format(Locale.GERMANY,
                "%s: Eingabe bis Bild p50 %.1f ms, p95 %.1f ms, max %.1f ms (%d Messungen)",
                step.label, total.percentile(50) / 1000.0, total.percentile(95) / 1000.0,
                total.slowest() / 1000.0, total.count()));
        LOG.info(String.format(Locale.GERMANY,
                "%s: davon A p95 %.1f ms | B p95 %.1f ms, max %.1f ms | C p95 %.1f ms",
                step.label, toPaint.percentile(95) / 1000.0,
                texture.uploadTimes().percentile(95) / 1000.0,
                texture.uploadTimes().slowest() / 1000.0,
                toScreen.percentile(95) / 1000.0));
        LOG.info(String.format(Locale.GERMANY,
                "%s: Minecrafts Bildzeit p50 %.1f ms, p95 %.1f ms, max %.1f ms (%.0f Bilder/s)",
                step.label, frameTimes.percentile(50) / 1000.0,
                frameTimes.percentile(95) / 1000.0, frameTimes.slowest() / 1000.0,
                frameTimes.percentile(50) > 0 ? 1_000_000.0 / frameTimes.percentile(50) : 0.0));
        saveScreenshot(step.label);
    }

    private void saveScreenshot(String label) {
        try {
            StringBuilder name = new StringBuilder("fntyp-"
                    + session().width() + "x" + session().height() + "-");
            for (char each : label.toCharArray()) {
                name.append(Character.isLetterOrDigit(each) ? each : '_');
            }
            name.append(".png");
            net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, name.toString(),
                    minecraft.getMainRenderTarget(), message -> { });
        } catch (Throwable broken) {
            LOG.warn("Bild ließ sich nicht ablegen", broken);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
