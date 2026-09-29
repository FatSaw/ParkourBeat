package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import java.util.ArrayList;
import java.util.List;

/**
 * Выбор языка интерфейса.
 * <p>
 * Список языков не зашит в коде: он строится из секций {@code localisation} в
 * {@code lang.yml}, а название и голова-флаг каждого языка берутся из его же секции
 * ({@code language.name}, {@code language.icon}). Чтобы добавить язык, достаточно
 * дописать секцию в файл - меню подхватит её само.
 */
public class LanguageMenu extends ParkourBeatInventory {
    /**
     * Слоты под языки: середина меню, по семь в ряд. Больше четырнадцати языков в
     * список не влезет - тогда пункты просто перестанут добавляться, а не наложатся
     * на рамку или кнопку «Назад».
     */
    private static final int[] SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
    };

    public LanguageMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 4, lang, Lang.item(lang, "inventory.language.title"));
        this.render(viewer);
    }

    private void render(@NonNull Player viewer) {
        this.clearInventory();
        this.fillBorder();

        // Пока игрок ничего не выбирал, отмечаем тот язык, на котором он и так видит
        // интерфейс: иначе меню выглядело бы так, будто язык не выбран вообще.
        String active = PlayerLang.effectiveLocale(viewer);

        int index = 0;
        for (String locale : PlayerLang.availableLocales()) {
            if (index >= SLOTS.length) break;

            boolean selected = locale.equals(active);
            String name = PlayerLang.displayName(locale);

            this.setItem(SLOTS[index], ItemUtils.modifyMeta(PlayerLang.displayIcon(locale), meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.language.entry.name",
                    "%language%", name));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                // Описание берём из самого языка, а не из текущего: игрок должен
                // понять пункт даже если сейчас у него стоит язык, которого он не знает.
                lore.add(Lang.item(locale, "language.description"));
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, selected
                    ? "inventory.language.selected"
                    : "inventory.language.click"));
                meta.lore(lore);

                if (selected) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
            }), event -> this.choose(event.getPlayer(), locale));

            index++;
        }

        this.setItem(31, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(Lang.item(this.lang, "inventory.common.back"))
        ), event -> new ServerMenu(this.plugin, PlayerLang.of(event.getPlayer()), event.getPlayer())
            .open(event.getPlayer()));
    }

    private void choose(@NonNull Player player, @NonNull String locale) {
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);
        settings.setLanguage(player.getUniqueId(), locale);
        settings.save();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);

        String updated = PlayerLang.of(player);
        player.sendMessage(Lang.text(updated, "inventory.language.changed",
            "%language%", PlayerLang.displayName(locale)));

        // Заголовок инвентаря в Minecraft не переписывается на лету, поэтому меню
        // пересоздаётся целиком - иначе шапка осталась бы на прежнем языке.
        new LanguageMenu(this.plugin, updated, player).open(player);

        // Скорборд и таб перерисовываются на своём такте и подхватят язык сами,
        // а вот предметы в руках лежат уже собранными - их надо выдать заново.
        this.plugin.get(ru.sortix.parkourbeat.inventory.LobbyItems.class).refresh(player);
    }
}
