package ru.sortix.parkourbeat.ui;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Пиксельные иконки из ресурспака.
 * <p>
 * {@link Icon} - символы 8px для чата, лора, экшнбара и табло. В lang.yml их
 * можно писать тегом {@code <i:rmb>}, {@code <i:checkpoint>} и т.д. - тег
 * понимает PbText, иконка всегда выходит в своих цветах.
 * <p>
 * {@link Button} - кнопки меню (предметы: бумага с CustomModelData), у каждой
 * три состояния: обычная, выбранная и недоступная.
 */
public final class Icons {
    private Icons() {
    }

    /** Порядок = порядок в РП (font/pb/icons.png), менять нельзя - только дописывать в конец. */
    public enum Icon {
        LMB, RMB, MMB, MOUSE, CHECK, CROSS, CHECKPOINT, FRIEND, USER, STAR, CROWN, CLOCK, HEART, SKULL,
        LOCK, UNLOCK, MUSIC, PLAY, PAUSE, ARROW_RIGHT, ARROW_LEFT, GEAR, SEARCH, INFO, WARN, TROPHY, BOOK,
        PENCIL, TRASH, EYE, GLOBE, PLUS, COIN, GEM, BOLT, FLAME, CHAT, LEAVE, MAP, CUBE,
        STAR_HALF, STAR_EMPTY, PING, SEND, TARGET,
        REC;

        public char ch() {
            return (char) (0xE400 + this.ordinal());
        }

        /** Иконка как строка для склейки с текстом (в lang-строках лучше тег {@code <i:name>}). */
        @NonNull
        public String str() {
            return String.valueOf(this.ch());
        }

        @NonNull
        public Component component() {
            return Glyphs.glyph(this.ch());
        }

        @Nullable
        public static Icon byName(@Nullable String name) {
            if (name == null) return null;
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public enum ButtonState {
        NORMAL, SELECTED, DISABLED
    }

    /** Порядок = порядок в РП (gui_build.py, BUTTON_ICONS). */
    public enum Button {
        MODE_3D, MODE_2D, MODE_DUEL, MODE_360, MODE_DIGGER,
        SIZE_1, SIZE_2, SIZE_3, SIZE_4,
        WORLD_NORMAL, WORLD_NETHER, WORLD_END,
        SKY_FULL, SKY_HALF,
        STAR,
        DIFF_NA, DIFF_EASY, DIFF_NORMAL, DIFF_HARD, DIFF_EXPERT, DIFF_EXPERT_PLUS,
        SORT_ASC, SORT_DESC, SORT_POPULAR, SORT_NEW,
        ARROW_LEFT, ARROW_RIGHT, PAGE, PENCIL, PLUS, BOOK, WARN,
        MM_MYSTATS, MM_GLOBALSTATS, MM_REPLAYS, MM_FRIENDS, MM_MODIFIERS, MM_RATING,
        MM_SETTINGS, MM_LANGUAGE, MM_MUSIC, MM_TUTORIAL, MM_HOWTO, MM_COMMANDS, MM_AFK,
        MOD_PRACTICE, MOD_PERFECT, MOD_HIDDEN, MOD_SUDDEN_DEATH, MOD_HARD_ROCK, TRASH, USER,
        SWITCH_ON, SWITCH_OFF, ACCESS_ALL, ACCESS_FRIENDS, ACCESS_NOBODY, OFF, MOON;

        /** Кнопка модификатора (порядок совпадает с Modifier). */
        @NonNull
        public static Button modifier(@NonNull ru.sortix.parkourbeat.rating.Modifier modifier) {
            return values()[MOD_PRACTICE.ordinal() + Math.min(4, modifier.ordinal())];
        }

        /** Кнопка-кубик сложности (порядок совпадает с LevelDifficulty). */
        @NonNull
        public static Button difficulty(@NonNull ru.sortix.parkourbeat.levels.LevelDifficulty difficulty) {
            return values()[DIFF_NA.ordinal() + difficulty.ordinal()];
        }

        public int modelData(@NonNull ButtonState state) {
            return 7200 + this.ordinal() * 3 + state.ordinal();
        }

        @NonNull
        public ItemStack item(@NonNull ButtonState state) {
            return paper(this.modelData(state));
        }
    }

    /** Красная кнопка с крестиком. */
    public static final int CANCEL_MODEL = 7390;
    /** Полностью прозрачный предмет: кнопка нарисована на фоне меню, а это - её «кликабельная» часть. */
    public static final int EMPTY_MODEL = 7391;

    /** Предметы хотбара лобби: плоские иконки в стиле сервера (видны в руке целиком). */
    public enum Hotbar {
        STATS, MENU, PLAY, RECORDS, MODIFIERS;

        @NonNull
        public ItemStack item() {
            return paper(7400 + this.ordinal());
        }
    }

    /** Кнопки меню редактора (CustomModelData 8000 + i*3 + состояние), порядок - как в РП. */
    private static final String[] EDITOR_ICONS = {
        "particles", "jumpcolor", "song", "lightshow", "textures", "glow",
        "start", "spawn", "markers", "checkpoints", "fallzones", "resetpoints",
        "borderpush", "difficulty", "sprint", "infinite", "particledist", "bossbar",
        "privacy", "coeditors", "worldtype", "copy", "preview", "exit", "delete", "digger", "twod", "lyrics"};

    @NonNull
    public static ItemStack editor(@NonNull String icon, @NonNull ButtonState state) {
        for (int i = 0; i < EDITOR_ICONS.length; i++) {
            if (EDITOR_ICONS[i].equals(icon)) return paper(8000 + i * 3 + state.ordinal());
        }
        return paper(8000);
    }

    /** Шлем-кубик сложности для NPC уровней (CustomModelData 7600 + порядок сложности). */
    @NonNull
    public static ItemStack helmet(@NonNull ru.sortix.parkourbeat.levels.LevelDifficulty difficulty) {
        return paper(7600 + difficulty.ordinal());
    }

    @NonNull
    public static ItemStack cancel() {
        return paper(CANCEL_MODEL);
    }

    @NonNull
    public static ItemStack empty() {
        return paper(EMPTY_MODEL);
    }

    @NonNull
    private static ItemStack paper(int modelData) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(modelData);
        item.setItemMeta(meta);
        return item;
    }
}
