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
 * Draws browser textures onto the screen — and restores the draw state to how
 * it was.
 *
 * <p><b>Premultiplied alpha is not a subtlety but the difference between
 * correct and gray.</b> Chromium delivers its pixels premultiplied:
 * semi-transparent white arrives as {@code rgb(128,128,128)} with {@code a=128},
 * not as {@code rgb(255,255,255)}. Measured, not assumed — the self-test found
 * it.
 *
 * <p>The usual blend {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA} multiplies the
 * color by the alpha once more. 128 would become 64, and everything translucent
 * would look too dark. Correct is:
 *
 * <pre>
 *   result = source · 1 + target · (1 − alpha)
 * </pre>
 *
 * <p>that is, {@code ONE, ONE_MINUS_SRC_ALPHA}.
 *
 * <p><b>Nothing is flipped for the image orientation, and that is verified.</b>
 * Chromium paints from top to bottom, OpenGL counts texture rows from the
 * bottom — but Minecraft's GUI likewise counts from the top. Two inversions
 * cancel each other out: texture row 0 (Chromium's topmost) lies at
 * {@code v = 0}, and {@code v = 0} belongs at the top edge of the surface. The
 * four-corners check run proves this instead of asserting it.
 *
 * <p><b>Nothing stays altered.</b> The draw state is saved beforehand and
 * restored afterwards — with NeoForge's own means for it, not with guessed
 * defaults. Whoever instead sets "the default" afterwards sets it even when the
 * caller had configured something else.
 */
public final class BrowserCompositor {

    /** Created once and reused: a new one per frame would be garbage. */
    private final GlStateBackup backup = new GlStateBackup();

    /**
     * Draws the main texture and, if one is open, the popup above it.
     *
     * @param graphics   Minecraft's draw helper, for the matrix
     * @param view       where the surface lies, in GUI units
     * @param textureId  the browser's texture
     * @param popup      the expanded field, or {@code null}
     */
    public void draw(GuiGraphics graphics, BrowserView view, int textureId, PopupPlacement popup) {
        if (textureId == 0) {
            return;
        }
        GlStateManager._backupGlState(backup);
        try {
            RenderSystem.enableBlend();
            // The one line it all comes down to.
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            // No depth test: a surface lies in front of everything drawn behind
            // it and has no depth of its own to defend.
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
     * A quad with the texture on it.
     *
     * <p>The order of the corners is counterclockwise from the bottom left, as
     * Minecraft does everywhere. The {@code v} coordinate runs with the screen
     * edge: zero at the top, one at the bottom. That is exactly why no flip is
     * needed.
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
     * Where an expanded field lies and which texture it carries.
     *
     * <p>In GUI units, because it is drawn there. The conversion from Chromium's
     * pixels is done by {@link BrowserView#toGui(int, int)} — the same
     * computation as for the mouse.
     */
    public record PopupPlacement(boolean visible, int textureId,
                                 double guiX, double guiY,
                                 double guiWidth, double guiHeight) {
    }
}
