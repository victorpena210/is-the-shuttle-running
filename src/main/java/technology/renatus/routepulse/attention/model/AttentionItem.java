package technology.renatus.routepulse.attention.model;

import java.util.Objects;

public record AttentionItem(
        String key,
        AttentionSeverity severity,
        String title,
        String detail,
        String actionLabel,
        String actionHref) {

    public AttentionItem {
        Objects.requireNonNull(key, "Attention item key is required.");
        Objects.requireNonNull(severity, "Attention item severity is required.");
        Objects.requireNonNull(title, "Attention item title is required.");
        Objects.requireNonNull(detail, "Attention item detail is required.");
        Objects.requireNonNull(actionLabel, "Attention item action label is required.");
        Objects.requireNonNull(actionHref, "Attention item action href is required.");
    }

    public String getKey() {
        return key;
    }

    public AttentionSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public String getActionHref() {
        return actionHref;
    }
}
