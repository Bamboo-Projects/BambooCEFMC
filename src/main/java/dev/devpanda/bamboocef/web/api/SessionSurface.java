package dev.devpanda.bamboocef.web.api;

import com.mojang.logging.LogUtils;
import dev.devpanda.bamboocef.web.runtime.BrowserSession;
import dev.devpanda.bamboocef.web.view.ManagedTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.function.IntConsumer;

/**
 * Die Umsetzung einer Fläche auf eine Sitzung.
 *
 * <p><b>Paketprivat, und das ist der Punkt.</b> Nach außen gibt es
 * {@link WebSurface} — eine Schnittstelle ohne einen einzigen Typ aus
 * {@code org.cef}. Was darunter liegt, darf sich ändern, ohne dass fremder
 * Code neu übersetzt werden muss.
 *
 * <p><b>Der Tastenfilter sitzt hier und nicht beim Aufrufer.</b> Eine Fläche,
 * die selbst weiß, welche Tasten sie nimmt, kann eine ehrliche Antwort geben:
 * Wahr heißt genommen, falsch heißt „gehört dem Spiel". Läge die Entscheidung
 * beim Aufrufer, müsste jeder sie neu treffen — und jeder anders.
 */
final class SessionSurface implements WebSurface {

    private static final Logger LOG = LogUtils.getLogger();

    private static int nextId;

    private final BrowserSession session;
    private final KeyFilter keys;
    private final KeyRouting routing;
    private final ResourceLocation location;
    private boolean closed;

    SessionSurface(BrowserSession session, KeyFilter keys) {
        this.session = session;
        this.keys = keys;
        this.routing = new KeyRouting(keys);
        this.location = ResourceLocation.fromNamespaceAndPath(
                "factorynetwork", "web_surface/" + nextId++);
        Minecraft.getInstance().getTextureManager()
                .register(location, new ManagedTexture(session::textureId));
    }

    @Override
    public int textureId() {
        return closed ? 0 : session.textureId();
    }

    @Override
    public ResourceLocation textureLocation() {
        return location;
    }

    @Override
    public int width() {
        return session.width();
    }

    @Override
    public int height() {
        return session.height();
    }

    @Override
    public void resize(int width, int height) {
        if (!closed) {
            session.resize(Math.clamp(width, 1, SurfaceSpec.MAX_EDGE),
                    Math.clamp(height, 1, SurfaceSpec.MAX_EDGE));
        }
    }

    @Override
    public String name() {
        return session.name();
    }

    @Override
    public boolean alive() {
        // Beides: Wer die Fläche selbst schloss, und wer den Browser darunter
        // verlor — beim Verlassen der Welt schließt der Verwalter alle
        // Sitzungen, und die Fläche erfährt es nur auf diesem Weg.
        return !closed && session.isOpen();
    }

    /** Für die Zeichner im selben Paket: die Sitzung samt Popup-Angaben. */
    BrowserSession session() {
        return session;
    }

    // ---- Eingaben -----------------------------------------------------------

    @Override
    public void mouseMoved(int x, int y, int modifiers) {
        if (!closed) {
            session.mouseMoved(x, y, modifiers);
        }
    }

    @Override
    public void mouseLeft(int x, int y) {
        if (!closed) {
            session.mouseLeft(x, y);
        }
    }

    @Override
    public void mousePressed(int x, int y, int button, int modifiers) {
        if (!closed) {
            session.mousePressed(x, y, button, modifiers, System.currentTimeMillis());
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button, int modifiers) {
        if (!closed) {
            session.mouseReleased(x, y, button, modifiers);
        }
    }

    @Override
    public void mouseScrolled(int x, int y, double amount, int modifiers) {
        if (!closed) {
            session.mouseScrolled(x, y, amount, modifiers);
        }
    }

    // Der Filter entscheidet beim Drücken; Zeichen, Wiederholung und
    // Loslassen folgen dieser Entscheidung — siehe KeyRouting.

    @Override
    public boolean keyPressed(int glfwKey, int scanCode, int modifiers) {
        if (closed || !routing.press(glfwKey, modifiers)) {
            return false;
        }
        session.keyPressed(glfwKey, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(int glfwKey, int scanCode, int modifiers) {
        if (closed || !routing.release(glfwKey)) {
            return false;
        }
        session.keyReleased(glfwKey, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char typed, int modifiers) {
        if (closed || !routing.typed()) {
            return false;
        }
        session.charTyped(typed, modifiers);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        if (closed) {
            return;
        }
        if (!focused) {
            // Wer den Fokus verliert, hält nichts mehr: Ein Loslassen, das
            // danach noch kommt, gehört dann dem Spiel.
            routing.releaseAll();
        }
        session.setFocused(focused);
    }

    @Override
    public void onCursor(IntConsumer sink) {
        session.onCursor(sink);
    }

    @Override
    public void onMessage(java.util.function.Consumer<String> handler) {
        if (!closed) {
            session.onMessage(handler);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // Erst abmelden, dann schließen: Andersherum bliebe eine Textur
        // angemeldet, deren Kennung ins Leere zeigt.
        Minecraft.getInstance().getTextureManager().release(location);
        try {
            session.close();
        } catch (Throwable broken) {
            LOG.warn("Die Fläche {} ließ sich nicht schließen", name(), broken);
        }
    }
}
