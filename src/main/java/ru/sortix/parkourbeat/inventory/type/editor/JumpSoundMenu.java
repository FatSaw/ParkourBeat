package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.PluginInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.JumpZone;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class JumpSoundMenu extends PluginInventory<ParkourBeat> {

    private static final class Entry {
        final String key;
        final float pitch;
        final LangOptions title;
        final Material icon;

        Entry(String key, float pitch, LangOptions title, Material icon) {
            this.key = key;
            this.pitch = pitch;
            this.title = title;
            this.icon = icon;
        }

        String stored() {
            return this.key + "@" + this.pitch;
        }
    }

    private static final Entry[] SOUNDS = {
        new Entry("entity.player.attack.strong", 1.0f, LangOptions.inventory_editorjumpsound_names_swing, Material.IRON_SWORD),
        new Entry("entity.iron_golem.attack", 1.0f, LangOptions.inventory_editorjumpsound_names_lightswing, Material.IRON_INGOT),
        new Entry("entity.generic.explode", 1.0f, LangOptions.inventory_editorjumpsound_names_explosion, Material.TNT),
        new Entry("item.shield.block", 1.0f, LangOptions.inventory_editorjumpsound_names_woodhit, Material.SHIELD),
        new Entry("block.piston.extend", 1.0f, LangOptions.inventory_editorjumpsound_names_extend, Material.PISTON),
        new Entry("block.netherite_block.break", 1.0f, LangOptions.inventory_editorjumpsound_names_quietbreak, Material.NETHERITE_BLOCK),
        new Entry("block.note_block.hat", 0.4f, LangOptions.inventory_editorjumpsound_names_dulljump, Material.NOTE_BLOCK),
    };

    @NonNull
    public static String titleFor(String lang, @NonNull String storedKey) {
        for (Entry entry : SOUNDS) {
            if (entry.stored().equalsIgnoreCase(storedKey)) {
                return LegacyComponentSerializer.legacyAmpersand()
                    .serialize(entry.title.getComponent(lang));
            }
        }
        return storedKey;
    }

    private final EditActivity activity;
    private final JumpZone zone;

    public JumpSoundMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity, @NonNull JumpZone zone) {
        super(plugin, 3, lang, LangOptions.inventory_editorjumpsound_title.getComponent(lang));
        this.activity = activity;
        this.zone = zone;
        this.render();
    }

    private void render() {
        this.clearInventory();

        int[] columns = {1, 2, 3, 4, 5, 6, 7};

        for (int i = 0; i < SOUNDS.length && i < columns.length; i++) {
            Entry entry = SOUNDS[i];
            boolean selected = this.zone.getSoundKey().equalsIgnoreCase(entry.stored());
            this.setItem(
                1,
                columns[i],
                ItemUtils.create(entry.icon, meta -> {
                    meta.displayName(LangOptions.inventory_editorjumpsound_entry_name.getComponent(
                        lang, new Placeholders("%name%", LegacyComponentSerializer.legacyAmpersand()
                            .serialize(entry.title.getComponent(lang)))));
                    meta.lore((selected
                        ? LangOptions.inventory_editorjumpsound_entry_selected
                        : LangOptions.inventory_editorjumpsound_entry_lore).getComponents(lang));
                }),
                event -> {
                    Player player = event.getPlayer();
                    if (event.isLeft()) {
                        this.zone.setSoundKey(entry.stored());
                        this.render();
                        this.open(player);
                    } else {
                        this.playPreview(player, entry);
                    }
                });
        }

        this.setItem(
            2, 4,
            ItemUtils.create(Material.BARRIER, meta ->
                meta.displayName(LangOptions.inventory_editorjumpsound_back.getComponent(lang))),
            event -> new JumpZoneMenu(this.plugin, this.lang, this.activity, this.zone).open(event.getPlayer()));
    }

    private void playPreview(@NonNull Player player, @NonNull Entry entry) {
        try {
            player.playSound(player.getLocation(), entry.key, 1.0f, entry.pitch);
        } catch (Throwable ignored) {
        }
    }
}
