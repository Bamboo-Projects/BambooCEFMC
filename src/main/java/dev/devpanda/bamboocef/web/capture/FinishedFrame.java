package dev.devpanda.bamboocef.web.capture;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The finished frame: everything drawn, nothing shown yet.
 *
 * <p><b>Why the proofs need this since 26.1.</b> Until 1.21.1 a screen drew
 * immediately, and one frame later its strokes stood in the buffer while the
 * next {@code render} ran. Since 26.1 a screen only collects; the interface is
 * drawn after it, and the next frame starts by drawing the world anew. There
 * is no moment inside a screen at which its own image can be read back. The
 * end of the frame is that moment: NeoForge fires its event right after the
 * game has drawn everything and right before the image goes to the window.
 *
 * <p>Render thread only, like everything that touches the graphics card.
 */
public final class FinishedFrame {

    private static final List<Runnable> WAITING = new ArrayList<>();
    private static int readFramebuffer;

    /** Runs the task once the current frame is completely drawn. */
    public static void whenDrawn(Runnable task) {
        WAITING.add(task);
    }

    /** Called at the end of every frame; runs what was waiting for it. */
    public static void frameDrawn() {
        if (WAITING.isEmpty()) {
            return;
        }
        List<Runnable> due = List.copyOf(WAITING);
        WAITING.clear();
        for (Runnable task : due) {
            task.run();
        }
    }

    /**
     * Reads one pixel of the main target as RGBA bytes.
     *
     * <p>Rows count from the bottom, the OpenGL way, as {@code glReadPixels}
     * always did. The main target has no framebuffer of its own to bind any
     * more, so a framebuffer of ours points at its color texture.
     */
    public static void readPixel(int x, int y, ByteBuffer into) {
        RenderSystem.assertOnRenderThread();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!(main.getColorTexture() instanceof GlTexture color)) {
            throw new IllegalStateException("Das Bild lässt sich nur über OpenGL zurücklesen");
        }
        if (readFramebuffer == 0) {
            readFramebuffer = GlStateManager.glGenFramebuffers();
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, color.glId(), 0);
        PixelPack.reset();
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, into);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
    }

    private FinishedFrame() {
    }
}
