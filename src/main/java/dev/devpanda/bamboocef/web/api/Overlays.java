package dev.devpanda.bamboocef.web.api;

import com.mojang.logging.LogUtils;
import dev.devpanda.bamboocef.web.screen.BrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * All open overlays — draw order, focus, and the hooks through which the game
 * reaches them.
 *
 * <p><b>Internal, not a guarantee.</b> The class is public because the client
 * entry point and the mixin have to call it; it is not part of the guaranteed
 * interface and may change.
 *
 * <p>Everything here runs on the render thread: drawing, ticking, and the
 * keyboard too — Minecraft forwards GLFW's callbacks there.
 */
public final class Overlays {

    private static final Logger LOG = LogUtils.getLogger();

    /** In the order they were opened — the later one lies on top. */
    private static final List<OverlayImpl> overlays = new ArrayList<>();
    private static OverlayImpl focused;

    private Overlays() {
    }

    static void add(OverlayImpl overlay) {
        overlays.add(overlay);
        LOG.info("{} geöffnet — offen: {}", overlay, overlays.size());
    }

    static void remove(OverlayImpl overlay) {
        if (overlays.remove(overlay)) {
            LOG.info("{} geschlossen — offen: {}", overlay, overlays.size());
        }
        if (focused == overlay) {
            focused = null;
        }
    }

    /** Only one has focus. Whoever gets it takes it from the other. */
    static void noteFocus(OverlayImpl overlay, OverlayFocus wanted) {
        if (wanted == OverlayFocus.NONE) {
            if (focused == overlay) {
                focused = null;
            }
            return;
        }
        OverlayImpl before = focused;
        focused = overlay;
        if (before != null && before != overlay) {
            before.focus(OverlayFocus.NONE);
        }
    }

    static OverlayImpl focused() {
        return focused;
    }

    // ---- Hooks for the client ------------------------------------------------------

    /** From the GUI layer, after everything Minecraft draws there. */
    public static void draw(GuiGraphics graphics) {
        if (overlays.isEmpty()) {
            return;
        }
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        for (OverlayImpl overlay : List.copyOf(overlays)) {
            overlay.draw(graphics, guiScale);
        }
    }

    /** Each tick: whatever has lost its browser is cleaned up. */
    public static void tick() {
        if (overlays.isEmpty()) {
            return;
        }
        for (OverlayImpl overlay : List.copyOf(overlays)) {
            if (!overlay.alive()) {
                overlay.close();
            }
        }
    }

    public static void closeAll() {
        for (OverlayImpl overlay : List.copyOf(overlays)) {
            overlay.close();
        }
    }

    public static int count() {
        return overlays.size();
    }

    // ---- Hooks for the keyboard (from the mixin) -----------------------------------

    /**
     * A key before Minecraft sees it.
     *
     * <p><b>Only without an open screen.</b> A screen gets its keys the usual
     * way — including the one of its own for the mouse focus. And
     * {@link BrowserScreen#RELEASE_KEY F10} ends any focus, no matter what the
     * filter says: there must be a way out that no surface can turn off.
     *
     * @return whether the key stays with an overlay
     */
    public static boolean keyPress(int glfwKey, int scanCode, int action, int modifiers) {
        OverlayImpl target = focused;
        if (target == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || client.getOverlay() != null) {
            return false;
        }
        if (glfwKey == BrowserScreen.RELEASE_KEY) {
            if (action == GLFW.GLFW_PRESS) {
                LOG.info("F10 — {} gibt die Tastatur zurück", target);
                target.focus(OverlayFocus.NONE);
            }
            return true;
        }
        return target.keyPress(glfwKey, scanCode, action, modifiers);
    }

    /** A character before Minecraft sees it. */
    public static boolean charTyped(int codePoint, int modifiers) {
        OverlayImpl target = focused;
        if (target == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || client.getOverlay() != null) {
            return false;
        }
        boolean taken = false;
        for (char part : Character.toChars(codePoint)) {
            taken |= target.charTyped(part, modifiers);
        }
        return taken;
    }
}
