// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/replay/ReplayJump.java
package ru.sortix.parkourbeat.replay;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.sortix.parkourbeat.rating.JumpResult;

@Getter
@RequiredArgsConstructor
public class ReplayJump {
    private final int frameIndex;
    private final JumpResult result;
}
