package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.BrowserVisibility;
import dev.devpanda.bamboocef.web.FramePacer;
import dev.devpanda.bamboocef.web.frame.BorrowedFrame;
import dev.devpanda.bamboocef.web.input.ClickCounter;
import dev.devpanda.bamboocef.web.input.MouseButtons;
import dev.devpanda.bamboocef.web.texture.GlTextureBackend;

import java.util.function.IntConsumer;

/**
 * A browser together with its textures, its input and its state.
 *
 * <p>The whole frame path in one place: Chromium paints, we upload, Minecraft
 * draws. Nothing in between — <b>no mailbox, no copy</b>.
 *
 * <p><b>Why this is right here and not lazy.</b> MCEF pumps Chromium's message
 * loop via a mixin in {@code GameRenderer.render}. With that, {@code onPaint}
 * arrives on the render thread, the drawing context is valid, and the borrowed
 * buffer is valid at exactly this moment. A copy into a separate image and a
 * mailbox in between would cost eight and a half megabytes per frame at 1080p
 * and would solve a problem that does not exist here.
 *
 * <p><b>All coordinates here are browser pixels.</b> Where the surface lies on
 * the screen this class does not know and is not supposed to know — that is
 * converted by {@code BrowserView}, and for drawing and the mouse with the same
 * formula. Only that way can a surface in the world later feed the same session
 * with its own coordinates.
 */
public final class BrowserSession implements AutoCloseable, FnBrowser.Events,
        dev.devpanda.bamboocef.web.ManagedBrowser {

    private final FnBrowser browser;
    private final GlTextureBackend texture = new GlTextureBackend();

    /**
     * The second texture, for expanded fields.
     *
     * <p><b>And not a copy-back into the first.</b> MCEF holds the content of a
     * dropdown field in its own buffer and paints it anew into the main view's
     * texture on every frame — because Chromium does not retain the popup
     * content between two calls. That costs one copy and one extra transfer per
     * frame for as long as the field is open.
     *
     * <p>A separate texture costs none of that: it keeps its content until a new
     * frame comes, and is simply laid over the top when drawing.
     */
    private final GlTextureBackend popupTexture = new GlTextureBackend();

    private final FramePacer pacer;
    private final MouseButtons buttons = new MouseButtons();
    private final ClickCounter clicks = new ClickCounter();

    private boolean popupShown;
    private int popupX;
    private int popupY;
    private int popupWidth;
    private int popupHeight;

    private IntConsumer cursorSink;
    private volatile java.util.function.Consumer<String> messageSink;

    /**
     * When input last went in for which no frame has yet come.
     *
     * <p><b>The number that decides an editor.</b> Frames per second say little
     * about whether the typing feels immediate; what counts is the span between
     * a keystroke and the frame that shows it. That is exactly what is measured
     * here — without a return channel from the page, solely from what passes
     * through this class anyway.
     *
     * <p>Zero means: no input is outstanding, the next frame belongs to nothing
     * anyone did.
     */
    private long pendingInputNanos;

    /**
     * The input whose frame is already uploaded and only still needs to be
     * drawn.
     */
    private long paintedForInputNanos;

    /** When the last upload finished. */
    private long uploadEndNanos;

    /** A: from the input until Chromium delivers a frame. */
    private final dev.devpanda.bamboocef.web.measure.DurationSamples toPaint =
            new dev.devpanda.bamboocef.web.measure.DurationSamples();

    /** C: from the end of the upload until Minecraft draws the next time. */
    private final dev.devpanda.bamboocef.web.measure.DurationSamples toScreen =
            new dev.devpanda.bamboocef.web.measure.DurationSamples();

    /** The whole span, from the input to the drawn frame. */
    private final dev.devpanda.bamboocef.web.measure.DurationSamples totalLatency =
            new dev.devpanda.bamboocef.web.measure.DurationSamples();

    /**
     * The gap between two frames that Chromium delivers.
     *
     * <p>This is the cadence of windowless mode, and it is a different quantity
     * from everything beside it: the three sections above measure how long
     * something takes. This one measures how often anyone gets a turn at all.
     * Whoever confuses the two takes waiting time for compute time.
     */
    private final dev.devpanda.bamboocef.web.measure.DurationSamples paintGaps =
            new dev.devpanda.bamboocef.web.measure.DurationSamples();

    private long lastPaintNanos;

    private long paints;
    private long popupPaints;
    private long firstPaintNanos;
    private final long createdNanos = System.nanoTime();
    private boolean closed;

    /**
     * A running number per session.
     *
     * <p>It stands in every log line about this session. Without it two browsers
     * open at once cannot be told apart in the log, and that is exactly when one
     * needs to.
     */
    private static final java.util.concurrent.atomic.AtomicInteger COUNTER =
            new java.util.concurrent.atomic.AtomicInteger();

    private final int id = COUNTER.incrementAndGet();

    /**
     * What this session is called.
     *
     * <p>Empty means: it has none, then its number stands in the log. Whoever
     * runs several browsers at once wants to be able to tell them apart — in the
     * log and in Chromium's list under the remote-debugging port.
     */
    private final String name;

    private BrowserSession(String url, boolean transparent, int width, int height,
                           BrowserVisibility visibility, String name) {
        this.name = name == null ? "" : name;
        this.pacer = new FramePacer(visibility);
        this.browser = FnBrowser.open(url, transparent, this);
        // <b>The order is not arbitrary.</b> First the browser is allowed to be
        // closed, then it is created, and only after that does it get its size.
        // The other way round, the resize goes to a browser that does not yet
        // exist — it fizzles, and because the rectangle is then already correct,
        // no one tries a second time. Result: a browser that never paints. That
        // is exactly what happened here first.
        browser.setCloseAllowed();
        browser.createImmediately();
        browser.resize(width, height);
        // Only now register: a browser that fails on creation should not count
        // as open.
        dev.devpanda.bamboocef.web.BrowserManager.register(this);
    }

    /** How this session is named in the log. */
    @Override
    public String describe() {
        String was = name.isEmpty() ? "Sitzung " + id : "\"" + name + "\" (Sitzung " + id + ")";
        return was + " (" + width() + "x" + height() + ")";
    }

    /** The assigned name, or empty. */
    public String name() {
        return name;
    }

    /**
     * Is the session still open?
     *
     * <p>False from the moment someone called {@link #close()} — including the
     * manager when leaving the world. Whoever draws a texture of this session
     * asks this before every frame.
     */
    public boolean isOpen() {
        return !closed;
    }

    /**
     * Chromium has acknowledged the close.
     *
     * <p>Comes after {@code close(true)} and out of the message loop, so later
     * and from the pump thread. Only with it is the session really gone.
     */
    @Override
    public void browserClosed() {
        dev.devpanda.bamboocef.web.BrowserManager.closed(this);
    }

    /**
     * Opens a browser.
     *
     * <p>Only to be called when the runtime is ready — otherwise there is no
     * {@code CefClient} to hang it on.
     */
    public static BrowserSession open(String url, boolean transparent,
                                      int width, int height,
                                      BrowserVisibility visibility) {
        return new BrowserSession(url, transparent, width, height, visibility, "");
    }

    /**
     * Opens a browser under a name.
     *
     * <p>The name stands in the log and in Chromium's list. It changes nothing
     * about the behavior — it answers the question of which of five pages is the
     * one meant.
     */
    public static BrowserSession open(String url, boolean transparent,
                                      int width, int height,
                                      BrowserVisibility visibility, String name) {
        return new BrowserSession(url, transparent, width, height, visibility, name);
    }

    // ---- What the browser reports -----------------------------------------

    @Override
    public void frame(BorrowedFrame frame) {
        if (closed) {
            return;
        }
        if (paints == 0) {
            firstPaintNanos = System.nanoTime();
        }
        // The gap between two frames — the number that shows the cadence.
        //
        // <p><b>Why it stands here and not in the screen.</b> What Minecraft
        // draws has nothing to do with what Chromium delivers; the screen only
        // sees when it itself had a turn. The browser's cadence is to be had
        // exactly here, at the point where its frame arrives.
        long paintNow = System.nanoTime();
        if (lastPaintNanos != 0) {
            paintGaps.record(paintNow - lastPaintNanos);
        }
        lastPaintNanos = paintNow;
        paints++;
        long pending = pendingInputNanos;
        if (pending != 0) {
            toPaint.record(System.nanoTime() - pending);
            pendingInputNanos = 0;
            paintedForInputNanos = pending;
        }
        texture.upload(frame);
        uploadEndNanos = System.nanoTime();
        pacer.drawn(uploadEndNanos);
    }

    @Override
    public void popupFrame(BorrowedFrame frame) {
        if (closed) {
            return;
        }
        popupPaints++;
        popupTexture.upload(frame);
    }

    @Override
    public void popupShown(boolean shown) {
        popupShown = shown;
        if (!shown) {
            // Chromium repaints the uncovered surface itself — it comes as an
            // ordinary frame of the main view. There is nothing to restore here,
            // only something to hide.
            popupWidth = 0;
            popupHeight = 0;
        }
    }

    @Override
    public void popupPlaced(int x, int y, int width, int height) {
        popupX = x;
        popupY = y;
        popupWidth = width;
        popupHeight = height;
    }

    @Override
    public void cursorChanged(int cefCursorType) {
        IntConsumer sink = cursorSink;
        if (sink != null) {
            sink.accept(cefCursorType);
        }
    }

    /** Who gets the cursor requests — the screen, as long as it is open. */
    public void onCursor(IntConsumer sink) {
        this.cursorSink = sink;
    }

    /**
     * The receiver for messages from the page ({@code window.fnSend}).
     *
     * <p>One receiver per session; it runs on the render thread. {@code null}
     * deregisters it again.
     */
    public void onMessage(java.util.function.Consumer<String> sink) {
        this.messageSink = sink;
        if (sink == null || closed) {
            WebMessages.unregister(browser);
        } else {
            WebMessages.register(browser, this::deliverMessage);
        }
    }

    private void deliverMessage(String message) {
        java.util.function.Consumer<String> sink = messageSink;
        if (sink != null && !closed) {
            sink.accept(message);
        }
    }

    /** Sends JSON to the active page; see WebSurface.postMessage for the delivery contract. */
    public void postMessage(String json) {
        if (!closed) {
            runScript(PageMessages.receiveScript(json));
        }
    }

    // ---- Input -------------------------------------------------------------

    /**
     * The pointer moves, in browser pixels.
     *
     * <p>Pressed mouse buttons go along: without them Chromium sees a move with
     * the button released, and every selection breaks off after the first
     * pixel.
     */
    public void mouseMoved(int x, int y, int keyboardModifiers) {
        if (!closed) {
            noteInput();
            browser.moveMouse(x, y, buttons.modifiersWith(keyboardModifiers), false);
        }
    }

    /**
     * The pointer leaves the surface.
     *
     * <p>Without this notification the last button touched stays highlighted,
     * and a hover hint remains until someone enters the surface again.
     */
    public void mouseLeft(int x, int y) {
        if (closed) {
            return;
        }
        browser.moveMouse(x, y, buttons.modifiersWith(0), true);
        clicks.forget();
    }

    public void mousePressed(int x, int y, int minecraftButton, int keyboardModifiers,
                             long nowMillis) {
        if (closed) {
            return;
        }
        int cefButton = MouseButtons.toBrowserButton(minecraftButton);
        if (cefButton < 0) {
            return;
        }
        noteInput();
        int count = clicks.pressed(minecraftButton, x, y, nowMillis);
        buttons.press(minecraftButton);
        browser.clickMouse(x, y, true, cefButton, count,
                buttons.modifiersWith(keyboardModifiers));
    }

    public void mouseReleased(int x, int y, int minecraftButton, int keyboardModifiers) {
        if (closed) {
            return;
        }
        int cefButton = MouseButtons.toBrowserButton(minecraftButton);
        if (cefButton < 0) {
            return;
        }
        int count = clicks.released();
        // Clear the flag first, then report: on release the button is already
        // up, and Chromium reads the state afterwards.
        buttons.release(minecraftButton);
        browser.clickMouse(x, y, false, cefButton, count,
                buttons.modifiersWith(keyboardModifiers));
    }

    /**
     * The wheel turns.
     *
     * <p>The factor of three and the rounding to whole notches are taken from
     * MCEF and proven there: Minecraft reports fractional values, and unrounded
     * the scrolling feels sluggish. On macOS it stays unrounded, because
     * trackpads there deliver fine values that mean something.
     */
    public void mouseScrolled(int x, int y, double amount, int keyboardModifiers) {
        if (closed) {
            return;
        }
        noteInput();
        double scroll = amount < 0 ? Math.floor(amount) : Math.ceil(amount);
        browser.scrollMouse(x, y, scroll * 3.0,
                buttons.modifiersWith(keyboardModifiers));
    }

    /** A key goes down. The key code is GLFW's, not Minecraft's. */
    public void keyPressed(int glfwKeyCode, int scanCode, int modifiers) {
        if (!closed) {
            noteInput();
            browser.sendKey(glfwKeyCode, scanCode, modifiers, true);
        }
    }

    public void keyReleased(int glfwKeyCode, int scanCode, int modifiers) {
        if (!closed) {
            browser.sendKey(glfwKeyCode, scanCode, modifiers, false);
        }
    }

    /**
     * A character was typed.
     *
     * <p>This comes from Minecraft's own text-input path and is already the
     * finished character — with keyboard layout, shift key and everything the
     * operating system contributes to it. Rebuilding it from key codes would
     * mean guessing the player's keyboard layout.
     */
    public void charTyped(char typed, int modifiers) {
        if (!closed) {
            noteInput();
            browser.sendChar(typed, modifiers);
        }
    }

    /**
     * Whether the page is still loading.
     *
     * <p>Important for everything that calls into the page: before loading ends,
     * the document does not yet exist, and a call fizzles — without an error,
     * without a trace. That is exactly what happened here first: the background
     * was set once, too early, and stayed empty forever.
     */
    public boolean loading() {
        try {
            return closed || browser.isLoading() || !browser.hasDocument();
        } catch (Throwable notReady) {
            return true;
        }
    }

    /**
     * Runs one line of JavaScript in the page.
     *
     * <p>This low-level operation has no return value. In addition to runtime
     * updates such as changing an image without reloading, it carries the
     * escaped JSON script used by {@link #postMessage(String)}. API consumers
     * should use the message method instead of constructing scripts.
     */
    public void runScript(String code) {
        if (closed) {
            return;
        }
        // Via the main frame and not via the browser: the browser passes the
        // call on to the same frame, but only if it has one — and it has none as
        // long as nothing is loaded.
        org.cef.browser.CefFrame frame = browser.getMainFrame();
        if (frame == null) {
            return;
        }
        frame.executeJavaScript(code, "fn://runtime", 0);
    }

    /**
     * Remembers that something just went in.
     *
     * <p>Only the <b>first</b> keystroke of a sequence counts: whoever types ten
     * times before a frame comes waits once — and that one wait is what they
     * notice.
     */
    private void noteInput() {
        if (pendingInputNanos == 0) {
            pendingInputNanos = System.nanoTime();
        }
    }

    /**
     * Reports that Minecraft is now drawing.
     *
     * <p>The last missing section: between the finished upload and the frame the
     * player sees lies the wait for Minecraft's next pass. Without this call
     * every measurement would end at the graphics card instead of at the eye.
     *
     * <p><b>And an honesty about it:</b> what is measured is up to the
     * <i>beginning</i> of the drawing, not until the frame stands on the screen.
     * What still comes after — drawing, buffer swap, refresh — is not in here.
     */
    public void noteScreenFrame() {
        if (uploadEndNanos == 0) {
            return;
        }
        long now = System.nanoTime();
        toScreen.record(now - uploadEndNanos);
        if (paintedForInputNanos != 0) {
            totalLatency.record(now - paintedForInputNanos);
            paintedForInputNanos = 0;
        }
        uploadEndNanos = 0;
    }

    /** A — from the input until Chromium delivers a frame. */
    public dev.devpanda.bamboocef.web.measure.DurationSamples toPaint() {
        return toPaint;
    }

    /** C — from the end of the upload until Minecraft draws. */
    public dev.devpanda.bamboocef.web.measure.DurationSamples toScreen() {
        return toScreen;
    }

    /** The whole span. Must be roughly A + B + C. */
    public dev.devpanda.bamboocef.web.measure.DurationSamples totalLatency() {
        return totalLatency;
    }

    /** How long it takes from an input to the frame (A). */
    public dev.devpanda.bamboocef.web.measure.DurationSamples inputLatency() {
        return toPaint;
    }

    /** Resets all three sections. */
    public void resetLatency() {
        toPaint.reset();
        toScreen.reset();
        totalLatency.reset();
        paintGaps.reset();
        lastPaintNanos = 0;
        pendingInputNanos = 0;
        paintedForInputNanos = 0;
        uploadEndNanos = 0;
    }

    /** The cadence: gaps between two frames from Chromium. */
    public dev.devpanda.bamboocef.web.measure.DurationSamples paintGaps() {
        return paintGaps;
    }

    /** Tells Chromium whether it has the keyboard. */
    public void setFocused(boolean focused) {
        if (closed) {
            return;
        }
        browser.setFocus(focused);
        if (!focused) {
            // What was pressed at focus loss would otherwise stay so forever.
            buttons.forget();
            clicks.forget();
        }
    }

    // ---- Size, visibility, state ------------------------------------------

    public void resize(int width, int height) {
        if (!closed) {
            browser.resize(width, height);
        }
    }

    public void setVisibility(BrowserVisibility visibility) {
        pacer.setVisibility(visibility);
        // Tell Chromium itself that no one is looking. In this JCEF version
        // that is setWindowVisibility and not wasHidden — looked up in the jar,
        // not in the current JCEF source, which is newer than what MCEF brings
        // along.
        browser.setWindowVisibility(visibility.drawing());
    }

    public int textureId() {
        return texture.textureId();
    }

    public int popupTextureId() {
        return popupTexture.textureId();
    }

    public boolean popupVisible() {
        return popupShown && popupWidth > 0 && popupHeight > 0;
    }

    public int popupX() {
        return popupX;
    }

    public int popupY() {
        return popupY;
    }

    public int popupWidth() {
        return popupWidth;
    }

    public int popupHeight() {
        return popupHeight;
    }

    public int width() {
        return browser.browserWidth();
    }

    public int height() {
        return browser.browserHeight();
    }

    public GlTextureBackend texture() {
        return texture;
    }

    public GlTextureBackend popupTexture() {
        return popupTexture;
    }

    public long paints() {
        return paints;
    }

    public long popupPaints() {
        return popupPaints;
    }

    /** What the browser has currently loaded — for debugging. */
    public String currentUrl() {
        try {
            return browser.getURL();
        } catch (Throwable notReady) {
            return "(noch keine)";
        }
    }

    /** How long it took from opening to the first frame, in milliseconds. */
    public double millisToFirstPaint() {
        return firstPaintNanos == 0 ? -1.0
                : (firstPaintNanos - createdNanos) / 1_000_000.0;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        cursorSink = null;
        messageSink = null;
        WebMessages.unregister(browser);
        // From here on it no longer counts as open, but it is not yet finished:
        // close(true) is a request, the acknowledgement comes later.
        dev.devpanda.bamboocef.web.BrowserManager.closing(this);
        try {
            browser.close(true);
        } catch (Throwable broken) {
            // A browser that acts up while closing must not hold up the rest —
            // the textures have to go all the same.
        }
        texture.close();
        popupTexture.close();
    }
}
