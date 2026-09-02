package dev.devpanda.bamboocef.web.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.devpanda.bamboocef.web.BrowserVisibility;
import dev.devpanda.bamboocef.web.WebRuntimeStatus;
import dev.devpanda.bamboocef.web.WebSupport;
import dev.devpanda.bamboocef.web.capture.FrameStore;
import dev.devpanda.bamboocef.web.runtime.BrowserSession;
import dev.devpanda.bamboocef.web.runtime.FrameSchemes;
import dev.devpanda.bamboocef.web.view.BrowserCompositor;
import dev.devpanda.bamboocef.web.view.BrowserView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Beweist, dass Chromium wirklich Minecrafts Bild als Hintergrund filtert.
 *
 * <p><b>Die Frage, die dieser Nachweis beantwortet.</b> Ein
 * {@code backdrop-filter} über einer durchsichtigen Seite sieht aus, als
 * funktioniere er — er filtert dann nur nichts. Nach der CSS-Spezifikation
 * umfasst das, was gefiltert wird, ausschließlich Inhalt des Dokuments; was
 * <b>hinter</b> dem Browserfenster liegt, gehört nicht dazu. Genau deshalb
 * muss Minecrafts Bild ein Element im Dokument sein und nicht der Untergrund,
 * auf den wir zeichnen.
 *
 * <p><b>Zwei Stufen, und die Reihenfolge ist wichtig.</b>
 *
 * <ol>
 *   <li><b>Außerhalb der Glasflächen</b> wird gegen die bekannten
 *       Quadrantenfarben geprüft. Stimmt das nicht, ist das Bild gar nicht
 *       angekommen — oder liegt verschoben. Dann hat es keinen Sinn, das Glas
 *       zu prüfen.</li>
 *   <li><b>Innerhalb</b> muss die Farbe des Untergrunds durchscheinen. Der
 *       Unterschied ist gewaltig und lässt sich nicht verwechseln: Eine
 *       Glasfläche über Rot ergibt etwa {@code rgb(173, 7, 10)}, dieselbe
 *       Fläche über Schwarz {@code rgb(7, 7, 10)}. Wer nur behauptet, es sehe
 *       richtig aus, kann diese beiden nicht auseinanderhalten.</li>
 * </ol>
 *
 * <p><b>Der schärfste Einzelnachweis steht in der Mitte:</b> eine Glasfläche
 * über der Grenze zwischen Rot und Grün. Dort müssen <b>beide</b> Kanäle
 * deutlich über null liegen. Über Schwarz oder Transparenz ist das unmöglich,
 * gleich wie stark gefiltert wird.
 *
 * <p>Das Hintergrundbild ist hier <b>künstlich</b> und nicht aus der Welt: Vier
 * bekannte Farben lassen sich nachrechnen, ein Waldstück nicht. Ob der Weg aus
 * der echten Welt funktioniert, ist eine andere Frage und wird woanders
 * gemessen.
 */
public final class BackdropProof extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/BackdropProof");

    /** Wie weit eine unverdeckte Farbe danebenliegen darf. */
    private static final int TOLERANCE = 16;

    private static final String PAGE = """
            <!doctype html><html><head><meta charset="utf-8"><style>
              html,body{margin:0;width:100%;height:100%;overflow:hidden;
                        background:#000}
              /* Minecrafts Bild als unterste Ebene des Dokuments — und nicht
                 als Untergrund hinter dem Fenster. Nur was im Dokument steht,
                 zählt für backdrop-filter. */
              #minecraft-background{position:fixed;inset:0;width:100%;height:100%;
                                    object-fit:cover;image-rendering:auto}
              .glass{position:fixed;border:1px solid rgba(255,255,255,0.15);
                     border-radius:14px;background:rgba(20,20,30,0.35)}
              #a{left:8%;top:8%;width:20%;height:20%;
                 backdrop-filter:blur(18px)}
              #b{left:62%;top:8%;width:20%;height:20%;
                 backdrop-filter:blur(18px) saturate(140%)}
              #c{left:8%;top:62%;width:20%;height:20%;
                 backdrop-filter:blur(40px)}
              /* Ohne Filter: Die Farbe muss trotzdem durchscheinen, nur
                 ungefiltert. Trennt „Filter kaputt" von „Ebene kaputt". */
              #d{left:62%;top:62%;width:20%;height:20%}
              /* Über der Grenze zwischen Rot und Grün — der schärfste Fall. */
              #e{left:40%;top:12%;width:20%;height:16%;
                 backdrop-filter:blur(18px) saturate(140%)}
              /* Zwei, die sich überschneiden: Die obere filtert, was die
                 untere schon hinterlassen hat. */
              #f{left:34%;top:44%;width:22%;height:14%;
                 backdrop-filter:blur(20px)}
              #g{left:44%;top:50%;width:22%;height:14%;
                 backdrop-filter:blur(20px)}
            </style></head><body>
              <img id="minecraft-background" src="URL_HIER" alt="">
              <div class="glass" id="a"></div>
              <div class="glass" id="b"></div>
              <div class="glass" id="c"></div>
              <div class="glass" id="d"></div>
              <div class="glass" id="e"></div>
              <div class="glass" id="f"></div>
              <div class="glass" id="g"></div>
            <script>
              window.fnSetBackground = function (url) {
                document.getElementById('minecraft-background').src = url;
              };
            </script></body></html>
            """;

    private static boolean finished;

    private final BrowserCompositor compositor = new BrowserCompositor();
    private final FrameStore store = FrameSchemes.store();
    private BrowserSession session;
    private BrowserView view;
    private boolean checked;
    private int framesDrawn;
    private long openedNanos;
    private boolean schemeAccepted;

    /** Ab wann die Probe auf den Zwischenspeicher zu lesen ist. */
    private long cacheProbeAtNanos;
    private boolean cacheProbeDone;

    public BackdropProof() {
        super(Component.literal("Hintergrundnachweis"));
    }

    public static boolean finished() {
        return finished;
    }

    /** Öffnet den Nachweis, wenn eine Runtime da ist. */
    public static boolean openIfPossible(Minecraft client) {
        WebRuntimeStatus status = WebSupport.ensureStarted();
        if (!status.usable()) {
            LOG.info("Hintergrundnachweis übersprungen: {}", status);
            finished = true;
            return false;
        }
        client.setScreen(new BackdropProof());
        return true;
    }

    @Override
    protected void init() {
        super.init();
        view = BrowserView.fullscreen(width, height, minecraft.getWindow().getGuiScale());
        if (session != null) {
            session.resize(view.browserWidth(), view.browserHeight());
            return;
        }
        schemeAccepted = FrameSchemes.register();
        if (!schemeAccepted) {
            LOG.warn("Hintergrundnachweis: Chromium hat das Schema nicht angenommen");
            finished = true;
            onClose();
            return;
        }
        long generation = putQuadrants(view.browserWidth(), view.browserHeight());
        if (generation == 0) {
            finished = true;
            onClose();
            return;
        }
        try {
            String page = PAGE.replace("URL_HIER", FrameSchemes.urlFor(generation));
            Path file = Files.createTempFile("fn-backdrop", ".html");
            Files.writeString(file, page, StandardCharsets.UTF_8);
            file.toFile().deleteOnExit();
            // Deckend: Die Seite bringt ihren Hintergrund selbst mit. Wäre der
            // Browser durchsichtig, filterte das Glas im Ladeloch die Leere,
            // und der Nachweis müsste beides unterscheiden können.
            session = BrowserSession.open(file.toUri().toString(), false,
                    view.browserWidth(), view.browserHeight(),
                    BrowserVisibility.FOREGROUND);
            openedNanos = System.nanoTime();
        } catch (Exception broken) {
            LOG.warn("Hintergrundnachweis: Seite ließ sich nicht ablegen", broken);
            finished = true;
            onClose();
        }
    }

    /**
     * Legt ein Bild aus vier bekannten Farben ab.
     *
     * <p>Rot oben links, Grün oben rechts, Blau unten links, Weiß unten
     * rechts — dieselbe Anordnung wie beim Bildnachweis, damit eine
     * Verwechslung denselben Fingerabdruck hinterlässt.
     *
     * @return die Nummer der Aufnahme, oder null bei einem Fehlschlag
     */
    private long putQuadrants(int imageWidth, int imageHeight) {
        // Das Format ist ABGR, nicht RGBA — der Name der Methode täuscht, der
        // Name ihres Parameters sagt die Wahrheit.
        final int red = 0xFF0000FF;
        final int green = 0xFF00FF00;
        final int blue = 0xFFFF0000;
        final int white = 0xFFFFFFFF;

        try (NativeImage image = new NativeImage(imageWidth, imageHeight, false)) {
            int halfWidth = imageWidth / 2;
            int halfHeight = imageHeight / 2;
            for (int y = 0; y < imageHeight; y++) {
                for (int x = 0; x < imageWidth; x++) {
                    boolean left = x < halfWidth;
                    boolean top = y < halfHeight;
                    image.setPixelRGBA(x, y, top ? (left ? red : green) : (left ? blue : white));
                }
            }
            byte[] png = image.asByteArray();
            long generation = store.put(png, "image/png", imageWidth, imageHeight);
            LOG.info(String.format(Locale.GERMANY,
                    "Hintergrundnachweis: Prüfbild %dx%d als PNG, %.1f KB, Nummer %d",
                    imageWidth, imageHeight, png.length / 1024.0, generation));
            return generation;
        } catch (Exception broken) {
            LOG.warn("Hintergrundnachweis: Prüfbild ließ sich nicht bauen", broken);
            return 0L;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (session == null || view == null) {
            return;
        }
        graphics.fill(0, 0, width, height, 0xFF000000);
        compositor.draw(graphics, view, session.textureId(), null);

        if (session.paints() == 0) {
            if (System.nanoTime() - openedNanos > 20_000_000_000L) {
                LOG.warn("Hintergrundnachweis: nach zwanzig Sekunden kam kein Bild von {}",
                        session.currentUrl());
                finished = true;
                onClose();
            }
            return;
        }
        framesDrawn++;
        // Drei Bilder abwarten: Das Bild wird nachgeladen, und die erste
        // Zeichnung zeigt die Seite womöglich noch ohne es.
        if (framesDrawn >= 3 && !checked) {
            checked = true;
            check();
            return;
        }
        if (cacheProbeAtNanos != 0 && !cacheProbeDone
                && System.nanoTime() >= cacheProbeAtNanos) {
            checkCacheProbe();
        }
    }

    private void check() {
        int fbWidth = minecraft.getMainRenderTarget().width;
        int fbHeight = minecraft.getMainRenderTarget().height;
        ByteBuffer pixels = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());

        LOG.info("Hintergrundnachweis, Stufe 1 — liegt das Bild überhaupt da, und richtig?");
        boolean loaded = true;
        loaded &= exact(pixels, fbWidth, fbHeight, 0.03, 0.03, "frei oben links", 255, 0, 0);
        loaded &= exact(pixels, fbWidth, fbHeight, 0.97, 0.03, "frei oben rechts", 0, 255, 0);
        loaded &= exact(pixels, fbWidth, fbHeight, 0.03, 0.97, "frei unten links", 0, 0, 255);
        loaded &= exact(pixels, fbWidth, fbHeight, 0.97, 0.97, "frei unten rechts", 255, 255, 255);

        if (!loaded) {
            LOG.warn("Hintergrundnachweis: Das Bild kam nicht an oder liegt falsch. "
                    + "Das Glas zu prüfen hätte jetzt keinen Sinn.");
            finished = true;
            onClose();
            return;
        }

        LOG.info("Hintergrundnachweis, Stufe 2 — filtert das Glas den Bildinhalt?");
        boolean glass = true;
        // Über Rot: Rot muss klar durchkommen. Über Schwarz käme rgb(7,7,10).
        glass &= dominant(pixels, fbWidth, fbHeight, 0.18, 0.18,
                "Glas mit Blur über Rot", 'r');
        glass &= dominant(pixels, fbWidth, fbHeight, 0.72, 0.18,
                "Glas mit Blur und Sättigung über Grün", 'g');
        glass &= dominant(pixels, fbWidth, fbHeight, 0.18, 0.72,
                "Glas mit starkem Blur über Blau", 'b');
        glass &= bright(pixels, fbWidth, fbHeight, 0.72, 0.72,
                "Glas ohne Filter über Weiß");
        glass &= twoChannels(pixels, fbWidth, fbHeight, 0.50, 0.20,
                "Glas über der Grenze Rot/Grün");
        glass &= notBlack(pixels, fbWidth, fbHeight, 0.50, 0.55,
                "zwei überlappende Glasflächen");

        LOG.info("Hintergrundnachweis: {}", glass
                ? "Chromium filtert Minecrafts Bild — echtes Glas über echtem Hintergrund"
                : "es stimmt nicht alles, siehe oben");
        startCacheProbe();
    }

    /**
     * Stufe 3: Hält Chromium an einem Bild fest, das es schon kennt?
     *
     * <p><b>Die Messung allein beantwortet das nicht.</b> Dass die Zahl der
     * Übertragungen gleich bleibt, kann auch daran liegen, dass die Seite aus
     * anderen Gründen neu gezeichnet wird. Bewiesen ist es erst, wenn ein
     * <b>anderes Bild</b> unter derselben Adresse ankommt und man es sieht.
     *
     * <p>Deshalb: ein einfarbiges Bild ablegen, die Adresse <b>ohne</b> Nummer
     * setzen und nachsehen, welche Farbe an einer freien Stelle steht. Kommt
     * die neue, genügt der Kopf {@code no-store}, und die Nummer ist eine
     * Bequemlichkeit statt einer Notwendigkeit.
     */
    private void startCacheProbe() {
        if (!putSolidMagenta(view.browserWidth(), view.browserHeight())) {
            finished = true;
            onClose();
            return;
        }
        LOG.info("Hintergrundnachweis, Stufe 3 — hält Chromium am alten Bild fest? "
                + "Neues Bild unter derselben Adresse, ohne Nummer.");
        runScriptOnSession("window.fnSetBackground && window.fnSetBackground('"
                + FrameSchemes.SCHEME_URL_WITHOUT_GENERATION + "');");
        cacheProbeAtNanos = System.nanoTime() + 1_500_000_000L;
    }

    /** Ein Bild, das mit keiner Quadrantenfarbe zu verwechseln ist. */
    private boolean putSolidMagenta(int imageWidth, int imageHeight) {
        final int magenta = 0xFFFF00FF;         // ABGR: A=FF, B=FF, G=00, R=FF
        try (NativeImage image = new NativeImage(imageWidth, imageHeight, false)) {
            for (int y = 0; y < imageHeight; y++) {
                for (int x = 0; x < imageWidth; x++) {
                    image.setPixelRGBA(x, y, magenta);
                }
            }
            store.put(image.asByteArray(), "image/png", imageWidth, imageHeight);
            return true;
        } catch (Exception broken) {
            LOG.warn("Hintergrundnachweis: Probebild ließ sich nicht bauen", broken);
            return false;
        }
    }

    private void checkCacheProbe() {
        cacheProbeDone = true;
        int fbWidth = minecraft.getMainRenderTarget().width;
        int fbHeight = minecraft.getMainRenderTarget().height;
        ByteBuffer pixels = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        int[] found = read(pixels, fbWidth, fbHeight, 0.03, 0.03);

        boolean fresh = found[0] > 200 && found[1] < 60 && found[2] > 200;
        boolean stale = found[0] > 200 && found[1] < 60 && found[2] < 60;
        if (fresh) {
            LOG.info("Hintergrundnachweis: Der Zwischenspeicher steht nicht im Weg — "
                    + "unter derselben Adresse kam das neue Bild an, rgb({}, {}, {}). "
                    + "Der Kopf no-store genügt; die Nummer bleibt trotzdem, weil sie "
                    + "nichts kostet und den Fall ohne Kopf mit abdeckt.",
                    found[0], found[1], found[2]);
        } else if (stale) {
            LOG.warn("Hintergrundnachweis: Chromium hält am alten Bild fest — "
                    + "rgb({}, {}, {}) ist noch das rote Feld. Die Nummer in der "
                    + "Adresse ist damit nicht verhandelbar.", found[0], found[1], found[2]);
        } else {
            LOG.warn("Hintergrundnachweis: Zwischenspeicher-Probe nicht deutbar — "
                    + "rgb({}, {}, {}) ist weder Magenta noch Rot.",
                    found[0], found[1], found[2]);
        }
        finished = true;
        onClose();
    }

    /** Eine unverdeckte Stelle muss genau die Quadrantenfarbe zeigen. */
    private boolean exact(ByteBuffer pixels, int fbWidth, int fbHeight,
                          double across, double down, String what,
                          int red, int green, int blue) {
        int[] found = read(pixels, fbWidth, fbHeight, across, down);
        boolean ok = Math.abs(found[0] - red) <= TOLERANCE
                && Math.abs(found[1] - green) <= TOLERANCE
                && Math.abs(found[2] - blue) <= TOLERANCE;
        report(ok, what, found, String.format(Locale.GERMANY,
                "erwartet rgb(%d, %d, %d)", red, green, blue));
        return ok;
    }

    /**
     * Hinter dem Glas muss der erwartete Kanal deutlich führen.
     *
     * <p>Die Schwellen sind grob und sollen es sein: Was hier zu unterscheiden
     * ist, sind nicht zwei ähnliche Farben, sondern <b>Farbe gegen fast
     * Schwarz</b>. Ein Panel über Schwarz käme auf etwa sieben in jedem Kanal.
     */
    private boolean dominant(ByteBuffer pixels, int fbWidth, int fbHeight,
                             double across, double down, String what, char channel) {
        int[] found = read(pixels, fbWidth, fbHeight, across, down);
        int index = channel == 'r' ? 0 : channel == 'g' ? 1 : 2;
        int wanted = found[index];
        int other1 = found[(index + 1) % 3];
        int other2 = found[(index + 2) % 3];
        boolean ok = wanted > 90 && wanted > other1 + 60 && wanted > other2 + 60;
        report(ok, what, found,
                "erwartet einen klar führenden Kanal — über Schwarz käme rgb(7, 7, 10)");
        return ok;
    }

    /** Hinter dem Glas über Weiß muss alles hell sein. */
    private boolean bright(ByteBuffer pixels, int fbWidth, int fbHeight,
                           double across, double down, String what) {
        int[] found = read(pixels, fbWidth, fbHeight, across, down);
        boolean ok = found[0] > 140 && found[1] > 140 && found[2] > 140;
        report(ok, what, found, "erwartet über 140 in allen Kanälen");
        return ok;
    }

    /**
     * Über einer Farbgrenze müssen beide Farben zu sehen sein.
     *
     * <p>Der schärfste Nachweis dieser Datei: Ein gefiltertes Nichts kann
     * niemals zwei verschiedene Kanäle gleichzeitig anheben.
     */
    private boolean twoChannels(ByteBuffer pixels, int fbWidth, int fbHeight,
                                double across, double down, String what) {
        int[] found = read(pixels, fbWidth, fbHeight, across, down);
        boolean ok = found[0] > 40 && found[1] > 40;
        report(ok, what, found,
                "erwartet Rot und Grün beide über 40 — über Schwarz unmöglich");
        return ok;
    }

    /** Auch durch zwei Schichten muss noch etwas vom Bild übrig sein. */
    private boolean notBlack(ByteBuffer pixels, int fbWidth, int fbHeight,
                             double across, double down, String what) {
        int[] found = read(pixels, fbWidth, fbHeight, across, down);
        int sum = found[0] + found[1] + found[2];
        boolean ok = sum > 90;
        report(ok, what, found, "erwartet eine Summe über 90 — über Schwarz wären es 24");
        return ok;
    }

    private void runScriptOnSession(String code) {
        if (session != null) {
            session.runScript(code);
        }
    }

    private int[] read(ByteBuffer pixels, int fbWidth, int fbHeight,
                       double across, double down) {
        int x = (int) (across * fbWidth);
        int y = fbHeight - 1 - (int) (down * fbHeight);
        pixels.clear();
        RenderSystem.assertOnRenderThread();
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return new int[] {pixels.get(0) & 0xFF, pixels.get(1) & 0xFF, pixels.get(2) & 0xFF};
    }

    private void report(boolean ok, String what, int[] found, String expectation) {
        String line = String.format(Locale.GERMANY, "%s — rgb(%d, %d, %d), %s",
                what, found[0], found[1], found[2], expectation);
        if (ok) {
            LOG.info("Hintergrundnachweis: {} stimmt: {}", what, line);
        } else {
            LOG.warn("Hintergrundnachweis: {} FALSCH: {}", what, line);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (session != null) {
            session.close();
            session = null;
        }
        // Nicht geleert: Der Ablageort gehört der Sitzung, und die Messung
        // danach braucht ihn.
        super.removed();
    }
}
