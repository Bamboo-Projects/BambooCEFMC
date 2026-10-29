package dev.devpanda.bamboocef.mixin;

import dev.devpanda.bamboocef.web.api.Overlays;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
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
 * <p>Two injections, both merely forwarding: the decision is made in
 * {@link Overlays}, where it is testable without a mixin.
 *
 * <p>Since 26.1 both methods take an event record instead of loose numbers,
 * and a typed character no longer carries modifier flags; they are queried.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void bamboocef$overlayKey(long window, int action, KeyEvent event,
                                      CallbackInfo info) {
        if (Overlays.keyPress(event.key(), event.scancode(), action, event.modifiers())) {
            info.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void bamboocef$overlayChar(long window, CharacterEvent event, CallbackInfo info) {
        if (Overlays.charTyped(event.codepoint(), heldModifiers())) {
            info.cancel();
        }
    }

    private static int heldModifiers() {
        Minecraft client = Minecraft.getInstance();
        int modifiers = 0;
        if (client.hasShiftDown()) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (client.hasControlDown()) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (client.hasAltDown()) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }
        return modifiers;
    }
}
