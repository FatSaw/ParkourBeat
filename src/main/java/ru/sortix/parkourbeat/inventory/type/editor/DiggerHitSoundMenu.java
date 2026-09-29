package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.digger.DiggerLevelSettings;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * ЗВУК ПОПАДАНИЯ ПО РУДЕ.
 * <p>
 * Прямой наследник меню звука прыжка: на «Копателе» прыжков нет, а вот удар по руде
 * есть, и он звучит по несколько раз в секунду. Поэтому выбор звука тут важнее, чем
 * где-либо ещё в редакторе - неудачный тембр на плотном треке превращает всё в кашу.
 * <p>
 * По умолчанию - короткий взмах на все руды. Вариант «по руде» даёт каждой руде свой
 * тембр: алмаз звенит, редстоун бьёт басом, кварц щёлкает. Он делает руду слышной, а не
 * только видимой, но на плотном треке десять тембров сливаются в кашу - поэтому не по
 * умолчанию.
 * <p>
 * Клик проигрывает звук сразу же: подобрать его на слух иначе невозможно.
 */
public class DiggerHitSoundMenu extends ParkourBeatInventory {

    private static final class Entry {
        final @Nullable String key;
        final float pitch;
        final @NonNull String plain;
        final @NonNull Material icon;

        Entry(@Nullable String key, float pitch, @NonNull String plain, @NonNull Material icon) {
            this.key = key;
            this.pitch = pitch;
            this.plain = plain;
            this.icon = icon;
        }
    }

    private static final Entry[] SOUNDS = {
        new Entry("entity.player.attack.sweep", 1.2f, "Взмах &8(по умолчанию)", Material.IRON_SWORD),
        new Entry(null, 1.0f, "По руде", Material.DIAMOND_ORE),
        new Entry("block.note_block.bell", 1.4f, "Колокольчик", Material.GOLD_INGOT),
        new Entry("block.note_block.chime", 1.6f, "Перезвон", Material.PACKED_ICE),
        new Entry("block.note_block.pling", 1.2f, "Плинк", Material.NOTE_BLOCK),
        new Entry("block.note_block.hat", 1.8f, "Щелчок", Material.LEATHER),
        new Entry("block.note_block.snare", 1.0f, "Малый барабан", Material.SAND),
        new Entry("block.note_block.basedrum", 0.8f, "Бочка", Material.STONE),
        new Entry("block.note_block.bass", 0.6f, "Бас", Material.OAK_PLANKS),
        new Entry("block.glass.break", 1.2f, "Стекло", Material.GLASS),
        new Entry("block.stone.break", 1.0f, "Камень", Material.COBBLESTONE),
        new Entry("block.netherite_block.break", 1.0f, "Незерит", Material.NETHERITE_BLOCK),
        new Entry("entity.player.attack.strong", 1.0f, "Удар", Material.NETHERITE_SWORD),
        new Entry("entity.player.attack.crit", 1.0f, "Крит", Material.GOLDEN_SWORD),
        new Entry("entity.generic.explode", 1.4f, "Взрыв", Material.TNT),
        new Entry("item.trident.hit", 1.2f, "Трезубец", Material.TRIDENT),
        new Entry("block.anvil.land", 1.6f, "Наковальня", Material.ANVIL),
        new Entry("entity.item.pickup", 2.0f, "Подбор", Material.HOPPER),
    };

    private final @NonNull EditActivity activity;
    private final @NonNull Level level;
    private final @NonNull DiggerLevelSettings settings;

    public DiggerHitSoundMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 3, lang, PbText.of("&8Звук попадания"));
        this.activity = activity;
        this.level = activity.getLevel();
        this.settings = this.level.getLevelSettings().getGameSettings().getDiggerSettings();
        this.updateItems();
    }

    private void updateItems() {
        this.clearInventory();

        for (int i = 0; i < SOUNDS.length; i++) {
            Entry entry = SOUNDS[i];
            boolean selected = entry.key == null
                ? !this.settings.hasCustomHitSound()
                : entry.key.equals(this.settings.getHitSoundKey());

            this.setItem(i, ItemUtils.create(entry.icon, meta -> {
                meta.displayName(PbText.item((selected ? "&a\u25c6 &a" : "&8\u25c6 &6") + entry.plain));
                List<Component> lore = new ArrayList<>();
                if (entry.key == null) {
                    lore.add(PbText.item("&7У каждой руды свой тембр."));
                    lore.add(PbText.item("&7Руду становится слышно, а не только видно."));
                } else {
                    lore.add(PbText.item("&7Один звук на все руды."));
                    lore.add(PbText.item("&8" + entry.key));
                }
                lore.add(Component.empty());
                lore.add(PbText.item(selected ? "&aВыбрано" : "&8ЛКМ - выбрать и послушать"));
                meta.lore(lore);
            }), event -> {
                Player player = event.getPlayer();
                this.settings.setHitSound(entry.key, entry.pitch);

                // Слушать надо сразу: подобрать звук по названию невозможно.
                if (entry.key != null) {
                    player.playSound(player.getLocation(), entry.key, 1.0f, entry.pitch);
                } else {
                    player.playSound(player.getLocation(),
                        ru.sortix.parkourbeat.digger.DiggerOre.DIAMOND.getSound(), 1.0f,
                        ru.sortix.parkourbeat.digger.DiggerOre.DIAMOND.getPitch());
                }
                new DiggerHitSoundMenu((ParkourBeat) this.plugin, this.lang, this.activity).open(player);
            });
        }

        this.setItem(3, 5, ItemUtils.create(Material.ARROW, meta -> {
            meta.displayName(PbText.item("&8◆ &6Назад"));
        }), event -> new EditorMainMenu((ParkourBeat) this.plugin, this.lang, this.activity)
            .open(event.getPlayer()));
    }
}
