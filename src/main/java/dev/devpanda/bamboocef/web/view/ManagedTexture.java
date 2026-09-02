package dev.devpanda.bamboocef.web.view;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.function.IntSupplier;

/**
 * A Minecraft texture that points at Chromium's texture.
 *
 * <p><b>What the detour is for.</b> On a screen a raw GL identifier can be
 * bound directly. In the world it cannot: there everything runs through
 * {@code RenderType}, and that requires a {@code ResourceLocation}. This class
 * is the translation in between — registered with the texture manager, and
 * queried on every bind.
 *
 * <p><b>The identifier is not remembered but fetched.</b> A {@code this.id},
 * once set, would be wrong after the first resize — Chromium then creates a new
 * texture. {@code getId()} therefore asks again every time.
 *
 * <p><b>And it is never deleted.</b> The texture belongs to the session, not
 * to the texture manager. Because the inherited {@code id} field stays
 * untouched at minus one, Minecraft reclaims nothing anyway; the two empty
 * methods are here regardless, so that the intent does not depend on chance.
 */
public final class ManagedTexture extends AbstractTexture {

    private final IntSupplier live;

    public ManagedTexture(IntSupplier live) {
        this.live = live;
    }

    @Override
    public int getId() {
        return live.getAsInt();
    }

    @Override
    public void load(ResourceManager manager) {
        // There is nothing to load: the images come from Chromium. That way
        // the texture also survives a resource reload (F3+T).
    }

    @Override
    public void close() {
        // It is not ours.
    }

    @Override
    public void releaseId() {
        // Neither is it.
    }
}
