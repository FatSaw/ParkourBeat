package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.rating.Modifier;
import ru.sortix.parkourbeat.rating.ModifierSet;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.utils.lang.Lang;

import java.util.ArrayList;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.PbText;
public class ModifiersMenu extends ParkourBeatInventory {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final int[] MODIFIER_SLOTS = {10, 11, 12, 13, 14};

    private final @NonNull Player player;

    public ModifiersMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player player) {
        super(plugin, 3, lang, Lang.item(lang, "inventory.modifiers.title"));
        this.player = player;
        this.render();
    }

    private void render() {
        this.clearInventory();
        this.drawBorders();

        ModifierSet selection = this.plugin.get(StatisticsManager.class)
            .getSelectedModifiers(this.player.getUniqueId());

        Modifier[] modifiers = Modifier.values();
        for (int i = 0; i < modifiers.length && i < MODIFIER_SLOTS.length; i++) {
            Modifier modifier = modifiers[i];
            boolean active = selection.isActive(modifier);
            this.setItem(MODIFIER_SLOTS[i], buildIcon(modifier, active), event -> {
                selection.toggle(modifier);
                this.player.playSound(this.player.getLocation(),
                    org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                this.render();
            });
        }

        this.setItem(16, ItemUtils.create(Material.BARRIER, meta -> {
            meta.displayName(Lang.item(this.lang, "inventory.modifiers.reset"));
        }), event -> {
            selection.clear();
            this.player.playSound(this.player.getLocation(),
                org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 0.8f);
            this.render();
        });
    }

    @NonNull
    private ItemStack buildIcon(@NonNull Modifier modifier, boolean active) {
        return ItemUtils.create(modifier.getIcon(), meta -> {
            meta.displayName(PbText.of(
                modifier.getColoredCode() + " &7- " + modifier.getColorPrefix()
                    + modifier.getDisplayName()));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            // Описание каждого модификатора - свой ключ: строк в нём разное количество,
            // и переводчику удобнее править их одним куском, а не списком.
            lore.addAll(Lang.lore(this.lang,
                "modifier." + modifier.name().toLowerCase(java.util.Locale.ROOT) + ".lore"));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.modifiers.multiplier",
                "%multiplier%", trimTrailingZero(modifier.getScoreMultiplier())));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, active
                ? "inventory.modifiers.enabled"
                : "inventory.modifiers.disabled"));
            meta.lore(lore);

            if (active) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
        });
    }

    @NonNull
    private static String trimTrailingZero(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        String formatted = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted;
    }

    private void drawBorders() {
        ItemStack glass = ItemUtils.create(Material.GRAY_STAINED_GLASS_PANE,
            meta -> meta.displayName(Component.empty()));
        for (int i = 0; i < 27; i++) {
            boolean isBorder = i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8;
            if (isBorder) this.setItem(i, glass, null);
        }
    }
}
