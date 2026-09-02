package dev.devpanda.bamboocef.web.input;

import org.lwjgl.glfw.GLFW;

import java.awt.event.InputEvent;

/**
 * Umschalttasten von GLFW nach AWT.
 *
 * <p><b>Die {@code _DOWN_MASK}-Familie, nicht die alte.</b> AWT führt zwei
 * Sätze von Konstanten: {@code SHIFT_MASK} (1) aus der Frühzeit und
 * {@code SHIFT_DOWN_MASK} (64) von heute. Der native Teil von JCEF liest die
 * neuen — er ruft {@code getModifiersEx}. Die alten sehen fast genauso aus und
 * tragen andere Zahlen; wer sie verwechselt, bekommt keinen Fehler, sondern
 * Tastenkürzel, die nicht auslösen.
 *
 * <p><b>Zwei Wege, ein Unterschied — und der ist gemessen.</b> Ein Tastendruck
 * trägt seine Umschalttasten mit, ein getipptes Zeichen nicht. Der Grund steht
 * in {@code tools/runtime/probe/KeyProbe.java}:
 *
 * <pre>
 *   KEY_TYPED '@' mit Strg+Alt am Ereignis   → nichts kommt an
 *   KEY_TYPED '@' ohne Modifikatoren         → '@' steht im Feld
 * </pre>
 *
 * <p>Chromium deutet ein Zeichen mit Strg als Steuerzeichen und trägt es nicht
 * ein. Auf einer deutschen Tastatur ist das kein Randfall: <b>Windows meldet
 * AltGr als Strg + rechtes Alt</b>, und damit hinge an jedem {@code @},
 * {@code €}, {@code \}, {@code |} und {@code ~} ein Strg. Ohne diese
 * Unterscheidung fehlten auf einer deutschen Tastatur die Zeichen, die man in
 * einem Editor am nötigsten braucht.
 */
public final class AwtModifiers {

    /**
     * Die Umschalttasten für einen Tastendruck oder ein Loslassen.
     *
     * <p>Vollständig — daran hängen die Tastenkürzel. Gemessen: Strg+A, C, V,
     * S und F kommen als {@code keydown} mit {@code ctrlKey} an und lösen
     * kein {@code keypress} aus, genau wie in einem Browser.
     */
    public static int forKey(int glfwModifiers) {
        int awt = 0;
        if ((glfwModifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            awt |= InputEvent.SHIFT_DOWN_MASK;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            awt |= InputEvent.CTRL_DOWN_MASK;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_ALT) != 0) {
            awt |= InputEvent.ALT_DOWN_MASK;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_SUPER) != 0) {
            awt |= InputEvent.META_DOWN_MASK;
        }
        return awt;
    }

    /**
     * Die Umschalttasten für ein getipptes Zeichen.
     *
     * <p>Ohne Strg und ohne Alt. Umschalt bleibt: Es entscheidet nichts mehr —
     * das Zeichen steht ja schon fest —, aber es kostet nichts und hält das
     * Ereignis vollständig. Gemessen: Mit Umschalt am {@code KEY_TYPED}
     * kommen Großbuchstaben unverändert an.
     *
     * <p><b>Warum nicht schon am Tastendruck entschärft wird.</b> Ein Browser
     * meldet AltGr ebenfalls als Strg und Alt; das Ereignis so weiterzugeben
     * ist also nicht falsch, sondern gewöhnlich. Ob Monaco eine
     * AltGr-Kombination als Tastenkürzel abfängt, entscheidet die Handprüfung
     * aus B3e — nicht eine Vermutung hier. Fällt sie negativ aus, ist die
     * Gegenmaßnahme umrissen: Liegen Strg und rechtes Alt zusammen an, auch am
     * {@code KEY_PRESSED} beide weglassen.
     */
    public static int forCharacter(int glfwModifiers) {
        return forKey(glfwModifiers) & ~(InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
    }

    private AwtModifiers() {}
}
