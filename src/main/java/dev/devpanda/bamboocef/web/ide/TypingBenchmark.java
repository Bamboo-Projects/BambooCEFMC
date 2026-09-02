package dev.devpanda.bamboocef.web.ide;

import dev.devpanda.bamboocef.web.measure.DurationSamples;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Beantwortet die eine Frage, die nach Schritt F offen blieb: Wie fühlt sich
 * Tippen bei voller Auflösung an?
 *
 * <p><b>Gemessen, nicht hochgerechnet.</b> Die Fenstergröße kommt beim Start
 * über {@code --width} und {@code --height}, und der Browser bekommt genau so
 * viele Bildpunkte. Was hier steht, gilt für die Auflösung, in der das Fenster
 * gerade läuft — sie steht in jeder Zeile mit dabei.
 *
 * <p><b>Lange Abschnitte statt vieler.</b> In Schritt F liefen die
 * Tippszenarien vier Sekunden und lieferten vierzig Messungen; ein p95 daraus
 * ist eine Behauptung. Hier tippt jeder Abschnitt eine Dreiviertelminute, und
 * die Verteilung trägt.
 *
 * <p><b>Die Verzögerung wird zerlegt.</b> Zwischen Tastendruck und Bild liegen
 * drei Strecken, und nur eine davon gehört uns:
 *
 * <pre>
 *   A   Eingabe → Chromium liefert ein Bild     (Chromium und Monaco)
 *   B   das Bild in die Textur schieben          (unser Weg)
 *   C   bis Minecraft das nächste Mal zeichnet   (Wartezeit)
 * </pre>
 *
 * <p>Ob wir am Upload hängen oder an Chromium, entscheidet sich an diesen drei
 * Zahlen — und daran, ob ihre Summe die gemessene Gesamtstrecke trifft. Tut
 * sie das nicht, ist die Zerlegung falsch, und das steht dann im Protokoll.
 */
public final class TypingBenchmark extends IdeScreen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/TypingBench");

    /** Lange genug, dass ein p95 etwas bedeutet. */
    private static final long TYPING_NANOS = 45_000_000_000L;

    /** Für alles, was nur der Vollständigkeit halber mitläuft. */
    private static final long SHORT_NANOS = 8_000_000_000L;

    /** Ruhe nach einem Wechsel, bevor gezählt wird. */
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
     * Was getippt wird.
     *
     * <p>Ohne Zeilenumbruch wächst dieselbe Zeile über eine Dreiviertelminute
     * auf mehrere tausend Zeichen — und Monacos Umgang mit Monsterzeilen ist
     * ein eigener Kostenfaktor, der hier nichts zu suchen hat. Deshalb kommt
     * alle vierzig Zeichen eine echte Eingabetaste.
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
            // Ein Zeitstempel als Anker, damit sich ein Lauf im Protokoll von
            // allen vorigen unterscheiden lässt.
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
                // <b>Zwanzig Zeichen je Sekunde, nicht hundert.</b> Beim
                // ersten Versuch kam bei jedem Bild ein Zeichen — gegen
                // hundertfünfzehn Bilder in der Sekunde also schneller, als
                // ein Mensch je tippt. Chromium kam bei voller Auflösung nicht
                // mehr hinterher: zehn Bilder in fünfundvierzig Sekunden, ein
                // Rückstau von fünfzehn Sekunden, und die beiden folgenden
                // Abschnitte maßen nur noch dessen Abarbeitung.
                //
                // Zwanzig je Sekunde ist schnelles menschliches Tippen und
                // damit die Zahl, die etwas bedeutet.
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
            // Eine echte Eingabetaste, kein Zeichen. Sie geht über den
            // Tastenweg und hält die Zeilen kurz.
            sendKeyCombination(GLFW.GLFW_KEY_ENTER, 0);
        } else {
            charTyped(TIPPTEXT.charAt(at), 0);
        }
    }

    private void begin() {
        switch (step) {
            // <b>Vor jedem Tippabschnitt ein frisches Dokument.</b> Sonst
            // schreibt jede Phase in das, was die vorigen hinterlassen haben —
            // nach drei Abschnitten sind das zweieinviertel Minuten Text, und
            // die Messung zeigt das Wachsen des Dokuments statt des
            // Unterschieds, um den es geht. Genau so ist der erste
            // Glas-Vergleich zustande gekommen, und er war damit wertlos.
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
                // Der Bildschirm bleibt, aber die Seite wird neu geladen: Das
                // ist das zweite Öffnen, wie es ein Spieler erlebt, der die
                // Oberfläche schließt und gleich wieder aufmacht. CEF läuft
                // dann, und die Dateien liegen ausgepackt bereit.
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

        // Die Zerlegung. B kommt aus der Uploadmessung, die es ohnehin gibt.
        double a = toPaint.percentile(50) / 1000.0;
        double b = texture.uploadTimes().percentile(50) / 1000.0;
        double c = toScreen.percentile(50) / 1000.0;
        double gesamt = total.percentile(50) / 1000.0;
        LOG.info(String.format(Locale.GERMANY,
                "%s: A bis Bild %.1f ms | B Upload %.1f ms | C bis Zeichnen %.1f ms "
                        + "| Summe %.1f ms | gemessen gesamt %.1f ms",
                step.label, a, b, c, a + b + c, gesamt));
        // Wenn die Summe die Gesamtstrecke verfehlt, ist die Zerlegung kaputt
        // — und das soll auffallen, nicht im Bericht landen.
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
