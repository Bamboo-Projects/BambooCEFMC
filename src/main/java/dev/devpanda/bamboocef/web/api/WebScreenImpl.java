package dev.devpanda.bamboocef.web.api;

import dev.devpanda.bamboocef.web.runtime.BrowserSession;
import dev.devpanda.bamboocef.web.screen.BrowserScreen;
import dev.devpanda.bamboocef.web.view.BrowserCompositor;
import dev.devpanda.bamboocef.web.view.BrowserCursor;
import dev.devpanda.bamboocef.web.view.BrowserView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Ein Web-Bildschirm über das ganze Bild — der Weg, wie eine Mod ihre
 * Oberfläche als Vollbild öffnet.
 *
 * <p><b>Aus Teilen, die es schon gibt.</b> Zeichnen über {@link BrowserCompositor},
 * Umrechnung über {@link BrowserView}, Zeiger über {@link BrowserCursor} —
 * dieselben wie im Overlay, nur füllt die Fläche hier den Bildschirm und die
 * Maus ist frei, weil es ein Bildschirm ist.
 *
 * <p><b>Kein Messwerkzeug.</b> Was der alte {@code BrowserScreen} an Bildzahl,
 * Uploadzeit und Tipplatenz kann, bleibt drüben bei den Prüfläufen. Hier steht
 * nur, was eine Mod für ihre Oberfläche braucht.
 *
 * <p><b>Zweimal Escape schließt.</b> Ein Editor wie Monaco fängt das erste
 * Escape selbst ab (er schließt damit seine Vorschläge); das zweite, das er
 * durchlässt, beendet den Bildschirm. F10 beendet ihn in jedem Fall.
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
        // Bei jeder Größen- oder Skalierungsänderung ruft Minecraft init erneut;
        // die Fläche bekommt dann das neue Maß.
        view = BrowserView.fullscreen(width, height, minecraft.getWindow().getGuiScale());
        surface.resize(view.browserWidth(), view.browserHeight());
        surface.onCursor(type -> wantedCursor = type);
        surface.setFocused(true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Der Hintergrund bleibt Minecrafts unscharfe Welt; eine durchsichtige
        // Seite lässt sie durchscheinen.
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int wanted = wantedCursor;
        if (wanted != appliedCursor) {
            cursor.apply(minecraft.getWindow().getWindow(), wanted);
            appliedCursor = wanted;
        }
        compositor.draw(graphics, view, surface.textureId(), popupPlacement());
    }

    private BrowserCompositor.PopupPlacement popupPlacement() {
        BrowserSession session = surface.session();
        if (!session.popupVisible()) {
            return null;
        }
        double[] corner = view.toGui(session.popupX(), session.popupY());
        return new BrowserCompositor.PopupPlacement(true, session.popupTextureId(),
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
        return false;      // Escape geht erst an die Seite, siehe unten.
    }

    // ---- Maus -------------------------------------------------------------------

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        surface.mouseMoved(view.toBrowserX(mouseX), view.toBrowserY(mouseY), heldModifiers());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        escapePending = false;
        surface.mousePressed(view.toBrowserX(mouseX), view.toBrowserY(mouseY), button, heldModifiers());
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        surface.mouseReleased(view.toBrowserX(mouseX), view.toBrowserY(mouseY), button, heldModifiers());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        surface.mouseMoved(view.toBrowserX(mouseX), view.toBrowserY(mouseY), heldModifiers());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        surface.mouseScrolled(view.toBrowserX(mouseX), view.toBrowserY(mouseY), scrollY, heldModifiers());
        return true;
    }

    // ---- Tasten -----------------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == BrowserScreen.RELEASE_KEY) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Erst an die Seite; erst das zweite Escape schließt.
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
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == BrowserScreen.RELEASE_KEY) {
            return true;
        }
        surface.keyReleased(keyCode, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char typed, int modifiers) {
        return typed != 0 && surface.charTyped(typed, modifiers);
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

    // ---- Ende -------------------------------------------------------------------

    @Override
    public void removed() {
        cursor.close(minecraft.getWindow().getWindow());
        surface.close();
        super.removed();
    }
}
