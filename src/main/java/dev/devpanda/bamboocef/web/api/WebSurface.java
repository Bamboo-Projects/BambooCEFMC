package dev.devpanda.bamboocef.web.api;

/**
 * A living web surface.
 *
 * <p>What it shows lies in a texture; what happens on it comes in through the
 * input methods. All of it belongs to the render thread — Chromium's session
 * lives there, and a call from elsewhere is not a chance error but one that
 * shows up at the very first frame.
 *
 * <p><b>No type from {@code org.cef} appears here</b>, and that is the whole
 * intent of the interface: whoever builds in a surface does not compile
 * against Chromium, and the implementation underneath may change.
 */
public interface WebSurface extends AutoCloseable {

    /**
     * The texture the current frame lies in.
     *
     * <p>A raw OpenGL id. For the world render path that is not enough — there
     * a {@code ResourceLocation} is needed, and that is available via
     * {@link #textureLocation()}.
     *
     * @return the id, or {@code 0} as long as there is no frame yet
     */
    int textureId();

    /**
     * The same texture, registered with the texture manager.
     *
     * <p>The path for everything that draws via {@code RenderType}. The address
     * belongs to the surface and is unregistered on close.
     */
    net.minecraft.resources.ResourceLocation textureLocation();

    int width();

    int height();

    /** Changes the size. Chromium creates a new texture in the process. */
    void resize(int width, int height);

    /** What the surface is called — in the log and in Chromium's list. */
    String name();

    /** Is it still running? */
    boolean alive();

    // ---- Input --------------------------------------------------------------

    /**
     * The pointer is at this point.
     *
     * @param x browser pixels, not screen points
     */
    void mouseMoved(int x, int y, int modifiers);

    /** The pointer leaves the surface — otherwise the last button stays lit. */
    void mouseLeft(int x, int y);

    /** @param button Minecraft's numbering: 0 left, 1 right, 2 middle */
    void mousePressed(int x, int y, int button, int modifiers);

    void mouseReleased(int x, int y, int button, int modifiers);

    void mouseScrolled(int x, int y, double amount, int modifiers);

    /**
     * A key goes down.
     *
     * @return true if the surface took it. False means: the filter let it
     *         through, and the caller should leave it to the game.
     */
    boolean keyPressed(int glfwKey, int scanCode, int modifiers);

    boolean keyReleased(int glfwKey, int scanCode, int modifiers);

    /**
     * A typed character.
     *
     * <p>Separate from the keys, because only the operating system knows what
     * an umlaut is on a German keyboard.
     *
     * @return true if the surface took it
     */
    boolean charTyped(char typed, int modifiers);

    /** Does the surface have keyboard focus? */
    void setFocused(boolean focused);

    /**
     * Called when Chromium wants a different mouse cursor.
     *
     * <p>The number is the ordinal from {@code cef_cursor_type_t}. Whoever
     * registers nothing gets the game's cursor.
     */
    void onCursor(java.util.function.IntConsumer sink);

    /**
     * Receive messages from the page.
     *
     * <p>In the page: {@code window.fnSend("...")} or
     * {@code window.fnSend({...})} — a string or an object as JSON. The
     * receiver runs on the render thread. {@code null} unregisters it.
     *
     * <p>This closes the loop: a quick menu can not only get keys but also
     * give its choice back.
     */
    void onMessage(java.util.function.Consumer<String> handler);

    /**
     * Sends a JSON value to {@code window.fnReceive(value)} on the active page.
     *
     * <p>Call on the render thread. Delivery is asynchronous and is not queued:
     * closed surfaces, missing main frames or missing page handlers discard the
     * message. Install the handler and signal readiness with {@code fnSend}
     * before sending initial state. Invalid JSON is discarded by the page's
     * {@code JSON.parse}; payload text is never executed as JavaScript.
     *
     * <p>The default keeps existing third-party implementations compatible and
     * reports that they do not support this operation. BambooCEF surfaces
     * implement it.
     *
     * @param json JSON text, including objects, arrays or primitive values
     * @throws IllegalArgumentException if {@code json} is {@code null} on an open surface
     * @throws UnsupportedOperationException if this implementation does not support messages
     */
    default void postMessage(String json) {
        throw new UnsupportedOperationException("This surface cannot receive messages");
    }

    @Override
    void close();
}
