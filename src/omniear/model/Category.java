package omniear.model;

import omniear.alerts.Priority;

public enum Category {
    SCREAM_DISTRESS("scream_distress", "Distress scream", Priority.P0, .52),
    EXPLOSION("explosion", "Explosion", Priority.P0, .65),
    IMPACT_CRASH("impact_crash", "Impact / crash", Priority.P1, .69),
    SIREN_TRAFFIC("siren_traffic", "Traffic siren", Priority.P4, .80),
    BACKGROUND("background", "Background noise", Priority.NONE, 1);

    private final String id;
    private final String displayName;
    private final Priority priority;
    private final double alertGate;
    Category(String id, String displayName, Priority priority, double alertGate) {
        this.id = id;
        this.displayName = displayName;
        this.priority = priority;
        this.alertGate = alertGate;
    }
    public String id() { return id; }
    public Priority priority() { return priority; }
    public double alertGate() { return alertGate; }
    @Override public String toString() { return displayName; }
    public static Category fromId(String id) {
        for (Category category : values()) if (category.id.equals(id)) return category;
        throw new IllegalArgumentException("Unknown category: " + id);
    }
}
