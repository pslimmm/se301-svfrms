package org.example.svfrms.model;

/** Alert severity levels from the brief (section 3.4). */
public enum AlertSeverity {
    /** Informational, no action needed. */
    INFO,
    /** May need attention to prevent problems. */
    WARNING,
    /** Needs immediate action to protect crops or the system. */
    CRITICAL
}
