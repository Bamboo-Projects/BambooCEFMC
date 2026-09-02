package dev.devpanda.bamboocef.mixin;

import dev.devpanda.bamboocef.web.api.Overlays;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keys and characters, before Minecraft sees them.
 *
 * <p><b>Why a mixin and not an event.</b> With no screen open,
 * {@code KeyboardHandler.keyPress} handles Escape (the pause menu) and the
 * movement keys ({@code KeyMapping.set}) itself, and only afterwards comes
 * NeoForge's {@code InputEvent.Key} — which cannot be cancelled. An overlay
 * that is meant to get Escape and the arrows while the player keeps moving
 * must sit ahead of that. This is the only place it can.
 *
 * <p>Two injections, one line each, both merely forwarding: the decision is
 * made in {@link Overlays}, where it is testable without a mixin.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void bamboocef$overlayKey(long window, int key, int scanCode, int action,
                                      int modifiers, CallbackInfo info) {
        if (Overlays.keyPress(key, scanCode, action, modifiers)) {
            info.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void bamboocef$overlayChar(long window, int codePoint, int modifiers,
                                       CallbackInfo info) {
        if (Overlays.charTyped(codePoint, modifiers)) {
            info.cancel();
        }
    }
}
