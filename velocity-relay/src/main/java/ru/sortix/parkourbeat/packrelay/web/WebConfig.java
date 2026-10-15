package ru.sortix.parkourbeat.packrelay.web;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class WebConfig {
    public boolean enabled = true;
    public String bindIp = "0.0.0.0";
    public int port = 25700;
    public String publicBaseUrl = "http://127.0.0.1:25700/";
    public String musicDirectory = "plugins/amusic/Music";
    public int maxTracksPerPlayer = 8;
    public long maxTrackSizeBytes = 30L * 1024L * 1024L;
    public long tokenLifetimeMillis = 15L * 60L * 1000L;
    public boolean httpsEnabled = false;
    public String httpsKeystore = "/home/container/plugins/amusic/cert.p12";
    public String httpsPassword = "";

    public static WebConfig load(Path directory, Logger logger) {
        WebConfig config = new WebConfig();
        Path file = directory.resolve("web.properties");

        Properties properties = new Properties();
        try {
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    properties.load(in);
                }
            }
        } catch (IOException e) {
            logger.warn("Unable to read web config, using defaults", e);
        }

        config.enabled = Boolean.parseBoolean(
            properties.getProperty("enabled", String.valueOf(config.enabled)).trim());
        config.bindIp = properties.getProperty("bind-ip", config.bindIp).trim();
        config.port = parseInt(properties.getProperty("port"), config.port);
        config.publicBaseUrl = properties.getProperty("public-base-url", config.publicBaseUrl).trim();
        config.musicDirectory = properties.getProperty("music-directory", config.musicDirectory).trim();
        config.maxTracksPerPlayer = parseInt(
            properties.getProperty("max-tracks-per-player"), config.maxTracksPerPlayer);
        config.maxTrackSizeBytes = parseLong(
            properties.getProperty("max-track-size-bytes"), config.maxTrackSizeBytes);
        config.tokenLifetimeMillis = parseLong(
            properties.getProperty("token-lifetime-millis"), config.tokenLifetimeMillis);
        config.httpsEnabled = Boolean.parseBoolean(
            properties.getProperty("https-enabled", String.valueOf(config.httpsEnabled)).trim());
        config.httpsKeystore = properties.getProperty("https-keystore", config.httpsKeystore).trim();
        config.httpsPassword = properties.getProperty("https-password", config.httpsPassword);

        try {
            Files.createDirectories(directory);
            Properties out = new Properties();
            out.setProperty("enabled", String.valueOf(config.enabled));
            out.setProperty("bind-ip", config.bindIp);
            out.setProperty("port", String.valueOf(config.port));
            out.setProperty("public-base-url", config.publicBaseUrl);
            out.setProperty("music-directory", config.musicDirectory);
            out.setProperty("max-tracks-per-player", String.valueOf(config.maxTracksPerPlayer));
            out.setProperty("max-track-size-bytes", String.valueOf(config.maxTrackSizeBytes));
            out.setProperty("token-lifetime-millis", String.valueOf(config.tokenLifetimeMillis));
            out.setProperty("https-enabled", String.valueOf(config.httpsEnabled));
            out.setProperty("https-keystore", config.httpsKeystore);
            out.setProperty("https-password", config.httpsPassword);
            try (OutputStream stream = Files.newOutputStream(file)) {
                out.store(stream, "ParkourBeat web uploader");
            }
        } catch (IOException e) {
            logger.warn("Unable to write web config", e);
        }

        return config;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
