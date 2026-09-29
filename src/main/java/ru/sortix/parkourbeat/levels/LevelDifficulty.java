package ru.sortix.parkourbeat.levels;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Сложности уровня.
 * <p>
 * Названия обёрнуты в {@code <v>}: это опознавательные цвета, такие же как у оценок за
 * прыжок, и палитра оформления их перекрашивать не должна. Без обёртки §a у EASY уезжал
 * в тематический зелёный, и все сложности сливались в один оттенок.
 */
@Getter
@RequiredArgsConstructor
public enum LevelDifficulty {
    N_A("<v>§7N/A</v>", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTU0NjFhMjE1YjMyNWZiZGY4OTJkYjY3YjdiZmI2MGFkMmJmMTU4MGRjOTY4YTE1ZGZiMzA0Y2NkNWU3NGRiIn19fQ=="),
    EASY("<v>§aEASY</v>", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE1NmEwYjVlYjVhZjViNDY1NzA4MDk4NDljNmQ5MWVhYzgzMzUzZTZhMDZhYjE1MjIwNDdlMzRkM2Q4MTkxMCJ9fX0="),
    HARD("<v>§eHARD</v>", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjY3ODk2MGU3ODMzMWFjZjEyOTg5NDE2OWI2MTQxNmZiYTFhZDc0MTkwNzUzNGNkN2I1ZjIyYzcxM2VkMTdiNiJ9fX0="),
    EXPERT("<v>§c§oEXPERT</v>", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmQ1MDYzN2MwZGUyMzdmMDVlZjUxMjM4MzFhZWY5NzA3NmI3ZDVlMTQ3YTQyNWZlMTA0YzliOTQ0ZGIwNGRlMCJ9fX0="),
    EXPERT_PLUS("<v>§d§o§lEXPERT+</v>", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjEwYzc1YzI5ZWE4MjhkYjBmODVjN2FkZDcyODAxZjFhNGY5ZTcwZjI5NDc2ZWFiYTk2OTA2ZjY5YzM1YjBjYSJ9fX0=");

    private final String displayName;
    private final String headBase64;
}
