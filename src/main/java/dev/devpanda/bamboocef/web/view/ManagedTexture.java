package dev.devpanda.bamboocef.web.view;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
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
 */
public final class ManagedTexture extends AbstractTexture {

    private final Supplier<GpuTextureView> live;

    public ManagedTexture(Supplier<GpuTextureView> live, GpuSampler sampler) {
        this.live = live;
        this.sampler = sampler;
    }

    @Override
    public GpuTextureView getTextureView() {
        GpuTextureView view = live.get();
        if (view == null) {
            throw new IllegalStateException("Die Browserfläche hat noch kein Bild");
        }
        return view;
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
