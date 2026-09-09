package technology.renatus.routepulse.attention.model;

import java.util.List;

public record AttentionCenter(List<AttentionItem> items) {

    public AttentionCenter {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public List<AttentionItem> getItems() {
        return items;
    }

    public int getCount() {
        return items.size();
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public boolean isClear() {
        return items.isEmpty();
    }

    public long getUrgentCount() {
        return items.stream()
                .filter(item -> item.severity() == AttentionSeverity.URGENT)
                .count();
    }

    public String getHeadline() {
        if (items.isEmpty()) {
            return "Nothing needs attention";
        }

        return items.size() == 1
                ? "1 thing needs attention"
                : items.size() + " things need attention";
    }
}
