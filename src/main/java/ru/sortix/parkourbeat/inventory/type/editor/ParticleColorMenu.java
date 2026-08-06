package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.settings.ParticleColorCue;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.ChatColorPalette;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

public class ParticleColorMenu extends LightShowElementMenu<ParticleColorCue> {

    public ParticleColorMenu(@NonNull ParkourBeat plugin,
                             String lang,
                             @NonNull EditActivity activity,
                             @NonNull ParticleColorCue cue
    ) {
        super(plugin, lang, activity, cue, LangOptions.inventory_editorpcolor_title.getComponent(lang));
        this.updateItems();
    }

    @Override
    protected void addSpecificItems() {
        this.setItem(
            2,
            5,
            ItemUtils.create(Material.PAINTING, meta -> {
                meta.displayName(LangOptions.inventory_editorpcolor_color_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorpcolor_color_lore.getComponents(
                    lang, new Placeholders("%color%", this.element.getHexColor())));
            }),
            this::requestHexColor);

        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer L =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
        this.setItem(
            2,
            3,
            ItemUtils.create(Material.FIRE_CHARGE, meta -> {
                meta.displayName(L.deserialize("&6Цвет прыжков")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                lore.add(net.kyori.adventure.text.Component.empty());
                lore.add(L.deserialize("&7Цвет круга частиц прыжка в этой зоне")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(net.kyori.adventure.text.Component.empty());
                String cur;
                switch (this.element.getJumpColorMode()) {
                    case CUSTOM: cur = "&f#" + String.format("%06X", this.element.getJumpColor() & 0xFFFFFF); break;
                    case SAME: cur = "&7как цвет пути"; break;
                    default: cur = "&7инверсия цвета пути"; break;
                }
                lore.add(L.deserialize("&8Текущий: " + cur)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(L.deserialize("&8ЛКМ - ввести HEX")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(L.deserialize("&8ПКМ - сбросить (инверсия)")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(lore);
            }),
            this::requestJumpHexColor);
    }

    private void requestJumpHexColor(@NonNull ru.sortix.parkourbeat.inventory.event.ClickEvent event) {
        Player player = event.getPlayer();
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer L =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();

        if (!event.isLeft()) {
            this.element.setJumpColorMode(ParticleColorCue.JumpColorMode.INVERTED);
            this.level.refreshParticleColorCues();
            player.sendMessage(L.deserialize("&aЦвет прыжков зоны сброшен (инверсия)"));
            this.reopen(player);
            return;
        }

        player.closeInventory();
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorpcolor_request_unavailable.getComponent(lang));
            return;
        }

        ChatColorPalette.sendPalette(player);

        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editorpcolor_request_timeout.getComponent(lang));
                return;
            }
            Integer rgb = parseHex(message.trim());
            if (rgb == null) {
                player.sendMessage(LangOptions.inventory_editorpcolor_request_invalid.getComponent(lang));
                this.reopen(player);
                return;
            }
            this.element.setJumpColor(rgb);
            this.element.setJumpColorMode(ParticleColorCue.JumpColorMode.CUSTOM);
            this.level.refreshParticleColorCues();

            String hexStr = String.format("#%06X", rgb & 0xFFFFFF);
            player.sendMessage(net.kyori.adventure.text.Component.text("Вы установили цвет прыжков зоны на " + hexStr)
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));

            this.reopen(player);
        });
    }

    private void requestHexColor(@NonNull ru.sortix.parkourbeat.inventory.event.ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorpcolor_request_unavailable.getComponent(lang));
            return;
        }

        ChatColorPalette.sendPalette(player);

        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) {
                player.sendMessage(LangOptions.inventory_editorpcolor_request_timeout.getComponent(lang));
                return;
            }
            Integer rgb = parseHex(message.trim());
            if (rgb == null) {
                player.sendMessage(LangOptions.inventory_editorpcolor_request_invalid.getComponent(lang));
                this.reopen(player);
                return;
            }
            this.element.setColor(rgb);
            this.level.refreshParticleColorCues();

            String hexStr = String.format("#%06X", rgb & 0xFFFFFF);
            player.sendMessage(net.kyori.adventure.text.Component.text("вы установили цвет пути зоны на " + hexStr)
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));

            this.reopen(player);
        });
    }

    private static Integer parseHex(@NonNull String input) {
        String hex = input.startsWith("#") ? input.substring(1) : input;
        if (hex.length() != 6) return null;
        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected boolean removeElement() {
        boolean removed = this.getLightShow().removeParticleColorCue(this.element);
        if (removed) this.level.refreshParticleColorCues();
        return removed;
    }

    @Override
    protected void openListMenu(@NonNull Player player) {
        new ParticleColorsMenu(this.plugin, this.lang, this.activity).open(player);
    }

    @Override
    protected void reopen(@NonNull Player player) {
        new ParticleColorMenu(this.plugin, this.lang, this.activity, this.element).open(player);
    }
}
