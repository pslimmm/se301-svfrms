package org.example;

import org.example.svfrms.SvfrmsApplication;

/** Backward-compatible launcher retained for existing scripts and the executable JAR. */
public final class SVFRMS {
    private SVFRMS() { }

    public static void main(String[] args) throws Exception {
        SvfrmsApplication.main(args);
    }
}
