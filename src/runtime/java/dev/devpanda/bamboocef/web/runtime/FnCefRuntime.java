package dev.devpanda.bamboocef.web.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.slf4j.Logger;

import java.io.File;

/**
 * Chromium without MCEF — the dev variant.
 *
 * <p><b>What this class is and what it is not.</b> It starts up the
 * self-built runtime, so that the new browser and input path can be tested in
 * the game. It is <i>not</i> a shipping route: there is no download, no
 * checksums, no watchdog over foreign processes. The directory must be
 * present, otherwise it says so and stops.
 *
 * <pre>
 *   pwsh -File tools/runtime/build-jcef.ps1
 *   ./gradlew runClient
 * </pre>
 *
 * <p><b>Everything runs on the render thread — startup included.</b> CEF
 * demands that initialization, message loop and shutdown take place on the
 * same thread, and our render path demands that it be the render thread:
 * {@code onPaint} arrives where the pumping happens, and the release of the
 * texture hangs off that. So nothing is kicked off here at game start, but on
 * first need — and that arises on the render thread.
 */
public final class FnCefRuntime {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * Whether debugging runs along: {@code -Dbamboocef.cef.trace=true}.
     *
     * <p>It hooks into the standard error output and records every key. Both
     * are only noise in everyday use and, in case of doubt, the only thing that
     * helps — hence a switch and not a decision for good.
     */
    static final boolean TRACE = Boolean.getBoolean("bamboocef.cef.trace");

    private static CefApp app;
    private static CefClient client;
    private static Thread owner;
    private static boolean failed;
    private static String failure;
    /** A foreseen reason that does not change — returns unchanged on the next call. */
    private static dev.devpanda.bamboocef.web.WebRuntimeStatus knownFailure;
    private static boolean shutDown;

    /**
     * Starts up if necessary, and returns the thread that CEF belongs to.
     *
     * @throws IllegalStateException when it does not work — the text names the
     *         reason and is meant for people
     */
    public static synchronized void ensureStarted() {
        if (app != null) {
            return;
        }
        if (shutDown) {
            // <b>An end state, not a retry.</b> CEF can be started only once
            // per process. On exit, Minecraft paints one more frame after
            // shutdown, and whoever wants a browser then gets this notice — no
            // second startup attempt that ended as an error with a stack trace
            // in the log, saying "Settings can only be passed to CEF before
            // createClient".
            throw new dev.devpanda.bamboocef.web.WebRuntimeUnavailable(
                    dev.devpanda.bamboocef.web.WebRuntimeState.SHUT_DOWN,
                    "Chromium ist heruntergefahren und startet in diesem Prozess nicht neu");
        }
        if (knownFailure != null) {
            throw new dev.devpanda.bamboocef.web.WebRuntimeUnavailable(
                    knownFailure.state(), knownFailure.reason());
        }
        if (failed) {
            throw new IllegalStateException(failure);
        }
        try {
            start();
        } catch (dev.devpanda.bamboocef.web.WebRuntimeUnavailable known) {
            // <b>A foreseen state passes on unchanged.</b> Whoever packed it
            // into an IllegalStateException would turn "currently loading" into
            // "broken" — and the notice the player reads would be the wrong
            // one.
            //
            // And it is not remembered when a second attempt is worthwhile:
            // after a download the same call should go through and not fail on
            // a remembered note. What is remembered is the state itself, not
            // its text — otherwise the second call would bring a FAILED with
            // the wording of a different reason.
            if (!known.status().state().worthRetrying()) {
                knownFailure = known.status();
            }
            throw known;
        } catch (Throwable broken) {
            failed = true;
            failure = broken.getMessage() == null ? broken.toString() : broken.getMessage();
            LOG.error("Die eigene Laufzeitumgebung kam nicht hoch", broken);
            throw new IllegalStateException(failure, broken);
        }
    }

    private static void start() {
        File dir = runtimeDir();
        owner = Thread.currentThread();
        LOG.info("Eigene Laufzeitumgebung: {} — im Thread {}", dir, owner.getName());
        if (TRACE) {
            captureStandardError();
        }

        // <b>We name the directory, the loading java-cef does itself.</b> A
        // loading path of our own via SystemBootstrap.setLoader and
        // System.load with an absolute path does load the file, but is of no
        // use: the JVM only finds native methods in libraries that the
        // classloader of the respective class loaded. Measured in the game —
        //
        //   CefApp        cpw.mods.cl.ModuleClassLoader
        //   FnCefRuntime  cpw.mods.modlauncher.TransformingClassLoader
        //
        // — and a System.load from our code binds to the second. The result is
        // an UnsatisfiedLinkError on the first native call, even though the
        // library has long been in the process. A loader we set from outside
        // would have the same problem: it would run in our loader.
        //
        // That is why patch 0005 tells the default path where to look. The
        // System.load call thus lives in SystemBootstrap itself, the loader is
        // correct, and no PATH entry is needed any more — under a real launcher
        // nobody sets that for us anyway.
        System.setProperty("jcef.library.path", dir.getAbsolutePath());

        // <b>Before CefApp.startup, not after.</b> The watchdog brackets our
        // own process, and child processes inherit the membership. Whoever
        // brackets only after the start no longer brackets the helpers that
        // are already running.
        dev.devpanda.bamboocef.web.ProcessGuard.install();

        if (!CefApp.startup(new String[] {})) {
            throw new IllegalStateException("CefApp.startup schlug fehl");
        }

        // Before the first getInstance and from the thread that pumps
        // afterwards. Without this CefApp pushes initialization and loop onto
        // AWT's event thread — the frames would then arrive on the wrong
        // thread, and the rate would be capped at thirty.
        CefApp.useCallingThread();

        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        settings.log_file = new File(gameDirectory(), "logs/fn-cef.log").getAbsolutePath();

        // Set explicitly, not guessed: otherwise upstream derives the path of
        // the helper process from java.library.path, and that does not point
        // at our directory here.
        settings.browser_subprocess_path = new File(dir, "jcef_helper.exe").getAbsolutePath();
        settings.resources_dir_path = dir.getAbsolutePath();
        settings.locales_dir_path = new File(dir, "locales").getAbsolutePath();

        // Without a cache of its own, CEF warns of unintended behaviour when
        // two applications share the same default path.
        File cache = new File(gameDirectory(), "fn-cef-cache");
        settings.root_cache_path = cache.getAbsolutePath();
        settings.cache_path = cache.getAbsolutePath();

        app = CefApp.getInstance(settings);
        client = app.createClient();
        LOG.info("Chromium ist da: {}", app.getVersion());
    }

    /**
     * One round of the message loop.
     *
     * <p>From the render thread, once per frame. Everything CEF has to report —
     * {@code onPaint} included — comes in this call and thus on this thread.
     *
     * <p>Not {@code doMessageLoopWork}: that is the route for applications
     * without a loop of their own and hands the work off to AWT. CEF also calls
     * it back itself to request a round — the first one still from the middle
     * of initialization.
     */
    public static void pump() {
        CefApp running = app;
        if (running != null) {
            running.doMessageLoopWorkNow();
        }
    }

    public static synchronized CefApp app() {
        ensureStarted();
        return app;
    }

    public static synchronized CefClient client() {
        ensureStarted();
        return client;
    }

    /** The thread that CEF belongs to — null as long as nothing is running. */
    public static Thread owner() {
        return owner;
    }

    public static synchronized boolean running() {
        return app != null;
    }

    /**
     * Shuts down.
     *
     * <p>On the same thread as startup, and with continued pumping: CEF works
     * the closing off via its own loop, and whoever stops pumping after
     * {@code dispose()} leaves the helper processes standing.
     */
    public static synchronized void shutdown() {
        if (app == null) {
            return;
        }
        CefApp closing = app;
        app = null;
        client = null;
        shutDown = true;
        try {
            closing.dispose();
            for (int i = 0; i < 200 && CefApp.getState() != CefApp.CefAppState.TERMINATED; i++) {
                closing.doMessageLoopWorkNow();
                Thread.sleep(5);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable broken) {
            LOG.warn("Beim Herunterfahren ging etwas schief", broken);
        }
        LOG.info("Chromium ist unten: {}", CefApp.getState());
    }

    /**
     * Records what goes to the standard error output.
     *
     * <p><b>What for, even though there is a log.</b> When an exception flies
     * out of a Chromium callback, the native part reports it via
     * {@code ExceptionDescribe}: the header line goes to the bare error output,
     * the stack trace via {@code printStackTrace} to {@code System.err} — and
     * the game has long since redirected that. What is left is an "Exception in
     * thread" with nothing behind it, and there is nothing to be done with
     * that.
     *
     * <p>That is why a second path is laid down alongside, one that belongs to
     * no one: a file under {@code logs/}. What passes through still also goes
     * where it went before.
     */
    private static void captureStandardError() {
        try {
            File file = new File(gameDirectory(), "logs/fn-cef-stderr.log");
            file.getParentFile().mkdirs();
            java.io.PrintStream original = System.err;
            java.io.OutputStream toFile = new java.io.FileOutputStream(file, true);
            System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
                @Override
                public void write(int b) throws java.io.IOException {
                    toFile.write(b);
                    original.write(b);
                }

                @Override
                public void flush() throws java.io.IOException {
                    toFile.flush();
                    original.flush();
                }
            }, true));
            LOG.info("Fehlerausgabe wird zusätzlich nach {} geschrieben", file);
        } catch (Throwable broken) {
            LOG.warn("Fehlerausgabe konnte nicht mitgeschrieben werden", broken);
        }
    }

    /**
     * Where the runtime lives.
     *
     * <p>The search lives in {@code RuntimeInstall} and not here: it must not
     * touch any class from {@code org.cef} — it runs precisely because it is
     * not yet settled whether there are any.
     */
    private static File runtimeDir() {
        return dev.devpanda.bamboocef.web.RuntimeInstall.locate(gameDirectory());
    }

    private static File gameDirectory() {
        try {
            return Minecraft.getInstance().gameDirectory;
        } catch (Throwable outsideTheGame) {
            return new File(".");
        }
    }

    private FnCefRuntime() {
    }
}
