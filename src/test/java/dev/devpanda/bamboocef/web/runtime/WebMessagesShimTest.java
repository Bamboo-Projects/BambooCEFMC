package dev.devpanda.bamboocef.web.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text that every page gets injected is intact.
 *
 * <p><b>Why a test run for a character string.</b> The shim is JavaScript
 * assembled in a Java string over three lines. A missing
 * bracket pair otherwise shows up only in the game, as a page that stays
 * silent. Here it shows up at build time.
 *
 * <p>It additionally writes the text to {@code build/shim-dump.js}, so that
 * a tool outside — node — can check the actual JavaScript validity,
 * on exactly the value that also runs in the game.
 */
class WebMessagesShimTest {

    @Test
    @DisplayName("The shim calls fnQuery, sets up fnSend and is bracketed")
    void theShimIsWellFormed() throws IOException {
        String shim = WebMessages.SEND_SHIM;

        assertTrue(shim.contains("window.fnQuery("), "er muss die rohe Abfrage rufen");
        assertTrue(shim.contains("window.fnSend"), "er muss fnSend anlegen");
        assertTrue(shim.contains("JSON.stringify"), "ein Objekt geht als JSON");
        assertEquals(count(shim, '{'), count(shim, '}'), "geschweifte Klammern paarig");
        assertEquals(count(shim, '('), count(shim, ')'), "runde Klammern paarig");

        Path dump = Path.of("build", "shim-dump.js");
        Files.createDirectories(dump.getParent());
        Files.writeString(dump, shim, StandardCharsets.UTF_8);
    }

    private static long count(String text, char c) {
        return text.chars().filter(ch -> ch == c).count();
    }
}
