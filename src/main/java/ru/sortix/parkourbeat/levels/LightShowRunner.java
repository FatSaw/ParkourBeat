package ru.sortix.parkourbeat.levels;

import lombok.NonNull;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.settings.BiomeZone;
import ru.sortix.parkourbeat.levels.settings.BossBarCue;
import ru.sortix.parkourbeat.levels.settings.FlashCue;
import ru.sortix.parkourbeat.levels.settings.LevelBossBarColor;
import ru.sortix.parkourbeat.levels.settings.LevelWeather;
import ru.sortix.parkourbeat.levels.settings.LightShowCue;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.settings.SkyCycleCue;
import ru.sortix.parkourbeat.levels.settings.SkyType;
import ru.sortix.parkourbeat.levels.settings.WeatherCue;
import ru.sortix.parkourbeat.player.SkyTimeManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Drives the sky, the weather and the boss bar colour of a single player along the song
 * timeline. The time packet is pushed every tick, so a transition is a real gradient rather
 * than the once per second steps the vanilla broadcast would produce.
 */
public class LightShowRunner {
    private static final int ROLLBACK_TICKS = 40;
    private static final int KEEP_ALIVE_TICKS = 20;
    private static final int WEATHER_KEEP_ALIVE_TICKS = 60;
    private static final int FLASH_AMPLIFIER = 1;

    private final @NonNull SkyTimeManager skyTimeManager;
    private final @NonNull Player player;
    private final @NonNull LightShowSettings settings;
    private final @NonNull Consumer<LevelBossBarColor> bossBarColorConsumer;

    private final List<LightShowCue> skyCues = new ArrayList<>();
    private final List<SkyType> skyCueSources = new ArrayList<>();
    private final List<BossBarCue> bossBarCues = new ArrayList<>();
    private final List<SkyCycleCue> skyCycleCues = new ArrayList<>();
    private final List<FlashCue> flashCues = new ArrayList<>();
    private final List<WeatherCue> weatherCues = new ArrayList<>();
    private final List<BiomeZone> biomeZones = new ArrayList<>();

    private @NonNull Mode mode = Mode.IDLE;
    private long currentTime;

    private long rollbackFrom;
    private @NonNull SkyType rollbackSourceSky = SkyType.DEFAULT;
    private int rollbackTicksLeft;

    private @NonNull SkyType currentSky = SkyType.DEFAULT;
    private @Nullable Boolean appliedNightVision = null;
    private @Nullable WeatherType appliedWeather = null;
    private @Nullable LevelBossBarColor appliedBossBarColor = null;
    private boolean flashing = false;
    private long lastSentTime = Long.MIN_VALUE;
    private int ticksSinceSend = 0;
    private int ticksSinceWeather = 0;

    private double timePushTarget = 0.0D;
    private double timePushCurrent = 0.0D;

    /**
     * Called on each player jump inside a TIME_PUSH zone. Adds another push that the tick loop
     * eases toward, so the sky glides forward instead of snapping.
     */
    public void addTimePush(long amount) {
        this.timePushTarget += amount;
    }

    public LightShowRunner(@NonNull ParkourBeat plugin,
                           @NonNull Player player,
                           @NonNull LightShowSettings settings,
                           @NonNull Consumer<LevelBossBarColor> bossBarColorConsumer
    ) {
        this.skyTimeManager = plugin.get(SkyTimeManager.class);
        this.player = player;
        this.settings = settings;
        this.bossBarColorConsumer = bossBarColorConsumer;
        this.currentTime = settings.getBaseSky().getPlayerTime();
    }

    public void snapToBase() {
        SkyType baseSky = this.settings.getBaseSky();
        this.mode = Mode.IDLE;
        this.clearTimeline();
        this.stopFlashing();
        this.timePushTarget = 0.0D;
        this.timePushCurrent = 0.0D;
        this.currentTime = baseSky.getPlayerTime();
        this.applyState(this.currentTime, baseSky, this.settings.getBaseWeather());
        this.applyBossBarColor(null);
    }

    public void startShow() {
        this.snapToBase();

        this.settings.sort();
        SkyType previousSky = this.settings.getBaseSky();
        for (LightShowCue cue : this.settings.getSkyCues()) {
            this.skyCues.add(cue);
            this.skyCueSources.add(previousSky);
            previousSky = cue.getSky();
        }
        this.bossBarCues.addAll(this.settings.getBossBarCues());
        this.skyCycleCues.addAll(this.settings.getSkyCycleCues());
        this.flashCues.addAll(this.settings.getFlashCues());
        this.weatherCues.addAll(this.settings.getWeatherCues());
        this.biomeZones.addAll(this.settings.getBiomeZones());

        this.mode = Mode.SHOW;
    }

    /**
     * Called when the run ends for any reason. Always a smooth fade back, never a jump.
     */
    public void rollbackToBase() {
        if (this.mode == Mode.IDLE) return;

        this.rollbackFrom = this.currentTime;
        this.rollbackSourceSky = this.currentSky;
        this.rollbackTicksLeft = ROLLBACK_TICKS;
        this.mode = Mode.ROLLBACK;

        this.clearTimeline();
        this.stopFlashing();
        this.applyBossBarColor(null);
    }

    public void shutdown() {
        this.mode = Mode.IDLE;
        this.clearTimeline();
        this.stopFlashing();
        this.appliedNightVision = null;
        this.appliedWeather = null;
        this.appliedBossBarColor = null;
        this.lastSentTime = Long.MIN_VALUE;
        this.applyBossBarColor(null);
        SkyType.reset(this.player);
        this.skyTimeManager.unfreeze(this.player);
    }

    private void clearTimeline() {
        this.skyCues.clear();
        this.skyCueSources.clear();
        this.bossBarCues.clear();
        this.skyCycleCues.clear();
        this.flashCues.clear();
        this.weatherCues.clear();
        this.biomeZones.clear();
    }

    public void tick(long songTimeMillis) {
        if (!this.player.isOnline()) return;

        switch (this.mode) {
            case SHOW -> this.tickShow(songTimeMillis);
            case ROLLBACK -> this.tickRollback();
            case IDLE -> this.resendIfNeeded();
        }
    }

    private void tickShow(long songTimeMillis) {
        SkyType baseSky = this.settings.getBaseSky();

        SkyType sourceSky = baseSky;
        SkyType targetSky = baseSky;
        double progress = 1.0D;

        for (int index = this.skyCues.size() - 1; index >= 0; index--) {
            LightShowCue cue = this.skyCues.get(index);
            if (cue.getStartMillis() > songTimeMillis) continue;
            sourceSky = this.skyCueSources.get(index);
            targetSky = cue.getSky();
            progress = cue.getProgress(songTimeMillis);
            break;
        }

        SkyType dominant = progress >= 0.5D ? targetSky : sourceSky;
        long time = SkyType.interpolateTime(
            sourceSky.getPlayerTime(), targetSky.getPlayerTime(), progress);

        SkyCycleCue activeCycle = null;
        for (SkyCycleCue cue : this.skyCycleCues) {
            if (cue.isActive(songTimeMillis)) activeCycle = cue;
        }
        boolean explicitTimePoint = false;
        if (activeCycle != null) {
            time = activeCycle.getSkyTime(songTimeMillis);
            explicitTimePoint = true;
        }

        LevelWeather weather = this.settings.getBaseWeather();
        for (int index = this.weatherCues.size() - 1; index >= 0; index--) {
            WeatherCue cue = this.weatherCues.get(index);
            if (cue.getTimeMillis() > songTimeMillis) continue;
            weather = cue.getWeather();
            break;
        }

        for (BiomeZone zone : this.biomeZones) {
            if (!zone.contains(songTimeMillis)) continue;
            if (zone.isForceRain()) weather = LevelWeather.RAIN;
            Long zoneTime = zone.getSkyTime().getPlayerTime();
            if (zoneTime != null) {
                time = zoneTime;
                explicitTimePoint = true;
            }
        }

        this.currentTime = time;
        this.easeTimePush();
        // Time push only nudges the sky where the author did not pin the time with a
        // sky cycle or a biome zone. Those points always win, so they never drift.
        long shownTime = explicitTimePoint ? time : time + Math.round(this.timePushCurrent);
        this.applyState(shownTime, dominant, weather);

        FlashCue activeFlash = null;
        for (FlashCue cue : this.flashCues) {
            if (cue.isActive(songTimeMillis)) activeFlash = cue;
        }
        this.tickFlash(activeFlash);

        LevelBossBarColor barColor = null;
        for (int index = this.bossBarCues.size() - 1; index >= 0; index--) {
            BossBarCue cue = this.bossBarCues.get(index);
            if (cue.getTimeMillis() > songTimeMillis) continue;
            barColor = cue.getColor();
            break;
        }
        this.applyBossBarColor(barColor);
    }

    private void tickRollback() {
        SkyType baseSky = this.settings.getBaseSky();

        if (this.rollbackTicksLeft <= 0) {
            this.currentTime = baseSky.getPlayerTime();
            this.applyState(this.currentTime, baseSky, this.settings.getBaseWeather());
            this.mode = Mode.IDLE;
            return;
        }

        this.rollbackTicksLeft--;
        double progress = 1.0D - ((double) this.rollbackTicksLeft / (double) ROLLBACK_TICKS);

        this.currentTime = SkyType.interpolateTime(this.rollbackFrom, baseSky.getPlayerTime(), progress);
        SkyType dominant = progress >= 0.5D ? baseSky : this.rollbackSourceSky;
        this.applyState(this.currentTime, dominant, this.settings.getBaseWeather());
    }

    private void resendIfNeeded() {
        this.easeTimePush();

        if (this.timePushCurrent > 0.5D) {
            // While a push is settling, send every tick so the glide is smooth.
            long shown = this.currentTime + Math.round(this.timePushCurrent);
            this.skyTimeManager.freeze(this.player, shown);
            this.ticksSinceSend = 0;
            return;
        }

        this.ticksSinceSend++;
        if (this.ticksSinceSend < KEEP_ALIVE_TICKS) return;
        this.ticksSinceSend = 0;
        this.skyTimeManager.freeze(this.player, this.currentTime);
    }

    /**
     * Eases the applied push toward the accumulated target by a fixed fraction each tick.
     */
    private void easeTimePush() {
        double diff = this.timePushTarget - this.timePushCurrent;
        if (Math.abs(diff) < 0.5D) {
            this.timePushCurrent = this.timePushTarget;
            return;
        }
        this.timePushCurrent += diff * 0.15D;
    }

    private void tickFlash(@Nullable FlashCue cue) {
        if (cue == null) {
            this.stopFlashing();
            return;
        }

        this.flashing = true;

        int durationTicks = cue.getSpeed().getDurationTicks();
        int applications = cue.getSpeed().getApplicationsPerTick();

        if (applications <= 1) {
            // Renew only once the client is about to lose the effect
            PotionEffect current = this.player.getPotionEffect(PotionEffectType.NIGHT_VISION);
            if (current != null && current.getAmplifier() >= FLASH_AMPLIFIER && current.getDuration() > 1) return;
            this.player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION, durationTicks, FLASH_AMPLIFIER, true, false, false));
            return;
        }

        for (int index = 0; index < applications; index++) {
            this.player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            this.player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION, durationTicks, FLASH_AMPLIFIER, true, false, false));
        }
    }

    private void stopFlashing() {
        if (!this.flashing) return;
        this.flashing = false;
        this.player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        this.appliedNightVision = null;
    }

    private void applyState(long playerTime, @NonNull SkyType dominant, @NonNull LevelWeather weather) {
        this.currentSky = dominant;

        WeatherType weatherType = weather.getWeatherType();
        if (weatherType == null) weatherType = dominant.getWeather();

        this.ticksSinceSend++;
        if (this.lastSentTime != playerTime || this.ticksSinceSend >= KEEP_ALIVE_TICKS) {
            this.skyTimeManager.freeze(this.player, playerTime);
            this.lastSentTime = playerTime;
            this.ticksSinceSend = 0;
        }
        if (this.appliedWeather != weatherType) {
            this.player.setPlayerWeather(weatherType);
            this.skyTimeManager.sendWeather(this.player, weatherType == WeatherType.DOWNFALL);
            this.appliedWeather = weatherType;
            this.ticksSinceWeather = 0;
        } else if (++this.ticksSinceWeather >= WEATHER_KEEP_ALIVE_TICKS) {
            this.skyTimeManager.sendWeather(this.player, weatherType == WeatherType.DOWNFALL);
            this.ticksSinceWeather = 0;
        }
        if (this.flashing) return;
        if (this.appliedNightVision == null || this.appliedNightVision != dominant.isNightVision()) {
            SkyType.setNightVision(this.player, dominant.isNightVision());
            this.appliedNightVision = dominant.isNightVision();
        }
    }

    private void applyBossBarColor(@Nullable LevelBossBarColor barColor) {
        if (this.appliedBossBarColor == barColor) return;
        this.appliedBossBarColor = barColor;
        this.bossBarColorConsumer.accept(barColor);
    }

    private enum Mode {
        IDLE,
        SHOW,
        ROLLBACK,
    }
}
