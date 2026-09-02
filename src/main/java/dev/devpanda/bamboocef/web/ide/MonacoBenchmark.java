package dev.devpanda.bamboocef.web.ide;

import dev.devpanda.bamboocef.web.measure.DurationSamples;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Fährt einen Editor durch das, was ein Mensch mit ihm tut — und misst dabei.
 *
 * <p><b>Die Frage ist nicht mehr, ob der Bildweg trägt.</b> Das ist gemessen.
 * Die Frage ist, ob <i>dieser</i> Arbeitsanfall ihn überfordert: Ein Editor mit
 * Zeilennummern, Übersicht, Faltung und Vervollständigung malt viel mehr als
 * eine Prüfseite mit vier Kästen.
 *
 * <p><b>Alles geht durch den Bildschirm, nichts daran vorbei.</b> Tasten,
 * Klicks und Rad nehmen denselben Weg wie bei einem Menschen — samt
 * Umrechnung, Fokusprüfung und Klickzählung. Was die Oberfläche selbst
 * einstellen muss (Übersicht an, Thema wechseln, Datei wählen), geht über
 * einzelne Aufrufe in die Seite: dieselbe Art wie der Hintergrundwechsel, ohne
 * Rückrichtung.
 */
public final class MonacoBenchmark extends IdeScreen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/MonacoBench");

    private static final long SECTION_NANOS = 4_000_000_000L;
    private static final long SETTLE_NANOS = 6_000_000_000L;

    private static boolean finished;

    /** Was der Reihe nach durchgespielt wird. */
    private enum Step {
        SETTLE("Aufbau"),
        IDLE("Ruhe — nichts tun"),
        CURSOR("Schreibmarke blinkt"),
        TYPING("Normales Tippen"),
        TYPING_NO_GLASS("Normales Tippen, ohne Glas"),
        TYPING_OPAQUE("Normales Tippen, deckender Editor"),
        FAST_TYPING("Schnelles Tippen"),
        UNICODE("Umlaute, ß, €, Emoji"),
        HOVER("Maus über Code"),
        SUGGEST("Vervollständigung offen"),
        FIND("Suchfeld offen"),
        MULTICURSOR("Zehn Schreibmarken"),
        SELECTION("Große Auswahl"),
        SCROLL_SLOW("Langsam rollen"),
        SCROLL_FAST("Schnell rollen, 10.000 Zeilen"),
        SCROLL_NO_MINIMAP("Schnell rollen, ohne Übersicht"),
        SCROLL_NO_GLASS("Schnell rollen, ohne Glas"),
        MINIMAP_OFF("Übersicht aus"),
        MINIMAP_ON("Übersicht an"),
        STICKY_OFF("Haftende Kopfzeile aus"),
        STICKY_ON("Haftende Kopfzeile an"),
        THEME_LIGHT("Thema hell"),
        THEME_DARK("Thema dunkel"),
        THEME_GLASS("Thema Glas"),
        TABS("Zwischen acht Dateien wechseln"),
        KEYS("Tastenkürzel"),
        ZOOM("Schriftgröße"),
        DONE("fertig");

        final String label;

        Step(String label) {
            this.label = label;
        }
    }

    private static final String TIPPTEXT =
            "    let vorrat = storage.count(\"eisenbarren\")\n";

    private Step step = Step.SETTLE;
    private long stepStartedNanos;
    private long paintsAtStart;
    private int typedIndex;
    private int frameInStep;

    private final DurationSamples frameTimes = new DurationSamples();
    private long lastFrameNanos;

    private MonacoBenchmark(String url) {
        super(url);
    }

    public static boolean finished() {
        return finished;
    }

    /** Öffnet die Messung. Packt bei Bedarf aus, wie die Oberfläche selbst. */
    public static boolean open(Minecraft client) {
        String url = prepare(client);
        if (url == null) {
            return false;
        }
        client.setScreen(new MonacoBenchmark(url));
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
        if (step == Step.DONE || !hasSession() || pageLoading()) {
            return;
        }
        if (stepStartedNanos == 0) {
            stepStartedNanos = System.nanoTime();
            return;
        }
        if (settleFrames > 0) {
            settle();
            return;
        }
        frameInStep++;
        act();

        long limit = step == Step.SETTLE ? SETTLE_NANOS : SECTION_NANOS;
        if (System.nanoTime() - stepStartedNanos < limit) {
            return;
        }
        report();
        step = Step.values()[step.ordinal() + 1];
        frameInStep = 0;
        begin();
        if (step == Step.DONE) {
            LOG.info("Monaco-Messung fertig.");
            finished = true;
            onClose();
        }
    }

    /** Was in diesem Abschnitt bei jedem Bild geschieht. */
    private void act() {
        switch (step) {
            case TYPING -> {
                // Etwa acht Zeichen je Sekunde — zügiges, aber menschliches
                // Tippen.
                if (frameInStep % 8 == 0) {
                    type(TIPPTEXT.charAt(typedIndex++ % TIPPTEXT.length()));
                }
            }
            case TYPING_NO_GLASS, TYPING_OPAQUE -> {
                if (frameInStep % 8 == 0) {
                    type(TIPPTEXT.charAt(typedIndex++ % TIPPTEXT.length()));
                }
            }
            case FAST_TYPING -> {
                // Jedes Bild ein Zeichen: schneller, als jemand tippen kann.
                type(TIPPTEXT.charAt(typedIndex++ % TIPPTEXT.length()));
            }
            case HOVER -> {
                // Über den Text wandern, nicht über die Ränder.
                double angle = frameInStep * 0.07;
                mouseMoved(width * (0.45 + 0.22 * Math.cos(angle)),
                        height * (0.45 + 0.18 * Math.sin(angle)));
            }
            case SCROLL_SLOW -> {
                if (frameInStep % 6 == 0) {
                    mouseScrolled(width * 0.5, height * 0.45, 0.0, -1.0);
                }
            }
            case SCROLL_FAST, SCROLL_NO_MINIMAP, SCROLL_NO_GLASS ->
                    mouseScrolled(width * 0.5, height * 0.45, 0.0, -3.0);
            case SELECTION -> {
                if (frameInStep % 3 == 0) {
                    runScript("window.fnIde && fnIde.markiere(20, "
                            + (25 + frameInStep) + ")");
                }
            }
            default -> {
            }
        }
    }

    /** Was zu Beginn eines Abschnitts einmal geschieht. */
    private void begin() {
        switch (step) {
            case IDLE -> click(0.45, 0.30);
            case TYPING, FAST_TYPING -> {
                runScript("window.fnIde && fnIde.glas(true)");
                runScript("window.fnIde && fnIde.waehle('main.fn')");
                click(0.45, 0.30);
            }
            case TYPING_NO_GLASS -> {
                // Dasselbe Tippen, nur ohne Weichzeichner. Der Unterschied ist
                // der Preis des Glases — und der ist sonst nicht zu trennen
                // von dem, was Monaco selbst malt.
                runScript("window.fnIde && fnIde.glas(false)");
                click(0.45, 0.30);
            }
            case TYPING_OPAQUE -> {
                // Glas bleibt aus, aber jetzt ist auch der Editor selbst
                // deckend. Bleibt die Fläche trotzdem bei neunzig Prozent,
                // liegt es an Monaco; fällt sie, an der Durchsichtigkeit.
                runScript("window.fnIde && fnIde.thema('fn-deckend')");
                click(0.45, 0.30);
            }
            case UNICODE -> {
                runScript("window.fnIde && fnIde.thema('fn-glas')");
                runScript("window.fnIde && fnIde.glas(true)");
                click(0.45, 0.30);
                // Alles bis U+FFFF geht als ein Zeichen. Das Emoji ist ein
                // Ersatzpaar und kommt als zwei — genau das soll sich zeigen.
                for (char each : "äöüÄÖÜß €{}[]<> ".toCharArray()) {
                    type(each);
                }
                for (char each : Character.toChars(0x1F600)) {
                    type(each);
                }
            }
            case SUGGEST -> {
                click(0.45, 0.30);
                runScript("window.fnIde && fnIde.vorschlag()");
            }
            case FIND -> {
                runScript("window.fnIde && fnIde.leerraum()");
                sendKeyCombination(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL);
                for (char each : "storage".toCharArray()) {
                    type(each);
                }
            }
            case MULTICURSOR -> {
                sendKeyCombination(GLFW.GLFW_KEY_ESCAPE, 0);
                runScript("window.fnIde && fnIde.cursor(10)");
            }
            case SELECTION -> runScript("window.fnIde && fnIde.markiere(20, 60)");
            case SCROLL_SLOW, SCROLL_FAST -> {
                runScript("window.fnIde && fnIde.glas(true)");
                runScript("window.fnIde && fnIde.minimap(true)");
                runScript("window.fnIde && fnIde.waehle('grossanlage.fn')");
                runScript("window.fnIde && fnIde.springe(1)");
            }
            case SCROLL_NO_MINIMAP -> {
                runScript("window.fnIde && fnIde.minimap(false)");
                runScript("window.fnIde && fnIde.springe(1)");
            }
            case SCROLL_NO_GLASS -> {
                runScript("window.fnIde && fnIde.minimap(true)");
                runScript("window.fnIde && fnIde.glas(false)");
                runScript("window.fnIde && fnIde.springe(1)");
            }
            // Diese beiden messen die Ruhe danach, nicht das Umschalten
            // selbst: Ein Wechsel malt einmal alles neu, und diese eine Spitze
            // hätte die ganze Messung beherrscht. Beim ersten Versuch stand
            // deshalb „Übersicht aus" mit dem Fünffachen von „Übersicht an" da.
            case MINIMAP_OFF -> {
                runScript("window.fnIde && fnIde.glas(true)");
                runScript("window.fnIde && fnIde.minimap(false)");
            }
            case MINIMAP_ON -> runScript("window.fnIde && fnIde.minimap(true)");
            case STICKY_OFF -> runScript("window.fnIde && fnIde.sticky(false)");
            case STICKY_ON -> runScript("window.fnIde && fnIde.sticky(true)");
            case THEME_LIGHT -> runScript("window.fnIde && fnIde.thema('vs')");
            case THEME_DARK -> runScript("window.fnIde && fnIde.thema('vs-dark')");
            case THEME_GLASS -> runScript("window.fnIde && fnIde.thema('fn-glas')");
            case TABS -> runScript("window.fnIde && fnIde.waehle('main.fn')");
            case KEYS -> runScript("window.fnIde && fnIde.waehle('main.fn')");
            case ZOOM -> runScript("window.fnIde && fnIde.schrift(20)");
            default -> {
            }
        }
        stepStartedNanos = System.nanoTime();
        settleFrames = SETTLE_FRAMES_PER_STEP;
    }

    /**
     * Wie viele Bilder nach dem Umschalten verworfen werden.
     *
     * <p>Fast jeder Abschnitt beginnt mit einer Einstellung, und fast jede
     * Einstellung lässt Monaco einmal alles neu malen. Wer sofort zu zählen
     * anfängt, misst diesen einen Wechsel statt des Zustands danach.
     */
    private static final int SETTLE_FRAMES_PER_STEP = 20;

    private int settleFrames;

    /** Setzt die Zähler zurück, sobald sich der Abschnitt beruhigt hat. */
    private void settle() {
        if (settleFrames <= 0) {
            return;
        }
        settleFrames--;
        if (settleFrames == 0) {
            stepStartedNanos = System.nanoTime();
            paintsAtStart = paints();
            if (hasSession()) {
                texture().resetStats();
                inputLatency().reset();
            }
            frameTimes.reset();
        }
    }

    /** Die Abschnitte, die aus einer Reihe von Einzelschritten bestehen. */
    private void actOnce() {
        switch (step) {
            case TABS -> {
                String[] dateien = {"sortierung.fn", "ofen.fn", "lager.fn", "netz.fn",
                        "anzeige.fn", "rezepte.fn", "grossanlage.fn", "main.fn"};
                if (frameInStep % 20 == 0 && frameInStep / 20 < dateien.length) {
                    runScript("window.fnIde && fnIde.waehle('"
                            + dateien[frameInStep / 20] + "')");
                }
            }
            case KEYS -> {
                // Reale Editor-Kürzel, eines je zehn Bilder. Was Minecraft
                // abfinge, käme hier nicht an — und Monaco täte nichts.
                int[][] kuerzel = {
                        {GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL},
                        {GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL},
                        {GLFW.GLFW_KEY_END, 0},
                        {GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL},
                        {GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL},
                        {GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT},
                        {GLFW.GLFW_KEY_D, GLFW.GLFW_MOD_CONTROL},
                        {GLFW.GLFW_KEY_HOME, 0},
                        {GLFW.GLFW_KEY_PAGE_DOWN, 0},
                        {GLFW.GLFW_KEY_G, GLFW.GLFW_MOD_CONTROL},
                        {GLFW.GLFW_KEY_ESCAPE, 0},
                        {GLFW.GLFW_KEY_DOWN, GLFW.GLFW_MOD_ALT},
                        {GLFW.GLFW_KEY_UP, GLFW.GLFW_MOD_ALT},
                        {GLFW.GLFW_KEY_F2, 0},
                        {GLFW.GLFW_KEY_ESCAPE, 0}
                };
                int index = frameInStep / 10;
                if (frameInStep % 10 == 0 && index < kuerzel.length) {
                    sendKeyCombination(kuerzel[index][0], kuerzel[index][1]);
                }
            }
            default -> {
            }
        }
    }

    private void type(char character) {
        charTyped(character, 0);
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
            saveScreenshot(step.label);
            return;
        }
        double seconds = (System.nanoTime() - stepStartedNanos) / 1_000_000_000.0;
        long paints = paints() - paintsAtStart;
        var texture = texture();
        double fullFrame = (double) session().width() * session().height() * 4;

        LOG.info("=== {} ===", step.label);
        LOG.info(String.format(Locale.GERMANY,
                "%s: %d Bilder (%.1f/s), %d Uploads, davon %d Vollbild, %d Ausschnitte",
                step.label, paints, paints / seconds,
                texture.uploads(), texture.fullUploads(), texture.regions()));
        LOG.info(String.format(Locale.GERMANY,
                "%s: %.1f KB je Bild (%.2f %% eines Vollbilds), %.2f MB/s, "
                        + "Uploadzeit p50 %.0f µs, p95 %.0f µs, max %.0f µs",
                step.label, texture.bytesPerUpload() / 1024.0,
                texture.bytesPerUpload() / fullFrame * 100.0,
                texture.uploadedBytes() / seconds / (1024.0 * 1024.0),
                texture.uploadTimes().percentile(50),
                texture.uploadTimes().percentile(95),
                texture.uploadTimes().slowest()));
        var latency = inputLatency();
        if (latency.count() > 0) {
            LOG.info(String.format(Locale.GERMANY,
                    "%s: Eingabe bis Bild p50 %.1f ms, p95 %.1f ms, max %.1f ms (%d Messungen)",
                    step.label, latency.percentile(50) / 1000.0,
                    latency.percentile(95) / 1000.0, latency.slowest() / 1000.0,
                    latency.count()));
        }
        LOG.info(String.format(Locale.GERMANY,
                "%s: Minecrafts Bildzeit p50 %.1f ms, p95 %.1f ms (%.0f Bilder/s)",
                step.label, frameTimes.percentile(50) / 1000.0,
                frameTimes.percentile(95) / 1000.0,
                frameTimes.percentile(50) > 0 ? 1_000_000.0 / frameTimes.percentile(50) : 0.0));
        saveScreenshot(step.label);
    }

    private void saveScreenshot(String label) {
        try {
            StringBuilder name = new StringBuilder("fnide-");
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
    protected void beforeDrawing() {
        super.beforeDrawing();
        actOnce();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
