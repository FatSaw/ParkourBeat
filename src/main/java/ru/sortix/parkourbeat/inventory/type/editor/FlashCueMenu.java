package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.FlashCue;
import ru.sortix.parkourbeat.levels.settings.FlashSpeed;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class FlashCueMenu extends LightShowElementMenu<FlashCue> {
    public FlashCueMenu(@NonNull ParkourBeat plugin,
                        String lang,
                        @NonNull EditActivity activity,
                        @NonNull FlashCue cue
    ) {
        super(plugin, lang, activity, cue, LangOptions.inventory_editorflash_title.getComponent(lang));
        this.updateItems();
    }

    @Override
    protected void addSpecificItems() {
        this.setItem(
            2,
            5,
            ItemUtils.create(org.bukkit.Material.GLOWSTONE_DUST, meta -> {
                meta.displayName(LangOptions.inventory_editorflash_speed_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorflash_speed_lore.getComponents(
                    lang, new Placeholders("%speed%", this.element.getSpeed().getDisplayNameString(lang))));
            }),
            event -> {
                Player player = event.getPlayer();
                FlashSpeed speed = this.element.getSpeed().next();
                this.element.setSpeed(speed);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f);
                player.sendMessage(LangOptions.inventory_editorflash_speed_changed.getComponent(
                    lang, new Placeholders("%speed%", speed.getDisplayNameString(lang))));
                this.updateItems();
            });
    }

    @Override
    protected boolean removeElement() {
        return this.getLightShow().removeFlashCue(this.element);
    }

    @Override
    protected void openListMenu(@NonNull Player player) {
        new FlashCuesMenu(this.plugin, this.lang, this.activity).open(player);
    }

    @Override
    protected void reopen(@NonNull Player player) {
        new FlashCueMenu(this.plugin, this.lang, this.activity, this.element).open(player);
    }
}
