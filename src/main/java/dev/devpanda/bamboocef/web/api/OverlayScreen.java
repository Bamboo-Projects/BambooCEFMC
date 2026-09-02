package dev.devpanda.bamboocef.web.api;

import dev.devpanda.bamboocef.web.screen.BrowserScreen;
import dev.devpanda.bamboocef.web.view.BrowserCursor;
import dev.devpanda.bamboocef.web.view.BrowserView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The screen behind the mouse focus.
 *
 * <p><b>A screen, because that is the only way the mouse becomes free.</b>
 * Without an open screen, Minecraft grabs the pointer again on the next click
 * (MouseHandler.onPress). This screen draws nothing — the overlay keeps
 * painting itself from the GUI layer — and does only one thing: pass mouse and
 * keys to the surface.
 *
 * <p><b>The player stands still as long as the pointer is free</b> — as in
 * every interface. Forwarding the movement keys via KeyMapping.set was built
 * and moved nothing: NeoForge disables key bindings for the game as soon as a
 * screen is open. What the filter does not take is lost here; Escape without a
 * filter gives the mouse back.
 */
final class OverlayScreen extends Screen {

    private final OverlayImpl target;
    private final BrowserCursor cursor = new BrowserCursor();
    private volatile int wantedCursor = -1;
    private int appliedCursor = -1;
    private boolean insideLastFrame;

    OverlayScreen(OverlayImpl target) {
        super(Component.literal("Overlay"));
        this.target = target;
    }

    /** Closes the screen if it belongs to this overlay. */
    static void closeFor(OverlayImpl overlay) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof OverlayScreen screen && screen.target == overlay) {
            client.setScreen(null);
        }
    }

    @Override
    protected void init() {
        super.init();
        target.surface().onCursor(type -> wantedCursor = type);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No background, no surface: both are already drawn by the GUI
        // layer. Only the pointer remains here.
        int wanted = wantedCursor;
        if (wanted != appliedCursor) {
            cursor.apply(minecraft.getWindow().getWindow(), wanted);
            appliedCursor = wanted;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private BrowserView view() {
        return target.view();
    }

    private WebSurface surface() {
        return target.surface();
    }

    // ---- Mouse ------------------------------------------------------------------

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        BrowserView view = view();
        boolean inside = view.contains(mouseX, mouseY);
        if (inside) {
            surface().mouseMoved(view.toBrowserX(mouseX), view.toBrowserY(mouseY), heldModifiers());
        } else if (insideLastFrame) {
            surface().mouseLeft(view.toBrowserX(mouseX), view.toBrowserY(mouseY));
        }
        insideLastFrame = inside;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BrowserView view = view();
        if (!view.contains(mouseX, mouseY)) {
            // A click beside it gives the mouse back — the way you close a
            // menu by clicking somewhere else.
            target.focus(OverlayFocus.NONE);
            return true;
        }
        surface().mousePressed(view.toBrowserX(mouseX), view.toBrowserY(mouseY), button, heldModifiers());
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        BrowserView view = view();
        surface().mouseReleased(view.toBrowserX(mouseX), view.toBrowserY(mouseY), button, heldModifiers());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        BrowserView view = view();
        surface().mouseMoved(view.toBrowserX(mouseX), view.toBrowserY(mouseY), heldModifiers());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        BrowserView view = view();
        if (!view.contains(mouseX, mouseY)) {
            return false;
        }
        surface().mouseScrolled(view.toBrowserX(mouseX), view.toBrowserY(mouseY), scrollY, heldModifiers());
        return true;
    }

    // ---- Keys -------------------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == BrowserScreen.RELEASE_KEY) {
            target.focus(OverlayFocus.NONE);
            return true;
        }
        if (surface().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            target.focus(OverlayFocus.NONE);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == BrowserScreen.RELEASE_KEY) {
            return true;
        }
        if (surface().keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char typed, int modifiers) {
        return typed != 0 && surface().charTyped(typed, modifiers);
    }

    private static int heldModifiers() {
        int modifiers = 0;
        if (hasShiftDown()) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (hasControlDown()) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (hasAltDown()) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }
        return modifiers;
    }

    // ---- End --------------------------------------------------------------------

    @Override
    public void removed() {
        cursor.close(minecraft.getWindow().getWindow());
        target.surface().onCursor(null);
        target.mouseScreenGone();
        super.removed();
    }
}
