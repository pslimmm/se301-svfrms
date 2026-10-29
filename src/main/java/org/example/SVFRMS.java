package org.example;

import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.example.svfrms.domain.chamber.ChamberSnapshot;
import org.example.svfrms.domain.chamber.ChamberSummarySnapshot;
import java.time.Duration;

/** Legacy operation names retained; state accessors now return immutable inspection snapshots. */
public final class SVFRMS {
    private static SvfrmsApplicationContext session;
    static { Runtime.getRuntime().addShutdownHook(new Thread(SVFRMS::closeSession, "svfrms-compatibility-shutdown")); }
    private SVFRMS() { }

    private static synchronized SvfrmsApplicationContext session() {
        if (session == null) session = new SvfrmsApplicationContext();
        return session;
    }

    /** Ends the owned compatibility session; the next operation starts with fresh state. */
    public static void closeSession() {
        SvfrmsApplicationContext current;
        synchronized (SVFRMS.class) { current = session; }
        if (current == null) return;
        // Worker output may call facade getters. Never hold the facade monitor while joining it.
        try { current.close(); }
        finally {
            // A failed run can still have stopped every writer. Retire it after explicit cleanup,
            // but retain ownership when a non-cooperative worker remains alive.
            if (current.isTerminated()) {
                synchronized (SVFRMS.class) { if (session == current) session = null; }
            }
        }
    }

    public static ChamberSnapshot getChamberState(String chamber) { return session().getChamberState(chamber); }
    public static ChamberSummarySnapshot getChamberSummary(String chamber) { return session().getChamberSummary(chamber); }
    public static void processSensor(String chamber, String crop, String sensor, double value) { session().processSensor(chamber,crop,sensor,value); }
    public static void activateControl(String chamber, String control) { session().activateControl(chamber,control); }
    public static void addControlRequest(String chamber, String control) { session().addControlRequest(chamber,control); }
    public static void handleEvent(String type, String chamber, String crop, String message) { session().handleEvent(type,chamber,crop,message); }
    public static void showLogs() { session().showLogs(); }
    public static void showAlerts() { session().showAlerts(); }
    public static void irrigationCycle() { session().irrigationCycle(); }
    public static void feedingCycle() { session().feedingCycle(); }
    public static void generateDailyReport() { session().generateDailyReport(); }
    public static void archiveAlerts() { session().archiveAlerts(); }
    public static void saveArchiveReport() { session().saveArchiveReport(); }
    public static void awaitControls(Duration timeout) throws InterruptedException { session().awaitControls(timeout); }

    public static void main(String[] args) throws Exception {
        synchronized (SVFRMS.class) {
            // A previous main already reported its failure. Do not replay a cached cleanup error
            // instead of starting a fresh run once all of that session's writers have stopped.
            if (session != null && session.isTerminated()) session = null;
        }
        closeSession();
        SvfrmsApplicationContext context;
        synchronized (SVFRMS.class) {
            context = new SvfrmsApplicationContext();
            session = context;
        }
        Throwable primary = null;
        try {
            context.runSimulation(20);
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            // Keep completed state available for legacy inspection until the next run or explicit cleanup.
            try { context.close(); }
            catch (RuntimeException cleanup) {
                if (primary == null) throw cleanup;
                if (primary != cleanup) primary.addSuppressed(cleanup);
            }
        }
    }
}
