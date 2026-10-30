package org.example.svfrms.model;

/** Which way a reading left its acceptable range. */
public enum Breach {
    HIGH("High"),
    LOW("Low"),
    /** The reading itself is unusable (sensor failure). */
    INVALID("Invalid");

    private final String word;

    Breach(String word) {
        this.word = word;
    }

    public String word() {
        return word;
    }

    public String configKey() {
        return name().toLowerCase();
    }
}
