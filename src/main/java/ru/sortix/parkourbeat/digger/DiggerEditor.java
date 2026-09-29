package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * СТОРОНА СТРОИТЕЛЯ.
 * <p>
 * Руда становится нотой ТОЛЬКО если её взяли из меню режима: у такого предмета внутри
 * стоит метка, и при постановке блок попадает в список нот уровня. Руда из обычного
 * инвентаря остаётся декорацией - её можно ставить сколько угодно, судья её не видит.
 * Метка заодно делает предмет зачарованным, поэтому в инвентаре ноты и декор
 * различаются с одного взгляда.
 * <p>
 * Второй кусок работы здесь - сетка бита. Строитель видит светящиеся линии ровно
 * там, где проходят доли трека, и кладёт руды на них. Без сетки попасть в ритм
 * на глаз невозможно.
 */
public class DiggerEditor implements Listener {

    public static final NamespacedKey ORE_KEY = NamespacedKey.fromString("parkourbeat:digger_ore");
    public static final NamespacedKey TOOL_KEY = NamespacedKey.fromString("parkourbeat:digger_tool");

    /** Насколько далеко вокруг строителя рисуется сетка, блоков. */
    private static final int GRID_RANGE = 48;

    /**
     * Радиус, в котором над рудами-нотами висят метки, блоков.
     * <p>
     * Специально маленький: метка есть у каждой ноты, а нот на карте бывает под тысячу.
     * На дистанции, где их всё равно не различить, это чистая нагрузка.
     */
    private static final int MARKER_RANGE = 10;

    /** Раз во сколько тиков перерисовывается сетка. */
    private static final int GRID_PERIOD = 4;

    /** Насколько далеко вперёд и назад тянется линия траектории, блоков. */
    private static final int PATH_RANGE = 48;

    /**
     * Шаг между точками траектории, блоков.
     * <p>
     * Два блока: реже - линия перестаёт читаться как линия, чаще - сливается в
     * сплошную трубу и закрывает собой руды.
     */
    private static final double PATH_STEP = 2.0D;

    public enum Tool {
        ORES(Material.DIAMOND_ORE, "&bРуды", "Открыть меню руд-нот"),
        SETTINGS(Material.COMPARATOR, "&eНастройки карты", "BPM, скорость, кирка, тоннель"),
        AUTOMAP(Material.REPEATER, "&dАвторазметка", "Разложить руды по сетке бита"),
        GRID(Material.LIGHT_BLUE_DYE, "&fСетка бита", "Показать или спрятать доли"),
        TEST(Material.NETHERITE_PICKAXE, "&aТест", "Проехать карту прямо сейчас");

        public final @NonNull Material material;
        public final @NonNull String display;
        public final @NonNull String hint;

        Tool(@NonNull Material material, @NonNull String display, @NonNull String hint) {
            this.material = material;
            this.display = display;
            this.hint = hint;
        }
    }

    private final @NonNull ParkourBeat plugin;
    /** Кому сейчас показывается сетка. */
    private final Set<UUID> gridViewers = new HashSet<>();
    private int ticks = 0;

    public DiggerEditor(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        HandlerList.unregisterAll(this);
    }

    public void forget(@NonNull Player player) {
        this.gridViewers.remove(player.getUniqueId());
    }

    // ==================== ПРЕДМЕТЫ ====================

    /** Руда-нота для инвентаря строителя: с меткой внутри и блеском снаружи. */
    @NonNull
    public static ItemStack buildOreItem(@NonNull DiggerOre ore, int amount) {
        ItemStack stack = new ItemStack(ore.getMaterial(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(PbText.item(ore.getColoredName() + " &8[нота]"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Множитель очков: &f×" + ore.getScoreMultiplier()));
            if (ore.isDrop()) lore.add(PbText.item("&5Дроп-нота: полный залп шоу"));
            else if (ore.isRare()) lore.add(PbText.item("&bРедкая: окно попадания уже"));
            lore.add(Component.empty());
            lore.add(PbText.item("&8Поставленный блок станет нотой."));
            lore.add(PbText.item("&8Обычная руда из инвентаря - просто декор."));
            meta.lore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            if (ORE_KEY != null) {
                meta.getPersistentDataContainer().set(ORE_KEY, PersistentDataType.STRING, ore.name());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Nullable
    public static DiggerOre oreOf(@Nullable ItemStack stack) {
        if (stack == null || ORE_KEY == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(ORE_KEY, PersistentDataType.STRING);
        return DiggerOre.byName(raw);
    }

    @NonNull
    public static ItemStack buildToolItem(@NonNull Tool tool) {
        ItemStack stack = new ItemStack(tool.material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(PbText.item(tool.display));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7" + tool.hint));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (TOOL_KEY != null) {
                meta.getPersistentDataContainer().set(TOOL_KEY, PersistentDataType.STRING, tool.name());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Nullable
    public static Tool toolOf(@Nullable ItemStack stack) {
        if (stack == null || TOOL_KEY == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(TOOL_KEY, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return Tool.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Выдать строителю набор инструментов режима. */
    public void giveTools(@NonNull Player player) {
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            player.getInventory().setItem(3 + i, buildToolItem(tools[i]));
        }
        player.sendMessage(PbText.of("&dКопатель&7: инструменты выданы в хотбар."));
    }

    // ==================== СОБЫТИЯ СТРОИТЕЛЯ ====================

    public void onPlace(@NonNull BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Level level = this.getEditedLevel(player);
        if (level == null) return;

        DiggerOre ore = oreOf(event.getItemInHand());
        if (ore == null) return;

        DiggerLevelSettings settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        Block block = event.getBlockPlaced();

        // Первая поставленная руда задаёт начало отсчёта, если строитель ещё не ставил старт.
        if (!settings.hasOrigin()) {
            settings.setOrigin(DiggerAutoMapper.defaultOrigin(level));
        }

        DiggerNote note = new DiggerNote(block.getX(), block.getY(), block.getZ(), ore);
        if (!settings.addNote(note)) {
            event.setCancelled(true);
            player.sendMessage(PbText.of("&cПредел карты: " + DiggerLevelSettings.MAX_NOTES + " нот."));
            return;
        }

        long millis = this.millisOf(level, settings, note);
        double beat = millis / DiggerTuning.beatMillis(settings.getBpm());
        player.sendActionBar(PbText.of("&a+нота &7" + ore.getDisplay()
            + " &8| &f" + String.format("%.2f", beat) + " &7доля"
            + " &8| &7всего &f" + settings.getNotesCount()));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.6f);
    }

    public void onBreak(@NonNull BlockBreakEvent event) {
        Player player = event.getPlayer();
        Level level = this.getEditedLevel(player);
        if (level == null) return;

        DiggerLevelSettings settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        Block block = event.getBlock();
        DiggerNote removed = settings.removeNote(block.getX(), block.getY(), block.getZ());
        if (removed == null) return;

        player.sendActionBar(PbText.of("&c-нота &7" + removed.getOre().getDisplay()
            + " &8| &7осталось &f" + settings.getNotesCount()));

        // Строитель слышит ровно тот звук, который услышит игрок. Иначе подобрать его
        // можно только на слух в тестовом забеге, а это десяток перезапусков подряд.
        Location center = removed.center(level.getWorld());
        if (settings.hasCustomHitSound() && settings.getHitSoundKey() != null) {
            player.playSound(center, settings.getHitSoundKey(), 1.0f, settings.getHitSoundPitch());
        } else {
            player.playSound(center, removed.getOre().getSound(), 1.0f, removed.getOre().getPitch());
        }
    }

    /**
     * СРЕДНЯЯ КНОПКА ПО РУДЕ-НОТЕ ДАЁТ РУДУ-НОТУ.
     * <p>
     * Ванильный «копировать блок» кладёт в руку обычный блок из мира - без метки
     * внутри, то есть чистый декор. Строитель этого не видит: в руке лежит та же самая
     * руда, ставится она так же, но нотой уже не становится, и узнаёт он об этом
     * только в тестовом забеге, где по ней нечем попасть.
     * <p>
     * Обработчик именно на {@code InventoryCreativeEvent}, а не на
     * {@code PlayerPickItemEvent}: последнего в 1.16.5 не существует вовсе, а копирование
     * блока в творческом режиме клиент делает как раз через творческое действие с
     * инвентарём.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickBlock(@NonNull org.bukkit.event.inventory.InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        Level level = this.getEditedLevel(player);
        if (level == null) return;

        ItemStack cursor = event.getCursor();
        if (cursor == null) return;

        DiggerOre ore = DiggerOre.byMaterial(cursor.getType());
        if (ore == null) return;

        // Уже помеченный предмет не трогаем: иначе копирование ноты из инвентаря
        // пересобирало бы её на каждом клике без всякой нужды.
        if (oreOf(cursor) != null) return;

        // На какой блок смотрит строитель. Проверяем именно ноту в этой точке, а не
        // просто «это руда»: декоративная руда обязана копироваться декоративной.
        Block target = player.getTargetBlockExact(6);
        if (target == null) return;

        DiggerLevelSettings settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        DiggerNote note = settings.getNoteAt(target.getX(), target.getY(), target.getZ());
        if (note == null) return;

        event.setCursor(buildOreItem(note.getOre(), Math.max(1, cursor.getAmount())));
        player.sendActionBar(PbText.of("&aСкопирована нота &7" + note.getOre().getDisplay()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(@NonNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Tool tool = toolOf(event.getItem());
        if (tool == null) return;

        Player player = event.getPlayer();
        Level level = this.getEditedLevel(player);
        if (level == null) return;

        event.setCancelled(true);
        switch (tool) {
            case ORES:
                new DiggerOresMenu(this.plugin, null, level).open(player);
                break;
            case SETTINGS:
                new DiggerSettingsMenu(this.plugin, null, level).open(player);
                break;
            case AUTOMAP:
                // Разметка спрашивает сложность: из одного разбора трека получается
                // и спокойная карта, и стена под эксперта.
                new DiggerAutoMapMenu(this.plugin, null, level).open(player);
                break;
            case GRID:
                this.toggleGrid(player);
                break;
            case TEST:
                this.plugin.get(DiggerManager.class).start(player, level);
                break;
        }
    }

    // ==================== СЕТКА БИТА ====================

    public void toggleGrid(@NonNull Player player) {
        if (this.gridViewers.remove(player.getUniqueId())) {
            player.sendMessage(PbText.of("&7Сетка бита скрыта."));
        } else {
            this.gridViewers.add(player.getUniqueId());
            player.sendMessage(PbText.of("&aСетка бита показана."));
        }
    }

    public void tick() {
        this.ticks++;
        if (this.ticks % GRID_PERIOD != 0) return;

        DiggerManager manager = this.plugin.get(DiggerManager.class);

        // Метки над рудами видит КАЖДЫЙ строитель, а не только тот, кто включил сетку:
        // без них игровая руда неотличима от декоративной, и это не вопрос вкуса.
        for (Player player : Bukkit.getOnlinePlayers()) {
            Level level = this.getEditedLevel(player);
            if (level == null) continue;

            // ВО ВРЕМЯ ЗАЕЗДА - НИЧЕГО.
            //
            // Тест запускается прямо из редактора и активность игрока при этом не
            // меняется: для этого класса он по-прежнему строитель. Поэтому весь
            // редакторский слой ехал вместе с ним по тоннелю - точка над каждой рудой,
            // включая те, которые окно видимости ещё не показало. Метка выдавала руду
            // раньше самой руды, то есть работала ровно против того, ради чего окно
            // видимости и сделано.
            if (manager != null && manager.isPlaying(player)) continue;

            this.drawMarkers(player, level);
            this.drawPath(player, level);
            if (this.gridViewers.contains(player.getUniqueId())) this.drawGrid(player, level);
        }

        this.gridViewers.removeIf(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            return player == null || !player.isOnline();
        });
    }

    /**
     * ЛИНИЯ, ПО КОТОРОЙ ПОЕДЕТ КАМЕРА.
     * <p>
     * Строитель ставит руды, стоя на полу тоннеля, а игрок проезжает мимо них на
     * высоте посадки и строго по оси уровня. Совместить одно с другим в голове нельзя:
     * руда, которая с пола выглядит удобной, на деле проходит над макушкой или за
     * спиной. Поэтому траектория рисуется прямо в мире.
     * <p>
     * END_ROD с ИНТЕРВАЛОМ, а не сплошной полосой: сплошная линия сливается в трубу и
     * закрывает собой ровно те руды, ради которых её включили. Редкие точки читаются
     * как направляющая и не мешают.
     */
    private void drawPath(@NonNull Player player, @NonNull Level level) {
        DiggerLevelSettings settings = level.getLevelSettings().getGameSettings().getDiggerSettings();

        Location origin = settings.getOrigin(level.getWorld());
        if (origin == null) origin = DiggerAutoMapper.defaultOrigin(level);

        DirectionChecker checker = level.getLevelSettings().getDirectionChecker();
        DirectionChecker.Direction direction = checker.direction();
        boolean axisX = direction == DirectionChecker.Direction.POSITIVE_X
            || direction == DirectionChecker.Direction.NEGATIVE_X;
        int sign = checker.isNegative() ? -1 : 1;

        double originCoordinate = checker.getCoordinate(origin);
        double playerCoordinate = checker.getCoordinate(player.getLocation());

        // Высота ровно та, на которой поедет игрок - иначе линия врёт о самом главном.
        double y = origin.getY() + DiggerTuning.CARRIER_Y_OFFSET;

        double from = (playerCoordinate - originCoordinate) * sign - PATH_RANGE;
        double to = (playerCoordinate - originCoordinate) * sign + PATH_RANGE;
        if (from < 0.0D) from = 0.0D;

        double first = Math.ceil(from / PATH_STEP) * PATH_STEP;
        for (double distance = first; distance <= to; distance += PATH_STEP) {
            double coordinate = originCoordinate + distance * sign;
            double x = axisX ? coordinate : origin.getX();
            double z = axisX ? origin.getZ() : coordinate;
            player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * Метка над каждой игровой рудой.
     * <p>
     * Крупная точка в цвете самой руды, чуть выше блока. Поставленная из меню руда и
     * такая же руда из обычного инвентаря выглядят абсолютно одинаково, и без метки
     * строитель узнаёт о декорации, которая не судится, только в тестовом забеге.
     */
    private void drawMarkers(@NonNull Player player, @NonNull Level level) {
        DiggerLevelSettings settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        if (settings.getNotesCount() == 0) return;

        Location eye = player.getLocation();
        for (DiggerNote note : settings.getAllNotes()) {
            if (Math.abs(note.getX() + 0.5D - eye.getX()) > MARKER_RANGE) continue;
            if (Math.abs(note.getZ() + 0.5D - eye.getZ()) > MARKER_RANGE) continue;
            if (Math.abs(note.getY() + 0.5D - eye.getY()) > MARKER_RANGE) continue;

            // Точка садится ровно на верхнюю грань блока: половина её внутри руды,
            // половина снаружи - так метка читается и не висит оторванно в воздухе.
            Particle.DustOptions dust = new Particle.DustOptions(note.getOre().getColor(), 2.9f);
            player.spawnParticle(Particle.REDSTONE,
                note.center(level.getWorld()).add(0.0D, 0.35D, 0.0D),
                1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
        }
    }

    /**
     * Рисует доли трека прямо в мире.
     * <p>
     * Каждая четвёртая линия ярче - это начало такта. Ноты подсвечиваются своим
     * цветом, поэтому зарегистрированная руда сразу отличается от декоративной,
     * даже если блок один и тот же.
     */
    private void drawGrid(@NonNull Player player, @NonNull Level level) {
        DiggerLevelSettings settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        World world = level.getWorld();

        Location origin = settings.getOrigin(world);
        if (origin == null) origin = DiggerAutoMapper.defaultOrigin(level);

        DirectionChecker checker = level.getLevelSettings().getDirectionChecker();
        DirectionChecker.Direction direction = checker.direction();
        boolean axisX = direction == DirectionChecker.Direction.POSITIVE_X
            || direction == DirectionChecker.Direction.NEGATIVE_X;
        int sign = checker.isNegative() ? -1 : 1;

        double originCoordinate = checker.getCoordinate(origin);
        double playerCoordinate = checker.getCoordinate(player.getLocation());
        double blocksPerBeat = Math.max(0.5D, settings.blocksPerBeat());

        int firstBeat = (int) Math.floor(((playerCoordinate - originCoordinate) * sign - GRID_RANGE) / blocksPerBeat);
        int lastBeat = (int) Math.ceil(((playerCoordinate - originCoordinate) * sign + GRID_RANGE) / blocksPerBeat);
        if (firstBeat < 0) firstBeat = 0;

        int half = settings.getTunnelWidth() / 2;
        double baseY = origin.getY();

        for (int beat = firstBeat; beat <= lastBeat; beat++) {
            boolean bar = beat % 4 == 0;
            Particle.DustOptions dust = new Particle.DustOptions(
                bar ? Color.fromRGB(0xFFFFFF) : Color.fromRGB(0x8A4DFF), bar ? 1.4f : 0.9f);

            double distance = beat * blocksPerBeat;
            double coordinate = originCoordinate + distance * sign;

            for (int offset = -half; offset <= half; offset++) {
                double x = axisX ? coordinate : origin.getX() + offset;
                double z = axisX ? origin.getZ() + offset : coordinate;
                player.spawnParticle(Particle.REDSTONE, x, baseY, z, 1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
            }
        }

    }

    // ==================== ОБЩЕЕ ====================

    /** Таймкод ноты вне забега - нужен строителю, чтобы видеть, на какую долю он встал. */
    public long millisOf(@NonNull Level level, @NonNull DiggerLevelSettings settings, @NonNull DiggerNote note) {
        DirectionChecker checker = level.getLevelSettings().getDirectionChecker();
        DirectionChecker.Direction direction = checker.direction();
        boolean axisX = direction == DirectionChecker.Direction.POSITIVE_X
            || direction == DirectionChecker.Direction.NEGATIVE_X;
        int sign = checker.isNegative() ? -1 : 1;

        Location origin = settings.getOrigin(level.getWorld());
        double originCoordinate = checker.getCoordinate(
            origin != null ? origin : DiggerAutoMapper.defaultOrigin(level));

        double coordinate = (axisX ? note.getX() : note.getZ()) + 0.5D;
        // Та же поправка, что и в забеге: блок стоит на точку удара дальше доли.
        return settings.millisAt((coordinate - originCoordinate) * sign
            - DiggerTuning.HIT_LEAD_BLOCKS);
    }

    @Nullable
    public Level getEditedLevel(@NonNull Player player) {
        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof EditActivity)) return null;
        Level level = activity.getLevel();
        if (level == null) return null;
        return level.getLevelSettings().getGameSettings().isDiggerLevel() ? level : null;
    }
}
