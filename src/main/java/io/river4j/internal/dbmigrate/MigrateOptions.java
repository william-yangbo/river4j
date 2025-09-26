package io.river4j.internal.dbmigrate;

/**
 * Options for migration operations.
 * Corresponds to MigrateOptions in Go.
 */
public record MigrateOptions(
    Integer maxSteps
) {
    public MigrateOptions() {
        this(null);
    }
    
    public MigrateOptions(int maxSteps) {
        this(Integer.valueOf(maxSteps));
    }
    
    /**
     * Check if max steps limit is set (includes zero and positive)
     */
    public boolean hasMaxStepsLimit() {
        return maxSteps != null;
    }
    
    /**
     * Get effective max steps, following Go's logic:
     * - maxSteps < 0: return 0 (no migrations)  
     * - maxSteps = 0: return 0 (no migrations)
     * - maxSteps > 0: return min(maxSteps, available)
     * - maxSteps = null: return available (all migrations)
     */
    public int getEffectiveMaxSteps(int available) {
        if (!hasMaxStepsLimit()) {
            return available; // null case - unlimited
        }
        if (maxSteps < 0) {
            return 0; // negative case - disable all
        }
        if (maxSteps == 0) {
            return 0; // zero case - no migrations
        }
        return Math.min(maxSteps, available); // positive case - limited
    }
}