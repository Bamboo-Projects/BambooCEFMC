package dev.devpanda.bamboocef.web.runtime;

import com.google.gson.Gson;

/** JSON transport to a page, kept independent of Chromium and Minecraft for testing. */
final class PageMessages {

    private static final Gson JSON = new Gson();

    private PageMessages() {
    }

    static String receiveScript(String json) {
        if (json == null) {
            throw new IllegalArgumentException("Message must be JSON text, not null");
        }
        // Serialize the entire text as a string literal. Never concatenate raw
        // JSON into executable code: strings, line separators and HTML-looking
        // values must remain data. Native JSON.parse also rejects non-JSON
        // extensions accepted by some Java JSON parsers.
        return "(function(){var value;try{value=JSON.parse(" + JSON.toJson(json)
                + ");}catch(invalid){return;}"
                + "if(typeof window.fnReceive==='function'){window.fnReceive(value);}})();";
    }
}
