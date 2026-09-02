package dev.devpanda.bamboocef.web.capture;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Das jeweils neueste Minecraft-Bild, für den Browser abholbereit.
 *
 * <p><b>Zwei Threads, eine Zuweisung.</b> Geschrieben wird im Render-Thread,
 * gelesen auf Chromiums Netzwerk-Thread. Weil ein {@link CapturedFrame}
 * unveränderlich ist, genügt eine einzige atomare Zuweisung — keine Sperre,
 * kein Kopieren, und ein Leser bekommt immer ein vollständiges Bild.
 *
 * <p><b>Genau eines, nicht mehr.</b> Ein Verlauf wäre nutzlos: Wer einen
 * Hintergrund anfordert, will den aktuellen. Ein älterer wäre falsch, und ihn
 * aufzubewahren kostete Speicher in Megabyte-Schritten.
 *
 * <p>Der Zähler läuft weiter, auch wenn niemand hinsieht — er ist das, was in
 * der Adresse steht und Chromiums Zwischenspeicher umgeht.
 */
public final class FrameStore {

    private final AtomicReference<CapturedFrame> latest = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();

    /** Legt ein neues Bild ab und gibt seine Nummer zurück. */
    public long put(byte[] bytes, String mimeType, int width, int height) {
        long next = generation.incrementAndGet();
        latest.set(new CapturedFrame(bytes, mimeType, width, height, next));
        return next;
    }

    /** Das neueste Bild, oder {@code null}, solange es keines gibt. */
    public CapturedFrame latest() {
        return latest.get();
    }

    /** Die Nummer der letzten Aufnahme. */
    public long generation() {
        return generation.get();
    }

    /** Ob überhaupt schon etwas da ist. */
    public boolean hasFrame() {
        return latest.get() != null;
    }

    /**
     * Vergisst das Bild.
     *
     * <p>Beim Schließen: Ein Bildschirm voller Pixel im Speicher zu behalten,
     * nachdem niemand mehr hinsieht, ist bei 1080p ein Megabyte für nichts.
     * Der Zähler bleibt stehen — eine Nummer, die schon einmal vergeben war,
     * darf nicht wiederkommen, sonst zeigt Chromium sein altes Bild.
     */
    public void clear() {
        latest.set(null);
    }
}
