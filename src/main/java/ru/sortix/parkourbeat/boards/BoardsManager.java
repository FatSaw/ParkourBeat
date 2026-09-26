package ru.sortix.parkourbeat.boards;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import javax.annotation.Nullable;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoardsManager implements PluginManager {

    private static final int MAX_WIDTH = 10;
    private static final int MAX_HEIGHT = 6;
    private static final double VIEW_DISTANCE = 32.0D;
    private static final double REACH = 5.0D;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull File file;
    private final @Getter @NonNull BoardAssets assets;
    private final @NonNull NamespacedKey key;

    private final Map<String, Board> boards = new LinkedHashMap<>();
    private final Map<BoardType, BoardRenderer> renderers = new EnumMap<>(BoardType.class);
    private final Map<UUID, Map<String, BoardSession>> sessions = new HashMap<>();
    private final Map<UUID, Map<String, PlayerView>> views = new HashMap<>();

    private final @Nullable BukkitTask task;
    private int ticks = 0;

    public BoardsManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boards.yml");
        this.assets = new BoardAssets(plugin);
        this.key = new NamespacedKey(plugin, "board");

        this.renderers.put(BoardType.LEVELS, new LevelsBoardRenderer(plugin, this.assets));
        this.renderers.put(BoardType.TOP, new TopBoardRenderer(plugin, this.assets));
        this.renderers.put(BoardType.LOGO, new LogoBoardRenderer(plugin, this.assets));

        this.load();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 2L);
    }

    @Override
    public void disable() {
        if (this.task != null) this.task.cancel();
        for (Board board : this.boards.values()) {
            this.detachRenderers(board);
        }
        this.boards.clear();
        this.sessions.clear();
        this.views.clear();
    }

    private void load() {
        this.boards.clear();
        if (!this.file.isFile()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = config.getConfigurationSection("boards");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            try {
                BoardType type = BoardType.byKey(section.getString("type"));
                BlockFace face = BlockFace.valueOf(section.getString("face", "NORTH"));
                if (type == null || !Board.isFlat(face)) continue;

                List<Integer> maps = section.getIntegerList("maps");
                int width = section.getInt("width");
                int height = section.getInt("height");
                if (width <= 0 || height <= 0 || maps.size() != width * height) continue;

                int[] mapIds = new int[maps.size()];
                for (int i = 0; i < mapIds.length; i++) mapIds[i] = maps.get(i);

                Board board = new Board(id, type, section.getString("world", "world"), face,
                    section.getInt("x"), section.getInt("y"), section.getInt("z"), width, height, mapIds);
                for (String raw : section.getStringList("frames")) {
                    try {
                        board.getFrames().add(UUID.fromString(raw));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                this.boards.put(id, board);
                this.attachRenderers(board);
            } catch (Throwable t) {
                this.plugin.getLogger().warning("Борд " + id + " не загрузился: " + t.getMessage());
            }
        }
        this.plugin.getLogger().info("Бордов загружено: " + this.boards.size());
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Board board : this.boards.values()) {
            String path = "boards." + board.getId() + ".";
            config.set(path + "type", board.getType().getKey());
            config.set(path + "world", board.getWorldName());
            config.set(path + "face", board.getFace().name());
            config.set(path + "x", board.getOriginX());
            config.set(path + "y", board.getOriginY());
            config.set(path + "z", board.getOriginZ());
            config.set(path + "width", board.getWidth());
            config.set(path + "height", board.getHeight());

            List<Integer> maps = new ArrayList<>();
            for (int id : board.getMapIds()) maps.add(id);
            config.set(path + "maps", maps);

            List<String> frames = new ArrayList<>();
            for (UUID uuid : board.getFrames()) frames.add(uuid.toString());
            config.set(path + "frames", frames);
        }
        try {
            config.save(this.file);
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Не удалось сохранить boards.yml: " + t.getMessage());
        }
    }

    public void reload() {
        for (Board board : this.boards.values()) this.detachRenderers(board);
        this.sessions.clear();
        this.views.clear();
        this.assets.reload();
        this.load();
    }

    @NonNull
    public List<Board> all() {
        return new ArrayList<>(this.boards.values());
    }

    @Nullable
    public String create(@NonNull Player player, @NonNull BoardType type) {
        RayTraceResult trace = player.rayTraceBlocks(6.0D);
        if (trace == null || trace.getHitBlock() == null || trace.getHitBlockFace() == null) {
            return Lang.raw(PlayerLang.of(player), "auto.boards_manager.create.1");
        }
        Block start = trace.getHitBlock();
        BlockFace face = trace.getHitBlockFace();
        if (start.getType() != Material.OBSERVER) return Lang.raw(PlayerLang.of(player), "auto.boards_manager.create.2") + start.getType() + ".";
        if (!Board.isFlat(face)) return Lang.raw(PlayerLang.of(player), "auto.boards_manager.create.3");

        World world = start.getWorld();
        BlockFace right = faceRight(face);
        Map<Long, Block> cells = new HashMap<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        cells.put(cellKey(start, right), start);

        while (!queue.isEmpty() && cells.size() <= MAX_WIDTH * MAX_HEIGHT + 1) {
            Block current = queue.poll();
            for (BlockFace step : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, right, right.getOppositeFace()}) {
                Block next = current.getRelative(step);
                if (next.getType() != Material.OBSERVER) continue;
                long id = cellKey(next, right);
                if (cells.containsKey(id)) continue;
                cells.put(id, next);
                queue.add(next);
            }
        }

        int minU = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Block block : cells.values()) {
            int u = axis(block, right);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minY = Math.min(minY, block.getY());
            maxY = Math.max(maxY, block.getY());
        }

        int width = maxU - minU + 1;
        int height = maxY - minY + 1;
        if (width > MAX_WIDTH || height > MAX_HEIGHT) {
            return Lang.raw(PlayerLang.of(player), "auto.boards_manager.create.4") + MAX_WIDTH + "x" + MAX_HEIGHT + Lang.raw(PlayerLang.of(player), "auto.boards_manager.create.5") + width + "x" + height + ".";
        }
        if (cells.size() != width * height) {
            return Lang.raw(PlayerLang.of(player), "auto.boards_manager.create.6");
        }

        Block origin = null;
        for (Block block : cells.values()) {
            if (axis(block, right) == minU && block.getY() == maxY) origin = block;
        }
        if (origin == null) return Lang.raw(PlayerLang.of(player), "auto.boards_manager.create.7");

        for (Block block : cells.values()) {
            Block front = block.getRelative(face);
            if (!front.isEmpty()) return Lang.raw(PlayerLang.of(player), "auto.boards_manager.create.8") + front.getType() + ".";
        }

        String id = type.getKey() + "_" + Long.toHexString(System.currentTimeMillis() & 0xFFFFFFL);
        int[] mapIds = new int[width * height];
        Board board = new Board(id, type, world.getName(), face,
            origin.getX(), origin.getY(), origin.getZ(), width, height, mapIds);

        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                MapView view = Bukkit.createMap(world);
                view.setScale(MapView.Scale.CLOSEST);
                view.setTrackingPosition(false);
                view.setUnlimitedTracking(false);
                try {
                    view.setLocked(true);
                } catch (Throwable ignored) {
                }
                mapIds[board.tileIndex(column, row)] = view.getId();

                ItemStack item = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) item.getItemMeta();
                if (meta != null) {
                    meta.setMapView(view);
                    item.setItemMeta(meta);
                }

                Location location = new Location(world,
                    board.wallX(column) + face.getModX() + 0.5D,
                    board.wallY(row) + 0.5D,
                    board.wallZ(column) + face.getModZ() + 0.5D);

                ItemFrame frame;
                try {
                    frame = world.spawn(location, ItemFrame.class, spawned -> {
                        spawned.setFacingDirection(face, true);
                        spawned.setItem(item);
                        spawned.setSilent(true);
                        spawned.setPersistent(true);
                        invisible(spawned);
                        spawned.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, id);
                    });
                } catch (Throwable t) {
                    this.plugin.getLogger().warning("Рамка борда не встала: " + t.getMessage());
                    continue;
                }
                board.getFrames().add(frame.getUniqueId());
            }
        }

        this.boards.put(id, board);
        this.attachRenderers(board);
        this.save();
        return null;
    }

    @Nullable
    public Board looking(@NonNull Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        for (Board board : this.boards.values()) {
            World world = board.world();
            if (world == null || !world.equals(player.getWorld())) continue;
            if (board.trace(eye, direction, REACH) != null) return board;
        }
        return null;
    }

    public void remove(@NonNull Board board) {
        this.detachRenderers(board);
        World world = board.world();
        if (world != null) {
            for (int row = 0; row < board.getHeight(); row++) {
                for (int column = 0; column < board.getWidth(); column++) {
                    Location location = new Location(world,
                        board.wallX(column) + board.getFace().getModX() + 0.5D,
                        board.wallY(row) + 0.5D,
                        board.wallZ(column) + board.getFace().getModZ() + 0.5D);
                    for (Entity entity : world.getNearbyEntities(location, 0.8D, 0.8D, 0.8D)) {
                        if (!(entity instanceof ItemFrame)) continue;
                        String owner = entity.getPersistentDataContainer()
                            .get(this.key, PersistentDataType.STRING);
                        if (board.getId().equals(owner) || board.getFrames().contains(entity.getUniqueId())) {
                            entity.remove();
                        }
                    }
                }
            }
        }
        this.boards.remove(board.getId());
        for (Map<String, BoardSession> map : this.sessions.values()) map.remove(board.getId());
        for (Map<String, PlayerView> map : this.views.values()) map.remove(board.getId());
        this.save();
    }

    private void attachRenderers(@NonNull Board board) {
        for (int tile = 0; tile < board.getMapIds().length; tile++) {
            MapView view = mapView(board.getMapIds()[tile]);
            if (view == null) continue;
            for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
                view.removeRenderer(renderer);
            }
            view.addRenderer(new BoardMapRenderer(this, board, tile));
        }
    }

    private void detachRenderers(@NonNull Board board) {
        for (int mapId : board.getMapIds()) {
            MapView view = mapView(mapId);
            if (view == null) continue;
            for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
                if (renderer instanceof BoardMapRenderer) view.removeRenderer(renderer);
            }
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static MapView mapView(int id) {
        try {
            return Bukkit.getMap(id);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void invisible(@NonNull ItemFrame frame) {
        try {
            ItemFrame.class.getMethod("setVisible", boolean.class).invoke(frame, false);
        } catch (Throwable ignored) {
        }
    }

    private static long cellKey(@NonNull Block block, @NonNull BlockFace right) {
        return ((long) axis(block, right) << 32) | (block.getY() & 0xFFFFFFFFL);
    }

    private static int axis(@NonNull Block block, @NonNull BlockFace right) {
        return right.getModX() != 0 ? block.getX() * right.getModX() : block.getZ() * right.getModZ();
    }

    @NonNull
    private static BlockFace faceRight(@NonNull BlockFace face) {
        switch (face) {
            case SOUTH: return BlockFace.EAST;
            case EAST: return BlockFace.NORTH;
            case WEST: return BlockFace.SOUTH;
            default: return BlockFace.WEST;
        }
    }

    @NonNull
    public BoardSession session(@NonNull Board board, @NonNull Player player) {
        return this.sessions
            .computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
            .computeIfAbsent(board.getId(), id -> new BoardSession());
    }

    public void forget(@NonNull Player player) {
        this.sessions.remove(player.getUniqueId());
        this.views.remove(player.getUniqueId());
    }

    private static final class PlayerView {
        private int version = -1;
        private byte[][] tiles = null;
        private int[] drawn = null;
    }

    @Nullable
    private PlayerView view(@NonNull Board board, @NonNull Player player) {
        BoardRenderer renderer = this.renderers.get(board.getType());
        if (renderer == null) return null;

        PlayerView view = this.views
            .computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
            .computeIfAbsent(board.getId(), id -> new PlayerView());

        BoardSession session = this.session(board, player);
        if (view.version == session.getVersion() && view.tiles != null) return view;

        BufferedImage image = new BufferedImage(board.pixelWidth(), board.pixelHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = BoardTheme.prepare(image);
        try {
            renderer.draw(g, board, player, session);
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Борд " + board.getId() + Lang.raw(PlayerLang.of(player), "auto.boards_manager.view.1") + t);
        } finally {
            g.dispose();
        }

        view.tiles = BoardPalette.slice(image, board.getWidth(), board.getHeight());
        if (view.drawn == null || view.drawn.length != board.tiles()) view.drawn = new int[board.tiles()];
        Arrays.fill(view.drawn, -1);
        view.version = session.getVersion();
        return view;
    }

    public void paint(@NonNull Board board, int tile, @NonNull MapCanvas canvas, @NonNull Player player) {
        PlayerView view = this.view(board, player);
        if (view == null || view.tiles == null || tile >= view.tiles.length) return;
        if (view.drawn != null && view.drawn[tile] == view.version) return;

        byte[] data = view.tiles[tile];
        for (int i = 0; i < data.length; i++) {
            canvas.setPixel(i & 127, i >> 7, data[i]);
        }
        if (view.drawn != null) view.drawn[tile] = view.version;
    }

    private void tick() {
        if (this.boards.isEmpty()) return;
        this.ticks++;
        boolean refresh = this.ticks % 60 == 0;

        for (Board board : this.boards.values()) {
            World world = board.world();
            Location center = board.center();
            if (world == null || center == null) continue;

            BoardRenderer renderer = this.renderers.get(board.getType());
            if (renderer == null) continue;

            for (Player player : world.getPlayers()) {
                BoardSession session = this.session(board, player);
                if (player.getLocation().distanceSquared(center) > VIEW_DISTANCE * VIEW_DISTANCE) {
                    if (session.setHoverIfChanged(BoardSession.NOTHING)) {
                        this.forceUpdate(board, player);
                    }
                    continue;
                }

                Location eye = player.getEyeLocation();
                int[] hit = board.trace(eye, eye.getDirection(), REACH);
                int code = hit == null
                    ? BoardSession.NOTHING
                    : renderer.hover(board, player, session, hit[0], hit[1]);

                boolean changed = session.setHoverIfChanged(code);
                if (changed) {
                    this.forceUpdate(board, player);
                } else if (refresh) {
                    session.bump();
                }
            }
        }
    }

    public boolean handleInteraction(@NonNull Player player, boolean right) {
        Board board = this.looking(player);
        if (board == null) return false;

        BoardRenderer renderer = this.renderers.get(board.getType());
        if (renderer == null) return false;

        Location eye = player.getEyeLocation();
        int[] hit = board.trace(eye, eye.getDirection(), REACH);
        if (hit == null) return false;

        BoardSession session = this.session(board, player);

        // ФИКС: Уменьшен порог антиспама со 100 мс до 40 мс (чтобы проходили даже 25 CPS)
        long now = System.currentTimeMillis();
        if (now - session.getTouchedAt() < 40) return true;
        session.setTouchedAt(now);

        if (renderer.click(board, player, session, hit[0], hit[1], right)) {
            session.bump();
            this.forceUpdate(board, player);
        }
        return true;
    }

    private void forceUpdate(@NonNull Board board, @NonNull Player player) {
        for (int mapId : board.getMapIds()) {
            MapView view = mapView(mapId);
            if (view != null) {
                player.sendMap(view);
            }
        }
    }

    @Nullable
    public Board byFrame(@NonNull Entity frame) {
        String id = frame.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        if (id != null) {
            Board board = this.boards.get(id);
            if (board != null) return board;
        }
        for (Board board : this.boards.values()) {
            if (board.getFrames().contains(frame.getUniqueId())) return board;
        }
        return null;
    }
}
