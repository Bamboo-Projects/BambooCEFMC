package dev.devpanda.bamboocef.web.runtime;

/**
 * Wer Chromiums Nachrichtenschleife dreht — in dieser Fassung: wir selbst.
 *
 * <p>Ohne MCEF gibt es keinen Mixin, der pumpt. Der Takt kommt deshalb aus
 * dem Renderereignis: einmal je Bild, im Renderthread, <b>vor</b> dem
 * Zeichnen. Vorher und nicht nachher, damit ein Bild, das Chromium gerade
 * geliefert hat, noch in dieselbe Textur wandert, die dieses Bild benutzt —
 * sonst hinkte die Seite um ein Bild hinterher.
 *
 * <p>Solange nichts hochgefahren ist, ist der Aufruf eine Nullprüfung. Es
 * gibt keinen Grund, das Hochfahren an das Zeichnen zu hängen: Ein Browser
 * entsteht, wenn jemand einen sehen will.
 */
public final class WebPump {

    /** Einmal je Bild, aus dem Renderthread. */
    public static void frame() {
        FnCefRuntime.pump();
    }

    private WebPump() {
    }
}
