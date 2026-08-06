package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.JumpEffect;
import ru.sortix.parkourbeat.levels.settings.JumpZone;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class JumpZoneMenu extends LightShowElementMenu<JumpZone> {

    // Effects laid out centered on row 2. SOUND is handled by its own button, so it is not
    // toggled here; only the visual effects appear as toggles.
    private static final JumpEffect[] TOGGLE_EFFECTS = {
        JumpEffect.TIME_PUSH, JumpEffect.JUMP_AIR, JumpEffect.JUMP_FIRE
    };

    // Centered columns for three toggles on a 9-wide row: 4,5,6.
    private static final int[] EFFECT_COLUMNS = {4, 5, 6};

    public JumpZoneMenu(@NonNull ParkourBeat plugin,
                        String lang,
                        @NonNull EditActivity activity,
                        @NonNull JumpZone zone
    ) {
        super(plugin, lang, activity, zone, LangOptions.inventory_editorjump_title.getComponent(lang));
        this.updateItems();
    }

    @Override
    protected void addSpecificItems() {
        this.addModeItem();
        this.addEffectItems();
        this.addSoundItem();
    }

    private void addModeItem() {
        JumpZone.Mode mode = this.element.getMode();
        this.setItem(
            1,
            5,
            ItemUtils.create(
                mode == JumpZone.Mode.RANDOM ? Material.ENDER_PEARL : Material.REPEATER,
                meta -> {
                    meta.displayName(LangOptions.inventory_editorjump_mode_name.getComponent(lang));
                    meta.lore(LangOptions.inventory_editorjump_mode_lore.getComponents(
                        lang, new Placeholders("%mode%", modeName(mode))));
                }),
            event -> {
                Player player = event.getPlayer();
                this.element.setMode(mode == JumpZone.Mode.SEQUENTIAL
                    ? JumpZone.Mode.RANDOM
                    : JumpZone.Mode.SEQUENTIAL);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f);
                this.updateItems();
            });
    }

    private void addEffectItems() {
        for (int i = 0; i < TOGGLE_EFFECTS.length; i++) {
            JumpEffect effect = TOGGLE_EFFECTS[i];
            int column = EFFECT_COLUMNS[i];
            boolean enabled = this.element.getEffects().contains(effect);

            this.setItem(
                2,
                column,
                ItemUtils.create(enabled ? Material.LIME_DYE : Material.GRAY_DYE, meta -> {
                    meta.displayName(LangOptions.inventory_editorjump_effect_name.getComponent(
                        lang, new Placeholders("%effect%", effectName(effect))));
                    meta.lore((enabled
                        ? LangOptions.inventory_editorjump_effect_lore_on
                        : LangOptions.inventory_editorjump_effect_lore_off).getComponents(lang));
                }),
                event -> {
                    Player player = event.getPlayer();
                    if (this.element.getEffects().contains(effect)) {
                        this.element.removeEffect(effect);
                    } else {
                        this.element.addEffect(effect);
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    this.updateItems();
                });
        }
    }

    private void addSoundItem() {
        boolean soundOn = this.element.getEffects().contains(JumpEffect.SOUND);
        this.setItem(
            2,
            8,
            ItemUtils.create(Material.NOTE_BLOCK, meta -> {
                meta.displayName(LangOptions.inventory_editorjump_sound_name.getComponent(lang));
                meta.lore((soundOn
                    ? LangOptions.inventory_editorjump_sound_lore_on
                    : LangOptions.inventory_editorjump_sound_lore_off).getComponents(lang,
                    new Placeholders("%sound%", JumpSoundMenu.titleFor(lang, this.element.getSoundKey()))));
            }),
            event -> {
                Player player = event.getPlayer();
                if (event.isLeft()) {
                    // Left toggles whether SOUND fires; right opens the picker.
                    if (this.element.getEffects().contains(JumpEffect.SOUND)) {
                        this.element.removeEffect(JumpEffect.SOUND);
                    } else {
                        this.element.addEffect(JumpEffect.SOUND);
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    this.updateItems();
                } else {
                    new JumpSoundMenu(this.plugin, this.lang, this.activity, this.element).open(player);
                }
            });
    }

    private String effectName(@NonNull JumpEffect effect) {
        LangOptions key = switch (effect) {
            case TIME_PUSH -> LangOptions.inventory_editorjump_effects_timepush;
            case JUMP_AIR -> LangOptions.inventory_editorjump_effects_air;
            case JUMP_FIRE -> LangOptions.inventory_editorjump_effects_fire;
            case SOUND -> LangOptions.inventory_editorjump_effects_sound;
        };
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand().serialize(key.getComponent(lang));
    }

    private String modeName(@NonNull JumpZone.Mode mode) {
        LangOptions key = mode == JumpZone.Mode.RANDOM
            ? LangOptions.inventory_editorjump_modes_random
            : LangOptions.inventory_editorjump_modes_sequential;
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand().serialize(key.getComponent(lang));
    }

    @Override
    protected boolean removeElement() {
        return this.getLightShow().removeJumpZone(this.element);
    }

    @Override
    protected void openListMenu(@NonNull Player player) {
        new JumpZonesMenu(this.plugin, this.lang, this.activity).open(player);
    }

    @Override
    protected void reopen(@NonNull Player player) {
        new JumpZoneMenu(this.plugin, this.lang, this.activity, this.element).open(player);
    }
}
