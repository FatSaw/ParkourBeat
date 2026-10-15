package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Material;
import ru.sortix.parkourbeat.digger.DiggerPickaxe;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;

/**
 * КИРКА НА ОТРЕЗКЕ ТРЕКА.
 * <p>
 * Работает только на картах «Копателя». Вне отрезков в руке лежит кирка уровня из
 * настроек карты, а внутри - та, что задана здесь: удобно, когда куплет проходится
 * деревянной, а на дропе в руке оказывается незеритовая.
 * <p>
 * На механику это не влияет вообще: в творческом режиме любая руда ломается с одного
 * клика любым инструментом. Кирка тут - часть светового шоу, а не инструмент.
 */
@Getter
public class PickaxeCue implements LightShowElement {
    public static final int DEFAULT_DURATION_MILLIS = 10_000;

    private int startMillis;
    private int endMillis;
    @Setter
    private @NonNull Material pickaxe;

    public PickaxeCue(int startMillis, int endMillis, @NonNull Material pickaxe) {
        this.startMillis = clamp(startMillis);
        this.endMillis = Math.max(this.startMillis, clamp(endMillis));
        this.pickaxe = DiggerPickaxe.isSupported(pickaxe) ? pickaxe : Material.NETHERITE_PICKAXE;
    }

    private static int clamp(int millis) {
        return Math.max(0, Math.min(TimeUtils.MAX_TIMECODE_MILLIS, millis));
    }

    @Override
    public boolean hasEnd() {
        return true;
    }

    @Override
    public void setStartMillis(int startMillis) {
        this.startMillis = clamp(startMillis);
        if (this.endMillis < this.startMillis) this.endMillis = this.startMillis;
    }

    @Override
    public void setEndMillis(int endMillis) {
        this.endMillis = Math.max(this.startMillis, clamp(endMillis));
    }

    public boolean isActive(long songTimeMillis) {
        return songTimeMillis >= this.startMillis && songTimeMillis < this.endMillis;
    }

    @NonNull
    @Override
    public String getTimecode() {
        return TimeUtils.formatTimecode(this.startMillis);
    }

    @NonNull
    public String getStartTimecode() {
        return TimeUtils.formatTimecode(this.startMillis);
    }

    @NonNull
    public String getEndTimecode() {
        return TimeUtils.formatTimecode(this.endMillis);
    }

    @NonNull
    public PickaxeCue copy() {
        return new PickaxeCue(this.startMillis, this.endMillis, this.pickaxe);
    }

    @NonNull
    public String serialize() {
        return this.startMillis + " " + this.endMillis + " " + this.pickaxe.name();
    }

    @Nullable
    public static PickaxeCue deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] parts = input.trim().split(" ");
        if (parts.length < 3) return null;
        try {
            Material material = Material.matchMaterial(parts[2]);
            if (material == null || !DiggerPickaxe.isSupported(material)) return null;
            return new PickaxeCue(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), material);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
