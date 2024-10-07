package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class CreateLevelMenu extends ParkourBeatInventory {
    // TODO Support for NETHER and THE_END environments
    public static final boolean DISPLAY_NON_DEFAULT_WORLD_TYPES = false;

    public CreateLevelMenu(@NonNull ParkourBeat plugin, String lang) {
        super(plugin, 3, lang, LangOptions.inventory_createlevel_title.getComponent(lang));
        if (DISPLAY_NON_DEFAULT_WORLD_TYPES) {
            this.setItem(
                2,
                3,
                ItemUtils.create(
                    Material.NETHERRACK, meta -> meta.displayName(LangOptions.inventory_createlevel_nether.getComponent(lang))),
                event -> this.createLevel(event.getPlayer(), World.Environment.NETHER));
        }
        this.setItem(
            2,
            5,
            ItemUtils.create(Material.GRASS_BLOCK, meta -> meta.displayName(LangOptions.inventory_createlevel_overworld.getComponent(lang))),
            event -> this.createLevel(event.getPlayer(), World.Environment.NORMAL));
        if (DISPLAY_NON_DEFAULT_WORLD_TYPES) {
            this.setItem(
                2,
                7,
                ItemUtils.create(
                    Material.END_STONE, meta -> meta.displayName(LangOptions.inventory_createlevel_theend.getComponent(lang))),
                event -> this.createLevel(event.getPlayer(), World.Environment.THE_END));
        }
        this.setItem(
            3,
            9,
            ItemUtils.create(Material.BARRIER, meta -> meta.displayName(LangOptions.inventory_createlevel_cancel.getComponent(lang))),
            event -> event.getPlayer().closeInventory());
    }

    @Override
    public void open(@NonNull Player player) {
        if (!player.hasPermission(PermissionConstants.CREATE_LEVEL)) {
        	LangOptions.inventory_createlevel_nopermission.sendMsg(player);
            return;
        }
        super.open(player);
    }

    private void createLevel(@NonNull Player owner, @NonNull World.Environment environment) {
        this.plugin
            .get(LevelsManager.class)
            .createLevel(environment, owner.getUniqueId(), owner.getName())
            .thenAccept(level -> {
                if (level == null) {

                	LangOptions.inventory_createlevel_create_fail.sendMsg(owner);
                    return;
                }
                EditActivity.createAsync(this.plugin, owner, level).thenAccept(editActivity -> {
                    if (editActivity == null) {
                    	LangOptions.inventory_createlevel_create_edit_unavilable.sendMsg(owner, new Placeholders("%level%", ((TextComponent)level.getDisplayName()).content()));
                        return;
                    }
                    this.plugin.get(ActivityManager.class).switchActivity(owner, editActivity, level.getSpawn()).thenAccept(success -> {
                    	owner.sendMessage((success ? LangOptions.inventory_createlevel_create_edit_start : LangOptions.inventory_createlevel_create_edit_fail).getComponent(lang, new Placeholders("%level%", ((TextComponent)level.getDisplayName()).content())));
                    });
                });
            });
    }
}
