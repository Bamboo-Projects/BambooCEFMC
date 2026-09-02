import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowser_N;
import org.cef.browser.CefPaintEvent;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Ein Browser ohne Fenster, ohne AWT-Fläche und ohne JOGL.
 *
 * <p><b>Warum diese Klasse überhaupt existiert.</b> Upstreams
 * {@code CefBrowserOsr} baut in seinem Konstruktor eine AWT-{@code GLCanvas}
 * über JOGL auf, malt in deren GL-Kontext und hält das Rechteck der Ansicht
 * privat. In einem Programm, das seinen eigenen GL-Kontext führt und JOGL
 * nicht mitliefert, ist davon nichts brauchbar. Was bleibt, ist die Ebene
 * darunter: {@link CefBrowser_N} bringt die Erzeugung, die Größenänderung und
 * die Eingabe mit, und {@link CefRenderHandler} ist eine Schnittstelle, die
 * jeder selbst erfüllen kann.
 *
 * <p>Deshalb erbt der Prüfstand hier — und der Mod später genauso. Was hier
 * steht, ist keine Probe-Sonderform, sondern die Bauform, die auch im Spiel
 * gilt: Nur so misst der Prüfstand, was später wirklich läuft.
 *
 * <p><b>Ein Rückruf kommt dort an, wo gepumpt wird.</b> Diese Klasse hat
 * keinen eigenen Thread und legt auch keinen an. Wer
 * {@code CefApp.doMessageLoopWork} aus seinem Renderthread ruft, bekommt
 * {@link #onPaint} in ebendiesem Thread.
 */
public class OsrBrowser extends CefBrowser_N implements CefRenderHandler {

    /** Was ein Browser meldet. */
    public interface Events {
        void frame(boolean popup, Rectangle[] dirty, ByteBuffer buffer, int width, int height);

        default void popupShown(boolean shown) {}

        default void popupPlaced(Rectangle where) {}

        default void cursorChanged(int cefCursorType) {}
    }

    private final Events events;
    private final boolean transparent;

    // Umgeht CEF-Fehler 1437: ein Rechteck der Größe null lässt Chromium gar
    // nicht erst anfangen zu malen.
    private final Rectangle viewRect = new Rectangle(0, 0, 1, 1);
    private boolean created = false;

    public OsrBrowser(CefClient client, String url, boolean transparent,
            CefBrowserSettings settings, Events events) {
        super(client, url, null, null, null, settings);
        this.transparent = transparent;
        this.events = events;
    }

    // ---- Was CefBrowser_N von einer Unterklasse verlangt --------------------

    @Override
    public void createImmediately() {
        createBrowserIfRequired();
    }

    /**
     * Erzeugt den nativen Browser, falls es ihn noch nicht gibt.
     *
     * <p>Dasselbe tut upstreams {@code CefBrowserOsr}; dort ist die Methode
     * privat, also steht sie hier noch einmal. Der Fensterhalter ist null, weil
     * es kein Fenster gibt, und {@code osr} ist wahr — daran und nur daran
     * erkennt CEF, dass es die Bilder liefern statt zeichnen soll.
     */
    private void createBrowserIfRequired() {
        if (getNativeRef("CefBrowser") != 0) {
            return;
        }
        createBrowser(getClient(), 0, getUrl(), true, transparent, null, getRequestContext());
        created = true;
    }

    /**
     * Ein Bauteil, das nie gezeigt wird.
     *
     * <p><b>Warum nicht null.</b> Es gibt keine Oberfläche, und null wäre die
     * ehrliche Antwort. Sie ist aber nicht die tragfähige:
     * {@code CefClient.onTakeFocus} ruft {@code getUIComponent().getParent()},
     * ohne zu prüfen — beim ersten Tabulator im Editor endet das in einer
     * NullPointerException. Gemessen, nicht vermutet: A4b hat sie ausgelöst.
     *
     * <p>Eine unmittelbare Unterklasse von {@link Component} zieht kein
     * Toolkit heran und hat keinen Peer; ihr {@code getParent()} ist null, und
     * die Fokuswanderung endet still, wie sie soll.
     */
    private static final Component NO_SURFACE = new Component() {};

    @Override
    public Component getUIComponent() {
        return NO_SURFACE;
    }

    @Override
    public CefRenderHandler getRenderHandler() {
        return this;
    }

    /**
     * Entwicklerwerkzeuge als eingebetteter Browser gibt es hier nicht.
     *
     * <p>Der Weg über den Debug-Port bleibt offen und ist der, den wir
     * benutzen — er braucht diese Methode nicht.
     */
    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url,
            CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
        return null;
    }

    /**
     * Ein Bildschirmfoto über den GL-Kontext gibt es nicht — es gibt keinen.
     */
    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        return CompletableFuture.completedFuture(null);
    }

    // ---- Größe --------------------------------------------------------------

    /**
     * Setzt die Größe der unsichtbaren Fläche.
     *
     * <p>Beides ist nötig: das Rechteck, das {@link #getViewRect} zurückgibt,
     * und die Meldung an Chromium. Ohne das Rechteck malt es in der alten
     * Größe weiter, ohne die Meldung malt es gar nicht neu.
     */
    public void resize(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (viewRect.width == safeWidth && viewRect.height == safeHeight) {
            return;
        }
        viewRect.setBounds(0, 0, safeWidth, safeHeight);
        wasResized(safeWidth, safeHeight);
    }

    public boolean isCreated() {
        return created;
    }

    // ---- CefRenderHandler ---------------------------------------------------

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        return viewRect;
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        return new Point(viewPoint);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        screenInfo.Set(1.0, 32, 8, false, viewRect.getBounds(), viewRect.getBounds());
        return true;
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
            ByteBuffer buffer, int width, int height) {
        if (events != null && buffer != null && width > 0 && height > 0) {
            events.frame(popup, dirtyRects, buffer, width, height);
        }
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        if (events != null) {
            events.popupShown(show);
        }
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        if (events != null && size != null) {
            events.popupPlaced(size);
        }
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        if (events != null) {
            events.cursorChanged(cursorType);
        }
        // Wahr heißt: Wir haben uns gekümmert. Chromium versucht sonst, selbst
        // einen Zeiger zu setzen — auf ein Fenster, das es nicht gibt.
        return true;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {}

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) {}

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {}

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {}

    // ---- Was hinausgeht -----------------------------------------------------

    /**
     * Eine Taste geht herunter oder herauf, oder ein Zeichen wird getippt.
     *
     * <p>Reicht nur weiter. Was welcher Wert bedeutet, steht in
     * {@code CefBrowser_N.sendKeyEventRaw}; die Übersetzung von GLFW nach AWT
     * gehört woandershin.
     */
    public void key(int type, int modifiers, char keyChar, int scancode, boolean extended,
            int keyCode) {
        sendKeyEventRaw(type, modifiers, keyChar, scancode, extended, keyCode);
    }

    public void mouse(java.awt.event.MouseEvent event) {
        sendMouseEvent(event);
    }

    public void wheel(java.awt.event.MouseWheelEvent event) {
        sendMouseWheelEvent(event);
    }
}
