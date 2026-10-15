// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/inventory/type/CreateLevelMenu.java
package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.SkyMode;
import ru.sortix.parkourbeat.twod.LevelMode;
import ru.sortix.parkourbeat.ui.Glyphs;
import ru.sortix.parkourbeat.ui.Icons;
import ru.sortix.parkourbeat.ui.Icons.Button;
import ru.sortix.parkourbeat.ui.Icons.ButtonState;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * МЕНЮ СОЗДАНИЯ УРОВНЯ - ВСЁ В ОДНОМ ОКНЕ.
 * <p>
 * Фон окна - картинка из ресурспака (символ в заголовке, см. {@link Glyphs#menuTitle}):
 * подписи «Режим / Размер / Мир / Небо», ячейки, большая кнопка «Создать» и вкладка
 * над окном нарисованы прямо на нём. Предметы - это кнопки-картинки:
 * <pre>
 *   ряд 1  РЕЖИМ   [3D] [2D] [ДУЭЛЬ] [360] [КОПАТЕЛЬ]
 *   ряд 2  РАЗМЕР  [1] [2] [3] [4]
 *   ряд 3  МИР     [обычный] [ад] [край]
 *   ряд 4  НЕБО    [полное] [половина]
 *   ряд 6  [X]          [====== СОЗДАТЬ ======]
 * </pre>
 * Каждый выбор - отдельная кнопка, а не переключатель по кругу: все варианты видны
 * сразу, выбранный подсвечен, недоступный (например, 2 чанка у обычного уровня)
 * затемнён и в описании объясняет почему.
 */
public class CreateLevelMenu extends ParkourBeatInventory {
    public static final boolean DISPLAY_NON_DEFAULT_WORLD_TYPES = true;

    private static final int ROW_MODE = 0, ROW_SIZE = 1, ROW_WORLD = 2, ROW_SKY = 3, ROW_ACTIONS = 5;
    private static final int FIRST_OPTION_COLUMN = 3;
    private static final int CREATE_FROM = 3, CREATE_TO = 7;
    private static final World.Environment[] WORLDS = {
        World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END};
    private static final SkyMode[] SKIES = {SkyMode.FULL, SkyMode.HALF};
    private static final Button[] WORLD_BUTTONS = {Button.WORLD_NORMAL, Button.WORLD_NETHER, Button.WORLD_END};
    private static final Button[] SKY_BUTTONS = {Button.SKY_FULL, Button.SKY_HALF};
    private static final Button[] SIZE_BUTTONS = {Button.SIZE_1, Button.SIZE_2, Button.SIZE_3, Button.SIZE_4};
    private static final int MAX_TITLE_NAME = 26;

    private final String levelName;

    /** Ширина уровня в чанках. Обычные уровни - 1 или 4, 360-уровни - любая от 1 до 4. */
    private int chunkWidth = 1;
    private LevelMode levelMode = LevelMode.THREE_D;
    private World.Environment environment = World.Environment.NORMAL;
    private SkyMode skyMode = SkyMode.FULL;

    public CreateLevelMenu(@NonNull ParkourBeat plugin, String lang, @NonNull String levelName) {
        super(plugin, 6, lang, title(lang, levelName));
        this.levelName = levelName;
        this.render();
    }

    @NonNull
    private static Component title(String lang, @NonNull String levelName) {
        String name = levelName.length() > MAX_TITLE_NAME ? levelName.substring(0, MAX_TITLE_NAME) + "…" : levelName;
        Component header = Icons.Icon.PENCIL.component()
            .append(Component.text(" "))
            .append(PbText.vanilla("&f" + name));
        return Glyphs.menuTitle(Glyphs.menuCreate(lang), header);
    }

    // ================================================================ ОТРИСОВКА

    private void render() {
        this.clearInventory();

        LevelMode[] modes = LevelMode.values();
        for (int i = 0; i < modes.length && i < 5; i++) {
            LevelMode mode = modes[i];
            this.put(ROW_MODE, i, this.modeItem(mode), event -> this.selectMode(event.getPlayer(), mode));
        }

        for (int i = 0; i < SIZE_BUTTONS.length; i++) {
            int width = i + 1;
            this.put(ROW_SIZE, i, this.sizeItem(width), event -> this.selectWidth(event.getPlayer(), width));
        }

        for (int i = 0; i < WORLDS.length; i++) {
            World.Environment env = WORLDS[i];
            Button button = WORLD_BUTTONS[i];
            this.put(ROW_WORLD, i, this.worldItem(env, button), event -> this.selectWorld(event.getPlayer(), env));
        }

        for (int i = 0; i < SKIES.length; i++) {
            SkyMode sky = SKIES[i];
            Button button = SKY_BUTTONS[i];
            this.put(ROW_SKY, i, this.skyItem(sky, button), event -> this.selectSky(event.getPlayer(), sky));
        }

        this.setItem(ROW_ACTIONS * 9, ItemUtils.modifyMeta(Icons.cancel(), meta ->
                meta.displayName(LangOptions.inventory_createlevel_cancel.getComponent(this.lang))),
            event -> event.getPlayer().closeInventory());

        // Рамка вокруг всей кнопки «Создать» при наведении на любой её слот.
        ItemStack create = Glyphs.withHover(this.createItem(), CREATE_FROM, ROW_ACTIONS, CREATE_TO, ROW_ACTIONS);
        for (int column = CREATE_FROM; column <= CREATE_TO; column++) {
            this.setItem(ROW_ACTIONS * 9 + column, create, event -> this.createLevel(event.getPlayer(), this.environment));
        }
    }

    private void put(int row, int index, @NonNull ItemStack item,
                     @NonNull java.util.function.Consumer<ru.sortix.parkourbeat.inventory.event.ClickEvent> action) {
        this.setItem(row * 9 + FIRST_OPTION_COLUMN + index, item, action);
    }

    // ---------------------------------------------------------------- режим

    @NonNull
    private ItemStack modeItem(@NonNull LevelMode mode) {
        boolean locked = mode.isDuel() && !this.duelAllowed();
        ButtonState state = mode == this.levelMode ? ButtonState.SELECTED
            : locked ? ButtonState.DISABLED : ButtonState.NORMAL;
        Button button = Button.values()[Button.MODE_3D.ordinal() + mode.ordinal()];

        return ItemUtils.modifyMeta(button.item(state), meta -> {
            meta.displayName(PbText.item(head(state) + "&f" + strip(mode.getDisplayName())));
            List<Component> lore = new ArrayList<>();
            switch (mode) {
                case THREE_D -> lore.add(PbText.item(this.t("&7Обычный паркур по пути из частиц",
                    "&7Classic parkour along a particle path", "&7Звичайний паркур шляхом із частинок")));
                case TWO_D -> lore.add(PbText.item(this.t("&7Двумерный уровень: кубик и линия",
                    "&7Two-dimensional level: a cube and a line", "&7Двовимірний рівень: кубик і лінія")));
                case DUEL -> {
                    lore.add(PbText.item(this.t("&7Карта на двоих: две трассы в одном мире",
                        "&7A map for two: two tracks in one world", "&7Карта на двох: дві траси в одному світі")));
                    lore.add(PbText.item(this.t("&7Синий путь - первый игрок, красный - второй",
                        "&7Blue path - first player, red - second", "&7Синій шлях - перший гравець, червоний - другий")));
                    lore.add(PbText.item(this.t("&7Ширина всегда 4 чанка", "&7Always 4 chunks wide", "&7Ширина завжди 4 чанки")));
                }
                case THREE_SIXTY -> {
                    lore.add(PbText.item(this.t("&7Пустота, каменная платформа и спавн на ней",
                        "&7Void, a stone platform and a spawn on it", "&7Порожнеча, кам'яна платформа і спавн на ній")));
                    lore.add(PbText.item(this.t("&7Строить можно во все стороны",
                        "&7Build in every direction", "&7Будувати можна в усі боки")));
                    lore.add(PbText.item(this.t("&8Урон за отпущенный бег выключен",
                        "&8No damage for releasing sprint", "&8Шкоду за відпущений біг вимкнено")));
                }
                case DIGGER -> lore.add(PbText.item(this.t("&7Ломать руды в ритм",
                    "&7Break ores to the rhythm", "&7Ламати руди в ритм")));
                default -> {
                }
            }
            lore.add(Component.empty());
            if (locked) {
                lore.add(PbText.item("<i:lock> &c" + this.t("Откроется после ", "Unlocks after ", "Відкриється після ")
                    + ru.sortix.parkourbeat.duel.DuelManager.REQUIRED_COMPLETED_LEVELS
                    + this.t(" пройденных уровней", " completed levels", " пройдених рівнів")));
            } else {
                lore.add(PbText.item(hint(state, this)));
            }
            meta.lore(lore);
        });
    }

    private void selectMode(@NonNull Player player, @NonNull LevelMode mode) {
        if (mode == this.levelMode) return;
        if (mode.isDuel() && !this.duelAllowed()) {
            this.deny(player);
            return;
        }
        this.levelMode = mode;
        // Ширина подгоняется под режим сразу: дуэль бывает только широкой, а обычный
        // уровень не умеет быть шириной в два или три чанка - под них нет шаблона.
        if (mode.isDuel()) {
            this.chunkWidth = ru.sortix.parkourbeat.duel.DuelManager.DUEL_CHUNK_WIDTH;
        } else if (!this.widthAllowed(this.chunkWidth)) {
            this.chunkWidth = 1;
        }
        this.click(player);
    }

    private boolean duelAllowed() {
        // Меню открывается у конкретного игрока, но держать его ссылку не нужно:
        // для отрисовки хватает «последнего кликнувшего», а проверка на создании
        // всё равно повторяется.
        return this.viewer == null
            || ru.sortix.parkourbeat.duel.DuelManager.canCreateDuelLevels(this.plugin, this.viewer);
    }

    private Player viewer;

    @Override
    public void open(@NonNull Player player) {
        this.viewer = player;
        this.render();
        super.open(player);
    }

    // ---------------------------------------------------------------- размер

    private boolean widthAllowed(int width) {
        if (this.levelMode.isDuel()) return width == ru.sortix.parkourbeat.duel.DuelManager.DUEL_CHUNK_WIDTH;
        if (this.levelMode.isThreeSixty()) return width >= 1 && width <= 4;
        return width == 1 || width == 4;
    }

    @NonNull
    private ItemStack sizeItem(int width) {
        boolean allowed = this.widthAllowed(width);
        ButtonState state = width == this.chunkWidth ? ButtonState.SELECTED
            : allowed ? ButtonState.NORMAL : ButtonState.DISABLED;
        return ItemUtils.modifyMeta(SIZE_BUTTONS[width - 1].item(state), meta -> {
            meta.displayName(PbText.item(head(state) + "&f" + width + " " + this.chunks(width)));
            List<Component> lore = new ArrayList<>();
            if (this.levelMode.isThreeSixty()) {
                lore.add(PbText.item(this.t("&7Площадка &f", "&7Platform &f", "&7Майданчик &f")
                    + (width * 16) + "x" + (width * 16) + this.t(" &7блоков", " &7blocks", " &7блоків")));
            } else {
                lore.add(PbText.item(this.t("&7Ширина площадки для строительства",
                    "&7Width of the building area", "&7Ширина майданчика для будування")));
            }
            lore.add(Component.empty());
            if (!allowed) {
                lore.add(PbText.item("<i:lock> &8" + (this.levelMode.isDuel()
                    ? this.t("Дуэльная карта всегда 4 чанка", "Duel maps are always 4 chunks", "Дуельна карта завжди 4 чанки")
                    : this.t("У этого режима только 1 или 4 чанка", "This mode supports only 1 or 4 chunks",
                    "У цього режиму лише 1 або 4 чанки"))));
            } else {
                lore.add(PbText.item(hint(state, this)));
            }
            meta.lore(lore);
        });
    }

    private void selectWidth(@NonNull Player player, int width) {
        if (width == this.chunkWidth) return;
        if (!this.widthAllowed(width)) {
            this.deny(player);
            return;
        }
        this.chunkWidth = width;
        this.click(player);
    }

    // ---------------------------------------------------------------- мир

    @NonNull
    private ItemStack worldItem(@NonNull World.Environment env, @NonNull Button button) {
        boolean allowed = DISPLAY_NON_DEFAULT_WORLD_TYPES || env == World.Environment.NORMAL;
        ButtonState state = env == this.environment ? ButtonState.SELECTED
            : allowed ? ButtonState.NORMAL : ButtonState.DISABLED;
        return ItemUtils.modifyMeta(button.item(state), meta -> {
            meta.displayName(PbText.item(head(state) + "&f" + this.worldName(env)));
            meta.lore(List.of(
                PbText.item(this.t("&7Небо, туман и общий вид уровня", "&7Sky, fog and the overall look",
                    "&7Небо, туман і загальний вигляд рівня")),
                PbText.item(this.t("&8Мир можно сменить и потом", "&8You can change it later", "&8Світ можна змінити й потім")),
                Component.empty(),
                PbText.item(hint(state, this))));
        });
    }

    private void selectWorld(@NonNull Player player, @NonNull World.Environment env) {
        if (env == this.environment) return;
        if (!DISPLAY_NON_DEFAULT_WORLD_TYPES && env != World.Environment.NORMAL) {
            this.deny(player);
            return;
        }
        this.environment = env;
        this.click(player);
    }

    // ---------------------------------------------------------------- небо

    @NonNull
    private ItemStack skyItem(@NonNull SkyMode sky, @NonNull Button button) {
        boolean available = SkyMode.isAvailableFor(this.levelMode);
        ButtonState state = !available ? ButtonState.DISABLED
            : sky == this.skyMode ? ButtonState.SELECTED : ButtonState.NORMAL;
        boolean movesPlatform = this.levelMode.isDigger() || this.levelMode.isThreeSixty();

        return ItemUtils.modifyMeta(button.item(state), meta -> {
            meta.displayName(PbText.item(head(state) + "&f" + strip(sky.getDisplayName())));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item(sky.isFull()
                ? this.t("&7Небо целиком, как на обычных уровнях", "&7The whole sky, like on regular levels",
                "&7Небо повністю, як на звичайних рівнях")
                : this.t("&7Нижняя половина неба тёмная", "&7The lower half of the sky is dark",
                "&7Нижня половина неба темна")));
            if (available && movesPlatform) {
                lore.add(PbText.item(this.t("&7Площадка встанет на Y=", "&7Platform will be at Y=",
                    "&7Майданчик стане на Y=") + sky.getPlatformY()));
            }
            lore.add(PbText.item(this.t("&8Потом это уже не поменять", "&8Can't be changed later",
                "&8Потім це вже не змінити")));
            lore.add(Component.empty());
            lore.add(PbText.item(available ? hint(state, this)
                : "<i:lock> &8" + this.t("Недоступно для этого режима", "Not available for this mode",
                "Недоступно для цього режиму")));
            meta.lore(lore);
        });
    }

    private void selectSky(@NonNull Player player, @NonNull SkyMode sky) {
        if (!SkyMode.isAvailableFor(this.levelMode)) {
            this.deny(player);
            return;
        }
        if (sky == this.skyMode) return;
        this.skyMode = sky;
        this.click(player);
    }

    // ---------------------------------------------------------------- создать

    @NonNull
    private ItemStack createItem() {
        return ItemUtils.modifyMeta(Icons.empty(), meta -> {
            meta.displayName(PbText.item("<i:plus> &f&l" + this.t("Создать уровень", "Create level", "Створити рівень")));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.vanilla("&f" + this.levelName));
            lore.add(PbText.item("&7" + strip(this.levelMode.getDisplayName())
                + " &8· &7" + this.chunkWidth + " " + this.chunks(this.chunkWidth)
                + " &8· &7" + this.worldName(this.environment)
                + (SkyMode.isAvailableFor(this.levelMode) ? " &8· &7" + strip(this.skyMode.getDisplayName()) : "")));
            lore.add(Component.empty());
            lore.add(PbText.item("<i:lmb> &7" + this.t("Нажмите, чтобы создать", "Click to create", "Натисніть, щоб створити")));
            meta.lore(lore);
        });
    }

    // ================================================================ МЕЛОЧИ

    private void click(@NonNull Player player) {
        ru.sortix.parkourbeat.ui.UiSounds.click(player);
        this.render();
    }

    private void deny(@NonNull Player player) {
        ru.sortix.parkourbeat.ui.UiSounds.deny(player);
    }

    @NonNull
    private static String head(@NonNull ButtonState state) {
        return state == ButtonState.SELECTED ? "<i:check> " : "";
    }

    @NonNull
    private static String hint(@NonNull ButtonState state, @NonNull CreateLevelMenu menu) {
        return state == ButtonState.SELECTED
            ? "<i:check> &a" + menu.t("Выбрано", "Selected", "Обрано")
            : "<i:lmb> &7" + menu.t("Нажмите, чтобы выбрать", "Click to select", "Натисніть, щоб обрати");
    }

    /** Текст на языке игрока: ru / en / uk. */
    @NonNull
    private String t(@NonNull String ru, @NonNull String en, @NonNull String uk) {
        int set = Glyphs.localeSet(this.lang);
        return set == 1 ? en : set == 2 ? uk : ru;
    }

    /** Снимает цвет из начала названия режима ("&b3D-уровень" -> "3D-уровень"). */
    @NonNull
    private static String strip(@NonNull String colored) {
        return colored.replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }

    @NonNull
    private String worldName(@NonNull World.Environment environment) {
        return switch (environment) {
            case NETHER -> this.t("Ад", "Nether", "Пекло");
            case THE_END -> this.t("Край", "The End", "Край");
            default -> this.t("Обычный", "Overworld", "Звичайний");
        };
    }

    @NonNull
    private String chunks(int chunks) {
        int set = Glyphs.localeSet(this.lang);
        if (set == 1) return chunks == 1 ? "chunk" : "chunks";
        if (set == 2) return chunks == 1 ? "чанк" : "чанки";
        return chunks == 1 ? "чанк" : (chunks < 5 ? "чанка" : "чанков");
    }

    // ================================================================ СОЗДАНИЕ (как раньше)

    public static void startCreating(@NonNull ParkourBeat plugin, @NonNull Player player, String lang) {
        if (!player.hasPermission(PermissionConstants.CREATE_LEVEL)) {
            LangOptions.inventory_createlevel_nopermission.sendMsg(player);
            return;
        }

        ru.sortix.parkourbeat.rating.StatisticsManager statistics =
            plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class);
        if (statistics.getDisplayRank(player.getUniqueId()) <= 0) {
            player.closeInventory();
            for (Component line : Lang.lore(player, "inventory.createlevel.locked")) {
                player.sendMessage(line);
            }
            return;
        }

        // Название спрашиваем наковальней, а не в чате: игроку не нужно закрывать
        // интерфейс, переключаться на чат и возвращаться обратно.
        new LevelNameAnvilMenu(plugin, lang, player).open();
    }

    private void createLevel(@NonNull Player owner, @NonNull World.Environment environment) {
        // Проверка повторяется на самом создании: меню живёт долго, а право строить
        // дуэли могло быть потеряно (например, статистику игрока сбросили).
        if (this.levelMode.isDuel()
            && !ru.sortix.parkourbeat.duel.DuelManager.canCreateDuelLevels(this.plugin, owner)) {
            owner.closeInventory();
            owner.sendMessage(PbText.of("&cДуэльные карты открываются после &f"
                + ru.sortix.parkourbeat.duel.DuelManager.REQUIRED_COMPLETED_LEVELS
                + "&c пройденных уровней."));
            return;
        }

        owner.closeInventory();
        ru.sortix.parkourbeat.ui.UiSounds.toggle(owner);

        String envName = switch (environment) {
            case NORMAL -> LangOptions.inventory_createlevel_dimension_overworld.get(lang);
            case NETHER -> LangOptions.inventory_createlevel_dimension_nether.get(lang);
            case THE_END -> LangOptions.inventory_createlevel_dimension_theend.get(lang);
            default -> "Unknown";
        };

        Component titleText = PbText.vanilla(this.levelName);

        AtomicInteger progress = new AtomicInteger(1);
        BukkitTask progressTask = this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, () -> {
            int current = progress.get();
            if (current < 99) {
                progress.set(current + (Math.random() < 0.2 ? 2 : 1));
            }

            Component subtitle = LangOptions.inventory_createlevel_generating.getComponent(lang,
                new Placeholders("%dimension%", envName),
                new Placeholders("%progress%", String.valueOf(Math.min(99, progress.get())))
            );

            owner.showTitle(Title.title(titleText, subtitle, Title.Times.of(Duration.ZERO, Duration.ofMillis(1000), Duration.ZERO)));
        }, 0L, 2L);

        this.plugin
            .get(LevelsManager.class)
            .createLevel(environment, owner.getUniqueId(), owner.getName(), this.levelName,
                this.chunkWidth, this.levelMode, this.skyMode)
            .thenAccept(level -> {
                progressTask.cancel();

                if (level == null) {
                    owner.showTitle(Title.title(titleText, LangOptions.inventory_createlevel_create_fail.getComponent(lang), Title.Times.of(Duration.ZERO, Duration.ofMillis(3000), Duration.ofMillis(1000))));
                    return;
                }
                owner.showTitle(Title.title(titleText, LangOptions.inventory_createlevel_finished.getComponent(lang), Title.Times.of(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(1000))));

                EditActivity.createAsync(this.plugin, owner, level).thenAccept(editActivity -> {
                    if (editActivity == null) {
                        LangOptions.inventory_createlevel_create_edit_unavilable.sendMsg(owner, new Placeholders("%level%", PbText.keepColors(
                            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().serialize(level.getDisplayName()))));
                        return;
                    }
                    this.plugin.get(ActivityManager.class).switchActivity(owner, editActivity, level.getSpawn()).thenAccept(success -> {
                        if (!success) {
                            LangOptions.inventory_createlevel_create_edit_fail.sendMsg(owner, new Placeholders("%level%", PbText.keepColors(
                                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                    .legacyAmpersand().serialize(level.getDisplayName()))));
                        }
                    });
                });
            });
    }
}
