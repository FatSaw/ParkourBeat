package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.Heads;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.*;

public class RatingFeedbackMenu extends PaginatedMenu<ParkourBeat, GameSettings> {
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public RatingFeedbackMenu(@NonNull ParkourBeat plugin, String lang) {
        super(plugin, 6, lang, LangOptions.inventory_feedback_title.getComponent(lang), CONTENT_SLOTS);
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<GameSettings> getAllItems() {
        List<GameSettings> settings = new ArrayList<>(this.plugin.get(LevelsManager.class).getAvailableLevelsSettings());
        settings.removeIf(gs -> !gs.isPublicVisible() || gs.getPlayerRatings().isEmpty());
        settings.sort((a, b) -> Integer.compare(b.getPlayerRatings().size(), a.getPlayerRatings().size()));
        return settings;
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull GameSettings settings) {
        return ItemUtils.modifyMeta(Heads.getHeadByTextureData(settings.getDifficulty().getHeadBase64(), true), meta -> {

            // ФИКС ЦВЕТА: Берем готовый Component, а не сырую строку
            meta.displayName(settings.getDisplayName());

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            Map<LevelDifficulty, Integer> votes = new HashMap<>();
            for (LevelDifficulty diff : settings.getPlayerRatings().values()) {
                votes.put(diff, votes.getOrDefault(diff, 0) + 1);
            }

            int total = settings.getPlayerRatings().size();
            votes.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                .forEach(e -> {
                    int percent = (int) Math.round((e.getValue() / (double) total) * 100);
                    lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(e.getKey().getDisplayName() + " §8- §e" + percent + "%"));
                });

            lore.add(net.kyori.adventure.text.Component.empty());
            lore.addAll(LangOptions.inventory_feedback_item_lore.getComponents(lang,
                new Placeholders("%votes%", String.valueOf(total)),
                new Placeholders("%difficulty%", settings.getDifficulty().getDisplayName())
            ));
            meta.lore(lore);
        });
    }

    @Override
    protected void onPageDisplayed() {
        ItemStack glass = ItemUtils.create(Material.BLACK_STAINED_GLASS_PANE, m -> m.displayName(net.kyori.adventure.text.Component.empty()));
        for (int i = 0; i < 54; i++) {
            boolean isContent = false;
            for (int slot : CONTENT_SLOTS) if (i == slot) { isContent = true; break; }
            if (!isContent) this.setItem(i, glass, null);
        }
        this.setPreviousPageItem(6, 4);
        this.setItem(6, 5, ItemUtils.create(Material.BARRIER, m -> m.displayName(LangOptions.inventory_regularitems_close.getComponent(lang))), e -> e.getPlayer().closeInventory());
        this.setNextPageItem(6, 6);

        this.setItem(45, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), m -> m.displayName(
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("§eНазад")
        )), e -> new LevelsListMenu(plugin, lang, LevelsListMenu.DisplayMode.MODERATION, e.getPlayer(), e.getPlayer().getUniqueId()).open(e.getPlayer()));
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull GameSettings settings) {
        new LevelDifficultyMenu(plugin, lang, settings, event.getPlayer()).open(event.getPlayer());
    }
}
