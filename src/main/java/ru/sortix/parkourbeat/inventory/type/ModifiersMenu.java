package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.rating.Modifier;
import ru.sortix.parkourbeat.rating.ModifierSet;
import ru.sortix.parkourbeat.rating.StatisticsManager;
import ru.sortix.parkourbeat.ui.Glyphs;
import ru.sortix.parkourbeat.ui.Icons;
import ru.sortix.parkourbeat.ui.Icons.Button;
import ru.sortix.parkourbeat.ui.Icons.ButtonState;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * МОДИФИКАТОРЫ.
 * <pre>
 *        [PC][PF][HD][SD][HR]      - выбранные подсвечены
 *   - - - - - - - - - - - - - -
 *   ИТОГО      [сводка]
 *   [<-]                    [сброс]
 * </pre>
 * Описания модификаторов - те же ключи lang.yml ({@code modifier.<name>.lore}), без изменений.
 */
public class ModifiersMenu extends ParkourBeatInventory {
    private static final int FIRST_SLOT = 9 + 2;
    private static final int SUMMARY_SLOT = 3 * 9 + 4;
    private static final int BACK_SLOT = 5 * 9;
    private static final int RESET_SLOT = 5 * 9 + 8;

    private final @NonNull Player player;

    public ModifiersMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player player) {
        super(plugin, 6, lang, Glyphs.menuTitle(Glyphs.menuModifiers(lang),
            Icons.Icon.GEAR.component().append(Component.text(" "))
                .append(Lang.item(lang, "inventory.modifiers.title"))));
        this.player = player;
        this.render();
    }

    private void render() {
        this.clearInventory();

        ModifierSet selection = this.plugin.get(StatisticsManager.class)
            .getSelectedModifiers(this.player.getUniqueId());

        Modifier[] modifiers = Modifier.values();
        for (int i = 0; i < modifiers.length && i < 5; i++) {
            Modifier modifier = modifiers[i];
            boolean active = selection.isActive(modifier);
            this.setItem(FIRST_SLOT + i, this.buildIcon(modifier, active), event -> {
                selection.toggle(modifier);
                ru.sortix.parkourbeat.ui.UiSounds.click(this.player);
                this.render();
            });
        }

        this.setItem(SUMMARY_SLOT, this.summary(selection), null);

        this.setItem(BACK_SLOT, ItemUtils.modifyMeta(Button.ARROW_LEFT.item(ButtonState.NORMAL), meta ->
                meta.displayName(Lang.item(this.lang, "inventory.common.back"))),
            event -> new ServerMenu(this.plugin, this.lang, event.getPlayer()).open(event.getPlayer()));

        this.setItem(RESET_SLOT, ItemUtils.modifyMeta(Button.TRASH.item(ButtonState.NORMAL), meta ->
            meta.displayName(Lang.item(this.lang, "inventory.modifiers.reset"))), event -> {
            selection.clear();
            ru.sortix.parkourbeat.ui.UiSounds.click(this.player);
            this.render();
        });
    }

    @NonNull
    private ItemStack buildIcon(@NonNull Modifier modifier, boolean active) {
        return ItemUtils.modifyMeta(Button.modifier(modifier).item(active ? ButtonState.SELECTED : ButtonState.NORMAL), meta -> {
            meta.displayName(PbText.of((active ? "<i:check> " : "")
                + modifier.getColoredCode() + " &7- " + modifier.getColorPrefix() + modifier.getDisplayName()));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
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
        });
    }

    /** Сводка: что включено и общий множитель очков. */
    @NonNull
    private ItemStack summary(@NonNull ModifierSet selection) {
        return ItemUtils.modifyMeta(Button.BOOK.item(ButtonState.NORMAL), meta -> {
            double total = 1.0D;
            List<Component> lore = new ArrayList<>();
            for (Modifier modifier : Modifier.values()) {
                if (!selection.isActive(modifier)) continue;
                total *= modifier.getScoreMultiplier();
                lore.add(PbText.of("<i:check> " + modifier.getColoredCode() + " &7" + modifier.getDisplayName()
                    + " &8x" + trimTrailingZero(modifier.getScoreMultiplier())));
            }
            if (lore.isEmpty()) lore.add(PbText.of("&8-"));
            lore.add(Component.empty());
            lore.add(Lang.item(this.lang, "inventory.modifiers.multiplier", "%multiplier%", trimTrailingZero(total)));
            meta.displayName(PbText.of("<i:info> &f" + Lang.raw(this.lang, "inventory.modifiers.title")));
            meta.lore(lore);
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
}
