package ru.sortix.parkourbeat.levels;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.player.ClientVersionUtils;

import javax.annotation.Nullable;

/**
 * Границы задаются НАЗВАНИЯМИ версий, а номера протоколов берутся у ViaVersion в рантайме.
 * Захардкоженные номера ломались на каждой новой версии игры и просто не пускали игроков.
 */
@Getter
@RequiredArgsConstructor
public enum TextureVersionRange {
    V1_6("1.6 - 1.8.9", null, "1.8.9"),
    V1_9("1.9 - 1.10.2", "1.9", "1.10.2"),
    V1_11("1.11 - 1.12.2", "1.11", "1.12.2"),
    V1_13("1.13 - 1.14.4", "1.13", "1.14.4"),
    V1_15("1.15 - 1.16.1", "1.15", "1.16.1"),
    V1_16_2("1.16.2 - 1.16.5", "1.16.2", "1.16.5"),
    V1_17("1.17 - 1.17.1", "1.17", "1.17.1"),
    V1_18("1.18 - 1.18.2", "1.18", "1.18.2"),
    V1_19("1.19 - 1.19.2", "1.19", "1.19.2"),
    V1_19_3("1.19.3 - 1.20.1", "1.19.3", "1.20.1"),
    V1_20_2("1.20.2 - 1.20.6", "1.20.2", "1.20.6"),
    V1_21("1.21 - 1.21.11", "1.21", "1.21.11"),
    V26("26.1 и новее", "26.1", null);

    private final @NonNull String label;
    private final @Nullable String minVersion;
    private final @Nullable String maxVersion;

    /**
     * Если версию клиента или границу диапазона определить не удалось, игрок пропускается.
     * Лучше пустить лишнего, чем заблокировать всех из-за неизвестного номера протокола.
     */
    public boolean accepts(@NonNull Player player) {
        int protocol = ClientVersionUtils.getProtocol(player);
        if (protocol < 0) return true;

        if (this.minVersion != null) {
            int min = ClientVersionUtils.resolveProtocol(this.minVersion);
            if (min < 0) return true;
            if (protocol < min) return false;
        }

        if (this.maxVersion != null) {
            int max = ClientVersionUtils.resolveProtocol(this.maxVersion);
            if (max < 0) return true;
            if (protocol > max) return false;
        }

        return true;
    }

    @Nullable
    public static TextureVersionRange byName(@Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        for (TextureVersionRange range : values()) {
            if (range.name().equals(name)) return range;
        }
        return null;
    }
}
