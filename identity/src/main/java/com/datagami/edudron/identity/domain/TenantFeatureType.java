package com.datagami.edudron.identity.domain;

/**
 * Enum defining all tenant-level feature flags with their default values.
 * Each feature can be overridden per tenant in the tenant_feature table.
 */
public enum TenantFeatureType {
    /**
     * Controls whether students can self-enroll in courses.
     * Default: false (students cannot self-enroll by default)
     */
    STUDENT_SELF_ENROLLMENT(false, "Allow students to self-enroll in courses"),

    /**
     * Controls whether psychometric test feature is available for students.
     * Default: false (psychometric test is disabled by default)
     */
    PSYCHOMETRIC_TEST(false, "Enable psychometric test feature for students");

    private final boolean defaultValue;
    private final String description;

    TenantFeatureType(boolean defaultValue, String description) {
        this.defaultValue = defaultValue;
        this.description = description;
    }

    /**
     * Returns the default value for this feature.
     * This value is used when no tenant-specific override exists.
     */
    public boolean getDefaultValue() {
        return defaultValue;
    }

    /**
     * Returns a human-readable description of this feature.
     */
    public String getDescription() {
        return description;
    }
}
