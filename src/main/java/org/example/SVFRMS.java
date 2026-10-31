package org.example;

import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;
import org.example.svfrms.domain.chamber.*;

import java.util.Objects;

/** Legacy operation names retained; state accessors now return immutable inspection snapshots. */
public final class SVFRMS {
    private static final int DEFAULT_ITERATIONS_PER_CHAMBER = 20;

    private final SvfrmsApplicationContext application;

    /** The caller owns the supplied context and is responsible for closing it. */
    public SVFRMS(SvfrmsApplicationContext application) {
        this.application = Objects.requireNonNull(application, "application");
    }

    public ChamberSnapshot getChamberState(String chamber) { return application.getChamberState(chamber); }
    public ChamberSummarySnapshot getChamberSummary(String chamber) { return application.getChamberSummary(chamber); }
    public void processSensor(String chamber, String crop, String sensor, double value) { application.processSensor(chamber,crop,sensor,value); }
    public void activateControl(String chamber, String control) { application.activateControl(chamber,control); }
    public void addControlRequest(String chamber, String control) { application.addControlRequest(chamber,control); }
    public void handleEvent(String type, String chamber, String crop, String message) { application.handleEvent(type,chamber,crop,message); }
    public void showLogs() { application.showLogs(); }
    public void showAlerts() { application.showAlerts(); }
    public void irrigationCycle() { application.irrigationCycle(); }
    public void feedingCycle() { application.feedingCycle(); }
    public void generateDailyReport() { application.generateDailyReport(); }
    public void archiveAlerts() { application.archiveAlerts(); }
    public void saveArchiveReport() { application.saveArchiveReport(); }

    public static void main(String[] args) throws Exception {
        SvfrmsApplicationContext application = new SvfrmsApplicationContext();
        // runSimulation drains or cancels its workers and closes the context before returning.
        application.runSimulation(DEFAULT_ITERATIONS_PER_CHAMBER);
        application.close();
    }
}
