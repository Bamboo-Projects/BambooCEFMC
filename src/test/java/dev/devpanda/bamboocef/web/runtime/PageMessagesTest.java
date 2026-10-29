package dev.devpanda.bamboocef.web.runtime;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageMessagesTest {

    private static final Gson JSON = new Gson();

    @Test
    void preservesEditorTextWithoutTurningItIntoCode() {
        String source = "quotes \"' `; backslash \\; newline\n"
                + "</script><script>globalThis.pwned=true</script>"
                + "\");globalThis.pwned=true;//";
        assertRoundTrip(JSON.toJson(source));
    }

    @Test
    void preservesUnicodeAndEscapesJavascriptLineSeparators() {
        String input = JSON.toJson("Bambus ä 日本語 🎋\u2028\u2029");
        String script = PageMessages.receiveScript(input);
        assertFalse(script.contains("\u2028"));
        assertFalse(script.contains("\u2029"));
        assertRoundTrip(input);
    }

    @Test
    void transportsEveryJsonValueType() {
        for (String input : List.of("null", "true", "false", "42", "1.5e-2",
                "\"hello\"", "[1,null,true]", "{\"source\":\"EVERY 20 TICKS DO\\nEND\"}")) {
            assertRoundTrip(input);
        }
    }

    @Test
    void rejectsJavaNull() {
        assertThrows(IllegalArgumentException.class, () -> PageMessages.receiveScript(null));
    }

    /** Run the emitted file with node to exercise actual JavaScript parsing and delivery. */
    @Test
    void writesExecutableBridgeChecks() throws IOException {
        StringBuilder checks = new StringBuilder("const assert=require('node:assert/strict');\n"
                + "globalThis.window={}; let received=[];\n"
                + "window.fnReceive=value=>received.push(value);\n");
        for (String value : List.of("null", "true", "123", "[1,2]",
                "{\"source\":\"ä 日本語 🎋\\nEND\"}",
                JSON.toJson("\");globalThis.pwned=true;//</script>\u2028\u2029"),
                "{\"__proto__\":{\"polluted\":true}}")) {
            checks.append("received=[];\n").append(PageMessages.receiveScript(value))
                    .append("\nassert.deepEqual(received,[JSON.parse(")
                    .append(JSON.toJson(value)).append(")]);\n");
        }
        checks.append("received=[];\n");
        for (String invalid : List.of("", "undefined", "NaN", "{bad:true}",
                "{\"a\":1,}", "/* comment */true", "true;globalThis.pwned=true",
                "\"raw\nnewline\"", "null null")) {
            checks.append(PageMessages.receiveScript(invalid)).append('\n');
        }
        checks.append("assert.equal(received.length,0);\n"
                + "assert.equal(globalThis.pwned,undefined);\n"
                + "assert.equal({}.polluted,undefined);\n"
                + "delete window.fnReceive;\n");
        checks.append(PageMessages.receiveScript("{\"ignored\":true}"));
        checks.append("\nwindow.fnReceive=42;\n");
        checks.append(PageMessages.receiveScript("null"));
        checks.append("\nconsole.log('PageMessages JavaScript delivery checks passed');\n");
        Path dump = Path.of("build", "page-messages-check.js");
        Files.createDirectories(dump.getParent());
        Files.writeString(dump, checks, StandardCharsets.UTF_8);
    }

    private static void assertRoundTrip(String input) {
        String script = PageMessages.receiveScript(input);
        String literal = script.substring(script.indexOf("JSON.parse(") + "JSON.parse(".length(),
                script.indexOf(");}catch(invalid)"));
        assertEquals(input, JSON.fromJson(literal, String.class));
    }
}
