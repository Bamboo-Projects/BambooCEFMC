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
 * Alle offenen Overlays — Zeichenreihenfolge, Fokus und die Haken, über die
 * das Spiel sie erreicht.
 *
 * <p><b>Innen, kein Versprechen.</b> Die Klasse ist öffentlich, weil der
 * Client-Einstieg und das Mixin sie rufen müssen; sie gehört nicht zur
 * zugesagten Schnittstelle und darf sich ändern.
 *
 * <p>Alles hier läuft im Renderthread: Zeichnen, Takt, und auch die
 * Tastatur — Minecraft reicht GLFWs Rückrufe dorthin weiter.
 */
public final class Overlays {

    private static final Logger LOG = LogUtils.getLogger();

    /** In der Reihenfolge des Öffnens — die spätere liegt obenauf. */
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

    /** Nur eines hat Fokus. Wer ihn bekommt, nimmt ihn dem anderen. */
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

    // ---- Haken für den Client ------------------------------------------------------

    /** Aus der Oberflächenschicht, nach allem, was Minecraft dort malt. */
    public static void draw(GuiGraphics graphics) {
        if (overlays.isEmpty()) {
            return;
        }
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        for (OverlayImpl overlay : List.copyOf(overlays)) {
            overlay.draw(graphics, guiScale);
        }
    }

    /** Je Takt: Was seinen Browser verloren hat, wird abgeräumt. */
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

    // ---- Haken für die Tastatur (aus dem Mixin) ------------------------------------

    /**
     * Eine Taste, bevor Minecraft sie sieht.
     *
     * <p><b>Nur ohne offenen Bildschirm.</b> Ein Bildschirm bekommt seine
     * Tasten auf dem gewohnten Weg — auch der eigene für den Mausfokus.
     * Und {@link BrowserScreen#RELEASE_KEY F10} beendet jeden Fokus, gleich,
     * was der Filter sagt: Es muss einen Weg hinaus geben, den keine Fläche
     * abstellen kann.
     *
     * @return ob die Taste bei einem Overlay bleibt
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

    /** Ein Zeichen, bevor Minecraft es sieht. */
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
