package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.player.AfkManager;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.ui.Glyphs;
import ru.sortix.parkourbeat.ui.Icons;
import ru.sortix.parkourbeat.ui.Icons.Button;
import ru.sortix.parkourbeat.ui.Icons.ButtonState;
import ru.sortix.parkourbeat.utils.lang.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * AFK.
 * <pre>
 *   [        ☾  РЕЖИМ AFK        ]   9x2 - войти / выйти
 *   - - - - - - - - - - - - - - -
 *   АВТО-AFK   [выкл][5][15][30]     - число на часах = минуты
 *   [<-]                     [i]
 * </pre>
 */
public class AfkMenu extends ParkourBeatInventory {
    private static final int[] HERO_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};

    public AfkMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 6, lang, Glyphs.menuTitle(Glyphs.menuAfk(lang),
            Icons.Icon.CLOCK.component().append(Component.text(" ")).append(Lang.item(lang, "inventory.afk.title"))));
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        AfkManager afk = this.plugin.get(AfkManager.class);
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);
        boolean isAfk = afk.isAfk(viewer.getUniqueId());
        boolean pending = afk.isPending(viewer.getUniqueId());

        ItemStack hero = Glyphs.withHover(ItemUtils.modifyMeta(Icons.empty(), meta -> {
            meta.displayName(Lang.item(this.lang, isAfk ? "inventory.afk.toggle.name_leave" : "inventory.afk.toggle.name_enter"));
            List<Component> lore = new ArrayList<>(Lang.lore(this.lang, isAfk
                ? "inventory.afk.toggle.lore_leave" : "inventory.afk.toggle.lore_enter"));
            lore.add(Lang.item(this.lang, pending ? "inventory.afk.toggle.pending" : "inventory.afk.toggle.click"));
            meta.lore(lore);
        }), 0, 0, 8, 1);
        for (int slot : HERO_SLOTS) {
            this.setItem(slot, hero, event -> {
                Player player = event.getPlayer();
                afk.requestToggle(player, !isAfk);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                player.sendMessage(Lang.text(this.lang, "inventory.afk.toggle.notice"));
                player.closeInventory();
            });
        }

        int current = settings.getAutoAfkMinutes(viewer.getUniqueId());
        for (int i = 0; i < AfkManager.AUTO_AFK_MINUTES.length && i < 4; i++) {
            int minutes = AfkManager.AUTO_AFK_MINUTES[i];
            boolean selected = minutes == current;
            ItemStack button = ItemUtils.modifyMeta((minutes == 0 ? Button.OFF : Button.SORT_NEW)
                .item(selected ? ButtonState.SELECTED : ButtonState.NORMAL), meta -> {
                String prefixKey = selected ? "selected_" : "";
                meta.displayName(Lang.item(this.lang, minutes == 0
                        ? "inventory.afk.auto." + prefixKey + "name_off"
                        : "inventory.afk.auto." + prefixKey + "name",
                    "%minutes%", String.valueOf(minutes)));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, minutes == 0 ? "inventory.afk.auto.lore_off" : "inventory.afk.auto.lore",
                    "%minutes%", String.valueOf(minutes)));
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, selected ? "inventory.common.selected" : "inventory.common.select"));
                meta.lore(lore);
            });
            if (minutes > 0) button.setAmount(Math.min(64, minutes));
            this.setItem(3 * 9 + 4 + i, button, event -> {
                Player player = event.getPlayer();
                settings.setAutoAfkMinutes(player.getUniqueId(), minutes);
                settings.save();
                ru.sortix.parkourbeat.ui.UiSounds.click(player);
                new AfkMenu(this.plugin, this.lang, player).open(player);
            });
        }

        this.setItem(5 * 9 + 8, ItemUtils.modifyMeta(Button.MM_HOWTO.item(ButtonState.NORMAL), meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.afk.header.name"));
            meta.lore(Lang.lore(this.lang, "inventory.afk.header.lore",
                "%current%", current == 0
                    ? Lang.raw(this.lang, "inventory.afk.header.off")
                    : Lang.raw(this.lang, "inventory.afk.header.minutes", "%minutes%", String.valueOf(current))));
        }), null);

        this.setItem(5 * 9, ItemUtils.modifyMeta(Button.ARROW_LEFT.item(ButtonState.NORMAL), meta ->
            meta.displayName(Lang.item(this.lang, "inventory.common.back"))
        ), event -> new ServerMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));
    }
}
