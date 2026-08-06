package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.JumpZone;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.util.Collection;

public class JumpZonesMenu extends LightShowElementsMenu<JumpZone> {
    public JumpZonesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, lang, activity, LangOptions.inventory_editorjumps_title.getComponent(lang));
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<JumpZone> getElements() {
        return this.getLightShow().getJumpZones();
    }

    @Override
    protected @NonNull ItemStack createEntry(@NonNull JumpZone zone) {
        return ItemUtils.create(Material.RABBIT_FOOT, meta -> {
            meta.displayName(LangOptions.inventory_editorjumps_entry_name.getComponent(
                lang, new Placeholders("%time%", zone.getStartTimecode())));
            meta.lore(LangOptions.inventory_editorjumps_entry_lore.getComponents(lang,
                new Placeholders("%mode%", net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().serialize((zone.getMode() == JumpZone.Mode.RANDOM
                        ? LangOptions.inventory_editorjump_modes_random
                        : LangOptions.inventory_editorjump_modes_sequential).getComponent(lang))),
                new Placeholders("%count%", String.valueOf(zone.getEffects().size())),
                new Placeholders("%start%", zone.getStartTimecode()),
                new Placeholders("%end%", zone.getEndTimecode())));
        });
    }

    @Override
    protected @NonNull JumpZone createNew(int timeMillis) {
        return new JumpZone(timeMillis, timeMillis + JumpZone.DEFAULT_LENGTH_MILLIS,
            new java.util.ArrayList<>(), JumpZone.Mode.SEQUENTIAL);
    }

    @Override
    protected boolean addElement(@NonNull JumpZone element) {
        return this.getLightShow().addJumpZone(element);
    }

    @Override
    protected boolean removeElement(@NonNull JumpZone element) {
        return this.getLightShow().removeJumpZone(element);
    }

    @Override
    protected void openElementMenu(@NonNull Player player, @NonNull JumpZone element) {
        new JumpZoneMenu(this.plugin, this.lang, this.activity, element).open(player);
    }

    @Override
    protected @NonNull Material addIconMaterial() {
        return Material.RABBIT_FOOT;
    }
}
