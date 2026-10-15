package ru.sortix.parkourbeat.rating;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import ru.sortix.parkourbeat.utils.text.Theme;

@Getter
@RequiredArgsConstructor
public enum JumpResult {
    // Цвета попаданий - опознавательные знаки, как и оценки: игрок ловит их боковым
    // зрением на лету. Поэтому коды прямые, палитра плагина их не перекрашивает.

    // Вторым аргументом теперь передаем красивый текст
    PERFECT(300, "+300", Theme.V_AQUA),
    GOOD(100, "+100", Theme.V_GREEN),
    OK(50, "+50", Theme.V_YELLOW),
    MISS(0, "", Theme.V_RED);

    /**
     * Raw base points before the combo multiplier.
     */
    private final int basePoints;

    /**
     * Украшенный текст для вывода на экран
     */
    private final @NonNull String displayPoints;

    /**
     * Legacy-ampersand color prefix for the "+300 / +100 / +50" sub-title.
     */
    private final @NonNull String colorPrefix;

    public boolean isHit() {
        return this != MISS;
    }

    /**
     * The signed accuracy nudge (in "accuracy identifier" terms, mapped later to a
     * small percentage delta by the accuracy model).
     */
    public double getAccuracyDelta() {
        switch (this) {
            case PERFECT: return +1.0D;
            case GOOD:    return -0.5D;
            case OK:      return -1.0D;
            case MISS:
            default:      return -4.0D;
        }
    }

    @NonNull
    public String formatPoints() {
        // Теперь плагин не генерирует текст из цифр, а берет наш красивый текст
        return this.colorPrefix + this.displayPoints;
    }
}
