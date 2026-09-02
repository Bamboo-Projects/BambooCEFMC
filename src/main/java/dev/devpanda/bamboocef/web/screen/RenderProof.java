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
 * Beweist, dass das Bild richtig herum und richtig gemischt auf dem Schirm
 * landet.
 *
 * <p><b>Zwei Fragen, die getrennt aussehen und zusammengehören.</b> Ob die
 * Textur richtig herum ist, hat der frühere Selbsttest schon geprüft — aber
 * nur bis zur Textur. Zwischen Textur und Schirm liegen die
 * Texturkoordinaten, und wer sie spiegelt, macht genau das rückgängig, was
 * vorher stimmte. Deshalb wird hier vom fertigen Bild zurückgelesen und nicht
 * von der Textur.
 *
 * <p><b>Vier Ecken, vier Farben.</b> Rot oben links, Grün oben rechts, Blau
 * unten links, Gelb unten rechts. Ein senkrecht gespiegeltes Bild vertauscht
 * Rot mit Blau, ein waagerecht gespiegeltes Rot mit Grün, und ein gedrehtes
 * beides. Jede Verwechslung hat damit ihren eigenen Fingerabdruck.
 *
 * <p><b>Und ein Streifen, der die Mischung entscheidet.</b> Über schwarzem
 * Grund:
 *
 * <table border="1">
 *   <caption>Was herauskommen muss</caption>
 *   <tr><th>Feld</th><th>richtig</th><th>mit der üblichen Mischung</th></tr>
 *   <tr><td>Weiß zu 50 %</td><td>128</td><td>64</td></tr>
 *   <tr><td>Rot zu 50 %</td><td>128, 0, 0</td><td>64, 0, 0</td></tr>
 *   <tr><td>ganz durchsichtig</td><td>0</td><td>0</td></tr>
 *   <tr><td>deckendes Weiß</td><td>255</td><td>255</td></tr>
 * </table>
 *
 * <p>Die mittlere Spalte ist das, was
 * {@code ONE, ONE_MINUS_SRC_ALPHA} über vormultiplizierten Farben ergibt; die
 * rechte, was {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA} daraus machte. Über
 * <b>schwarzem</b> Grund liegen die beiden weit auseinander — über weißem
 * fielen beide auf 255 und der Prüflauf wäre blind. Deshalb Schwarz.
 */
public final class RenderProof extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/RenderProof");

    /** Wie weit ein Wert danebenliegen darf. Chromium rundet, wir auch. */
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
     * Ob der Nachweis in dieser Sitzung schon durch ist.
     *
     * <p>Damit sich eine Kette bauen lässt: erst der Textur-Selbsttest, dann
     * dieser hier, dann die Messung. Zwei Browser gleichzeitig teilten sich
     * die Bildrate, und jede Messung berichtete die Hälfte als Grenze.
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

    /** Öffnet den Nachweis, wenn eine Runtime da ist. */
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

    /** Ob der Nachweis durch ist — für alles, was danach kommen soll. */
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
        // <b>Schwarz, und zwar deckend.</b> Der übliche verdunkelte Hintergrund
        // eines Bildschirms ist selbst durchscheinend — darunter läge die
        // Spielwelt, und die Rechnung ginge nicht mehr auf.
        graphics.fill(0, 0, width, height, 0xFF000000);
        compositor.draw(graphics, view, session.textureId(), null);

        if (session.paints() == 0) {
            // Geduld, aber nicht endlos: Ein Nachweis, der auf ein Bild
            // wartet, das nie kommt, hält alles auf, was danach käme.
            if (System.nanoTime() - openedNanos > 20_000_000_000L) {
                LOG.warn("Bildnachweis: nach zwanzig Sekunden kam kein Bild von {}",
                        session.currentUrl());
                finished = true;
                onClose();
            }
            return;
        }
        // Ein Bild zeichnen lassen, bevor gelesen wird: Im selben Aufruf steht
        // das Ergebnis noch nicht sicher im Puffer.
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
        // Mitten in den Eckfeldern, die je 30 Prozent messen.
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
     * Liest einen Bildpunkt aus dem fertigen Bild und vergleicht ihn.
     *
     * <p>Die senkrechte Umkehrung beim Lesen ist kein Teil dessen, was hier
     * geprüft wird: {@code glReadPixels} zählt immer von unten, das ist eine
     * Festlegung von OpenGL und keine Frage der Bildlage. Geprüft wird, was
     * <b>davor</b> passiert.
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
