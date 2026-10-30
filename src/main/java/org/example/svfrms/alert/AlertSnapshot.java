package org.example.svfrms.alert;

import java.util.List;

import org.example.svfrms.model.Alert;

/** Immutable view of the alert state at one moment. */
public record AlertSnapshot(long totalGenerated, List<Alert> active, List<Alert> archived) {
}
