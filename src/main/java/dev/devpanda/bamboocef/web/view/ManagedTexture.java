package dev.devpanda.bamboocef.web.view;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.function.Supplier;

/**
 * A Minecraft texture that points at Chromium's texture.
 *
 * <p><b>What the detour is for.</b> Minecraft draws by name: both the GUI's
 * {@code blit} with its own pipeline and every {@code RenderType} in the world
 * ask the texture manager for an {@code Identifier}. This class is the
 * translation in between: registered with the texture manager, and queried on
 * every draw.
 *
 * <p><b>The view is not remembered but fetched.</b> A field, once set, would be
 * wrong after the first resize: a new size means a new texture.
 * {@link #getTextureView()} therefore asks again every time.
 *
 * <p><b>And it is never deleted here.</b> The texture belongs to the session,
 * not to the texture manager. {@link #close()} stays empty so that releasing
 * the name does not tear the image out from under the session.
 *
 * <p><b>Before the first image there is an empty one.</b> Until 1.21.1 a
 * surface without an image had the id zero, and drawing it drew nothing.
 * Minecraft's own textures throw instead; a mod that draws a surface one frame
 * too early would crash the game. A single transparent pixel keeps the old
 * behavior.
 */
public final class ManagedTexture extends AbstractTexture {

    private static GpuTextureView empty;

    private final Supplier<GpuTextureView> live;

    public ManagedTexture(Supplier<GpuTextureView> live, GpuSampler sampler) {
        this.live = live;
        this.sampler = sampler;
        // Made here and not on the first draw: clearing a texture is not
        // allowed while a render pass is open, and a draw may be inside one.
        empty();
    }

    @Override
    public GpuTextureView getTextureView() {
        GpuTextureView view = live.get();
        return view != null && !view.isClosed() ? view : empty();
    }

    /** One transparent pixel, made once and kept for the whole game. */
    private static GpuTextureView empty() {
        if (empty == null) {
            GpuTexture texture = RenderSystem.getDevice().createTexture(
                    () -> "bamboocef empty",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST
                            | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8, 1, 1, 1, 1);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, 0);
            empty = RenderSystem.getDevice().createTextureView(texture);
        }
        return empty;
    }

    @Override
    public GpuTexture getTexture() {
        return getTextureView().texture();
    }

    @Override
    public void close() {
        // It is not ours.
    }
}
