package ru.sortix.parkourbeat.packrelay;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class RelayConfig {
    public boolean relayToBackend = true;
    public boolean autoRetry = true;
    public int retryAttempts = 3;
    public long retryDelayMillis = 1500L;
    public boolean stuckWatchdog = true;
    public long stuckTimeoutMillis = 25_000L;
    public boolean patchStrictAccess = true;
    public boolean patchWaitAcception = true;
    public boolean coachDeclined = true;
    public boolean verboseLog = true;
    /** Путь к ffmpeg. По умолчанию берётся из PATH. */
    public String ffmpegPath = "ffmpeg";
    /** Путь к ffprobe: им меряется фактическая длительность готовых кусков. */
    public String ffprobePath = "ffprobe";
    /** Качество vorbis для кусков нарезки: 0 - минимум, 10 - максимум. */
    public int sliceQuality = 5;

    public static RelayConfig load(Path directory, Logger logger) {
        RelayConfig config = new RelayConfig();
        Path file = directory.resolve("config.properties");

        Properties properties = new Properties();
        try {
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    properties.load(in);
                }
            }
        } catch (IOException e) {
            logger.warn("Unable to read relay config, using defaults", e);
        }

        config.relayToBackend = bool(properties, "relay-to-backend", config.relayToBackend);
        config.autoRetry = bool(properties, "auto-retry", config.autoRetry);
        config.retryAttempts = integer(properties, "retry-attempts", config.retryAttempts);
        config.retryDelayMillis = longValue(properties, "retry-delay-millis", config.retryDelayMillis);
        config.stuckWatchdog = bool(properties, "stuck-watchdog", config.stuckWatchdog);
        config.stuckTimeoutMillis = longValue(properties, "stuck-timeout-millis", config.stuckTimeoutMillis);
        config.patchStrictAccess = bool(properties, "patch-strict-access", config.patchStrictAccess);
        config.patchWaitAcception = bool(properties, "patch-wait-acception", config.patchWaitAcception);
        config.coachDeclined = bool(properties, "coach-declined", config.coachDeclined);
        config.verboseLog = bool(properties, "verbose-log", config.verboseLog);
        config.ffmpegPath = properties.getProperty("ffmpeg-path", config.ffmpegPath).trim();
        config.ffprobePath = properties.getProperty("ffprobe-path", config.ffprobePath).trim();
        config.sliceQuality = integer(properties, "slice-quality", config.sliceQuality);

        try {
            Files.createDirectories(directory);
            Properties out = new Properties();
            out.setProperty("relay-to-backend", String.valueOf(config.relayToBackend));
            out.setProperty("auto-retry", String.valueOf(config.autoRetry));
            out.setProperty("retry-attempts", String.valueOf(config.retryAttempts));
            out.setProperty("retry-delay-millis", String.valueOf(config.retryDelayMillis));
            out.setProperty("stuck-watchdog", String.valueOf(config.stuckWatchdog));
            out.setProperty("stuck-timeout-millis", String.valueOf(config.stuckTimeoutMillis));
            out.setProperty("patch-strict-access", String.valueOf(config.patchStrictAccess));
            out.setProperty("patch-wait-acception", String.valueOf(config.patchWaitAcception));
            out.setProperty("coach-declined", String.valueOf(config.coachDeclined));
            out.setProperty("verbose-log", String.valueOf(config.verboseLog));
            out.setProperty("ffmpeg-path", config.ffmpegPath);
            out.setProperty("ffprobe-path", config.ffprobePath);
            out.setProperty("slice-quality", String.valueOf(config.sliceQuality));
            try (OutputStream stream = Files.newOutputStream(file)) {
                out.store(stream, "parkourbeatpackrelay");
            }
        } catch (IOException e) {
            logger.warn("Unable to write relay config", e);
        }

        return config;
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            String value = properties.getProperty(key);
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long longValue(Properties properties, String key, long fallback) {
        try {
            String value = properties.getProperty(key);
            return value == null ? fallback : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
