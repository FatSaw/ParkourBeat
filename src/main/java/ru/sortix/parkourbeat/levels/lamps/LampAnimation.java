package ru.sortix.parkourbeat.levels.lamps;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Material;

/**
 * Готовые узоры для стен из ламп.
 * <p>
 * Каждая анимация решает про одну клетку: гореть ей или нет в этот момент.
 * Благодаря этому стена любого размера работает без пересчёта самой анимации.
 */
@Getter
public enum LampAnimation {

    WAVE(Material.LIGHT_BLUE_STAINED_GLASS) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            double head = phase * (cols + 3) - 1.5D;
            return Math.abs(col - head) < 1.5D;
        }
    },
    SWEEP(Material.PAPER) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            double filled = (phase < 0.5D ? phase * 2 : (1 - phase) * 2) * rows;
            return (rows - 1 - row) < filled;
        }
    },
    RIPPLE(Material.HEART_OF_THE_SEA) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            double dx = col - (cols - 1) / 2.0D, dy = row - (rows - 1) / 2.0D;
            double distance = Math.sqrt(dx * dx + dy * dy);
            return Math.abs((distance - phase * 12.0D) % 4.0D) < 1.2D;
        }
    },
    CHECKER(Material.BLACK_STAINED_GLASS) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            boolean even = ((col + row) & 1) == 0;
            return even == (((int) (phase * 8)) % 2 == 0);
        }
    },
    SNAKE(Material.LEAD) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            int total = cols * rows;
            int index = (row % 2 == 0) ? row * cols + col : row * cols + (cols - 1 - col);
            double head = phase * total;
            double delta = head - index;
            return delta >= 0 && delta < Math.max(3, total * 0.12D);
        }
    },
    RAIN(Material.WATER_BUCKET) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            double offset = ((col * 37) % 100) / 100.0D;
            double head = ((phase * 3 + offset) % 1.0D) * (rows + 2) - 1;
            return Math.abs(row - head) < 1.2D;
        }
    },
    BLINK(Material.REDSTONE) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            return ((phase * 8) % 1.0D) < 0.45D;
        }
    },
    SPIRAL(Material.CHAIN) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            double dx = col - (cols - 1) / 2.0D, dy = row - (rows - 1) / 2.0D;
            double angle = Math.atan2(dy, dx);
            double distance = Math.sqrt(dx * dx + dy * dy);
            double value = angle / (Math.PI * 2) + distance * 0.25D - phase * 2;
            return Math.abs(((value % 1.0D) + 1.0D) % 1.0D - 0.5D) < 0.18D;
        }
    },
    SPARKLE(Material.GLOWSTONE_DUST) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            int seed = (col * 73856093) ^ (row * 19349663) ^ ((int) (phase * 10) * 83492791);
            seed = (seed ^ (seed >>> 13)) * 1274126177;
            return ((seed >>> 7) & 7) == 0;
        }
    },
    STROBE(Material.REDSTONE_TORCH) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            return ((phase * 40) % 1.0D) < 0.35D;
        }
    },
    RUNNER(Material.RAIL) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            double sweep = Math.abs(((phase * 4) % 2.0D) - 1.0D);
            return Math.abs(col - sweep * (cols - 1)) < 1.0D;
        }
    },
    PATTERN_WIPE(Material.SHEARS) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            if (pattern == null || pattern.length == 0) return false;
            int index = row * cols + col;
            if (index < 0 || index >= pattern.length || !pattern[index]) return false;
            double open = phase < 0.5D ? phase * 2 : (1 - phase) * 2;
            return col <= open * cols;
        }
    },
    PATTERN_PULSE(Material.HEART_OF_THE_SEA) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            if (pattern == null || pattern.length == 0) return false;
            int index = row * cols + col;
            if (index < 0 || index >= pattern.length || !pattern[index]) return false;
            return ((phase * 10) % 1.0D) < 0.55D;
        }
    },
    PATTERN_FADE(Material.GLOWSTONE_DUST) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            if (pattern == null || pattern.length == 0) return false;
            int index = row * cols + col;
            if (index < 0 || index >= pattern.length || !pattern[index]) return false;
            int seed = (col * 73856093) ^ (row * 19349663);
            seed = (seed ^ (seed >>> 13)) * 1274126177;
            double own = ((seed >>> 8) & 1023) / 1023.0D;
            double open = phase < 0.5D ? phase * 2 : (1 - phase) * 2;
            return own <= open;
        }
    },
    PATTERN(Material.PAINTING) {
        public boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern) {
            if (pattern == null || pattern.length == 0) return false;
            int index = row * cols + col;
            return index >= 0 && index < pattern.length && pattern[index];
        }
    };

    private final @NonNull Material icon;

    /** Подпись на языке зрителя: тексты живут в lang.yml, а не в константах. */
    @NonNull
    public String getDisplay(String locale) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale,
            "lamp.animation." + this.name().toLowerCase(java.util.Locale.ROOT) + ".name");
    }

    @NonNull
    public String getHint(String locale) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale,
            "lamp.animation." + this.name().toLowerCase(java.util.Locale.ROOT) + ".hint");
    }

    LampAnimation(@NonNull Material icon) {
        this.icon = icon;
    }

    /**
     * @param phase 0..1, сколько прошло от начала эффекта, умноженное на скорость
     */
    public abstract boolean lit(int col, int row, int cols, int rows, double phase, boolean[] pattern);

    @NonNull
    public LampAnimation next() {
        LampAnimation[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    @NonNull
    public static LampAnimation byName(String name, @NonNull LampAnimation def) {
        if (name == null) return def;
        for (LampAnimation animation : values()) if (animation.name().equalsIgnoreCase(name)) return animation;
        return def;
    }
}
