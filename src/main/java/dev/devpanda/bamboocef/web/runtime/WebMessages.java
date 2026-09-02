package dev.devpanda.bamboocef.web.runtime;

import com.mojang.logging.LogUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.slf4j.Logger;

import java.util.function.Consumer;

/**
 * The channel from the page to the mod.
 *
 * <p><b>One router on the shared client.</b> All browsers share one
 * {@code CefClient}; a single {@link CefMessageRouter} on it serves every one
 * of them. On a query Chromium passes along the browser that made it —
 * {@link MessageRouting} finds the right session from that.
 *
 * <p><b>Two names in the page.</b> Chromium creates {@code window.fnQuery}
 * itself — that is the raw form with {@code onSuccess} and {@code onFailure}.
 * For everyday use a load handler additionally injects
 * {@code window.fnSend(message)}: a line that sends a string or an object (as
 * JSON) and does not concern itself with the return path.
 *
 * <p>The {@code onQuery} callbacks arrive on the render thread, where
 * {@code onPaint} also pumps — the receiver of a message therefore runs on the
 * same thread as the painting.
 */
public final class WebMessages {

    private static final Logger LOG = LogUtils.getLogger();

    /** The name under which Chromium creates the raw query function. */
    private static final String QUERY = "fnQuery";
    private static final String CANCEL = "fnQueryCancel";

    /**
     * The convenient form, injected into every page.
     *
     * <p>Takes a string or an object; an object goes as JSON. The return path is
     * empty — whoever needs an answer uses {@code fnQuery}.
     */
    static final String SEND_SHIM =
            "if(!window.fnSend){window.fnSend=function(m){try{window." + QUERY
                    + "({request:(typeof m==='string'?m:JSON.stringify(m)),"
                    + "onSuccess:function(){},onFailure:function(){}});}catch(e){}};}";

    private static final MessageRouting routing = new MessageRouting();
    private static boolean attached;

    private WebMessages() {
    }

    /**
     * Attaches router and load handler to the client — once per session.
     *
     * <p>Called next to {@link WebConsole#attach()} and for the same reason: as
     * soon as the runtime is up, for every page, not only for the one with an
     * editor.
     */
    public static synchronized void attach() {
        if (attached) {
            return;
        }
        try {
            CefMessageRouter.CefMessageRouterConfig config =
                    new CefMessageRouter.CefMessageRouterConfig(QUERY, CANCEL);
            CefMessageRouter router = CefMessageRouter.create(config);
            router.addHandler(new Handler(), true);
            CefHost.client().addMessageRouter(router);
            CefHost.client().addLoadHandler(new CefLoadHandlerAdapter() {
                @Override
                public void onLoadStart(CefBrowser browser, CefFrame frame,
                                        org.cef.network.CefRequest.TransitionType transitionType) {
                    inject(frame);
                }

                @Override
                public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                    inject(frame);
                }

                private void inject(CefFrame frame) {
                    if (frame != null && frame.isMain()) {
                        frame.executeJavaScript(SEND_SHIM, frame.getURL(), 0);
                    }
                }
            });
            attached = true;
        } catch (Throwable broken) {
            LOG.warn("Der Kanal von der Seite zur Mod ließ sich nicht anlegen", broken);
        }
    }

    /** Registers a session's receiver, under the identity of its browser. */
    static void register(CefBrowser browser, Consumer<String> sink) {
        routing.register(browser, sink);
    }

    static void unregister(CefBrowser browser) {
        routing.unregister(browser);
    }

    /** For the test bench: forget all receivers. */
    static void clear() {
        routing.clear();
    }

    private static final class Handler extends CefMessageRouterHandlerAdapter {
        @Override
        public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                               String request, boolean persistent, CefQueryCallback callback) {
            boolean handled = routing.dispatch(browser, request);
            // Always acknowledge: an open callback would hang in Chromium.
            // "Taken" here only means that someone listened.
            callback.success(handled ? "1" : "0");
            return true;
        }
    }
}
