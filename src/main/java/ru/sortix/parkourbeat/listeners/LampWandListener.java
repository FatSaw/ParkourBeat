package ru.sortix.parkourbeat.listeners;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Палочка для выделения области ламп: два угла, как в любом редакторе регионов. */
public class LampWandListener implements Listener {

    private static final String WAND_NAME = "§bПалочка ламп";
    private static final Map<UUID, int[]> FIRST = new HashMap<>();
    private static final Map<UUID, int[]> SECOND = new HashMap<>();
    /** Стена, которую строитель сейчас правит: выделение и рисование уходят прямо в неё. */
    private static final Map<UUID, ru.sortix.parkourbeat.levels.lamps.LampWall> EDITING = new HashMap<>();
    private static final java.util.Set<UUID> PAINTING = new java.util.HashSet<>();

    private final @NonNull ParkourBeat plugin;

    public LampWandListener(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
    }

    @NonNull
    public static ItemStack createWand() {
        return ItemUtils.create(Material.STICK, meta -> {
            meta.displayName(PbText.of(WAND_NAME).decoration(TextDecoration.ITALIC, false));
            java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
            lore.add(PbText.of("&7Если первый угол, то ЛКМ, если второй, то ПКМ")
                .decoration(TextDecoration.ITALIC, false));
            lore.add(PbText.of("&7Если сразу всю стену, то Shift и любой клик по лампе")
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
    }

    private static boolean isWand(@Nullable ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) return false;
        return item.getItemMeta().hasDisplayName();
    }

    public static void setEditing(@NonNull Player player,
                                  @Nullable ru.sortix.parkourbeat.levels.lamps.LampWall wall) {
        if (wall == null) EDITING.remove(player.getUniqueId());
        else EDITING.put(player.getUniqueId(), wall);
    }

    public static boolean togglePaintMode(@NonNull Player player) {
        UUID id = player.getUniqueId();
        if (PAINTING.remove(id)) return false;
        PAINTING.add(id);
        return true;
    }

    public static boolean isPainting(@NonNull Player player) {
        return PAINTING.contains(player.getUniqueId());
    }

    @Nullable
    public static int[] selection(@NonNull Player player) {
        int[] first = FIRST.get(player.getUniqueId());
        int[] second = SECOND.get(player.getUniqueId());
        if (first == null || second == null) return null;
        return new int[] { first[0], first[1], first[2], second[0], second[1], second[2] };
    }

    @EventHandler
    public void onInteract(@NonNull PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isWand(event.getItem())) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        ru.sortix.parkourbeat.levels.lamps.LampWall wall = EDITING.get(player.getUniqueId());
        boolean left = action == Action.LEFT_CLICK_BLOCK;

        // Режим рисования: тыкаем прямо по лампам в стене, узор пишется сразу
        if (PAINTING.contains(player.getUniqueId()) && wall != null) {
            if (!ru.sortix.parkourbeat.levels.lamps.LampEngine.isLamp(block)) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.1")));
                return;
            }
            boolean changed = ru.sortix.parkourbeat.levels.lamps.LampEngine
                .paint(wall, block.getX(), block.getY(), block.getZ(), left);
            if (!changed) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.2")));
                return;
            }
            // Сразу показываем результат в мире, чтобы рисовать было видно
            ru.sortix.parkourbeat.levels.lamps.LampEngine.showPattern(block.getWorld(), wall);
            return;
        }

        // Умное выделение: тычок с Shift берёт всю постройку из ламп целиком
        if (player.isSneaking() && ru.sortix.parkourbeat.levels.lamps.LampEngine.isLamp(block)) {
            int[] box = ru.sortix.parkourbeat.levels.lamps.LampEngine
                .detectRegion(block.getWorld(), block, 8000);
            FIRST.put(player.getUniqueId(), new int[] { box[0], box[1], box[2] });
            SECOND.put(player.getUniqueId(), new int[] { box[3], box[4], box[5] });

            if (wall != null) {
                wall.setCorners(box[0], box[1], box[2], box[3], box[4], box[5]);
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.3")
                    + wall.getColumns() + Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.4") + wall.getRows() + Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.5")
                    + ru.sortix.parkourbeat.levels.lamps.LampEngine.countLamps(block.getWorld(), wall)));
            } else {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.6")));
            }
            return;
        }

        int[] point = { block.getX(), block.getY(), block.getZ() };
        (left ? FIRST : SECOND).put(player.getUniqueId(), point);

        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.7") + (left ? Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.8") : Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.9")) + Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.10")
            + point[0] + " " + point[1] + " " + point[2]));

        int[] selection = selection(player);
        if (selection == null) return;

        // Применяем сразу: отдельная кнопка «применить» только сбивала с толку
        if (wall != null) {
            wall.setCorners(selection[0], selection[1], selection[2],
                selection[3], selection[4], selection[5]);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.11")
                + wall.getColumns() + Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.12") + wall.getRows()
                + Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.13")
                + ru.sortix.parkourbeat.levels.lamps.LampEngine.countLamps(block.getWorld(), wall)));
        } else {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.lamp_wand_listener.on_interact.14")));
        }
    }
}
