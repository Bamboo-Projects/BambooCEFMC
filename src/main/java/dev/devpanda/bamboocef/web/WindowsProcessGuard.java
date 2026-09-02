package dev.devpanda.bamboocef.web;

import com.mojang.logging.LogUtils;
import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.slf4j.Logger;

import java.util.List;

/**
 * The Windows bracket: a job object that takes everything with it when it dies.
 *
 * <p><b>Why JNA and not the foreign-function tool from Java 22.</b> The game
 * runs on Java 21. There the foreign function interface is a preview and
 * demands {@code --enable-preview} — a JVM switch that players do not set and a
 * mod cannot force. JNA is on the runtime path anyway, because Minecraft brings
 * it in via OSHI.
 *
 * <p><b>Why a custom interface.</b> JNA's {@code Kernel32} does not know the
 * job-object functions — looked up, not assumed. There are three.
 *
 * <p><b>The handle is deliberately never closed.</b> That is exactly what the
 * effect hangs on: Windows terminates the group when the last handle onto the
 * job disappears. When the process dies it does so by itself — even on the
 * hardest abort, because it is the operating system that closes handles, not
 * the program.
 */
final class WindowsProcessGuard {

    private static final Logger LOG = LogUtils.getLogger();

    /** {@code JobObjectExtendedLimitInformation} from {@code JOBOBJECTINFOCLASS}. */
    private static final int EXTENDED_LIMIT_INFORMATION = 9;

    /** Terminates all processes in the job as soon as the last handle onto it closes. */
    private static final int LIMIT_KILL_ON_JOB_CLOSE = 0x2000;

    /** The expected size of the structure on a 64-bit Windows. */
    private static final int EXPECTED_SIZE = 144;

    /**
     * The handle onto the job — as a field, so that no one collects it.
     *
     * <p>Were it collected and closed, Windows would, at that very moment,
     * terminate its own process along with everything in it. The field is not a
     * state that anyone reads; it is the only reason the handle stays alive.
     */
    @SuppressWarnings("unused")
    private static HANDLE job;

    interface JobApi extends StdCallLibrary {

        JobApi INSTANCE = Native.load("kernel32", JobApi.class, W32APIOptions.DEFAULT_OPTIONS);

        HANDLE CreateJobObject(Pointer attributes, String name) throws LastErrorException;

        boolean SetInformationJobObject(HANDLE job, int infoClass, Pointer info, int length)
                throws LastErrorException;

        boolean AssignProcessToJobObject(HANDLE job, HANDLE process) throws LastErrorException;

        boolean IsProcessInJob(HANDLE process, HANDLE job, IntByReference result)
                throws LastErrorException;
    }

    /**
     * {@code JOBOBJECT_EXTENDED_LIMIT_INFORMATION}.
     *
     * <p>Only a single field of it is used — {@code limitFlags}. The rest stands
     * here because the structure is passed as a whole and its size is part of
     * the call. The numbers are all zero; that means "no limit".
     *
     * <p>The field types are chosen for 64-bit: {@code SIZE_T} and
     * {@code ULONG_PTR} are eight bytes there. Whether the arithmetic works out,
     * {@link #install()} checks against the size rather than believing it.
     */
    @Structure.FieldOrder({
            "perProcessUserTimeLimit", "perJobUserTimeLimit", "limitFlags",
            "minimumWorkingSetSize", "maximumWorkingSetSize", "activeProcessLimit",
            "affinity", "priorityClass", "schedulingClass",
            "readOperationCount", "writeOperationCount", "otherOperationCount",
            "readTransferCount", "writeTransferCount", "otherTransferCount",
            "processMemoryLimit", "jobMemoryLimit",
            "peakProcessMemoryUsed", "peakJobMemoryUsed"})
    public static class ExtendedLimitInformation extends Structure {
        // JOBOBJECT_BASIC_LIMIT_INFORMATION
        public long perProcessUserTimeLimit;
        public long perJobUserTimeLimit;
        public int limitFlags;
        public long minimumWorkingSetSize;
        public long maximumWorkingSetSize;
        public int activeProcessLimit;
        public long affinity;
        public int priorityClass;
        public int schedulingClass;
        // IO_COUNTERS
        public long readOperationCount;
        public long writeOperationCount;
        public long otherOperationCount;
        public long readTransferCount;
        public long writeTransferCount;
        public long otherTransferCount;
        // The rest of JOBOBJECT_EXTENDED_LIMIT_INFORMATION
        public long processMemoryLimit;
        public long jobMemoryLimit;
        public long peakProcessMemoryUsed;
        public long peakJobMemoryUsed;

        public List<String> fieldOrder() {
            return getFieldOrder();
        }
    }

    /**
     * Opens the bracket.
     *
     * @return what came of it, in one sentence for the log
     */
    static String install() {
        HANDLE self = Kernel32.INSTANCE.GetCurrentProcess();

        // <b>First check whether someone has already bracketed.</b> A launcher
        // may do so, and since Windows 8 jobs can be nested — before that every
        // second attempt failed. The answer belongs in the log, because it
        // explains a failure further down instead of making it a riddle.
        String already = "unbekannt";
        try {
            IntByReference inJob = new IntByReference();
            if (JobApi.INSTANCE.IsProcessInJob(self, null, inJob)) {
                already = inJob.getValue() != 0 ? "ja" : "nein";
            }
        } catch (LastErrorException | Error unavailable) {
            already = "nicht feststellbar (" + unavailable + ")";
        }

        ExtendedLimitInformation limits = new ExtendedLimitInformation();
        if (limits.size() != EXPECTED_SIZE) {
            // No abort: a wrong picture of the structure must not cost the
            // start. Without a guard, at worst a process stays put — with a
            // wrongly laid-out call, worse could happen.
            return "nicht aufgespannt — die Struktur misst " + limits.size()
                    + " statt " + EXPECTED_SIZE + " Bytes (schon im Job: " + already + ")";
        }

        HANDLE created;
        try {
            created = JobApi.INSTANCE.CreateJobObject(null, null);
        } catch (LastErrorException failed) {
            return "CreateJobObject scheiterte mit Fehler " + failed.getErrorCode()
                    + " (schon im Job: " + already + ")";
        }
        if (created == null) {
            return "CreateJobObject gab nichts zurück (schon im Job: " + already + ")";
        }

        limits.limitFlags = LIMIT_KILL_ON_JOB_CLOSE;
        limits.write();
        try {
            JobApi.INSTANCE.SetInformationJobObject(created, EXTENDED_LIMIT_INFORMATION,
                    limits.getPointer(), limits.size());
        } catch (LastErrorException failed) {
            return "SetInformationJobObject scheiterte mit Fehler " + failed.getErrorCode()
                    + " (schon im Job: " + already + ")";
        }

        try {
            JobApi.INSTANCE.AssignProcessToJobObject(created, self);
        } catch (LastErrorException failed) {
            // Error 5 is access denied and here almost always means: an outer
            // job cannot be nested. That is an environment question, not a bug
            // in the program.
            return "AssignProcessToJobObject scheiterte mit Fehler " + failed.getErrorCode()
                    + (failed.getErrorCode() == 5 ? " (Zugriff verweigert — vermutlich hält ein "
                            + "äußerer Job den Prozess und lässt keine Verschachtelung zu)" : "")
                    + " (schon im Job: " + already + ")";
        }

        job = created;
        LOG.info("ProcessGuard: Job Object aufgespannt, KILL_ON_JOB_CLOSE gesetzt");
        return "aktiv — Job Object mit KILL_ON_JOB_CLOSE (schon im Job: " + already + ")";
    }

    private WindowsProcessGuard() {
    }
}
