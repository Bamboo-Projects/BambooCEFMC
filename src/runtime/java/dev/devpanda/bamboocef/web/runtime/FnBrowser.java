package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.frame.BorrowedFrame;
import dev.devpanda.bamboocef.web.frame.DirtyRegion;
import dev.devpanda.bamboocef.web.input.AwtModifiers;
import dev.devpanda.bamboocef.web.input.AwtMouseEvents;
import dev.devpanda.bamboocef.web.input.GlfwKeys;
import dev.devpanda.bamboocef.web.input.GlfwScancodes;
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
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A browser without a window, whose frames and events we accept ourselves.
 *
 * <p><b>This variant inherits from {@link CefBrowser_N} and not from
 * {@code CefBrowserOsr}.</b> Upstream's {@code CefBrowserOsr} is not the
 * stripped-down class of the CinemaMod fork: its constructor builds an AWT
 * {@code GLCanvas} via JOGL, paints into its GL context, and keeps the view
 * rectangle private. In a game that runs its own GL context and does not ship
 * JOGL, none of that is usable.
 *
 * <p>What remains is the layer below. {@code CefBrowser_N} brings creation,
 * resizing and input; {@link CefRenderHandler} is an interface anyone can
 * fulfil themselves. The test rig
 * {@code tools/runtime/probe/OsrBrowser.java} measured exactly this shape
 * before it landed here.
 *
 * <p><b>The input has to live here and can live nowhere else.</b>
 * {@code sendKeyEventRaw}, {@code sendMouseEvent} and
 * {@code sendMouseWheelEvent} are {@code protected} on {@code CefBrowser_N}.
 * Only a subclass can reach them. The decisions — which click was the nth one,
 * which key goes to whom — therefore live elsewhere, in classes with no
 * Chromium in them; here lives only the translation and the forwarding.
 *
 * <p><b>The buffer is only borrowed.</b> What goes out is a
 * {@link BorrowedFrame} — valid as long as this call runs, and deliberately
 * unrelated to the image that owns it.
 */
public class FnBrowser extends CefBrowser_N implements CefRenderHandler {

    /**
     * What a browser reports.
     *
     * <p>Everything here arrives on the render thread, as long as the runtime
     * pumps Chromium's message loop there — except {@link #cursorChanged(int)},
     * which can also come from Chromium's own threads.
     */
    public interface Events {

        /** A new frame of the main view. The buffer is valid only in this call. */
        void frame(BorrowedFrame frame);

        /**
         * A new frame of a dropdown popup.
         *
         * <p>The buffer is the size of the popup, not the page, and the dirty
         * regions count from the corner of the popup.
         */
        void popupFrame(BorrowedFrame frame);

        /** A popup opens or closes. */
        void popupShown(boolean shown);

        /** Where the popup lies, in browser pixels of the main view. */
        void popupPlaced(int x, int y, int width, int height);

        /** Chromium would like a different mouse cursor. */
        void cursorChanged(int cefCursorType);

        /**
         * Chromium has confirmed the close.
         *
         * <p>Comes after {@code close(true)} and out of the message loop — so
         * later, not immediately. Whoever waits for it during shutdown must
         * pump.
         */
        void browserClosed();
    }

    /**
     * A component that is never shown.
     *
     * <p><b>Why not null.</b> {@code CefClient.onTakeFocus} calls
     * {@code getParent()} on it without checking — with null the first tab in
     * the editor ends in a {@code NullPointerException}. Measured in the test
     * rig, not guessed. The peerless component from the input adapter does the
     * job: it has no parent, and the focus traversal ends quietly.
     */
    private static final Component NO_SURFACE =
            dev.devpanda.bamboocef.web.input.AwtEventSource.SOURCE;

    private final Events events;
    private final boolean transparent;

    /**
     * A running number per browser.
     *
     * <p>It appears in every trace line. Without it, with two browsers open at
     * once, there is no telling which one just did what — and that is exactly
     * the question with the error that arrives without a stack trace.
     */
    private static final java.util.concurrent.atomic.AtomicInteger COUNTER =
            new java.util.concurrent.atomic.AtomicInteger();

    private final int id = COUNTER.incrementAndGet();

    private boolean firstPaintSeen;

    /**
     * Records one step in the life of this browser.
     *
     * <p><b>What for.</b> On the new route, every browser creation produces
     * {@code Exception in thread "Render thread"} one to seven times — with no
     * stack trace, no message, and without any of our callbacks reporting
     * anything. As long as the origin cannot be pinned down, the only help is
     * to narrow down the moment: if the header lines in the log always sit
     * between the same two steps, that is half the answer.
     *
     * <p>Behind {@code -Dbamboocef.cef.trace=true}, because in everyday use it would
     * be noise.
     */
    private void trace(String step) {
        if (FnCefRuntime.TRACE) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "Spur Browser {}: {} — Thread {}", id, step,
                    Thread.currentThread().getName());
        }
    }

    // Works around CEF bug 1437: a rectangle of size zero keeps Chromium from
    // even starting to paint.
    private final Rectangle viewRect = new Rectangle(0, 0, 1, 1);

    /**
     * Opens a browser on this variant's client.
     *
     * <p>The factory lives here and not at the caller, because this class
     * exists twice — once on MCEF, once on our own runtime. Where the client
     * comes from only the respective variant knows; everything above it stays
     * untouched by that.
     */
    public static FnBrowser open(String url, boolean transparent, Events events) {
        // Sixty frames per second, set via CefBrowserSettings.
        //
        // This retires the proof-of-concept's environment-variable stopgap:
        // upstream knows the field, the fork did not.
        CefBrowserSettings settings = new CefBrowserSettings();
        settings.windowless_frame_rate = 60;
        return new FnBrowser(CefHost.client(), url, transparent, settings, events);
    }

    public FnBrowser(CefClient client, String url, boolean transparent,
            CefBrowserSettings settings, Events events) {
        super(client, url, null, null, null, settings);
        this.transparent = transparent;
        this.events = events;
    }

    // ---- What CefBrowser_N demands from a subclass --------------------------

    @Override
    public void createImmediately() {
        trace("createImmediately");
        if (getNativeRef("CefBrowser") != 0) {
            return;
        }
        // The window handle is null because there is no window, and osr is
        // true — by that and only that does CEF recognise that it should
        // deliver the frames rather than draw them.
        createBrowser(getClient(), 0, getUrl(), true, transparent, null, getRequestContext());
        trace("createBrowser zurück");
    }

    @Override
    public Component getUIComponent() {
        return NO_SURFACE;
    }

    @Override
    public CefRenderHandler getRenderHandler() {
        return this;
    }

    /**
     * There are no developer tools as an embedded browser.
     *
     * <p>The route via the debug port stays open and is the one we use — it
     * does not need this method.
     */
    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url,
            CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
        return null;
    }

    /** There is no screenshot via the GL context — there is none. */
    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        return CompletableFuture.completedFuture(null);
    }

    // ---- Size ---------------------------------------------------------------

    /**
     * Sets the size of the invisible window.
     *
     * <p>Both are needed: the rectangle that {@link #getViewRect} returns, and
     * the notification to Chromium. Without the rectangle it keeps painting at
     * the old size, without the notification it does not repaint at all.
     */
    public void resize(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (viewRect.width == safeWidth && viewRect.height == safeHeight) {
            return;
        }
        viewRect.setBounds(0, 0, safeWidth, safeHeight);
        wasResized(safeWidth, safeHeight);
        trace("resize auf " + safeWidth + "x" + safeHeight);
    }

    public int browserWidth() {
        return viewRect.width;
    }

    public int browserHeight() {
        return viewRect.height;
    }

    // ---- What comes in -----------------------------------------------------

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        countCall("getViewRect");
        return viewRect;
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        countCall("getScreenPoint");
        return viewPoint == null ? new Point() : new Point(viewPoint);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        boolean[] ok = {false};
        guarded("getScreenInfo", () -> {
            screenInfo.Set(1.0, 32, 8, false, viewRect.getBounds(), viewRect.getBounds());
            ok[0] = true;
        });
        return ok[0];
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
                        ByteBuffer buffer, int width, int height) {
        if (events == null || buffer == null || width <= 0 || height <= 0) {
            return;
        }
        if (!firstPaintSeen && !popup) {
            firstPaintSeen = true;
            trace("erstes onPaint " + width + "x" + height);
        }
        BorrowedFrame frame = new BorrowedFrame(buffer, width, height, regionsOf(dirtyRects));
        guarded("onPaint", () -> {
            if (popup) {
                events.popupFrame(frame);
            } else {
                events.frame(frame);
            }
        });
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        if (events != null) {
            guarded("onPopupShow", () -> events.popupShown(show));
        }
    }

    /**
     * Where the dropdown popup belongs.
     *
     * <p><b>Can come before the first frame and stick out past the edge.</b>
     * Chromium reports the desired position, not the clipped one. Clipping
     * therefore happens at drawing time and not here — what arrives here is the
     * truth about the popup and shall stay that way.
     */
    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        if (events != null && size != null) {
            guarded("onPopupSize",
                    () -> events.popupPlaced(size.x, size.y, size.width, size.height));
        }
    }


    /**
     * Chromium confirms the close.
     *
     * <p><b>Only here is the browser gone</b>, not already at
     * {@code close(true)} — that is a request. The confirmation comes out of
     * the message loop and thus from the pump thread.
     *
     * <p>The call to the superclass has to come first: there it releases its
     * own references and marks the browser as closed.
     */
    /**
     * Whether a focus change is currently in progress.
     *
     * <p>Not a state anyone queries — only the brake against the loop below. A
     * field without concurrency protection is enough: focus is set on the
     * render thread, and the feedback comes out of the same message loop.
     */
    private boolean settingFocus;

    /**
     * Sets the focus — exactly once per occasion.
     *
     * <p><b>Without the brake this is an endless loop.</b> Measured in the
     * game, as a stack trace:
     *
     * <pre>
     *   FnBrowser.setFocus(true)
     *     → CefBrowser_N.setFocus → N_SetFocus (native)
     *         → CefClient.onGotFocus
     *             → browser.setFocus(true)     back to the start
     * </pre>
     *
     * <p>It runs until the stack overflows; the native part swallows the
     * {@code StackOverflowError} and carries on. All that was visible of it was
     * {@code Exception in thread "Render thread"} without a stack trace — one
     * to seven times per browser creation, depending on how often the screen
     * sets the focus.
     *
     * <p><b>The CinemaMod fork does not have the feedback loop:</b> its
     * {@code CefClient.onGotFocus} only forwards the notification to the focus
     * handler, whereas upstream additionally sets the focus again. That is the
     * reason the same screen on MCEF produces not a single one of these
     * messages.
     *
     * <p>The loop can only be broken here: the other half belongs to upstream,
     * and a patch for it would be a patch against foreign focus behaviour
     * rather than against a missing capability.
     */
    @Override
    public void setFocus(boolean enable) {
        if (settingFocus) {
            // This is Chromium's feedback to the focus we are currently
            // setting. Forwarding it once more would mean getting it a third
            // time.
            return;
        }
        settingFocus = true;
        try {
            trace("setFocus " + enable);
            super.setFocus(enable);
        } finally {
            settingFocus = false;
        }
    }

    @Override
    public void onBeforeClose() {
        trace("onBeforeClose");
        super.onBeforeClose();
        if (events != null) {
            try {
                events.browserClosed();
            } catch (Throwable broken) {
                com.mojang.logging.LogUtils.getLogger()
                        .warn("Rückruf onBeforeClose ist gescheitert", broken);
            }
        }
    }

    /**
     * Chromium would like a different mouse cursor.
     *
     * <p><b>What arrives here is an AWT identifier and not a CEF cursor.</b>
     * The native part of this variant already translates — see
     * {@link AwtCursors}. Chromium's ordinal is passed on, so that everything
     * downstream looks the same for both routes.
     */
    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        if (events != null) {
            int cefType = AwtCursors.toCefCursorType(cursorType);
            guarded("onCursorChange", () -> events.cursorChanged(cefType));
        }
        // True means: we have taken care of it. Otherwise Chromium tries to set
        // a cursor itself — on a window that does not exist.
        return true;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        countCall("startDragging");
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        countCall("updateDragCursor");
    }

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) {
    }

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
    }

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {
    }

    private static List<DirtyRegion> regionsOf(Rectangle[] rectangles) {
        if (rectangles == null || rectangles.length == 0) {
            return List.of();
        }
        List<DirtyRegion> regions = new ArrayList<>(rectangles.length);
        for (Rectangle rectangle : rectangles) {
            if (rectangle.width > 0 && rectangle.height > 0) {
                regions.add(new DirtyRegion(Math.max(0, rectangle.x),
                        Math.max(0, rectangle.y), rectangle.width, rectangle.height));
            }
        }
        return regions;
    }

    // ---- What goes out -----------------------------------------------------

    /**
     * A mouse movement.
     *
     * @param leaving true when the cursor leaves the surface — Chromium needs
     *                this to undo hover states, otherwise the last button
     *                touched stays highlighted forever
     */
    public void moveMouse(int x, int y, int modifiers, boolean leaving) {
        int awt = AwtMouseEvents.toAwtModifiers(modifiers);
        if (leaving) {
            sendMouseEvent(AwtMouseEvents.exited(x, y, awt));
        } else {
            // With a button held down it is a drag and not a move. Chromium
            // distinguishes the two, and a text selection only comes about
            // during a drag.
            sendMouseEvent(AwtMouseEvents.moved(x, y, awt,
                    AwtMouseEvents.anyButtonDown(modifiers)));
        }
    }

    /**
     * A mouse button goes down or up.
     *
     * <p><b>No {@code MOUSE_CLICKED} of its own.</b> Chromium builds the click
     * out of down and up; an additional event would be a second click and
     * would falsify double and triple clicks.
     *
     * @param cefButton  Chromium's numbering, not Minecraft's
     * @param clickCount 1, 2 or 3 — by that and only that does Chromium
     *                   recognise a double click
     */
    public void clickMouse(int x, int y, boolean down, int cefButton,
                           int clickCount, int modifiers) {
        if (cefButton < 0) {
            return;
        }
        sendMouseEvent(AwtMouseEvents.awtButton(x, y, down,
                AwtMouseEvents.browserButtonToAwt(cefButton), clickCount,
                AwtMouseEvents.toAwtModifiers(modifiers)));
    }

    /**
     * The wheel turns.
     *
     * <p>The caller reports the distance, already multiplied by the factor of
     * three per notch. AWT splits the two apart again: {@code wheelRotation}
     * counts the notches, {@code scrollAmount} the lines per notch, and the
     * native part computes the same delta from the product.
     *
     * <p><b>The sign passes through unchanged</b> — measured, not guessed:
     * {@code wheelRotation +1} produces {@code deltaY -2.0} in the page, so
     * upward, and Minecraft's delta is positive when scrolling up.
     */
    public void scrollMouse(int x, int y, double amount, int modifiers) {
        int notches = (int) Math.round(amount / UNITS_PER_NOTCH);
        if (notches == 0) {
            // A movement too small for a notch must still not vanish —
            // otherwise slow scrolling feels as if the page were not
            // responding.
            if (amount == 0) {
                return;
            }
            notches = amount > 0 ? 1 : -1;
        }
        sendMouseWheelEvent(AwtMouseEvents.wheel(x, y, notches, UNITS_PER_NOTCH,
                AwtMouseEvents.toAwtModifiers(modifiers)));
    }

    /** Lines per notch, adopted from MCEF and proven there. */
    private static final int UNITS_PER_NOTCH = 3;

    private static int tracedKeys;

    /**
     * Catches what would otherwise vanish into JNI.
     *
     * <p><b>An exception out of a callback is invisible.</b> The native part
     * reports it via {@code ExceptionDescribe}; the header line lands on the
     * bare standard error output, the stack trace in the log — and because the
     * two take different paths, all that is left in the end is an "Exception in
     * thread" with nothing behind it. Measured exactly like that, on the first
     * run in the game.
     *
     * <p>That is why it is caught here and written properly. Rethrowing would
     * have helped no one: Chromium can do nothing with an exception out of a
     * callback and carries on regardless.
     */
    private static final java.util.Set<String> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Writes each callback to the log once.
     *
     * <p>For debugging only: which callbacks Chromium makes in which order is
     * documented nowhere, and an exception out of one of them arrives without
     * a stack trace.
     */
    private static void countCall(String what) {
        if (FnCefRuntime.TRACE && SEEN.add(what)) {
            com.mojang.logging.LogUtils.getLogger().info("Rückruf zum ersten Mal: {}", what);
        }
    }

    private static void guarded(String what, Runnable body) {
        countCall(what);
        try {
            body.run();
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .error("Rückruf {} ist gescheitert", what, broken);
        }
    }

    /**
     * Writes the first key presses to the log — raw.
     *
     * <p><b>What for, even though the test rig is green.</b> There the scancode
     * came from {@code glfwGetKeyScancode}; here it comes from Minecraft's key
     * callback. That both carry the same encoding is likely and unproven — and
     * if the 0x100 bit were missing here, the adapter would be quietly wrong in
     * the game even though every measurement is correct. Ten lines in the log
     * answer that once and for all.
     */
    private static void traceFirstKeys(int glfwKeyCode, int scanCode, int modifiers,
            boolean down) {
        if (!FnCefRuntime.TRACE || tracedKeys >= 80) {
            return;
        }
        tracedKeys++;
        com.mojang.logging.LogUtils.getLogger().info(
                "Taste {}: glfw={} scancode=0x{} basis=0x{} erweitert={} mods=0x{} vk={}",
                down ? "herunter" : "herauf", glfwKeyCode,
                Integer.toHexString(scanCode), Integer.toHexString(GlfwScancodes.base(scanCode)),
                GlfwScancodes.extended(scanCode), Integer.toHexString(modifiers),
                GlfwKeys.toAwtKeyCode(glfwKeyCode));
    }

    /**
     * A key goes down or up.
     *
     * <p><b>Why {@code sendKeyEventRaw} and not {@code sendKeyEvent}.</b> On
     * Windows the native part reads the scancode from the field
     * {@code java.awt.event.KeyEvent.scancode} — a private field that only the
     * native AWT code fills. A self-built event carries zero there, and out of
     * zero {@code MapVirtualKey} makes the virtual key code zero for every key:
     * Chromium then recognises not a single key. Hence the patch that takes the
     * values as parameters.
     *
     * <p><b>And why the scancode is taken apart.</b> GLFW carries "extended
     * key" as bit 8, Windows expects it in a different place. Arrow keys and
     * numpad share the lower eight bits; whoever loses the marker gets, for
     * arrow-up, the eight of the numpad.
     */
    public void sendKey(int glfwKeyCode, int scanCode, int modifiers, boolean down) {
        traceFirstKeys(glfwKeyCode, scanCode, modifiers, down);
        sendKeyEventRaw(down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                AwtModifiers.forKey(modifiers),
                KeyEvent.CHAR_UNDEFINED,
                GlfwScancodes.base(scanCode),
                GlfwScancodes.extended(scanCode),
                GlfwKeys.toAwtKeyCode(glfwKeyCode));
        if (down && needsCarriageReturn(glfwKeyCode, modifiers)) {
            sendChar(CARRIAGE_RETURN, modifiers);
        }
    }

    /** What Chromium wants to see for a line break. */
    private static final char CARRIAGE_RETURN = '\r';

    /**
     * Whether a carriage return still belongs after this key press.
     *
     * <p><b>The Enter key is the one key that needs both</b> — the key press
     * <i>and</i> a character. Upstream's own source says so explicitly in the
     * branch for Linux: "We need to treat the enter key as a key press of
     * character \r. This is apparently just how webkit handles it and what it
     * expects." On Windows the operating system sends a character message after
     * the key message for exactly this purpose.
     *
     * <p>For us it never arrived: {@code KEY_TYPED} comes solely from
     * Minecraft's {@code charTyped}, and that is not called for the Enter key —
     * it is not a printable character. The key press arrived, the line break
     * did not. The manual check found it; the automated matrix had checked the
     * key code for the Enter key and not the text.
     *
     * <p><b>Not with Ctrl or Alt.</b> Then the Enter key is a keyboard shortcut
     * and not a line break — sending a character after it would write a line
     * nobody wanted. Shift stays allowed: Shift+Enter is a line break in an
     * editor like any other.
     */
    private static boolean needsCarriageReturn(int glfwKeyCode, int modifiers) {
        boolean enter = glfwKeyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || glfwKeyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
        int blocking = org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL | org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
        return enter && (modifiers & blocking) == 0;
    }

    /**
     * A typed character.
     *
     * <p><b>Only from {@code charTyped}, never derived from a key code.</b>
     * What is an umlaut on a German keyboard, the operating system knows.
     * Whoever additionally wanted to build a character from the key press would
     * get every letter twice.
     *
     * <p><b>Without Ctrl and Alt.</b> Measured: a character with Ctrl does not
     * arrive at all — Chromium interprets it as a control character. Windows
     * reports AltGr as Ctrl + right Alt, and with that, on a German keyboard,
     * every {@code @}, {@code €}, {@code \}, {@code |} and {@code ~} would have
     * a Ctrl hanging off it.
     *
     * <p>The limit: {@code char} is sixteen bits wide. Everything up to
     * {@code U+FFFF} works; characters above it would arrive as two halves.
     */
    public void sendChar(char typed, int modifiers) {
        sendKeyEventRaw(KeyEvent.KEY_TYPED,
                AwtModifiers.forCharacter(modifiers),
                typed, 0, false, 0);
    }
}
