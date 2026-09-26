package ru.sortix.parkourbeat.levels.wonder;

import ru.sortix.parkourbeat.utils.lang.Lang;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Location;
import ru.sortix.parkourbeat.levels.settings.LightShowElement;
import ru.sortix.parkourbeat.utils.ConfigUtils;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Один чудоэффект на таймлайне: с какой по какую секунду песни он живёт и что именно рисует.
 * <p>
 * {@link #spec} хранится ровно в том виде, в каком его понимает LightShow — слои через "|",
 * параметры слоя после "@". Благодаря этому пресеты, ручная правка и то, что напишет ИИ,
 * идут по одному и тому же пути, и ни одна из веток не может разойтись с другими.
 */
@Getter
public class WonderEffect implements LightShowElement {
    public static final int DEFAULT_DURATION_MILLIS = 4_000;

    private int startMillis;
    private int endMillis;

    @Setter private @NonNull String presetId;
    @Setter private @NonNull String spec;
    @Setter private @NonNull String params;
    @Setter private @NonNull WonderAnchor anchor;
    @Setter private double distance;
    @Setter private double height;
    @Setter private double side;
    @Setter private double scale;
    /** Куда эффект доезжает к концу. Отрицательное значение — подлёт выключен. */
    @Setter private double approach = -1.0D;
    /** Доворот вокруг вертикали в градусах. NaN означает автоматический доворот к игроку. */
    @Setter private double turn = 0.0D;
    /** 0 без утолщения, дальше плотнее. */
    @Setter private int thick = 0;
    /** Куда летит подлёт: 0 навстречу, 1 к виду, 2 вверх, 3 вниз, 4 влево, 5 вправо. */
    @Setter private int approachDir = 0;
    @Setter private @Nullable String color;
    @Setter private @Nullable Location fixedLocation;

    public WonderEffect(int startMillis,
                        int endMillis,
                        @NonNull String presetId,
                        @NonNull String spec,
                        @NonNull String params,
                        @NonNull WonderAnchor anchor
    ) {
        this.startMillis = clamp(startMillis);
        this.endMillis = Math.max(this.startMillis, clamp(endMillis));
        this.presetId = presetId;
        this.spec = spec;
        this.params = params;
        this.anchor = anchor;
        this.distance = 14.0D;
        this.height = 3.0D;
        this.side = 0.0D;
        this.scale = 1.0D;
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

    @NonNull
    public String getStartTimecode() {
        return TimeUtils.formatTimecode(this.startMillis);
    }

    @NonNull
    public String getEndTimecode() {
        return TimeUtils.formatTimecode(this.endMillis);
    }

    @NonNull
    @Override
    public String getTimecode() {
        return this.getStartTimecode();
    }

    public int getDurationMillis() {
        return this.endMillis - this.startMillis;
    }

    public boolean isActive(long songTimeMillis) {
        return songTimeMillis >= this.startMillis && songTimeMillis < this.endMillis;
    }

    @NonNull
    public WonderEffect copy() {
        WonderEffect copy = new WonderEffect(
            this.startMillis, this.endMillis, this.presetId, this.spec, this.params, this.anchor);
        copy.distance = this.distance;
        copy.height = this.height;
        copy.side = this.side;
        copy.scale = this.scale;
        copy.approach = this.approach;
        copy.turn = this.turn;
        copy.thick = this.thick;
        copy.approachDir = this.approachDir;
        copy.color = this.color;
        copy.fixedLocation = this.fixedLocation == null ? null : this.fixedLocation.clone();
        return copy;
    }

    public static final String[] APPROACH_DIRS = {
        "Навстречу игроку", "Прямо к виду игрока", "Снизу вверх", "Сверху вниз", "Слева", "Справа"
    };

    @NonNull
    public String approachDirName() {
        return APPROACH_DIRS[Math.max(0, Math.min(APPROACH_DIRS.length - 1, this.approachDir))];
    }

    /** Короткое человекочитаемое имя для строки списка. */
    @NonNull
    public String getDisplayName(String locale) {
        WonderPreset preset = WonderPresets.byId(this.presetId);
        if (preset != null) return preset.getDisplay(PlayerLang.DEFAULT_LOCALE);
        String trimmed = this.spec.trim();
        if (trimmed.toLowerCase().startsWith("text:")) {
            String words = trimmed.substring(5);
            int at = words.indexOf('@');
            if (at > 0) words = words.substring(0, at);
            words = words.trim().replace('_', ' ');
            return Lang.raw(locale, "auto.wonder_effect.get_display_name.1") + (words.length() > 16 ? words.substring(0, 16) + "…" : words);
        }
        return Lang.raw(locale, "auto.wonder_effect.get_display_name.2");
    }

    // ------------------------------------------------------------- сохранение

    private static String enc(@Nullable String value) {
        if (value == null || value.isEmpty()) return "-";
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    private static String dec(@Nullable String value) {
        if (value == null || value.isEmpty() || value.equals("-")) return null;
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Формулы и параметры содержат пробелы, поэтому они уезжают в base64:
     * строка настроек остаётся одной строкой и переживает любую правку yml руками.
     */
    @NonNull
    public String serialize() {
        return this.startMillis
            + " " + this.endMillis
            + " " + this.anchor.name()
            + " " + this.distance
            + " " + this.height
            + " " + this.side
            + " " + this.scale
            + " " + (this.presetId.isEmpty() ? "-" : this.presetId)
            + " " + enc(this.spec)
            + " " + enc(this.params)
            + " " + enc(this.color)
            + " " + (this.fixedLocation == null ? "-" : ConfigUtils.serializeLocation(false, this.fixedLocation))
            + " " + this.approach
            + " " + (Double.isNaN(this.turn) ? "auto" : String.valueOf(this.turn))
            + " " + this.thick + " " + this.approachDir;
    }

    @Nullable
    public static WonderEffect deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] a = input.trim().split(" ");
        if (a.length < 11) return null;
        try {
            String spec = dec(a[8]);
            if (spec == null) return null;
            String params = dec(a[9]);
            WonderEffect effect = new WonderEffect(
                Integer.parseInt(a[0]),
                Integer.parseInt(a[1]),
                a[7].equals("-") ? "" : a[7],
                spec,
                params == null ? "" : params,
                WonderAnchor.byName(a[2], WonderAnchor.AHEAD));
            effect.distance = Double.parseDouble(a[3]);
            effect.height = Double.parseDouble(a[4]);
            effect.side = Double.parseDouble(a[5]);
            effect.scale = Double.parseDouble(a[6]);
            effect.color = dec(a[10]);
            if (a.length > 11 && !a[11].equals("-")) {
                effect.fixedLocation = ConfigUtils.parseLocation(false, a[11]);
            }
            if (a.length > 12) effect.approach = Double.parseDouble(a[12]);
            if (a.length > 13) {
                effect.turn = a[13].equals("auto") ? Double.NaN : Double.parseDouble(a[13]);
            }
            if (a.length > 15) {
                effect.thick = Integer.parseInt(a[14]);
                effect.approachDir = Integer.parseInt(a[15]);
            }
            return effect;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
