package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.Portal;
import ru.sortix.parkourbeat.listeners.PortalWandListener;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.ChatColorPalette;

public class PortalSideMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;
    private final @NonNull Portal portal;
    private final int portalIndex;
    private final boolean entrySide;

    public PortalSideMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity,
                          @NonNull Portal portal, boolean entrySide) {
        super(plugin, 4, lang, FallZonesMenu.text(entrySide ? Lang.raw(lang, "auto.portal_side_menu.portal_side_menu.1") : Lang.raw(lang, "auto.portal_side_menu.portal_side_menu.2")));
        this.activity = activity;
        this.level = activity.getLevel();
        this.portal = portal;
        this.portalIndex = activity.getLevel().getLightShow().getPortals().indexOf(portal);
        this.entrySide = entrySide;
        this.activity.setSelectedPortal(portal);
        this.updateItems();
    }

    @NonNull
    private Portal portal() {
        java.util.List<Portal> portals = this.level.getLightShow().getPortals();
        if (portals.contains(this.portal)) return this.portal;
        if (this.portalIndex >= 0 && this.portalIndex < portals.size()) {
            return portals.get(this.portalIndex);
        }
        return this.portal;
    }

    @NonNull
    private Portal.Side side() {
        Portal current = this.portal();
        return this.entrySide ? current.getEntry() : current.getExit();
    }

    public void updateItems() {
        this.clearInventory();
        Portal.Side side = this.side();
        String accent = this.entrySide ? "&a" : "&b";

        this.setItem(1, 5, ItemUtils.create(
            this.entrySide ? Material.LIME_CONCRETE : Material.LIGHT_BLUE_CONCRETE, meta -> {
                meta.displayName(FallZonesMenu.text(accent + (this.entrySide ? Lang.raw(this.lang, "auto.portal_side_menu.update_items.1") : Lang.raw(this.lang, "auto.portal_side_menu.update_items.2"))));
                meta.lore(PortalMenu.lore("", Lang.raw(this.lang, "auto.portal_side_menu.update_items.3") + side.format(), "",
                    Lang.raw(this.lang, "auto.portal_side_menu.update_items.4"),
                    Lang.raw(this.lang, "auto.portal_side_menu.update_items.5")));
            }), event -> {
            Player player = event.getPlayer();
            if (event.isLeft()) {
                side.setPosition(player.getLocation().toVector());
            } else {
                RayTraceResult result = player.rayTraceBlocks(120.0D);
                if (result == null || result.getHitPosition() == null) {
                    player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.6")));
                    return;
                }
                Vector position = result.getHitPosition().clone();
                if (result.getHitBlockFace() != null) {
                    position.add(new Vector(
                        result.getHitBlockFace().getModX() * 0.55D,
                        result.getHitBlockFace().getModY() * 0.55D,
                        result.getHitBlockFace().getModZ() * 0.55D));
                }
                side.setPosition(position);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.8f);
            this.level.getLevelSettings().updateParticleLocations();
            this.updateItems();
        });

        this.setItem(2, 3, ItemUtils.create(Material.COMPASS, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.7")));
            meta.lore(PortalMenu.lore("", Lang.raw(this.lang, "auto.portal_side_menu.update_items.8"),
                Lang.raw(this.lang, "auto.portal_side_menu.update_items.9") + side.getFacing().getDisplayName(),
                "", Lang.raw(this.lang, "auto.portal_side_menu.update_items.10")));
        }), event -> {
            Player player = event.getPlayer();
            side.setFacing(side.getFacing().next());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            this.level.getLevelSettings().updateParticleLocations();
            this.updateItems();
        });

        this.setItem(2, 5, ItemUtils.create(Material.STRING, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.11")));
            meta.lore(PortalMenu.lore("", Lang.raw(this.lang, "auto.portal_side_menu.update_items.12"),
                Lang.raw(this.lang, "auto.portal_side_menu.update_items.13") + PortalMenu.fmt(side.getSize()) + Lang.raw(this.lang, "auto.portal_side_menu.update_items.14"),
                Lang.raw(this.lang, "auto.portal_side_menu.update_items.15") + PortalMenu.fmt(Portal.MIN_SIZE) + Lang.raw(this.lang, "auto.portal_side_menu.update_items.16") + PortalMenu.fmt(Portal.MAX_SIZE),
                "", Lang.raw(this.lang, "auto.portal_side_menu.update_items.17")));
        }), event -> {
            Player player = event.getPlayer();
            side.setSize(side.getSize() + (event.isLeft() ? 0.5D : -0.5D));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            this.updateItems();
        });

        this.setItem(2, 7, ItemUtils.create(Material.FIREWORK_STAR, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.18")));
            meta.lore(PortalMenu.lore("", Lang.raw(this.lang, "auto.portal_side_menu.update_items.19") + side.getColorHex(),
                "", Lang.raw(this.lang, "auto.portal_side_menu.update_items.20")));
        }), event -> this.requestColor(event.getPlayer()));

        this.setItem(2, 9, ItemUtils.create(
            side.isLookSet() ? Material.ENDER_EYE : Material.SPECTRAL_ARROW, meta -> {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.21")));
                meta.lore(PortalMenu.lore("",
                    Lang.raw(this.lang, "auto.portal_side_menu.update_items.22"),
                    Lang.raw(this.lang, "auto.portal_side_menu.update_items.23") + side.formatLook(),
                    "",
                    Lang.raw(this.lang, "auto.portal_side_menu.update_items.24"),
                    Lang.raw(this.lang, "auto.portal_side_menu.update_items.25"),
                    "",
                    Lang.raw(this.lang, "auto.portal_side_menu.update_items.26"),
                    Lang.raw(this.lang, "auto.portal_side_menu.update_items.27")));
            }), event -> {
            Player player = event.getPlayer();
            if (event.isLeft()) {
                side.setLook(player.getLocation().getYaw(), player.getLocation().getPitch());
                player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.28") + side.formatLook()));
            } else {
                side.clearLook();
                player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.29")));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            this.updateItems();
        });

        this.setItem(3, 5, ItemUtils.create(Material.TRIPWIRE_HOOK, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.30")));
            meta.lore(PortalMenu.lore("",
                Lang.raw(this.lang, "auto.portal_side_menu.update_items.31"),
                Lang.raw(this.lang, "auto.portal_side_menu.update_items.32"),
                "",
                Lang.raw(this.lang, "auto.portal_side_menu.update_items.33") + side.format(),
                Lang.raw(this.lang, "auto.portal_side_menu.update_items.34"),
                "",
                Lang.raw(this.lang, "auto.portal_side_menu.update_items.35"),
                Lang.raw(this.lang, "auto.portal_side_menu.update_items.36")));
        }), event -> {
            Player player = event.getPlayer();
            this.nudge(event.isLeft() ? 0.15D : -0.15D);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.4f);
            this.level.getLevelSettings().updateParticleLocations();
            this.updateItems();
        });

        this.setItem(3, 3, ItemUtils.create(Material.FISHING_ROD, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.37")));
            meta.lore(PortalMenu.lore("", Lang.raw(this.lang, "auto.portal_side_menu.update_items.38"), Lang.raw(this.lang, "auto.portal_side_menu.update_items.39")));
        }), event -> {
            Player player = event.getPlayer();
            ru.sortix.parkourbeat.listeners.PortalWandListener.give(this.plugin, player);
            player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.40")));
            player.closeInventory();
        });

        this.setItem(3, 7, ItemUtils.create(Material.ENDER_PEARL, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.41")));
            meta.lore(PortalMenu.lore("", Lang.raw(this.lang, "auto.portal_side_menu.update_items.42")));
        }), event -> {
            Player player = event.getPlayer();
            player.closeInventory();
            player.teleport(side.toLocation(this.level.getWorld()));
        });

        this.setItem(4, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.update_items.43")))
        ), event -> new PortalMenu(this.plugin, this.lang, this.activity, this.portal)
            .open(event.getPlayer()));
    }

    private void nudge(double amount) {
        Portal.Side side = this.side();
        Vector position = side.getPosition().clone();
        switch (side.getFacing()) {
            case WALL_X -> position.setX(position.getX() + amount);
            case WALL_Z -> position.setZ(position.getZ() + amount);
            case FLOOR -> position.setY(position.getY() + amount);
        }
        side.setPosition(position);
    }

    private void requestColor(@NonNull Player player) {
        player.closeInventory();
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) return;

        ChatColorPalette.sendPalette(player);
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) return;
            String hex = message.trim().startsWith("#") ? message.trim().substring(1) : message.trim();
            Color color;
            try {
                color = Color.fromRGB(Integer.valueOf(hex, 16));
            } catch (IllegalArgumentException e) {
                player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.request_color.1")));
                return;
            }
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                this.side().setColor(color);
                player.sendMessage(FallZonesMenu.text(Lang.raw(this.lang, "auto.portal_side_menu.request_color.2") + this.side().getColorHex()));
                new PortalSideMenu(this.plugin, this.lang, this.activity, this.portal, this.entrySide)
                    .open(player);
            });
        });
    }
}
