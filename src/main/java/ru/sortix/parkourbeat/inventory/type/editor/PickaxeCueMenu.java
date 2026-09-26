package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.digger.DiggerPickaxe;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.PickaxeCue;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/** Настройка одного кьюса кирки: выбор самой кирки, время правится общими кнопками. */
public class PickaxeCueMenu extends LightShowElementMenu<PickaxeCue> {

    public PickaxeCueMenu(@NonNull ParkourBeat plugin,
                          String lang,
                          @NonNull EditActivity activity,
                          @NonNull PickaxeCue cue
    ) {
        super(plugin, lang, activity, cue, PbText.of("&8Кирка на отрезке"));
        this.updateItems();
    }

    @Override
    protected void addSpecificItems() {
        this.setItem(2, 5, ItemUtils.create(this.element.getPickaxe(), meta -> {
            DiggerPickaxe pickaxe = DiggerPickaxe.byMaterial(this.element.getPickaxe());
            meta.displayName(PbText.item("&fКирка: " + (pickaxe == null ? "&7?" : pickaxe.getDisplay())));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Что будет в руке на этом отрезке."));
            lore.add(PbText.item("&7На скорость ломки не влияет."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8ЛКМ - следующая"));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            DiggerPickaxe current = DiggerPickaxe.byMaterial(this.element.getPickaxe());
            DiggerPickaxe next = (current == null ? DiggerPickaxe.getDefault() : current).next();
            this.element.setPickaxe(next.getMaterial());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f);
            this.reopen(player);
        });
    }

    @Override
    protected boolean removeElement() {
        return this.getLightShow().removePickaxeCue(this.element);
    }

    @Override
    protected void openListMenu(@NonNull Player player) {
        new PickaxeCuesMenu(this.plugin, this.lang, this.activity).open(player);
    }

    @Override
    protected void reopen(@NonNull Player player) {
        new PickaxeCueMenu(this.plugin, this.lang, this.activity, this.element).open(player);
    }
}
