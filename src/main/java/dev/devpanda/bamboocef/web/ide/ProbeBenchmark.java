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
 * Der Messablauf für Takt und Tipplatenz.
 *
 * <p><b>Wozu er jetzt dient.</b> Alle bisherigen Zahlen dieses Projekts sind
 * über eine Fernsitzung entstanden. Eine Fernsitzung kann in den Bildweg
 * hineinregieren — Fensterverwaltung, Bildsynchronisation, Zeitplanung auf
 * der Grafikkarte hängen daran. Dieser Ablauf prüft lokal nach, ob die
 * Schlüsselzahlen bleiben, und wiederholt dabei ausdrücklich <i>nicht</i> die
 * ganzen alten Reihen. Zwei Stufen genügen.
 *
 * <p><b>Die erste Stufe misst den Takt, nicht die Dauer.</b> Eine Seite, die
 * in jedem Bild ein kleines Feld umfärbt, zwingt Chromium zu liefern, sooft
 * es kann. Der Abstand zweier Bilder ist dann der Takt selbst und sonst
 * nichts — die Zahl, die nach Schritt I den ganzen Befund trägt und die nach
 * dem Patch auf sechzig Bilder je Sekunde springen muss.
 *
 * <p><b>Die zweite Stufe misst, was das Produkt werden soll:</b> Monaco
 * vollständig, mit dem vorgeblurten Hintergrund statt echtem Weichzeichner.
 * Beides in einem Lauf, damit dieselbe Sitzung beide Zahlen liefert.
 */
public final class ProbeBenchmark extends IdeScreen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/Probe");

    private static final long TYPING_NANOS = 45_000_000_000L;
    /**
     * Wie lange eine Speicherstufe steht.
     *
     * <p>Lang genug, dass die Messung von außen sie trifft: Der Aufruf, der
     * den Speicher abfragt, braucht selbst gut eine Sekunde. Beim ersten
     * Versuch standen alle fünf Stufen bei denselben achthundert Megabyte —
     * gemessen wurde jedes Mal derselbe Zustand.
     */
    private static final long RAM_NANOS = 20_000_000_000L;
    private static final int SETTLE_FRAMES = 40;

    private static boolean finished;

    /**
     * Was der Reihe nach gemessen wird.
     *
     * <p>Die Speicherstufen zuerst: Ist Monaco einmal geladen, gibt es den
     * leeren Zustand in dieser Sitzung nicht zurück.
     */
    private enum Step {
        // Der lokale Kontrolllauf, nachdem alle bisherigen Messungen über eine
        // Fernsitzung liefen. Zwei Stufen, mehr braucht es nicht: einmal der
        // nackte Takt, einmal die Konfiguration, die das Produkt werden soll.
        // Alles dazwischen ist beantwortet und wird nicht wiederholt.
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

    /** Öffnet die Messung — sie beginnt mit einer leeren Seite. */
    public static boolean open(Minecraft client) {
        String ide = prepare(client);
        if (ide == null) {
            return false;
        }
        // Die Messfläche liegt neben der Oberfläche und wird genauso ausgepackt.
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
                // frisch() statt leeren(): Es setzt auch den Inhalt des
                // Editors zurück. Ohne das tippen aufeinanderfolgende Stufen
                // in dasselbe, immer länger werdende Dokument — in Schritt H
                // hat genau das eine Stufe um acht Millisekunden langsamer
                // aussehen lassen, als sie war.
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
            // <b>Nicht sofort schließen.</b> Der Bericht der Seite geht über
            // die Konsole und braucht eine Handvoll Bilder, bis er ankommt.
            // Beim letzten Lauf hat das Schließen ihn überholt, und für die
            // letzte Stufe fehlte die Sicht der Seite ganz.
            closingFrames = 30;
        }
    }

    /** Wie viele Bilder noch zu warten sind, bis geschlossen werden darf. */
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
        // Die Seite neu laden statt einen zweiten Browser aufzumachen: Zwei
        // Browser teilten sich die Bildrate, und jede Zahl wäre halb so gut.
        runScript("location.href='" + pageBase + "?v=" + step.variant + "'");
        stepStartedNanos = System.nanoTime();
        settleFrames = SETTLE_FRAMES;
    }

    /**
     * Ein Zeichen auf dem vollen Weg, wie es von einer Tastatur käme.
     *
     * <p><b>Nur ein Zeichen zu schicken, reicht nicht.</b> Das erzeugt in
     * Chromium ein {@code input}-Ereignis, aber kein {@code keydown} — und die
     * Seite misst an {@code keydown}. Beim ersten Versuch stand deshalb in
     * jeder Zeile {@code n=0}: Java tippte, die Seite sah nichts.
     *
     * <p>Echte Eingabe kommt in drei Teilen: Taste herunter, Zeichen, Taste
     * herauf. Genau so wird hier getippt.
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
        // Die Form der Verteilung, nicht nur ihre Lage. Ein Takt von dreißig
        // Bildern je Sekunde trägt seine Unterschrift sichtbar: Häufungen bei
        // 33 ms und bei 67 ms, dazwischen fast nichts. Verteilte Rechenzeit
        // sähe dagegen aus wie ein einzelner Hügel.
        LOG.info("{}: Verteilung von A (Fächer zu 4 ms)\n{}",
                step.label, toPaint.histogram(4.0, 25));
        // Und jetzt die Sicht der Seite auf dieselbe Strecke.
        runScript("window.fnProbe && fnProbe.bericht('" + step.label + "')");
    }

    /**
     * Der Takt: wie weit zwei Bilder aus Chromium auseinanderliegen.
     *
     * <p><b>Die Zahl, auf die es bei diesem Lauf ankommt.</b> Nicht wie lange
     * etwas dauert, sondern wie oft überhaupt jemand drankommt. Bei einer
     * Seite, die in jedem Bild etwas ändert, ist der Abstand der Takt selbst
     * und sonst nichts.
     *
     * <p>Ein Vorbehalt gehört ins Protokoll: Das Bild kommt im Render-Thread
     * an, die Abstände liegen also auf Minecrafts eigenem Raster von rund
     * achteinhalb Millisekunden. Dreiunddreißig von sechzehn zu
     * unterscheiden reicht das mühelos; auf die Nachkommastelle ist es nicht
     * zu lesen.
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
