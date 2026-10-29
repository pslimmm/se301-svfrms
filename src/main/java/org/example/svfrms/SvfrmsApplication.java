package org.example.svfrms;

import org.example.svfrms.concurrency.ChamberSimulationTask;
import org.example.svfrms.domain.alert.AlertEvent;
import org.example.svfrms.domain.chamber.ChamberId;
import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;

import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main application coordinator for the Singapore Vertical Farming Research Management System.
 */
public class SvfrmsApplication {

    private final SvfrmsApplicationContext context;

    public SvfrmsApplication(SvfrmsApplicationContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }

    public SvfrmsApplicationContext getContext() {
        return context;
    }

    /**
     * Executes the end-to-end chamber simulation, telemetry evaluation, control actuation,
     * daily reporting, and alert archiving lifecycle.
     */
    public void runSimulation() throws Exception {
        // 1. Start background actuator execution service
        context.getActuatorService().start();

        // 2. Launch concurrent chamber sensor simulators on Virtual Threads
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(new ChamberSimulationTask(ChamberId.of("CH1"), "Tomato", 20, context.getTelemetryPublisher(), context.getMetrics()));
            executor.submit(new ChamberSimulationTask(ChamberId.of("CH2"), "Strawberry", 20, context.getTelemetryPublisher(), context.getMetrics()));
            executor.submit(new ChamberSimulationTask(ChamberId.of("CH3"), "Lettuce", 20, context.getTelemetryPublisher(), context.getMetrics()));
            executor.submit(new ChamberSimulationTask(ChamberId.of("CH4"), "Chili", 20, context.getTelemetryPublisher(), context.getMetrics()));
        }

        // 3. Gracefully stop actuator consumer loop
        context.getActuatorService().stop();
        context.getActuatorService().awaitTermination(5, TimeUnit.SECONDS);

        // 4. Concurrently execute daily report and alert archiving (thread-safe without phase races)
        Thread genReport = Thread.ofVirtual().name("Report-Thread").start(() -> {
            context.getReportService().generateDailyReport();
        });

        Thread archiveAlerts = Thread.ofVirtual().name("Archive-Thread").start(() -> {
            List<AlertEvent> archived = context.getAlertService().archiveAlerts();
            if (!archived.isEmpty()) {
                context.getReportService().recordReportEvent("Archived " + archived.size() + " alerts");
                System.out.println("Archiving alerts... " + archived.size() + " moved to archive.");
            } else {
                context.getReportService().recordReportEvent("No alerts to archive");
                System.out.println("Archiving alerts... no active alerts to archive.");
            }
        });

        genReport.join();
        archiveAlerts.join();

        // 5. Completion notice and persistent archive report generation
        System.out.println("System finished sensor processing.");
        context.getReportService().saveArchiveReport(Paths.get("archive", "daily_report.txt"));
    }

    public static void main(String[] args) throws Exception {
        SvfrmsApplicationContext context = new SvfrmsApplicationContext();
        SvfrmsApplication app = new SvfrmsApplication(context);
        app.runSimulation();
    }
}
