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
 * The handler for {@code mc://frame} — variant for upstream java-cef.
 *
 * <p>Upstream demands three more methods from a {@code CefResourceHandler}
 * than the CinemaMod fork: {@code open}, {@code read} and {@code skip}. Their
 * parameter types — {@code BoolRef}, {@code CefResourceReadCallback},
 * {@code LongRef}, {@code CefResourceSkipCallback} — do not exist in the fork,
 * which is why they live here and not in {@link FrameScheme}.
 *
 * <p><b>They do nothing of their own.</b> For {@code open} and {@code read}
 * upstream explicitly describes a fallback onto the older methods, and that is
 * exactly what these take. So all the actual work — which image, which
 * headers, which bytes — stays in the shared variant, and this file is only
 * the translation between two interface generations.
 */
public final class FrameHandler extends FrameScheme {

    public FrameHandler(FrameStore store) {
        super(store);
    }

    /**
     * Opens the response stream.
     *
     * <p>Per upstream's own description: returning {@code handleRequest} false
     * and false means "use {@code processRequest}".
     */
    @Override
    public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
        handleRequest.set(false);
        return false;
    }

    /**
     * Reads response data.
     *
     * <p>Per upstream's own description: returning {@code bytesRead} minus one
     * and false means "use {@code readResponse}".
     */
    @Override
    public boolean read(byte[] dataOut, int bytesToRead, IntRef bytesRead,
            CefResourceReadCallback callback) {
        bytesRead.set(-1);
        return false;
    }

    /**
     * Skips data when a {@code Range} header asks for it.
     *
     * <p><b>There is nothing to skip.</b> What is served here is a single
     * image that comes into being fresh each time and is explicitly not to be
     * cached — a sub-range of it makes no sense. For this case upstream
     * provides no fallback; a negative value is Chromium's way of hearing
     * "that cannot be done", and {@code -2} stands for {@code ERR_FAILED}.
     */
    @Override
    public boolean skip(long bytesToSkip, LongRef bytesSkipped,
            CefResourceSkipCallback callback) {
        bytesSkipped.set(-2);
        return false;
    }
}
