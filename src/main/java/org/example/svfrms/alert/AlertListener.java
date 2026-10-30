package org.example.svfrms.alert;

import org.example.svfrms.model.Alert;

/** Observer: anything that wants to react when an alert is raised. */
@FunctionalInterface
public interface AlertListener {

    void onAlert(Alert alert);
}
