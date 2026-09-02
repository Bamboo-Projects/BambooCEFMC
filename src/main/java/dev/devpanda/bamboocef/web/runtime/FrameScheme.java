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
 * Serves the current Minecraft frame to Chromium.
 *
 * <p><b>From memory, not from disk.</b> The obvious way would be one file per
 * capture with a {@code file://} in front. At ten frames per second that would
 * be ten files that would have to be written, read and deleted again — for data
 * that already sits in memory.
 *
 * <p><b>This call does not come from the render thread.</b> Chromium fetches
 * its content on its own network thread. That is why nothing here touches
 * OpenGL, and why the frame is immutable: the store hands out a finished
 * snapshot, and whatever is captured afterwards no longer changes it.
 *
 * <p><b>No caching.</b> Chromium would answer an address it already knows out of
 * its own cache — and the background would stay the first one forever. Two
 * things guard against that, and both stand here because one alone is hard to
 * prove: a number in the address and a {@code Cache-Control: no-store}.
 */
public abstract class FrameScheme implements CefResourceHandler {

    // <b>Abstract, even though everything is here.</b> The CefResourceHandler
    // interface has diverged between the CinemaMod fork and upstream: upstream
    // additionally requires skip(long, LongRef, CefResourceSkipCallback), and
    // neither parameter type exists in the fork. One file cannot satisfy that
    // for both paths, so the shared version ends one derivation earlier — the
    // rest sits per source directory in FrameHandler.

    /** The name of the scheme — {@code mc://frame/current}. */
    public static final String SCHEME = "mc";

    /** The host name in the address — in the text, not in the registration. */
    public static final String DOMAIN = "frame";



    private final FrameStore store;

    private CapturedFrame serving;
    private int offset;

    public FrameScheme(FrameStore store) {
        this.store = store;
    }

    /**
     * Accepts the request — or declines it if there is nothing to show.
     *
     * <p>Which number is in the address is immaterial: the newest frame is
     * always what is asked for. The number is only there so that Chromium takes
     * the address for a new one.
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
        // Without this Chromium never fetches the same address again — and with
        // a number in the address it would not even notice, because every number
        // is new. Both together are intentional.
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
