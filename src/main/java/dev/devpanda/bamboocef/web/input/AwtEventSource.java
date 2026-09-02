package dev.devpanda.bamboocef.web.input;

import java.awt.Component;

/**
 * Die eine Komponente, die nie gezeigt wird und nur als Absender dient.
 *
 * <p><b>Warum es sie überhaupt gibt.</b> AWT verlangt für ein Mausereignis
 * eine Quelle; {@code null} wirft eine {@code IllegalArgumentException}. Für
 * die Tastatur entfällt die Frage — dort gehen keine AWT-Objekte hinaus,
 * sondern Werte über {@code sendKeyEventRaw}. Damit bleibt diese Komponente
 * beweisbar auf die Maus beschränkt.
 *
 * <p><b>Warum kein Canvas und kein Frame.</b> Beide ziehen bei ihrer Erzeugung
 * das Toolkit heran und legen sich einen Peer zu. Eine unmittelbare
 * Unterklasse von {@link Component} tut das nicht — sie hat keinen Peer und
 * braucht keinen, weil niemand sie zeichnet.
 *
 * <p><b>Sie ist zugleich die Antwort auf {@code getUIComponent()}.</b>
 * Gemessen im Prüfstand: {@code CefClient.onTakeFocus} ruft darauf
 * {@code getParent()}, ohne zu prüfen — mit {@code null} endet der erste
 * Tabulator im Editor in einer {@code NullPointerException}. Diese Komponente
 * hat keinen Vater, die Fokuswanderung endet still, und mehr soll sie nicht.
 *
 * <p><b>{@code java.awt.headless} wird nicht gesetzt.</b> Eine Mod, die eine
 * globale Systemeigenschaft verändert, kann anderen Mods den Boden wegziehen.
 * Eine peerlose Komponente ist davon ohnehin unabhängig.
 */
public final class AwtEventSource {

    public static final Component SOURCE = new Component() {};

    private AwtEventSource() {}
}
