package ru.sortix.parkourbeat.player;

import lombok.NonNull;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import javax.annotation.Nullable;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerSettingsManager implements PluginManager {
    public static final int[] VOLUME_STEPS = {25, 50, 75, 100};
    public static final int MUSIC_NOTICE_DETAILED_TIMES = 3;

    /**
     * Кто может телепортироваться к игроку и наблюдать за ним (/tptoggle).
     * <p>
     * Это НЕ замена приватности уровня: на приватный уровень посторонний не попадёт
     * в любом случае, здесь регулируется только доступ к самому игроку.
     */
    public enum TeleportAccess {
        ALL("all"),
        FRIENDS("friends"),
        NOBODY("nobody");

        private final @NonNull String langKey;

        TeleportAccess(@NonNull String langKey) {
            this.langKey = langKey;
        }

        @NonNull
        public String getDisplay(@Nullable String locale) {
            return Lang.raw(locale, "settings.teleport." + this.langKey + ".name");
        }

        @NonNull
        public String getDescription(@Nullable String locale) {
            return Lang.raw(locale, "settings.teleport." + this.langKey + ".lore");
        }

        @NonNull
        public TeleportAccess next() {
            return switch (this) {
                case ALL -> FRIENDS;
                case FRIENDS -> NOBODY;
                case NOBODY -> ALL;
            };
        }

        @NonNull
        public static TeleportAccess parse(String value, @NonNull TeleportAccess fallback) {
            if (value == null) return fallback;
            String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "ALL", "ВСЕ", "ON", "TRUE" -> ALL;
                case "FRIENDS", "ДРУЗЬЯ", "FRIEND" -> FRIENDS;
                case "NOBODY", "NONE", "НИКТО", "OFF", "FALSE" -> NOBODY;
                default -> fallback;
            };
        }
    }

    private final @NonNull ParkourBeat plugin;
    private final @NonNull File file;

    private final Map<UUID, Boolean> hidePlayingStatus = new ConcurrentHashMap<>();
    /**
     * Язык интерфейса, выбранный игроком вручную.
     * <p>
     * Отсутствие записи и пустая строка значат одно и то же - «брать язык у клиента»
     * ({@link PlayerLang#AUTO}), поэтому старые файлы настроек читаются как есть.
     */
    private final Map<UUID, String> language = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> musicVolume = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> musicNoticeShown = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> autoAfkMinutes = new ConcurrentHashMap<>();
    /**
     * Кто может смотреть реплеи игрока.
     * <p>
     * Раньше это был флаг "скрыть/не скрыть", поэтому NOBODY соответствует старому true,
     * а ALL - старому false: сохранённые настройки читаются без конвертации.
     */
    public enum ReplayAccess {
        ALL("all"),
        FRIENDS("friends"),
        NOBODY("nobody");

        private final @NonNull String langKey;

        ReplayAccess(@NonNull String langKey) {
            this.langKey = langKey;
        }

        @NonNull
        public String getDisplay(@Nullable String locale) {
            return Lang.raw(locale, "settings.replays." + this.langKey + ".name");
        }

        @NonNull
        public String getDescription(@Nullable String locale) {
            return Lang.raw(locale, "settings.replays." + this.langKey + ".lore");
        }

        @NonNull
        public ReplayAccess next() {
            return switch (this) {
                case ALL -> FRIENDS;
                case FRIENDS -> NOBODY;
                case NOBODY -> ALL;
            };
        }

        @NonNull
        public static ReplayAccess parse(String value, @NonNull ReplayAccess fallback) {
            if (value == null) return fallback;
            try {
                return ReplayAccess.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    private final Map<UUID, Boolean> hideReplays = new ConcurrentHashMap<>();
    private final Map<UUID, ReplayAccess> replayAccess = new ConcurrentHashMap<>();
    /** Закреплённые реплеи: id забегов, которые игрок вывел вперёд в своём профиле. */
    private final Map<UUID, java.util.LinkedHashSet<Long>> pinnedReplays = new ConcurrentHashMap<>();
    private final Map<UUID, TeleportAccess> teleportAccess = new ConcurrentHashMap<>();
    private final Set<UUID> privateBypass = ConcurrentHashMap.newKeySet();

    public PlayerSettingsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player_settings.yml");
        this.load();

        // Язык спрашивают из мест без ссылки на плагин (скорборд, предметы, статические
        // фабрики меню), поэтому мост ставится один раз здесь и снимается в disable().
        PlayerLang.setResolver(this::getLanguage);
    }

    private void load() {
        if (!this.file.exists()) return;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(this.file);
            for (String key : config.getKeys(false)) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                this.hidePlayingStatus.put(playerId, config.getBoolean(key + ".hide_playing_status", false));
                this.language.put(playerId, config.getString(key + ".language", PlayerLang.AUTO));
                this.musicVolume.put(playerId, config.getInt(key + ".music_volume", 100));
                this.musicNoticeShown.put(playerId, config.getInt(key + ".music_notice_shown", 0));
                this.autoAfkMinutes.put(playerId, config.getInt(key + ".auto_afk_minutes", 0));
                this.hideReplays.put(playerId, config.getBoolean(key + ".hide_replays", false));
                this.teleportAccess.put(playerId, TeleportAccess.parse(
                    config.getString(key + ".teleport_access"), TeleportAccess.ALL));

                // Старый булев флаг остаётся источником по умолчанию, если новое поле пустое.
                boolean legacyHidden = config.getBoolean(key + ".hide_replays", false);
                this.replayAccess.put(playerId, ReplayAccess.parse(
                    config.getString(key + ".replay_access"),
                    legacyHidden ? ReplayAccess.NOBODY : ReplayAccess.ALL));

                List<Long> pinned = new java.util.ArrayList<>();
                for (Object raw : config.getList(key + ".pinned_replays", java.util.List.of())) {
                    if (raw instanceof Number number) pinned.add(number.longValue());
                }
                if (!pinned.isEmpty()) {
                    this.pinnedReplays.put(playerId, new java.util.LinkedHashSet<>(pinned));
                }
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to load player settings", e);
        }
    }

    public void save() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            Set<UUID> ids = new HashSet<>();
            ids.addAll(this.hidePlayingStatus.keySet());
            ids.addAll(this.language.keySet());
            ids.addAll(this.musicVolume.keySet());
            ids.addAll(this.musicNoticeShown.keySet());
            ids.addAll(this.autoAfkMinutes.keySet());
            ids.addAll(this.hideReplays.keySet());
            ids.addAll(this.teleportAccess.keySet());
            ids.addAll(this.replayAccess.keySet());
            ids.addAll(this.pinnedReplays.keySet());
            for (UUID playerId : ids) {
                String key = playerId.toString();
                config.set(key + ".hide_playing_status", this.isPlayingStatusHidden(playerId));

                // «Авто» не пишем вовсе: пустой ключ в файле ничем не отличался бы от
                // отсутствующего, а так настройки не пухнут от значений по умолчанию.
                String chosenLanguage = this.getLanguage(playerId);
                config.set(key + ".language", chosenLanguage.isEmpty() ? null : chosenLanguage);
                config.set(key + ".music_volume", this.getMusicVolume(playerId));
                config.set(key + ".music_notice_shown", this.getMusicNoticeShown(playerId));
                config.set(key + ".auto_afk_minutes", this.getAutoAfkMinutes(playerId));
                config.set(key + ".hide_replays", this.areReplaysHidden(playerId));
                config.set(key + ".teleport_access", this.getTeleportAccess(playerId).name());
                config.set(key + ".replay_access", this.getReplayAccess(playerId).name());

                java.util.LinkedHashSet<Long> pinned = this.pinnedReplays.get(playerId);
                config.set(key + ".pinned_replays",
                    pinned == null || pinned.isEmpty() ? null : new java.util.ArrayList<>(pinned));
            }
            File parent = this.file.getParentFile();
            if (parent != null) parent.mkdirs();
            config.save(this.file);
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to save player settings", e);
        }
    }

    public boolean isPlayingStatusHidden(@NonNull UUID playerId) {
        return Boolean.TRUE.equals(this.hidePlayingStatus.get(playerId));
    }

    public void setPlayingStatusHidden(@NonNull UUID playerId, boolean hidden) {
        this.hidePlayingStatus.put(playerId, hidden);
    }

    /**
     * @return код языка из lang.yml либо {@link PlayerLang#AUTO}, если язык берётся у клиента
     */
    @NonNull
    public String getLanguage(@NonNull UUID playerId) {
        String value = this.language.get(playerId);
        return value == null ? PlayerLang.AUTO : value;
    }

    /**
     * @param locale код секции из lang.yml, либо {@link PlayerLang#AUTO} / {@code null}
     *               для возврата к языку клиента
     */
    public void setLanguage(@NonNull UUID playerId, @Nullable String locale) {
        this.language.put(playerId, locale == null ? PlayerLang.AUTO : locale);
    }

    public int getMusicVolume(@NonNull UUID playerId) {
        Integer value = this.musicVolume.get(playerId);
        return value == null ? 100 : value;
    }

    public float getMusicVolumeFactor(@NonNull UUID playerId) {
        return this.getMusicVolume(playerId) / 100.0F;
    }

    public void setMusicVolume(@NonNull UUID playerId, int volume) {
        int closest = VOLUME_STEPS[VOLUME_STEPS.length - 1];
        for (int step : VOLUME_STEPS) {
            if (volume <= step) {
                closest = step;
                break;
            }
        }
        this.musicVolume.put(playerId, closest);
    }

    public int nextVolume(@NonNull UUID playerId) {
        int current = this.getMusicVolume(playerId);
        for (int i = 0; i < VOLUME_STEPS.length; i++) {
            if (VOLUME_STEPS[i] == current) {
                int next = VOLUME_STEPS[(i + 1) % VOLUME_STEPS.length];
                this.musicVolume.put(playerId, next);
                return next;
            }
        }
        this.musicVolume.put(playerId, VOLUME_STEPS[0]);
        return VOLUME_STEPS[0];
    }

    public int getMusicNoticeShown(@NonNull UUID playerId) {
        Integer value = this.musicNoticeShown.get(playerId);
        return value == null ? 0 : value;
    }

    public boolean shouldShowDetailedMusicNotice(@NonNull UUID playerId) {
        return this.getMusicNoticeShown(playerId) < MUSIC_NOTICE_DETAILED_TIMES;
    }

    public void increaseMusicNoticeShown(@NonNull UUID playerId) {
        this.musicNoticeShown.merge(playerId, 1, Integer::sum);
    }

    public int getAutoAfkMinutes(@NonNull UUID playerId) {
        Integer value = this.autoAfkMinutes.get(playerId);
        return value == null ? 0 : value;
    }

    public void setAutoAfkMinutes(@NonNull UUID playerId, int minutes) {
        this.autoAfkMinutes.put(playerId, minutes);
    }

    public boolean areReplaysHidden(@NonNull UUID playerId) {
        return Boolean.TRUE.equals(this.hideReplays.get(playerId));
    }

    public void setReplaysHidden(@NonNull UUID playerId, boolean hidden) {
        this.hideReplays.put(playerId, hidden);
    }

    @NonNull
    public TeleportAccess getTeleportAccess(@NonNull UUID playerId) {
        TeleportAccess value = this.teleportAccess.get(playerId);
        return value == null ? TeleportAccess.ALL : value;
    }

    public void setTeleportAccess(@NonNull UUID playerId, @NonNull TeleportAccess access) {
        this.teleportAccess.put(playerId, access);
    }

    @NonNull
    public TeleportAccess nextTeleportAccess(@NonNull UUID playerId) {
        TeleportAccess next = this.getTeleportAccess(playerId).next();
        this.teleportAccess.put(playerId, next);
        return next;
    }

    /**
     * Может ли {@code viewerId} телепортироваться к {@code targetId} / наблюдать за ним.
     * <p>
     * Проверка только про самого игрока. Доступ к уровню проверяется отдельно и раньше:
     * на приватный уровень посторонний не зайдёт независимо от этой настройки.
     */
    public boolean canTeleportTo(@NonNull UUID viewerId, @NonNull UUID targetId) {
        if (viewerId.equals(targetId)) return true;

        return switch (this.getTeleportAccess(targetId)) {
            case ALL -> true;
            case NOBODY -> false;
            case FRIENDS -> {
                try {
                    ru.sortix.parkourbeat.player.friends.FriendsManager friends =
                        this.plugin.get(ru.sortix.parkourbeat.player.friends.FriendsManager.class);
                    yield friends.areFriends(targetId, viewerId)
                        && friends.allowsFriendTeleport(targetId, viewerId);
                } catch (Exception e) {
                    yield false;
                }
            }
        };
    }

    // ==================== РЕПЛЕИ ====================

    @NonNull
    public ReplayAccess getReplayAccess(@NonNull UUID playerId) {
        ReplayAccess value = this.replayAccess.get(playerId);
        if (value != null) return value;
        // Настройка могла остаться со времён простого флага.
        return this.areReplaysHidden(playerId) ? ReplayAccess.NOBODY : ReplayAccess.ALL;
    }

    @NonNull
    public ReplayAccess nextReplayAccess(@NonNull UUID playerId) {
        ReplayAccess next = this.getReplayAccess(playerId).next();
        this.replayAccess.put(playerId, next);
        // Держим старый флаг в согласии с новым: его читает остальной код.
        this.hideReplays.put(playerId, next == ReplayAccess.NOBODY);
        return next;
    }

    /**
     * Может ли {@code viewerId} смотреть реплеи игрока {@code ownerId}.
     */
    public boolean canWatchReplays(@NonNull UUID viewerId, @NonNull UUID ownerId) {
        if (viewerId.equals(ownerId)) return true;

        return switch (this.getReplayAccess(ownerId)) {
            case ALL -> true;
            case NOBODY -> false;
            case FRIENDS -> {
                try {
                    yield this.plugin.get(ru.sortix.parkourbeat.player.friends.FriendsManager.class)
                        .areFriends(ownerId, viewerId);
                } catch (Exception e) {
                    yield false;
                }
            }
        };
    }

    public boolean isReplayPinned(@NonNull UUID playerId, long runRowId) {
        java.util.LinkedHashSet<Long> pinned = this.pinnedReplays.get(playerId);
        return pinned != null && pinned.contains(runRowId);
    }

    @NonNull
    public java.util.Set<Long> getPinnedReplays(@NonNull UUID playerId) {
        java.util.LinkedHashSet<Long> pinned = this.pinnedReplays.get(playerId);
        return pinned == null ? java.util.Set.of() : java.util.Set.copyOf(pinned);
    }

    /**
     * @return true, если реплей закреплён после вызова
     */
    public boolean toggleReplayPin(@NonNull UUID playerId, long runRowId) {
        java.util.LinkedHashSet<Long> pinned =
            this.pinnedReplays.computeIfAbsent(playerId, id -> new java.util.LinkedHashSet<>());
        if (pinned.remove(runRowId)) return false;
        pinned.add(runRowId);
        return true;
    }

    public void grantPrivateBypass(@NonNull UUID playerId) {
        this.privateBypass.add(playerId);
    }

    public boolean consumePrivateBypass(@NonNull UUID playerId) {
        return this.privateBypass.remove(playerId);
    }

    public boolean hasPrivateBypass(@NonNull UUID playerId) {
        return this.privateBypass.contains(playerId);
    }

    @Override
    public void disable() {
        this.save();
        // Иначе после /pb reload в статике остался бы мост на менеджер прошлого экземпляра.
        PlayerLang.setResolver(null);
    }
}
