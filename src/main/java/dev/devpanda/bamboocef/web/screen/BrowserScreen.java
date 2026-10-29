package dev.devpanda.bamboocef.web.screen;

import dev.devpanda.bamboocef.web.BrowserVisibility;
import dev.devpanda.bamboocef.web.WebRuntimeStatus;
import dev.devpanda.bamboocef.web.WebSupport;
import dev.devpanda.bamboocef.web.input.BrowserFocus;
import dev.devpanda.bamboocef.web.runtime.BrowserSession;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A screen that shows a web page and makes it usable.
 *
 * <p><b>On the Escape key, and why it does not belong to Minecraft.</b> The
 * convenient assumption would be: Escape closes the screen, as everywhere in
 * Minecraft. For a web UI that is wrong — an open dropdown closes with Escape,
 * so does a dialog, and an editor cancels its autocompletion with it. Whoever
 * intercepts Escape takes from the page a key it needs itself, and the player
 * only notices that "the menu will not close".
 *
 * <p>The decision: <b>Escape goes to the page.</b> For the way back there is
 * {@link #RELEASE_KEY} — it hands the focus back to Minecraft, and then Escape
 * closes as usual. Two states instead of a special rule, and the same model
 * later carries a surface in the world: it too takes the focus and gives it
 * back again.
 *
 * <p><b>Cleanup happens in {@code removed()}, not in {@code onClose()}.</b>
 * Whoever opens another screen while this one is running gets no
 * {@code onClose()} — the browser would stay open, invisible, and keep
 * painting. {@code removed()} comes in every case.
 *
 * <p><b>Closing affects only this browser.</b> Chromium itself stays up — and
 * that still holds now that it belongs to us: other browsers of the same mod
 * hang off it, and starting up costs seconds. It is torn down on leaving the
 * game, in one place, not at every closed screen.
 */
public class BrowserScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("BambooCEF/BrowserScreen");

    /**
     * The key that reclaims the focus.
     *
     * <p>F10, because vanilla does not bind it and web UIs rarely need it.
     * Escape would be the obvious choice and is precisely for that reason not
     * one.
     */
    public static final int RELEASE_KEY = GLFW.GLFW_KEY_F10;

    /**
     * The key that closes off the measurement section.
     *
     * <p>It writes to the log what has accrued in frames and bytes since the
     * last press, and starts over. This way a single action can be measured —
     * scrolling only, typing only — without the sections mixing.
     */
    public static final int MEASURE_KEY = GLFW.GLFW_KEY_F9;

    /** How long the hint about the reclaim key stays up. */
    private static final long HINT_MILLIS = 6000L;

    private final String url;
    private final boolean transparent;

    private BrowserSession session;
    private BrowserView view;
    private BrowserFocus focus = BrowserFocus.BROWSER;
    private final BrowserCompositor compositor = new BrowserCompositor();
    private final BrowserCursor cursor = new BrowserCursor();

    /**
     * The most recently requested cursor, remembered rather than set at once.
     *
     * <p>Chromium reports cursor changes from its own threads too. GLFW
     * tolerates window calls only from the render thread; remembered and
     * applied at drawing time is the route without a crash.
     */
    private volatile int wantedCursor = -1;
    private int appliedCursor = -1;

    /** Which cursor kinds have already been reported — one line per kind. */
    private final java.util.Set<Integer> seenCursors = new java.util.HashSet<>();

    private long openedMillis;
    private boolean insideLastFrame;
    private long measuringSinceNanos;
    private long paintsAtMeasureStart;
    private long popupPaintsAtMeasureStart;
    private int measureSection;

    public BrowserScreen(Component title, String url, boolean transparent) {
        super(title);
        this.url = url;
        this.transparent = transparent;
    }

    /** Whether a browser can be had at all — to be asked before opening. */
    public static WebRuntimeStatus availability() {
        return WebSupport.ensureStarted();
    }

    @Override
    protected void init() {
        super.init();
        openedMillis = System.currentTimeMillis();
        view = BrowserView.fullscreen(width, height, minecraft.getWindow().getGuiScale());
        if (session == null) {
            WebRuntimeStatus status = WebSupport.ensureStarted();
            if (!status.usable()) {
                LOG.warn("Kein Browser zu haben: {}", status);
                return;
            }
            session = BrowserSession.open(url, transparent,
                    view.browserWidth(), view.browserHeight(),
                    BrowserVisibility.FOREGROUND);
            session.onCursor(type -> wantedCursor = type);
            session.setFocused(true);
        } else {
            // <b>This is where resizing hangs.</b> On every window or scaling
            // change Minecraft calls {@code resize()}, and that calls
            // {@code init()} again. An override of {@code resize()} of our own
            // would only be needed to do the same thing once more. The browser
            // stays up and gets a new dimension.
            session.resize(view.browserWidth(), view.browserHeight());
        }
    }

    /**
     * Runs before anything is drawn.
     *
     * <p><b>The only point at which Minecraft's own image still stands
     * untouched in the buffer.</b> The world is finished, and since 26.1 the
     * whole interface is only collected here and drawn afterwards, background
     * blur included. Whoever wants to capture the image must do it here: one
     * frame later the browser would be in it.
     */
    protected void beforeDrawing() {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (session != null) {
            session.noteScreenFrame();
        }
        // The blurred background is no longer drawn here: since 26.1
        // Minecraft collects it itself before this method runs.
        beforeDrawing();
        if (session == null || view == null) {
            graphics.centeredText(font,
                    Component.literal("Die Web-Runtime steht nicht bereit."),
                    width / 2, height / 2, 0xFFFFFFFF);
            return;
        }
        applyWantedCursor();
        compositor.draw(graphics, view, session.texture(), popupPlacement());
        drawHint(graphics);
    }

    /**
     * Where the dropdown popup lies on screen.
     *
     * <p>Via the same conversion as the mouse: Chromium reports the position in
     * its own pixels, and whoever converts it differently from the mouse
     * cursor draws the popup next to the spot where it is clicked.
     */
    private BrowserCompositor.PopupPlacement popupPlacement() {
        if (!session.popupVisible()) {
            return null;
        }
        double[] corner = view.toGui(session.popupX(), session.popupY());
        return new BrowserCompositor.PopupPlacement(true, session.popupTexture(),
                corner[0], corner[1],
                view.lengthToGui(session.popupWidth()),
                view.lengthToGui(session.popupHeight()));
    }

    private void applyWantedCursor() {
        int wanted = wantedCursor;
        if (wanted != appliedCursor && focus == BrowserFocus.BROWSER) {
            cursor.apply(minecraft.getWindow().handle(), wanted);
            appliedCursor = wanted;
            // Each kind to the log once: whether the cursor changes arrive at
            // all can otherwise only be seen by looking — and "looking" is the
            // answer this whole spike avoids.
            //
            // <b>With -Ptrace every change, not only every new kind.</b> A
            // cursor sitting in the wrong place cannot otherwise be told apart
            // from one that simply was not reset: the second request for the
            // same kind would appear nowhere.
            boolean firstOfKind = seenCursors.add(wanted);
            if (firstOfKind || Boolean.getBoolean("bamboocef.cef.trace")) {
                LOG.info("Mauszeiger: Chromium wünscht {} ({})",
                        dev.devpanda.bamboocef.web.view.CursorType.fromId(wanted), wanted);
            }
        }
    }

    /**
     * The hint about the reclaim key, for the first few seconds.
     *
     * <p>A key you have to know and that appears nowhere is not a solution — it
     * is a trap. After a few seconds the hint disappears, because by then it
     * would only be in the way.
     */
    private void drawHint(GuiGraphicsExtractor graphics) {
        long shown = System.currentTimeMillis() - openedMillis;
        if (shown > HINT_MILLIS) {
            return;
        }
        String text = focus == BrowserFocus.BROWSER
                ? "F10 gibt die Tastatur an Minecraft zurück · F9 misst einen Abschnitt"
                : "F10 gibt die Tastatur an die Seite — Escape schließt";
        int alpha = (int) (255 * Math.min(1.0, (HINT_MILLIS - shown) / 1000.0));
        graphics.fill(0, 0, width, 14, (alpha / 2) << 24);
        graphics.text(font, text, 6, 3, (alpha << 24) | 0xFFFFFF, false);
    }

    // ---- Focus -------------------------------------------------------------

    /**
     * Escape does not close on its own.
     *
     * <p>As long as the page has the focus, the first Escape belongs to it; a
     * second one immediately after closes. If the focus is with Minecraft,
     * Escape is handled by hand in {@link #keyPressed}. Minecraft's own
     * handling would close on the first Escape and leave the page nothing.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /**
     * The game keeps running.
     *
     * <p>An editor is not a pause menu: whoever writes a program while a
     * machine is running wants to see it run.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void setFocus(BrowserFocus wanted) {
        if (focus == wanted) {
            return;
        }
        focus = wanted;
        openedMillis = System.currentTimeMillis();      // show the hint again
        if (session != null) {
            session.setFocused(wanted == BrowserFocus.BROWSER);
        }
        if (wanted == BrowserFocus.MINECRAFT) {
            cursor.reset(minecraft.getWindow().handle());
            appliedCursor = -1;
        }
    }

    // ---- Keyboard ----------------------------------------------------------

    /**
     * Whether the last event was an Escape.
     *
     * <p><b>Why it is needed.</b> Escape belongs to two recipients: Monaco
     * closes its suggestion list, its search box, its overview with it — and a
     * Minecraft screen closes with it. Both at once is impossible, and whoever
     * has the focus otherwise gets everything.
     *
     * <p>Until now the page got every Escape, and the branch that closes was
     * never reachable. Now the rule is: <b>the first Escape belongs to the
     * page, a second one immediately after closes the screen.</b> Any other
     * input in between — key or mouse click — resets the counter, because then
     * the user has kept working and meant no close.
     *
     * <p>Java cannot know whether Monaco has something open at the moment. Nor
     * does this rule need to: whoever has closed something does not immediately
     * press again.
     */
    private boolean escapePending;

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == RELEASE_KEY) {
            setFocus(focus == BrowserFocus.BROWSER
                    ? BrowserFocus.MINECRAFT : BrowserFocus.BROWSER);
            return true;
        }
        if (keyCode == MEASURE_KEY) {
            reportAndResetMeasurement();
            return true;
        }
        if (session != null && focus.routesKeyboard()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (escapePending) {
                    // The second Escape. The first went to the page; if it
                    // closed something with it, the user would have kept
                    // working afterwards and not pressed Escape once more.
                    escapePending = false;
                    onClose();
                    return true;
                }
                escapePending = true;
            } else {
                escapePending = false;
            }
            session.keyPressed(keyCode, event.scancode(), event.modifiers());
            // <b>Do not pass on to super.</b> Otherwise the screen sees the
            // same key once more, and what the page has just accepted
            // additionally triggers something in Minecraft.
            return true;
        }
        escapePending = false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == RELEASE_KEY) {
            return true;
        }
        if (session != null && focus.routesKeyboard()) {
            session.keyReleased(event.key(), event.scancode(), event.modifiers());
            return true;
        }
        return super.keyReleased(event);
    }

    /**
     * Typed text.
     *
     * <p>The only route by which umlauts, ß and the euro sign arrive
     * correctly: Minecraft got the character from the operating system,
     * keyboard layout included. It could not be back-computed from key codes.
     *
     * <p>Since 26.1 a whole code point arrives, without modifier flags.
     * Chromium takes UTF-16 units, so a character beyond the basic plane goes
     * out as its two halves, the way Minecraft used to hand it over; the flags
     * are queried.
     */
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event.codepoint() == 0) {
            return false;
        }
        if (session != null && focus.routesKeyboard()) {
            int modifiers = heldModifiers();
            for (char unit : Character.toChars(event.codepoint())) {
                session.charTyped(unit, modifiers);
            }
            return true;
        }
        return super.charTyped(event);
    }

    // ---- Mouse -------------------------------------------------------------

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (session == null || view == null) {
            return;
        }
        boolean inside = view.contains(mouseX, mouseY);
        if (inside) {
            session.mouseMoved(view.toBrowserX(mouseX), view.toBrowserY(mouseY),
                    heldModifiers());
        } else if (insideLastFrame) {
            // Exactly once on leaving, not on every movement outside.
            session.mouseLeft(view.toBrowserX(mouseX), view.toBrowserY(mouseY));
        }
        insideLastFrame = inside;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Whoever clicks keeps working and does not mean, with the next Escape,
        // to close the screen.
        escapePending = false;
        if (session == null || view == null || !view.contains(event.x(), event.y())) {
            return super.mouseClicked(event, doubleClick);
        }
        // A click into the surface reclaims the focus — otherwise one would
        // have to repeat it with a key after giving it back.
        setFocus(BrowserFocus.BROWSER);
        // Minecraft's own double-click flag stays unused: the session counts
        // clicks itself, by Chromium's rules.
        session.mousePressed(view.toBrowserX(event.x()), view.toBrowserY(event.y()),
                event.button(), heldModifiers(), System.currentTimeMillis());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (session == null || view == null) {
            return super.mouseReleased(event);
        }
        session.mouseReleased(view.toBrowserX(event.x()), view.toBrowserY(event.y()),
                event.button(), heldModifiers());
        return true;
    }

    /**
     * Dragging is a movement with a button held down.
     *
     * <p>Minecraft reports it as its own kind of event, Chromium knows only
     * movements — which buttons are down is in the flags. The session carries
     * them along, so an ordinary movement suffices here.
     */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (session == null || view == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        session.mouseMoved(view.toBrowserX(event.x()), view.toBrowserY(event.y()),
                    heldModifiers());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (session == null || view == null || !view.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        session.mouseScrolled(view.toBrowserX(mouseX), view.toBrowserY(mouseY),
                scrollY, heldModifiers());
        return true;
    }

    // ---- Measurement -------------------------------------------------------

    /**
     * Writes the measurement section to the log and begins a new one.
     *
     * <p>The question behind it is not whether the frame path is fast — that is
     * measured. The question is whether ordinary use makes unexpectedly large
     * regions dirty. A scroll that repaints the whole page every time costs as
     * much as an animation, and one would not see it.
     */
    private void reportAndResetMeasurement() {
        reportSection(null);
    }

    /**
     * Like {@link #reportAndResetMeasurement()}, but with a name.
     *
     * <p>For a run that knows the sections itself: "Rollen" says more in the
     * log than "Abschnitt 3".
     */
    protected void reportSection(String label) {
        if (session == null) {
            return;
        }
        String name = label == null ? "Abschnitt " + measureSection : label;
        if (measuringSinceNanos != 0) {
            double seconds = (System.nanoTime() - measuringSinceNanos) / 1_000_000_000.0;
            long paints = session.paints() - paintsAtMeasureStart;
            long popupPaints = session.popupPaints() - popupPaintsAtMeasureStart;
            var main = session.texture();
            var popup = session.popupTexture();
            double fullFrame = (double) session.width() * session.height() * 4;
            LOG.info(String.format(java.util.Locale.GERMANY,
                    "%s über %.1f s: %d Bilder (%.1f/s), %d Uploads, "
                            + "davon %d Vollbild, %d Ausschnitte",
                    name, seconds, paints, paints / seconds,
                    main.uploads(), main.fullUploads(), main.regions()));
            LOG.info(String.format(java.util.Locale.GERMANY,
                    "%s: %.1f KB je Bild (%.2f %% eines Vollbilds), "
                            + "%.2f MB/s, Uploadzeit %s",
                    name, main.bytesPerUpload() / 1024.0,
                    main.bytesPerUpload() / fullFrame * 100.0,
                    main.uploadedBytes() / seconds / (1024.0 * 1024.0),
                    main.uploadTimes().summary()));
            if (popupPaints > 0) {
                LOG.info(String.format(java.util.Locale.GERMANY,
                        "%s: Popup %d Bilder, %d Uploads, %.1f KB gesamt",
                        name, popupPaints, popup.uploads(),
                        popup.uploadedBytes() / 1024.0));
            }
        }
        measureSection++;
        measuringSinceNanos = System.nanoTime();
        paintsAtMeasureStart = session.paints();
        popupPaintsAtMeasureStart = session.popupPaints();
        session.texture().resetStats();
        session.popupTexture().resetStats();
    }

    /** Whether a browser is running at all. */
    protected boolean hasSession() {
        return session != null;
    }

    /** The running browser, for everything that needs more than the metrics. */
    protected dev.devpanda.bamboocef.web.runtime.BrowserSession session() {
        return session;
    }

    /** How many frames the browser has delivered so far. */
    protected long paints() {
        return session == null ? 0L : session.paints();
    }

    /** How long it takes from an input to the frame. */
    protected dev.devpanda.bamboocef.web.measure.DurationSamples inputLatency() {
        return session == null
                ? new dev.devpanda.bamboocef.web.measure.DurationSamples()
                : session.inputLatency();
    }

    /** The metrics of the frame path back to Minecraft. */
    protected dev.devpanda.bamboocef.web.texture.GlTextureBackend texture() {
        return session == null ? null : session.texture();
    }

    /**
     * The currently pressed modifier keys, as GLFW flags.
     *
     * <p><b>Without them half the mouse is dead.</b> Ctrl and mouse wheel zoom
     * an editor, Shift and click extend a selection, Alt and click set a second
     * text cursor. Whoever leaves the flags at zero for mouse events takes all
     * that from the page — and it looks as if it cannot do it.
     *
     * <p>For key events Minecraft supplies the flags; for typed characters and
     * movements it does not, and there they have to be queried.
     */
    protected static int heldModifiers() {
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

    /**
     * A key together with a modifier, as it would come from a keyboard.
     *
     * <p><b>The scancode is not optional on Windows.</b> The native part builds
     * the Windows key code with {@code MapVirtualKey} <b>from the scancode</b>;
     * a zero in it yields a key code of zero, and Chromium sees a key that does
     * not exist. On real input Minecraft supplies it — whoever generates keys
     * themselves has to query it.
     */
    protected void sendKeyCombination(int glfwKey, int modifiers) {
        int scanCode = GLFW.glfwGetKeyScancode(glfwKey);
        keyPressed(new KeyEvent(glfwKey, scanCode, modifiers));
        keyReleased(new KeyEvent(glfwKey, scanCode, modifiers));
    }

    /** Whether the page is still loading — before that, any call into it fizzles out. */
    protected boolean pageLoading() {
        return session == null || session.loading();
    }

    /** Passes a line of JavaScript through to the page. */
    protected void runScript(String code) {
        if (session != null) {
            session.runScript(code);
        }
    }

    /**
     * Shrinks the surface to a fraction of the full size.
     *
     * <p>For the measurement: a resize makes Chromium recompute the whole page,
     * and what that costs in transfer wants to be measured.
     */
    protected void resizeTo(double factor) {
        if (session == null) {
            return;
        }
        view = new BrowserView(0, 0,
                Math.max(1, (int) (width * factor)),
                Math.max(1, (int) (height * factor)),
                minecraft.getWindow().getGuiScale());
        session.resize(view.browserWidth(), view.browserHeight());
    }

    // ---- End ---------------------------------------------------------------

    /**
     * Cleanup — and right here.
     *
     * <p>{@code onClose()} comes only when this screen closes itself. Whoever
     * opens another one instead leaves it behind here. A browser that survives
     * that keeps painting and is never seen again.
     */
    @Override
    public void removed() {
        cursor.close(minecraft.getWindow().handle());
        if (session != null) {
            session.setFocused(false);
            session.close();
            session = null;
        }
        super.removed();
    }
}
