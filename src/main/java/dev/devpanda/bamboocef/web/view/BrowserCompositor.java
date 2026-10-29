package dev.devpanda.bamboocef.web.view;

import dev.devpanda.bamboocef.web.texture.GlTextureBackend;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Draws browser textures onto the screen.
 *
 * <p><b>Premultiplied alpha is not a subtlety but the difference between
 * correct and gray.</b> Chromium delivers its pixels premultiplied:
 * semi-transparent white arrives as {@code rgb(128,128,128)} with {@code a=128},
 * not as {@code rgb(255,255,255)}. Measured, not assumed: the self-test found
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
 * <p>that is, {@code ONE, ONE_MINUS_SRC_ALPHA}. Since 26.1 Minecraft ships
 * exactly that as {@link RenderPipelines#GUI_TEXTURED_PREMULTIPLIED_ALPHA}, so
 * no draw state has to be saved and restored here any more: the pipeline
 * carries it, and nothing outside it changes.
 *
 * <p><b>Nothing is flipped for the image orientation.</b> Chromium paints from
 * top to bottom, and texture row 0 (Chromium's topmost) lies at {@code v = 0},
 * which Minecraft's GUI puts at the top edge. The four-corners check run proves
 * this instead of asserting it.
 *
 * <p><b>Drawing is only an order.</b> The GUI of 26.1 collects what is to be
 * drawn and draws it later in the frame. That is fine: the texture is uploaded
 * in the pump at the start of the frame, before anything is collected.
 */
public final class BrowserCompositor {

    /**
     * Draws the main texture and, if one is open, the popup above it.
     *
     * @param graphics Minecraft's draw helper
     * @param view     where the surface lies, in GUI units
     * @param texture  the browser's texture
     * @param popup    the expanded field, or {@code null}
     */
    public void draw(GuiGraphicsExtractor graphics, BrowserView view, GlTextureBackend texture,
                     PopupPlacement popup) {
        if (texture.textureView() == null) {
            return;
        }
        blit(graphics, texture, view.guiX(), view.guiY(), view.guiWidth(), view.guiHeight());

        if (popup != null && popup.visible() && popup.texture().textureView() != null) {
            blit(graphics, popup.texture(),
                    popup.guiX(), popup.guiY(), popup.guiWidth(), popup.guiHeight());
        }
    }

    /**
     * The texture, stretched onto a rectangle in GUI units.
     *
     * <p>Minecraft's {@code blit} only takes whole numbers. A popup, though,
     * lies at fractions of a GUI unit as soon as the GUI scale is not one. The
     * fraction therefore goes into the matrix, and the {@code blit} itself
     * draws the image at its own size.
     */
    private static void blit(GuiGraphicsExtractor graphics, GlTextureBackend texture,
                             double left, double top, double width, double height) {
        int textureWidth = texture.width();
        int textureHeight = texture.height();
        if (textureWidth <= 0 || textureHeight <= 0) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) left, (float) top);
        graphics.pose().scale((float) (width / textureWidth), (float) (height / textureHeight));
        graphics.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, texture.location(),
                0, 0, 0f, 0f, textureWidth, textureHeight, textureWidth, textureHeight);
        graphics.pose().popMatrix();
    }

    /**
     * Where an expanded field lies and which texture it carries.
     *
     * <p>In GUI units, because it is drawn there. The conversion from Chromium's
     * pixels is done by {@link BrowserView#toGui(int, int)}, the same
     * computation as for the mouse.
     */
    public record PopupPlacement(boolean visible, GlTextureBackend texture,
                                 double guiX, double guiY,
                                 double guiWidth, double guiHeight) {
    }
}
