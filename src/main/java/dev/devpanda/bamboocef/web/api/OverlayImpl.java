package dev.devpanda.bamboocef.web.api;

import dev.devpanda.bamboocef.web.runtime.BrowserSession;
import dev.devpanda.bamboocef.web.view.BrowserCompositor;
import dev.devpanda.bamboocef.web.view.BrowserView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Ein Overlay: eine {@link SessionSurface} und ein Rechteck in Bildschirmpunkten.
 *
 * <p>Zeichnen, Fokus und Eingaben — alles, was über die Fläche hinausgeht.
 * Paketprivat: Nach außen zählt {@link WebOverlay}.
 */
final class OverlayImpl implements WebOverlay {

    private final SessionSurface surface;
    private final BrowserCompositor compositor = new BrowserCompositor();
    private BrowserView view;
    private boolean visible = true;
    private OverlayFocus focus = OverlayFocus.NONE;
    private boolean closed;

    OverlayImpl(SessionSurface surface, BrowserView view) {
        this.surface = surface;
        this.view = view;
    }

    // ---- Lage -----------------------------------------------------------------

    @Override
    public WebSurface surface() {
        return surface;
    }

    @Override
    public int x() {
        return view.guiX();
    }

    @Override
    public int y() {
        return view.guiY();
    }

    @Override
    public int width() {
        return view.guiWidth();
    }

    @Override
    public int height() {
        return view.guiHeight();
    }

    BrowserView view() {
        return view;
    }

    @Override
    public void moveTo(int x, int y) {
        view = new BrowserView(x, y, view.guiWidth(), view.guiHeight(), view.guiScale());
    }

    @Override
    public void resize(int width, int height) {
        view = view.resized(Math.max(1, width), Math.max(1, height), view.guiScale());
        surface.resize(view.browserWidth(), view.browserHeight());
    }

    // ---- Zeichnen ---------------------------------------------------------------

    /**
     * Ein Bild — aus der Oberflächenschicht, unter jedem Bildschirm.
     *
     * <p><b>Die Skalierung wird hier geprüft, nicht gemeldet.</b> Es gibt
     * kein Ereignis, das einen Wechsel der GUI-Skalierung ansagt; der
     * Vergleich je Bild ist billiger als jede Suche nach einem Haken.
     */
    void draw(GuiGraphics graphics, double guiScale) {
        if (!visible || !alive()) {
            return;
        }
        if (view.guiScale() != guiScale) {
            view = view.resized(view.guiWidth(), view.guiHeight(), guiScale);
            surface.resize(view.browserWidth(), view.browserHeight());
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
    public boolean visible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            focus(OverlayFocus.NONE);
        }
    }

    // ---- Fokus ------------------------------------------------------------------

    @Override
    public OverlayFocus focus() {
        return focus;
    }

    @Override
    public void focus(OverlayFocus wanted) {
        if (closed || wanted == focus) {
            return;
        }
        if (wanted != OverlayFocus.NONE && !visible) {
            return;
        }
        OverlayFocus before = focus;
        focus = wanted;
        Overlays.noteFocus(this, wanted);
        surface.setFocused(wanted.takesKeys());
        if (before == OverlayFocus.MOUSE) {
            OverlayScreen.closeFor(this);
        }
        if (wanted == OverlayFocus.MOUSE) {
            Minecraft.getInstance().setScreen(new OverlayScreen(this));
        }
    }

    /** Der Mausbildschirm ist weg, gleich warum — dann ist auch der Mausfokus weg. */
    void mouseScreenGone() {
        if (focus == OverlayFocus.MOUSE) {
            focus = OverlayFocus.NONE;
            Overlays.noteFocus(this, OverlayFocus.NONE);
            surface.setFocused(false);
        }
    }

    // ---- Eingaben aus der Tastaturschicht ----------------------------------------

    /**
     * Eine Taste, wie GLFW sie meldet.
     *
     * @return ob die Fläche sie behält
     */
    boolean keyPress(int glfwKey, int scanCode, int action, int modifiers) {
        if (action == GLFW.GLFW_RELEASE) {
            return surface.keyReleased(glfwKey, scanCode, modifiers);
        }
        return surface.keyPressed(glfwKey, scanCode, modifiers);
    }

    boolean charTyped(char typed, int modifiers) {
        return surface.charTyped(typed, modifiers);
    }

    // ---- Ende -------------------------------------------------------------------

    @Override
    public boolean alive() {
        return !closed && surface.alive();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        focus(OverlayFocus.NONE);
        closed = true;
        Overlays.remove(this);
        surface.close();
    }

    @Override
    public String toString() {
        return "Overlay " + surface.name() + " " + width() + "x" + height()
                + " bei " + x() + "," + y() + " (" + focus + ")";
    }
}
