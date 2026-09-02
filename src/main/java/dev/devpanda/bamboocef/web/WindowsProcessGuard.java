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
 * Die Windows-Klammer: ein Job Object, das beim Sterben alles mitnimmt.
 *
 * <p><b>Warum JNA und nicht das Fremdfunktions-Werkzeug aus Java 22.</b> Das
 * Spiel läuft auf Java 21. Dort ist die Fremdfunktionsschnittstelle eine
 * Vorschau und verlangt {@code --enable-preview} — einen JVM-Schalter, den
 * Spieler nicht setzen und den eine Mod nicht erzwingen kann. JNA liegt
 * ohnehin im Laufzeitpfad, weil Minecraft es über OSHI mitbringt.
 *
 * <p><b>Warum ein eigenes Interface.</b> JNAs {@code Kernel32} kennt die
 * Job-Object-Funktionen nicht — nachgesehen, nicht vermutet. Es sind drei.
 *
 * <p><b>Die Handhabe wird absichtlich nie geschlossen.</b> Genau daran hängt
 * die Wirkung: Windows beendet die Gruppe, wenn die letzte Handhabe auf den
 * Job verschwindet. Beim Sterben des Prozesses tut sie das von selbst — auch
 * beim härtesten Abbruch, denn Handhaben schließt das Betriebssystem, nicht
 * das Programm.
 */
final class WindowsProcessGuard {

    private static final Logger LOG = LogUtils.getLogger();

    /** {@code JobObjectExtendedLimitInformation} aus {@code JOBOBJECTINFOCLASS}. */
    private static final int EXTENDED_LIMIT_INFORMATION = 9;

    /** Beendet alle Prozesse im Job, sobald die letzte Handhabe darauf zugeht. */
    private static final int LIMIT_KILL_ON_JOB_CLOSE = 0x2000;

    /** Die erwartete Größe der Struktur auf einem 64-Bit-Windows. */
    private static final int EXPECTED_SIZE = 144;

    /**
     * Die Handhabe auf den Job — als Feld, damit sie niemand einsammelt.
     *
     * <p>Würde sie eingesammelt und geschlossen, beendete Windows in genau
     * diesem Augenblick den eigenen Prozess samt allem darin. Das Feld ist
     * kein Zustand, den jemand liest; es ist der einzige Grund, warum die
     * Handhabe leben bleibt.
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
     * <p>Gebraucht wird davon ein einziges Feld — {@code limitFlags}. Der Rest
     * steht hier, weil die Struktur als Ganzes übergeben wird und ihre Größe
     * Teil des Aufrufs ist. Die Zahlen sind alle null; das heißt „keine
     * Grenze".
     *
     * <p>Die Feldarten sind für 64-Bit gewählt: {@code SIZE_T} und
     * {@code ULONG_PTR} sind dort acht Bytes. Ob die Rechnung aufgeht, prüft
     * {@link #install()} an der Größe, statt es zu glauben.
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
        // Der Rest von JOBOBJECT_EXTENDED_LIMIT_INFORMATION
        public long processMemoryLimit;
        public long jobMemoryLimit;
        public long peakProcessMemoryUsed;
        public long peakJobMemoryUsed;

        public List<String> fieldOrder() {
            return getFieldOrder();
        }
    }

    /**
     * Spannt die Klammer auf.
     *
     * @return was daraus geworden ist, in einem Satz fürs Protokoll
     */
    static String install() {
        HANDLE self = Kernel32.INSTANCE.GetCurrentProcess();

        // <b>Erst nachsehen, ob schon jemand geklammert hat.</b> Ein Launcher
        // darf das, und seit Windows 8 lassen sich Jobs verschachteln — davor
        // schlug jeder zweite Versuch fehl. Die Antwort gehört ins Protokoll,
        // weil sie einen Fehlschlag weiter unten erklärt, statt ihn zu einem
        // Rätsel zu machen.
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
            // Kein Abbruch: Ein falsches Bild von der Struktur darf den Start
            // nicht kosten. Ohne Wächter bleibt im schlimmsten Fall ein
            // Prozess stehen — mit einem falsch belegten Aufruf könnte
            // Schlimmeres passieren.
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
            // Fehler 5 ist Zugriff verweigert und heißt hier fast immer: Ein
            // äußerer Job lässt sich nicht verschachteln. Das ist eine
            // Umgebungsfrage, kein Fehler im Programm.
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
