import org.cef.CefApp;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefDisplayHandlerAdapter;

import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A4b — der Tastatur-Prüfstand.
 *
 * <p><b>Wozu.</b> Pfeiltasten und Ziffernblock teilen sich unter Windows die
 * unteren acht Bit des Scancodes; unterschieden werden sie über ein
 * Erweiterungs-Bit. Wer es verliert, bekommt für Pfeil-hoch die Acht des
 * Ziffernblocks — der Editor schriebe eine Ziffer, statt den Cursor zu
 * bewegen. Ob unser Weg das Bit richtig trägt, ist keine Frage der Herleitung,
 * sondern eine Messung: Die Seite sagt, was Chromium wirklich empfangen hat.
 *
 * <p><b>Deshalb steht dieser Prüfstand vor den Übersetzungstabellen im Mod.</b>
 * Ein Fehler hier hat eine Ursache. Derselbe Fehler im Spiel hat zwanzig.
 *
 * <p><b>Der Rückweg aus der Seite ist die Konsole, nicht der Debug-Port.</b>
 * Geplant war CDP. Die Konsole kann dasselbe und kostet zehn Zeilen statt
 * hundert: {@code CefDisplayHandler.onConsoleMessage} liefert den Text im
 * Pumpthread, im selben Takt wie alles andere. CDP bräuchte einen offenen
 * Port, einen WebSocket und einen zweiten Thread, der blockierend liest —
 * und der dürfte den Pumpthread nicht sein, sonst misst der Prüfstand
 * Stillstand. Der Debug-Port bleibt für das, wofür er gedacht war: Zusehen
 * von außen.
 *
 * <p>Aufruf: {@code run-probe.ps1 -Main KeyProbe}
 */
public final class KeyProbe {

    // ---- Was geprüft wird ---------------------------------------------------

    /**
     * Eine Taste.
     *
     * @param scancode      Windows-Scancode, unteres Byte
     * @param extended      ob Windows sie als erweitert führt
     * @param virtualKey    AWT-{@code VK_*}; auf Windows ungenutzt, wird
     *                      trotzdem mitgeschickt
     * @param expectedCode  was {@code KeyboardEvent.code} sein soll
     * @param expectedKey   was {@code KeyboardEvent.keyCode} sein soll
     */
    record KeyCase(String name, int scancode, boolean extended, int virtualKey,
            String expectedCode, int expectedKey) {}

    /** Die Fälle, an denen sich Pfeil und Ziffernblock scheiden, und ihre Nachbarn. */
    private static final List<KeyCase> KEYS = List.of(
            // Pfeile — erweitert. Ohne das Bit werden es Ziffernblocktasten.
            new KeyCase("ArrowUp", 0x48, true, KeyEvent.VK_UP, "ArrowUp", 38),
            new KeyCase("ArrowDown", 0x50, true, KeyEvent.VK_DOWN, "ArrowDown", 40),
            new KeyCase("ArrowLeft", 0x4B, true, KeyEvent.VK_LEFT, "ArrowLeft", 37),
            new KeyCase("ArrowRight", 0x4D, true, KeyEvent.VK_RIGHT, "ArrowRight", 39),

            // Dieselben Scancodes ohne das Bit: der Ziffernblock.
            new KeyCase("Numpad8", 0x48, false, KeyEvent.VK_NUMPAD8, "Numpad8", 104),
            new KeyCase("Numpad2", 0x50, false, KeyEvent.VK_NUMPAD2, "Numpad2", 98),
            new KeyCase("Numpad0", 0x52, false, KeyEvent.VK_NUMPAD0, "Numpad0", 96),

            // Navigation — alle erweitert.
            new KeyCase("Home", 0x47, true, KeyEvent.VK_HOME, "Home", 36),
            new KeyCase("End", 0x4F, true, KeyEvent.VK_END, "End", 35),
            new KeyCase("PageUp", 0x49, true, KeyEvent.VK_PAGE_UP, "PageUp", 33),
            new KeyCase("PageDown", 0x51, true, KeyEvent.VK_PAGE_DOWN, "PageDown", 34),
            new KeyCase("Insert", 0x52, true, KeyEvent.VK_INSERT, "Insert", 45),
            new KeyCase("Delete", 0x53, true, KeyEvent.VK_DELETE, "Delete", 46),

            // Eingabe gegen Ziffernblock-Eingabe: gleicher Tastencode, anderer code.
            new KeyCase("Enter", 0x1C, false, KeyEvent.VK_ENTER, "Enter", 13),
            new KeyCase("NumpadEnter", 0x1C, true, KeyEvent.VK_ENTER, "NumpadEnter", 13),

            // Links gegen rechts: gleicher Tastencode, anderer code.
            new KeyCase("ControlLeft", 0x1D, false, KeyEvent.VK_CONTROL, "ControlLeft", 17),
            new KeyCase("ControlRight", 0x1D, true, KeyEvent.VK_CONTROL, "ControlRight", 17),
            new KeyCase("AltLeft", 0x38, false, KeyEvent.VK_ALT, "AltLeft", 18),
            new KeyCase("AltRight", 0x38, true, KeyEvent.VK_ALT, "AltRight", 18),
            new KeyCase("ShiftLeft", 0x2A, false, KeyEvent.VK_SHIFT, "ShiftLeft", 16),
            new KeyCase("ShiftRight", 0x36, false, KeyEvent.VK_SHIFT, "ShiftRight", 16),

            // Das Erweiterungs-Bit für sich allein, an zwei Tasten, deren
            // beide Bedeutungen weit auseinanderliegen und nicht vom Layout
            // abhängen. Kommen hier zweimal dieselben Werte an, trägt das Bit
            // nicht — und alles darüber wäre Zufall.
            new KeyCase("PrintScreen (0x37 erweitert)", 0x37, true, KeyEvent.VK_PRINTSCREEN,
                    "PrintScreen", 44),
            new KeyCase("NumpadMultiply (0x37 einfach)", 0x37, false, KeyEvent.VK_MULTIPLY,
                    "NumpadMultiply", 106),
            new KeyCase("NumpadDivide (0x35 erweitert)", 0x35, true, KeyEvent.VK_DIVIDE,
                    "NumpadDivide", 111),
            new KeyCase("NumLock (0x45 einfach)", 0x45, false, KeyEvent.VK_NUM_LOCK, "NumLock", 144),

            // Steuertasten.
            new KeyCase("Backspace", 0x0E, false, KeyEvent.VK_BACK_SPACE, "Backspace", 8),
            new KeyCase("Escape", 0x01, false, KeyEvent.VK_ESCAPE, "Escape", 27),
            new KeyCase("Tab", 0x0F, false, KeyEvent.VK_TAB, "Tab", 9),
            new KeyCase("F1", 0x3B, false, KeyEvent.VK_F1, "F1", 112),
            new KeyCase("F12", 0x58, false, KeyEvent.VK_F12, "F12", 123));

    /** Ein Tastenkürzel mit Strg. */
    record ShortcutCase(String name, int scancode, int virtualKey, String expectedCode) {}

    private static final List<ShortcutCase> SHORTCUTS = List.of(
            new ShortcutCase("Strg+A", 0x1E, KeyEvent.VK_A, "KeyA"),
            new ShortcutCase("Strg+C", 0x2E, KeyEvent.VK_C, "KeyC"),
            new ShortcutCase("Strg+V", 0x2F, KeyEvent.VK_V, "KeyV"),
            new ShortcutCase("Strg+S", 0x1F, KeyEvent.VK_S, "KeyS"),
            new ShortcutCase("Strg+F", 0x21, KeyEvent.VK_F, "KeyF"));

    /** Ein Buchstabe mit bekanntem Scancode — für die Frage nach Dopplungen. */
    record LetterCase(char character, int scancode, int virtualKey) {}

    private static final List<LetterCase> LETTERS = List.of(
            new LetterCase('a', 0x1E, KeyEvent.VK_A),
            new LetterCase('b', 0x30, KeyEvent.VK_B),
            new LetterCase('c', 0x2E, KeyEvent.VK_C),
            new LetterCase('X', 0x2D, KeyEvent.VK_X),
            new LetterCase('Y', 0x15, KeyEvent.VK_Y),
            new LetterCase('Z', 0x2C, KeyEvent.VK_Z),
            new LetterCase('0', 0x0B, KeyEvent.VK_0),
            new LetterCase('1', 0x02, KeyEvent.VK_1),
            new LetterCase('2', 0x03, KeyEvent.VK_2));

    /**
     * Zeichen, die auf einer deutschen Tastatur nur über das Betriebssystem
     * entstehen. Sie kommen im Mod über {@code charTyped} und gehen deshalb
     * allein als KEY_TYPED hinaus — ohne Scancode, ohne Tastencode.
     */
    private static final String SPECIAL_TEXT = "äöüß@€\\|~";

    // ---- Zustand ------------------------------------------------------------

    private static final List<String> CONSOLE = new ArrayList<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static int passed = 0;
    private static int failed = 0;

    private static CefApp app;
    private static OsrBrowser browser;

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : null;
        if (url == null) {
            System.out.println("FEHLER: keine URL");
            System.exit(2);
        }

        Thread render = new Thread(null, () -> {
            try {
                run(url);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(3);
            }
        }, "render", 16L * 1024 * 1024);
        render.start();
        render.join();
    }

    private static void run(String url) throws Exception {
        if (!CefApp.startup(new String[] {})) {
            System.out.println("FEHLER: CefApp.startup fehlgeschlagen");
            System.exit(2);
        }
        CefApp.useCallingThread();

        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        settings.log_file = new java.io.File(System.getProperty("java.io.tmpdir"),
                "fn-keyprobe-cef.log").getAbsolutePath();
        settings.cache_path = null;

        app = CefApp.getInstance(settings);
        CefClient client = app.createClient();
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser b, CefSettings.LogSeverity level,
                    String message, String source, int line) {
                CONSOLE.add(message);
                return true; // wir haben uns gekümmert; nichts weiterreichen
            }
        });

        CefBrowserSettings browserSettings = new CefBrowserSettings();
        browserSettings.windowless_frame_rate = 60;

        browser = new OsrBrowser(client, url, false, browserSettings,
                (popup, dirty, buffer, w, h) -> { /* Bilder zählen hier nicht */ });
        browser.setCloseAllowed();
        browser.createImmediately();
        browser.resize(1280, 720);

        // Warten, bis die Seite steht. Sie meldet sich selbst; ein fester
        // Zeitraum wäre entweder zu kurz oder verschenkte Zeit.
        pump(200);
        js("console.log('BEREIT')");
        if (!awaitConsole("BEREIT", 5000)) {
            System.out.println("FEHLER: die Seite meldet sich nicht");
            shutdown();
            System.exit(4);
        }
        browser.setFocus(true);
        pump(30);

        for (KeyCase c : KEYS) {
            checkKey(c);
        }
        for (ShortcutCase c : SHORTCUTS) {
            checkShortcut(c);
        }
        checkLetters();
        checkSpecialText();
        checkAltGr();
        checkWheel();

        System.out.println();
        System.out.println("{");
        System.out.println("  \"faelle\": [");
        System.out.println(String.join(",\n", REPORT));
        System.out.println("  ],");
        System.out.println("  \"bestanden\": " + passed + ",");
        System.out.println("  \"gescheitert\": " + failed);
        System.out.println("}");

        shutdown();
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---- Die einzelnen Prüfungen --------------------------------------------

    /** Eine Taste herunter und herauf, und was die Seite davon gesehen hat. */
    private static void checkKey(KeyCase c) {
        clear();
        press(c.scancode(), c.extended(), c.virtualKey(), 0);
        release(c.scancode(), c.extended(), c.virtualKey(), 0);
        List<String[]> log = report();

        String[] down = firstOf(log, "down");
        String gotCode = down == null ? "" : down[2];
        String gotKey = down == null ? "" : down[3];

        // **Geurteilt wird über keyCode, nicht über code.**
        //
        // Gemessen: Über diesen Weg ist `code` in der Seite immer leer, und
        // zwar für jede Taste. Das ist keine Eigenart unseres Patches — CEFs
        // eigener Beispielcode füllt native_key_code genauso
        // (osr_window_win.cc: `event.native_key_code = lParam`), und Chromium
        // leitet daraus keinen physischen Tastencode ab, wenn das Ereignis
        // nicht aus einer echten Fensternachricht stammt.
        //
        // Für den Editor ist das folgenlos: Monaco unterscheidet Pfeil-hoch
        // von der Acht des Ziffernblocks über keyCode (38 gegen 104), und
        // genau der kommt an. `code` wird trotzdem mitgeschrieben — was heute
        // leer ist, soll auffallen, wenn es das eines Tages nicht mehr ist.
        boolean ok = down != null && String.valueOf(c.expectedKey()).equals(gotKey);

        // Ein Loslassen muss es auch geben — sonst bleibt die Taste in
        // Chromiums Sicht gedrückt, und das nächste Kürzel geht schief.
        boolean hasUp = firstOf(log, "up") != null;

        // location wird mitgeschrieben, aber nicht bewertet: Sie ist die
        // zweite Sicht auf dieselbe Frage (0 normal, 1 links, 2 rechts,
        // 3 Ziffernblock) und hilft, einen Fehlschlag zu deuten, ohne dass
        // hier eine Erwartung geraten werden müsste.
        String gotLocation = down == null ? "" : down[6];

        note(c.name(),
                "{\"keyCode\": " + c.expectedKey() + ", \"waere_code\": \"" + c.expectedCode() + "\"}",
                "{\"keyCode\": " + (gotKey.isEmpty() ? "null" : gotKey)
                        + ", \"code\": \"" + gotCode + "\""
                        + ", \"location\": " + (gotLocation.isEmpty() ? "null" : gotLocation)
                        + ", \"up\": " + hasUp + "}",
                ok && hasUp);
    }

    /** Strg gedrückt halten, Taste drücken: keydown mit ctrlKey, kein keypress. */
    private static void checkShortcut(ShortcutCase c) {
        clear();
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        press(0x1D, false, KeyEvent.VK_CONTROL, ctrl);
        press(c.scancode(), false, c.virtualKey(), ctrl);
        release(c.scancode(), false, c.virtualKey(), ctrl);
        release(0x1D, false, KeyEvent.VK_CONTROL, 0);
        List<String[]> log = report();

        // Gesucht wird das keydown des Buchstabens, nicht das von Strg selbst.
        // Erkannt wird es am keyCode — `code` ist über diesen Weg leer.
        String letter = String.valueOf(c.virtualKey());
        boolean withCtrl = false;
        boolean seen = false;
        boolean anyPress = false;
        for (String[] e : log) {
            if ("down".equals(e[0]) && letter.equals(e[3])) {
                seen = true;
                if (e[5].contains("C")) {
                    withCtrl = true;
                }
            }
            if ("press".equals(e[0])) {
                anyPress = true;
            }
        }
        note(c.name(),
                "{\"keydown\": " + letter + ", \"ctrlKey\": true, \"keypress\": false}",
                "{\"keydown\": " + seen + ", \"ctrlKey\": " + withCtrl + ", \"keypress\": " + anyPress
                        + "}",
                seen && withCtrl && !anyPress);
    }

    /**
     * Buchstaben und Ziffern, jeweils vollständig: Drücken, Zeichen, Loslassen.
     *
     * <p>Genau so schickt der Mod sie später. Die Frage dahinter ist die nach
     * Dopplungen: Wenn schon KEY_PRESSED ein Zeichen einträgt und KEY_TYPED
     * noch eines, steht am Ende jeder Buchstabe zweimal da.
     */
    private static void checkLetters() {
        clear();
        StringBuilder expected = new StringBuilder();
        for (LetterCase l : LETTERS) {
            int shift = Character.isUpperCase(l.character()) ? InputEvent.SHIFT_DOWN_MASK : 0;
            if (shift != 0) {
                press(0x2A, false, KeyEvent.VK_SHIFT, shift);
            }
            press(l.scancode(), false, l.virtualKey(), shift);
            type(l.character(), shift);
            release(l.scancode(), false, l.virtualKey(), shift);
            if (shift != 0) {
                release(0x2A, false, KeyEvent.VK_SHIFT, 0);
            }
            expected.append(l.character());
        }
        String got = text();
        note("Text abcXYZ012", quote(expected.toString()), quote(got),
                expected.toString().equals(got));
    }

    /** Zeichen, die allein über KEY_TYPED hereinkommen. */
    private static void checkSpecialText() {
        clear();
        for (char ch : SPECIAL_TEXT.toCharArray()) {
            type(ch, 0);
        }
        String got = text();
        note("Sonderzeichen", quote(SPECIAL_TEXT), quote(got), SPECIAL_TEXT.equals(got));
    }

    /**
     * AltGr, wie Windows es meldet: rechtes Alt zusammen mit Strg.
     *
     * <p>Zwei Fragen in einer: Kommt das Zeichen an, und deutet die Seite das
     * begleitende KEY_PRESSED als Tastenkürzel? Beides entscheidet, ob im
     * Editor ein {@code @} erscheint oder ein Menü aufgeht.
     */
    private static void checkAltGr() {
        clear();
        int both = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        // Windows schiebt vor das rechte Alt ein *linkes* Strg — nicht
        // erweitert. Genau so kommt es bei GLFW an, und genau so muss es hier
        // stehen, sonst prüft der Fall etwas anderes als das Layout tut.
        press(0x1D, false, KeyEvent.VK_CONTROL, InputEvent.CTRL_DOWN_MASK);
        press(0x38, true, KeyEvent.VK_ALT, both);
        press(0x10, false, KeyEvent.VK_Q, both); // Q trägt auf deutschem Layout das @
        type('@', both);
        release(0x10, false, KeyEvent.VK_Q, both);
        release(0x38, true, KeyEvent.VK_ALT, InputEvent.CTRL_DOWN_MASK);
        release(0x1D, false, KeyEvent.VK_CONTROL, 0);

        // Gegenprobe: Hier ist der leere Text das erwartete Ergebnis. Chromium
        // deutet ein Zeichen mit Strg als Steuerzeichen und trägt es nicht ein.
        // Käme das @ hier durch, wäre die Gegenmaßnahme unten überflüssig — und
        // dieser Fall würde es sagen.
        String got = text();
        note("Gegenprobe: AltGr @ mit Modifikatoren am Zeichen", quote(""), quote(got),
                got.isEmpty());

        // Dieselbe Folge, aber das Zeichen ohne Modifikatoren. Das ist die
        // Gegenmaßnahme, die der Plan für B3c vorsieht — hier wird gemessen,
        // ob sie nötig ist und ob sie reicht.
        clear();
        press(0x1D, false, KeyEvent.VK_CONTROL, InputEvent.CTRL_DOWN_MASK);
        press(0x38, true, KeyEvent.VK_ALT, both);
        press(0x10, false, KeyEvent.VK_Q, both);
        type('@', 0);
        release(0x10, false, KeyEvent.VK_Q, both);
        release(0x38, true, KeyEvent.VK_ALT, InputEvent.CTRL_DOWN_MASK);
        release(0x1D, false, KeyEvent.VK_CONTROL, 0);

        String stripped = text();
        note("AltGr @ ohne Modifikatoren am Zeichen", quote("@"), quote(stripped),
                "@".equals(stripped));
    }

    /**
     * Das Vorzeichen des Rades — gemessen, bevor eine Klasse es festschreibt.
     *
     * <p>Drei Zählweisen treffen hier aufeinander, und keine zwei stimmen
     * überein: GLFW meldet nach oben ein <b>positives</b> Delta, AWT nach oben
     * ein <b>negatives</b> {@code wheelRotation}, und Chromiums
     * {@code WheelEvent.deltaY} ist nach oben ebenfalls negativ. Zwei
     * Vorzeichenwechsel, die sich fast aufheben — fast.
     *
     * <p>Geprüft wird deshalb die ganze Strecke: ein AWT-Ereignis mit
     * {@code wheelRotation = -1} hinein, das {@code deltaY} der Seite heraus.
     * Was hier steht, entscheidet, ob {@code AwtMouseEvents} das Vorzeichen
     * dreht oder nicht.
     */
    private static void checkWheel() {
        clear();
        // Minecraft meldet beim Drehen nach oben ein positives Delta. Wird es
        // unverändert als wheelRotation weitergereicht, muss in der Seite ein
        // negatives deltaY ankommen — so heißt „nach oben" im DOM.
        double positiveRotation = wheelDelta(1);
        double negativeRotation = wheelDelta(-1);

        boolean ok = positiveRotation < 0 && negativeRotation > 0;
        note("Rad: Vorzeichen geht unverändert durch",
                "{\"wheelRotation +1\": \"deltaY negativ (hinauf)\","
                        + " \"wheelRotation -1\": \"deltaY positiv (hinunter)\"}",
                "{\"wheelRotation +1\": " + positiveRotation
                        + ", \"wheelRotation -1\": " + negativeRotation + "}",
                ok);
    }

    private static double wheelDelta(int wheelRotation) {
        clear();
        browser.wheel(new java.awt.event.MouseWheelEvent(SOURCE,
                java.awt.event.MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                100, 100, 0, false,
                java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
                3,                 // Einheiten je Rastung, wie im Mod
                wheelRotation));
        pump(20);
        CONSOLE.clear();
        js("console.log('RAD\\n' + window.radBericht())");
        if (!awaitConsole("RAD", 2000)) {
            return 0;
        }
        String message = lastConsole("RAD");
        int nl = message.indexOf('\n');
        String body = nl < 0 ? "" : message.substring(nl + 1).trim();
        if (body.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(body.split("\t")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- Die Werkzeuge ------------------------------------------------------

    /**
     * Der Absender für Mausereignisse.
     *
     * <p>AWT wirft bei {@code null}. Eine unmittelbare Unterklasse von
     * {@link java.awt.Component} zieht kein Toolkit heran und braucht keinen
     * Peer — niemand zeichnet sie, sie ist nur ein Absender.
     */
    private static final java.awt.Component SOURCE = new java.awt.Component() {};

    private static void press(int scancode, boolean extended, int virtualKey, int modifiers) {
        browser.key(KeyEvent.KEY_PRESSED, modifiers, KeyEvent.CHAR_UNDEFINED, scancode, extended,
                virtualKey);
        pump(4);
    }

    private static void release(int scancode, boolean extended, int virtualKey, int modifiers) {
        browser.key(KeyEvent.KEY_RELEASED, modifiers, KeyEvent.CHAR_UNDEFINED, scancode, extended,
                virtualKey);
        pump(4);
    }

    private static void type(char character, int modifiers) {
        browser.key(KeyEvent.KEY_TYPED, modifiers, character, 0, false, 0);
        pump(4);
    }

    private static void clear() {
        CONSOLE.clear();
        js("window.leeren(); console.log('LEER')");
        // Bestätigen lassen statt eine Frist abzuwarten: executeJavaScript geht
        // an den Renderprozess und kommt an, wann es ankommt.
        if (!awaitConsole("LEER", 2000)) {
            System.out.println("WARNUNG: die Seite bestätigt das Leeren nicht");
        }
    }

    /** Holt das Protokoll als Zeilen mit Tabulatoren — kein JSON, kein Parser. */
    private static List<String[]> report() {
        pump(10);
        CONSOLE.clear();
        js("console.log('LOG\\n' + window.bericht())");
        List<String[]> rows = new ArrayList<>();
        if (!awaitConsole("LOG", 2000)) {
            return rows;
        }
        String message = lastConsole("LOG");
        for (String line : message.split("\n")) {
            if (line.isEmpty() || line.equals("LOG")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            // Die Seite schreibt immer sieben Felder, auch leere. Was weniger
            // hat, ist keine Protokollzeile, sondern etwas anderes.
            if (parts.length == 7) {
                rows.add(parts);
            }
        }
        return rows;
    }

    private static String text() {
        pump(10);
        CONSOLE.clear();
        js("console.log('TEXT\\n' + window.text())");
        if (!awaitConsole("TEXT", 2000)) {
            return "<keine Antwort>";
        }
        String message = lastConsole("TEXT");
        int nl = message.indexOf('\n');
        return nl < 0 ? "" : message.substring(nl + 1);
    }

    private static String[] firstOf(List<String[]> log, String kind) {
        for (String[] row : log) {
            if (kind.equals(row[0])) {
                return row;
            }
        }
        return null;
    }

    private static void js(String script) {
        browser.executeJavaScript(script, browser.getURL(), 0);
    }

    /** Pumpt, bis eine Konsolenmeldung mit dem Präfix da ist. */
    private static boolean awaitConsole(String prefix, int millis) {
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            for (String message : CONSOLE) {
                if (message.startsWith(prefix)) {
                    return true;
                }
            }
            pump(1);
        }
        return false;
    }

    private static String lastConsole(String prefix) {
        for (int i = CONSOLE.size() - 1; i >= 0; i--) {
            if (CONSOLE.get(i).startsWith(prefix)) {
                return CONSOLE.get(i);
            }
        }
        return "";
    }

    /** Eine Anzahl Runden der Nachrichtenschleife, mit einer Millisekunde Pause. */
    private static void pump(int rounds) {
        for (int i = 0; i < rounds; i++) {
            app.doMessageLoopWorkNow();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void note(String name, String expected, String got, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        REPORT.add("    {\"name\": " + quote(name)
                + ", \"erwartet\": " + expected
                + ", \"empfangen\": " + got
                + ", \"ok\": " + ok + "}");
        System.out.printf("%-28s %s%n", name, ok ? "ok" : "GESCHEITERT   erwartet " + expected
                + "   empfangen " + got);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static void shutdown() {
        browser.close(true);
        pump(200);
        app.dispose();
        pump(200);
    }

    private KeyProbe() {}
}
