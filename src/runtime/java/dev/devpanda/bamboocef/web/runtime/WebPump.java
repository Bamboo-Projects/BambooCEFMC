package dev.devpanda.bamboocef.web.runtime;

/**
 * Who turns Chromium's message loop — in this variant: we do ourselves.
 *
 * <p>Without MCEF there is no mixin that pumps. The beat therefore comes from
 * the render event: once per frame, on the render thread, <b>before</b>
 * drawing. Before and not after, so that a frame Chromium has just delivered
 * still makes it into the same texture this frame uses — otherwise the page
 * would lag one frame behind.
 *
 * <p>As long as nothing has started up, the call is a null check. There is no
 * reason to tie startup to drawing: a browser comes into being when someone
 * wants to see one.
 */
public final class WebPump {

    /** Once per frame, from the render thread. */
    public static void frame() {
        FnCefRuntime.pump();
    }

    private WebPump() {
    }
}
