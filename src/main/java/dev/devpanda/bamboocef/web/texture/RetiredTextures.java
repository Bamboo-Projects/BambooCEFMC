package dev.devpanda.bamboocef.web.texture;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.ArrayList;
import java.util.List;

/**
 * Textures that are no longer needed, released only once the frame is drawn.
 *
 * <p><b>Found by a crash.</b> Since 26.1 a screen only collects what is to be
 * drawn; the drawing comes later in the same frame. A session closed while
 * its screen is being collected (a screen that closes itself, a browser that
 * gets a new size) used to take its texture with it immediately, and the draw
 * a moment later found it closed: "Texture view has been closed". So a texture
 * is only handed in here, and the end of the frame closes it.
 *
 * <p>Render thread only.
 */
public final class RetiredTextures {

    private static final List<Runnable> WAITING = new ArrayList<>();

    /** Closes both once the current frame has been drawn. */
    public static void afterFrame(GpuTextureView view, GpuTexture texture) {
        if (view != null) {
            WAITING.add(view::close);
        }
        if (texture != null) {
            WAITING.add(texture::close);
        }
    }

    /** Called at the end of every frame. */
    public static void frameDrawn() {
        if (WAITING.isEmpty()) {
            return;
        }
        List<Runnable> due = List.copyOf(WAITING);
        WAITING.clear();
        for (Runnable release : due) {
            release.run();
        }
    }

    private RetiredTextures() {
    }
}
