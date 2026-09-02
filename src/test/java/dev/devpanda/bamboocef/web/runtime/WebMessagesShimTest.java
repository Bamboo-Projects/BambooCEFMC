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
 * Der Text, den jede Seite eingespritzt bekommt, ist heil.
 *
 * <p><b>Warum ein Prüflauf für eine Zeichenkette.</b> Der Shim ist JavaScript
 * in einem Java-String über drei Zeilen zusammengesetzt. Ein fehlendes
 * Klammernpaar fällt sonst erst im Spiel auf, als eine Seite, die stumm
 * bleibt. Hier fällt es beim Bauen auf.
 *
 * <p>Er schreibt den Text zusätzlich nach {@code build/shim-dump.js}, damit
 * ein Werkzeug außerhalb — node — die eigentliche JavaScript-Gültigkeit
 * prüfen kann, an genau dem Wert, der auch im Spiel läuft.
 */
class WebMessagesShimTest {

    @Test
    @DisplayName("Der Shim ruft fnQuery, legt fnSend an und ist geklammert")
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
