package ru.sortix.parkourbeat.ui.lyrics;

import lombok.NonNull;

import javax.annotation.Nullable;

/** Одна фраза шоу: [start; end) в миллисекундах трека. Хранится строкой «start|end|text». */
public final class LyricCue {
    public final long start;
    public final long end;
    public final String text;

    public LyricCue(long start, long end, @NonNull String text) {
        this.start = start;
        this.end = end;
        this.text = text;
    }

    @NonNull
    public String serialize() {
        return this.start + "|" + this.end + "|" + this.text;
    }

    @Nullable
    public static LyricCue deserialize(@NonNull String line) {
        String[] parts = line.split("\\|", 3);
        if (parts.length < 3) return null;
        try {
            return new LyricCue(Long.parseLong(parts[0]), Long.parseLong(parts[1]), parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
