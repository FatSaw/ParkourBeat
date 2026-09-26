package ru.sortix.parkourbeat.boards;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import ru.sortix.parkourbeat.rating.StatisticsManager;

@Getter
@Setter
public class BoardSession {

    public static final int NOTHING = -1;

    private int page = 0;
    private int hover = NOTHING;
    private int version = 1;
    private @NonNull StatisticsManager.SortKey sortKey = StatisticsManager.SortKey.PP;
    private int levelSort = 0;
    private long touchedAt = System.currentTimeMillis();

    public void bump() {
        this.version++;
        this.touchedAt = System.currentTimeMillis();
    }

    public boolean setHoverIfChanged(int value) {
        if (this.hover == value) return false;
        this.hover = value;
        this.bump();
        return true;
    }
}
