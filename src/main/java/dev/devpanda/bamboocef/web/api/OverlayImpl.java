package dev.devpanda.bamboocef.web.api;

import dev.devpanda.bamboocef.web.runtime.BrowserSession;
import dev.devpanda.bamboocef.web.view.BrowserCompositor;
import dev.devpanda.bamboocef.web.view.BrowserView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/**
 * An overlay: a {@link SessionSurface} and a rectangle in screen points.
 *
 * <p>Drawing, focus, and input — everything that goes beyond the surface.
 * Package-private: to the outside, {@link WebOverlay} is what counts.
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

    // ---- Position -------------------------------------------------------------

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

    // ---- Drawing ----------------------------------------------------------------

    /**
     * One frame — from the GUI layer, beneath any screen.
     *
     * <p><b>The scale is checked here, not announced.</b> There is no event
     * that signals a change of the GUI scale; the per-frame comparison is
     * cheaper than any search for a hook.
     */
    void draw(GuiGraphicsExtractor graphics, double guiScale) {
        if (!visible || !alive()) {
            return;
        }
        if (view.guiScale() != guiScale) {
            view = view.resized(view.guiWidth(), view.guiHeight(), guiScale);
            surface.resize(view.browserWidth(), view.browserHeight());
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

    // ---- Focus ------------------------------------------------------------------

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

    /** The mouse screen is gone, no matter why — then the mouse focus is gone too. */
    void mouseScreenGone() {
        if (focus == OverlayFocus.MOUSE) {
            focus = OverlayFocus.NONE;
            Overlays.noteFocus(this, OverlayFocus.NONE);
            surface.setFocused(false);
        }
    }

    // ---- Input from the keyboard layer -------------------------------------------

    /**
     * A key as GLFW reports it.
     *
     * @return whether the surface keeps it
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

    // ---- End --------------------------------------------------------------------

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
