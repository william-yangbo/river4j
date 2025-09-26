package io.river4j.internal.database.model;

/**
 * River job states enum.
 * Corresponds to river_job_state PostgreSQL enum.
 */
public enum JobState {
    AVAILABLE("available"),
    CANCELLED("cancelled"), 
    COMPLETED("completed"),
    DISCARDED("discarded"),
    RETRYABLE("retryable"),
    RUNNING("running"),
    SCHEDULED("scheduled");
    
    private final String value;
    
    JobState(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return value;
    }
    
    public static JobState fromString(String value) {
        for (JobState state : JobState.values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown job state: " + value);
    }
}