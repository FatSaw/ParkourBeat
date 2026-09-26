package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.digger.DiggerPickaxe;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.PickaxeCue;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * СПИСОК КИРОК ПО ТАЙМКОДУ.
 * <p>
 * Кьюс живёт в световом шоу, а не в настройках карты, потому что это часть картинки:
 * куплет проходится деревянной киркой, а на дропе в руке оказывается незеритовая.
 * На механику смена не влияет никак - в творческом режиме руда ломается любым
 * инструментом с одного клика.
 */
public class PickaxeCuesMenu extends LightShowElementsMenu<PickaxeCue> {

    public PickaxeCuesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, lang, activity, PbText.of("&8Кирки по таймкоду"));
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<PickaxeCue> getElements() {
        return this.getLightShow().getPickaxeCues();
    }

    @Override
    protected @NonNull ItemStack createEntry(@NonNull PickaxeCue cue) {
        return ItemUtils.create(cue.getPickaxe(), meta -> {
            DiggerPickaxe pickaxe = DiggerPickaxe.byMaterial(cue.getPickaxe());
            meta.displayName(PbText.item("&f" + cue.getStartTimecode()
                + " &8- &f" + cue.getEndTimecode()));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Кирка: " + (pickaxe == null ? "&7?" : pickaxe.getDisplay())));
            lore.add(Component.empty());
            lore.add(PbText.item("&8ЛКМ - настроить, Shift + ПКМ - удалить"));
            meta.lore(lore);
        });
    }

    @Override
    protected @NonNull PickaxeCue createNew(int timeMillis) {
        return new PickaxeCue(timeMillis,
            timeMillis + PickaxeCue.DEFAULT_DURATION_MILLIS,
            DiggerPickaxe.getDefault().getMaterial());
    }

    @Override
    protected boolean addElement(@NonNull PickaxeCue element) {
        return this.getLightShow().addPickaxeCue(element);
    }

    @Override
    protected boolean removeElement(@NonNull PickaxeCue element) {
        return this.getLightShow().removePickaxeCue(element);
    }

    @Override
    protected void openElementMenu(@NonNull Player player, @NonNull PickaxeCue element) {
        new PickaxeCueMenu(this.plugin, this.lang, this.activity, element).open(player);
    }

    @Override
    protected @NonNull Material addIconMaterial() {
        return Material.NETHERITE_PICKAXE;
    }
}
