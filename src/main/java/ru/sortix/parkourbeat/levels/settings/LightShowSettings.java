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
    private final List<WeatherCue> weatherCues = new ArrayList<>();
    private final List<BiomeZone> biomeZones = new ArrayList<>();
    private final List<JumpZone> jumpZones = new ArrayList<>();
    private final List<ParticleColorCue> particleColorCues = new ArrayList<>();
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

    public boolean removeParticleColorCue(@NonNull ParticleColorCue cue) {
        return this.particleColorCues.remove(cue);
    }

    public boolean removeSkyCue(@NonNull LightShowCue cue) {
        return this.skyCues.remove(cue);
    }

    public boolean removeBossBarCue(@NonNull BossBarCue cue) {
        return this.bossBarCues.remove(cue);
    }

    public boolean removeSkyCycleCue(@NonNull SkyCycleCue cue) {
        return this.skyCycleCues.remove(cue);
    }

    public boolean removeFlashCue(@NonNull FlashCue cue) {
        return this.flashCues.remove(cue);
    }

    public boolean removeWeatherCue(@NonNull WeatherCue cue) {
        return this.weatherCues.remove(cue);
    }

    public boolean removeBiomeZone(@NonNull BiomeZone zone) {
        return this.biomeZones.remove(zone);
    }

    public boolean removeJumpZone(@NonNull JumpZone zone) {
        return this.jumpZones.remove(zone);
    }

    public void sort() {
        Comparator<LightShowElement> byStart = Comparator.comparingInt(LightShowElement::getStartMillis);
        this.skyCues.sort(byStart);
        this.bossBarCues.sort(byStart);
        this.skyCycleCues.sort(byStart);
        this.flashCues.sort(byStart);
        this.weatherCues.sort(byStart);
        this.biomeZones.sort(byStart);
        this.jumpZones.sort(byStart);
        this.particleColorCues.sort(byStart);
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
        for (WeatherCue cue : this.weatherCues) result.weatherCues.add(cue.copy());
        for (BiomeZone zone : this.biomeZones) result.biomeZones.add(zone.copy());
        for (JumpZone zone : this.jumpZones) result.jumpZones.add(zone.copy());
        for (ParticleColorCue cue : this.particleColorCues) result.particleColorCues.add(cue.copy());
        result.defaultJumpTrigger = this.defaultJumpTrigger == null ? null : this.defaultJumpTrigger.copy();
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
        writeList(section, "weather_cues", this.weatherCues, WeatherCue::serialize);
        writeList(section, "biome_zones", this.biomeZones, BiomeZone::serialize);
        writeList(section, "jump_zones", this.jumpZones, JumpZone::serialize);
        writeList(section, "particle_color_cues", this.particleColorCues, ParticleColorCue::serialize);
        section.set("default_jump_trigger", this.defaultJumpTrigger == null ? null : this.defaultJumpTrigger.serialize());
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
        readList(section, "weather_cues", result.weatherCues, WeatherCue::deserialize);
        readList(section, "biome_zones", result.biomeZones, BiomeZone::deserialize);
        readList(section, "jump_zones", result.jumpZones, JumpZone::deserialize);
        readList(section, "particle_color_cues", result.particleColorCues, ParticleColorCue::deserialize);
        result.defaultJumpTrigger = JumpZone.deserialize(section.getString("default_jump_trigger"));
        result.winParticle = CompletionParticle.byName(section.getString("win_particle"), CompletionParticle.NONE);
        result.loseParticle = CompletionParticle.byName(section.getString("lose_particle"), CompletionParticle.NONE);
        result.fallParticle = CompletionParticle.byName(section.getString("fall_particle"), CompletionParticle.NONE);

        result.sort();
        return result;
    }
}
