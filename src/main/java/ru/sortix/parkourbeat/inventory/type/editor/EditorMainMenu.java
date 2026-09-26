package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.commands.CommandDelete;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.item.editor.type.EditTrackPointsItem;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.LevelBossBarColor;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.utils.ChatColorPalette;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.world.LocationUtils;
import ru.sortix.parkourbeat.world.TeleportUtils;

import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.lang.Lang;
public class EditorMainMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final EditActivity activity;
    private final Level level;
    private int deleteConfirmations = 0;
    private int copyConfirmations = 0;

    public EditorMainMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 5, lang, LangOptions.inventory_editormain_title.getComponent(lang));
        this.activity = activity;
        this.level = activity.getLevel();

        boolean isOwner = activity.isOwner();

        this.setItem(
            2,
            1,
            ItemUtils.create(Material.FIREWORK_STAR, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_particlecolor_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_particlecolor_lore.getComponents(lang));
            }),
            this::selectParticlesColor);
        this.setItem(
            2,
            2,
            ItemUtils.create(Material.FIRE_CHARGE, (meta) -> {
                meta.displayName(Lang.item(this.lang, "inventory.editormain.jumpcolor.name")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer L =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
                lore.add(net.kyori.adventure.text.Component.empty());
                lore.add(Lang.item(this.lang, "inventory.editormain.jumpcolor.lore1")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(Lang.item(this.lang, "inventory.editormain.jumpcolor.lore2")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(net.kyori.adventure.text.Component.empty());
                Color jc = this.activity.getCurrentJumpColor();
                lore.add(PbText.of(jc == null
                        ? Lang.raw(lang, "auto.editor_main_menu.editor_main_menu.1")
                        : Lang.raw(lang, "auto.editor_main_menu.editor_main_menu.2") + String.format("%06X", jc.asRGB()))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(Lang.item(this.lang, "inventory.editormain.jumpcolor.lore_left")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(Lang.item(this.lang, "inventory.editormain.jumpcolor.lore_right")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(lore);
            }),
            this::selectJumpColor);
        this.setItem(
            2,
            3,
            ItemUtils.modifyMeta(SelectSongMenu.NOTE_HEAD.clone(), meta -> {
                meta.displayName(LangOptions.inventory_editormain_selectsong_name.getComponent(lang));
                MusicTrack musicTrack = activity.getLevel()
                    .getLevelSettings()
                    .getGameSettings()
                    .getMusicTrack();

                if (musicTrack == null) {
                    meta.lore(LangOptions.inventory_editormain_selectsong_notracklore.getComponents(lang));
                } else {
                    meta.lore(LangOptions.inventory_editormain_selectsong_lore.getComponents(lang,
                        new Placeholders("%track%", musicTrack.getName())));
                }
            }),
            this::selectLevelSong);
        this.setItem(
            2,
            4,
            ItemUtils.create(Material.NOTE_BLOCK, meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.editormain.markers.name"));
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                lore.add(Lang.item(this.lang, "inventory.editormain.markers.lore1"));
                lore.add(net.kyori.adventure.text.Component.empty());
                lore.add(legacyLine(Lang.raw(lang, "auto.editor_main_menu.editor_main_menu.3")
                    + this.level.getLightShow().getHelperMarkers().size()));
                meta.lore(lore);
            }),
            event -> new MarkersMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
        // Ровно над "Точкой спавна": перенос старта - соседняя по смыслу операция,
        // и искать её строитель будет там же.
        this.setItem(
            1,
            5,
            ItemUtils.create(Material.LIME_CONCRETE, (meta) -> {
                meta.displayName(Lang.item(this.lang, "inventory.editormain.startpoint.name"));
                java.util.List<Component> lore = new java.util.ArrayList<>();
                lore.add(Lang.item(this.lang, "inventory.editormain.startpoint.lore1"));
                lore.add(Lang.item(this.lang, "inventory.editormain.startpoint.lore2"));
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "inventory.editormain.startpoint.lore_click"));
                meta.lore(lore);
            }),
            this::setStartPoint);
        this.setItem(
            2,
            5,
            ItemUtils.create(Material.ENDER_PEARL, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_spawnpoint_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_spawnpoint_lore.getComponents(lang));
            }),
            this::setSpawnPoint);
        this.setItem(
            2,
            6,
            ItemUtils.modifyMeta(
                ru.sortix.parkourbeat.inventory.Heads.getHeadByTextureData(TEXTURES_HEAD, true),
                meta -> {
                    meta.displayName(Lang.item(this.lang, "inventory.editormain.textures.name"));
                    java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                    lore.add(Lang.item(this.lang, "inventory.editormain.textures.lore1"));
                    lore.add(net.kyori.adventure.text.Component.empty());
                    boolean has = this.level.getLevelSettings().getGameSettings().isCustomTextures();
                    lore.add(legacyLine(has ? Lang.raw(lang, "auto.editor_main_menu.editor_main_menu.4") : Lang.raw(lang, "auto.editor_main_menu.editor_main_menu.5")));
                    if (has) lore.add(Lang.item(this.lang, "inventory.editormain.textures.lore_moderation"));
                    meta.lore(lore);
                }),
            event -> new CustomTexturesMenu(this.plugin, this.lang, this.activity)
                .open(event.getPlayer()));
        this.setItem(
            2,
            7,
            ItemUtils.create(Material.BEACON, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_lightshow_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_lightshow_lore.getComponents(lang));
            }),
            this::openLightShowSettings);
        this.setItem(
            2,
            8,
            ItemUtils.create(Material.RED_CONCRETE, (meta) -> {
                meta.displayName(Lang.item(this.lang, "inventory.editormain.fallzones.name"));
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                lore.add(Lang.item(this.lang, "inventory.editormain.fallzones.lore1"));
                lore.add(net.kyori.adventure.text.Component.empty());
                lore.add(legacyLine(Lang.raw(lang, "auto.editor_main_menu.editor_main_menu.6") + this.level.getLightShow().getFallZonesAmount()));
                meta.lore(lore);
            }),
            event -> new FallZonesMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
        this.updateBossBarColorItem();
        this.setHeadsItem();
        this.updateBorderPushItem();
        this.updateDifficultyMultiplierItem();
        this.updateCheckpointsItem();

        if (isOwner) {
            this.setItem(
                3,
                3,
                ItemUtils.create(Material.WRITABLE_BOOK, (meta) -> {
                    meta.displayName(LangOptions.inventory_editormain_privacy_name.getComponent(lang));
                    meta.lore(LangOptions.inventory_editormain_privacy_lore.getComponents(lang));
                }),
                this::openPrivacySettings);
            this.setItem(
                3,
                7,
                ItemUtils.create(Material.PLAYER_HEAD, (meta) -> {
                    meta.displayName(LangOptions.inventory_editormain_coeditors_name.getComponent(lang));
                    meta.lore(LangOptions.inventory_editormain_coeditors_lore.getComponents(lang));
                }),
                this::openCoEditorsSettings);
        }

        this.setItem(
            3,
            5,
            ItemUtils.create(Material.REDSTONE_BLOCK, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_glow_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_glow_lore.getComponents(lang));
            }),
            event -> new GlowingBarriersMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
        this.setItem(
            4,
            3,
            ItemUtils.create(Material.NETHER_STAR, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_resetpoints_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_resetpoints_lore.getComponents(lang));
            }),
            this::resetAllTrackPoints);
        this.setItem(
            4,
            5,
            ItemUtils.create(Material.REDSTONE_TORCH, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_exit_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_exit_lore.getComponents(lang));
            }),
            this::leaveEditor);

        if (isOwner) {
            this.setItem(
                4,
                7,
                ItemUtils.create(Material.BARRIER, (meta) -> {
                    meta.displayName(LangOptions.inventory_editormain_delete_name.getComponent(lang));
                    meta.lore(LangOptions.inventory_editormain_delete_lore.getComponents(lang));
                }),
                this::deleteLevel);
        }

        boolean previewEnabled = activity.isPreviewEnabled();
        this.setItem(
            5,
            5,
            ItemUtils.create(previewEnabled ? Material.ENDER_EYE : Material.ENDER_PEARL, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_preview_name.getComponent(lang));
                meta.lore((previewEnabled
                    ? LangOptions.inventory_editormain_preview_lore_on
                    : LangOptions.inventory_editormain_preview_lore_off).getComponents(lang));
            }),
            event -> {
                Player player = event.getPlayer();
                this.activity.setPreviewEnabled(!this.activity.isPreviewEnabled());
                player.sendMessage((this.activity.isPreviewEnabled()
                    ? LangOptions.inventory_editormain_preview_turnedon
                    : LangOptions.inventory_editormain_preview_turnedoff).getComponent(lang));
                new EditorMainMenu(this.plugin, lang, this.activity).open(player);
            });

        boolean infiniteRunEnabled = activity.isInfiniteTesting();
        this.setItem(
            5,
            4,
            ItemUtils.create(infiniteRunEnabled ? Material.GOLDEN_BOOTS : Material.LEATHER_BOOTS, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_infiniterun_name.getComponent(lang));
                meta.lore((infiniteRunEnabled
                    ? LangOptions.inventory_editormain_infiniterun_lore_on
                    : LangOptions.inventory_editormain_infiniterun_lore_off).getComponents(lang));
            }),
            event -> {
                Player player = event.getPlayer();
                this.activity.setInfiniteTesting(!this.activity.isInfiniteTesting());
                player.sendMessage((this.activity.isInfiniteTesting()
                    ? LangOptions.inventory_editormain_infiniterun_turnedon
                    : LangOptions.inventory_editormain_infiniterun_turnedoff).getComponent(lang));
                new EditorMainMenu(this.plugin, lang, this.activity).open(player);
            });

        this.updateWorldTypeItem();

        this.setItem(
            5,
            3,
            ItemUtils.create(Material.STRING, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_particledistance_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_particledistance_lore.getComponents(lang,
                    new Placeholders("%distance%", String.format(java.util.Locale.ROOT, "%.1f",
                        this.level.getLevelSettings().getWorldSettings().getParticleViewDistance()))));
            }),
            this::changeParticleDistance);

        this.renderModeItems();

        // 2D-уровень: половина настроек к нему просто не относится.
        if (ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(this.level)) {
            this.applyTwoDLayout();
        }
    }

    /**
     * Кнопки, которые есть только у отдельных режимов уровня.
     * <p>
     * На обычном уровне ни одна из них не появляется: настройка урона за бег там не
     * трогается принципиально (это основа геймплея), а сторон у трассы всего одна.
     */
    private void renderModeItems() {
        GameSettings gameSettings = this.level.getLevelSettings().getGameSettings();

        this.renderCopyItem(gameSettings);

        if (gameSettings.isThreeSixtyLevel()) {
            this.applyThreeSixtyLayout();
        }

        if (gameSettings.isDuelLevel()) {
            this.applyDuelLayout();
        }

        if (gameSettings.isDiggerLevel()) {
            this.applyDiggerLayout();
        }

        if (gameSettings.isThreeSixtyLevel()) {
            boolean sprintDamage = gameSettings.isSprintDamage();
            this.setItem(
                5,
                2,
                ItemUtils.create(sprintDamage ? Material.IRON_BOOTS : Material.FEATHER, (meta) -> {
                    meta.displayName(PbText.item("&8◆ &6Урон за отпущенный бег"));
                    meta.lore(java.util.List.of(
                        PbText.item(sprintDamage ? "&aВключён" : "&7Выключен"),
                        PbText.item("&eНа 360-уровне клиент сам снимает спринт"),
                        PbText.item("&eна разворотах, поэтому по умолчанию урона нет"),
                        PbText.item("&7Включайте, если трасса рассчитана"),
                        PbText.item("&7на обычные правила"),
                        PbText.item("&8Нажмите, чтобы переключить")
                    ));
                }),
                event -> {
                    Player player = event.getPlayer();
                    gameSettings.setSprintDamage(!gameSettings.isSprintDamage());
                    this.plugin.get(ru.sortix.parkourbeat.levels.LevelsManager.class)
                        .saveGameSettings(gameSettings);
                    player.sendMessage(PbText.of(gameSettings.isSprintDamage()
                        ? "&aУрон за отпущенный бег включён."
                        : "&cУрон за отпущенный бег выключен."));
                    new EditorMainMenu(this.plugin, this.lang, this.activity).open(player);
                });
        }
    }

    /**
     * КОПИЯ УРОВНЯ.
     * <p>
     * Кнопка есть только у владельца: копия создаётся на его имя, и раздавать это
     * право соредакторам значило бы плодить чужие уровни от чужого лица.
     */
    private void renderCopyItem(@NonNull GameSettings gameSettings) {
        if (!this.activity.isOwner()) return;

        boolean available = gameSettings.canBeCopied();

        this.setItem(
            5,
            7,
            ItemUtils.create(available ? Material.BOOK : Material.GRAY_DYE, (meta) -> {
                meta.displayName(PbText.item("&8◆ &6Создать копию уровня"));

                if (!available) {
                    meta.lore(java.util.List.of(
                        PbText.item(gameSettings.isCopy()
                            ? "&7Это и есть копия: с неё копию не снять"
                            : "&7Копия с этого уровня уже снята"),
                        PbText.item("&8Копия разрешена ровно одна")
                    ));
                    return;
                }

                meta.lore(java.util.List.of(
                    PbText.item("&eНовый уровень со всем содержимым:"),
                    PbText.item("&7блоки, путь, световое шоу, маркеры,"),
                    PbText.item("&7порталы, зоны, чекпоинты, трек и название"),
                    PbText.item("&7Копия будет приватной и без рейтинга"),
                    PbText.item("&8Ресурспак не переносится: он привязан к оригиналу"),
                    PbText.item(this.copyConfirmations > 0
                        ? "&cНажмите ещё раз для создания копии"
                        : "&8Нажмите, чтобы создать копию")
                ));
            }),
            this::copyLevel);
    }

    private void copyLevel(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        GameSettings gameSettings = this.level.getLevelSettings().getGameSettings();

        if (!this.activity.isOwner()) return;

        if (!gameSettings.canBeCopied()) {
            player.sendMessage(PbText.of(gameSettings.isCopy()
                ? "&cС копии копию снять нельзя."
                : "&cКопия с этого уровня уже создана."));
            return;
        }

        if (this.activity.isTesting()) {
            player.sendMessage(PbText.of("&cСначала завершите тестовый забег."));
            return;
        }

        if (this.copyConfirmations == 0) {
            this.copyConfirmations++;
            this.renderCopyItem(gameSettings);
            player.sendMessage(PbText.of("&eКопия разрешена одна. Нажмите ещё раз, чтобы подтвердить."));
            return;
        }

        this.copyConfirmations = 0;
        player.closeInventory();
        player.sendMessage(PbText.of("&7Копируем уровень, это займёт несколько секунд..."));

        this.plugin.get(ru.sortix.parkourbeat.levels.LevelsManager.class)
            .copyLevelAsync(this.level, player.getUniqueId(), player.getName())
            .thenAccept(copy -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (copy == null) {
                    player.sendMessage(PbText.of("&cНе удалось создать копию уровня."));
                    return;
                }
                String copyName = net.kyori.adventure.text.serializer.legacy
                    .LegacyComponentSerializer.legacyAmpersand().serialize(copy.getDisplayName());
                player.sendMessage(PbText.of("&aКопия создана: " + copyName
                    + " &7(#" + copy.getUniqueNumber() + ")"));
                player.sendMessage(PbText.of("&7Она приватная и без рейтинга. Открываю редактор копии..."));
                ru.sortix.parkourbeat.inventory.type.LevelsListMenu
                    .startEditing(this.plugin, player, copy, false);
            }));
    }

    /**
     * ЧИСТКА МЕНЮ ПОД 2D.
     * <p>
     * Цвет частиц, цвет прыжков, маркеры, текстуры, зоны падения, отталкивание от
     * границы, жёсткость, чекпоинты и дальность пути на двумерном уровне не работают
     * вообще: там нет ни пути из частиц, ни прыжковых окон, ни границ. Оставлять
     * мёртвые кнопки хуже, чем убрать их совсем.
     */
    /**
     * ЧИСТКА МЕНЮ ПОД 360.
     * <p>
     * Убирается всё, что относится к пути из частиц: его на 360-уровне нет, а значит
     * нет ни цвета частиц, ни цвета прыжковых колец, ни точки старта (забег начинается
     * с первого движения). Бесконечный бег тоже не нужен: заканчивается забег на блоке
     * финиша, а не на линии, и «бежать дальше финиша» там некуда.
     * <p>
     * Эффекты, световое шоу, маркеры и порталы остаются: они к пути не привязаны.
     */
    private void applyThreeSixtyLayout() {
        this.setItem(1, 5, null, null); // перенос точки старта
        this.setItem(2, 1, null, null); // цвет частиц
        this.setItem(2, 2, null, null); // цвет прыжков
        this.setItem(5, 4, null, null); // бесконечный бег
    }

    /**
     * ЧИСТКА МЕНЮ ПОД ДУЭЛЬ.
     * <p>
     * Цвет частиц и цвет прыжковых колец на дуэльной карте не настраиваются: цвет там
     * означает сторону - синяя трасса у первого игрока, красная у второго. Дать их
     * перекрасить значит дать перепутать, кто по какой бежит.
     */
    /**
     * ЧИСТКА МЕНЮ ПОД «КОПАТЕЛЯ».
     * <p>
     * Пути из частиц здесь нет вообще: игрока везёт носитель по прямому тоннелю, и
     * ни точки старта, ни цвета частиц, ни цвета прыжковых колец не существует. Прыжков
     * нет тоже, зато есть удар по руде - и слот бывшего цвета частиц занимает выбор его
     * звука, самая частая настройка в этом режиме.
     * <p>
     * Чекпоинты убраны отдельно: карта размечена одним таймкодом от старта, трек играет
     * целиком, и нарезать его по чекпоинтам значит развалить весь ритм.
     */
    private void applyDiggerLayout() {
        this.setItem(1, 5, null, null); // перенос точки старта
        this.setItem(2, 2, null, null); // цвет прыжковых колец
        this.setItem(2, 8, null, null); // зоны падения: падать не с чего
        this.setItem(3, 1, null, null); // отталкивание от границы
        this.setItem(3, 2, null, null); // жёсткость
        this.setItem(3, 4, null, null); // чекпоинты
        this.setItem(5, 4, null, null); // бесконечный бег

        // Слот цвета частиц освобождается совсем: звук попадания и всё остальное
        // живёт в настройках карты, дублировать его здесь незачем.
        this.setItem(2, 1, null, null);

        // Слот дальности пути - под настройки карты: BPM, скорость, кирка, тоннель.
        this.setItem(
            5,
            3,
            ItemUtils.create(Material.NETHERITE_PICKAXE, (meta) -> {
                meta.displayName(legacyLine("&8◆ &6Настройки «Копателя»"));
                java.util.List<Component> lore = new java.util.ArrayList<>();
                lore.add(legacyLine("&eТемп, скорость, кирка, размеры тоннеля"));
                lore.add(legacyLine("&7Там же руды, авторазметка и звук удара"));
                lore.add(legacyLine("&8Нажмите, чтобы открыть"));
                meta.lore(lore);
            }),
            event -> new ru.sortix.parkourbeat.digger.DiggerSettingsMenu(
                this.plugin, this.lang, this.level).open(event.getPlayer()));
    }

    private void applyDuelLayout() {
        this.setItem(2, 1, null, null); // цвет частиц
        this.setItem(2, 2, null, null); // цвет прыжков
    }

    private void applyTwoDLayout() {
        this.setItem(2, 1, null, null); // цвет частиц
        this.setItem(2, 2, null, null); // цвет прыжков
        // Маркеры (2,4) НЕ убираем: строителю 2D-уровня они нужны ровно так же,
        // как и на обычном - размечать связки и сложные места.
        this.setItem(2, 6, null, null); // пользовательские текстуры
        this.setItem(2, 8, null, null); // зоны падения
        this.setItem(3, 1, null, null); // отталкивание от границы
        this.setItem(3, 2, null, null); // жёсткость
        this.setItem(3, 4, null, null); // чекпоинты
        // Дальность пути на 2D не нужна, зато её слот освобождается под вход
        // в настройки 2D. Маркеры (2,4) при этом остаются на месте: строителю
        // 2D-уровня они нужны ровно так же, как и на обычном.
        this.setItem(
            5,
            3,
            ItemUtils.create(Material.REPEATING_COMMAND_BLOCK, (meta) -> {
                meta.displayName(legacyLine(Lang.raw(this.lang, "auto.editor_main_menu.apply_two_d_layout.1")));
                java.util.List<Component> lore = new java.util.ArrayList<>();
                lore.add(legacyLine(Lang.raw(this.lang, "auto.editor_main_menu.apply_two_d_layout.2")));
                lore.add(legacyLine(Lang.raw(this.lang, "auto.editor_main_menu.apply_two_d_layout.3")));
                lore.add(legacyLine(Lang.raw(this.lang, "auto.editor_main_menu.apply_two_d_layout.4")));
                meta.lore(lore);
            }),
            event -> new TwoDSettingsMenu(this.plugin, this.lang, this.activity)
                .open(event.getPlayer()));
    }

    /**
     * Иконка показывает, в каком мире уровень находится прямо сейчас, поэтому строителю
     * не нужно открывать меню, чтобы это узнать.
     */
    private void updateWorldTypeItem() {
        org.bukkit.World.Environment environment =
            this.level.getLevelSettings().getWorldSettings().getEnvironment();

        this.setItem(
            5,
            6,
            ItemUtils.create(SelectWorldTypeMenu.getIcon(environment), (meta) -> {
                meta.displayName(Lang.item(this.lang, "inventory.editormain.worldtype.name"));

                java.util.List<Component> lore = new java.util.ArrayList<>();
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "inventory.editormain.worldtype.lore1"));
                lore.add(Lang.item(this.lang, "inventory.editormain.worldtype.lore2"));
                lore.add(Lang.item(this.lang, "inventory.editormain.worldtype.lore3"));
                lore.add(Component.empty());
                lore.add(legacyLine(Lang.raw(this.lang, "auto.editor_main_menu.update_world_type_item.1")
                    + ru.sortix.parkourbeat.world.WorldTypeSwitcher.getDisplayName(environment)));
                lore.add(Lang.item(this.lang, "inventory.editormain.worldtype.lore_click"));
                meta.lore(lore);
            }),
            event -> new SelectWorldTypeMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    @NonNull
    private static net.kyori.adventure.text.Component legacyLine(@NonNull String legacy) {
        return PbText.of(legacy)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    private void leaveEditor(@NonNull ClickEvent event) {
        Player player = event.getPlayer();

        TeleportUtils.teleportAsync(this.plugin, player, Settings.getLobbySpawn()).thenAccept(success -> {
            if (success) return;
            player.sendMessage(LangOptions.inventory_editormain_exit_canceled.getComponent(lang));
        });
    }

    private void selectParticlesColor(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editormain_particlecolor_unavilable.getComponent(lang));
            return;
        }

        ChatColorPalette.sendPalette(player);

        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editormain_particlecolor_timeout.getComponent(lang));
                return;
            }

            String hex = message.startsWith("#") ? message.substring(1) : message;
            Color color;
            try {
                color = Color.fromRGB(Integer.valueOf(hex, 16));
            } catch (IllegalArgumentException e) {
                player.sendMessage(LangOptions.inventory_editormain_particlecolor_invalidhex.getComponent(lang));
                return;
            }
            this.activity.setCurrentColor(color);
            for(Component component : LangOptions.inventory_editormain_particlecolor_selectedcolor.getComponents(lang, new Placeholders("%color%", hex))) {
                player.sendMessage(component);
            }
        });
    }

    private void selectJumpColor(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        if (!event.isLeft()) {
            this.activity.setCurrentJumpColor(null);
            player.sendMessage(Lang.item(this.lang, "inventory.editormain.jumpcolor.reset"));
            return;
        }

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editormain_particlecolor_unavilable.getComponent(lang));
            return;
        }

        ChatColorPalette.sendPalette(player);

        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editormain_particlecolor_timeout.getComponent(lang));
                return;
            }
            String hex = message.startsWith("#") ? message.substring(1) : message;
            Color color;
            try {
                color = Color.fromRGB(Integer.valueOf(hex, 16));
            } catch (IllegalArgumentException e) {
                player.sendMessage(LangOptions.inventory_editormain_particlecolor_invalidhex.getComponent(lang));
                return;
            }
            this.activity.setCurrentJumpColor(color);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.editor_main_menu.select_jump_color.1") + hex.toUpperCase()));
        });
    }

    private void selectLevelSong(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        new SelectSongMenu(this.plugin, lang, this.activity).open(player);
    }

    /**
     * Перенести стартовую точку уровня туда, где стоит строитель.
     * <p>
     * Финиш отдельной кнопки не имеет и иметь не должен: им всегда становится последняя
     * точка пути из частиц. Поэтому здесь двигается ровно нулевая точка списка, а порядок
     * прохождения (нулевая - начало, последняя - конец) остаётся нерушимым.
     */
    private void setStartPoint(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        LevelSettings levelSettings = this.level.getLevelSettings();
        WorldSettings worldSettings = levelSettings.getWorldSettings();
        ru.sortix.parkourbeat.levels.DirectionChecker checker = levelSettings.getDirectionChecker();

        Location newStart = player.getLocation().clone();

        // Старт не может стоять дальше по трассе, чем следующая точка пути: иначе первый
        // же шаг игрока считался бы движением назад, а часть пути осталась бы "до старта".
        java.util.List<ru.sortix.parkourbeat.levels.Waypoint> waypoints = worldSettings.getWaypoints();
        if (waypoints.size() >= 2
            && !checker.isCorrectDirection(newStart, waypoints.get(1).getLocation())) {
            player.sendMessage(Lang.text(this.lang, "inventory.editormain.startpoint.fail_ahead"));
            return;
        }

        // Спавн обязан оставаться позади старта: забег начинается в момент пересечения
        // стартовой точки, и спавн впереди неё запускал бы уровень мгновенно.
        if (!checker.isCorrectDirection(worldSettings.getSpawn(), newStart)) {
            player.sendMessage(Lang.text(this.lang, "inventory.editormain.startpoint.fail_spawn"));
            return;
        }

        worldSettings.moveStartPoint(newStart);
        levelSettings.recalculateWaypoints(this.level.getWorld());
        levelSettings.updateParticleLocations();

        player.sendMessage(Lang.text(this.lang, "inventory.editormain.startpoint.success"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
    }

    private void setSpawnPoint(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        LevelSettings levelSettings = this.level.getLevelSettings();
        Location playerLocation = player.getLocation().clone();

        if (!LocationUtils.isValidSpawnPoint(playerLocation, levelSettings)) {
            player.sendMessage(LangOptions.inventory_editormain_spawnpoint_fail.getComponent(lang));
            return;
        }

        playerLocation.setPitch(0f);
        switch (levelSettings.getDirectionChecker().direction()) {
            case POSITIVE_X: playerLocation.setYaw(-90f); break;
            case NEGATIVE_X: playerLocation.setYaw(90f); break;
            case POSITIVE_Z: playerLocation.setYaw(0f); break;
            case NEGATIVE_Z: playerLocation.setYaw(180f); break;
        }

        levelSettings.getWorldSettings().setSpawn(playerLocation);
        player.teleport(playerLocation);

        player.sendMessage(LangOptions.inventory_editormain_spawnpoint_success.getComponent(lang));
    }

    private void openLightShowSettings(@NonNull ClickEvent event) {
        new LightShowMenu(this.plugin, this.lang, this.activity).open(event.getPlayer());
    }

    private void updateBorderPushItem() {
        double strength = this.getGameSettings().getBorderPushStrength();
        this.setItem(
            3,
            1,
            ItemUtils.create(strength > 0 ? org.bukkit.Material.SLIME_BALL : org.bukkit.Material.GRAY_DYE, (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_borderpush_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editormain_borderpush_lore.getComponents(
                    lang, new Placeholders("%value%", String.format("%.2f", strength))));
            }),
            this::requestBorderPush);
    }

    private void requestBorderPush(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager =
            this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editormain_borderpush_unavailable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editormain_borderpush_request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editormain_borderpush_timeout.getComponent(lang));
                return;
            }
            double value;
            try {
                value = Double.parseDouble(message.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                player.sendMessage(LangOptions.inventory_editormain_borderpush_invalid.getComponent(lang));
                return;
            }
            if (value < 0) value = 0;
            if (value > 5) value = 5;
            this.getGameSettings().setBorderPushStrength(value);
            new EditorMainMenu(this.plugin, this.lang, this.activity).open(player);
        });
    }

    // ==================== ЧЕКПОИНТЫ ====================

    private void updateCheckpointsItem() {
        ru.sortix.parkourbeat.levels.settings.LightShowSettings lightShow = this.level.getLightShow();
        int amount = lightShow.getCheckpointsAmount();

        java.util.List<Integer> offsets = new java.util.ArrayList<>();
        java.util.List<ru.sortix.parkourbeat.levels.settings.Checkpoint> ordered =
            new java.util.ArrayList<>(lightShow.getCheckpoints());
        ordered.sort(java.util.Comparator.comparingDouble(checkpoint ->
            ru.sortix.parkourbeat.levels.LightShowPositions
                .getSignedDistance(this.level, checkpoint.getPosition())));
        for (ru.sortix.parkourbeat.levels.settings.Checkpoint checkpoint : ordered) {
            if (!checkpoint.isEnabled()) continue;
            offsets.add(ru.sortix.parkourbeat.levels.LightShowPositions
                .toTimeMillis(this.level, checkpoint.getPosition()));
        }

        GameSettings settings = this.getGameSettings();
        boolean ready = settings.hasUsableSlices(offsets.size());
        boolean outdated = settings.isSliceOutdated(offsets);

        this.setItem(
            3,
            4,
            ItemUtils.create(amount == 0
                ? Material.WHITE_BANNER
                : (ready && !outdated ? Material.LIME_BANNER : Material.ORANGE_BANNER), (meta) -> {
                meta.displayName(Lang.item(this.lang, "inventory.editormain.checkpoints.name"));

                java.util.List<Component> lore = new java.util.ArrayList<>();
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "inventory.editormain.checkpoints.lore1"));
                lore.add(legacyLine(Lang.raw(this.lang, "auto.editor_main_menu.update_checkpoints_item.1") + amount + "&8/&f"
                    + ru.sortix.parkourbeat.levels.settings.LightShowSettings.MAX_CHECKPOINTS));
                lore.add(legacyLine(amount == 0 ? Lang.raw(this.lang, "auto.editor_main_menu.update_checkpoints_item.2")
                    : (ready && !outdated ? Lang.raw(this.lang, "auto.editor_main_menu.update_checkpoints_item.3") : Lang.raw(this.lang, "auto.editor_main_menu.update_checkpoints_item.4"))));
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "inventory.editormain.checkpoints.lore_click"));
                meta.lore(lore);
            }),
            event -> new CheckpointsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    // ==================== СЛОЖНОСТЬ УРОВНЯ (+N) ====================

    private void updateDifficultyMultiplierItem() {
        double multiplier = this.getGameSettings().getDifficultyMultiplier();
        boolean hardcore = this.getGameSettings().isHardcoreDifficulty();

        this.setItem(
            3,
            2,
            ItemUtils.create(hardcore ? Material.DIAMOND_SWORD : Material.IRON_SWORD, (meta) -> {
                meta.displayName(legacyLine(Lang.raw(this.lang, "auto.editor_main_menu.update_difficulty_multiplier_item.1") + GameSettings.formatDifficultyColored(multiplier)));

                java.util.List<Component> lore = new java.util.ArrayList<>();
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "inventory.editormain.difficulty.lore1"));
                lore.add(Lang.item(this.lang, "inventory.editormain.difficulty.lore2"));
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, "inventory.editormain.difficulty.lore_left"));
                lore.add(Lang.item(this.lang, "inventory.editormain.difficulty.lore_right"));
                meta.lore(lore);

                if (hardcore) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                        org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                } else {
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                }
            }),
            this::requestDifficultyMultiplier);
    }

    private static double perfectWindowRatio(double multiplier) {
        double base = ru.sortix.parkourbeat.rating.JumpTriggerEvaluator.FRONT_PERFECT_RADIUS;
        double now = ru.sortix.parkourbeat.rating.JumpTriggerEvaluator.frontPerfectRadius(multiplier);
        return base <= 0 ? 1.0D : now / base;
    }

    private static double damageScale(double multiplier) {
        return 1.0D + ru.sortix.parkourbeat.game.Game.DAMAGE_GROW_PER_DIFFICULTY_LEVEL
            * Math.max(0.0D, multiplier - 1.0D);
    }

    private static double damageRateScale(double multiplier) {
        return 1.0D + ru.sortix.parkourbeat.game.Game.DAMAGE_RATE_GROW_PER_DIFFICULTY_LEVEL
            * Math.max(0.0D, multiplier - 1.0D);
    }

    @NonNull
    private static String formatNumber(double value) {
        String formatted = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted;
    }

    private void requestDifficultyMultiplier(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        if (!event.isLeft()) {
            this.getGameSettings().setDifficultyMultiplier(GameSettings.MIN_DIFFICULTY_MULTIPLIER);
            player.sendMessage(legacyLine(Lang.raw(PlayerLang.of(player), "auto.editor_main_menu.request_difficulty_multiplier.1")
                + GameSettings.formatDifficultyColored(GameSettings.MIN_DIFFICULTY_MULTIPLIER)));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            new EditorMainMenu(this.plugin, this.lang, this.activity).open(player);
            return;
        }

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(Lang.item(this.lang, "inventory.editormain.difficulty.input_busy"));
            return;
        }

        player.sendMessage(Lang.item(this.lang, "inventory.editormain.difficulty.input_ask"));
        player.sendMessage(legacyLine(Lang.raw(PlayerLang.of(player), "auto.editor_main_menu.request_difficulty_multiplier.2")
            + GameSettings.formatDifficultyValue(GameSettings.MIN_DIFFICULTY_MULTIPLIER)
            + Lang.raw(PlayerLang.of(player), "auto.editor_main_menu.request_difficulty_multiplier.3") + GameSettings.formatDifficultyValue(GameSettings.MAX_DIFFICULTY_MULTIPLIER)
            + Lang.raw(PlayerLang.of(player), "auto.editor_main_menu.request_difficulty_multiplier.4")));

        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(Lang.item(this.lang, "inventory.editormain.difficulty.input_timeout"));
                return;
            }
            double value;
            try {
                value = Double.parseDouble(message.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                player.sendMessage(Lang.item(this.lang, "inventory.editormain.difficulty.input_invalid"));
                return;
            }
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                player.sendMessage(Lang.item(this.lang, "inventory.editormain.difficulty.input_invalid2"));
                return;
            }

            double clamped = Math.max(GameSettings.MIN_DIFFICULTY_MULTIPLIER,
                Math.min(GameSettings.MAX_DIFFICULTY_MULTIPLIER, value));
            this.getGameSettings().setDifficultyMultiplier(clamped);

            if (clamped != value) {
                player.sendMessage(legacyLine(Lang.raw(PlayerLang.of(player), "auto.editor_main_menu.request_difficulty_multiplier.5")
                    + GameSettings.formatDifficultyValue(GameSettings.MIN_DIFFICULTY_MULTIPLIER) + " - "
                    + GameSettings.formatDifficultyValue(GameSettings.MAX_DIFFICULTY_MULTIPLIER)));
            }
            player.sendMessage(legacyLine(Lang.raw(PlayerLang.of(player), "auto.editor_main_menu.request_difficulty_multiplier.6")
                + GameSettings.formatDifficultyColored(clamped)));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            new EditorMainMenu(this.plugin, this.lang, this.activity).open(player);
        });
    }

    private static final String TEXTURES_HEAD =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzk0YTNkNWQ5MmQ1YTYwNjQ2NzAzYmU5NWNiYzRmMjdiZmMyNDUwNjc1MGU5ZGIyYWJlMzRhZTI3MjIxOWMwMyJ9fX0=";

    private static final String HEADS_TEXTURE =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTZlZjFjMjVmNTE2ZjJlN2Q2Zjc2Njc0MjBlMzNhZGNmM2NkZjkzOGNiMzdmOWE0MWE4YjM1ODY5ZjU2OWIifX19";

    private void setHeadsItem() {
        this.setItem(3, 9,
            ItemUtils.modifyMeta(
                ru.sortix.parkourbeat.inventory.Heads.getHeadByTextureData(HEADS_TEXTURE, true),
                meta -> {
                    meta.displayName(Lang.item(this.lang, "inventory.editormain.heads.name"));
                    java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                    lore.add(Lang.item(this.lang, "inventory.editormain.heads.lore1"));
                    lore.add(net.kyori.adventure.text.Component.empty());
                    lore.add(Lang.item(this.lang, "inventory.editormain.heads.lore_left"));
                    lore.add(Lang.item(this.lang, "inventory.editormain.heads.lore_right"));
                    lore.add(net.kyori.adventure.text.Component.empty());
                    lore.add(Lang.item(this.lang, "inventory.editormain.heads.lore_hint1"));
                    lore.add(Lang.item(this.lang, "inventory.editormain.heads.lore_hint2"));
                    meta.lore(lore);
                }),
            event -> {
                Player player = event.getPlayer();
                if (event.isLeft()) {
                    player.closeInventory();
                    player.performCommand("hdb");
                } else {
                    this.requestHeadSearch(player);
                }
            });
    }

    /**
     * Фраза спрашивается в чат, потому что каталог ищет только по английским словам,
     * и вслепую подобрать нужное почти невозможно.
     */
    private void requestHeadSearch(@NonNull Player player) {
        ru.sortix.parkourbeat.player.input.PlayersInputManager manager =
            this.plugin.get(ru.sortix.parkourbeat.player.input.PlayersInputManager.class);
        if (manager.isInputRequested(player)) return;

        player.closeInventory();
        player.sendMessage(Lang.item(this.lang, "inventory.editormain.heads.search_ask"));
        player.sendMessage(Lang.item(this.lang, "inventory.editormain.heads.search_example"));

        manager.requestChatInput(player, 20 * 60).thenAccept(message -> {
            if (message == null) return;

            String phrase = message.trim();
            if (phrase.isEmpty()) return;
            if (phrase.length() > 64) phrase = phrase.substring(0, 64);

            String query = phrase.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
            if (query.isEmpty()) {
                player.sendMessage(Lang.item(this.lang, "inventory.editormain.heads.search_invalid"));
                return;
            }

            String command = "hdb search " + query;
            this.plugin.getServer().getScheduler().runTask(this.plugin,
                () -> {
                    if (!player.isOnline()) return;
                    player.performCommand(command);
                });
        });
    }

    private void updateBossBarColorItem() {
        LevelBossBarColor barColor = this.getGameSettings().getBossBarColor();
        boolean hidden = this.getGameSettings().isHideBossBar();
        this.setItem(
            2,
            9,
            ItemUtils.create(hidden ? org.bukkit.Material.BARRIER : barColor.getIconMaterial(), (meta) -> {
                meta.displayName(LangOptions.inventory_editormain_bossbar_name.getComponent(lang));
                meta.lore(concatLore(
                    LangOptions.inventory_editormain_bossbar_lore.getComponents(
                        lang, new Placeholders("%color%", barColor.getDisplayNameString(lang))),
                    (hidden
                        ? LangOptions.inventory_editormain_bossbar_hidden
                        : LangOptions.inventory_editormain_bossbar_shown).getComponents(lang)));
            }),
            event -> {
                if (event.isLeft()) {
                    this.openBossBarColorSelection(event);
                } else {
                    this.getGameSettings().setHideBossBar(!this.getGameSettings().isHideBossBar());
                    Player player = event.getPlayer();
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    this.updateBossBarColorItem();
                }
            });
    }

    private static java.util.List<net.kyori.adventure.text.Component> concatLore(
        java.util.List<net.kyori.adventure.text.Component> a,
        java.util.List<net.kyori.adventure.text.Component> b) {
        java.util.List<net.kyori.adventure.text.Component> result = new java.util.ArrayList<>(a);
        result.add(net.kyori.adventure.text.Component.empty());
        result.addAll(b);
        return result;
    }

    @NonNull
    private GameSettings getGameSettings() {
        return this.level.getLevelSettings().getGameSettings();
    }

    private void openBossBarColorSelection(@NonNull ClickEvent event) {
        new SelectBossBarColorMenu(
            this.plugin,
            this.lang,
            this.activity,
            this.getGameSettings().getBossBarColor(),
            (player, barColor) -> {
                this.getGameSettings().setBossBarColor(barColor);

                Placeholders namePlaceholder = new Placeholders("%name%", player.getName());
                Placeholders colorPlaceholder = new Placeholders("%color%", barColor.getDisplayNameString(lang));
                for (Player editor : this.activity.getAllEditors()) {
                    editor.sendMessage(LangOptions.inventory_editormain_bossbarchanged.getComponent(
                        PlayerLang.of(editor), namePlaceholder, colorPlaceholder));
                }

                new EditorMainMenu(this.plugin, this.lang, this.activity).open(player);
            },
            player -> new EditorMainMenu(this.plugin, this.lang, this.activity).open(player)
        ).open(event.getPlayer());
    }

    private void openPrivacySettings(@NonNull ClickEvent event) {
        new PrivacySettingsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer());
    }

    private void openCoEditorsSettings(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        // true - разрешаем администратору: кнопка ему уже показана, отказывать в ней
        // после нажатия было бы издевательством.
        if (!this.level.getLevelSettings().getGameSettings().isOwner(player, true, false)) {
            player.sendMessage(LangOptions.inventory_editorcoeditors_notowner.getComponent(lang));
            player.closeInventory();
            return;
        }
        new CoEditorsMenu(this.plugin, this.lang, this.activity).open(player);
    }

    private void resetAllTrackPoints(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        EditTrackPointsItem.clearAllPoints(this.level);
        player.sendMessage(LangOptions.inventory_editormain_resetpoints_reset.getComponent(lang));
    }

    private void deleteLevel(@NonNull ClickEvent event) {
        Player player = event.getPlayer();

        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        if (!settings.isOwner(player, true, true)) {
            player.closeInventory();
            player.sendMessage(LangOptions.level_editor_delete_notowner.getComponent(lang));
            return;
        }

        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer L =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();

        this.deleteConfirmations++;
        if (this.deleteConfirmations < 2) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.editor_main_menu.delete_level.1")));
            this.setItem(
                4,
                7,
                ItemUtils.create(Material.BARRIER, (meta) -> {
                    meta.displayName(Lang.item(this.lang, "inventory.editormain.delete.confirm_name")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                    lore.add(net.kyori.adventure.text.Component.empty());
                    lore.add(Lang.item(this.lang, "inventory.editormain.delete.confirm_lore1")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    lore.add(Lang.item(this.lang, "inventory.editormain.delete.confirm_lore2")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    meta.lore(lore);
                }),
                this::deleteLevel);
            return;
        }

        player.closeInventory();
        CommandDelete.deleteLevel(this.plugin, player, settings);
    }

    private void changeParticleDistance(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editormain_particledistance_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editormain_particledistance_request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editormain_particledistance_timeout.getComponent(lang));
                return;
            }

            double distance;
            try {
                distance = Double.parseDouble(message.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                player.sendMessage(LangOptions.inventory_editormain_particledistance_invalid.getComponent(lang));
                return;
            }
            if (distance < WorldSettings.MIN_VIEW_DISTANCE || distance > WorldSettings.MAX_VIEW_DISTANCE) {
                player.sendMessage(LangOptions.inventory_editormain_particledistance_invalid.getComponent(lang));
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                this.level.getLevelSettings().getWorldSettings().setParticleViewDistance(distance);
                this.level.applyViewDistances();
                player.sendMessage(LangOptions.inventory_editormain_particledistance_success.getComponent(lang,
                    new Placeholders("%distance%", String.format(java.util.Locale.ROOT, "%.1f",
                        this.level.getLevelSettings().getWorldSettings().getParticleViewDistance()))));
                new EditorMainMenu(this.plugin, this.lang, this.activity).open(player);
            });
        });
    }
}
