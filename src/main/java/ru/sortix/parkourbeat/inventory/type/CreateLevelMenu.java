// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/inventory/type/CreateLevelMenu.java
package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
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
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import ru.sortix.parkourbeat.utils.text.PbText;

public class CreateLevelMenu extends ParkourBeatInventory {
    public static final boolean DISPLAY_NON_DEFAULT_WORLD_TYPES = true;
    private final String levelName;

    /** Ширина уровня в чанках. Обычные уровни - 1 или 4, 360-уровни - любая от 1 до 4. */
    private int chunkWidth = 1;

    /** Режим уровня. По умолчанию обычный 3D — как было всегда. */
    private ru.sortix.parkourbeat.twod.LevelMode levelMode =
        ru.sortix.parkourbeat.twod.LevelMode.THREE_D;

    /** Мир уровня. Меняется той же кнопкой, что и всё остальное, а не тремя разными. */
    private World.Environment environment = World.Environment.NORMAL;

    /** Полное небо или его тёмная нижняя половина. Есть не у всех режимов. */
    private ru.sortix.parkourbeat.levels.SkyMode skyMode =
        ru.sortix.parkourbeat.levels.SkyMode.FULL;

    public CreateLevelMenu(@NonNull ParkourBeat plugin, String lang, @NonNull String levelName) {
        super(plugin, 5, lang, LangOptions.inventory_createlevel_title.getComponent(lang));
        this.levelName = levelName;
        this.render();
    }

    /**
     * ОДИН ЭКРАН - ТРИ ВЫБОРА И ОДНА КНОПКА.
     * <p>
     * Раньше меню сразу создавало уровень по клику на один из трёх блоков мира, а
     * режим и размер стояли рядом отдельными переключателями: было непонятно, что из
     * этого выбор, а что действие. Теперь в ряду стоят ровно три переключателя -
     * режим, размер, мир, - а создание вынесено отдельной кнопкой ниже.
     */
    private void render() {
        this.clearInventory();

        this.setItem(3, 2, this.modeItem(), event -> this.switchMode(event.getPlayer()));
        this.setItem(3, 4, this.sizeItem(), event -> this.switchWidth());
        this.setItem(3, 6, this.worldItem(), event -> this.switchWorld());
        // НА МЕСТЕ БИРКИ С НАЗВАНИЕМ - ВЫБОР НЕБА.
        //
        // Бирка не была кнопкой: название уже введено в наковальне, а менять его здесь
        // всё равно нельзя. Она занимала четвёртый слот ряда переключателей и выглядела
        // как ещё одна кнопка, которая почему-то не нажимается.
        //
        // У режимов, где выбора неба нет, бирка остаётся: пустая клетка посреди ряда
        // читается как поломка, а переключатель, который ничего не делает, - хуже.
        if (ru.sortix.parkourbeat.levels.SkyMode.isAvailableFor(this.levelMode)) {
            this.setItem(3, 8, this.skyItem(), event -> this.switchSky());
        } else {
            this.setItem(3, 8, this.nameItem(), null);
        }

        this.setItem(5, 5, ItemUtils.create(Material.EMERALD_BLOCK, meta -> {
            meta.displayName(PbText.item("&a&lСоздать уровень"));
            meta.lore(java.util.List.of(
                PbText.item("&f" + this.levelName),
                PbText.item("&7" + this.levelMode.getDisplayName().substring(2)
                    + " &8| &7" + this.chunkWidth + " " + chunksWord(this.chunkWidth)
                    + " &8| &7" + worldName(this.environment)
                    + (ru.sortix.parkourbeat.levels.SkyMode.isAvailableFor(this.levelMode)
                    ? " &8| &7" + this.skyMode.getDisplayName().substring(2) : "")),
                PbText.item("&8Нажмите, чтобы создать")
            ));
        }), event -> this.createLevel(event.getPlayer(), this.environment));

        this.setItem(1, 9, ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(LangOptions.inventory_createlevel_cancel.getComponent(this.lang))),
            event -> event.getPlayer().closeInventory());

        this.fillCreateBorder();
    }

    /**
     * Обводка из чёрных панелей. Своя, а не общая: у общей рамки пустое название, а
     * здесь оно должно быть именно "&8" - так его просит ресурспак.
     */
    private void fillCreateBorder() {
        ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE,
            meta -> meta.displayName(PbText.item("&8")));

        for (int row = 1; row <= 5; row++) {
            for (int column = 1; column <= 9; column++) {
                boolean border = row == 1 || row == 5 || column == 1 || column == 9;
                if (!border) continue;
                int slot = ((row - 1) * 9) + (column - 1);
                if (this.getInventory().getItem(slot) != null) continue;
                this.setItem(slot, glass, null);
            }
        }
    }

    @NonNull
    private ItemStack nameItem() {
        return ItemUtils.create(Material.NAME_TAG, meta -> {
            meta.displayName(PbText.item("&8◆ &6Название"));
            meta.lore(java.util.List.of(
                PbText.item("&f" + this.levelName),
                PbText.item("&7Сменить можно в меню редактора")
            ));
        });
    }

    /**
     * ПЕРЕКЛЮЧАТЕЛЬ НЕБА.
     * <p>
     * Белый бетон - полное небо, кварцевая плита - половина: плита и есть половина
     * блока, так что по одному виду предмета понятно, что выбрано, даже не читая
     * подпись.
     */
    @NonNull
    private ItemStack skyItem() {
        ru.sortix.parkourbeat.levels.SkyMode sky = this.skyMode;
        return ItemUtils.create(sky.getIcon(), meta -> {
            meta.displayName(PbText.item("&8◆ &6Небо: " + sky.getDisplayName()));

            // ВЫСОТА УПОМИНАЕТСЯ ТОЛЬКО ТАМ, ГДЕ ОНА ДЕЙСТВИТЕЛЬНО МЕНЯЕТСЯ.
            //
            // Площадку двигают лишь те режимы, чей мир создаётся пустым. У шаблонных
            // она остаётся на своём месте, а небо держится флагом плоскости - обещать
            // им «встанет высоко» значило бы соврать.
            boolean movesPlatform = this.levelMode.isDigger() || this.levelMode.isThreeSixty();

            java.util.List<Component> lore = new java.util.ArrayList<>();
            if (sky.isFull()) {
                lore.add(PbText.item("&eНебо целиком, как на обычных уровнях"));
                if (movesPlatform) {
                    lore.add(PbText.item("&7Площадка встанет высоко - на Y=" + sky.getPlatformY()));
                }
            } else {
                lore.add(PbText.item("&eНижняя половина неба тёмная"));
                if (movesPlatform) {
                    lore.add(PbText.item("&7Площадка встанет низко - на Y=" + sky.getPlatformY()));
                }
            }
            lore.add(PbText.item("&8Потом это уже не поменять"));
            lore.add(PbText.item("&8Нажмите, чтобы сменить небо"));
            meta.lore(lore);
        });
    }

    private void switchSky() {
        this.skyMode = this.skyMode.toggle();
        this.render();
    }

    @NonNull
    private ItemStack modeItem() {
        ru.sortix.parkourbeat.twod.LevelMode mode = this.levelMode;
        return ItemUtils.create(mode.getIcon(), meta -> {
            meta.displayName(PbText.item("&8◆ &6Режим: " + mode.getDisplayName()));

            java.util.List<Component> lore = new java.util.ArrayList<>();
            switch (mode) {
                case THREE_D -> lore.add(PbText.item("&eОбычный паркур по пути из частиц"));
                case TWO_D -> lore.add(PbText.item("&eДвумерный уровень: кубик и линия"));
                case DUEL -> {
                    lore.add(PbText.item("&eКарта на двоих: две трассы в одном мире"));
                    lore.add(PbText.item("&7Синий путь - первый игрок, красный - второй"));
                    lore.add(PbText.item("&7Ширина всегда 4 чанка"));
                    lore.add(PbText.item("&8Нужно пройти "
                        + ru.sortix.parkourbeat.duel.DuelManager.REQUIRED_COMPLETED_LEVELS + " уровней"));
                }
                case THREE_SIXTY -> {
                    lore.add(PbText.item("&eПустота, каменная платформа и спавн на ней"));
                    lore.add(PbText.item("&7Строить можно во все стороны: по стенам,"));
                    lore.add(PbText.item("&7по потолку и под платформой"));
                    lore.add(PbText.item("&7Путей из частиц тут нет"));
                    lore.add(PbText.item("&8Урон за отпущенный бег выключен,"));
                    lore.add(PbText.item("&8можно будет включить в меню редактора если надо"));
                }
            }
            lore.add(PbText.item("&8Нажмите, чтобы сменить режим"));
            meta.lore(lore);
        });
    }

    @NonNull
    private ItemStack sizeItem() {
        return ItemUtils.create(this.chunkWidth >= 4 ? Material.QUARTZ_BLOCK : Material.STONE_BRICKS, meta -> {
            meta.displayName(PbText.item("&8◆ &6Размер: " + this.chunkWidth
                + " " + chunksWord(this.chunkWidth)));

            java.util.List<Component> lore = new java.util.ArrayList<>();
            if (this.levelMode.isThreeSixty()) {
                lore.add(PbText.item("&eПлощадка &f" + (this.chunkWidth * 16)
                    + "x" + (this.chunkWidth * 16) + "&e блоков вокруг спавна"));
                lore.add(PbText.item("&7Строить можно ровно в этих чанках"));
                lore.add(PbText.item("&8Нажмите, чтобы сменить размер"));
            } else if (this.levelMode.isDuel()) {
                lore.add(PbText.item("&eДуэльная карта всегда 4 чанка в ширину"));
                lore.add(PbText.item("&7Двум трассам нужно место, чтобы разойтись"));
                lore.add(PbText.item("&8Размер здесь не меняется"));
            } else {
                lore.add(PbText.item("&eШирина площадки для строительства"));
                lore.add(PbText.item("&8Нажмите, чтобы сменить ширину"));
            }
            meta.lore(lore);
        });
    }

    @NonNull
    private ItemStack worldItem() {
        World.Environment env = this.environment;
        return ItemUtils.create(worldIcon(env), meta -> {
            meta.displayName(PbText.item("&8◆ &6Мир: " + worldName(env)));
            meta.lore(java.util.List.of(
                PbText.item("&eНебо, туман и общий вид уровня"),
                PbText.item("&7Мир можно сменить и потом, блоки останутся"),
                PbText.item("&8Нажмите, чтобы сменить мир")
            ));
        });
    }

    @NonNull
    private static String worldName(@NonNull World.Environment environment) {
        return switch (environment) {
            case NETHER -> "Ад";
            case THE_END -> "Край";
            default -> "Обычный";
        };
    }

    @NonNull
    private static Material worldIcon(@NonNull World.Environment environment) {
        return switch (environment) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    private void switchWorld() {
        if (!DISPLAY_NON_DEFAULT_WORLD_TYPES) return;
        this.environment = switch (this.environment) {
            case NORMAL -> World.Environment.NETHER;
            case NETHER -> World.Environment.THE_END;
            default -> World.Environment.NORMAL;
        };
        this.render();
    }

    public static void startCreating(@NonNull ParkourBeat plugin, @NonNull Player player, String lang) {
        if (!player.hasPermission(PermissionConstants.CREATE_LEVEL)) {
            LangOptions.inventory_createlevel_nopermission.sendMsg(player);
            return;
        }

        ru.sortix.parkourbeat.rating.StatisticsManager statistics =
            plugin.get(ru.sortix.parkourbeat.rating.StatisticsManager.class);
        if (statistics.getDisplayRank(player.getUniqueId()) <= 0) {
            player.closeInventory();
            for (net.kyori.adventure.text.Component line
                : Lang.lore(player, "inventory.createlevel.locked")) {
                player.sendMessage(line);
            }
            return;
        }

        // Название спрашиваем наковальней, а не в чате: игроку не нужно закрывать
        // интерфейс, переключаться на чат и возвращаться обратно. Ресурспак сервера
        // перерисовывает это окно под создание уровня.
        new LevelNameAnvilMenu(plugin, lang, player).open();
    }

    @NonNull
    private static String chunksWord(int chunks) {
        return chunks == 1 ? "чанк" : (chunks < 5 ? "чанка" : "чанков");
    }

    /**
     * Переключение режима по кругу. Дуэльные карты пропускаются, если игрок ещё не
     * набрал нужного числа пройденных уровней: право строить их зарабатывается игрой.
     */
    private void switchMode(@NonNull Player player) {
        ru.sortix.parkourbeat.twod.LevelMode next = this.levelMode.next();

        if (next.isDuel()
            && !ru.sortix.parkourbeat.duel.DuelManager.canCreateDuelLevels(this.plugin, player)) {
            int completed = ru.sortix.parkourbeat.duel.DuelManager.getCompletedLevels(this.plugin, player);
            player.sendMessage(PbText.of("&cДуэльные карты открываются после &f"
                + ru.sortix.parkourbeat.duel.DuelManager.REQUIRED_COMPLETED_LEVELS
                + "&c пройденных уровней. Пройдено: &f" + completed + "&c."));
            next = next.next();
        }

        this.levelMode = next;

        // Ширина подгоняется под режим сразу: дуэль бывает только широкой, а обычный
        // уровень не умеет быть шириной в два или три чанка - под них нет шаблона.
        if (this.levelMode.isDuel()) {
            this.chunkWidth = ru.sortix.parkourbeat.duel.DuelManager.DUEL_CHUNK_WIDTH;
        } else if (!this.levelMode.isThreeSixty() && this.chunkWidth != 1 && this.chunkWidth < 4) {
            this.chunkWidth = 1;
        }

        this.render();
    }

    private void switchWidth() {
        if (this.levelMode.isDuel()) return;

        if (this.levelMode.isThreeSixty()) {
            // 1 - 2 - 3 - 4 - 1: у трубы осмысленны все размеры.
            this.chunkWidth = this.chunkWidth >= 4 ? 1 : this.chunkWidth + 1;
        } else {
            this.chunkWidth = this.chunkWidth >= 4 ? 1 : 4;
        }
        this.render();
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
