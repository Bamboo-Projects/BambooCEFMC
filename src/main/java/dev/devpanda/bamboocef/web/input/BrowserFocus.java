package dev.devpanda.bamboocef.web.input;

/**
 * Who gets the keyboard: Minecraft or the browser.
 *
 * <p><b>Without this distinction an editor is unusable.</b> Whoever types an
 * {@code e} in program text does not want the inventory. Whoever types a
 * {@code 1} does not want to switch tools. And whoever types {@code w} does not
 * want to start walking. A screen does intercept much already — but as soon as
 * a surface in the world is meant to accept text, there is no longer a screen
 * that would do it for us. That is why the state lives here and not in the
 * screen.
 *
 * <p><b>On the Escape key.</b> The obvious assumption that Escape always
 * belongs to Minecraft is wrong: a dropdown closes with it, a menu likewise,
 * and an editor cancels its autocompletion with it. Whoever intercepts Escape
 * takes from the web interface a key it needs itself.
 *
 * <p>The decision for the first screen: <b>Escape goes to the browser</b>, and
 * for the way back there is a key of its own. That is the less likely of two
 * inconveniences — learning one key is easier than working in an editor with no
 * cancel. As soon as the interface itself can say "I did not need this Escape",
 * the key can move back.
 */
public enum BrowserFocus {

    /** Everything takes the usual path. The browser sees no key. */
    MINECRAFT,

    /**
     * The browser gets keys and text; Minecraft sees nothing of it.
     *
     * <p>Not even as a second use: an event the browser gets must not then
     * count once more as game control.
     */
    BROWSER;

    /** Should this key press go to the browser? */
    public boolean routesKeyboard() {
        return this == BROWSER;
    }

    /**
     * May Minecraft still see this key press?
     *
     * <p>Always the opposite of {@link #routesKeyboard()} — both answers live
     * here together so that nobody accidentally permits both.
     */
    public boolean routesGameplay() {
        return this == MINECRAFT;
    }
}
