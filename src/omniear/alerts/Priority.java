package omniear.alerts;

public enum Priority {
    P0("Critical"), P1("High"), P4("Monitoring"), NONE("Suppressed");
    private final String severity;
    Priority(String severity) { this.severity = severity; }
    public String severity() { return severity; }
}
