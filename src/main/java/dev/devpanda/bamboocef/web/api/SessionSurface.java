package dev.devpanda.bamboocef.web.api;

import com.mojang.logging.LogUtils;
import dev.devpanda.bamboocef.web.runtime.BrowserSession;
import dev.devpanda.bamboocef.web.view.ManagedTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.function.IntConsumer;

/**
 * The mapping of a surface onto a session.
 *
 * <p><b>Package-private, and that is the point.</b> To the outside there is
 * {@link WebSurface} — an interface without a single type from
 * {@code org.cef}. What lies beneath may change without foreign code having
 * to be recompiled.
 *
 * <p><b>The key filter sits here and not with the caller.</b> A surface that
 * itself knows which keys it takes can give an honest answer: true means
 * taken, false means "belongs to the game". If the decision lay with the
 * caller, everyone would have to make it anew — and everyone differently.
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
                "bamboocef", "web_surface/" + nextId++);
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
        // Both: whoever closed the surface itself, and whoever lost the
        // browser beneath — on leaving the world the manager closes all
        // sessions, and the surface learns of it only this way.
        return !closed && session.isOpen();
    }

    /** For the renderers in the same package: the session along with its popup details. */
    BrowserSession session() {
        return session;
    }

    // ---- Input --------------------------------------------------------------

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

    // The filter decides on the press; character, repeat, and release follow
    // this decision — see KeyRouting.

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
            // Whoever loses focus holds nothing anymore: a release that still
            // comes afterward then belongs to the game.
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
    public void postMessage(String json) {
        if (!closed) {
            session.postMessage(json);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // Unregister first, then close: the other way around would leave a
        // texture registered whose id points into the void.
        Minecraft.getInstance().getTextureManager().release(location);
        try {
            session.close();
        } catch (Throwable broken) {
            LOG.warn("Die Fläche {} ließ sich nicht schließen", name(), broken);
        }
    }
}
