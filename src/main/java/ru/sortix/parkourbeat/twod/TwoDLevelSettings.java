package ru.sortix.parkourbeat.twod;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Настройки конкретного 2D-уровня: спавн кубика, длина линии и монетки.
 * <p>
 * Длина линии тут не украшение, а ДЛИНА САМОГО УРОВНЯ: финиш стоит ровно там, где
 * заканчивается линия. Поэтому линию можно тянуть куда угодно, даже если паркур под
 * ней ещё не построен - это просто «уровень пока не доделан», а не ошибка.
 * <p>
 * Всё хранится без мира: мир у уровня ровно один и подставляется при чтении, так
 * настройки переживают копирование уровня и шаблоны.
 */
@Getter
public class TwoDLevelSettings {

    /** Длина линии по умолчанию: короткая, ровно как просил заказчик. */
    public static final double DEFAULT_LINE_LENGTH = 16.0D;
    public static final double MIN_LINE_LENGTH = 4.0D;
    public static final double MAX_LINE_LENGTH = 4096.0D;

    /** Больше монеток на уровень не имеет смысла: это уже не уровень, а склад. */
    public static final int MAX_COINS = 64;

    /** Радиус подбора монетки кубиком. */
    public static final double COIN_PICKUP_RADIUS = 1.05D;

    private @Nullable Double cubeSpawnX = null;
    private @Nullable Double cubeSpawnY = null;
    private @Nullable Double cubeSpawnZ = null;

    private double lineLength = DEFAULT_LINE_LENGTH;

    /**
     * Своя скорость кубика на этом уровне, блоков в секунду.
     * 0 значит "как на сервере" ({@link TwoDTuning#SPEED}).
     */
    private double speed = 0.0D;

    private final List<Vector> coins = new ArrayList<>();

    /**
     * Блоки-шипы. Хранятся координатами блока, а не точками: шип - это весь блок
     * целиком, и попадание в него смертельно с любой стороны.
     */
    private final java.util.Set<Vector> spikes = new java.util.HashSet<>();

    public boolean hasCubeSpawn() {
        return this.cubeSpawnX != null && this.cubeSpawnY != null && this.cubeSpawnZ != null;
    }

    /**
     * @return точка спавна кубика в этом мире или null, если строитель её ещё не ставил
     */
    @Nullable
    public Location getCubeSpawn(@Nullable World world) {
        if (!this.hasCubeSpawn() || world == null) return null;
        return new Location(world, this.cubeSpawnX, this.cubeSpawnY, this.cubeSpawnZ);
    }

    public void setCubeSpawn(@Nullable Location location) {
        if (location == null) {
            this.cubeSpawnX = null;
            this.cubeSpawnY = null;
            this.cubeSpawnZ = null;
            return;
        }
        // Центр блока по горизонтали: кубик обязан ехать ровно по середине дорожки,
        // иначе он цепляется углом за стены, стоящие вплотную к пути.
        this.cubeSpawnX = location.getBlockX() + 0.5D;
        this.cubeSpawnY = location.getY();
        this.cubeSpawnZ = location.getBlockZ() + 0.5D;
    }

    public static final double MIN_SPEED = 1.0D;
    public static final double MAX_SPEED = 30.0D;

    /**
     * @param speed блоков в секунду; 0 или меньше возвращает уровень к серверной скорости
     */
    public void setSpeed(double speed) {
        if (Double.isNaN(speed) || Double.isInfinite(speed) || speed <= 0.0D) {
            this.speed = 0.0D;
            return;
        }
        this.speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    /** Скорость, с которой реально поедет кубик. */
    public double resolveSpeed() {
        return this.speed > 0.0D ? this.speed : TwoDTuning.SPEED;
    }

    public boolean hasOwnSpeed() {
        return this.speed > 0.0D;
    }

    public void setLineLength(double lineLength) {
        if (Double.isNaN(lineLength) || Double.isInfinite(lineLength)) {
            this.lineLength = DEFAULT_LINE_LENGTH;
            return;
        }
        this.lineLength = Math.max(MIN_LINE_LENGTH, Math.min(MAX_LINE_LENGTH, lineLength));
    }

    // ==================== МОНЕТКИ ====================

    @NonNull
    public List<Vector> getCoins() {
        return Collections.unmodifiableList(this.coins);
    }

    public int getCoinsAmount() {
        return this.coins.size();
    }

    /**
     * @return false, если монеток уже максимум или в этой точке она уже лежит
     */
    public boolean addCoin(@NonNull Location location) {
        if (this.coins.size() >= MAX_COINS) return false;

        Vector point = new Vector(
            location.getBlockX() + 0.5D,
            location.getY(),
            location.getBlockZ() + 0.5D);

        for (Vector coin : this.coins) {
            if (coin.distanceSquared(point) < 0.25D) return false;
        }
        this.coins.add(point);
        return true;
    }

    /**
     * Убрать ближайшую монетку в радиусе.
     *
     * @return true, если что-то убрали
     */
    public boolean removeCoinNear(@NonNull Location location, double radius) {
        Vector point = location.toVector();
        int best = -1;
        double bestDistance = radius * radius;

        for (int i = 0; i < this.coins.size(); i++) {
            double distance = this.coins.get(i).distanceSquared(point);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        if (best < 0) return false;
        this.coins.remove(best);
        return true;
    }

    public void clearCoins() {
        this.coins.clear();
    }

    // ==================== ШИПЫ ====================

    public int getSpikesAmount() {
        return this.spikes.size();
    }

    @NonNull
    private static Vector blockKey(@NonNull Location location) {
        return new Vector(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** @return true, если блок стал шипом; false, если он им уже был */
    public boolean addSpike(@NonNull Location location) {
        return this.spikes.add(blockKey(location));
    }

    /** @return true, если шип был и его убрали */
    public boolean removeSpike(@NonNull Location location) {
        return this.spikes.remove(blockKey(location));
    }

    public boolean isSpike(int x, int y, int z) {
        if (this.spikes.isEmpty()) return false;
        return this.spikes.contains(new Vector(x, y, z));
    }

    @NonNull
    public java.util.Set<Vector> getSpikes() {
        return java.util.Collections.unmodifiableSet(this.spikes);
    }

    public void clearSpikes() {
        this.spikes.clear();
    }

    // ==================== СОХРАНЕНИЕ ====================

    public void write(@NonNull ConfigurationSection config, @NonNull String path) {
        if (this.hasCubeSpawn()) {
            config.set(path + ".cube_spawn.x", this.cubeSpawnX);
            config.set(path + ".cube_spawn.y", this.cubeSpawnY);
            config.set(path + ".cube_spawn.z", this.cubeSpawnZ);
        } else {
            config.set(path + ".cube_spawn", null);
        }
        config.set(path + ".line_length", this.lineLength);
        config.set(path + ".speed", this.speed);

        List<String> rawCoins = new ArrayList<>();
        for (Vector coin : this.coins) {
            rawCoins.add(coin.getX() + ";" + coin.getY() + ";" + coin.getZ());
        }
        config.set(path + ".coins", rawCoins);

        List<String> rawSpikes = new ArrayList<>();
        for (Vector spike : this.spikes) {
            rawSpikes.add(spike.getBlockX() + ";" + spike.getBlockY() + ";" + spike.getBlockZ());
        }
        config.set(path + ".spikes", rawSpikes);
    }

    @NonNull
    public static TwoDLevelSettings read(@NonNull ConfigurationSection config, @NonNull String path) {
        TwoDLevelSettings result = new TwoDLevelSettings();
        result.setLineLength(config.getDouble(path + ".line_length", DEFAULT_LINE_LENGTH));
        result.setSpeed(config.getDouble(path + ".speed", 0.0D));

        if (config.contains(path + ".cube_spawn.x")) {
            result.cubeSpawnX = config.getDouble(path + ".cube_spawn.x");
            result.cubeSpawnY = config.getDouble(path + ".cube_spawn.y");
            result.cubeSpawnZ = config.getDouble(path + ".cube_spawn.z");
        }

        for (String raw : config.getStringList(path + ".coins")) {
            if (raw == null) continue;
            String[] parts = raw.split(";");
            if (parts.length != 3) continue;
            try {
                result.coins.add(new Vector(
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        for (String raw : config.getStringList(path + ".spikes")) {
            if (raw == null) continue;
            String[] parts = raw.split(";");
            if (parts.length != 3) continue;
            try {
                result.spikes.add(new Vector(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())));
            } catch (NumberFormatException ignored) {
            }
        }

        return result;
    }
}
