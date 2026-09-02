package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.capture.FrameStore;
import org.cef.callback.CefCallback;
import org.cef.callback.CefResourceReadCallback;
import org.cef.callback.CefResourceSkipCallback;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.LongRef;
import org.cef.network.CefRequest;

/**
 * Der Bearbeiter für {@code mc://frame} — Fassung für upstream java-cef.
 *
 * <p>Upstream verlangt von einem {@code CefResourceHandler} drei Methoden
 * mehr als der CinemaMod-Fork: {@code open}, {@code read} und {@code skip}.
 * Ihre Parametertypen — {@code BoolRef}, {@code CefResourceReadCallback},
 * {@code LongRef}, {@code CefResourceSkipCallback} — gibt es im Fork nicht,
 * deshalb stehen sie hier und nicht in {@link FrameScheme}.
 *
 * <p><b>Sie tun nichts Eigenes.</b> Upstream beschreibt für {@code open} und
 * {@code read} ausdrücklich einen Rückweg auf die älteren Methoden, und genau
 * den nehmen sie. Damit bleibt die ganze Arbeit — welches Bild, welche Köpfe,
 * welche Bytes — in der gemeinsamen Fassung, und diese Datei ist nur die
 * Übersetzung zwischen zwei Schnittstellenständen.
 */
public final class FrameHandler extends FrameScheme {

    public FrameHandler(FrameStore store) {
        super(store);
    }

    /**
     * Öffnet den Antwortstrom.
     *
     * <p>Nach upstreams eigener Beschreibung: {@code handleRequest} auf falsch
     * und falsch zurückgeben heißt „nimm {@code processRequest}".
     */
    @Override
    public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
        handleRequest.set(false);
        return false;
    }

    /**
     * Liest Antwortdaten.
     *
     * <p>Nach upstreams eigener Beschreibung: {@code bytesRead} auf minus eins
     * und falsch zurückgeben heißt „nimm {@code readResponse}".
     */
    @Override
    public boolean read(byte[] dataOut, int bytesToRead, IntRef bytesRead,
            CefResourceReadCallback callback) {
        bytesRead.set(-1);
        return false;
    }

    /**
     * Überspringt Daten, wenn ein {@code Range}-Kopf danach fragt.
     *
     * <p><b>Es gibt nichts zu überspringen.</b> Was hier ausgeliefert wird,
     * ist ein einzelnes Bild, das jedes Mal frisch entsteht und ausdrücklich
     * nicht zwischengespeichert werden soll — ein Teilbereich davon ergibt
     * keinen Sinn. Für diesen Fall sieht upstream keinen Rückweg vor; ein
     * negativer Wert ist Chromiums Art, „das geht nicht" zu hören, und
     * {@code -2} steht für {@code ERR_FAILED}.
     */
    @Override
    public boolean skip(long bytesToSkip, LongRef bytesSkipped,
            CefResourceSkipCallback callback) {
        bytesSkipped.set(-2);
        return false;
    }
}
