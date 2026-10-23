package com.amperly.app.vehicle;

/**
 * How much the resolved dimensions can be trusted. Anything below HIGH is shown to the driver
 * with a body-type picker so they can correct the silhouette.
 */
public enum SpecConfidence {
    HIGH("Verified specs"),
    MEDIUM("Confirmed by driver"),
    LOW("Estimated - please confirm");

    private final String label;

    SpecConfidence(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean needsConfirmation() {
        return this == LOW;
    }
}
