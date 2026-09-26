package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.lamps.LampEngine;
import ru.sortix.parkourbeat.levels.lamps.LampWall;

import java.util.ArrayList;
import java.util.List;

/**
 * СВЕТОВОЕ ШОУ ЗАБЕГА.
 * <p>
 * Каждый слой включается своим событием:
 * <ol>
 *     <li>ПОПАДАНИЕ. Идеальный удар отмечается отдельным цветным всплеском - иначе
 *         его не отличить от просто хорошего.</li>
 *     <li>ДРОП. На древних обломках бьёт залп во весь тоннель: вспышка, лучи из
 *         четырёх углов, взрыв искр и разряд по всем ламповым стенам уровня.</li>
 *     <li>СРЫВ. Комбо порвалось - свет гаснет одним кадром.</li>
 * </ol>
 * Всё - и частицы, и лампы - идёт лично игроку: частицы через {@code spawnParticle},
 * лампы поддельными изменениями блоков. По одному тоннелю одновременно едут разные
 * люди, и ни один из них не должен видеть чужой дроп у себя на экране.
 */
public class DiggerShow {

    private final @NonNull ru.sortix.parkourbeat.ParkourBeat plugin;
    private final @NonNull Player player;
    private final @NonNull Level level;
    private final @NonNull DiggerLevelSettings settings;
    private final @NonNull World world;

    private final boolean axisX;
    private final int sign;

    /** Потолок блоков на один залп ламп: дальше это уже пакетный шторм, а не шоу. */
    private static final int MAX_LAMP_PACKETS = 300;

    /** Сколько тиков горит залп, прежде чем стена вернётся к настоящему виду. */
    private static final long LAMP_FLASH_TICKS = 6L;

    /** Текущий угол цвета, 0..360. Крутится сам, скорость зависит от комбо. */
    private double hue = 0.0D;

    public DiggerShow(@NonNull ru.sortix.parkourbeat.ParkourBeat plugin,
                      @NonNull Player player,
                      @NonNull Level level,
                      boolean axisX,
                      int sign
    ) {
        this.plugin = plugin;
        this.player = player;
        this.level = level;
        this.settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        this.world = level.getWorld();
        this.axisX = axisX;
        this.sign = sign;
    }

    // ==================== ЦВЕТ ====================

    /**
     * Цвет по углу на круге. Именно так получается настоящий RGB, а не шестнадцать
     * ванильных оттенков: частица REDSTONE принимает любой цвет целиком.
     */
    @NonNull
    public static Color hueColor(double degrees, double saturation, double value) {
        double h = ((degrees % 360.0D) + 360.0D) % 360.0D / 60.0D;
        int sector = (int) Math.floor(h);
        double f = h - sector;
        double p = value * (1.0D - saturation);
        double q = value * (1.0D - saturation * f);
        double t = value * (1.0D - saturation * (1.0D - f));

        double r, g, b;
        switch (sector % 6) {
            case 0: r = value; g = t; b = p; break;
            case 1: r = q; g = value; b = p; break;
            case 2: r = p; g = value; b = t; break;
            case 3: r = p; g = q; b = value; break;
            case 4: r = t; g = p; b = value; break;
            default: r = value; g = p; b = q; break;
        }
        return Color.fromRGB(
            (int) Math.round(Math.max(0, Math.min(255, r * 255))),
            (int) Math.round(Math.max(0, Math.min(255, g * 255))),
            (int) Math.round(Math.max(0, Math.min(255, b * 255))));
    }

    // ==================== СОБЫТИЯ ====================

    /**
     * Попадание.
     * <p>
     * Пусто намеренно: любое оформление обычного удара на плотном треке мешает видеть
     * следующие руды. Всё, что должно светиться, строитель ставит сам в световом шоу.
     */
    public void onHit(@NonNull Location center, @NonNull DiggerOre ore, @NonNull DiggerHit hit, int multiplier) {
    }

    /**
     * ДРОП-НОТА. Самое громкое событие в режиме.
     * <p>
     * Ламповые стены уровня разряжаются целиком: их состояние ставится прямо в блоки,
     * без редстоуна и без физики, поэтому даже стена в пару сотен ламп не кладёт тик.
     */
    public void onDrop(@NonNull Location center, int multiplier) {
        // ОДИН ЛУЧ ВВЕРХ - И БОЛЬШЕ НИЧЕГО.
        //
        // Раньше здесь был полный залп: вспышка, два десятка искр, четыре луча по углам
        // сечения и два кольца цветной пыли вокруг. Всё это разворачивалось ровно перед
        // камерой, в том самом месте, куда игрок в этот момент смотрит, и на несколько
        // тиков закрывало собой следующие руды - то есть самая заметная нота карты
        // гарантированно портила пару нот после себя.
        //
        // Луч из точки дропа строго вверх виден так же хорошо, но стоит на месте самой
        // руды и не лезет в стороны, откуда прилетают следующие.
        double height = Math.max(2.0D, this.settings.getTunnelHeight() - 1.0D);
        DiggerEffects.beam(this.player, center.clone(), height);

        // Разряд по ламповым стенам и раскат грома остались, но по умолчанию выключены:
        // это тоже часть того самого залпа. Включаются обратно командой
        // /pb digger set drop_burn_lamps true и drop_thunder true.
        if (DiggerTuning.DROP_BURN_LAMPS) this.burnLampWalls();
        if (DiggerTuning.DROP_THUNDER) {
            this.player.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.18f, 1.9f);
        }
    }

    /**
     * Разряд по всем ламповым стенам уровня - ТОЛЬКО у этого игрока.
     * <p>
     * Настоящие блоки не трогаются вообще: по тому же тоннелю едут другие люди, и
     * стена, которую поджёг чужой дроп, сбивала бы им картинку в самый неудобный
     * момент. Вместо этого игроку уходят поддельные изменения блоков, а через
     * несколько тиков - настоящие обратно.
     * <p>
     * Есть жёсткий потолок в {@link #MAX_LAMP_PACKETS} блоков на залп: стена в
     * несколько тысяч ламп превратила бы каждый дроп в пакетный шторм.
     */
    private void burnLampWalls() {
        List<Location> touched = new ArrayList<>();
        try {
            BlockData lit = Material.REDSTONE_LAMP.createBlockData();
            if (lit instanceof Lightable) ((Lightable) lit).setLit(true);

            for (LampWall wall : this.level.getLightShow().getLampWalls()) {
                for (int x = wall.getX1(); x <= wall.getX2(); x++) {
                    for (int y = wall.getY1(); y <= wall.getY2(); y++) {
                        for (int z = wall.getZ1(); z <= wall.getZ2(); z++) {
                            if (touched.size() >= MAX_LAMP_PACKETS) break;
                            Block block = this.world.getBlockAt(x, y, z);
                            if (!LampEngine.isLamp(block)) continue;
                            Location location = block.getLocation();
                            this.player.sendBlockChange(location, lit);
                            touched.add(location);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Стен может не быть вовсе - это нормальная карта, а не ошибка.
        }

        if (touched.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!this.player.isOnline()) return;
            for (Location location : touched) {
                this.player.sendBlockChange(location, this.world.getBlockAt(location).getBlockData());
            }
        }, LAMP_FLASH_TICKS);
    }

    /** Срыв комбо: свет гаснет одним кадром. */
    public void onBreakCombo(@NonNull Location center) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0x303030), 1.4f);
        this.player.spawnParticle(Particle.REDSTONE, center, 16, 1.0D, 1.0D, 1.0D, 0.0D, dust);
    }
}
