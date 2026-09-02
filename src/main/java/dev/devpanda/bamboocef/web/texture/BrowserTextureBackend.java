package dev.devpanda.bamboocef.web.texture;

import dev.devpanda.bamboocef.web.frame.BrowserFrame;

/**
 * Wohin Bilder gehen.
 *
 * <p><b>Was hier fehlt, ist Absicht:</b> kein Texturname, kein
 * {@code ByteBuffer}, kein Handle. Ein Unterbau, der ein Bild entgegennimmt,
 * ist austauschbar; einer, der einen OpenGL-Namen herausgibt, legt den
 * ganzen Weg fest.
 *
 * <p>Wie das Bild sichtbar wird, entscheidet der, der das Backend gebaut hat —
 * er kennt beide Enden. Diese Schnittstelle kennt nur, dass es eines gibt.
 */
public interface BrowserTextureBackend extends AutoCloseable {

    /**
     * Übernimmt ein Bild.
     *
     * <p>Das Bild gehört danach weiterhin dem Aufrufer: Das Backend kopiert,
     * was es braucht, und hält nichts fest. So kann der Aufrufer es
     * unmittelbar danach wiederverwenden.
     *
     * <p>Muss dort gerufen werden, wo der Zeichenkontext gilt — bei Minecraft
     * ist das der Render-Thread. Ein Backend darf sich darauf verlassen.
     */
    void upload(BrowserFrame frame);

    /** Gibt alles frei. Mehrfaches Schließen ist erlaubt. */
    @Override
    void close();
}
