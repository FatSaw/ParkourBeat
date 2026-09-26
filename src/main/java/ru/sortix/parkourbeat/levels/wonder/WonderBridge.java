package ru.sortix.parkourbeat.levels.wonder;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ru.lightshow.api.Audience;
import ru.lightshow.api.LightShowAPI;
import ru.lightshow.api.LightShowProvider;
import ru.lightshow.api.ShowBuilder;
import ru.lightshow.api.ShowHandle;

import javax.annotation.Nullable;

/**
 * Единственное место, где ParkourBeat разговаривает с LightShow.
 * <p>
 * Плагин объявлен как softdepend, поэтому классы LightShow трогаются только внутри этого
 * класса и только после {@link #isAvailable()}: если LightShow не установлен, редактор
 * продолжает работать и честно говорит строителю, чего не хватает.
 */
public final class WonderBridge {

    private static Boolean pluginPresent = null;

    private WonderBridge() {
    }

    public static boolean isAvailable() {
        if (pluginPresent == null) {
            pluginPresent = Bukkit.getPluginManager().getPlugin("LightShow") != null;
        }
        if (!pluginPresent) return false;
        try {
            return LightShowProvider.get() != null;
        } catch (Throwable t) {
            pluginPresent = false;
            return false;
        }
    }

    /** Сбрасывается на /pb reload, иначе после перезагрузки держали бы старый ответ. */
    public static void invalidate() {
        pluginPresent = null;
    }

    @Nullable
    public static String transport() {
        if (!isAvailable()) return null;
        return LightShowProvider.get().transport().name();
    }

    /**
     * Проверить эффект, не запуская его. null — всё в порядке, иначе текст ошибки для строителя.
     */
    @Nullable
    public static String validate(@NonNull WonderEffect effect) {
        if (!isAvailable()) return "плагин LightShow не установлен";
        try {
            ShowBuilder builder = LightShowProvider.get().show();
            applySpec(builder, effect);
            return builder.validate();
        } catch (Throwable t) {
            return t.getMessage() == null ? t.toString() : t.getMessage();
        }
    }

    public static int estimatePoints(@NonNull WonderEffect effect) {
        if (!isAvailable()) return 0;
        try {
            ShowBuilder builder = LightShowProvider.get().show();
            applySpec(builder, effect);
            return builder.estimatePoints();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Запустить эффект для одного игрока. Никто, кроме него, частиц не увидит:
     * у каждого бегущего своя позиция в песне, и общий показ превратился бы в кашу.
     */
    @Nullable
    public static ShowHandle play(@NonNull Player player,
                                  @NonNull WonderEffect effect,
                                  int durationTicks
    ) {
        return play(player, effect, durationTicks, null);
    }

    @Nullable
    public static ShowHandle play(@NonNull Player player,
                                  @NonNull WonderEffect effect,
                                  int durationTicks,
                                  @Nullable Location onTrack
    ) {
        if (!isAvailable()) return null;
        try {
            LightShowAPI api = LightShowProvider.get();
            Location where = effect.getAnchor() == WonderAnchor.FOLLOW
                ? player.getLocation()
                : resolveLocation(player, effect, onTrack);

            ShowBuilder builder = api.show();
            applySpec(builder, effect, where);

            builder.owner(player)
                .audience(Audience.of(player))
                .duration(Math.max(1, durationTicks))
                .label("pb-wonder:" + effect.getPresetId())
                .scale(effect.getScale());

            if (effect.getAnchor() == WonderAnchor.FOLLOW) {
                builder.attachTo(player).offset(effect.getSide(), effect.getHeight(), 0.0D);
            } else {
                builder.at(where);
                if (effect.getAnchor() == WonderAnchor.PATH && onTrack != null) {
                    // Ориентация берётся из самой точки: фигура ложится вдоль дорожки
                    builder.face("loc");
                }
            }
            return builder.start();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Куда встанет эффект. Точка считается один раз, поэтому эффект спокойно остаётся
     * позади, пока игрок бежит дальше — как декорация, а не как приклеенный к лицу спрайт.
     */
    @NonNull
    public static Location resolveLocation(@NonNull Player player, @NonNull WonderEffect effect) {
        return resolveLocation(player, effect, null);
    }

    /**
     * Точка эффекта.
     * <p>
     * PATH считается от самой трассы: точка на таймкоде плюс сдвиги в осях дорожки.
     * Она не зависит от того, куда игрок смотрит, поэтому коридор ложится вдоль пути,
     * а не уезжает вслед за камерой.
     */
    @NonNull
    public static Location resolveLocation(@NonNull Player player,
                                           @NonNull WonderEffect effect,
                                           @Nullable Location onTrack) {
        if (effect.getAnchor() == WonderAnchor.PATH && onTrack != null && onTrack.getWorld() != null) {
            Location base = onTrack.clone();
            Vector forward = base.getDirection().setY(0);
            if (forward.lengthSquared() < 1.0E-6D) forward = new Vector(0, 0, 1);
            forward.normalize();
            Vector right = new Vector(-forward.getZ(), 0, forward.getX());

            Location result = base.clone()
                .add(forward.multiply(effect.getDistance()))
                .add(right.multiply(effect.getSide()));
            result.add(0, effect.getHeight(), 0);
            result.setDirection(base.getDirection());
            result.setPitch(0f);
            return result;
        }
        return resolveFromPlayer(player, effect);
    }

    @NonNull
    private static Location resolveFromPlayer(@NonNull Player player, @NonNull WonderEffect effect) {
        if (effect.getAnchor() == WonderAnchor.FIXED && effect.getFixedLocation() != null) {
            Location fixed = effect.getFixedLocation().clone();
            if (fixed.getWorld() == null) fixed.setWorld(player.getWorld());
            return fixed;
        }

        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-6D) forward = new Vector(0, 0, 1);
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());

        Location result = eye.clone()
            .add(forward.multiply(effect.getDistance()))
            .add(right.multiply(effect.getSide()));
        result.add(0, effect.getHeight(), 0);
        result.setPitch(0f);
        return result;
    }

    /**
     * Вектор полёта фигуры.
     * <p>
     * Движок сам двигает и точку рождения, и уже живущие частицы по одному закону,
     * поэтому фигура едет целиком, а позади не остаётся ни одной брошенной частицы.
     * Дополнительных частиц не появляется: их ровно столько же, сколько стояло на месте.
     */
    /**
     * Сколько тиков длится полёт.
     * <p>
     * Частица живёт 60 тиков, поэтому дольше 55 нести фигуру просто некому.
     */
    private static int flightTicks(@NonNull WonderEffect effect) {
        return (int) Math.min(51, Math.max(5, effect.getDurationMillis() / 50));
    }

    /**
     * Так это и делается на самом деле.
     * <p>
     * Все точки рождаются в ОДНОЙ точке далеко впереди, и каждой выдаётся своя скорость.
     * Клиент сам разводит их в форму текста и одновременно уносит фигуру вперёд: за 0 мс
     * появляется одна вспышка, которая на глазах разворачивается в надпись и подлетает.
     * Отправка ровно одна, поэтому в пикселе одна частица и ничего не пересоздаётся.
     */
    private static String assembleParams(@NonNull WonderEffect effect, int ticks) {
        double a = effect.getApproach();
        double ax = 0, ay = 0, az = -a;
        switch (effect.getApproachDir()) {
            case 2: ax = 0; ay = a; az = 0; break;      // снизу вверх
            case 3: ax = 0; ay = -a; az = 0; break;     // сверху вниз
            case 4: ax = -a; ay = 0; az = 0; break;     // слева
            case 5: ax = a; ay = 0; az = 0; break;      // справа
            default: break;                              // навстречу и к виду совпадают по осям
        }
        return "assemble:" + ticks + "t"
            + " arrive:" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", ax, ay, az)
            + " to:" + (ticks + 12) + "t";
    }

    /** Куда встанет фигура после сборки: то же смещение, только уже как позиция. */
    private static String holdOffset(@NonNull WonderEffect effect) {
        double a = effect.getApproach();
        switch (effect.getApproachDir()) {
            case 2: return "oy:" + fmt(a);
            case 3: return "oy:" + fmt(-a);
            case 4: return "ox:" + fmt(-a);
            case 5: return "ox:" + fmt(a);
            default: return "oz:" + fmt(-a);
        }
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /**
     * Подлёт собирается из ДВУХ слоёв, а не из движущейся фигуры.
     * <p>
     * Первый слой рождается один раз на полном расстоянии и летит к игроку своей скоростью:
     * частицы не пересоздаются, поэтому ничего не мигает и не оставляет шлейфа. Второй слой
     * включается ровно тогда, когда полёт кончился, и просто держит фигуру там, где она встала.
     * Направление берёт сам движок через motion:to_player, поэтому оно не может оказаться
     * задом наперёд, куда бы ни смотрел игрок.
     */
    private static void applySpec(@NonNull ShowBuilder builder, @NonNull WonderEffect effect) {
        applySpec(builder, effect, null);
    }

    private static void applySpec(@NonNull ShowBuilder builder, @NonNull WonderEffect effect,
                                  @Nullable Location where) {
        String showParams = effect.getParams();
        if (showParams != null && !showParams.trim().isEmpty()) builder.params(showParams);

        double approach = effect.getApproach();
        boolean flying = approach > 0.5D;
        int ticks = flightTicks(effect);

        for (String chunk : effect.getSpec().split("\\|")) {
            String layer = chunk.trim();
            if (layer.isEmpty()) continue;

            int at = layer.indexOf('@');
            String geometry = (at >= 0 ? layer.substring(0, at) : layer).trim();
            String layerParams = at >= 0 ? layer.substring(at + 1).trim() : "";
            if (geometry.isEmpty()) continue;

            if (!flying) {
                addLayer(builder, effect, geometry, layerParams, null);
                continue;
            }

            // Сборка: одна вспышка, которая разворачивается в фигуру и подлетает
            addLayer(builder, effect, geometry, layerParams, assembleParams(effect, ticks));

            // Держим фигуру там, где сборка закончилась, и ни тиком раньше.
            // in:none обязателен: иначе слой на своём старте честно проигрывает анимацию
            // входа заново, и поверх готовой надписи начинается второй прилёт.
            addLayer(builder, effect, geometry, layerParams,
                holdOffset(effect) + " from:" + ticks + "t burst:true motion:none refresh:51"
                    + " in:none int:0t");
        }
    }

    private static void addLayer(@NonNull ShowBuilder builder, @NonNull WonderEffect effect,
                                 @NonNull String geometry, @NonNull String layerParams,
                                 @Nullable String extra) {
        ru.lightshow.api.LayerBuilder layerBuilder;
        String lower = geometry.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("text:")) {
            layerBuilder = builder.text(geometry.substring(5).replace('_', ' '));
        } else {
            layerBuilder = builder.formula(geometry);
        }
        if (!layerParams.isEmpty()) layerBuilder.params(layerParams);
        if (extra != null) layerBuilder.params(extra);

        String color = effect.getColor();
        if (color != null && !color.isEmpty()) layerBuilder.color(color);
        if (effect.getThick() > 0) layerBuilder.param("thick", effect.getThick());
        layerBuilder.and();
    }

}
