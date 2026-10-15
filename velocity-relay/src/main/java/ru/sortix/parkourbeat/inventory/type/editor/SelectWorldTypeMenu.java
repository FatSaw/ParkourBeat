package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.world.LevelWidthSwitcher;
import ru.sortix.parkourbeat.world.WorldTypeSwitcher;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.PbText;
public class SelectWorldTypeMenu extends ParkourBeatInventory implements EditLevelMenu {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    /** Мир, выбранный первым кликом и ждущий подтверждения вторым. */
    private @Nullable World.Environment pending = null;
    /** Ширина, выбранная первым кликом и ждущая подтверждения вторым. */
    private @Nullable Integer pendingWidth = null;

    public SelectWorldTypeMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, ru.sortix.parkourbeat.ui.Glyphs.menuTitle(
            (char) (0xE56A + ru.sortix.parkourbeat.ui.Glyphs.localeSet(lang)),
            ru.sortix.parkourbeat.ui.Icons.Icon.GLOBE.component().append(Component.text(" "))
                .append(line(Lang.raw(lang, "auto.select_world_type_menu.select_world_type_menu.1")))));
        this.activity = activity;
        this.level = activity.getLevel();
        this.redraw();
    }

    @NonNull
    private static Component line(@NonNull String legacy) {
        return PbText.of(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Багровый нилий появился только в 1.16, поэтому материал берётся по имени:
     * на более старом сервере меню просто покажет незернистую замену вместо падения.
     */
    @NonNull
    private static Material material(@NonNull String name, @NonNull Material fallback) {
        Material material = Material.getMaterial(name);
        return material == null ? fallback : material;
    }

    @NonNull
    public static Material getIcon(@NonNull World.Environment environment) {
        switch (environment) {
            case NETHER:
                return material("CRIMSON_NYLIUM", Material.NETHERRACK);
            case THE_END:
                return material("END_STONE", Material.STONE);
            default:
                return material("GRASS_BLOCK", Material.DIRT);
        }
    }

    private void redraw() {
        World.Environment current = this.level.getLevelSettings().getWorldSettings().getEnvironment();

        // Раскладка как в меню создания уровня (фон menu_worldtype в РП):
        //   МИР     [обычный][ад][край]
        //   РАЗМЕР  [1 чанк][4 чанка]
        //   [<-]
        this.setEnvironmentItem(12, World.Environment.NORMAL, current);
        this.setEnvironmentItem(13, World.Environment.NETHER, current);
        this.setEnvironmentItem(14, World.Environment.THE_END, current);

        this.setWidthItem(30, LevelWidthSwitcher.NARROW_CHUNKS);
        this.setWidthItem(31, LevelWidthSwitcher.WIDE_CHUNKS);

        this.setItem(
            45,
            ItemUtils.modifyMeta(ru.sortix.parkourbeat.ui.Icons.Button.ARROW_LEFT.item(ru.sortix.parkourbeat.ui.Icons.ButtonState.NORMAL),
                meta -> meta.displayName(line("&6Назад в настройки"))),
            event -> new EditorMainMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    /**
     * Ширина площадки. Стоит рядом со сменой мира, потому что действие такое же:
     * уровень ненадолго закрывается и открывается заново.
     */
    private void setWidthItem(int slot, int target) {
        int current = this.level.getLevelSettings().getGameSettings().getChunkWidth()
            >= LevelWidthSwitcher.WIDE_CHUNKS
            ? LevelWidthSwitcher.WIDE_CHUNKS : LevelWidthSwitcher.NARROW_CHUNKS;
        boolean isCurrent = target == current;
        boolean isPending = this.pendingWidth != null && this.pendingWidth == target;
        ru.sortix.parkourbeat.ui.Icons.Button button = target == LevelWidthSwitcher.WIDE_CHUNKS
            ? ru.sortix.parkourbeat.ui.Icons.Button.SIZE_4 : ru.sortix.parkourbeat.ui.Icons.Button.SIZE_1;
        this.setItem(
            slot,
            ItemUtils.modifyMeta(button.item(isCurrent
                ? ru.sortix.parkourbeat.ui.Icons.ButtonState.SELECTED : ru.sortix.parkourbeat.ui.Icons.ButtonState.NORMAL), meta -> {
                meta.displayName(line((isPending ? Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.1") : "&6")
                    + Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.2") + LevelWidthSwitcher.getDisplayName(target)));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.3") + LevelWidthSwitcher.getDisplayName(current)));
                if (!isCurrent) {
                    lore.add(Component.empty());
                    if (isPending) {
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.4")));
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.5")));
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.6")));
                    } else if (target == LevelWidthSwitcher.WIDE_CHUNKS) {
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.7")));
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.8")));
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.9")));
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.10")));
                    } else {
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.11")));
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.12")));
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.13")));
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.14")));
                    }
                    lore.add(Component.empty());
                    lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_width_item.15")));
                }
                meta.lore(lore);
                if (isPending) glint(meta);
            }),
            event -> {
                if (isCurrent) {
                    Player player = event.getPlayer();
                    ru.sortix.parkourbeat.ui.UiSounds.deny(player);
                    return;
                }
                if (!isPending) {
                    this.pendingWidth = target;
                    this.pending = null;
                    ru.sortix.parkourbeat.ui.UiSounds.toggle(event.getPlayer());
                    this.redraw();
                    return;
                }
                this.pendingWidth = null;
                event.getPlayer().closeInventory();
                LevelWidthSwitcher.switchWidth(this.plugin, this.level, target, event.getPlayer());
            });
    }

    /** Мерцание «ждёт подтверждения» (блеск без строчки зачарования). */
    private static void glint(@NonNull org.bukkit.inventory.meta.ItemMeta meta) {
        try {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        } catch (Exception ignored) {
        }
    }

    private void setEnvironmentItem(int slot,
                                    @NonNull World.Environment environment,
                                    @NonNull World.Environment current
    ) {
        boolean isCurrent = environment == current;
        boolean isPending = this.pending == environment;

        ru.sortix.parkourbeat.ui.Icons.Button button = environment == World.Environment.NETHER
            ? ru.sortix.parkourbeat.ui.Icons.Button.WORLD_NETHER
            : environment == World.Environment.THE_END
            ? ru.sortix.parkourbeat.ui.Icons.Button.WORLD_END : ru.sortix.parkourbeat.ui.Icons.Button.WORLD_NORMAL;
        this.setItem(
            slot,
            ItemUtils.modifyMeta(button.item(isCurrent
                ? ru.sortix.parkourbeat.ui.Icons.ButtonState.SELECTED : ru.sortix.parkourbeat.ui.Icons.ButtonState.NORMAL), meta -> {
                meta.displayName(line((isPending ? Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.1") : "&6")
                    + WorldTypeSwitcher.getDisplayName(environment)));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());

                if (isCurrent) {
                    lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.2")));
                } else if (isPending) {
                    lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.3")));
                    lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.4")));
                    lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.5")));
                } else {
                    lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.6")));
                    lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.7")));
                    lore.add(Component.empty());
                    if (environment == World.Environment.NORMAL) {
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.8")));
                    } else {
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.9")));
                        lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.10")));
                    }
                    lore.add(Component.empty());
                    lore.add(line(Lang.raw(this.lang, "auto.select_world_type_menu.set_environment_item.11")));
                }

                meta.lore(lore);

                if (isPending) glint(meta);
            }),
            isCurrent ? this::alreadyCurrent : event -> this.select(event, environment));
    }

    private void alreadyCurrent(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(line(Lang.raw(PlayerLang.of(player), "auto.select_world_type_menu.already_current.1")));
        ru.sortix.parkourbeat.ui.UiSounds.deny(player);
    }

    private void select(@NonNull ClickEvent event, @NonNull World.Environment environment) {
        Player player = event.getPlayer();

        if (this.pending != environment) {
            this.pending = environment;
            this.redraw();
            ru.sortix.parkourbeat.ui.UiSounds.toggle(player);
            player.sendMessage(line(Lang.raw(PlayerLang.of(player), "auto.select_world_type_menu.select.1")
                + WorldTypeSwitcher.getDisplayName(environment)));
            return;
        }

        this.pending = null;
        player.closeInventory();

        if (this.activity.isTesting()) {
            player.sendMessage(line(Lang.raw(PlayerLang.of(player), "auto.select_world_type_menu.select.2")));
            return;
        }

        WorldTypeSwitcher.switchEnvironment(this.plugin, this.level, environment, player);
    }
}
