package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.player.PlayerSettingsManager;
import ru.sortix.parkourbeat.ui.Glyphs;
import ru.sortix.parkourbeat.ui.Icons;
import ru.sortix.parkourbeat.ui.Icons.Button;
import ru.sortix.parkourbeat.ui.Icons.ButtonState;
import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ЯЗЫК - по карточке на язык: флаг слева, название на самой карточке, отметка справа.
 * <p>
 * Названия русского, английского и украинского нарисованы в РП и кладутся прямо в
 * заголовок окна на свою карточку (см. {@link Glyphs#menuTitle(char, Component, List)}).
 * У других языков название видно в подсказке.
 */
public class LanguageMenu extends ParkourBeatInventory {
    /** Начало кода языка -> какая нарисованная подпись (ru_ru, ru, ru-RU - всё это русский). */
    private static final String[] LABEL_LOCALES = {"ru", "en", "uk"};
    private static final int[] LABEL_WIDTHS = {43, 41, 59};
    private static final int MAX_ROWS = 4;

    public LanguageMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Player viewer) {
        super(plugin, 6, lang, title(lang));
        this.render(viewer);
    }

    @NonNull
    private static Component title(String lang) {
        List<Glyphs.TitleLabel> labels = new ArrayList<>();
        List<String> locales = PlayerLang.availableLocales();
        for (int row = 0; row < locales.size() && row < MAX_ROWS; row++) {
            int i = labelIndex(locales.get(row));
            if (i >= 0) labels.add(new Glyphs.TitleLabel((char) (0xE550 + i * 4 + row), 30, LABEL_WIDTHS[i]));
        }
        return Glyphs.menuTitle(Glyphs.menuLanguage(lang),
            Icons.Icon.GLOBE.component().append(Component.text(" ")).append(Lang.item(lang, "inventory.language.title")),
            labels);
    }

    /**
     * Какая нарисованная подпись подходит языку. Смотрим и на код, и на название из
     * lang.yml: английский у тебя - секция «default», по коду его не узнать.
     */
    private static int labelIndex(@NonNull String locale) {
        String code = locale.toLowerCase(java.util.Locale.ROOT);
        String name = PlayerLang.displayName(locale).toLowerCase(java.util.Locale.ROOT)
            .replaceAll("(?i)[&\u00A7][0-9a-fk-or]", "");
        if (code.startsWith("ru") || name.contains("\u0440\u0443\u0441") || name.contains("russ")) return 0;
        if (code.startsWith("uk") || name.contains("\u0443\u043A\u0440") || name.contains("ukr")) return 2;
        if (code.startsWith("en") || name.contains("engl") || name.contains("\u0430\u043D\u0433\u043B")) return 1;
        return -1;
    }

    private void render(@NonNull Player viewer) {
        this.clearInventory();
        String active = PlayerLang.effectiveLocale(viewer);
        List<String> locales = PlayerLang.availableLocales();
        for (int row = 0; row < locales.size() && row < MAX_ROWS; row++) {
            String locale = locales.get(row);
            boolean selected = locale.equals(active);
            String name = PlayerLang.displayName(locale);
            Consumer<ItemMeta> describe = meta -> {
                meta.displayName(Lang.item(this.lang, "inventory.language.entry.name", "%language%", name));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Lang.item(locale, "language.description"));
                lore.add(Component.empty());
                lore.add(Lang.item(this.lang, selected ? "inventory.language.selected" : "inventory.language.click"));
                meta.lore(lore);
            };
            Consumer<ru.sortix.parkourbeat.inventory.event.ClickEvent> action = event -> this.choose(event.getPlayer(), locale);
            int r = row;
            ItemStack flag = Glyphs.withHover(ItemUtils.modifyMeta(PlayerLang.displayIcon(locale), describe), 0, r, 8, r);
            ItemStack body = Glyphs.withHover(ItemUtils.modifyMeta(Icons.empty(), describe), 0, r, 8, r);
            ItemStack state = Glyphs.withHover(ItemUtils.modifyMeta(
                (selected ? Button.SWITCH_ON : Button.SWITCH_OFF).item(ButtonState.NORMAL), describe), 0, r, 8, r);
            this.setItem(r * 9, flag, action);
            for (int c = 1; c < 8; c++) this.setItem(r * 9 + c, body, action);
            this.setItem(r * 9 + 8, state, action);
        }

        this.setItem(5 * 9, ItemUtils.modifyMeta(Button.ARROW_LEFT.item(ButtonState.NORMAL), meta ->
            meta.displayName(Lang.item(this.lang, "inventory.common.back"))
        ), event -> new ServerMenu(this.plugin, PlayerLang.of(event.getPlayer()), event.getPlayer())
            .open(event.getPlayer()));
    }

    private void choose(@NonNull Player player, @NonNull String locale) {
        PlayerSettingsManager settings = this.plugin.get(PlayerSettingsManager.class);
        settings.setLanguage(player.getUniqueId(), locale);
        settings.save();
        ru.sortix.parkourbeat.ui.UiSounds.toggle(player);
        String updated = PlayerLang.of(player);
        player.sendMessage(Lang.text(updated, "inventory.language.changed",
            "%language%", PlayerLang.displayName(locale)));
        new LanguageMenu(this.plugin, updated, player).open(player);
        this.plugin.get(ru.sortix.parkourbeat.inventory.LobbyItems.class).refresh(player);
    }
}
