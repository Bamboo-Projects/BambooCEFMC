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
 * Proves that Chromium really filters Minecraft's image as a backdrop.
 *
 * <p><b>The question this proof answers.</b> A {@code backdrop-filter} over a
 * transparent page looks as if it works — it just filters nothing. Per the CSS
 * specification, what gets filtered covers exclusively content of the
 * document; what lies <b>behind</b> the browser window is not part of it. That
 * is precisely why Minecraft's image has to be an element in the document and
 * not the ground we draw onto.
 *
 * <p><b>Two stages, and the order matters.</b>
 *
 * <ol>
 *   <li><b>Outside the glass panes</b> it is checked against the known
 *       quadrant colours. If that does not match, the image has not arrived at
 *       all — or lies shifted. Then there is no point in checking the
 *       glass.</li>
 *   <li><b>Inside</b>, the colour of the ground has to shine through. The
 *       difference is enormous and cannot be mistaken: a glass pane over red
 *       yields about {@code rgb(173, 7, 10)}, the same pane over black
 *       {@code rgb(7, 7, 10)}. Whoever merely claims it looks right cannot
 *       tell these two apart.</li>
 * </ol>
 *
 * <p><b>The sharpest single proof is in the middle:</b> a glass pane over the
 * boundary between red and green. There <b>both</b> channels have to be
 * clearly above zero. Over black or transparency that is impossible, no matter
 * how strongly it is filtered.
 *
 * <p>The backdrop image here is <b>artificial</b> and not from the world: four
 * known colours can be recomputed, a patch of forest cannot. Whether the route
 * from the real world works is a different question and is measured elsewhere.
 */
public final class BackdropProof extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/BackdropProof");

    /** How far an unoccluded colour may be off. */
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

    /** From when the cache probe is to be read. */
    private long cacheProbeAtNanos;
    private boolean cacheProbeDone;

    public BackdropProof() {
        super(Component.literal("Hintergrundnachweis"));
    }

    public static boolean finished() {
        return finished;
    }

    /** Opens the proof when a runtime is present. */
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
            // Opaque: the page brings its own background. Were the browser
            // transparent, during the loading gap the glass would filter the
            // void, and the proof would have to be able to tell the two apart.
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
     * Stores an image made of four known colours.
     *
     * <p>Red top left, green top right, blue bottom left, white bottom
     * right — the same arrangement as in the render proof, so that a mix-up
     * leaves the same fingerprint.
     *
     * @return the generation of the capture, or 0 on failure
     */
    private long putQuadrants(int imageWidth, int imageHeight) {
        // The format is ABGR, not RGBA — the method's name deceives, the name
        // of its parameter tells the truth.
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
        // Wait for three frames: the image is loaded lazily, and the first
        // draw may still show the page without it.
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
        // Over red: red has to come through clearly. Over black it would be
        // rgb(7,7,10).
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
     * Stage 3: does Chromium hold on to an image it already knows?
     *
     * <p><b>The measurement alone does not answer that.</b> That the number of
     * transfers stays the same may also be because the page is redrawn for
     * other reasons. It is only proven once a <b>different image</b> arrives
     * under the same address and one sees it.
     *
     * <p>So: store a single-colour image, set the address <b>without</b> the
     * generation, and check which colour sits at an unoccluded spot. If the new
     * one comes, the {@code no-store} header is enough, and the generation is a
     * convenience rather than a necessity.
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

    /** An image that cannot be mistaken for any quadrant colour. */
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

    /** An unoccluded spot has to show exactly the quadrant colour. */
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
     * Behind the glass the expected channel has to lead clearly.
     *
     * <p>The thresholds are coarse and are meant to be: what has to be
     * distinguished here is not two similar colours, but <b>colour against
     * near-black</b>. A panel over black would come to about seven in each
     * channel.
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

    /** Behind the glass over white everything has to be bright. */
    private boolean bright(ByteBuffer pixels, int fbWidth, int fbHeight,
                           double across, double down, String what) {
        int[] found = read(pixels, fbWidth, fbHeight, across, down);
        boolean ok = found[0] > 140 && found[1] > 140 && found[2] > 140;
        report(ok, what, found, "erwartet über 140 in allen Kanälen");
        return ok;
    }

    /**
     * Over a colour boundary both colours have to be visible.
     *
     * <p>The sharpest proof in this file: a filtered nothing can never raise
     * two different channels at the same time.
     */
    private boolean twoChannels(ByteBuffer pixels, int fbWidth, int fbHeight,
                                double across, double down, String what) {
        int[] found = read(pixels, fbWidth, fbHeight, across, down);
        boolean ok = found[0] > 40 && found[1] > 40;
        report(ok, what, found,
                "erwartet Rot und Grün beide über 40 — über Schwarz unmöglich");
        return ok;
    }

    /** Even through two layers something of the image has to be left. */
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
        // Not cleared: the store belongs to the session, and the measurement
        // afterwards needs it.
        super.removed();
    }
}
