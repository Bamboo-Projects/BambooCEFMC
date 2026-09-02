package dev.devpanda.bamboocef.web.view;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;

/**
 * Zeichnet Browsertexturen auf den Schirm — und gibt den Zeichenzustand zurück,
 * wie er war.
 *
 * <p><b>Vormultipliziertes Alpha ist keine Feinheit, sondern der Unterschied
 * zwischen richtig und grau.</b> Chromium liefert seine Bildpunkte
 * vormultipliziert: Halbdurchsichtiges Weiß kommt als {@code rgb(128,128,128)}
 * mit {@code a=128} an, nicht als {@code rgb(255,255,255)}. Gemessen, nicht
 * vermutet — der Selbsttest hat es gefunden.
 *
 * <p>Die übliche Mischung {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA} multipliziert
 * die Farbe noch einmal mit dem Alpha. Aus 128 würde 64, und alles
 * Durchscheinende sähe zu dunkel aus. Richtig ist:
 *
 * <pre>
 *   Ergebnis = Quelle · 1 + Ziel · (1 − Alpha)
 * </pre>
 *
 * <p>also {@code ONE, ONE_MINUS_SRC_ALPHA}.
 *
 * <p><b>Zur Bildlage wird nichts gedreht, und das ist geprüft.</b> Chromium
 * malt von oben nach unten, OpenGL zählt Texturzeilen von unten — aber
 * Minecrafts GUI zählt ebenfalls von oben. Zwei Umkehrungen heben einander
 * auf: Die Texturzeile 0 (Chromiums oberste) liegt bei {@code v = 0}, und
 * {@code v = 0} gehört an die obere Kante der Fläche. Der Vier-Ecken-Prüflauf
 * belegt das, statt es zu behaupten.
 *
 * <p><b>Nichts bleibt verstellt.</b> Der Zeichenzustand wird vorher gesichert
 * und hinterher zurückgegeben — mit NeoForges eigenem Mittel dafür, nicht mit
 * geratenen Voreinstellungen. Wer stattdessen hinterher „den Standard" setzt,
 * setzt ihn auch dann, wenn der Aufrufer etwas anderes eingestellt hatte.
 */
public final class BrowserCompositor {

    /** Einmal angelegt und wiederverwendet: je Bild ein neues wäre Abfall. */
    private final GlStateBackup backup = new GlStateBackup();

    /**
     * Zeichnet die Haupttextur und, wenn eines offen ist, das Popup darüber.
     *
     * @param graphics   Minecrafts Zeichenhilfe, wegen der Matrix
     * @param view       wo die Fläche liegt, in GUI-Einheiten
     * @param textureId  die Textur des Browsers
     * @param popup      das aufgeklappte Feld, oder {@code null}
     */
    public void draw(GuiGraphics graphics, BrowserView view, int textureId, PopupPlacement popup) {
        if (textureId == 0) {
            return;
        }
        GlStateManager._backupGlState(backup);
        try {
            RenderSystem.enableBlend();
            // Die eine Zeile, um die es geht.
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            // Ohne Tiefentest: Eine Oberfläche liegt vor allem, was hinter ihr
            // gezeichnet wurde, und hat keine eigene Tiefe zu verteidigen.
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

            Matrix4f matrix = graphics.pose().last().pose();
            blit(matrix, textureId,
                    view.guiX(), view.guiY(),
                    view.guiX() + view.guiWidth(), view.guiY() + view.guiHeight());

            if (popup != null && popup.visible() && popup.textureId() != 0) {
                blit(matrix, popup.textureId(),
                        (float) popup.guiX(), (float) popup.guiY(),
                        (float) (popup.guiX() + popup.guiWidth()),
                        (float) (popup.guiY() + popup.guiHeight()));
            }
        } finally {
            RenderSystem.setShaderTexture(0, 0);
            GlStateManager._restoreGlState(backup);
        }
    }

    /**
     * Ein Viereck mit der Textur darauf.
     *
     * <p>Die Reihenfolge der Ecken ist gegen den Uhrzeigersinn ab unten links,
     * wie Minecraft es überall tut. Die {@code v}-Koordinate läuft mit der
     * Bildschirmkante: oben null, unten eins. Genau deshalb ist keine
     * Spiegelung nötig.
     */
    private static void blit(Matrix4f matrix, int textureId,
                             float left, float top, float right, float bottom) {
        RenderSystem.setShaderTexture(0, textureId);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, left, bottom, 0f).setUv(0f, 1f).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, right, bottom, 0f).setUv(1f, 1f).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, right, top, 0f).setUv(1f, 0f).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, left, top, 0f).setUv(0f, 0f).setColor(255, 255, 255, 255);
        BufferUploader.drawWithShader(buffer.build());
    }

    /**
     * Wo ein aufgeklapptes Feld liegt und welche Textur es trägt.
     *
     * <p>In GUI-Einheiten, weil es dort gezeichnet wird. Die Umrechnung aus
     * Chromiums Pixeln macht {@link BrowserView#toGui(int, int)} — dieselbe
     * Rechnung wie für die Maus.
     */
    public record PopupPlacement(boolean visible, int textureId,
                                 double guiX, double guiY,
                                 double guiWidth, double guiHeight) {
    }
}
