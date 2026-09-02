package dev.devpanda.bamboocef.web.api;

/**
 * Whom the input belongs to while an overlay is open.
 *
 * <p><b>Three levels, not two.</b> "Focus, yes or no" does not know the most
 * common case: a quick menu that takes Escape and the arrow keys while the
 * player keeps walking and looking around. For that the mouse has to stay
 * with the game and only part of the keyboard go to the page.
 */
public enum OverlayFocus {

    /** Only to be seen. Every input belongs to the game. */
    NONE,

    /**
     * Keys according to the surface's filter; the mouse stays with the game.
     *
     * <p>The player keeps looking around, keeps walking, and what the filter
     * does not take reaches the game as if the overlay were not there.
     */
    KEYBOARD,

    /**
     * Mouse and keys. The pointer is free and points at the surface.
     *
     * <p>The player stands still meanwhile — as in every interface with a free
     * pointer; NeoForge disables the game's key bindings as soon as a screen
     * is open. A click beside the surface, Escape without a filter, or F10
     * give the mouse back.
     */
    MOUSE;

    /** Does the surface get keys at this level? */
    public boolean takesKeys() {
        return this != NONE;
    }

    /** Does the surface get the mouse at this level? */
    public boolean takesMouse() {
        return this == MOUSE;
    }
}
