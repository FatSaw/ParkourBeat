package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * ЧТЕНИЕ РАЗБОРА ТРЕКА С ДИСКА.
 * <p>
 * Анализ звука делается вне сервера: ogg-файлы лежат на прокси, а тянуть в игровой
 * тик разбор спектра - последнее, что стоит делать. Скрипт
 * {@code digger_analyze.py} прогоняет трек и кладёт результат в
 * {@code plugins/ParkourBeat/digger_analysis/<id трека>.txt}, а плагин его просто читает.
 * <p>
 * Формат:
 * <pre>
 * bpm 128.5
 * offset 37
 * onset 480 0.92 low
 * onset 720 0.41 high
 * </pre>
 * Строки с решёткой и пустые пропускаются. Ошибка в одной строке не роняет весь файл:
 * битая строка просто игнорируется, остальное читается.
 */
public final class DiggerAnalysisStore {

    public static final String FOLDER = "digger_analysis";

    private DiggerAnalysisStore() {
    }

    @NonNull
    public static File folder(@NonNull Plugin plugin) {
        File folder = new File(plugin.getDataFolder(), FOLDER);
        if (!folder.isDirectory()) folder.mkdirs();
        return folder;
    }

    @NonNull
    public static File fileOf(@NonNull Plugin plugin, @NonNull String trackId) {
        return new File(folder(plugin), safe(trackId) + ".txt");
    }

    public static boolean exists(@NonNull Plugin plugin, @NonNull String trackId) {
        return fileOf(plugin, trackId).isFile();
    }

    /**
     * Прочитать разбор трека.
     *
     * @return null, если файла нет или в нём не оказалось ни одного удара
     */
    @Nullable
    public static DiggerAnalysis load(@NonNull Plugin plugin, @NonNull String trackId) {
        File file = fileOf(plugin, trackId);
        if (!file.isFile()) return null;

        DiggerAnalysis analysis = new DiggerAnalysis(trackId, DiggerTuning.DEFAULT_BPM, 0);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                String key = parts[0].toLowerCase(Locale.ROOT);
                try {
                    if (key.equals("bpm") && parts.length >= 2) {
                        analysis.setBpm(Double.parseDouble(parts[1]));
                    } else if (key.equals("offset") && parts.length >= 2) {
                        analysis.setOffsetMillis(Integer.parseInt(parts[1]));
                    } else if (key.equals("onset") && parts.length >= 3) {
                        int millis = Integer.parseInt(parts[1]);
                        double strength = Double.parseDouble(parts[2]);
                        DiggerAnalysis.Band band = parts.length >= 4
                            ? DiggerAnalysis.Band.byName(parts[3])
                            : DiggerAnalysis.Band.MID;
                        analysis.addOnset(new DiggerAnalysis.Onset(millis, strength, band));
                    }
                } catch (NumberFormatException ignored) {
                    // Битая строка - не повод терять весь разбор.
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Копатель: не удалось прочитать разбор трека "
                + trackId + ": " + e.getMessage());
            return null;
        }

        if (analysis.size() == 0) return null;
        analysis.sort();
        return analysis;
    }

    /** Id трека уходит в путь файла, поэтому всё лишнее из него вырезается. */
    @NonNull
    private static String safe(@NonNull String trackId) {
        StringBuilder result = new StringBuilder();
        for (char c : trackId.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ') result.append(c);
        }
        String value = result.toString().trim();
        return value.isEmpty() ? "unknown" : value;
    }
}
