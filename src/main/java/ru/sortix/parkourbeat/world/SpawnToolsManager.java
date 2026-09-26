// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/world/SpawnToolsManager.java
package ru.sortix.parkourbeat.world;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.*;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.ConfigUtils;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class SpawnToolsManager implements PluginManager, Listener {

    private static final Material CARPET_MATERIAL = Material.PURPLE_CARPET;
    private static final Particle CARPET_PARTICLE = Particle.PORTAL;
    private static final String RIDE_TAG = "pb_moveblock_ride";
    private static final String NPC_TAG = "pb_lobby_npc";
    private static final double RIDE_HEIGHT = 1.0d;
    private static final double DEFAULT_SPEED = 0.35d;
    private static final double DEFAULT_ARC = 0.15d;
    private static final int RING_POINTS = 10;
    private static final double RING_RADIUS = 0.62d;
    private static final double MAX_DESYNC = 6.0d;

    private static final int SKIN_LAYERS_INDEX = resolveSkinLayersIndex();

    private final @NonNull ParkourBeat plugin;
    private final File file;
    private final BlockData carpetData;

    private final Map<String, LaunchPad> pads = new HashMap<>();
    private final Map<String, LobbyParkour> parkours = new HashMap<>();
    private final Map<String, MoveBlock> moveBlocks = new HashMap<>();
    private final Map<String, LobbyNpc> npcs = new HashMap<>();

    private final Map<UUID, LaunchPad> launchedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, ParkourSession> parkourSessions = new ConcurrentHashMap<>();
    private final Map<UUID, MoveSession> movingPlayers = new ConcurrentHashMap<>();

    private final BukkitTask tickTask;
    private PacketAdapter npcClickListener;
    private int tickCounter = 0;

    public record LaunchPad(double strength, double speed, Particle particle, int amount) {}

    public static class LobbyParkour {
        public Location start;
        public Location finish;
        public Particle particle;
        public int amount;
    }

    public static class ParkourSession {
        public String id;
        public long startTime;
        public int shiftTicks;

        public ParkourSession(String id) {
            this.id = id;
            this.startTime = System.currentTimeMillis();
            this.shiftTicks = 0;
        }
    }

    public static class MoveBlock {
        public Location a;
        public Location b;
        public double speed;
        public Particle particle;
        public int amount;
        public double arc = DEFAULT_ARC;
    }

    public static class MoveSession {
        public final MoveBlock moveBlock;
        public final ArmorStand seat;
        public final FallingBlock carpet;
        public final Vector from;
        public final Vector control;
        public final Vector to;
        public final double length;
        public double progress = 0.0d;
        public int ticks = 0;

        public MoveSession(MoveBlock moveBlock, ArmorStand seat, FallingBlock carpet, Vector from, Vector control, Vector to, double length) {
            this.moveBlock = moveBlock;
            this.seat = seat;
            this.carpet = carpet;
            this.from = from;
            this.control = control;
            this.to = to;
            this.length = length;
        }

        public void despawn() {
            if (this.carpet != null && !this.carpet.isDead()) this.carpet.remove();
            if (this.seat != null && !this.seat.isDead()) this.seat.remove();
        }
    }

    public static class LobbyNpc {
        public String levelId;
        public Location location;
        public String skinValue;
        public String skinSignature;

        public transient int entityId;
        public transient UUID uuid;
        public transient ArmorStand holoName;
        public transient ArmorStand holoDiff;
        public transient Set<UUID> viewers = ConcurrentHashMap.newKeySet();

        public LobbyNpc() {
            this.entityId = ThreadLocalRandom.current().nextInt(2000000, 9000000);
            this.uuid = UUID.randomUUID();
        }

        public void despawnAll(ParkourBeat plugin) {
            if (holoName != null) holoName.remove();
            if (holoDiff != null) holoDiff.remove();

            for (UUID viewerId : viewers) {
                Player player = Bukkit.getPlayer(viewerId);
                if (player != null) sendHidePackets(player);
            }
            viewers.clear();
        }

        public void spawnBukkitEntities(GameSettings gs) {
            if (location == null || location.getWorld() == null) return;

            holoName = location.getWorld().spawn(location.clone().add(0, 2.25, 0), ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(true);
                as.setCustomNameVisible(true);
                as.customName(gs.getDisplayName().colorIfAbsent(NamedTextColor.WHITE));
                as.addScoreboardTag(NPC_TAG);
            });

            holoDiff = location.getWorld().spawn(location.clone().add(0, 1.95, 0), ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(true);
                as.setCustomNameVisible(true);
                as.customName(PbText.of("Сложность: " + gs.getDifficulty().getDisplayName()));
                as.addScoreboardTag(NPC_TAG);
            });
        }

        public void sendShowPackets(Player viewer, GameSettings gs) {
            try {
                ProtocolManager pm = ProtocolLibrary.getProtocolManager();
                WrappedGameProfile profile = new WrappedGameProfile(uuid, "");

                if (skinValue != null && skinSignature != null) {
                    profile.getProperties().put("textures", new WrappedSignedProperty("textures", skinValue, skinSignature));
                }

                PacketContainer info = pm.createPacket(PacketType.Play.Server.PLAYER_INFO);
                info.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
                info.getPlayerInfoDataLists().write(0, Collections.singletonList(
                    new PlayerInfoData(profile, 0, EnumWrappers.NativeGameMode.SURVIVAL, WrappedChatComponent.fromText(""))
                ));
                pm.sendServerPacket(viewer, info);

                PacketContainer spawn = pm.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
                spawn.getIntegers().write(0, entityId);
                spawn.getUUIDs().write(0, uuid);
                spawn.getDoubles().write(0, location.getX()).write(1, location.getY()).write(2, location.getZ());
                spawn.getBytes().write(0, (byte) (location.getYaw() * 256.0F / 360.0F)).write(1, (byte) (location.getPitch() * 256.0F / 360.0F));
                pm.sendServerPacket(viewer, spawn);

                PacketContainer head = pm.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
                head.getIntegers().write(0, entityId);
                head.getBytes().write(0, (byte) (location.getYaw() * 256.0F / 360.0F));
                pm.sendServerPacket(viewer, head);

                PacketContainer metadata = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);
                metadata.getIntegers().write(0, entityId);
                List<WrappedWatchableObject> values = new ArrayList<>();
                values.add(new WrappedWatchableObject(new WrappedDataWatcher.WrappedDataWatcherObject(SKIN_LAYERS_INDEX, WrappedDataWatcher.Registry.get(Byte.class)), (byte) 0x7F));
                metadata.getWatchableCollectionModifier().write(0, values);
                pm.sendServerPacket(viewer, metadata);

                PacketContainer equip = pm.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
                equip.getIntegers().write(0, entityId);
                List<com.comphenix.protocol.wrappers.Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();
                equipment.add(new com.comphenix.protocol.wrappers.Pair<>(EnumWrappers.ItemSlot.HEAD, UIHeads.PLAY));
                equip.getSlotStackPairLists().write(0, equipment);
                pm.sendServerPacket(viewer, equip);

                // Прячем из таба через 10 тиков
                ParkourBeat.getPlugin(ParkourBeat.class).getServer().getScheduler().runTaskLaterAsynchronously(ParkourBeat.getPlugin(ParkourBeat.class), () -> {
                    if (!viewer.isOnline()) return;
                    try {
                        PacketContainer removeInfo = pm.createPacket(PacketType.Play.Server.PLAYER_INFO);
                        removeInfo.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
                        removeInfo.getPlayerInfoDataLists().write(0, Collections.singletonList(
                            new PlayerInfoData(profile, 0, EnumWrappers.NativeGameMode.SURVIVAL, WrappedChatComponent.fromText(""))
                        ));
                        pm.sendServerPacket(viewer, removeInfo);
                    } catch (Throwable ignored) {}
                }, 10L);

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        public void sendHidePackets(Player viewer) {
            try {
                ProtocolManager pm = ProtocolLibrary.getProtocolManager();
                PacketContainer destroy = pm.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                try {
                    destroy.getIntLists().write(0, Collections.singletonList(entityId));
                } catch (Throwable t) {
                    destroy.getIntegerArrays().write(0, new int[]{entityId});
                }
                pm.sendServerPacket(viewer, destroy);
            } catch (Throwable ignored) {}
        }
    }

    public SpawnToolsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawntools.yml");
        this.carpetData = CARPET_MATERIAL.createBlockData();
        this.load();
        this.removeStrayEntities();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickTasks, 1L, 1L);
        this.registerNpcClickListener();
    }

    private void registerNpcClickListener() {
        this.npcClickListener = new PacketAdapter(plugin, com.comphenix.protocol.events.ListenerPriority.NORMAL, PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                try {
                    int entityId = event.getPacket().getIntegers().read(0);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        for (LobbyNpc npc : npcs.values()) {
                            if (npc.entityId == entityId) {
                                event.getPlayer().performCommand("play " + npc.levelId);
                                break;
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(this.npcClickListener);
    }

    private void removeStrayEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(RIDE_TAG) || entity.getScoreboardTags().contains(NPC_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private Location readLocationSafely(ConfigurationSection sec, String path) {
        if (!sec.contains(path)) return null;

        Location result = null;
        if (sec.isString(path)) {
            try {
                result = ConfigUtils.parseLocation(false, sec.getString(path));
            } catch (Exception ignored) {}
        } else if (sec.isConfigurationSection(path)) {
            ConfigurationSection locSec = sec.getConfigurationSection(path);
            try {
                result = new Location(null,
                    locSec.getDouble("x"),
                    locSec.getDouble("y"),
                    locSec.getDouble("z"),
                    (float) locSec.getDouble("yaw", 0),
                    (float) locSec.getDouble("pitch", 0));
            } catch (Exception ignored) {}
        } else {
            try {
                result = sec.getLocation(path);
            } catch (Exception ignored) {}
        }

        if (result != null) {
            if (result.getWorld() == null && Settings.getLobbySpawn() != null) {
                result.setWorld(Settings.getLobbySpawn().getWorld());
            }
        }
        return result;
    }

    private void load() {
        if (!this.file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(this.file);

        ConfigurationSection padsSec = config.getConfigurationSection("pads");
        if (padsSec != null) {
            for (String key : padsSec.getKeys(false)) {
                try {
                    String cleanKey = key.replace(':', '_');
                    pads.put(cleanKey, new LaunchPad(
                        padsSec.getDouble(key + ".strength"),
                        padsSec.getDouble(key + ".speed"),
                        Particle.valueOf(padsSec.getString(key + ".particle")),
                        padsSec.getInt(key + ".amount")));
                } catch (Exception ignored) {}
            }
        }

        ConfigurationSection pkSec = config.getConfigurationSection("parkours");
        if (pkSec != null) {
            for (String key : pkSec.getKeys(false)) {
                try {
                    LobbyParkour pk = new LobbyParkour();
                    pk.start = readLocationSafely(pkSec, key + ".start");
                    pk.finish = readLocationSafely(pkSec, key + ".finish");
                    if (pkSec.contains(key + ".particle")) pk.particle = Particle.valueOf(pkSec.getString(key + ".particle"));
                    if (pkSec.contains(key + ".amount")) pk.amount = pkSec.getInt(key + ".amount");
                    parkours.put(key, pk);
                } catch (Exception ignored) {}
            }
        }

        ConfigurationSection mbSec = config.getConfigurationSection("moveblocks");
        if (mbSec != null) {
            for (String key : mbSec.getKeys(false)) {
                try {
                    MoveBlock mb = new MoveBlock();
                    mb.a = readLocationSafely(mbSec, key + ".a");
                    mb.b = readLocationSafely(mbSec, key + ".b");
                    if (mbSec.contains(key + ".speed")) mb.speed = mbSec.getDouble(key + ".speed");
                    if (mbSec.contains(key + ".particle")) mb.particle = Particle.valueOf(mbSec.getString(key + ".particle"));
                    if (mbSec.contains(key + ".amount")) mb.amount = mbSec.getInt(key + ".amount");
                    if (mbSec.contains(key + ".arc")) mb.arc = mbSec.getDouble(key + ".arc");
                    moveBlocks.put(key, mb);
                } catch (Exception ignored) {}
            }
        }

        ConfigurationSection npcSec = config.getConfigurationSection("npcs");
        if (npcSec != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (String key : npcSec.getKeys(false)) {
                    try {
                        LobbyNpc npc = new LobbyNpc();
                        npc.levelId = key;
                        npc.location = readLocationSafely(npcSec, key + ".location");
                        npc.skinValue = npcSec.getString(key + ".skin.value");
                        npc.skinSignature = npcSec.getString(key + ".skin.signature");

                        GameSettings gs = plugin.get(LevelsManager.class).findLevel(npc.levelId);
                        if (gs != null) {
                            npcs.put(key, npc);
                            npc.spawnBukkitEntities(gs);
                        }
                    } catch (Exception ignored) {}
                }
            }, 60L);
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, LaunchPad> entry : pads.entrySet()) {
            String key = "pads." + entry.getKey();
            config.set(key + ".strength", entry.getValue().strength());
            config.set(key + ".speed", entry.getValue().speed());
            config.set(key + ".particle", entry.getValue().particle().name());
            config.set(key + ".amount", entry.getValue().amount());
        }

        for (Map.Entry<String, LobbyParkour> entry : parkours.entrySet()) {
            String key = "parkours." + entry.getKey();
            if (entry.getValue().start != null) config.set(key + ".start", ConfigUtils.serializeLocation(false, entry.getValue().start));
            if (entry.getValue().finish != null) config.set(key + ".finish", ConfigUtils.serializeLocation(false, entry.getValue().finish));
            if (entry.getValue().particle != null) config.set(key + ".particle", entry.getValue().particle.name());
            config.set(key + ".amount", entry.getValue().amount);
        }

        for (Map.Entry<String, MoveBlock> entry : moveBlocks.entrySet()) {
            String key = "moveblocks." + entry.getKey();
            if (entry.getValue().a != null) config.set(key + ".a", ConfigUtils.serializeLocation(false, entry.getValue().a));
            if (entry.getValue().b != null) config.set(key + ".b", ConfigUtils.serializeLocation(false, entry.getValue().b));
            config.set(key + ".speed", entry.getValue().speed);
            if (entry.getValue().particle != null) config.set(key + ".particle", entry.getValue().particle.name());
            config.set(key + ".amount", entry.getValue().amount);
            config.set(key + ".arc", entry.getValue().arc);
        }

        for (Map.Entry<String, LobbyNpc> entry : npcs.entrySet()) {
            String key = "npcs." + entry.getKey();
            if (entry.getValue().location != null) config.set(key + ".location", ConfigUtils.serializeLocation(false, entry.getValue().location));
            if (entry.getValue().skinValue != null) config.set(key + ".skin.value", entry.getValue().skinValue);
            if (entry.getValue().skinSignature != null) config.set(key + ".skin.signature", entry.getValue().skinSignature);
        }

        try { config.save(this.file); } catch (Exception ignored) {}
    }

    public void handleNpcCommand(Player player, String[] args) {
        LevelsManager lm = plugin.get(LevelsManager.class);

        if (args.length == 0 || (args.length == 1 && isNumeric(args[0]) && lm.findLevel(args[0]) == null)) {
            int page = args.length == 1 ? Integer.parseInt(args[0]) : 1;
            List<GameSettings> rated = lm.getAvailableLevelsSettings().stream()
                .filter(gs -> gs.getDifficulty() != null && gs.getDifficulty() != LevelDifficulty.N_A)
                .sorted(Comparator.comparing(GameSettings::getDifficulty).reversed())
                .collect(Collectors.toList());

            if (rated.isEmpty()) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.1")));
                return;
            }

            int perPage = 10;
            int maxPages = (int) Math.ceil(rated.size() / (double) perPage);
            if (page < 1) page = 1;
            if (page > maxPages) page = maxPages;

            player.sendMessage(Component.empty());
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.2") + page + Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.3") + maxPages + ")"));
            int start = (page - 1) * perPage;
            for (int i = start; i < Math.min(start + perPage, rated.size()); i++) {
                GameSettings gs = rated.get(i);
                String diffColor = gs.getDifficulty().getDisplayName().replace("<v>", "").replace("</v>", "");
                String msg = "&8- " + diffColor + " &8| &f" + gs.getDisplayNameLegacy(false) + " &8| &7ID: " + gs.getUniqueNumber();

                player.sendMessage(Component.text()
                    .append(LegacyComponentSerializer.legacyAmpersand().deserialize(msg))
                    .clickEvent(ClickEvent.runCommand("/spawntools npc " + gs.getUniqueId().toString()))
                    .hoverEvent(HoverEvent.showText(Component.text(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.4"), NamedTextColor.GREEN)))
                    .build());
            }
            player.sendMessage(Component.empty());
            return;
        }

        String levelIdOrNumber = args[0];
        GameSettings settings = lm.findLevel(levelIdOrNumber);
        if (settings == null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.5")));
            return;
        }

        String realId = settings.getUniqueId().toString();

        if (args.length == 1) {
            createOrMoveNpc(player.getLocation(), settings);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.6") + settings.getDisplayNameLegacy(false) + Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.7")));
            return;
        }

        String subCmd = args[1].toLowerCase();
        LobbyNpc npc = npcs.get(realId);

        if (subCmd.equals("remove")) {
            if (npc != null) {
                npc.despawnAll(plugin);
                npcs.remove(realId);
                save();
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.8")));
            } else {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.9")));
            }
        } else if (subCmd.equals("tphere")) {
            createOrMoveNpc(player.getLocation(), settings);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.10")));
        } else if (subCmd.equals("skin") && args.length >= 3) {
            String skinName = args[2];
            if (npc == null) {
                createOrMoveNpc(player.getLocation(), settings);
                npc = npcs.get(realId);
            }
            fetchAndSetSkin(npc, skinName, player, settings);
        } else {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.handle_npc_command.11")));
        }
    }

    private void createOrMoveNpc(Location loc, GameSettings gs) {
        String realId = gs.getUniqueId().toString();
        LobbyNpc npc = npcs.get(realId);
        if (npc != null) {
            npc.despawnAll(plugin);
        } else {
            npc = new LobbyNpc();
            npc.levelId = realId;
        }
        npc.location = loc.clone();
        npcs.put(realId, npc);
        npc.spawnBukkitEntities(gs);
        save();
    }

    private void fetchAndSetSkin(LobbyNpc npc, String skinName, Player player, GameSettings gs) {
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.1") + skinName + "&7..."));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.net.URL url = new java.net.URL("https://mc-api.io/profile/" + skinName + "/JAVA");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "ParkourBeat-Server");

                int responseCode = connection.getResponseCode();
                if (responseCode == 404) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.2") + skinName + Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.3")));
                    });
                    return;
                } else if (responseCode != 200) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.4") + responseCode + Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.5") + skinName));
                    });
                    return;
                }

                java.io.InputStreamReader reader = new java.io.InputStreamReader(connection.getInputStream());

                // ИСПРАВЛЕНИЕ: Используем old-school способ парсинга для старых версий GSON
                JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                reader.close();

                if (!json.has("textures")) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.6") + skinName + Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.7")));
                    });
                    return;
                }

                JsonObject textures = json.getAsJsonObject("textures");
                String tex = textures.has("value") ? textures.get("value").getAsString() : null;
                String sig = textures.has("signature") ? textures.get("signature").getAsString() : null;

                if (tex != null && sig != null) {
                    npc.skinValue = tex;
                    npc.skinSignature = sig;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        npc.despawnAll(plugin);
                        npc.spawnBukkitEntities(gs);
                        save();
                        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.8") + skinName + Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.9")));
                    });
                } else {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.10") + skinName + Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.11")));
                    });
                }
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.fetch_and_set_skin.12") + e.getMessage()));
                });
            }
        });
    }
    private boolean isNumeric(String str) {
        try { Integer.parseInt(str); return true; } catch (Exception e) { return false; }
    }

    private String locToString(Location loc) {
        return loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    private Location parseKeyToLoc(String key, World world) {
        String[] parts = key.split("_");
        if (parts.length != 3) return null;
        try { return new Location(world, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])); }
        catch (Exception e) { return null; }
    }

    public void addPad(Location loc, double strength, double speed, Particle particle, int amount) {
        pads.put(locToString(loc), new LaunchPad(strength, speed, particle, amount));
        save();
    }

    public boolean removePad(Location loc) {
        boolean removed = pads.remove(locToString(loc)) != null;
        if (removed) save();
        return removed;
    }

    public Map<String, LaunchPad> getAllPads() { return Collections.unmodifiableMap(pads); }
    public Map<String, LobbyParkour> getAllParkours() { return Collections.unmodifiableMap(parkours); }
    public Map<String, MoveBlock> getAllMoveBlocks() { return Collections.unmodifiableMap(moveBlocks); }

    public void setParkourStart(String id, Location loc) {
        LobbyParkour pk = parkours.computeIfAbsent(id, k -> new LobbyParkour());
        pk.start = loc;
        save();
    }

    public void setParkourFinish(String id, Location loc, Particle particle, int amount) {
        LobbyParkour pk = parkours.computeIfAbsent(id, k -> new LobbyParkour());
        pk.finish = loc;
        pk.particle = particle;
        pk.amount = amount;
        save();
    }

    public void setMoveBlockA(String id, Location loc) {
        MoveBlock mb = moveBlocks.computeIfAbsent(id, k -> new MoveBlock());
        mb.a = loc;
        save();
    }

    public void setMoveBlockB(String id, Location loc, double speed, Particle particle, int amount) {
        MoveBlock mb = moveBlocks.computeIfAbsent(id, k -> new MoveBlock());
        mb.b = loc;
        mb.speed = speed;
        mb.particle = particle;
        mb.amount = amount;
        save();
    }

    private void tickTasks() {
        tickCounter++;
        this.tickLaunchTrails();
        this.tickParkourSessions();
        this.tickRides();
        this.tickNpcs();
        if (tickCounter % 5 == 0) {
            this.tickHighlightParticles();
        }
    }

    private void tickNpcs() {
        if (tickCounter % 20 != 0) return; // Раз в секунду

        Location lobbySpawn = Settings.getLobbySpawn();
        if (lobbySpawn == null || lobbySpawn.getWorld() == null) return;

        for (LobbyNpc npc : npcs.values()) {
            if (npc.location == null || npc.location.getWorld() == null) continue;
            GameSettings gs = plugin.get(LevelsManager.class).findLevel(npc.levelId);
            if (gs == null) continue;

            for (Player player : lobbySpawn.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(npc.location) < 3600) { // 60 блоков
                    if (npc.viewers.add(player.getUniqueId())) {
                        npc.sendShowPackets(player, gs);
                    }
                } else {
                    if (npc.viewers.remove(player.getUniqueId())) {
                        npc.sendHidePackets(player);
                    }
                }
            }
        }
    }

    private void tickHighlightParticles() {
        Location lobbySpawn = Settings.getLobbySpawn();
        if (lobbySpawn == null || lobbySpawn.getWorld() == null) return;
        World world = lobbySpawn.getWorld();

        java.util.List<Player> players = world.getPlayers();
        if (players.isEmpty()) return;

        org.bukkit.Color[] purpleGradient = new org.bukkit.Color[]{
            org.bukkit.Color.fromRGB(0x8A, 0x2B, 0xE2),
            org.bukkit.Color.fromRGB(0x94, 0x00, 0xD3),
            org.bukkit.Color.fromRGB(0x99, 0x32, 0xCC),
            org.bukkit.Color.fromRGB(0xBA, 0x55, 0xD3),
            org.bukkit.Color.fromRGB(0x93, 0x70, 0xDB),
            org.bukkit.Color.fromRGB(0xDA, 0x70, 0xD6),
            org.bukkit.Color.fromRGB(0xDD, 0xA0, 0xDD)
        };

        java.util.function.Consumer<Location> spawnP = (loc) -> {
            if (loc == null) return;
            Location checkLoc = loc.clone();
            checkLoc.setWorld(world);

            boolean near = false;
            for (Player pl : players) {
                if (pl.getLocation().distanceSquared(checkLoc) <= 182.25) {
                    near = true;
                    break;
                }
            }
            if (near) {
                Location top = checkLoc.clone().add(0.5, 1.2, 0.5);
                world.spawnParticle(Particle.SPELL_WITCH, top, 2, 0.15, 0.15, 0.15, 0.0);
                for (int i = 0; i < 7; i++) {
                    org.bukkit.Color c = purpleGradient[java.util.concurrent.ThreadLocalRandom.current().nextInt(purpleGradient.length)];
                    Particle.DustOptions dust = new Particle.DustOptions(c, 1.30f);
                    world.spawnParticle(Particle.REDSTONE, top, 1, 0.15, 0.15, 0.15, 0.0, dust);
                }
            }
        };

        for (String key : pads.keySet()) {
            Location loc = parseKeyToLoc(key, world);
            if (loc != null) spawnP.accept(loc);
        }
        for (LobbyParkour pk : parkours.values()) {
            if (pk.start != null) spawnP.accept(pk.start);
        }
        for (MoveBlock mb : moveBlocks.values()) {
            if (mb.a != null) spawnP.accept(mb.a);
        }
    }

    private void tickLaunchTrails() {
        launchedPlayers.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) return true;
            if (player.isOnGround() && player.getVelocity().getY() <= 0.1) return true;

            LaunchPad pad = entry.getValue();
            if (pad.particle() == null || pad.amount() <= 0) return false;

            Location center = player.getLocation().add(0, 0.1, 0);
            Vector behind = player.getLocation().getDirection().multiply(-0.5).setY(0);
            center.add(behind);

            player.getWorld().spawnParticle(pad.particle(), center, pad.amount(), 0.2, 0.2, 0.2, 0.01);
            return false;
        });
    }

    private void tickParkourSessions() {
        parkourSessions.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) return true;

            ParkourSession session = entry.getValue();
            LobbyParkour parkour = parkours.get(session.id);
            if (parkour == null || parkour.start == null) return true;

            if (movingPlayers.containsKey(player.getUniqueId())) return false;

            if (player.isSneaking()) {
                session.shiftTicks++;
                if (session.shiftTicks >= 40) {
                    player.sendActionBar(Component.empty());
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                    if (Settings.getLobbySpawn() != null) {
                        player.teleport(Settings.getLobbySpawn());
                    }
                    return true;
                }
            } else {
                session.shiftTicks = 0;
            }

            if (player.getLocation().getY() < parkour.start.getY() - 0.2) {
                Location startLoc = parkour.start.clone().add(0.5, 1.0, 0.5);
                if (startLoc.getWorld() == null && Settings.getLobbySpawn() != null) {
                    startLoc.setWorld(Settings.getLobbySpawn().getWorld());
                }
                startLoc.setYaw(player.getLocation().getYaw());
                startLoc.setPitch(player.getLocation().getPitch());

                player.teleport(startLoc);
                player.setVelocity(new Vector());
                player.setFallDistance(0f);
                session.startTime = System.currentTimeMillis();
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }

            long elapsed = System.currentTimeMillis() - session.startTime;
            String timeStr = TimeUtils.formatTimecode(elapsed);
            player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.tick_parkour_sessions.1") + timeStr));

            return false;
        });
    }

    private void tickRides() {
        Iterator<Map.Entry<UUID, MoveSession>> iterator = movingPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MoveSession> entry = iterator.next();
            MoveSession session = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());

            if (player == null || !player.isOnline()) {
                iterator.remove();
                session.despawn();
                continue;
            }

            if (!session.seat.isValid() || !session.carpet.isValid() || !session.seat.equals(player.getVehicle())) {
                iterator.remove();
                this.stopRide(player, session, false, true, true);
                continue;
            }

            double speed = session.moveBlock.speed > 0 ? session.moveBlock.speed : DEFAULT_SPEED;
            session.progress = Math.min(1.0d, session.progress + speed / session.length);

            double t = smoothStep(session.progress);
            Vector target = getBezierPoint(session.from, session.control, session.to, t);

            Location seatLoc = session.seat.getLocation();
            Location carpetLoc = session.carpet.getLocation();

            double mountOffset = carpetLoc.getY() - seatLoc.getY();
            if (!Double.isFinite(mountOffset) || Math.abs(mountOffset) > 3.0d) mountOffset = 0.0d;

            if (session.ticks > 5 && seatLoc.toVector().distance(target) > MAX_DESYNC) {
                iterator.remove();
                this.stopRide(player, session, false, true, true);
                continue;
            }

            double seatY = target.getY() - mountOffset;

            if (!moveEntityRaw(session.seat, target.getX(), seatY, target.getZ(), seatLoc.getYaw(), 0f)) {
                session.seat.setVelocity(new Vector(target.getX(), seatY, target.getZ()).subtract(seatLoc.toVector()));
            }

            session.carpet.setTicksLived(1);
            player.setFallDistance(0f);
            player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.tick_rides.1")));

            this.spawnCarpetParticles(carpetLoc, session);
            session.ticks++;

            if (session.progress >= 1.0d) {
                iterator.remove();
                this.stopRide(player, session, true, true, false);
            }
        }
    }

    private void startRide(@NonNull Player player, @NonNull MoveBlock mb) {
        if (mb.a == null || mb.b == null) return;
        World world = mb.a.getWorld();
        if (world == null) {
            if (Settings.getLobbySpawn() != null) world = Settings.getLobbySpawn().getWorld();
            else return;
        }

        if (player.isInsideVehicle() || movingPlayers.containsKey(player.getUniqueId())) return;

        Vector from = new Vector(mb.a.getBlockX() + 0.5d, mb.a.getBlockY() + RIDE_HEIGHT, mb.a.getBlockZ() + 0.5d);
        Vector to = new Vector(mb.b.getBlockX() + 0.5d, mb.b.getBlockY() + RIDE_HEIGHT, mb.b.getBlockZ() + 0.5d);

        double length = from.distance(to);
        if (length < 0.5d) return;

        Vector control = from.clone().add(to).multiply(0.5d);
        control.setY(control.getY() + length * mb.arc * 2.0d);

        Location spawnLoc = new Location(world, from.getX(), from.getY(), from.getZ(), player.getLocation().getYaw(), 0f);

        ArmorStand seat = world.spawn(spawnLoc, ArmorStand.class);
        seat.setVisible(false);
        seat.setMarker(true);
        seat.setSmall(true);
        seat.setBasePlate(false);
        seat.setArms(false);
        seat.setGravity(false);
        seat.setSilent(true);
        seat.setInvulnerable(true);
        seat.setCollidable(false);
        seat.setCustomNameVisible(false);
        seat.setRemoveWhenFarAway(false);
        seat.addScoreboardTag(RIDE_TAG);

        FallingBlock carpet = world.spawnFallingBlock(spawnLoc, this.carpetData);
        carpet.setGravity(false);
        carpet.setDropItem(false);
        carpet.setHurtEntities(false);
        carpet.setInvulnerable(true);
        carpet.setSilent(true);
        carpet.setVelocity(new Vector());
        carpet.setTicksLived(1);
        carpet.addScoreboardTag(RIDE_TAG);

        seat.addPassenger(carpet);
        seat.addPassenger(player);

        if (!seat.equals(player.getVehicle())) {
            carpet.remove();
            seat.remove();
            return;
        }

        player.setFallDistance(0f);
        movingPlayers.put(player.getUniqueId(), new MoveSession(mb, seat, carpet, from, control, to, length));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
    }

    private void stopRide(@NonNull Player player, @NonNull MoveSession session, boolean toDestination, boolean notify, boolean isDismount) {
        session.despawn();
        if (!player.isOnline()) return;
        if (player.isInsideVehicle()) player.leaveVehicle();

        if (!isDismount) {
            Location target = toDestination ? session.moveBlock.b : session.moveBlock.a;
            if (target != null) {
                Location dest = target.clone().add(0.5d, 1.0d, 0.5d);
                if (dest.getWorld() == null && Settings.getLobbySpawn() != null) dest.setWorld(Settings.getLobbySpawn().getWorld());
                dest.setYaw(player.getLocation().getYaw());
                dest.setPitch(player.getLocation().getPitch());
                player.teleport(dest);
            }
        }

        player.setVelocity(new Vector());
        player.setFallDistance(0f);
        player.sendActionBar(Component.empty());

        if (!notify) return;
        if (toDestination && !isDismount) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
        }
    }

    private void spawnCarpetParticles(@NonNull Location carpetLoc, @NonNull MoveSession session) {
        World world = carpetLoc.getWorld();
        if (world == null) return;

        double angleOffset = session.ticks * 0.12d;
        double y = carpetLoc.getY() + 0.08d;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = angleOffset + (Math.PI * 2.0d / RING_POINTS) * i;
            world.spawnParticle(CARPET_PARTICLE, carpetLoc.getX() + Math.cos(angle) * RING_RADIUS, y, carpetLoc.getZ() + Math.sin(angle) * RING_RADIUS, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        }

        MoveBlock mb = session.moveBlock;
        if (mb.particle != null && mb.amount > 0) {
            world.spawnParticle(mb.particle, carpetLoc.getX(), y, carpetLoc.getZ(), mb.amount, 0.15, 0.05, 0.15, 0.01);
        }
    }

    private static double smoothStep(double t) {
        if (t <= 0.0d) return 0.0d;
        if (t >= 1.0d) return 1.0d;
        return t * t * (3.0d - 2.0d * t);
    }

    private Vector getBezierPoint(Vector p0, Vector p1, Vector p2, double t) {
        double u = 1 - t;
        return p0.clone().multiply(u * u).add(p1.clone().multiply(2 * u * t)).add(p2.clone().multiply(t * t));
    }

    private static Method handleMethod;
    private static Method setLocationMethod;
    private static boolean nmsUnavailable = false;

    private static boolean moveEntityRaw(@NonNull Entity entity, double x, double y, double z, float yaw, float pitch) {
        if (nmsUnavailable) return false;
        try {
            Method handle = handleMethod;
            if (handle == null || !handle.getDeclaringClass().isInstance(entity)) {
                handle = entity.getClass().getMethod("getHandle");
                handleMethod = handle;
            }
            Object nmsEntity = handle.invoke(entity);

            Method setLocation = setLocationMethod;
            if (setLocation == null || !setLocation.getDeclaringClass().isInstance(nmsEntity)) {
                setLocation = nmsEntity.getClass().getMethod("setLocation", double.class, double.class, double.class, float.class, float.class);
                setLocationMethod = setLocation;
            }
            setLocation.invoke(nmsEntity, x, y, z, yaw, pitch);
            return true;
        } catch (Throwable throwable) {
            nmsUnavailable = true;
            return false;
        }
    }

    private static int resolveSkinLayersIndex() {
        try {
            String version = org.bukkit.Bukkit.getBukkitVersion();
            String[] parts = version.split("-")[0].split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (major > 1) return 17;
            if (minor >= 17) return 17;
            if (minor >= 15) return 16;
            if (minor >= 14) return 15;
            return 13;
        } catch (Throwable t) {
            return 17;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        Player player = event.getPlayer();

        if (movingPlayers.containsKey(player.getUniqueId()) || player.isInsideVehicle()) return;

        Location lobbySpawn = Settings.getLobbySpawn();
        if (lobbySpawn == null || lobbySpawn.getWorld() == null || !player.getWorld().getUID().equals(lobbySpawn.getWorld().getUID())) return;

        Block block = to.getBlock();
        Block below = to.clone().subtract(0, 0.1, 0).getBlock();

        LaunchPad pad = getPadAt(block);
        if (pad == null) pad = getPadAt(below);
        if (pad != null) {
            if (launchedPlayers.containsKey(player.getUniqueId()) && !player.isOnGround()) return;
            Vector dir = player.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            else dir.normalize();
            dir.multiply(pad.speed());
            dir.setY(pad.strength());
            player.setVelocity(dir);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);
            launchedPlayers.put(player.getUniqueId(), pad);
            return;
        }

        for (Map.Entry<String, LobbyParkour> entry : parkours.entrySet()) {
            LobbyParkour pk = entry.getValue();

            if (pk.start != null && (isSameBlock(block, pk.start) || isSameBlock(below, pk.start))) {
                if (!parkourSessions.containsKey(player.getUniqueId())) {
                    parkourSessions.put(player.getUniqueId(), new ParkourSession(entry.getKey()));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                }
            }

            if (pk.finish != null && (isSameBlock(block, pk.finish) || isSameBlock(below, pk.finish))) {
                ParkourSession session = parkourSessions.get(player.getUniqueId());
                if (session != null && session.id.equals(entry.getKey())) {
                    long elapsed = System.currentTimeMillis() - session.startTime;
                    player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.on_move.1") + TimeUtils.formatTimecode(elapsed) + Lang.raw(PlayerLang.of(player), "auto.spawn_tools_manager.on_move.2")));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    if (pk.particle != null && pk.amount > 0) {
                        player.getWorld().spawnParticle(pk.particle, player.getLocation().add(0, 1, 0), pk.amount, 0.5, 0.5, 0.5, 0.1);
                    }
                    parkourSessions.remove(player.getUniqueId());
                    player.sendActionBar(Component.empty());
                }
            }
        }

        for (Map.Entry<String, MoveBlock> entry : moveBlocks.entrySet()) {
            MoveBlock mb = entry.getValue();
            if (mb.a != null && (isSameBlock(block, mb.a) || isSameBlock(below, mb.a))) {
                this.startRide(player, mb);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity().getScoreboardTags().contains(RIDE_TAG)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        launchedPlayers.remove(id);
        parkourSessions.remove(id);
        MoveSession session = movingPlayers.remove(id);
        if (session != null) session.despawn();

        for (LobbyNpc npc : npcs.values()) {
            npc.viewers.remove(id);
        }
    }

    private boolean isSameBlock(Block a, Location b) {
        return a.getX() == b.getBlockX() && a.getY() == b.getBlockY() && a.getZ() == b.getBlockZ();
    }

    private LaunchPad getPadAt(Block block) {
        return pads.get(locToString(block.getLocation()));
    }

    @Override
    public void disable() {
        if (tickTask != null) tickTask.cancel();
        if (npcClickListener != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(npcClickListener);
            npcClickListener = null;
        }
        HandlerList.unregisterAll(this);

        for (Map.Entry<UUID, MoveSession> entry : movingPlayers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                this.stopRide(player, entry.getValue(), entry.getValue().progress >= 0.5d, false, false);
            } else {
                entry.getValue().despawn();
            }
        }

        this.removeStrayEntities();

        launchedPlayers.clear();
        parkourSessions.clear();
        movingPlayers.clear();

        for (LobbyNpc npc : npcs.values()) {
            npc.despawnAll(plugin);
        }
        npcs.clear();
    }
}
