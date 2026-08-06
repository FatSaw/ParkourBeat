package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.inventory.Heads;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class LevelDifficultyMenu extends ParkourBeatInventory {

    public LevelDifficultyMenu(@NonNull ParkourBeat plugin, String lang, @NonNull GameSettings settings, @NonNull Player player) {
        super(plugin, 3, lang, LangOptions.inventory_levelrate_title.getComponent(lang));

        LevelDifficulty[] difficulties = {LevelDifficulty.EASY, LevelDifficulty.HARD, LevelDifficulty.EXPERT, LevelDifficulty.EXPERT_PLUS};
        int[] slots = {2, 4, 6, 8};

        for (int i = 0; i < difficulties.length; i++) {
            LevelDifficulty diff = difficulties[i];
            this.setItem(1, slots[i], ItemUtils.modifyMeta(Heads.getHeadByTextureData(diff.getHeadBase64(), true), meta -> {
                meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(diff.getDisplayName()));
                if (settings.getDifficulty() == diff || settings.getPlayerRatings().get(player.getUniqueId()) == diff) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
            }), event -> {
                if (player.hasPermission(PermissionConstants.MODERATE_LEVELS)) {
                    settings.setDifficulty(diff);

                    // Если карта была на модерации, сразу одобряем её!
                    if (settings.getModerationStatus() == ModerationStatus.ON_MODERATION) {
                        settings.setModerationStatus(ModerationStatus.MODERATED);
                    }

                    plugin.get(LevelsManager.class).saveGameSettings(settings);
                    LangOptions.inventory_levelrate_success_mod.sendMsg(player, new Placeholders("%difficulty%", diff.getDisplayName()));
                } else {
                    settings.setPlayerRating(player.getUniqueId(), diff);
                    plugin.get(LevelsManager.class).saveGameSettings(settings);
                    LangOptions.inventory_levelrate_success_player.sendMsg(player);
                    LangOptions.inventory_levelrate_error_player.sendMsg(player);
                }
                new LevelDetailsMenu(plugin, lang, settings, player).open(player);
            });
        }

        if (player.hasPermission(PermissionConstants.MODERATE_LEVELS)) {
            this.setItem(3, 5, ItemUtils.modifyMeta(Heads.getHeadByTextureData(LevelDifficulty.N_A.getHeadBase64(), true), meta -> {
                meta.displayName(LangOptions.inventory_levelrate_reset.getComponent(lang));
            }), event -> {
                settings.setDifficulty(LevelDifficulty.N_A);
                plugin.get(LevelsManager.class).saveGameSettings(settings);
                new LevelDetailsMenu(plugin, lang, settings, player).open(player);
            });
        }

        this.setItem(3, 1, ItemUtils.modifyMeta(UIHeads.ARROW_LEFT.clone(), meta -> {
            meta.displayName(LangOptions.inventory_regularitems_previous.getComponent(lang));
        }), event -> {
            new LevelDetailsMenu(plugin, lang, settings, player).open(player);
        });
    }
}
