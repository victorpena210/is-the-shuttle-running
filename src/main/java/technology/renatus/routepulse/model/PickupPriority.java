package technology.renatus.routepulse.model;

public enum PickupPriority {

    LOW("Low", "low"),
    MEDIUM("Moderate", "moderate"),
    HIGH("High", "high"),
    VERY_HIGH("Very high", "very-high");

    private final String label;
    private final String cssClass;

    PickupPriority(
            String label,
            String cssClass) {

        this.label = label;
        this.cssClass = cssClass;
    }

    public String getLabel() {
        return label;
    }

    public String getCssClass() {
        return cssClass;
    }
}