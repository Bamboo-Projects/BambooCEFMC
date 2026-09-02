package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.capture.FrameStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers with Chromium who answers {@code mc://frame/…}.
 *
 * <p><b>After initialization, not before — and that is documented.</b> MCEF
 * registers its own {@code mod://} immediately after {@code CefUtil.init()}, in
 * the same call in which it brings Chromium up. A scheme handler can therefore
 * be registered afterwards; that is not a guess but the path MCEF itself takes.
 * And it also holds for an image <b>inside</b> a page that comes from elsewhere
 * — that too has been demonstrated.
 *
 * <p><b>One store for the whole session, and that is no convenience.</b>
 * Chromium accepts one registration per scheme, and what is deposited there
 * holds until the end. Whoever later registers a second store gets a friendly
 * "yes" and is nonetheless never asked: the factory entered first stays put.
 *
 * <p>That is exactly what happened here and cost dearly. The proof registered
 * its own store, cleared it on close — and the measurement afterwards never got
 * a frame, because the emptied store still hung on the registration. The bug
 * looked like an address problem and was none; two corrections to the address
 * form came to nothing before the cause stood revealed. What finally showed it
 * was Chromium's own console.
 *
 * <p>That is why the store exists <b>here</b> and only once. Whoever wants to
 * put a frame into it gets it with {@link #store()}.
 */
public final class FrameSchemes {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/FrameSchemes");

    /** The one store. It lives as long as Chromium's registration. */
    private static final FrameStore STORE = new FrameStore();

    /**
     * The same address without a number — for the cache probe.
     *
     * <p>Chromium then sees the same address every time. Whether it reloads
     * anyway is decided solely by the {@code Cache-Control: no-store} header
     * that the store sends along.
     */
    public static final String SCHEME_URL_WITHOUT_GENERATION =
            FrameScheme.SCHEME + "://" + FrameScheme.DOMAIN + "/current";

    private static boolean registered;

    private FrameSchemes() {
    }

    /** The store into which everyone puts their frame. */
    public static FrameStore store() {
        return STORE;
    }

    /**
     * Registers the store, if not already done.
     *
     * @return whether Chromium accepted the registration
     */
    public static synchronized boolean register() {
        if (registered) {
            return true;
        }
        try {
            // While we are at it: Chromium's console into the log. What goes
            // wrong in the page should not be known only to the page — without
            // this line the bug above would never have been found.
            WebConsole.attach();
            boolean accepted = CefHost.app().registerSchemeHandlerFactory(
                    FrameScheme.SCHEME, FrameScheme.DOMAIN,
                    (browser, frame, url, request) -> new FrameHandler(STORE));
            registered = accepted;
            LOG.info("Schema {}://{} angemeldet: {}",
                    FrameScheme.SCHEME, FrameScheme.DOMAIN, accepted);
            return accepted;
        } catch (Throwable broken) {
            LOG.warn("Schema {}://{} ließ sich nicht anmelden",
                    FrameScheme.SCHEME, FrameScheme.DOMAIN, broken);
            return false;
        }
    }

    /**
     * The address for the newest frame, with a number against the cache.
     *
     * <p>Which number is in it is immaterial to the store — it always hands out
     * the newest frame. It is only there so that Chromium takes the address for
     * a new one.
     */
    public static String urlFor(long generation) {
        return FrameScheme.SCHEME + "://" + FrameScheme.DOMAIN + "/current?v=" + generation;
    }

    public static boolean isRegistered() {
        return registered;
    }
}
