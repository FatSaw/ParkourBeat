package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.GlowingBarrierItems;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.GlowColor;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.levels.settings.GlowMode;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class GlowingBarriersMenu extends ParkourBeatInventory implements EditLevelMenu {
    private static final int GIVEN_AMOUNT = 1;

    private final @NonNull EditActivity activity;

    public GlowingBarriersMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, LangOptions.inventory_editorglow_title.getComponent(lang));
        this.activity = activity;
        this.updateItems();
    }

    public void updateItems() {
        this.clearInventory();

        GlowColor[] colors = GlowColor.values();
        for (int index = 0; index < colors.length; index++) {
            GlowColor color = colors[index];
            int row = (index / 8) + 1;
            int column = (index % 8) + 1;
            this.setItem(
                row,
                column,
                ItemUtils.create(color.getIconMaterial(), meta -> {
                    meta.displayName(color.getDisplayName(lang));
                    meta.lore(LangOptions.inventory_editorglow_color_lore.getComponents(lang));
                    meta.addItemFlags(ItemFlag.values());
                }),
                event -> this.giveGlowing(event.getPlayer(), color));
        }

        this.setItem(
            4,
            3,
            ItemUtils.create(Material.STRING, meta -> {
                meta.displayName(LangOptions.inventory_editorglow_distance_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorglow_distance_lore.getComponents(lang,
                    new Placeholders("%distance%", String.format(java.util.Locale.ROOT, "%.1f",
                        this.activity.getLevel().getLevelSettings()
                            .getWorldSettings().getGlowViewDistance()))));
            }),
            this::changeDistance);

        GlowMode mode = this.activity.getGlowMode();
        this.setItem(
            4,
            5,
            ItemUtils.create(mode.getIconMaterial(), meta -> {
                meta.displayName(LangOptions.inventory_editorglow_mode_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorglow_mode_lore.getComponents(
                    lang, new Placeholders("%mode%", mode.getDisplayNameString(lang))));
            }),
            this::switchMode);

        this.setItem(
            4,
            7,
            ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(LangOptions.inventory_editorglow_plain_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorglow_plain_lore.getComponents(lang));
            }),
            event -> {
                Player player = event.getPlayer();
                GlowingBarrierItems.give(player, GlowingBarrierItems.createPlain(this.lang, GIVEN_AMOUNT));
                player.closeInventory();
            });

        this.setItem(
            6,
            5,
            ItemUtils.create(Material.REDSTONE_TORCH, meta ->
                meta.displayName(LangOptions.inventory_editorglow_back.getComponent(lang))),
            event -> new EditorMainMenu(this.plugin, lang, this.activity).open(event.getPlayer()));
    }

    private void giveGlowing(@NonNull Player player, @NonNull GlowColor color) {
        GlowingBarrierItems.give(player, GlowingBarrierItems.createGlowing(
            this.plugin,
            this.lang,
            color,
            this.activity.getGlowMode(),
            GIVEN_AMOUNT));
        player.sendMessage(LangOptions.inventory_editorglow_given.getComponent(
            lang, new Placeholders("%color%", color.getDisplayNameString(lang))));
        player.closeInventory();
    }

    private void changeDistance(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorglow_distance_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editorglow_distance_request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editorglow_distance_timeout.getComponent(lang));
                return;
            }

            double distance;
            try {
                distance = Double.parseDouble(message.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                player.sendMessage(LangOptions.inventory_editorglow_distance_invalid.getComponent(lang));
                return;
            }
            if (distance < WorldSettings.MIN_VIEW_DISTANCE || distance > WorldSettings.MAX_VIEW_DISTANCE) {
                player.sendMessage(LangOptions.inventory_editorglow_distance_invalid.getComponent(lang));
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                this.activity.getLevel().getLevelSettings().getWorldSettings().setGlowViewDistance(distance);
                player.sendMessage(LangOptions.inventory_editorglow_distance_success.getComponent(lang,
                    new Placeholders("%distance%", String.format(java.util.Locale.ROOT, "%.1f",
                        this.activity.getLevel().getLevelSettings()
                            .getWorldSettings().getGlowViewDistance()))));
                new GlowingBarriersMenu(this.plugin, this.lang, this.activity).open(player);
            });
        });
    }

    private void switchMode(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        this.activity.setGlowMode(this.activity.getGlowMode().next());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f);
        this.rewriteCarriedBarriers(player);
        this.updateItems();
    }

    /**
     * The settings are baked into the item when it is taken, so barriers already in the
     * inventory are rewritten too. Otherwise switching the mode looks like it does nothing.
     */
    private void rewriteCarriedBarriers(@NonNull Player player) {
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            org.bukkit.inventory.ItemStack stack = contents[slot];
            if (!GlowingBarrierItems.isGlowing(this.plugin, stack)) continue;
            player.getInventory().setItem(slot, GlowingBarrierItems.createGlowing(
                this.plugin,
                this.lang,
                GlowingBarrierItems.readColor(this.plugin, stack),
                this.activity.getGlowMode(),
                stack.getAmount()));
        }
    }
}
