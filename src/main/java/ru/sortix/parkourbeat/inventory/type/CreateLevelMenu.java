package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class CreateLevelMenu extends ParkourBeatInventory {
    public static final boolean DISPLAY_NON_DEFAULT_WORLD_TYPES = true;
    private final String levelName;

    public CreateLevelMenu(@NonNull ParkourBeat plugin, String lang, @NonNull String levelName) {
        super(plugin, 3, lang, LangOptions.inventory_createlevel_title.getComponent(lang));
        this.levelName = levelName;

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

    public static void startCreating(@NonNull ParkourBeat plugin, @NonNull Player player, String lang) {
        if (!player.hasPermission(PermissionConstants.CREATE_LEVEL)) {
            LangOptions.inventory_createlevel_nopermission.sendMsg(player);
            return;
        }

        PlayersInputManager manager = plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorprivacy_rename_unavilable.getComponent(lang));
            return;
        }

        player.closeInventory();
        player.sendMessage(LangOptions.inventory_createlevel_request_name.getComponent(lang));

        manager.requestChatInput(player, 20 * 30).thenAccept(name -> {
            if (name == null) {
                player.sendMessage(LangOptions.inventory_createlevel_timeout.getComponent(lang));
                return;
            }

            if (name.length() < 3 || name.length() > 30) {
                player.sendMessage(LangOptions.inventory_createlevel_invalid_name.getComponent(lang));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                new CreateLevelMenu(plugin, lang, name).open(player);
            });
        });
    }

    private void createLevel(@NonNull Player owner, @NonNull World.Environment environment) {
        owner.closeInventory();

        String envName = switch (environment) {
            case NORMAL -> LangOptions.inventory_createlevel_dimension_overworld.get(lang);
            case NETHER -> LangOptions.inventory_createlevel_dimension_nether.get(lang);
            case THE_END -> LangOptions.inventory_createlevel_dimension_theend.get(lang);
            default -> "Unknown";
        };

        Component titleText = LegacyComponentSerializer.legacyAmpersand().deserialize(this.levelName);

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
            .createLevel(environment, owner.getUniqueId(), owner.getName(), this.levelName)
            .thenAccept(level -> {
                progressTask.cancel();

                if (level == null) {
                    owner.showTitle(Title.title(titleText, LangOptions.inventory_createlevel_create_fail.getComponent(lang), Title.Times.of(Duration.ZERO, Duration.ofMillis(3000), Duration.ofMillis(1000))));
                    return;
                }
                owner.showTitle(Title.title(titleText, LangOptions.inventory_createlevel_finished.getComponent(lang), Title.Times.of(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(1000))));

                EditActivity.createAsync(this.plugin, owner, level).thenAccept(editActivity -> {
                    if (editActivity == null) {
                        LangOptions.inventory_createlevel_create_edit_unavilable.sendMsg(owner, new Placeholders("%level%", ((TextComponent)level.getDisplayName()).content()));
                        return;
                    }
                    this.plugin.get(ActivityManager.class).switchActivity(owner, editActivity, level.getSpawn()).thenAccept(success -> {
                        if (!success) {
                            LangOptions.inventory_createlevel_create_edit_fail.sendMsg(owner, new Placeholders("%level%", ((TextComponent)level.getDisplayName()).content()));
                        }
                    });
                });
            });
    }
}
