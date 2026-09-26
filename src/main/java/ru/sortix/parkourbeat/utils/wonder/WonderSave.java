package ru.sortix.parkourbeat.utils.wonder;

import lombok.NonNull;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.Level;


/**
 * Немедленная запись настроек уровня на диск.
 * <p>
 * Обычно настройки сохраняются только когда редактор закрывается, поэтому перезагрузка
 * плагина прямо из редактора теряла всё, что успели наставить. Эффекты и ламповые стены
 * пишутся сразу после каждого изменения: работа строителя не должна зависеть от того,
 * успел ли он выйти из редактора.
 */
public final class WonderSave {

    private WonderSave() {
    }

    public static void now(@NonNull ParkourBeat plugin, @NonNull Level level) {
        try {
            // Основной путь: штатное сохранение уровня. После починки copy() оно больше
            // не теряет наши списки, а значит всё лежит там же, где остальные настройки.
            plugin.get(ru.sortix.parkourbeat.levels.LevelsManager.class)
                .saveLevelSettingsAndBlocks(level);
            // Подстраховка на случай краша между сохранениями: тот же набор в своём файле.
            plugin.get(WonderStorage.class).save(level);
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось сохранить настройки уровня: " + t.getMessage());
        }
    }
}
