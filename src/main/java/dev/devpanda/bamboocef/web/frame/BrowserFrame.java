package dev.devpanda.bamboocef.web.frame;

import java.util.List;

/**
 * Ein Bild des Browsers.
 *
 * <p><b>Ein Bild besitzt seine Bildpunkte.</b> Das ist keine Vorliebe,
 * sondern folgt aus JCEF: Sein {@code onPaint} reicht einen
 * {@code ByteBuffer} herein, den der native Teil mit
 * {@code NewDirectByteBuffer} direkt auf Chromiums Speicher legt — ohne
 * Kopie. Nach der Rückkehr aus dem Aufruf darf Chromium diesen Speicher
 * wiederverwenden. Ein Bild, das den Puffer nur festhält, zeigt beim nächsten
 * Blick auf etwas anderes oder auf nichts.
 *
 * <p>Deshalb kopiert die Implementierung im Aufruf, und deshalb hat ein Bild
 * ein {@link #close()}: Direkte Puffer liegen außerhalb des Java-Speichers,
 * und der Sammler räumt sie spät oder nie ab. Bei achteinhalb Megabyte je Bild
 * ist „spät" dasselbe wie „nie".
 *
 * <p><b>Dieser Typ bedeutet Besitz.</b> Was nur geliehen ist — der Puffer aus
 * einem laufenden {@code onPaint} — heißt {@link BorrowedFrame} und ist
 * absichtlich <i>nicht</i> mit diesem verwandt. Damit kann ein geliehener
 * Puffer nirgends landen, wo er den Aufruf überleben müsste: Der Übersetzer
 * weist es ab, und niemand muss sich an eine Regel erinnern.
 *
 * <p>Was hier <b>nicht</b> steht, ist die Art der Bildpunkte. Kein
 * {@code ByteBuffer}, kein Texturname, kein Handle. Ein Bild ist Maß und
 * Änderung; woraus es besteht, weiß nur, wer es erzeugt hat und wer es
 * verbraucht — und genau deshalb kann später ein Bild dazukommen, das auf der
 * Grafikkarte liegt und nie im Hauptspeicher war.
 */
public interface BrowserFrame extends AutoCloseable {

    int width();

    int height();

    /**
     * Welche Bereiche sich seit dem vorigen Bild geändert haben.
     *
     * <p>Nie leer: Ein Bild ohne Änderung ist kein Bild.
     */
    List<DirtyRegion> dirty();

    /** Deckt die Änderung das ganze Bild ab? */
    default boolean full() {
        List<DirtyRegion> regions = dirty();
        return regions.size() == 1
                && regions.get(0).equals(DirtyRegion.whole(width(), height()));
    }

    /** Gibt die Bildpunkte frei. Mehrfaches Schließen ist erlaubt. */
    @Override
    void close();
}
