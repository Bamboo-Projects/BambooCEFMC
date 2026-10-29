package dev.devpanda.bamboocef.web.api;

import dev.devpanda.bamboocef.web.runtime.BrowserSession;
import dev.devpanda.bamboocef.web.screen.BrowserScreen;
import dev.devpanda.bamboocef.web.view.BrowserCompositor;
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
 * A web screen over the whole image — the way a mod opens its interface as
 * fullscreen.
 *
 * <p><b>From parts that already exist.</b> Drawing via {@link BrowserCompositor},
 * conversion via {@link BrowserView}, the pointer via {@link BrowserCursor} —
 * the same as in the overlay, only here the surface fills the screen and the
 * mouse is free, because it is a screen.
 *
 * <p><b>Not a measuring tool.</b> What the old {@code BrowserScreen} can do in
 * terms of frame rate, upload time, and typing latency stays over there with
 * the test runs. Here there is only what a mod needs for its interface.
 *
 * <p><b>Two presses of Escape close it.</b> An editor like Monaco catches the
 * first Escape itself (dismissing its suggestions with it); the second one,
 * which it lets through, ends the screen. F10 ends it in any case.
 */
final class WebScreenImpl extends Screen {

    private final SessionSurface surface;
    private final boolean transparent;
    private final BrowserCompositor compositor = new BrowserCompositor();
    private final BrowserCursor cursor = new BrowserCursor();
    private BrowserView view;
    private volatile int wantedCursor = -1;
    private int appliedCursor = -1;
    private boolean escapePending;

    WebScreenImpl(SessionSurface surface, BrowserView view, boolean transparent) {
        super(Component.literal("Web"));
        this.surface = surface;
        this.view = view;
        this.transparent = transparent;
    }

    @Override
    protected void init() {
        super.init();
        // On every size or scale change Minecraft calls init again;
        // the surface then gets the new measure.
        view = BrowserView.fullscreen(width, height, minecraft.getWindow().getGuiScale());
        surface.resize(view.browserWidth(), view.browserHeight());
        surface.onCursor(type -> wantedCursor = type);
        surface.setFocused(true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The background stays Minecraft's blurred world; a transparent page
        // lets it show through. Since 26.1 Minecraft collects it itself before
        // this method runs.
        int wanted = wantedCursor;
        if (wanted != appliedCursor) {
            cursor.apply(minecraft.getWindow().handle(), wanted);
            appliedCursor = wanted;
        }
        compositor.draw(graphics, view, surface.session().texture(), popupPlacement());
    }

    private BrowserCompositor.PopupPlacement popupPlacement() {
        BrowserSession session = surface.session();
        if (!session.popupVisible()) {
            return null;
        }
        double[] corner = view.toGui(session.popupX(), session.popupY());
        return new BrowserCompositor.PopupPlacement(true, session.popupTexture(),
                corner[0], corner[1],
                view.lengthToGui(session.popupWidth()),
                view.lengthToGui(session.popupHeight()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;      // Escape goes to the page first, see below.
    }

    // ---- Mouse ------------------------------------------------------------------

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        surface.mouseMoved(view.toBrowserX(mouseX), view.toBrowserY(mouseY), heldModifiers());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        escapePending = false;
        surface.mousePressed(view.toBrowserX(event.x()), view.toBrowserY(event.y()), event.button(), heldModifiers());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        surface.mouseReleased(view.toBrowserX(event.x()), view.toBrowserY(event.y()), event.button(), heldModifiers());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        surface.mouseMoved(view.toBrowserX(event.x()), view.toBrowserY(event.y()), heldModifiers());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        surface.mouseScrolled(view.toBrowserX(mouseX), view.toBrowserY(mouseY), scrollY, heldModifiers());
        return true;
    }

    // ---- Keys -------------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        if (keyCode == BrowserScreen.RELEASE_KEY) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // To the page first; only the second Escape closes.
            if (escapePending) {
                onClose();
                return true;
            }
            escapePending = true;
            surface.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        escapePending = false;
        surface.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == BrowserScreen.RELEASE_KEY) {
            return true;
        }
        surface.keyReleased(event.key(), event.scancode(), event.modifiers());
        return true;
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
            taken |= surface.charTyped(unit, modifiers);
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
        surface.close();
        super.removed();
    }
}
