package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;

/**
 * Чекпоинт уровня: точка, на которую игрока возвращает при проигрыше.
 * <p>
 * ЭТО НЕ РЕЖИМ СИНХРОНИЗАЦИИ ПО СЕКУНДАМ. Там трек резался на кусочки по секунде и
 * проигрывался кусками весь забег. Здесь трек режется ровно по числу чекпоинтов
 * (максимум {@link LightShowSettings#MAX_CHECKPOINTS} штук, то есть максимум
 * {@link LightShowSettings#MAX_CHECKPOINTS}+1 файл в ресурспаке), и каждый кусок играет
 * целиком от своего чекпоинта до следующего.
 */
@Getter
public class Checkpoint {
    private @NonNull Vector position;
    @Setter
    private boolean enabled = true;

    public Checkpoint(@NonNull Vector position) {
        this.position = position.clone();
    }

    public void setPosition(@NonNull Vector position) {
        this.position = position.clone();
    }

    @NonNull
    public Location toLocation(@NonNull World world) {
        return this.position.toLocation(world);
    }

    @NonNull
    public Checkpoint copy() {
        Checkpoint copy = new Checkpoint(this.position);
        copy.enabled = this.enabled;
        return copy;
    }

    @NonNull
    public String format() {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f",
            this.position.getX(), this.position.getY(), this.position.getZ());
    }

    @NonNull
    public String serialize() {
        return this.position.getX() + "/" + this.position.getY() + "/" + this.position.getZ()
            + "/" + this.enabled;
    }

    @Nullable
    public static Checkpoint deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split("/");
        if (args.length < 3) return null;
        try {
            Checkpoint checkpoint = new Checkpoint(new Vector(
                Double.parseDouble(args[0]),
                Double.parseDouble(args[1]),
                Double.parseDouble(args[2])));
            if (args.length >= 4) checkpoint.enabled = Boolean.parseBoolean(args[3]);
            return checkpoint;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
