package ru.sortix.parkourbeat.levels.lamps;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import ru.sortix.parkourbeat.levels.settings.LightShowElement;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;

/**
 * Стена или платформа из красных ламп, которая живёт по таймкоду песни.
 * <p>
 * Область задаётся двумя углами, сетка считается по двум самым длинным сторонам,
 * поэтому одинаково работают и вертикальная стена, и пол под ногами.
 */
@Getter
public class LampWall implements LightShowElement {
    public static final int DEFAULT_DURATION_MILLIS = 6_000;

    private int startMillis;
    private int endMillis;

    private int x1, y1, z1, x2, y2, z2;
    @Setter private @NonNull LampAnimation animation;
    @Setter private double speed;
    @Setter private boolean inverted;
    @Setter private @Nullable String pattern;
    /** Проявление и угасание узора, в долях длительности. */
    @Setter private double fadeIn = 0.0D;
    @Setter private double fadeOut = 0.0D;
    @Setter private boolean loop = false;

    public LampWall(int startMillis, int endMillis,
                    int x1, int y1, int z1, int x2, int y2, int z2) {
        this.startMillis = Math.max(0, startMillis);
        this.endMillis = Math.max(this.startMillis, endMillis);
        this.x1 = Math.min(x1, x2); this.y1 = Math.min(y1, y2); this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2); this.y2 = Math.max(y1, y2); this.z2 = Math.max(z1, z2);
        this.animation = LampAnimation.WAVE;
        this.speed = 1.0D;
    }

    public void setCorners(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = Math.min(x1, x2); this.y1 = Math.min(y1, y2); this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2); this.y2 = Math.max(y1, y2); this.z2 = Math.max(z1, z2);
    }

    public int getWidthX() { return this.x2 - this.x1 + 1; }
    public int getHeightY() { return this.y2 - this.y1 + 1; }
    public int getWidthZ() { return this.z2 - this.z1 + 1; }

    public int getBlocksAmount() { return this.getWidthX() * this.getHeightY() * this.getWidthZ(); }

    /**
     * Какая ось у области самая тонкая: 0 это X, 1 это Y, 2 это Z.
     * <p>
     * Раньше сетка всегда строилась от Y, и если строитель выделял пол, захватив пару блоков
     * воздуха сверху, все клетки пола оказывались в одной строке. Один клик кистью зажигал
     * тогда сразу всю ширину. Теперь тонкая ось это «толщина» стены, а сетку дают две другие.
     */
    public int getNormalAxis() {
        // Полом считаем только то, что реально плоское по высоте. Иначе стена, у которой
        // высота случайно оказалась меньше ширины, разворачивалась набок, и один клик кистью
        // зажигал целый столбец.
        if (this.getHeightY() <= 1) return 1;
        return this.getWidthX() <= this.getWidthZ() ? 0 : 2;
    }

    /** Сколько клеток по горизонтали в сетке анимации. */
    public int getColumns() {
        int normal = this.getNormalAxis();
        if (normal == 1) return Math.max(this.getWidthX(), this.getWidthZ());
        return normal == 0 ? this.getWidthZ() : this.getWidthX();
    }

    /** Сколько клеток по вертикали. У пола вторым измерением становится глубина. */
    public int getRows() {
        int normal = this.getNormalAxis();
        if (normal == 1) return Math.min(this.getWidthX(), this.getWidthZ());
        return this.getHeightY();
    }

    /** Клетка сетки для конкретного блока, или null если блок вне области. */
    public int[] cellOf(int x, int y, int z) {
        if (x < this.x1 || x > this.x2 || y < this.y1 || y > this.y2 || z < this.z1 || z > this.z2) return null;

        int normal = this.getNormalAxis();
        int col, row;
        if (normal == 1) {
            boolean alongX = this.getWidthX() >= this.getWidthZ();
            col = alongX ? x - this.x1 : z - this.z1;
            row = alongX ? z - this.z1 : x - this.x1;
        } else if (normal == 0) {
            col = z - this.z1;
            row = this.y2 - y;
        } else {
            col = x - this.x1;
            row = this.y2 - y;
        }
        if (col < 0 || row < 0 || col >= this.getColumns() || row >= this.getRows()) return null;
        return new int[] { col, row };
    }

    @Override
    public boolean hasEnd() { return true; }

    @Override
    public void setStartMillis(int startMillis) {
        this.startMillis = Math.max(0, startMillis);
        if (this.endMillis < this.startMillis) this.endMillis = this.startMillis;
    }

    @Override
    public void setEndMillis(int endMillis) {
        this.endMillis = Math.max(this.startMillis, endMillis);
    }

    @NonNull
    public String getStartTimecode() { return TimeUtils.formatTimecode(this.startMillis); }

    @NonNull
    public String getEndTimecode() { return TimeUtils.formatTimecode(this.endMillis); }

    @NonNull
    @Override
    public String getTimecode() { return this.getStartTimecode(); }

    public int getDurationMillis() { return this.endMillis - this.startMillis; }

    public boolean isActive(long songTimeMillis) {
        return songTimeMillis >= this.startMillis && songTimeMillis < this.endMillis;
    }

    /** Маска рисунка под текущий размер сетки. */
    @Nullable
    public boolean[] patternMask() {
        if (this.pattern == null || this.pattern.isEmpty()) return null;
        int cols = this.getColumns(), rows = this.getRows();
        String[] lines = this.pattern.split("/");
        int srcRows = lines.length;
        int srcCols = 1;
        for (String line : lines) srcCols = Math.max(srcCols, line.length());

        // Растягиваем рисунок на всю стену: сердечко, нарисованное на холсте девять на четыре,
        // должно занимать стену любого размера, а не гореть в её левом нижнем углу.
        boolean[] mask = new boolean[cols * rows];
        for (int row = 0; row < rows; row++) {
            int srcRow = rows <= 1 ? 0 : row * srcRows / rows;
            if (srcRow >= srcRows) srcRow = srcRows - 1;
            String line = lines[srcRow];
            for (int col = 0; col < cols; col++) {
                int srcCol = cols <= 1 ? 0 : col * srcCols / cols;
                if (srcCol >= line.length()) continue;
                mask[row * cols + col] = line.charAt(srcCol) != '0';
            }
        }
        return mask;
    }

    @NonNull
    public LampWall copy() {
        LampWall copy = new LampWall(this.startMillis, this.endMillis,
            this.x1, this.y1, this.z1, this.x2, this.y2, this.z2);
        copy.animation = this.animation;
        copy.speed = this.speed;
        copy.inverted = this.inverted;
        copy.pattern = this.pattern;
        copy.fadeIn = this.fadeIn;
        copy.fadeOut = this.fadeOut;
        copy.loop = this.loop;
        return copy;
    }

    @NonNull
    public String serialize() {
        return this.startMillis + " " + this.endMillis
            + " " + this.x1 + " " + this.y1 + " " + this.z1
            + " " + this.x2 + " " + this.y2 + " " + this.z2
            + " " + this.animation.name()
            + " " + this.speed
            + " " + this.inverted
            + " " + (this.pattern == null || this.pattern.isEmpty() ? "-" : this.pattern)
            + " " + this.fadeIn + " " + this.fadeOut + " " + this.loop;
    }

    @Nullable
    public static LampWall deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] a = input.trim().split(" ");
        if (a.length < 11) return null;
        try {
            LampWall wall = new LampWall(
                Integer.parseInt(a[0]), Integer.parseInt(a[1]),
                Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]),
                Integer.parseInt(a[5]), Integer.parseInt(a[6]), Integer.parseInt(a[7]));
            wall.animation = LampAnimation.byName(a[8], LampAnimation.WAVE);
            wall.speed = Double.parseDouble(a[9]);
            wall.inverted = Boolean.parseBoolean(a[10]);
            if (a.length > 11 && !a[11].equals("-")) wall.pattern = a[11];
            if (a.length > 14) {
                wall.fadeIn = Double.parseDouble(a[12]);
                wall.fadeOut = Double.parseDouble(a[13]);
                wall.loop = Boolean.parseBoolean(a[14]);
            }
            return wall;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
