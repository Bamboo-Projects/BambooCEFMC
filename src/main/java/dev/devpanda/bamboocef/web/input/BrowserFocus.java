package dev.devpanda.bamboocef.web.input;

/**
 * Wer die Tastatur bekommt: Minecraft oder der Browser.
 *
 * <p><b>Ohne diese Unterscheidung ist ein Editor unbenutzbar.</b> Wer in einem
 * Programmtext ein {@code e} tippt, will kein Inventar. Wer eine {@code 1}
 * tippt, will keinen Werkzeugwechsel. Und wer {@code w} tippt, will nicht
 * loslaufen. Ein Screen fängt zwar schon vieles ab — aber sobald eine Fläche
 * in der Welt Text annehmen soll, gibt es keinen Screen mehr, der das für uns
 * täte. Deshalb steht der Zustand hier und nicht im Screen.
 *
 * <p><b>Zur Escape-Taste.</b> Die naheliegende Annahme, Escape gehöre immer
 * Minecraft, ist falsch: Ein Auswahlfeld schließt sich damit, ein Menü ebenso,
 * und ein Editor bricht damit die Vervollständigung ab. Wer Escape abfängt,
 * nimmt der Weboberfläche eine Taste, die sie selbst braucht.
 *
 * <p>Die Entscheidung für den ersten Screen: <b>Escape geht an den Browser</b>,
 * und für den Rückweg gibt es eine eigene Taste. Das ist die
 * unwahrscheinlichere von zwei Unbequemlichkeiten — eine Taste lernen ist
 * leichter, als in einem Editor ohne Abbrechen zu arbeiten. Sobald die
 * Oberfläche selbst sagen kann „diese Escape habe ich nicht gebraucht", kann
 * die Taste zurückwandern.
 */
public enum BrowserFocus {

    /** Alles geht den gewohnten Weg. Der Browser sieht keine Taste. */
    MINECRAFT,

    /**
     * Der Browser bekommt Tasten und Text; Minecraft sieht nichts davon.
     *
     * <p>Auch nicht als Zweitverwertung: Ein Ereignis, das der Browser
     * bekommt, darf danach nicht noch einmal als Spielsteuerung gelten.
     */
    BROWSER;

    /** Soll dieser Tastendruck an den Browser gehen? */
    public boolean routesKeyboard() {
        return this == BROWSER;
    }

    /**
     * Darf Minecraft diesen Tastendruck noch sehen?
     *
     * <p>Immer das Gegenteil von {@link #routesKeyboard()} — beide Antworten
     * stehen hier zusammen, damit niemand versehentlich beides zulässt.
     */
    public boolean routesGameplay() {
        return this == MINECRAFT;
    }
}
