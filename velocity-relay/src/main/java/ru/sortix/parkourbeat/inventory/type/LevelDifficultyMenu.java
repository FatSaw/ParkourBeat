package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.constant.PermissionConstants;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelDifficulty;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.ui.Glyphs;
import ru.sortix.parkourbeat.ui.Icons;
import ru.sortix.parkourbeat.ui.Icons.Button;
import ru.sortix.parkourbeat.ui.Icons.ButtonState;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ОЦЕНКА УРОВНЯ: звёзды качества (1-5) и голос за сложность.
 * <pre>
 *   КАЧЕСТВО   [★][★][★][★][★]
 *   СЛОЖНОСТЬ  [ЛЁГ][НОР][СЛО][ЭКС][ЭКС+]
 *                                  [N/A]   - только модераторам
 *   [<-]                           [итоги]
 * </pre>
 * Звёзды ставит любой игрок (одна оценка на игрока, можно поменять). Сложность:
 * модератор назначает её сразу (как раньше), игрок - голосует.
 */
public class LevelDifficultyMenu extends ParkourBeatInventory {
    private static final LevelDifficulty[] DIFFICULTIES = {LevelDifficulty.EASY, LevelDifficulty.NORMAL,
        LevelDifficulty.HARD, LevelDifficulty.EXPERT, LevelDifficulty.EXPERT_PLUS};
    private static final int FIRST_COLUMN = 4;

    private final @NonNull GameSettings settings;
    private final @NonNull Player player;
    private final boolean moderator;

    public LevelDifficultyMenu(@NonNull ParkourBeat plugin, String lang, @NonNull GameSettings settings, @NonNull Player player) {
        super(plugin, 6, lang, Glyphs.menuTitle(Glyphs.menuRate(lang),
            Icons.Icon.STAR.component().append(Component.text(" ")).append(settings.getDisplayName())));
        this.settings = settings;
        this.player = player;
        this.moderator = player.hasPermission(PermissionConstants.MODERATE_LEVELS);
        this.render();
    }

    private void render() {
        this.clearInventory();
        int myStars = this.settings.getStarRatings().getOrDefault(this.player.getUniqueId(), 0);

        for (int i = 1; i <= 5; i++) {
            int stars = i;
            ButtonState state = stars <= myStars ? ButtonState.SELECTED : ButtonState.NORMAL;
            this.setItem(FIRST_COLUMN - 1 + i, ItemUtils.modifyMeta(Button.STAR.item(state), meta -> {
                meta.displayName(PbText.item("&f" + Glyphs.stars(stars) + " &7" + stars + "/5"));
                meta.lore(List.of(
                    PbText.item(this.t("&7Насколько уровень хорош", "&7How good the level is", "&7Наскільки рівень гарний")),
                    Component.empty(),
                    PbText.item(stars == myStars
                        ? "<i:check> &a" + this.t("Ваша оценка", "Your rating", "Ваша оцінка")
                        : "<i:lmb> &7" + this.t("Поставить ", "Rate ", "Поставити ") + stars + " <i:star>")));
            }), event -> this.rateStars(stars));
        }

        LevelDifficulty myVote = this.settings.getPlayerRatings().get(this.player.getUniqueId());
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            LevelDifficulty diff = DIFFICULTIES[i];
            boolean current = this.settings.getDifficulty() == diff;
            boolean voted = myVote == diff;
            ButtonState state = (this.moderator ? current : voted) ? ButtonState.SELECTED : ButtonState.NORMAL;
            this.setItem(2 * 9 + FIRST_COLUMN + i, ItemUtils.modifyMeta(Button.difficulty(diff).item(state), meta -> {
                meta.displayName(Glyphs.glyph(Glyphs.banner(diff, this.lang)));
                List<Component> lore = new ArrayList<>();
                if (current) lore.add(PbText.item("<i:info> &7" + this.t("Сложность уровня сейчас", "Current level difficulty", "Поточна складність рівня")));
                if (voted) lore.add(PbText.item("<i:check> &a" + this.t("Ваш голос", "Your vote", "Ваш голос")));
                lore.add(PbText.item("<i:lmb> &7" + (this.moderator
                    ? this.t("Назначить сложность", "Set difficulty", "Призначити складність")
                    : this.t("Проголосовать", "Vote", "Проголосувати"))));
                meta.lore(lore);
            }), event -> this.rateDifficulty(diff));
        }

        if (this.moderator) {
            this.setItem(3 * 9 + 8, ItemUtils.modifyMeta(Button.DIFF_NA.item(
                this.settings.getDifficulty() == LevelDifficulty.N_A ? ButtonState.SELECTED : ButtonState.NORMAL), meta ->
                meta.displayName(LangOptions.inventory_levelrate_reset.getComponent(this.lang))), event -> {
                this.settings.setDifficulty(LevelDifficulty.N_A);
                this.plugin.get(LevelsManager.class).saveGameSettings(this.settings);
                this.click();
            });
        }

        this.setItem(5 * 9, ItemUtils.modifyMeta(Button.ARROW_LEFT.item(ButtonState.NORMAL), meta ->
                meta.displayName(LangOptions.inventory_regularitems_previous.getComponent(this.lang))),
            event -> new LevelDetailsMenu(this.plugin, this.lang, this.settings, this.player).open(this.player));

        this.setItem(5 * 9 + 8, this.summaryItem(), null);
    }

    @NonNull
    private ItemStack summaryItem() {
        Map<Integer, Integer> stars = new java.util.HashMap<>();
        for (int value : this.settings.getStarRatings().values()) stars.merge(value, 1, Integer::sum);
        Map<LevelDifficulty, Integer> votes = new EnumMap<>(LevelDifficulty.class);
        for (LevelDifficulty d : this.settings.getPlayerRatings().values()) votes.merge(d, 1, Integer::sum);

        return ItemUtils.modifyMeta(Button.BOOK.item(ButtonState.NORMAL), meta -> {
            meta.displayName(PbText.item("<i:info> &f" + this.t("Итоги", "Summary", "Підсумки")));
            List<Component> lore = new ArrayList<>();
            int count = this.settings.getStarRatings().size();
            lore.add(PbText.item("&f" + Glyphs.stars(this.settings.getAverageStars()) + " &7"
                + String.format(java.util.Locale.ROOT, "%.1f", this.settings.getAverageStars())
                + " &8(" + count + ")"));
            for (int i = 5; i >= 1; i--) {
                lore.add(PbText.item("&8" + i + " <i:star> &7" + stars.getOrDefault(i, 0)));
            }
            lore.add(Component.empty());
            for (LevelDifficulty d : DIFFICULTIES) {
                lore.add(Glyphs.glyph(Glyphs.banner(d, this.lang))
                    .append(PbText.item(" &7" + votes.getOrDefault(d, 0))));
            }
            meta.lore(lore);
        });
    }

    private void rateStars(int stars) {
        this.settings.setStarRating(this.player.getUniqueId(), stars);
        this.plugin.get(LevelsManager.class).saveGameSettings(this.settings);
        this.click();
    }

    private void rateDifficulty(@NonNull LevelDifficulty diff) {
        if (this.moderator) {
            this.settings.setDifficulty(diff);
            // Если карта была на модерации, сразу одобряем её!
            if (this.settings.getModerationStatus() == ModerationStatus.ON_MODERATION) {
                this.settings.setModerationStatus(ModerationStatus.MODERATED);
            }
            this.plugin.get(LevelsManager.class).saveGameSettings(this.settings);
            LangOptions.inventory_levelrate_success_mod.sendMsg(this.player, new Placeholders("%difficulty%", diff.getDisplayName()));
        } else {
            this.settings.setPlayerRating(this.player.getUniqueId(), diff);
            this.plugin.get(LevelsManager.class).saveGameSettings(this.settings);
            LangOptions.inventory_levelrate_success_player.sendMsg(this.player);
        }
        this.click();
    }

    private void click() {
        ru.sortix.parkourbeat.ui.UiSounds.click(this.player);
        this.render();
    }

    @NonNull
    private String t(@NonNull String ru, @NonNull String en, @NonNull String uk) {
        int set = Glyphs.localeSet(this.lang);
        return set == 1 ? en : set == 2 ? uk : ru;
    }
}
