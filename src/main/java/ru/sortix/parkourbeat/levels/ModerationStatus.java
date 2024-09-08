package ru.sortix.parkourbeat.levels;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModerationStatus {
    NOT_MODERATED("Черновик"),
    ON_MODERATION("Проходит модерацию"),
    MODERATED("Модерация пройдена");

    private final String displayName;
}
