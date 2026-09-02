package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.WebBackend;
import org.cef.CefApp;
import org.cef.CefClient;

/**
 * Where Chromium comes from — in this variant: from our own runtime.
 *
 * <p><b>This class existed twice up to B8</b>, under the same name, in two
 * source directories — one built against MCEF, one against upstream java-cef.
 * A switch in the build script decided. Since MCEF is gone there is only
 * {@code src/runtime/java} left, and the switch is gone with it.
 *
 * <p><b>Why there were two source directories in the first place and not a
 * branch.</b> MCEF and upstream java-cef both ship the complete
 * {@code org.cef} package — 170 classes, same names, different content. They
 * cannot sit next to each other on the class path. That ruled out any solution
 * within a single compilation unit; the split had to happen before the
 * compiler. The directory has stayed: it keeps visible what touches
 * {@code org.cef} at all.
 */
public final class CefHost {

    /** The client that browsers and handlers hang off. */
    public static CefClient client() {
        return FnCefRuntime.client();
    }

    /** The application that schemes are registered with. */
    public static CefApp app() {
        return FnCefRuntime.app();
    }

    /** The backend, exactly as {@code WebSupport} requests it. */
    public static WebBackend backend() {
        return FnRuntimeBackend.create();
    }

    private CefHost() {
    }
}
