package ru.sortix.parkourbeat.item.editor.type;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.item.editor.EditorItem;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

public class TestGameItem extends EditorItem {
    public TestGameItem(@NonNull ParkourBeat plugin, String lang, int slot) {
        super(plugin, lang, slot, 20, ItemUtils.create(Material.DIAMOND, (meta) -> {
            meta.displayName(LangOptions.item_editor_test.getComponent(lang));
        }));
    }

    @Override
    public void onUse(@NonNull PlayerInteractEvent event, @NonNull EditActivity activity) {
        // У 2D-уровней свой режим тестирования: обычный забег им не подходит вообще.
        if (ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(activity.getLevel())) {
            this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class).toggleEditorTest(activity);
            return;
        }
        if (activity.isTesting()) {
            activity.endTesting();
            return;
        }

        // На дуэльной карте сначала спрашиваем сторону: трассы две, и бежать «просто
        // тест» тут не значит ничего - прыжки засчитываются только на своей стороне.
        if (ru.sortix.parkourbeat.duel.DuelManager.isDuel(activity.getLevel())) {
            new ru.sortix.parkourbeat.inventory.type.editor.DuelTestSideMenu(
                this.plugin, this.lang, activity).open(event.getPlayer());
            return;
        }

        activity.startTesting();
    }
}
