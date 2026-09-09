package technology.renatus.routepulse.attention.model;

public enum AttentionSeverity {
    URGENT(0),
    WARNING(1),
    INFO(2);

    private final int priority;

    AttentionSeverity(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public String getCssModifier() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
