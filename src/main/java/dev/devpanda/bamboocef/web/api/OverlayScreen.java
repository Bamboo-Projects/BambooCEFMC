package dev.devpanda.bamboocef.web.api;

import dev.devpanda.bamboocef.web.screen.BrowserScreen;
import dev.devpanda.bamboocef.web.view.BrowserCursor;
import dev.devpanda.bamboocef.web.view.BrowserView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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

    /**
     * No background: the world stays as it is, the overlay lies on top of it.
     *
     * <p>Since 26.1 Minecraft collects a screen's background on its own before
     * {@link #extractRenderState}; without this empty method the overlay
     * would suddenly sit on a blurred world.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The one thing the usual background also does: subtitles that the
        // HUD held back for an open screen.
        minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // No surface: it is already drawn by the GUI layer. Only the pointer
        // remains here.
        int wanted = wantedCursor;
        if (wanted != appliedCursor) {
            cursor.apply(minecraft.getWindow().handle(), wanted);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        BrowserView view = view();
        if (!view.contains(mouseX, mouseY)) {
            // A click beside it gives the mouse back — the way you close a
            // menu by clicking somewhere else.
            target.focus(OverlayFocus.NONE);
            return true;
        }
        surface().mousePressed(view.toBrowserX(mouseX), view.toBrowserY(mouseY), event.button(), heldModifiers());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        BrowserView view = view();
        surface().mouseReleased(view.toBrowserX(event.x()), view.toBrowserY(event.y()), event.button(), heldModifiers());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        BrowserView view = view();
        surface().mouseMoved(view.toBrowserX(event.x()), view.toBrowserY(event.y()), heldModifiers());
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
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == BrowserScreen.RELEASE_KEY) {
            target.focus(OverlayFocus.NONE);
            return true;
        }
        if (surface().keyPressed(keyCode, event.scancode(), event.modifiers())) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            target.focus(OverlayFocus.NONE);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == BrowserScreen.RELEASE_KEY) {
            return true;
        }
        if (surface().keyReleased(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        return super.keyReleased(event);
    }

    /** A whole code point; Chromium takes it as UTF-16 units, see BrowserScreen. */
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event.codepoint() == 0) {
            return false;
        }
        boolean taken = false;
        int modifiers = heldModifiers();
        for (char unit : Character.toChars(event.codepoint())) {
            taken |= surface().charTyped(unit, modifiers);
        }
        return taken;
    }

    private static int heldModifiers() {
        int modifiers = 0;
        if (Minecraft.getInstance().hasShiftDown()) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (Minecraft.getInstance().hasControlDown()) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (Minecraft.getInstance().hasAltDown()) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }
        return modifiers;
    }

    // ---- End --------------------------------------------------------------------

    @Override
    public void removed() {
        cursor.close(minecraft.getWindow().handle());
        target.surface().onCursor(null);
        target.mouseScreenGone();
        super.removed();
    }
}
