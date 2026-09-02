package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.frame.BorrowedFrame;
import dev.devpanda.bamboocef.web.frame.DirtyRegion;
import dev.devpanda.bamboocef.web.input.AwtModifiers;
import dev.devpanda.bamboocef.web.input.AwtMouseEvents;
import dev.devpanda.bamboocef.web.input.GlfwKeys;
import dev.devpanda.bamboocef.web.input.GlfwScancodes;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowser_N;
import org.cef.browser.CefPaintEvent;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Ein Browser ohne Fenster, dessen Bilder und Ereignisse wir selbst annehmen.
 *
 * <p><b>Diese Fassung erbt von {@link CefBrowser_N} und nicht von
 * {@code CefBrowserOsr}.</b> Upstreams {@code CefBrowserOsr} ist nicht die
 * zusammengestrichene Klasse des CinemaMod-Forks: Ihr Konstruktor baut eine
 * AWT-{@code GLCanvas} über JOGL auf, malt in deren GL-Kontext und hält das
 * Rechteck der Ansicht privat. In einem Spiel, das seinen eigenen GL-Kontext
 * führt und JOGL nicht mitliefert, ist davon nichts brauchbar.
 *
 * <p>Was bleibt, ist die Ebene darunter. {@code CefBrowser_N} bringt
 * Erzeugung, Größenänderung und Eingabe mit; {@link CefRenderHandler} ist eine
 * Schnittstelle, die jeder selbst erfüllen kann. Der Prüfstand
 * {@code tools/runtime/probe/OsrBrowser.java} hat genau diese Bauform gemessen,
 * bevor sie hier gelandet ist.
 *
 * <p><b>Die Eingabe muss hier stehen und kann nirgends sonst.</b>
 * {@code sendKeyEventRaw}, {@code sendMouseEvent} und
 * {@code sendMouseWheelEvent} sind auf {@code CefBrowser_N}
 * {@code protected}. Nur wer erbt, kommt heran. Die Entscheidungen — welcher
 * Klick der wievielte war, welche Taste an wen geht — stehen deshalb
 * woanders, in Klassen ohne Chromium darin; hier steht nur die Übersetzung
 * und die Weitergabe.
 *
 * <p><b>Der Puffer wird nur geliehen.</b> Was hinausgeht, ist ein
 * {@link BorrowedFrame} — gültig, solange dieser Aufruf läuft, und mit dem
 * besitzenden Bild absichtlich nicht verwandt.
 */
public class FnBrowser extends CefBrowser_N implements CefRenderHandler {

    /**
     * Was ein Browser meldet.
     *
     * <p>Alles hier kommt im Renderthread an, solange die Runtime Chromiums
     * Nachrichtenschleife dort pumpt — außer {@link #cursorChanged(int)}, das
     * auch von Chromiums eigenen Threads kommen kann.
     */
    public interface Events {

        /** Ein neues Bild der Hauptansicht. Der Puffer gilt nur in diesem Aufruf. */
        void frame(BorrowedFrame frame);

        /**
         * Ein neues Bild eines aufgeklappten Feldes.
         *
         * <p>Der Puffer ist so groß wie das Feld, nicht wie die Seite, und die
         * geänderten Bereiche zählen von der Ecke des Feldes.
         */
        void popupFrame(BorrowedFrame frame);

        /** Ein Feld klappt auf oder zu. */
        void popupShown(boolean shown);

        /** Wo das Feld liegt, in Browser-Pixeln der Hauptansicht. */
        void popupPlaced(int x, int y, int width, int height);

        /** Chromium hätte gern einen anderen Mauszeiger. */
        void cursorChanged(int cefCursorType);

        /**
         * Chromium hat das Schließen bestätigt.
         *
         * <p>Kommt nach {@code close(true)} und aus der Nachrichtenschleife —
         * also später, nicht sofort. Wer beim Herunterfahren darauf wartet,
         * muss pumpen.
         */
        void browserClosed();
    }

    /**
     * Ein Bauteil, das nie gezeigt wird.
     *
     * <p><b>Warum nicht null.</b> {@code CefClient.onTakeFocus} ruft darauf
     * {@code getParent()}, ohne zu prüfen — mit null endet der erste Tabulator
     * im Editor in einer {@code NullPointerException}. Gemessen im Prüfstand,
     * nicht vermutet. Die peerlose Komponente aus dem Eingabe-Adapter tut es:
     * Sie hat keinen Vater, und die Fokuswanderung endet still.
     */
    private static final Component NO_SURFACE =
            dev.devpanda.bamboocef.web.input.AwtEventSource.SOURCE;

    private final Events events;
    private final boolean transparent;

    /**
     * Eine laufende Nummer je Browser.
     *
     * <p>Sie steht in jeder Spurzeile. Ohne sie lässt sich bei zwei
     * gleichzeitig offenen Browsern nicht sagen, welcher gerade was tat — und
     * genau das ist die Frage bei der Meldung, die ohne Stapel kommt.
     */
    private static final java.util.concurrent.atomic.AtomicInteger COUNTER =
            new java.util.concurrent.atomic.AtomicInteger();

    private final int id = COUNTER.incrementAndGet();

    private boolean firstPaintSeen;

    /**
     * Schreibt einen Schritt im Leben dieses Browsers mit.
     *
     * <p><b>Wozu.</b> Im neuen Weg erscheint bei jeder Browsererzeugung ein
     * bis sieben Mal {@code Exception in thread "Render thread"} — ohne
     * Stapel, ohne Meldung, ohne dass einer unserer Rückrufe etwas meldet.
     * Solange sich die Herkunft nicht fassen lässt, hilft nur, den Zeitpunkt
     * einzugrenzen: Wenn die Kopfzeilen im Protokoll immer zwischen denselben
     * beiden Schritten stehen, ist das die halbe Antwort.
     *
     * <p>Hinter {@code -Dfn.cef.trace=true}, weil es im Alltag Lärm wäre.
     */
    private void trace(String step) {
        if (FnCefRuntime.TRACE) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "Spur Browser {}: {} — Thread {}", id, step,
                    Thread.currentThread().getName());
        }
    }

    // Umgeht CEF-Fehler 1437: Ein Rechteck der Größe null lässt Chromium gar
    // nicht erst anfangen zu malen.
    private final Rectangle viewRect = new Rectangle(0, 0, 1, 1);

    /**
     * Öffnet einen Browser am Client dieser Fassung.
     *
     * <p>Die Fabrik steht hier und nicht beim Aufrufer, weil es diese Klasse
     * zweimal gibt — einmal auf MCEF, einmal auf der eigenen
     * Laufzeitumgebung. Woher der Client kommt, weiß nur die jeweilige
     * Fassung; alles davor bleibt davon unberührt.
     */
    public static FnBrowser open(String url, boolean transparent, Events events) {
        // Sechzig Bilder je Sekunde, gesetzt über CefBrowserSettings.
        //
        // Der Umgebungsvariablen-Behelf des Proof-of-Concept ist damit
        // erledigt: Upstream kennt das Feld, der Fork kannte es nicht.
        CefBrowserSettings settings = new CefBrowserSettings();
        settings.windowless_frame_rate = 60;
        return new FnBrowser(CefHost.client(), url, transparent, settings, events);
    }

    public FnBrowser(CefClient client, String url, boolean transparent,
            CefBrowserSettings settings, Events events) {
        super(client, url, null, null, null, settings);
        this.transparent = transparent;
        this.events = events;
    }

    // ---- Was CefBrowser_N von einer Unterklasse verlangt --------------------

    @Override
    public void createImmediately() {
        trace("createImmediately");
        if (getNativeRef("CefBrowser") != 0) {
            return;
        }
        // Der Fensterhalter ist null, weil es kein Fenster gibt, und osr ist
        // wahr — daran und nur daran erkennt CEF, dass es die Bilder liefern
        // statt zeichnen soll.
        createBrowser(getClient(), 0, getUrl(), true, transparent, null, getRequestContext());
        trace("createBrowser zurück");
    }

    @Override
    public Component getUIComponent() {
        return NO_SURFACE;
    }

    @Override
    public CefRenderHandler getRenderHandler() {
        return this;
    }

    /**
     * Entwicklerwerkzeuge als eingebetteter Browser gibt es nicht.
     *
     * <p>Der Weg über den Debug-Port bleibt offen und ist der, den wir
     * benutzen — er braucht diese Methode nicht.
     */
    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url,
            CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
        return null;
    }

    /** Ein Bildschirmfoto über den GL-Kontext gibt es nicht — es gibt keinen. */
    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        return CompletableFuture.completedFuture(null);
    }

    // ---- Größe --------------------------------------------------------------

    /**
     * Setzt die Größe des unsichtbaren Fensters.
     *
     * <p>Beides ist nötig: das Rechteck, das {@link #getViewRect} zurückgibt,
     * und die Meldung an Chromium. Ohne das Rechteck malt es in der alten
     * Größe weiter, ohne die Meldung malt es gar nicht neu.
     */
    public void resize(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (viewRect.width == safeWidth && viewRect.height == safeHeight) {
            return;
        }
        viewRect.setBounds(0, 0, safeWidth, safeHeight);
        wasResized(safeWidth, safeHeight);
        trace("resize auf " + safeWidth + "x" + safeHeight);
    }

    public int browserWidth() {
        return viewRect.width;
    }

    public int browserHeight() {
        return viewRect.height;
    }

    // ---- Was hereinkommt ---------------------------------------------------

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        countCall("getViewRect");
        return viewRect;
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        countCall("getScreenPoint");
        return viewPoint == null ? new Point() : new Point(viewPoint);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        boolean[] ok = {false};
        guarded("getScreenInfo", () -> {
            screenInfo.Set(1.0, 32, 8, false, viewRect.getBounds(), viewRect.getBounds());
            ok[0] = true;
        });
        return ok[0];
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
                        ByteBuffer buffer, int width, int height) {
        if (events == null || buffer == null || width <= 0 || height <= 0) {
            return;
        }
        if (!firstPaintSeen && !popup) {
            firstPaintSeen = true;
            trace("erstes onPaint " + width + "x" + height);
        }
        BorrowedFrame frame = new BorrowedFrame(buffer, width, height, regionsOf(dirtyRects));
        guarded("onPaint", () -> {
            if (popup) {
                events.popupFrame(frame);
            } else {
                events.frame(frame);
            }
        });
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        if (events != null) {
            guarded("onPopupShow", () -> events.popupShown(show));
        }
    }

    /**
     * Wo das aufgeklappte Feld hingehört.
     *
     * <p><b>Kann vor dem ersten Bild kommen und über den Rand hinausragen.</b>
     * Chromium meldet die gewünschte Lage, nicht die zurechtgeschnittene.
     * Beschnitten wird deshalb beim Zeichnen und nicht hier — was hier
     * ankommt, ist die Wahrheit über das Feld und soll sie bleiben.
     */
    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        if (events != null && size != null) {
            guarded("onPopupSize",
                    () -> events.popupPlaced(size.x, size.y, size.width, size.height));
        }
    }


    /**
     * Chromium bestätigt das Schließen.
     *
     * <p><b>Erst hier ist der Browser weg</b>, nicht schon bei
     * {@code close(true)} — das ist eine Bitte. Die Bestätigung kommt aus der
     * Nachrichtenschleife und damit aus dem Pumpthread.
     *
     * <p>Der Aufruf an die Oberklasse muss zuerst kommen: Sie gibt dort
     * eigene Verweise frei und markiert den Browser als geschlossen.
     */
    /**
     * Ob gerade ein Fokuswechsel läuft.
     *
     * <p>Kein Zustand, den jemand abfragt — nur die Bremse gegen den Kreis
     * unten. Ein Feld ohne Nebenläufigkeitsschutz genügt: Fokus wird im
     * Renderthread gesetzt, und die Rückmeldung kommt aus derselben
     * Nachrichtenschleife.
     */
    private boolean settingFocus;

    /**
     * Setzt den Fokus — genau einmal je Anlass.
     *
     * <p><b>Ohne die Bremse ist das eine Endlosschleife.</b> Gemessen im
     * Spiel, als Stapel:
     *
     * <pre>
     *   FnBrowser.setFocus(true)
     *     → CefBrowser_N.setFocus → N_SetFocus (nativ)
     *         → CefClient.onGotFocus
     *             → browser.setFocus(true)     zurück auf Anfang
     * </pre>
     *
     * <p>Sie läuft, bis der Stapel überläuft; der native Teil schluckt den
     * {@code StackOverflowError} und macht weiter. Sichtbar war davon nur
     * {@code Exception in thread "Render thread"} ohne Stapel — ein bis sieben
     * Mal je Browsererzeugung, je nachdem wie oft der Bildschirm den Fokus
     * setzt.
     *
     * <p><b>Der CinemaMod-Fork hat den Rückschlag nicht:</b> Dessen
     * {@code CefClient.onGotFocus} reicht die Meldung nur an den
     * Fokus-Handler weiter, upstream setzt zusätzlich den Fokus erneut. Das
     * ist der Grund, warum derselbe Bildschirm auf MCEF keine einzige dieser
     * Meldungen erzeugt.
     *
     * <p>Der Kreis lässt sich nur hier brechen: Die andere Hälfte gehört
     * upstream, und ein Patch dafür wäre ein Patch gegen fremdes
     * Fokusverhalten statt gegen eine fehlende Möglichkeit.
     */
    @Override
    public void setFocus(boolean enable) {
        if (settingFocus) {
            // Das ist Chromiums Rückmeldung auf den Fokus, den wir gerade
            // setzen. Sie noch einmal weiterzureichen hieße, sie ein drittes
            // Mal zu bekommen.
            return;
        }
        settingFocus = true;
        try {
            trace("setFocus " + enable);
            super.setFocus(enable);
        } finally {
            settingFocus = false;
        }
    }

    @Override
    public void onBeforeClose() {
        trace("onBeforeClose");
        super.onBeforeClose();
        if (events != null) {
            try {
                events.browserClosed();
            } catch (Throwable broken) {
                com.mojang.logging.LogUtils.getLogger()
                        .warn("Rückruf onBeforeClose ist gescheitert", broken);
            }
        }
    }

    /**
     * Chromium hätte gern einen anderen Mauszeiger.
     *
     * <p><b>Was hier ankommt, ist eine AWT-Kennung und kein CEF-Zeiger.</b>
     * Der native Teil dieser Fassung übersetzt bereits — siehe
     * {@link AwtCursors}. Weitergereicht wird die Ordnungszahl von Chromium,
     * damit alles dahinter für beide Wege gleich aussieht.
     */
    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        if (events != null) {
            int cefType = AwtCursors.toCefCursorType(cursorType);
            guarded("onCursorChange", () -> events.cursorChanged(cefType));
        }
        // Wahr heißt: Wir haben uns gekümmert. Chromium versucht sonst, selbst
        // einen Zeiger zu setzen — auf ein Fenster, das es nicht gibt.
        return true;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        countCall("startDragging");
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        countCall("updateDragCursor");
    }

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) {
    }

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
    }

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {
    }

    private static List<DirtyRegion> regionsOf(Rectangle[] rectangles) {
        if (rectangles == null || rectangles.length == 0) {
            return List.of();
        }
        List<DirtyRegion> regions = new ArrayList<>(rectangles.length);
        for (Rectangle rectangle : rectangles) {
            if (rectangle.width > 0 && rectangle.height > 0) {
                regions.add(new DirtyRegion(Math.max(0, rectangle.x),
                        Math.max(0, rectangle.y), rectangle.width, rectangle.height));
            }
        }
        return regions;
    }

    // ---- Was hinausgeht ----------------------------------------------------

    /**
     * Eine Mausbewegung.
     *
     * @param leaving wahr, wenn der Zeiger die Fläche verlässt — Chromium
     *                braucht das, um Schwebezustände zurückzunehmen, sonst
     *                bleibt der zuletzt berührte Knopf für immer hell
     */
    public void moveMouse(int x, int y, int modifiers, boolean leaving) {
        int awt = AwtMouseEvents.toAwtModifiers(modifiers);
        if (leaving) {
            sendMouseEvent(AwtMouseEvents.exited(x, y, awt));
        } else {
            // Mit gedrückter Taste ist es ein Ziehen und keine Bewegung.
            // Chromium unterscheidet beides, und eine Textauswahl entsteht
            // nur beim Ziehen.
            sendMouseEvent(AwtMouseEvents.moved(x, y, awt,
                    AwtMouseEvents.anyButtonDown(modifiers)));
        }
    }

    /**
     * Eine Maustaste geht herunter oder herauf.
     *
     * <p><b>Kein eigenes {@code MOUSE_CLICKED}.</b> Chromium baut den Klick
     * aus Herunter und Herauf; ein zusätzliches Ereignis wäre ein zweiter
     * Klick und verfälschte Doppel- und Dreifachklicks.
     *
     * @param cefButton  Chromiums Zählung, nicht Minecrafts
     * @param clickCount 1, 2 oder 3 — daran und nur daran erkennt Chromium
     *                   einen Doppelklick
     */
    public void clickMouse(int x, int y, boolean down, int cefButton,
                           int clickCount, int modifiers) {
        if (cefButton < 0) {
            return;
        }
        sendMouseEvent(AwtMouseEvents.awtButton(x, y, down,
                AwtMouseEvents.browserButtonToAwt(cefButton), clickCount,
                AwtMouseEvents.toAwtModifiers(modifiers)));
    }

    /**
     * Das Rad dreht sich.
     *
     * <p>Der Aufrufer meldet die Strecke, schon mit dem Faktor drei je Rastung
     * multipliziert. AWT trennt beides wieder: {@code wheelRotation} zählt die
     * Rastungen, {@code scrollAmount} die Zeilen je Rastung, und der native
     * Teil rechnet aus dem Produkt dasselbe Delta.
     *
     * <p><b>Das Vorzeichen geht unverändert durch</b> — gemessen, nicht
     * geraten: {@code wheelRotation +1} erzeugt in der Seite
     * {@code deltaY -2.0}, also hinauf, und Minecrafts Delta ist beim Drehen
     * nach oben positiv.
     */
    public void scrollMouse(int x, int y, double amount, int modifiers) {
        int notches = (int) Math.round(amount / UNITS_PER_NOTCH);
        if (notches == 0) {
            // Eine Bewegung, die zu klein für eine Rastung ist, darf trotzdem
            // nicht verschwinden — sonst fühlt sich langsames Scrollen an, als
            // reagierte die Seite nicht.
            if (amount == 0) {
                return;
            }
            notches = amount > 0 ? 1 : -1;
        }
        sendMouseWheelEvent(AwtMouseEvents.wheel(x, y, notches, UNITS_PER_NOTCH,
                AwtMouseEvents.toAwtModifiers(modifiers)));
    }

    /** Zeilen je Rastung, von MCEF übernommen und dort erprobt. */
    private static final int UNITS_PER_NOTCH = 3;

    private static int tracedKeys;

    /**
     * Fängt ab, was sonst in JNI verschwindet.
     *
     * <p><b>Eine Ausnahme aus einem Rückruf ist unsichtbar.</b> Der native
     * Teil meldet sie über {@code ExceptionDescribe}; die Kopfzeile landet auf
     * der nackten Standardfehlerausgabe, der Stapel im Protokoll — und weil
     * beides verschiedene Wege nimmt, steht am Ende ein „Exception in thread"
     * ohne alles dahinter. Gemessen genau so, beim ersten Lauf im Spiel.
     *
     * <p>Deshalb wird hier gefangen und ordentlich geschrieben. Weiterwerfen
     * hätte niemandem geholfen: Chromium kann mit einer Ausnahme aus einem
     * Rückruf nichts anfangen und macht ohnehin weiter.
     */
    private static final java.util.Set<String> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Schreibt jeden Rückruf einmal ins Protokoll.
     *
     * <p>Nur für die Fehlersuche: Welche Rückrufe Chromium in welcher
     * Reihenfolge macht, steht in keiner Dokumentation, und eine Ausnahme aus
     * einem davon kommt ohne Stapel an.
     */
    private static void countCall(String what) {
        if (FnCefRuntime.TRACE && SEEN.add(what)) {
            com.mojang.logging.LogUtils.getLogger().info("Rückruf zum ersten Mal: {}", what);
        }
    }

    private static void guarded(String what, Runnable body) {
        countCall(what);
        try {
            body.run();
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .error("Rückruf {} ist gescheitert", what, broken);
        }
    }

    /**
     * Schreibt die ersten Tastendrücke ins Protokoll — roh.
     *
     * <p><b>Wozu, obwohl der Prüfstand grün ist.</b> Dort kam der Scancode aus
     * {@code glfwGetKeyScancode}; hier kommt er aus Minecrafts Tastenrückruf.
     * Dass beide dieselbe Kodierung tragen, ist wahrscheinlich und
     * unbewiesen — und fehlte das Bit 0x100 hier, wäre der Adapter im Spiel
     * still falsch, obwohl jede Messung stimmt. Zehn Zeilen im Protokoll
     * beantworten das einmal und für immer.
     */
    private static void traceFirstKeys(int glfwKeyCode, int scanCode, int modifiers,
            boolean down) {
        if (!FnCefRuntime.TRACE || tracedKeys >= 80) {
            return;
        }
        tracedKeys++;
        com.mojang.logging.LogUtils.getLogger().info(
                "Taste {}: glfw={} scancode=0x{} basis=0x{} erweitert={} mods=0x{} vk={}",
                down ? "herunter" : "herauf", glfwKeyCode,
                Integer.toHexString(scanCode), Integer.toHexString(GlfwScancodes.base(scanCode)),
                GlfwScancodes.extended(scanCode), Integer.toHexString(modifiers),
                GlfwKeys.toAwtKeyCode(glfwKeyCode));
    }

    /**
     * Eine Taste geht herunter oder herauf.
     *
     * <p><b>Warum {@code sendKeyEventRaw} und nicht {@code sendKeyEvent}.</b>
     * Auf Windows liest der native Teil den Scancode aus dem Feld
     * {@code java.awt.event.KeyEvent.scancode} — ein privates Feld, das nur
     * der native AWT-Code füllt. Ein selbst gebautes Ereignis trägt dort null,
     * und aus null macht {@code MapVirtualKey} für jede Taste den virtuellen
     * Tastencode null: Chromium erkennt dann keine einzige Taste. Deshalb der
     * Patch, der die Werte als Parameter nimmt.
     *
     * <p><b>Und warum der Scancode zerlegt wird.</b> GLFW führt „erweiterte
     * Taste" als Bit 8, Windows erwartet es an anderer Stelle. Pfeiltasten und
     * Ziffernblock teilen sich die unteren acht Bit; wer das Kennzeichen
     * verliert, bekommt für Pfeil-hoch die Acht des Ziffernblocks.
     */
    public void sendKey(int glfwKeyCode, int scanCode, int modifiers, boolean down) {
        traceFirstKeys(glfwKeyCode, scanCode, modifiers, down);
        sendKeyEventRaw(down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                AwtModifiers.forKey(modifiers),
                KeyEvent.CHAR_UNDEFINED,
                GlfwScancodes.base(scanCode),
                GlfwScancodes.extended(scanCode),
                GlfwKeys.toAwtKeyCode(glfwKeyCode));
        if (down && needsCarriageReturn(glfwKeyCode, modifiers)) {
            sendChar(CARRIAGE_RETURN, modifiers);
        }
    }

    /** Was Chromium für einen Zeilenumbruch sehen will. */
    private static final char CARRIAGE_RETURN = '\r';

    /**
     * Ob nach diesem Tastendruck noch ein Wagenrücklauf gehört.
     *
     * <p><b>Die Eingabetaste ist die eine Taste, die beides braucht</b> — den
     * Tastendruck <i>und</i> ein Zeichen. Upstreams eigener Quelltext sagt es
     * im Zweig für Linux ausdrücklich: „We need to treat the enter key as a
     * key press of character \r. This is apparently just how webkit handles it
     * and what it expects." Unter Windows liefert das Betriebssystem nach der
     * Tastenmeldung genau dafür eine Zeichenmeldung nach.
     *
     * <p>Bei uns kam sie nie an: {@code KEY_TYPED} entsteht ausschließlich aus
     * Minecrafts {@code charTyped}, und das wird für die Eingabetaste nicht
     * gerufen — sie ist kein druckbares Zeichen. Der Tastendruck kam an, der
     * Zeilenumbruch nicht. Gefunden hat das die Handprüfung; die maschinelle
     * Matrix hatte für die Eingabetaste den Tastencode geprüft und nicht den
     * Text.
     *
     * <p><b>Nicht mit Strg oder Alt.</b> Dann ist die Eingabetaste ein
     * Tastenkürzel und kein Zeilenumbruch — ein Zeichen hinterherzuschicken
     * schriebe eine Zeile, die niemand wollte. Umschalt bleibt erlaubt:
     * Umschalt+Eingabe ist in einem Editor ein Zeilenumbruch wie jeder andere.
     */
    private static boolean needsCarriageReturn(int glfwKeyCode, int modifiers) {
        boolean enter = glfwKeyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || glfwKeyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
        int blocking = org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL | org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
        return enter && (modifiers & blocking) == 0;
    }

    /**
     * Ein getipptes Zeichen.
     *
     * <p><b>Nur aus {@code charTyped}, nie aus einem Tastencode abgeleitet.</b>
     * Was auf einer deutschen Tastatur ein Umlaut ist, weiß das
     * Betriebssystem. Wer zusätzlich aus dem Tastendruck ein Zeichen bauen
     * wollte, bekäme jeden Buchstaben zweimal.
     *
     * <p><b>Ohne Strg und Alt.</b> Gemessen: Ein Zeichen mit Strg kommt gar
     * nicht an — Chromium deutet es als Steuerzeichen. Windows meldet AltGr
     * als Strg + rechtes Alt, und damit hinge auf einer deutschen Tastatur an
     * jedem {@code @}, {@code €}, {@code \}, {@code |} und {@code ~} ein Strg.
     *
     * <p>Die Grenze: {@code char} ist sechzehn Bit breit. Alles bis
     * {@code U+FFFF} geht; Zeichen darüber kämen als zwei Hälften an.
     */
    public void sendChar(char typed, int modifiers) {
        sendKeyEventRaw(KeyEvent.KEY_TYPED,
                AwtModifiers.forCharacter(modifiers),
                typed, 0, false, 0);
    }
}
