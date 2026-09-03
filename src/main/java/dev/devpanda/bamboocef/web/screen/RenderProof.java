package dev.devpanda.bamboocef.web.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.devpanda.bamboocef.web.BrowserVisibility;
import dev.devpanda.bamboocef.web.WebRuntimeStatus;
import dev.devpanda.bamboocef.web.WebSupport;
import dev.devpanda.bamboocef.web.runtime.BrowserSession;
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
 * Proves that the image lands on screen the right way up and correctly
 * blended.
 *
 * <p><b>Two questions that look separate and belong together.</b> Whether the
 * texture is the right way up the earlier self-test already checked — but only
 * as far as the texture. Between texture and screen lie the texture
 * coordinates, and whoever mirrors them undoes exactly what was correct
 * before. That is why this reads back from the finished image and not from the
 * texture.
 *
 * <p><b>Four corners, four colours.</b> Red top left, green top right, blue
 * bottom left, yellow bottom right. A vertically mirrored image swaps red with
 * blue, a horizontally mirrored one red with green, and a rotated one both.
 * Every mix-up thus has its own fingerprint.
 *
 * <p><b>And a strip that decides the blending.</b> Over a black ground:
 *
 * <table border="1">
 *   <caption>What must come out</caption>
 *   <tr><th>Field</th><th>correct</th><th>with the usual blending</th></tr>
 *   <tr><td>White at 50 %</td><td>128</td><td>64</td></tr>
 *   <tr><td>Red at 50 %</td><td>128, 0, 0</td><td>64, 0, 0</td></tr>
 *   <tr><td>fully transparent</td><td>0</td><td>0</td></tr>
 *   <tr><td>opaque white</td><td>255</td><td>255</td></tr>
 * </table>
 *
 * <p>The middle column is what {@code ONE, ONE_MINUS_SRC_ALPHA} yields over
 * premultiplied colours; the right one, what
 * {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA} made of it. Over a <b>black</b> ground
 * the two lie far apart — over a white one both would fall to 255 and the test
 * run would be blind. Hence black.
 */
public final class RenderProof extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("BambooCEF/RenderProof");

    /** How far a value may be off. Chromium rounds, so do we. */
    private static final int TOLERANCE = 14;

    private static final String PAGE = """
            <!doctype html><html><head><style>
              html,body{margin:0;width:100%;height:100%;background:transparent;
                        overflow:hidden}
              div{position:absolute}
              /* Die Ecken bleiben in den Ecken. Sie über die ganze Fläche zu
                 ziehen wäre bequemer und machte den Streifen darunter
                 unbrauchbar: Er läge dann über Blau statt über dem Grund, den
                 Minecraft malt — und die Mischung prüfte etwas anderes, als
                 sie soll. Genau so ist es hier zuerst ausgegangen. */
              .tl{left:0;top:0;width:30%;height:30%;background:rgb(255,0,0)}
              .tr{right:0;top:0;width:30%;height:30%;background:rgb(0,255,0)}
              .bl{left:0;bottom:0;width:30%;height:30%;background:rgb(0,0,255)}
              .br{right:0;bottom:0;width:30%;height:30%;background:rgb(255,255,0)}
              /* Der Streifen liegt über nichts — dort scheint der schwarze
                 Grund durch, und nur über Schwarz trennen sich die richtige
                 und die falsche Mischung weit genug. */
              .b1{left:0;top:45%;width:25%;height:10%;background:rgba(255,255,255,0.5)}
              .b2{left:25%;top:45%;width:25%;height:10%;background:rgba(255,0,0,0.5)}
              .b3{left:50%;top:45%;width:25%;height:10%;background:rgba(0,0,0,0)}
              .b4{left:75%;top:45%;width:25%;height:10%;background:rgb(255,255,255)}
            </style></head><body>
              <div class="tl"></div><div class="tr"></div>
              <div class="bl"></div><div class="br"></div>
              <div class="b1"></div><div class="b2"></div>
              <div class="b3"></div><div class="b4"></div>
            </body></html>
            """;

    /**
     * Whether the proof has already gone through in this session.
     *
     * <p>So that a chain can be built: first the texture self-test, then this
     * one, then the measurement. Two browsers at once would share the frame
     * rate, and each measurement would report half of it as the limit.
     */
    private static boolean finished;

    private final BrowserCompositor compositor = new BrowserCompositor();
    private BrowserSession session;
    private BrowserView view;
    private boolean checked;
    private int framesDrawn;
    private long openedNanos;

    public RenderProof() {
        super(Component.literal("Bildnachweis"));
    }

    /** Opens the proof when a runtime is present. */
    public static boolean openIfPossible(Minecraft client) {
        WebRuntimeStatus status = WebSupport.ensureStarted();
        if (!status.usable()) {
            LOG.info("Bildnachweis übersprungen: {}", status);
            finished = true;
            return false;
        }
        client.setScreen(new RenderProof());
        return true;
    }

    /** Whether the proof has gone through — for everything meant to follow. */
    public static boolean finished() {
        return finished;
    }

    @Override
    protected void init() {
        super.init();
        view = BrowserView.fullscreen(width, height, minecraft.getWindow().getGuiScale());
        if (session != null) {
            session.resize(view.browserWidth(), view.browserHeight());
            return;
        }
        try {
            Path file = Files.createTempFile("fn-renderproof", ".html");
            Files.writeString(file, PAGE, StandardCharsets.UTF_8);
            file.toFile().deleteOnExit();
            session = BrowserSession.open(file.toUri().toString(), true,
                    view.browserWidth(), view.browserHeight(),
                    BrowserVisibility.FOREGROUND);
            openedNanos = System.nanoTime();
        } catch (Exception broken) {
            LOG.warn("Bildnachweis: Prüfseite ließ sich nicht ablegen", broken);
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (session == null || view == null) {
            return;
        }
        // <b>Black, and opaque at that.</b> A screen's usual darkened
        // background is itself translucent — beneath it would lie the game
        // world, and the arithmetic would no longer add up.
        graphics.fill(0, 0, width, height, 0xFF000000);
        compositor.draw(graphics, view, session.textureId(), null);

        if (session.paints() == 0) {
            // Patience, but not endless: a proof that waits for a frame that
            // never comes holds up everything that would follow.
            if (System.nanoTime() - openedNanos > 20_000_000_000L) {
                LOG.warn("Bildnachweis: nach zwanzig Sekunden kam kein Bild von {}",
                        session.currentUrl());
                finished = true;
                onClose();
            }
            return;
        }
        // Let one frame be drawn before reading: within the same call the
        // result is not yet reliably in the buffer.
        framesDrawn++;
        if (framesDrawn >= 2 && !checked) {
            checked = true;
            check();
        }
    }

    private void check() {
        int fbWidth = minecraft.getMainRenderTarget().width;
        int fbHeight = minecraft.getMainRenderTarget().height;
        ByteBuffer pixels = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());

        boolean allWell = true;
        // In the middle of the corner fields, which measure 30 percent each.
        allWell &= at(pixels, fbWidth, fbHeight, 0.15, 0.15, "oben links", 255, 0, 0);
        allWell &= at(pixels, fbWidth, fbHeight, 0.85, 0.15, "oben rechts", 0, 255, 0);
        allWell &= at(pixels, fbWidth, fbHeight, 0.15, 0.85, "unten links", 0, 0, 255);
        allWell &= at(pixels, fbWidth, fbHeight, 0.85, 0.85, "unten rechts", 255, 255, 0);

        allWell &= at(pixels, fbWidth, fbHeight, 0.125, 0.50,
                "Weiß zu 50 % über Schwarz", 128, 128, 128);
        allWell &= at(pixels, fbWidth, fbHeight, 0.375, 0.50,
                "Rot zu 50 % über Schwarz", 128, 0, 0);
        allWell &= at(pixels, fbWidth, fbHeight, 0.625, 0.50,
                "ganz durchsichtig", 0, 0, 0);
        allWell &= at(pixels, fbWidth, fbHeight, 0.875, 0.50,
                "deckendes Weiß", 255, 255, 255);

        LOG.info("Bildnachweis: {}", allWell
                ? "alle acht Stellen stimmen — Bildlage und Mischung sind richtig"
                : "es stimmt nicht alles, siehe oben");
        finished = true;
        onClose();
    }

    /**
     * Reads a pixel from the finished image and compares it.
     *
     * <p>The vertical inversion on reading is not part of what is checked here:
     * {@code glReadPixels} always counts from the bottom, that is an OpenGL
     * convention and not a question of image orientation. What is checked is
     * what happens <b>before</b> that.
     */
    private boolean at(ByteBuffer pixels, int fbWidth, int fbHeight,
                       double acrossFraction, double downFraction,
                       String what, int red, int green, int blue) {
        int x = (int) (acrossFraction * fbWidth);
        int y = fbHeight - 1 - (int) (downFraction * fbHeight);
        pixels.clear();
        RenderSystem.assertOnRenderThread();
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

        int foundRed = pixels.get(0) & 0xFF;
        int foundGreen = pixels.get(1) & 0xFF;
        int foundBlue = pixels.get(2) & 0xFF;
        boolean ok = Math.abs(foundRed - red) <= TOLERANCE
                && Math.abs(foundGreen - green) <= TOLERANCE
                && Math.abs(foundBlue - blue) <= TOLERANCE;

        if (ok) {
            LOG.info(String.format(Locale.GERMANY,
                    "Bildnachweis: %s stimmt — rgb(%d, %d, %d)",
                    what, foundRed, foundGreen, foundBlue));
        } else {
            LOG.warn(String.format(Locale.GERMANY,
                    "Bildnachweis: %s FALSCH — gefunden rgb(%d, %d, %d), erwartet rgb(%d, %d, %d)",
                    what, foundRed, foundGreen, foundBlue, red, green, blue));
        }
        return ok;
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
        super.removed();
    }
}
