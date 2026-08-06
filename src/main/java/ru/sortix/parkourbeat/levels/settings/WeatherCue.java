package ru.sortix.parkourbeat.levels.settings;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;

@Getter
public class WeatherCue implements LightShowElement {
    @Setter
    private @NonNull LevelWeather weather;

    private int timeMillis;

    public WeatherCue(int timeMillis, @NonNull LevelWeather weather) {
        this.weather = weather;
        this.setTimeMillis(timeMillis);
    }

    public void setTimeMillis(int timeMillis) {
        this.timeMillis = Math.max(0, Math.min(TimeUtils.MAX_TIMECODE_MILLIS, timeMillis));
    }

    @Override
    public int getStartMillis() {
        return this.timeMillis;
    }

    @Override
    public void setStartMillis(int startMillis) {
        this.setTimeMillis(startMillis);
    }

    @NonNull
    @Override
    public String getTimecode() {
        return TimeUtils.formatTimecode(this.timeMillis);
    }

    @NonNull
    public WeatherCue copy() {
        return new WeatherCue(this.timeMillis, this.weather);
    }

    @NonNull
    public String serialize() {
        return this.timeMillis + " " + this.weather.name();
    }

    @Nullable
    public static WeatherCue deserialize(@Nullable String input) {
        if (input == null) return null;
        String[] args = input.trim().split(" ");
        if (args.length != 2) return null;
        try {
            return new WeatherCue(Integer.parseInt(args[0]), LevelWeather.byName(args[1], LevelWeather.DEFAULT));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
