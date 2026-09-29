package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.configuration.ConfigurationSection;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class LightShowSettings {
    public static final int MAX_CUES = 128;
    /**
     * Больше пяти чекпоинтов не даём намеренно: каждый чекпоинт — это ещё один
     * отдельный ogg-файл внутри ресурспака уровня, а пак должен оставаться лёгким.
     */
    public static final int MAX_CHECKPOINTS = 5;

    /**
     * СЧЁТЧИК ПРАВОК СВЕТОВОГО ШОУ.
     * <p>
     * Предпросмотр в редакторе держит собственный запущенный показ, и удалённая
     * вставка продолжала висеть у строителя на экране до перезахода на уровень.
     * Теперь любая правка списка поднимает счётчик, редактор это замечает и
     * пересобирает предпросмотр на следующем тике.
     */
    private int revision = 0;

    public int getRevision() {
        return this.revision;
    }

    public void bumpRevision() {
        this.revision++;
    }

    @Getter @Setter
    private @Nullable Integer defaultDeathY = null;

    @Getter
    private @NonNull SkyType baseSky = SkyType.DEFAULT;
    @Getter
    @Setter
    private @NonNull LevelWeather baseWeather = LevelWeather.DEFAULT;
    @Getter
    @Setter
    private @NonNull LevelBiome levelBiome = LevelBiome.DEFAULT;

    private final List<LightShowCue> skyCues = new ArrayList<>();
    private final List<BossBarCue> bossBarCues = new ArrayList<>();
    private final List<SkyCycleCue> skyCycleCues = new ArrayList<>();
    private final List<FlashCue> flashCues = new ArrayList<>();
    private final List<PickaxeCue> pickaxeCues = new ArrayList<>();
    private final List<WeatherCue> weatherCues = new ArrayList<>();
    private final List<BiomeZone> biomeZones = new ArrayList<>();
    private final List<JumpZone> jumpZones = new ArrayList<>();
    private final List<ParticleColorCue> particleColorCues = new ArrayList<>();
    private final List<FallZone> fallZones = new ArrayList<>();
    private final List<Portal> portals = new ArrayList<>();
    private final List<AutoDoor> autoDoors = new ArrayList<>();
    private final List<HelperMarker> helperMarkers = new ArrayList<>();
    private final List<Checkpoint> checkpoints = new ArrayList<>();
    private final List<ru.sortix.parkourbeat.levels.wonder.WonderEffect> wonderEffects = new ArrayList<>();
    private final List<ru.sortix.parkourbeat.levels.lamps.LampWall> lampWalls = new ArrayList<>();
    @Getter
    @Setter
    private @Nullable JumpZone defaultJumpTrigger = null;
    @Getter @Setter
    private @NonNull CompletionParticle winParticle = CompletionParticle.NONE;
    @Getter @Setter
    private @NonNull CompletionParticle loseParticle = CompletionParticle.NONE;
    @Getter @Setter
    private @NonNull CompletionParticle fallParticle = CompletionParticle.NONE;

    public void setBaseSky(@NonNull SkyType baseSky) {
        this.baseSky = baseSky;
    }

    @NonNull
    public List<LightShowCue> getSkyCues() {
        return Collections.unmodifiableList(this.skyCues);
    }

    @NonNull
    public List<BossBarCue> getBossBarCues() {
        return Collections.unmodifiableList(this.bossBarCues);
    }

    @NonNull
    public List<SkyCycleCue> getSkyCycleCues() {
        return Collections.unmodifiableList(this.skyCycleCues);
    }

    @NonNull
    public List<PickaxeCue> getPickaxeCues() {
        return Collections.unmodifiableList(this.pickaxeCues);
    }

    /** Кирка, которая должна быть в руке в этот момент трека, или null - тогда берётся кирка уровня. */
    @Nullable
    public PickaxeCue getPickaxeCueAt(long songTimeMillis) {
        for (PickaxeCue cue : this.pickaxeCues) {
            if (cue.isActive(songTimeMillis)) return cue;
        }
        return null;
    }

    @NonNull
    public List<FlashCue> getFlashCues() {
        return Collections.unmodifiableList(this.flashCues);
    }

    @NonNull
    public List<WeatherCue> getWeatherCues() {
        return Collections.unmodifiableList(this.weatherCues);
    }

    @NonNull
    public List<BiomeZone> getBiomeZones() {
        return Collections.unmodifiableList(this.biomeZones);
    }

    @NonNull
    public List<JumpZone> getJumpZones() {
        return Collections.unmodifiableList(this.jumpZones);
    }

    @NonNull
    public List<ParticleColorCue> getParticleColorCues() {
        return Collections.unmodifiableList(this.particleColorCues);
    }

    @NonNull
    public List<FallZone> getFallZones() {
        return Collections.unmodifiableList(this.fallZones);
    }

    @NonNull
    public List<Portal> getPortals() {
        return Collections.unmodifiableList(this.portals);
    }

    @NonNull
    public List<Checkpoint> getCheckpoints() {
        return Collections.unmodifiableList(this.checkpoints);
    }

    @NonNull
    public List<ru.sortix.parkourbeat.levels.wonder.WonderEffect> getWonderEffects() {
        return this.wonderEffects;
    }

    public int getWonderEffectsAmount() {
        return this.wonderEffects.size();
    }

    public boolean addWonderEffect(@NonNull ru.sortix.parkourbeat.levels.wonder.WonderEffect effect) {
        return this.add(this.wonderEffects, effect);
    }

    public boolean removeWonderEffect(@NonNull ru.sortix.parkourbeat.levels.wonder.WonderEffect effect) {
        this.revision++;
        return this.wonderEffects.remove(effect);
    }

    @NonNull
    public List<ru.sortix.parkourbeat.levels.lamps.LampWall> getLampWalls() {
        return this.lampWalls;
    }

    public int getLampWallsAmount() {
        return this.lampWalls.size();
    }

    public boolean addLampWall(@NonNull ru.sortix.parkourbeat.levels.lamps.LampWall wall) {
        return this.add(this.lampWalls, wall);
    }

    public boolean removeLampWall(@NonNull ru.sortix.parkourbeat.levels.lamps.LampWall wall) {
        this.revision++;
        return this.lampWalls.remove(wall);
    }

    public int getCheckpointsAmount() {
        return this.checkpoints.size();
    }

    public boolean addCheckpoint(@NonNull Checkpoint checkpoint) {
        if (this.checkpoints.size() >= MAX_CHECKPOINTS) return false;
        this.checkpoints.add(checkpoint);
        return true;
    }

    public boolean removeCheckpoint(@NonNull Checkpoint checkpoint) {
        this.revision++;
        return this.checkpoints.remove(checkpoint);
    }

    public void clearCheckpoints() {
        this.revision++;
        this.checkpoints.clear();
    }

    @NonNull
    public List<HelperMarker> getHelperMarkers() {
        return Collections.unmodifiableList(this.helperMarkers);
    }

    /**
     * Маркеры хранятся точками, а не таймкодами: строителю нужно видеть конкретное место
     * прыжка, а не линию поперёк всего уровня.
     */
    public boolean addHelperMarker(@NonNull HelperMarker marker) {
        if (this.helperMarkers.size() >= 512) return false;
        for (HelperMarker existing : this.helperMarkers) {
            if (existing.getPosition().distanceSquared(marker.getPosition()) < 0.25D) return false;
        }
        this.helperMarkers.add(marker);
        return true;
    }

    public boolean removeHelperMarker(@NonNull HelperMarker marker) {
        this.revision++;
        return this.helperMarkers.remove(marker);
    }

    public void clearHelperMarkers() {
        this.revision++;
        this.helperMarkers.clear();
    }

    @NonNull
    public List<AutoDoor> getAutoDoors() {
        return Collections.unmodifiableList(this.autoDoors);
    }

    public int getSkyCuesAmount() {
        return this.skyCues.size();
    }

    public int getBossBarCuesAmount() {
        return this.bossBarCues.size();
    }

    public int getSkyCycleCuesAmount() {
        return this.skyCycleCues.size();
    }

    public int getFlashCuesAmount() {
        return this.flashCues.size();
    }

    public int getWeatherCuesAmount() {
        return this.weatherCues.size();
    }

    public int getBiomeZonesAmount() {
        return this.biomeZones.size();
    }

    public int getJumpZonesAmount() {
        return this.jumpZones.size();
    }

    public int getParticleColorCuesAmount() {
        return this.particleColorCues.size();
    }

    public int getFallZonesAmount() {
        return this.fallZones.size();
    }

    public int getPortalsAmount() {
        return this.portals.size();
    }

    public int getAutoDoorsAmount() {
        return this.autoDoors.size();
    }

    public boolean isSkyCuesEmpty() {
        return this.skyCues.isEmpty();
    }

    public boolean isBossBarCuesEmpty() {
        return this.bossBarCues.isEmpty();
    }

    public boolean isSkyCycleCuesEmpty() {
        return this.skyCycleCues.isEmpty();
    }

    public boolean isFlashCuesEmpty() {
        return this.flashCues.isEmpty();
    }

    public boolean isWeatherCuesEmpty() {
        return this.weatherCues.isEmpty();
    }

    public boolean isBiomeZonesEmpty() {
        return this.biomeZones.isEmpty();
    }

    public boolean isJumpZonesEmpty() {
        return this.jumpZones.isEmpty();
    }

    private <T> boolean add(@NonNull List<T> list, @NonNull T element) {
        if (list.size() >= MAX_CUES) return false;
        list.add(element);
        this.revision++;
        this.sort();
        return true;
    }

    public boolean addSkyCue(@NonNull LightShowCue cue) {
        return this.add(this.skyCues, cue);
    }

    public boolean addBossBarCue(@NonNull BossBarCue cue) {
        return this.add(this.bossBarCues, cue);
    }

    public boolean addSkyCycleCue(@NonNull SkyCycleCue cue) {
        return this.add(this.skyCycleCues, cue);
    }

    public boolean addFlashCue(@NonNull FlashCue cue) {
        return this.add(this.flashCues, cue);
    }

    public boolean addPickaxeCue(@NonNull PickaxeCue cue) {
        return this.add(this.pickaxeCues, cue);
    }

    public boolean removePickaxeCue(@NonNull PickaxeCue cue) {
        this.revision++;
        return this.pickaxeCues.remove(cue);
    }

    public boolean addWeatherCue(@NonNull WeatherCue cue) {
        return this.add(this.weatherCues, cue);
    }

    public boolean addBiomeZone(@NonNull BiomeZone zone) {
        return this.add(this.biomeZones, zone);
    }

    public boolean addJumpZone(@NonNull JumpZone zone) {
        return this.add(this.jumpZones, zone);
    }

    public boolean addParticleColorCue(@NonNull ParticleColorCue cue) {
        return this.add(this.particleColorCues, cue);
    }

    public boolean addFallZone(@NonNull FallZone zone) {
        return this.add(this.fallZones, zone);
    }

    public boolean removeFallZone(@NonNull FallZone zone) {
        this.revision++;
        return this.fallZones.remove(zone);
    }

    public boolean addPortal(@NonNull Portal portal) {
        return this.add(this.portals, portal);
    }

    public boolean removePortal(@NonNull Portal portal) {
        this.revision++;
        return this.portals.remove(portal);
    }

    public boolean addAutoDoor(@NonNull AutoDoor door) {
        return this.add(this.autoDoors, door);
    }

    public boolean removeAutoDoor(@NonNull AutoDoor door) {
        this.revision++;
        return this.autoDoors.remove(door);
    }

    /**
     * Одна дверь - одна привязка. Иначе повторный клик палочкой по той же двери молча
     * плодил бы дубликаты с разными радиусами, которые дерутся за её состояние.
     */
    @Nullable
    public AutoDoor findAutoDoorAt(int x, int y, int z) {
        for (AutoDoor door : this.autoDoors) {
            if (door.isSameBlock(x, y, z)) return door;
        }
        return null;
    }

    public boolean removeParticleColorCue(@NonNull ParticleColorCue cue) {
        this.revision++;
        return this.particleColorCues.remove(cue);
    }

    public boolean removeSkyCue(@NonNull LightShowCue cue) {
        this.revision++;
        return this.skyCues.remove(cue);
    }

    public boolean removeBossBarCue(@NonNull BossBarCue cue) {
        this.revision++;
        return this.bossBarCues.remove(cue);
    }

    public boolean removeSkyCycleCue(@NonNull SkyCycleCue cue) {
        this.revision++;
        return this.skyCycleCues.remove(cue);
    }

    public boolean removeFlashCue(@NonNull FlashCue cue) {
        this.revision++;
        return this.flashCues.remove(cue);
    }

    public boolean removeWeatherCue(@NonNull WeatherCue cue) {
        this.revision++;
        return this.weatherCues.remove(cue);
    }

    public boolean removeBiomeZone(@NonNull BiomeZone zone) {
        this.revision++;
        return this.biomeZones.remove(zone);
    }

    public boolean removeJumpZone(@NonNull JumpZone zone) {
        this.revision++;
        return this.jumpZones.remove(zone);
    }

    public void sort() {
        Comparator<LightShowElement> byStart = Comparator.comparingInt(LightShowElement::getStartMillis);
        this.skyCues.sort(byStart);
        this.bossBarCues.sort(byStart);
        this.skyCycleCues.sort(byStart);
        this.flashCues.sort(byStart);
        this.pickaxeCues.sort(byStart);
        this.weatherCues.sort(byStart);
        this.biomeZones.sort(byStart);
        this.jumpZones.sort(byStart);
        this.particleColorCues.sort(byStart);
        this.fallZones.sort(byStart);
        this.wonderEffects.sort(byStart);
        this.lampWalls.sort(byStart);
    }

    /**
     * Sky the level is showing right before the given cue starts, which is what the
     * transition of that cue grows out of.
     */
    @NonNull
    public SkyType getSkyBefore(@NonNull LightShowCue cue) {
        this.sort();
        SkyType result = this.baseSky;
        for (LightShowCue current : this.skyCues) {
            if (current == cue) break;
            result = current.getSky();
        }
        return result;
    }

    @NonNull
    public LightShowSettings copy() {
        LightShowSettings result = new LightShowSettings();
        result.baseSky = this.baseSky;
        result.baseWeather = this.baseWeather;
        result.levelBiome = this.levelBiome;
        for (LightShowCue cue : this.skyCues) result.skyCues.add(cue.copy());
        for (BossBarCue cue : this.bossBarCues) result.bossBarCues.add(cue.copy());
        for (SkyCycleCue cue : this.skyCycleCues) result.skyCycleCues.add(cue.copy());
        for (FlashCue cue : this.flashCues) result.flashCues.add(cue.copy());
        for (PickaxeCue cue : this.pickaxeCues) result.pickaxeCues.add(cue.copy());
        for (WeatherCue cue : this.weatherCues) result.weatherCues.add(cue.copy());
        for (BiomeZone zone : this.biomeZones) result.biomeZones.add(zone.copy());
        for (JumpZone zone : this.jumpZones) result.jumpZones.add(zone.copy());
        for (ParticleColorCue cue : this.particleColorCues) result.particleColorCues.add(cue.copy());
        for (FallZone zone : this.fallZones) result.fallZones.add(zone.copy());
        for (Portal portal : this.portals) result.portals.add(portal.copy());
        for (AutoDoor door : this.autoDoors) result.autoDoors.add(door.copy());
        for (HelperMarker marker : this.helperMarkers) result.helperMarkers.add(marker.copy());
        for (Checkpoint checkpoint : this.checkpoints) result.checkpoints.add(checkpoint.copy());
        for (ru.sortix.parkourbeat.levels.wonder.WonderEffect effect : this.wonderEffects)
            result.wonderEffects.add(effect.copy());
        for (ru.sortix.parkourbeat.levels.lamps.LampWall wall : this.lampWalls)
            result.lampWalls.add(wall.copy());
        result.defaultJumpTrigger = this.defaultJumpTrigger == null ? null : this.defaultJumpTrigger.copy();
        result.defaultDeathY = this.defaultDeathY;
        result.winParticle = this.winParticle;
        result.loseParticle = this.loseParticle;
        result.fallParticle = this.fallParticle;
        return result;
    }

    private static <T> void writeList(@NonNull ConfigurationSection section,
                                      @NonNull String key,
                                      @NonNull List<T> list,
                                      @NonNull Function<T, String> serializer
    ) {
        List<String> serialized = new ArrayList<>(list.size());
        for (T element : list) serialized.add(serializer.apply(element));
        section.set(key, serialized);
    }

    private static <T> void readList(@NonNull ConfigurationSection section,
                                     @NonNull String key,
                                     @NonNull List<T> target,
                                     @NonNull Function<String, T> deserializer
    ) {
        for (String serialized : section.getStringList(key)) {
            T element = deserializer.apply(serialized);
            if (element == null) continue;
            if (target.size() >= MAX_CUES) break;
            target.add(element);
        }
    }

    public void write(@NonNull ConfigurationSection parentSection) {
        ConfigurationSection section = parentSection.createSection("lightshow");
        section.set("base_sky", this.baseSky.name());
        section.set("base_weather", this.baseWeather.name());
        section.set("level_biome", this.levelBiome.name());

        this.sort();

        writeList(section, "cues", this.skyCues, LightShowCue::serialize);
        writeList(section, "boss_bar_cues", this.bossBarCues, BossBarCue::serialize);
        writeList(section, "sky_cycle_cues", this.skyCycleCues, SkyCycleCue::serialize);
        writeList(section, "flash_cues", this.flashCues, FlashCue::serialize);
        writeList(section, "pickaxe_cues", this.pickaxeCues, PickaxeCue::serialize);
        writeList(section, "weather_cues", this.weatherCues, WeatherCue::serialize);
        writeList(section, "biome_zones", this.biomeZones, BiomeZone::serialize);
        writeList(section, "jump_zones", this.jumpZones, JumpZone::serialize);
        writeList(section, "particle_color_cues", this.particleColorCues, ParticleColorCue::serialize);
        writeList(section, "fall_zones", this.fallZones, FallZone::serialize);
        writeList(section, "portals", this.portals, Portal::serialize);
        writeList(section, "auto_doors", this.autoDoors, AutoDoor::serialize);
        writeList(section, "helper_markers", this.helperMarkers, HelperMarker::serialize);
        writeList(section, "checkpoints", this.checkpoints, Checkpoint::serialize);
        writeList(section, "wonder_effects", this.wonderEffects,
            ru.sortix.parkourbeat.levels.wonder.WonderEffect::serialize);
        writeList(section, "lamp_walls", this.lampWalls,
            ru.sortix.parkourbeat.levels.lamps.LampWall::serialize);
        section.set("default_jump_trigger", this.defaultJumpTrigger == null ? null : this.defaultJumpTrigger.serialize());
        if (this.defaultDeathY != null) section.set("default_death_y", this.defaultDeathY);
        section.set("win_particle", this.winParticle.name());
        section.set("lose_particle", this.loseParticle.name());
        section.set("fall_particle", this.fallParticle.name());
    }

    @NonNull
    public static LightShowSettings read(@Nullable ConfigurationSection parentSection) {
        LightShowSettings result = new LightShowSettings();
        if (parentSection == null) return result;

        ConfigurationSection section = parentSection.getConfigurationSection("lightshow");
        if (section == null) return result;

        result.baseSky = SkyType.byName(section.getString("base_sky"), SkyType.DEFAULT);
        result.baseWeather = LevelWeather.byName(section.getString("base_weather"), LevelWeather.DEFAULT);
        result.levelBiome = LevelBiome.byName(section.getString("level_biome"), LevelBiome.DEFAULT);

        readList(section, "cues", result.skyCues, LightShowCue::deserialize);
        readList(section, "boss_bar_cues", result.bossBarCues, BossBarCue::deserialize);
        readList(section, "sky_cycle_cues", result.skyCycleCues, SkyCycleCue::deserialize);
        readList(section, "flash_cues", result.flashCues, FlashCue::deserialize);
        readList(section, "pickaxe_cues", result.pickaxeCues, PickaxeCue::deserialize);
        readList(section, "weather_cues", result.weatherCues, WeatherCue::deserialize);
        readList(section, "biome_zones", result.biomeZones, BiomeZone::deserialize);
        readList(section, "jump_zones", result.jumpZones, JumpZone::deserialize);
        readList(section, "particle_color_cues", result.particleColorCues, ParticleColorCue::deserialize);
        readList(section, "fall_zones", result.fallZones, FallZone::deserialize);
        readList(section, "portals", result.portals, Portal::deserialize);
        readList(section, "auto_doors", result.autoDoors, AutoDoor::deserialize);
        readList(section, "helper_markers", result.helperMarkers, HelperMarker::deserialize);
        readList(section, "checkpoints", result.checkpoints, Checkpoint::deserialize);
        readList(section, "wonder_effects", result.wonderEffects,
            ru.sortix.parkourbeat.levels.wonder.WonderEffect::deserialize);
        readList(section, "lamp_walls", result.lampWalls,
            ru.sortix.parkourbeat.levels.lamps.LampWall::deserialize);
        while (result.checkpoints.size() > MAX_CHECKPOINTS) {
            result.checkpoints.remove(result.checkpoints.size() - 1);
        }
        result.defaultJumpTrigger = JumpZone.deserialize(section.getString("default_jump_trigger"));
        if (section.contains("default_death_y")) {
            result.setDefaultDeathY(section.getInt("default_death_y"));
        }
        result.winParticle = CompletionParticle.byName(section.getString("win_particle"), CompletionParticle.NONE);
        result.loseParticle = CompletionParticle.byName(section.getString("lose_particle"), CompletionParticle.NONE);
        result.fallParticle = CompletionParticle.byName(section.getString("fall_particle"), CompletionParticle.NONE);

        result.sort();
        return result;
    }
}
