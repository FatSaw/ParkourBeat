package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * НАСТРОЙКИ КОНКРЕТНОЙ КАРТЫ «КОПАТЕЛЯ».
 * <p>
 * BPM здесь - не украшение, а единственный источник скорости: из него считается,
 * сколько блоков в секунду летит игрок, и из него же выводится сетка, по которой
 * строитель раскладывает руды. Поменял BPM - карта целиком переехала на новый темп,
 * при этом руды остались стоять там же, где стояли, и ритм не поехал.
 * <p>
 * Ноты лежат в map по упакованным координатам: на каждый сломанный блок нужно за
 * один такт понять, нота это или декор, а перебирать список из пары тысяч руд на
 * каждом клике нельзя.
 */
@Getter
public class DiggerLevelSettings {

    public static final double MIN_BPM = 40.0D;
    public static final double MAX_BPM = 300.0D;
    public static final int MAX_NOTES = 4096;

    /** Взмах: самое близкое к звуку разреза из ванильных. */
    /**
     * Звук попадания по умолчанию.
     * <p>
     * Критический удар, а не размах: он короче, резче и звучит ровно как удар по ноте
     * в ритм-играх, тогда как размах - это протяжный шорох, который на плотной карте
     * сливается сам с собой.
     */
    public static final String DEFAULT_HIT_SOUND = "entity.player.attack.crit";
    public static final float DEFAULT_HIT_PITCH = 1.2f;

    public static final double MIN_HARDNESS = 1.0D;
    public static final double MAX_HARDNESS = 10.0D;

    /** Сколько секунд руда видна на самой мягкой жёсткости. */
    private static final double SOFT_REACTION_SECONDS = 3.2D;
    /** И сколько - на самой жёсткой. */
    private static final double HARD_REACTION_SECONDS = 0.7D;

    /** Темп трека. Из него считается вообще всё остальное. */
    private double bpm = DiggerTuning.DEFAULT_BPM;

    /**
     * Личный множитель скорости карты, 0.5..2.0.
     * <p>
     * Нужен для случаев, когда темп определён верно, но играть на нём хочется
     * быстрее или медленнее: например, трек на 85 BPM ощущается вяло, и карта
     * ставится на удвоенную скорость.
     */
    private double speedMultiplier = 1.0D;

    /** Смещение первой доли трека, мс. Обычно берётся из анализа, правится руками. */
    private int firstBeatOffsetMillis = 0;

    private int tunnelWidth = DiggerTuning.DEFAULT_TUNNEL_WIDTH;
    private int tunnelHeight = DiggerTuning.DEFAULT_TUNNEL_HEIGHT;

    /**
     * Кирка по умолчанию на весь уровень. Отдельные куски трека могут перебивать её
     * через кьюс светового шоу - там уже своя кирка на отрезок времени.
     */
    @Setter
    private @NonNull Material pickaxe = Material.NETHERITE_PICKAXE;

    /**
     * ЗВУК ПОПАДАНИЯ, ВЫБРАННЫЙ СТРОИТЕЛЕМ.
     * <p>
     * По умолчанию - один короткий взмах на все руды. Именно один: на плотном треке
     * десять разных тембров превращаются в кашу, и слышно становится хуже, а не лучше.
     * <p>
     * null - у каждой руды свой собственный звук: алмаз звенит, редстоун бьёт басом,
     * кварц щёлкает. Годится для редких, «мелодичных» карт.
     */
    private @Nullable String hitSoundKey = DEFAULT_HIT_SOUND;
    private float hitSoundPitch = DEFAULT_HIT_PITCH;

    /** Координаты центра тоннеля на старте. Заполняются при первой постановке руды. */
    private @Nullable Double originX = null;
    private @Nullable Double originY = null;
    private @Nullable Double originZ = null;

    private final Map<Long, DiggerNote> notes = new LinkedHashMap<>();

    // ==================== ТЕМП И СКОРОСТЬ ====================

    public void setBpm(double bpm) {
        if (Double.isNaN(bpm) || Double.isInfinite(bpm)) return;
        this.bpm = Math.max(MIN_BPM, Math.min(MAX_BPM, bpm));
    }

    public void setSpeedMultiplier(double multiplier) {
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier)) return;
        this.speedMultiplier = Math.max(0.5D, Math.min(2.0D, multiplier));
    }

    public void setFirstBeatOffsetMillis(int millis) {
        this.firstBeatOffsetMillis = Math.max(-5000, Math.min(5000, millis));
    }

    /** Реальная скорость полёта, блоков в секунду. */
    public double resolveSpeed() {
        double speed = DiggerTuning.speedFromBpm(this.bpm) * this.speedMultiplier;
        return Math.max(DiggerTuning.MIN_SPEED, Math.min(DiggerTuning.MAX_SPEED, speed));
    }

    /** Сколько блоков занимает одна доля на этой карте. */
    public double blocksPerBeat() {
        return this.resolveSpeed() * DiggerTuning.beatMillis(this.bpm) / 1000.0D;
    }

    /**
     * Момент, когда игрок поравняется с точкой на расстоянии {@code distance} от старта.
     * Смещение первой доли уже учтено.
     */
    public long millisAt(double distance) {
        // Через профиль, а не делением на скорость: при зонах скорости деление врёт
        // ровно настолько, насколько зоны отклонились от базовой, и таймкод руды в
        // редакторе разошёлся бы с тем, что покажет забег.
        return Math.round(this.speedProfile().millisAt(distance)) + this.firstBeatOffsetMillis;
    }

    /**
     * ЖЁСТКОСТЬ КАРТЫ.
     * <p>
     * Руды не стоят в тоннеле изначально - они появляются перед игроком на ходу.
     * Жёсткость решает, насколько заранее: на единице руда видна больше трёх секунд и
     * карта читается с листа, на десятке - меньше секунды, и пройти её можно только
     * зная наизусть.
     * <p>
     * Считается в СЕКУНДАХ, а не в блоках, специально: иначе на быстром треке та же
     * дистанция давала бы вдвое меньше времени на реакцию, и одна и та же цифра
     * означала бы разную сложность на разных картах.
     * <p>
     * Значение дробное: разница между 2.3 и 2.5 на плотной карте слышна, а целые
     * ступени слишком грубые, чтобы карту можно было довести.
     */
    private double hardness = 2.359D;

    public void setHardness(double hardness) {
        if (Double.isNaN(hardness) || Double.isInfinite(hardness)) return;
        this.hardness = Math.max(MIN_HARDNESS, Math.min(MAX_HARDNESS, hardness));
    }

    /** Сколько секунд игрок видит руду до того, как поравняется с ней. */
    public double reactionSeconds() {
        double span = (this.hardness - MIN_HARDNESS) / (MAX_HARDNESS - MIN_HARDNESS);
        return SOFT_REACTION_SECONDS + (HARD_REACTION_SECONDS - SOFT_REACTION_SECONDS) * span;
    }

    /** На каком расстоянии перед игроком руда появляется, блоков. */
    public double revealDistance() {
        return this.resolveSpeed() * this.reactionSeconds();
    }

    /** Словесное описание жёсткости - строителю цифра сама по себе ничего не говорит. */
    @NonNull
    public String hardnessTitle() {
        if (this.hardness <= 2.0D) return "&aчитается с листа";
        if (this.hardness <= 4.0D) return "&2спокойно";
        if (this.hardness <= 6.0D) return "&eплотно";
        if (this.hardness <= 8.0D) return "&6жёстко";
        return "&cтолько наизусть";
    }

    public void setHitSound(@Nullable String key, float pitch) {
        this.hitSoundKey = key == null || key.trim().isEmpty() ? null : key.trim();
        this.hitSoundPitch = Math.max(0.5f, Math.min(2.0f, pitch));
    }

    public boolean hasCustomHitSound() {
        return this.hitSoundKey != null;
    }

    // ==================== ТОННЕЛЬ ====================

    public void setTunnelWidth(int width) {
        this.tunnelWidth = DiggerTuning.clampTunnel(width);
    }

    public void setTunnelHeight(int height) {
        this.tunnelHeight = DiggerTuning.clampTunnel(height);
    }

    public boolean hasOrigin() {
        return this.originX != null && this.originY != null && this.originZ != null;
    }

    @Nullable
    public Location getOrigin(@Nullable World world) {
        if (world == null || !this.hasOrigin()) return null;
        return new Location(world, this.originX, this.originY, this.originZ);
    }

    public void setOrigin(@Nullable Location location) {
        if (location == null) {
            this.originX = null;
            this.originY = null;
            this.originZ = null;
            return;
        }
        this.originX = location.getBlockX() + 0.5D;
        this.originY = location.getY();
        this.originZ = location.getBlockZ() + 0.5D;
    }

    // ==================== НОТЫ ====================

    @NonNull
    public Collection<DiggerNote> getAllNotes() {
        return Collections.unmodifiableCollection(this.notes.values());
    }

    public int getNotesCount() {
        return this.notes.size();
    }

    @Nullable
    public DiggerNote getNoteAt(int x, int y, int z) {
        return this.notes.get(DiggerNote.key(x, y, z));
    }

    public boolean isNote(int x, int y, int z) {
        return this.notes.containsKey(DiggerNote.key(x, y, z));
    }

    /**
     * @return false, если карта уже упёрлась в потолок по числу нот
     */
    public boolean addNote(@NonNull DiggerNote note) {
        if (this.notes.size() >= MAX_NOTES && !this.notes.containsKey(note.key())) return false;
        this.notes.put(note.key(), note);
        return true;
    }

    @Nullable
    public DiggerNote removeNote(int x, int y, int z) {
        return this.notes.remove(DiggerNote.key(x, y, z));
    }

    /**
     * Убрать все ноты из карты.
     * <p>
     * Блоки в мире тоже сносятся: иначе после очистки тоннель остаётся забит рудой,
     * которая уже ничего не значит, и разбирать её приходится руками.
     *
     * @param world мир уровня; null - только список, блоки не трогать
     */
    public void clearNotes(@Nullable org.bukkit.World world) {
        if (world != null) {
            for (DiggerNote note : this.notes.values()) {
                org.bukkit.block.Block block = world.getBlockAt(note.getX(), note.getY(), note.getZ());
                if (block.getType() == note.getOre().getMaterial()) {
                    block.setType(org.bukkit.Material.AIR, false);
                }
            }
        }
        this.notes.clear();
    }

    /**
     * Ноты, отсортированные по расстоянию вдоль оси уровня.
     * <p>
     * Судья идёт по карте строго вперёд и держит указатель на ближайшую неразобранную
     * ноту, поэтому порядок обязан быть честным.
     *
     * @param axisX  true, если уровень тянется по X, иначе по Z
     * @param sign   +1, если уровень идёт в плюс по оси, иначе -1
     */
    @NonNull
    public List<DiggerNote> sortedNotes(boolean axisX, int sign) {
        List<DiggerNote> result = new ArrayList<>(this.notes.values());
        result.sort((a, b) -> {
            int ca = (axisX ? a.getX() : a.getZ()) * sign;
            int cb = (axisX ? b.getX() : b.getZ()) * sign;
            if (ca != cb) return Integer.compare(ca, cb);
            if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
            return Integer.compare(axisX ? a.getZ() : a.getX(), axisX ? b.getZ() : b.getX());
        });
        return result;
    }

    // ==================== СОХРАНЕНИЕ ====================

    /**
     * ЗОНЫ СКОРОСТИ КАРТЫ.
     * <p>
     * Пустой список - карта едется одной скоростью, ровно как раньше. Ни одна старая
     * карта от появления этого поля не меняется.
     */
    private final List<DiggerSpeedZone> speedZones = new ArrayList<>();

    /** Предел на число зон: больше полусотни строитель уже не удержит в голове. */
    public static final int MAX_SPEED_ZONES = 64;

    @NonNull
    public List<DiggerSpeedZone> getSpeedZones() {
        return java.util.Collections.unmodifiableList(this.speedZones);
    }

    public int getSpeedZonesCount() {
        return this.speedZones.size();
    }

    public boolean addSpeedZone(@NonNull DiggerSpeedZone zone) {
        if (this.speedZones.size() >= MAX_SPEED_ZONES) return false;
        this.speedZones.add(zone);
        this.speedZones.sort((a, b) -> Integer.compare(a.getStartMillis(), b.getStartMillis()));
        return true;
    }

    public boolean removeSpeedZone(@NonNull DiggerSpeedZone zone) {
        return this.speedZones.remove(zone);
    }

    public int clearSpeedZones() {
        int count = this.speedZones.size();
        this.speedZones.clear();
        return count;
    }

    /** Зона, накрывающая этот момент трека, или null. */
    @Nullable
    public DiggerSpeedZone speedZoneAt(double millis) {
        for (DiggerSpeedZone zone : this.speedZones) {
            if (zone.contains(millis)) return zone;
        }
        return null;
    }

    /**
     * ЕДИНЫЙ ПРОФИЛЬ СКОРОСТИ КАРТЫ.
     * <p>
     * И разметка, и забег обязаны переводить время в расстояние ЧЕРЕЗ ЭТОТ ОБЪЕКТ, а не
     * умножением на скорость: иначе при непостоянной скорости они разойдутся, и ноты
     * окажутся не там, где их ждёт судья.
     */
    @NonNull
    public DiggerSpeedProfile speedProfile() {
        return DiggerSpeedProfile.of(this.resolveSpeed(), this.speedZones);
    }

    /**
     * Длительность трека, мс. 0 - неизвестна.
     * <p>
     * Нужна ровно для одного: забег обязан доиграть до конца музыки, а не оборваться на
     * последней ноте. В тихом аутро ударов не находится вовсе, и карта заканчивалась за
     * несколько секунд до самой песни - выглядело это как обрыв трека.
     */
    private int trackDurationMillis = 0;

    public int getTrackDurationMillis() {
        return this.trackDurationMillis;
    }

    public void setTrackDurationMillis(int trackDurationMillis) {
        this.trackDurationMillis = Math.max(0, trackDurationMillis);
    }

    public void write(@NonNull ConfigurationSection config, @NonNull String path) {
        config.set(path + ".bpm", this.bpm);
        config.set(path + ".speed_multiplier", this.speedMultiplier);
        config.set(path + ".first_beat_offset_millis", this.firstBeatOffsetMillis);
        config.set(path + ".tunnel_width", this.tunnelWidth);
        config.set(path + ".tunnel_height", this.tunnelHeight);
        config.set(path + ".pickaxe", this.pickaxe.name());
        config.set(path + ".hardness", this.hardness);
        config.set(path + ".hit_sound", this.hitSoundKey);
        config.set(path + ".hit_sound_pitch", this.hitSoundPitch);

        if (this.hasOrigin()) {
            config.set(path + ".origin.x", this.originX);
            config.set(path + ".origin.y", this.originY);
            config.set(path + ".origin.z", this.originZ);
        } else {
            config.set(path + ".origin", null);
        }

        List<String> raw = new ArrayList<>(this.notes.size());
        for (DiggerNote note : this.notes.values()) {
            raw.add(note.serialize());
        }
        config.set(path + ".notes", raw);

        List<String> zones = new ArrayList<>(this.speedZones.size());
        for (DiggerSpeedZone zone : this.speedZones) zones.add(zone.serialize());
        config.set(path + ".speed_zones", zones);
        config.set(path + ".track_duration_millis", this.trackDurationMillis);
    }

    @NonNull
    public static DiggerLevelSettings read(@NonNull ConfigurationSection config, @NonNull String path) {
        DiggerLevelSettings result = new DiggerLevelSettings();
        result.setBpm(config.getDouble(path + ".bpm", DiggerTuning.DEFAULT_BPM));
        result.setSpeedMultiplier(config.getDouble(path + ".speed_multiplier", 1.0D));
        result.setFirstBeatOffsetMillis(config.getInt(path + ".first_beat_offset_millis", 0));
        result.setTunnelWidth(config.getInt(path + ".tunnel_width", DiggerTuning.DEFAULT_TUNNEL_WIDTH));
        result.setTunnelHeight(config.getInt(path + ".tunnel_height", DiggerTuning.DEFAULT_TUNNEL_HEIGHT));

        Material pickaxe = Material.matchMaterial(config.getString(path + ".pickaxe", "NETHERITE_PICKAXE"));
        if (pickaxe != null && DiggerPickaxe.isSupported(pickaxe)) result.setPickaxe(pickaxe);

        result.setHardness(config.getDouble(path + ".hardness", 2.359D));
        result.setTrackDurationMillis(config.getInt(path + ".track_duration_millis", 0));
        result.setHitSound(config.getString(path + ".hit_sound", DEFAULT_HIT_SOUND),
            (float) config.getDouble(path + ".hit_sound_pitch", DEFAULT_HIT_PITCH));

        if (config.contains(path + ".origin.x")) {
            result.originX = config.getDouble(path + ".origin.x");
            result.originY = config.getDouble(path + ".origin.y");
            result.originZ = config.getDouble(path + ".origin.z");
        }

        for (String raw : config.getStringList(path + ".notes")) {
            DiggerNote note = DiggerNote.deserialize(raw);
            if (note != null) result.notes.put(note.key(), note);
        }

        for (String raw : config.getStringList(path + ".speed_zones")) {
            DiggerSpeedZone zone = DiggerSpeedZone.deserialize(raw);
            if (zone != null) result.speedZones.add(zone);
        }
        result.speedZones.sort((a, b) -> Integer.compare(a.getStartMillis(), b.getStartMillis()));

        return result;
    }
}
