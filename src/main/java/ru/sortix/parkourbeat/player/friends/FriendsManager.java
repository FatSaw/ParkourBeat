package ru.sortix.parkourbeat.player.friends;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Система друзей.
 * <p>
 * Данные лежат в friends.yml рядом с остальными настройками игроков: отдельная база
 * ради списка друзей не нужна, а YAML переживает перезагрузку плагина и читается глазами.
 * <p>
 * Дружба всегда симметрична: записи создаются и удаляются сразу у обоих. Права при этом
 * односторонние - см. {@link FriendEntry}.
 */
public class FriendsManager implements PluginManager, Listener {
    public static final int MAX_FRIENDS = 100;
    public static final int MAX_REQUESTS = 50;

    /**
     * Результат попытки что-то сделать с дружбой. Текст сообщения выбирает вызывающий код,
     * чтобы одна и та же логика работала и из команды, и из меню.
     */
    public enum Result {
        OK,
        SELF,
        ALREADY_FRIENDS,
        ALREADY_REQUESTED,
        NO_REQUEST,
        NOT_FRIENDS,
        LIMIT_REACHED,
        TARGET_LIMIT_REACHED,
        UNKNOWN_PLAYER
    }

    private final @NonNull ParkourBeat plugin;
    private final @NonNull File file;
    private final Map<UUID, PlayerFriends> profiles = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public FriendsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "friends.yml");
        this.load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Периодическое сохранение: терять список друзей из-за жёсткого падения сервера
        // обиднее, чем раз в минуту записать небольшой файл.
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!this.dirty) return;
            this.dirty = false;
            this.save();
        }, 20L * 60L, 20L * 60L);
    }

    // ==================== ХРАНИЛИЩЕ ====================

    private void load() {
        if (!this.file.isFile()) return;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(this.file);
            for (String key : config.getKeys(false)) {
                UUID playerId = parseUuid(key);
                if (playerId == null) continue;

                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) continue;

                PlayerFriends profile = new PlayerFriends(playerId, section.getString("name", "?"));
                profile.setPrivateLevelsAccess(
                    FriendAccess.parse(section.getString("private_access"), FriendAccess.SELECTED));
                profile.setBuildAccess(
                    FriendAccess.parse(section.getString("build_access"), FriendAccess.NONE));
                profile.setJoinNotifications(section.getBoolean("join_notifications", true));

                ConfigurationSection friends = section.getConfigurationSection("friends");
                if (friends != null) {
                    for (String friendKey : friends.getKeys(false)) {
                        UUID friendId = parseUuid(friendKey);
                        if (friendId == null) continue;
                        ConfigurationSection friendSection = friends.getConfigurationSection(friendKey);
                        if (friendSection == null) continue;

                        FriendEntry entry = new FriendEntry(friendId,
                            friendSection.getString("name", "?"),
                            friendSection.getLong("since", System.currentTimeMillis()));
                        entry.setPrivateAccess(friendSection.getBoolean("private", false));
                        entry.setBuildAccess(friendSection.getBoolean("build", false));
                        entry.setTeleportAccess(friendSection.getBoolean("teleport", true));
                        entry.setJoinNotifications(friendSection.getBoolean("notify", true));
                        profile.getFriends().put(friendId, entry);
                    }
                }

                for (String raw : section.getStringList("outgoing")) {
                    UUID id = parseUuid(raw);
                    if (id != null) profile.getOutgoing().add(id);
                }
                for (String raw : section.getStringList("incoming")) {
                    UUID id = parseUuid(raw);
                    if (id != null) profile.getIncoming().add(id);
                }

                this.profiles.put(playerId, profile);
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to load friends data", e);
        }
    }

    public void save() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            for (PlayerFriends profile : this.profiles.values()) {
                String key = profile.getPlayerId().toString();
                config.set(key + ".name", profile.getPlayerName());
                config.set(key + ".private_access", profile.getPrivateLevelsAccess().name());
                config.set(key + ".build_access", profile.getBuildAccess().name());
                config.set(key + ".join_notifications", profile.isJoinNotifications());

                for (FriendEntry entry : profile.getAllFriends()) {
                    String path = key + ".friends." + entry.getPlayerId();
                    config.set(path + ".name", entry.getPlayerName());
                    config.set(path + ".since", entry.getFriendsSinceMillis());
                    config.set(path + ".private", entry.isPrivateAccess());
                    config.set(path + ".build", entry.isBuildAccess());
                    config.set(path + ".teleport", entry.isTeleportAccess());
                    config.set(path + ".notify", entry.isJoinNotifications());
                }

                config.set(key + ".outgoing", toStringList(profile.getOutgoing()));
                config.set(key + ".incoming", toStringList(profile.getIncoming()));
            }

            File parent = this.file.getParentFile();
            if (parent != null) parent.mkdirs();
            config.save(this.file);
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to save friends data", e);
        }
    }

    private static List<String> toStringList(@NonNull Collection<UUID> ids) {
        List<String> result = new ArrayList<>(ids.size());
        for (UUID id : ids) result.add(id.toString());
        return result;
    }

    @Nullable
    private static UUID parseUuid(@Nullable String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void markDirty() {
        this.dirty = true;
    }

    // ==================== ДОСТУП К ПРОФИЛЯМ ====================

    @NonNull
    public PlayerFriends getProfile(@NonNull UUID playerId, @NonNull String playerName) {
        PlayerFriends profile = this.profiles.computeIfAbsent(playerId,
            id -> new PlayerFriends(id, playerName));
        if (!profile.getPlayerName().equals(playerName)) {
            profile.setPlayerName(playerName);
            this.markDirty();
        }
        return profile;
    }

    @NonNull
    public PlayerFriends getProfile(@NonNull Player player) {
        return this.getProfile(player.getUniqueId(), player.getName());
    }

    /**
     * @return профиль, если игрок вообще появлялся в системе друзей, иначе null
     */
    @Nullable
    public PlayerFriends getProfileIfKnown(@NonNull UUID playerId) {
        return this.profiles.get(playerId);
    }

    public boolean areFriends(@NonNull UUID first, @NonNull UUID second) {
        PlayerFriends profile = this.profiles.get(first);
        return profile != null && profile.isFriend(second);
    }

    @NonNull
    public Collection<FriendEntry> getFriends(@NonNull UUID playerId) {
        PlayerFriends profile = this.profiles.get(playerId);
        return profile == null ? java.util.List.of() : profile.getAllFriends();
    }

    public int getOnlineFriendsCount(@NonNull UUID playerId) {
        int count = 0;
        for (FriendEntry entry : this.getFriends(playerId)) {
            Player online = this.plugin.getServer().getPlayer(entry.getPlayerId());
            if (online != null && online.isOnline()) count++;
        }
        return count;
    }

    public int getIncomingRequestsCount(@NonNull UUID playerId) {
        PlayerFriends profile = this.profiles.get(playerId);
        return profile == null ? 0 : profile.getIncoming().size();
    }

    /**
     * Ищет игрока по нику: сначала среди онлайна, затем среди тех, кто уже известен
     * системе друзей, и в самом конце - среди профилей статистики.
     */
    @Nullable
    public UUID findPlayerIdByName(@NonNull String name) {
        Player online = this.plugin.getServer().getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        for (PlayerFriends profile : this.profiles.values()) {
            if (profile.getPlayerName().equalsIgnoreCase(name)) return profile.getPlayerId();
            for (FriendEntry entry : profile.getAllFriends()) {
                if (entry.getPlayerName().equalsIgnoreCase(name)) return entry.getPlayerId();
            }
        }

        try {
            ru.sortix.parkourbeat.stats.PlayerProfile statsProfile = this.plugin
                .get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
                .findProfileByName(name);
            if (statsProfile != null) return statsProfile.getPlayerId();
        } catch (Exception ignored) {
        }

        return null;
    }

    @NonNull
    public String getKnownName(@NonNull UUID playerId) {
        Player online = this.plugin.getServer().getPlayer(playerId);
        if (online != null) return online.getName();

        PlayerFriends profile = this.profiles.get(playerId);
        if (profile != null) return profile.getPlayerName();

        for (PlayerFriends other : this.profiles.values()) {
            FriendEntry entry = other.getFriend(playerId);
            if (entry != null) return entry.getPlayerName();
        }

        try {
            ru.sortix.parkourbeat.stats.PlayerProfile statsProfile = this.plugin
                .get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
                .getProfileIfKnown(playerId);
            if (statsProfile != null) return statsProfile.getPlayerName();
        } catch (Exception ignored) {
        }

        return "?";
    }

    // ==================== ЗАЯВКИ ====================

    /**
     * Отправляет заявку. Если встречная заявка уже есть - дружба оформляется сразу,
     * без второго подтверждения: обе стороны уже согласны.
     */
    @NonNull
    public Result sendRequest(@NonNull Player sender, @NonNull UUID targetId) {
        UUID senderId = sender.getUniqueId();
        if (senderId.equals(targetId)) return Result.SELF;

        PlayerFriends senderProfile = this.getProfile(sender);
        if (senderProfile.isFriend(targetId)) return Result.ALREADY_FRIENDS;
        if (senderProfile.getFriendsCount() >= MAX_FRIENDS) return Result.LIMIT_REACHED;

        String targetName = this.getKnownName(targetId);
        PlayerFriends targetProfile = this.getProfile(targetId, targetName);
        if (targetProfile.getFriendsCount() >= MAX_FRIENDS) return Result.TARGET_LIMIT_REACHED;

        // Встречная заявка - сразу друзья.
        if (senderProfile.getIncoming().contains(targetId)) {
            this.acceptRequest(sender, targetId);
            return Result.OK;
        }

        if (senderProfile.getOutgoing().contains(targetId)) return Result.ALREADY_REQUESTED;
        if (targetProfile.getIncoming().size() >= MAX_REQUESTS) return Result.TARGET_LIMIT_REACHED;

        senderProfile.getOutgoing().add(targetId);
        targetProfile.getIncoming().add(senderId);
        this.markDirty();

        Player target = this.plugin.getServer().getPlayer(targetId);
        if (target != null && target.isOnline()) {
            target.sendMessage(Component.empty());
            target.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.friends_manager.send_request.1")));
            target.sendMessage(PbText.of("&f" + sender.getName() + Lang.raw(PlayerLang.of(sender), "auto.friends_manager.send_request.2")));
            target.sendMessage(PbText.of("&a/friend accept " + sender.getName()
                + Lang.raw(PlayerLang.of(sender), "auto.friends_manager.send_request.3") + sender.getName() + Lang.raw(PlayerLang.of(sender), "auto.friends_manager.send_request.4")));
            target.sendMessage(Component.empty());
        }

        return Result.OK;
    }

    @NonNull
    public Result acceptRequest(@NonNull Player accepter, @NonNull UUID senderId) {
        UUID accepterId = accepter.getUniqueId();
        if (accepterId.equals(senderId)) return Result.SELF;

        PlayerFriends accepterProfile = this.getProfile(accepter);
        if (accepterProfile.isFriend(senderId)) return Result.ALREADY_FRIENDS;
        if (!accepterProfile.getIncoming().contains(senderId)) return Result.NO_REQUEST;
        if (accepterProfile.getFriendsCount() >= MAX_FRIENDS) return Result.LIMIT_REACHED;

        String senderName = this.getKnownName(senderId);
        PlayerFriends senderProfile = this.getProfile(senderId, senderName);
        if (senderProfile.getFriendsCount() >= MAX_FRIENDS) return Result.TARGET_LIMIT_REACHED;

        accepterProfile.getIncoming().remove(senderId);
        senderProfile.getOutgoing().remove(accepterId);
        // Заявка могла быть встречной - тогда чистим и её.
        accepterProfile.getOutgoing().remove(senderId);
        senderProfile.getIncoming().remove(accepterId);

        long now = System.currentTimeMillis();
        accepterProfile.getFriends().put(senderId, new FriendEntry(senderId, senderName, now));
        senderProfile.getFriends().put(accepterId,
            new FriendEntry(accepterId, accepter.getName(), now));
        this.markDirty();

        accepter.sendMessage(PbText.of(Lang.raw(PlayerLang.of(accepter), "auto.friends_manager.accept_request.1") + senderName));

        Player sender = this.plugin.getServer().getPlayer(senderId);
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(PbText.of("&f" + accepter.getName() + Lang.raw(PlayerLang.of(accepter), "auto.friends_manager.accept_request.2")));
        }

        return Result.OK;
    }

    @NonNull
    public Result denyRequest(@NonNull Player player, @NonNull UUID senderId) {
        PlayerFriends profile = this.getProfile(player);
        if (!profile.getIncoming().remove(senderId)) return Result.NO_REQUEST;

        PlayerFriends senderProfile = this.profiles.get(senderId);
        if (senderProfile != null) senderProfile.getOutgoing().remove(player.getUniqueId());
        this.markDirty();
        return Result.OK;
    }

    @NonNull
    public Result cancelRequest(@NonNull Player player, @NonNull UUID targetId) {
        PlayerFriends profile = this.getProfile(player);
        if (!profile.getOutgoing().remove(targetId)) return Result.NO_REQUEST;

        PlayerFriends targetProfile = this.profiles.get(targetId);
        if (targetProfile != null) targetProfile.getIncoming().remove(player.getUniqueId());
        this.markDirty();
        return Result.OK;
    }

    @NonNull
    public Result removeFriend(@NonNull Player player, @NonNull UUID friendId) {
        PlayerFriends profile = this.getProfile(player);
        if (profile.getFriends().remove(friendId) == null) return Result.NOT_FRIENDS;

        PlayerFriends friendProfile = this.profiles.get(friendId);
        if (friendProfile != null) friendProfile.getFriends().remove(player.getUniqueId());
        this.markDirty();

        Player friend = this.plugin.getServer().getPlayer(friendId);
        if (friend != null && friend.isOnline()) {
            friend.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.friends_manager.remove_friend.1") + player.getName() + Lang.raw(PlayerLang.of(player), "auto.friends_manager.remove_friend.2")));
        }
        return Result.OK;
    }

    // ==================== ПРАВА ====================

    /**
     * Может ли {@code visitorId} зайти на приватный уровень игрока {@code ownerId}
     * по дружбе (без учёта прав администрации и соредакторов - это проверяется отдельно).
     */
    public boolean canVisitPrivateLevels(@NonNull UUID ownerId, @NonNull UUID visitorId) {
        PlayerFriends owner = this.profiles.get(ownerId);
        if (owner == null) return false;
        FriendEntry entry = owner.getFriend(visitorId);
        if (entry == null) return false;
        return owner.getPrivateLevelsAccess().allows(entry.isPrivateAccess());
    }

    /**
     * Может ли друг строить на уровнях владельца.
     */
    public boolean canBuildOnLevels(@NonNull UUID ownerId, @NonNull UUID builderId) {
        PlayerFriends owner = this.profiles.get(ownerId);
        if (owner == null) return false;
        FriendEntry entry = owner.getFriend(builderId);
        if (entry == null) return false;
        return owner.getBuildAccess().allows(entry.isBuildAccess());
    }

    /**
     * Разрешил ли конкретный друг телепорт к себе лично.
     * Общий режим телепортов лежит в настройках игрока, здесь только персональный флаг.
     */
    public boolean allowsFriendTeleport(@NonNull UUID ownerId, @NonNull UUID visitorId) {
        PlayerFriends owner = this.profiles.get(ownerId);
        if (owner == null) return false;
        FriendEntry entry = owner.getFriend(visitorId);
        return entry != null && entry.isTeleportAccess();
    }

    // ==================== ОНЛАЙН-СТАТУС ====================

    /**
     * Короткое описание того, чем друг занят прямо сейчас - для списка друзей и таба.
     */
    @NonNull
    public String describeStatus(@NonNull UUID playerId, String locale) {
        Player online = this.plugin.getServer().getPlayer(playerId);
        if (online == null || !online.isOnline()) {
            return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "friends.status.offline");
        }

        try {
            if (this.plugin.get(ru.sortix.parkourbeat.player.AfkManager.class).isAfk(playerId)) {
                return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "friends.status.afk");
            }
        } catch (Exception ignored) {
        }

        try {
            boolean hidden = this.plugin.get(ru.sortix.parkourbeat.player.PlayerSettingsManager.class)
                .isPlayingStatusHidden(playerId);

            ru.sortix.parkourbeat.activity.UserActivity activity =
                this.plugin.get(ru.sortix.parkourbeat.activity.ActivityManager.class).getActivity(online);

            if (activity == null) {
                return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "friends.status.lobby");
            }
            if (hidden) {
                return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "friends.status.online");
            }

            String levelName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().serialize(activity.getLevel().getDisplayName());

            if (activity instanceof ru.sortix.parkourbeat.activity.type.EditActivity) {
                return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale,
                    "friends.status.editing", "%level%", levelName);
            }
            if (activity instanceof ru.sortix.parkourbeat.activity.type.ReplayActivity) {
                return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "friends.status.replay");
            }
            if (activity instanceof ru.sortix.parkourbeat.activity.type.SpectateActivity) {
                return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale,
                    "friends.status.spectating", "%level%", levelName);
            }
            return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale,
                "friends.status.playing", "%level%", levelName);
        } catch (Exception e) {
            return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "friends.status.online");
        }
    }

    // ==================== СОБЫТИЯ ====================

    @EventHandler
    public void onJoin(@NonNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Ник мог поменяться - профиль сам себя обновит.
        this.getProfile(player);

        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline()) return;
            this.notifyFriends(player, true);
            this.notifyAboutRequests(player);
        }, 40L);
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        this.notifyFriends(event.getPlayer(), false);
    }

    private void notifyFriends(@NonNull Player player, boolean joined) {
        for (FriendEntry entry : this.getFriends(player.getUniqueId())) {
            Player friend = this.plugin.getServer().getPlayer(entry.getPlayerId());
            if (friend == null || !friend.isOnline()) continue;

            PlayerFriends friendProfile = this.profiles.get(entry.getPlayerId());
            if (friendProfile == null || !friendProfile.isJoinNotifications()) continue;

            FriendEntry back = friendProfile.getFriend(player.getUniqueId());
            if (back != null && !back.isJoinNotifications()) continue;

            friend.sendMessage(joined
                ? PbText.of(Lang.raw(PlayerLang.of(player), "auto.friends_manager.notify_friends.1") + player.getName() + Lang.raw(PlayerLang.of(player), "auto.friends_manager.notify_friends.2"))
                : PbText.of(Lang.raw(PlayerLang.of(player), "auto.friends_manager.notify_friends.3") + player.getName() + Lang.raw(PlayerLang.of(player), "auto.friends_manager.notify_friends.4")));
        }
    }

    private void notifyAboutRequests(@NonNull Player player) {
        PlayerFriends profile = this.profiles.get(player.getUniqueId());
        if (profile == null || profile.getIncoming().isEmpty()) return;

        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.friends_manager.notify_about_requests.1") + profile.getIncoming().size()
            + Lang.raw(PlayerLang.of(player), "auto.friends_manager.notify_about_requests.2")));
    }

    /**
     * Пинг всем друзьям, что игрок начал забег. Дёргается из статистики/игры при желании -
     * сейчас используется списком друзей для актуального статуса без своего таймера.
     */
    @NonNull
    public List<Player> getOnlineFriends(@NonNull UUID playerId) {
        List<Player> result = new ArrayList<>();
        for (FriendEntry entry : this.getFriends(playerId)) {
            Player online = this.plugin.getServer().getPlayer(entry.getPlayerId());
            if (online != null && online.isOnline()) result.add(online);
        }
        return result;
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        this.save();
    }

    /**
     * Утилита для меню и команд: полное имя игрока с рангом, как в табе.
     */
    @NonNull
    public String getDisplayName(@NonNull UUID playerId) {
        String name = this.getKnownName(playerId);
        try {
            String rank = this.plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class)
                .getRankLabel(playerId);
            return rank + " &f" + name;
        } catch (Exception e) {
            return "&f" + name;
        }
    }

    @NonNull
    public ParkourBeat getPlugin() {
        return this.plugin;
    }
}
