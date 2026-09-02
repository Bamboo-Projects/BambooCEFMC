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
 * Der Kanal von der Seite zur Mod.
 *
 * <p><b>Ein Router am geteilten Client.</b> Alle Browser teilen sich einen
 * {@code CefClient}; ein einziger {@link CefMessageRouter} daran bedient
 * jeden von ihnen. Chromium reicht bei einer Anfrage den Browser mit, der sie
 * stellte — {@link MessageRouting} findet daraus die richtige Sitzung.
 *
 * <p><b>Zwei Namen in der Seite.</b> Chromium legt {@code window.fnQuery}
 * selbst an — das ist die rohe Form mit {@code onSuccess} und
 * {@code onFailure}. Für den Alltag spritzt ein Load-Handler zusätzlich
 * {@code window.fnSend(nachricht)} ein: eine Zeile, die eine Zeichenkette
 * oder ein Objekt (als JSON) schickt und sich um den Rückweg nicht kümmert.
 *
 * <p>Die {@code onQuery}-Rückrufe kommen im Renderthread an, dort, wo auch
 * {@code onPaint} pumpt — der Empfänger einer Nachricht läuft also im selben
 * Thread wie das Zeichnen.
 */
public final class WebMessages {

    private static final Logger LOG = LogUtils.getLogger();

    /** Der Name, unter dem Chromium die rohe Abfragefunktion anlegt. */
    private static final String QUERY = "fnQuery";
    private static final String CANCEL = "fnQueryCancel";

    /**
     * Die bequeme Form, in jede Seite eingespritzt.
     *
     * <p>Nimmt eine Zeichenkette oder ein Objekt; ein Objekt geht als JSON.
     * Der Rückweg ist leer — wer eine Antwort braucht, nimmt {@code fnQuery}.
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
     * Hängt Router und Load-Handler an den Client — einmal je Sitzung.
     *
     * <p>Neben {@link WebConsole#attach()} und aus demselben Grund dort
     * gerufen: sobald die Laufzeit steht, für jede Seite, nicht erst für die
     * mit einem Editor.
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

    /** Meldet den Empfänger einer Sitzung an, unter der Kennung ihres Browsers. */
    static void register(CefBrowser browser, Consumer<String> sink) {
        routing.register(browser, sink);
    }

    static void unregister(CefBrowser browser) {
        routing.unregister(browser);
    }

    /** Für den Prüfstand: alle Empfänger vergessen. */
    static void clear() {
        routing.clear();
    }

    private static final class Handler extends CefMessageRouterHandlerAdapter {
        @Override
        public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                               String request, boolean persistent, CefQueryCallback callback) {
            boolean handled = routing.dispatch(browser, request);
            // Immer bestätigen: Ein offener Rückruf bliebe in Chromium hängen.
            // „Genommen" heißt hier nur, dass jemand zugehört hat.
            callback.success(handled ? "1" : "0");
            return true;
        }
    }
}
