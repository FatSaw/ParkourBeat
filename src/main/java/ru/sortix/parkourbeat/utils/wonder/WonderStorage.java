package ru.sortix.parkourbeat.utils.wonder;

import lombok.NonNull;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.lamps.LampWall;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Своё хранилище для чудоэффектов и ламповых стен.
 * <p>
 * Настройки уровня пишутся на диск только в момент закрытия редактора, и любой рестарт
 * или перезагрузка плагина между этими моментами теряли работу целиком. Здесь всё иначе:
 * файл на уровень, запись сразу после каждого изменения, чтение при первом обращении.
 * Ни от чужого расписания сохранений, ни от порядка выключения плагина это не зависит.
 * <p>
 * Строки, которые не удалось разобрать, не выбрасываются молча, а попадают в лог: потерю
 * работы строителя нельзя оставлять незаметной.
 */
public class WonderStorage implements PluginManager {

    private final @NonNull ParkourBeat plugin;
    private final @NonNull File folder;
    private final Set<UUID> loaded = new HashSet<>();

    public WonderStorage(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "wonder");
        if (!this.folder.exists() && !this.folder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку wonder");
        }
    }

    @Override
    public void disable() {
        this.loaded.clear();
    }

    @NonNull
    private File fileOf(@NonNull UUID levelId) {
        return new File(this.folder, levelId + ".yml");
    }

    /**
     * Подстраховка.
     * <p>
     * Обычно эффекты приходят вместе с настройками уровня. Читаем свой файл только если там
     * пусто: значит настройки не успели записаться или потерялись, и работу надо вернуть.
     */
    public void ensureLoaded(@NonNull Level level) {
        if (!this.loaded.add(level.getUniqueId())) return;

        LightShowSettings existing = level.getLightShow();
        if (existing.getWonderEffectsAmount() > 0 || existing.getLampWallsAmount() > 0) return;

        File file = this.fileOf(level.getUniqueId());
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        LightShowSettings lightShow = level.getLightShow();

        int effects = 0, broken = 0;
        for (String line : yaml.getStringList("wonder_effects")) {
            WonderEffect effect = WonderEffect.deserialize(line);
            if (effect == null) {
                broken++;
                this.plugin.getLogger().warning("Битый чудоэффект в " + file.getName() + ": " + line);
                continue;
            }
            if (lightShow.addWonderEffect(effect)) effects++;
        }

        int walls = 0;
        for (String line : yaml.getStringList("lamp_walls")) {
            LampWall wall = LampWall.deserialize(line);
            if (wall == null) {
                broken++;
                this.plugin.getLogger().warning("Битая ламповая стена в " + file.getName() + ": " + line);
                continue;
            }
            if (lightShow.addLampWall(wall)) walls++;
        }

        lightShow.sort();
        if (effects > 0 || walls > 0) {
            this.plugin.getLogger().info("Восстановлено из резервной копии, уровень " + level.getUniqueId()
                + ": эффектов " + effects + ", ламповых стен " + walls
                + (broken > 0 ? ", битых записей " + broken : ""));
        }
    }

    /** Записать прямо сейчас. Вызывается после каждого изменения. */
    public void save(@NonNull Level level) {
        LightShowSettings lightShow = level.getLightShow();

        List<String> effects = new ArrayList<>();
        for (WonderEffect effect : lightShow.getWonderEffects()) effects.add(effect.serialize());

        List<String> walls = new ArrayList<>();
        for (LampWall wall : lightShow.getLampWalls()) walls.add(wall.serialize());

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("wonder_effects", effects);
        yaml.set("lamp_walls", walls);

        try {
            yaml.save(this.fileOf(level.getUniqueId()));
            this.loaded.add(level.getUniqueId());
        } catch (Exception e) {
            this.plugin.getLogger().warning("Не удалось сохранить эффекты уровня "
                + level.getUniqueId() + ": " + e.getMessage());
        }
    }

    /** Уровень выгрузили из памяти: при следующем обращении читаем с диска заново. */
    public void forget(@NonNull UUID levelId) {
        this.loaded.remove(levelId);
    }
}
