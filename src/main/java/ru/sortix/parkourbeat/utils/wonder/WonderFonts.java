package ru.sortix.parkourbeat.utils.wonder;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Свои шрифты по прямой ссылке.
 * <p>
 * Кода LightShow это не касается вообще. Он рисует текст обычным java.awt.Font по имени
 * семейства, а семейства берутся из GraphicsEnvironment процесса. Нам достаточно скачать
 * ttf и зарегистрировать его в той же JVM: LightShow найдёт шрифт сам, как системный.
 * <p>
 * Имя семейства отдаётся наружу с подчёркиваниями вместо пробелов: параметр font: едет
 * одним словом в строке настроек, а LightShow разворачивает подчёркивания обратно.
 */
public class WonderFonts implements PluginManager {

    private static final long MAX_BYTES = 8L * 1024L * 1024L;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull File folder;
    private final Map<String, File> families = new LinkedHashMap<>();

    public WonderFonts(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "fonts");
        if (!this.folder.exists() && !this.folder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку для шрифтов");
        }
        this.loadAll();
    }

    @Override
    public void disable() {
        this.families.clear();
    }

    /** Шрифты переживают рестарт: файлы лежат рядом с плагином и поднимаются при старте. */
    private void loadAll() {
        File[] files = this.folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) continue;
            String family = register(file);
            if (family != null) this.families.put(family, file);
        }
        if (!this.families.isEmpty()) {
            this.plugin.getLogger().info("Свои шрифты для эффектов: " + this.families.size());
        }
    }

    @Nullable
    private String register(@NonNull File file) {
        try {
            Font font = Font.createFont(Font.TRUETYPE_FONT, file);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font.getFamily(Locale.ROOT);
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Шрифт " + file.getName() + " не подошёл: " + t.getMessage());
            return null;
        }
    }

    /** Имена семейств так, как их надо писать в font: */
    @NonNull
    public List<String> customFamilies() {
        List<String> out = new ArrayList<>();
        for (String family : this.families.keySet()) out.add(family.replace(' ', '_'));
        return out;
    }

    /** Готовый набор: строителю не нужно искать ссылки самому. */
    public static final String[][] CATALOGUE = {
        {"Lobster", "https://raw.githubusercontent.com/google/fonts/main/ofl/lobster/Lobster-Regular.ttf", "Плавный рукописный, с кириллицей"},
        {"Pacifico", "https://raw.githubusercontent.com/google/fonts/main/ofl/pacifico/Pacifico-Regular.ttf", "Курсивный, летний, с кириллицей"},
        {"Marck Script", "https://raw.githubusercontent.com/google/fonts/main/ofl/marckscript/MarckScript-Regular.ttf", "Настоящий рукописный курсив, кириллица"},
        {"Philosopher Italic", "https://raw.githubusercontent.com/google/fonts/main/ofl/philosopher/Philosopher-Italic.ttf", "Наклонный с засечками, кириллица"},
        {"Oranienbaum", "https://raw.githubusercontent.com/google/fonts/main/ofl/oranienbaum/Oranienbaum-Regular.ttf", "Высокий книжный, кириллица"},
        {"Prosto One", "https://raw.githubusercontent.com/google/fonts/main/ofl/prostoone/ProstoOne-Regular.ttf", "Простой плакатный, кириллица"},
        {"Bebas Neue", "https://raw.githubusercontent.com/google/fonts/main/ofl/bebasneue/BebasNeue-Regular.ttf", "Узкие заглавные, для громких слов"},
        {"Press Start 2P", "https://raw.githubusercontent.com/google/fonts/main/ofl/pressstart2p/PressStart2P-Regular.ttf", "Аркадный пиксель"},
        {"VT323", "https://raw.githubusercontent.com/google/fonts/main/ofl/vt323/VT323-Regular.ttf", "Терминальный, кириллица"},
        {"Monoton", "https://raw.githubusercontent.com/google/fonts/main/ofl/monoton/Monoton-Regular.ttf", "Неоновая вывеска в полоску"},
        {"Bungee", "https://raw.githubusercontent.com/google/fonts/main/ofl/bungee/Bungee-Regular.ttf", "Толстый уличный"},
        {"Audiowide", "https://raw.githubusercontent.com/google/fonts/main/ofl/audiowide/Audiowide-Regular.ttf", "Техно, широкий"},
        {"Creepster", "https://raw.githubusercontent.com/google/fonts/main/ofl/creepster/Creepster-Regular.ttf", "Хоррор, рваные края"},
        {"Rubik Mono One", "https://raw.githubusercontent.com/google/fonts/main/ofl/rubikmonoone/RubikMonoOne-Regular.ttf", "Тяжёлый моноширинный, кириллица"}
    };

    @NonNull
    public List<String> builtInFamilies() {
        List<String> out = new ArrayList<>();
        out.add("pixel");
        out.add("bold");
        out.add("thin");
        return out;
    }

    public boolean isCustom(@NonNull String family) {
        return this.families.containsKey(family.replace('_', ' '));
    }

    public boolean remove(@NonNull String family) {
        File file = this.families.remove(family.replace('_', ' '));
        if (file == null) return false;
        // Сам шрифт из JVM убрать нельзя, но до перезапуска он просто останется висеть
        // невидимым для меню: файла нет, значит после рестарта его тоже не будет.
        return file.delete();
    }

    /**
     * Скачать шрифт по прямой ссылке и сразу сделать доступным для эффектов.
     * Ответ приходит в главный поток.
     */
    public void download(@NonNull Player player, @NonNull String url, @NonNull Consumer<String> onFamily) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_fonts.download.1")));
            return;
        }
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_fonts.download.2")));

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            String error = null;
            String family = null;
            File target = null;
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestProperty("User-Agent", "ParkourBeat-Wonder/1.0");
                con.setConnectTimeout(8000);
                con.setReadTimeout(20000);
                con.setInstanceFollowRedirects(true);
                if (con.getContentLengthLong() > MAX_BYTES) throw new Exception(Lang.raw(PlayerLang.of(player), "auto.wonder_fonts.download.3"));

                String name = url.substring(url.lastIndexOf('/') + 1).split("\\?")[0];
                name = name.replaceAll("[^A-Za-z0-9._-]", "_");
                if (!name.toLowerCase(Locale.ROOT).endsWith(".ttf")
                    && !name.toLowerCase(Locale.ROOT).endsWith(".otf")) name = name + ".ttf";

                target = new File(this.folder, name);
                long written = 0;
                try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        written += read;
                        if (written > MAX_BYTES) throw new Exception("файл больше 8 МБ");
                        out.write(buffer, 0, read);
                    }
                }

                family = this.register(target);
                if (family == null) throw new Exception(Lang.raw(PlayerLang.of(player), "auto.wonder_fonts.download.4"));
            } catch (Throwable t) {
                error = t.getMessage() == null ? t.toString() : t.getMessage();
                if (target != null && target.exists() && family == null) target.delete();
            }

            final String resultFamily = family;
            final String resultError = error;
            final File resultFile = target;
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (resultFamily == null) {
                    player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_fonts.download.5") + resultError));
                    return;
                }
                this.families.put(resultFamily, resultFile);
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_fonts.download.6") + resultFamily));
                onFamily.accept(resultFamily.replace(' ', '_'));
            });
        });
    }
}
