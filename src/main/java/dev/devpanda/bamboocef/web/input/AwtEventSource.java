package dev.devpanda.bamboocef.web.input;

import java.awt.Component;

/**
 * The one component that is never shown and serves only as a sender.
 *
 * <p><b>Why it exists at all.</b> AWT requires a source for a mouse event;
 * {@code null} throws an {@code IllegalArgumentException}. For the keyboard the
 * question does not arise — there no AWT objects go out, only values via
 * {@code sendKeyEventRaw}. That keeps this component provably confined to the
 * mouse.
 *
 * <p><b>Why not a Canvas and not a Frame.</b> Both pull in the toolkit when
 * created and acquire a peer. A direct subclass of {@link Component} does not —
 * it has no peer and needs none, because nobody draws it.
 *
 * <p><b>It is at the same time the answer to {@code getUIComponent()}.</b>
 * Measured on the test bench: {@code CefClient.onTakeFocus} calls
 * {@code getParent()} on it without checking — with {@code null} the first Tab
 * in the editor ends in a {@code NullPointerException}. This component has no
 * parent, the focus traversal ends quietly, and it is meant to be nothing more.
 *
 * <p><b>{@code java.awt.headless} is not set.</b> A mod that changes a global
 * system property can pull the ground out from under other mods. A peerless
 * component is independent of it anyway.
 */
public final class AwtEventSource {

    public static final Component SOURCE = new Component() {};

    private AwtEventSource() {}
}
