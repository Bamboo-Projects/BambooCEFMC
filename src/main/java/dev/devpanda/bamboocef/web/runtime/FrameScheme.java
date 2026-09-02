package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.capture.CapturedFrame;
import dev.devpanda.bamboocef.web.capture.FrameStore;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

/**
 * Liefert das aktuelle Minecraft-Bild an Chromium aus.
 *
 * <p><b>Aus dem Speicher, nicht von der Platte.</b> Der naheliegende Weg wäre
 * eine Datei je Aufnahme und ein {@code file://} davor. Bei zehn Bildern in der
 * Sekunde wären das zehn Dateien, die geschrieben, gelesen und wieder gelöscht
 * werden müssten — für Daten, die schon im Speicher liegen.
 *
 * <p><b>Dieser Aufruf kommt nicht vom Render-Thread.</b> Chromium holt seine
 * Inhalte auf einem eigenen Netzwerk-Thread ab. Deshalb steht hier nichts, was
 * OpenGL anfasst, und deshalb ist das Bild unveränderlich: Der Ablageort gibt
 * eine fertige Momentaufnahme heraus, und was danach aufgenommen wird, ändert
 * sie nicht mehr.
 *
 * <p><b>Kein Zwischenspeichern.</b> Chromium würde eine Adresse, die es schon
 * kennt, aus seinem eigenen Speicher beantworten — und der Hintergrund bliebe
 * für immer der erste. Dagegen helfen zwei Dinge, und beide stehen hier, weil
 * eines allein sich schwer nachweisen lässt: eine Nummer in der Adresse und
 * ein {@code Cache-Control: no-store}.
 */
public abstract class FrameScheme implements CefResourceHandler {

    // <b>Abstrakt, obwohl hier alles steht.</b> Die Schnittstelle
    // CefResourceHandler ist zwischen dem CinemaMod-Fork und upstream
    // gewachsen: Upstream verlangt zusätzlich skip(long, LongRef,
    // CefResourceSkipCallback), und beide Parametertypen gibt es im Fork
    // nicht. Eine Datei kann das nicht für beide Wege erfüllen, also endet
    // die gemeinsame Fassung eine Ableitung früher — der Rest steht je
    // Quellverzeichnis in FrameHandler.

    /** Der Name des Schemas — {@code mc://frame/current}. */
    public static final String SCHEME = "mc";

    /** Der Rechnername in der Adresse — im Text, nicht in der Anmeldung. */
    public static final String DOMAIN = "frame";



    private final FrameStore store;

    private CapturedFrame serving;
    private int offset;

    public FrameScheme(FrameStore store) {
        this.store = store;
    }

    /**
     * Nimmt die Anfrage an — oder lehnt sie ab, wenn es nichts zu zeigen gibt.
     *
     * <p>Welche Nummer in der Adresse steht, ist gleichgültig: Gefragt wird
     * immer nach dem neuesten Bild. Die Nummer ist nur dazu da, dass Chromium
     * die Adresse für eine neue hält.
     */
    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        serving = store.latest();
        offset = 0;
        if (serving == null) {
            callback.cancel();
            return false;
        }
        callback.Continue();
        return true;
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength,
                                   StringRef redirectUrl) {
        if (serving == null) {
            response.setStatus(404);
            responseLength.set(0);
            return;
        }
        response.setStatus(200);
        response.setMimeType(serving.mimeType());
        // Ohne das holt Chromium dieselbe Adresse nie wieder — und mit einer
        // Nummer in der Adresse fiele es nicht einmal auf, weil jede Nummer
        // neu ist. Beides zusammen ist Absicht.
        response.setHeaderByName("Cache-Control", "no-store, no-cache, must-revalidate", true);
        responseLength.set(serving.byteCount());
    }

    @Override
    public boolean readResponse(byte[] out, int bytesToRead, IntRef bytesRead,
                                CefCallback callback) {
        if (serving == null || offset >= serving.byteCount()) {
            bytesRead.set(0);
            return false;
        }
        int count = Math.min(bytesToRead, serving.byteCount() - offset);
        System.arraycopy(serving.bytes(), offset, out, 0, count);
        offset += count;
        bytesRead.set(count);
        return true;
    }

    @Override
    public void cancel() {
        serving = null;
        offset = 0;
    }
}
