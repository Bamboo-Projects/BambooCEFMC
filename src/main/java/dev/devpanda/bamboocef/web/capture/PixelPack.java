package dev.devpanda.bamboocef.web.capture;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;

/**
 * Puts OpenGL's read-back settings to their defaults before reading pixels.
 *
 * <p><b>Found by a crash, not by reading.</b> Since 26.1 Minecraft copies
 * textures into buffers itself and sets the pack row length for that, but never
 * resets it. A {@code glGetTexImage} into a buffer sized for one image then
 * writes its rows at the stride of whatever Minecraft read last, past the end
 * of the buffer: in the game that ended in a heap corruption right after the
 * self-test. Whoever reads pixels back calls this first.
 */
public final class PixelPack {

    public static void reset() {
        // No pixel buffer bound: otherwise the target address would be taken
        // as an offset into that buffer.
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 4);
    }

    private PixelPack() {
    }
}
