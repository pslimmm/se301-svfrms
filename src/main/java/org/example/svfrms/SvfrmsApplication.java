package org.example.svfrms;

import org.example.svfrms.infrastructure.context.SvfrmsApplicationContext;

/** Standalone application entry point. */
public final class SvfrmsApplication {
    private SvfrmsApplication() { }

    public static void main(String[] args) throws Exception {
        SvfrmsApplicationContext context = new SvfrmsApplicationContext();
        Thread shutdownHook = new Thread(context::close, "svfrms-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try {
            context.runSimulation(20);
        } finally {
            if (!Thread.currentThread().isInterrupted()) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException shutdownAlreadyStarted) {
                    // The JVM is already running shutdown hooks.
                }
            }
            context.close();
        }
    }
}
