package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.PluginInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.CompletionParticle;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CompletionParticlesMenu extends PluginInventory<ParkourBeat> {

    public enum Kind {WIN, LOSE}

    private final EditActivity activity;
    private final Kind kind;

    public CompletionParticlesMenu(@NonNull ParkourBeat plugin, String lang,
                                   @NonNull EditActivity activity, @NonNull Kind kind) {
        super(plugin, 6, lang, LangOptions.inventory_editorcompletion_title.getComponent(lang));
        this.activity = activity;
        this.kind = kind;
        this.render();
    }

    private CompletionParticle current() {
        LightShowSettings ls = this.activity.getLevel().getLightShow();
        return switch (this.kind) {
            case WIN -> ls.getWinParticle();
            case LOSE -> ls.getLoseParticle();
        };
    }

    private void set(@NonNull CompletionParticle particle) {
        LightShowSettings ls = this.activity.getLevel().getLightShow();
        switch (this.kind) {
            case WIN -> ls.setWinParticle(particle);
            case LOSE -> ls.setLoseParticle(particle);
        }
    }

    private void render() {
        this.clearInventory();

        CompletionParticle currentParticle = this.current();
        CompletionParticle[] all = CompletionParticle.values();

        // Centered grid: start at row 1, columns 1..7, wrapping.
        int index = 0;
        for (CompletionParticle particle : all) {
            int row = 1 + index / 7;
            int column = 1 + index % 7;
            boolean selected = currentParticle == particle;

            this.setItem(
                row,
                column,
                ItemUtils.create(particle.getIcon(), meta -> {
                    meta.displayName(LangOptions.inventory_editorcompletion_entry_name.getComponent(
                        lang, new Placeholders("%name%", titleOf(particle))));
                    meta.lore((selected
                        ? LangOptions.inventory_editorcompletion_entry_selected
                        : LangOptions.inventory_editorcompletion_entry_lore).getComponents(lang));
                }),
                event -> {
                    Player player = event.getPlayer();
                    if (event.isLeft()) {
                        this.set(particle);
                        this.render();
                        this.open(player);
                    } else {
                        this.previewWithCountdown(player, particle);
                    }
                });
            index++;
        }

        this.setItem(
            6, 5,
            ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(LangOptions.inventory_editorcompletion_back.getComponent(lang))),
            event -> new LightShowMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    /**
     * Plays the particle immediately, shows a 2..1 countdown, then reopens the menu.
     */
    private void previewWithCountdown(@NonNull Player player, @NonNull CompletionParticle particle) {
        player.closeInventory();
        particle.play(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        showCount(player, 2);
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline()) return;
            showCount(player, 1);
        }, 20L);

        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline()) return;
            new CompletionParticlesMenu(this.plugin, this.lang, this.activity, this.kind).open(player);
        }, 40L);
    }

    private String titleOf(@NonNull CompletionParticle particle) {
        LangOptions key = switch (particle) {
            case NONE -> LangOptions.inventory_editorcompletion_particles_none;
            case WARPED_SPORE_NOSE -> LangOptions.inventory_editorcompletion_particles_spores;
            case DRAGON_BREATH -> LangOptions.inventory_editorcompletion_particles_dragonbreath;
            case GREEN_DECAY -> LangOptions.inventory_editorcompletion_particles_greendecay;
            case WITCH_FLEW -> LangOptions.inventory_editorcompletion_particles_witchflew;
            case OBSIDIAN_BREAK -> LangOptions.inventory_editorcompletion_particles_obsidian;
            case BLOOD_SPILL -> LangOptions.inventory_editorcompletion_particles_blood;
            case WHITE_FLASH -> LangOptions.inventory_editorcompletion_particles_whiteflash;
            case SCATTER_EYES -> LangOptions.inventory_editorcompletion_particles_eyes;
            case GOLD_SPLIT -> LangOptions.inventory_editorcompletion_particles_goldsplit;
        };
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand().serialize(key.getComponent(lang));
    }

    private static void showCount(@NonNull Player player, int number) {
        player.showTitle(Title.title(
            Component.empty(),
            Component.text(String.valueOf(number)),
            Title.Times.of(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(200))
        ));
    }
}
