package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.RegularItems;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LightShowPositions;
import ru.sortix.parkourbeat.levels.settings.Checkpoint;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.player.music.TrackSlicerBridge;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Чекпоинты уровня: точки, на которые игрока возвращает при проигрыше вместо вылета.
 * <p>
 * Под каждый чекпоинт трек режется на отдельный кусок, поэтому кнопка нарезки нарочно
 * сделана упрямой: нажать её надо трижды подряд. Нарезка идёт на прокси (там лежат
 * ogg-файлы) и занимает время, а лишний повторный прогон никому не нужен.
 */
public class CheckpointsMenu extends ParkourBeatInventory implements EditLevelMenu {
    private static final int[] CHECKPOINT_SLOTS = {10, 11, 12, 13, 14};

    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    /** Сколько раз подряд нажали «нарезать». Нужно три. */
    private int sliceConfirmations = 0;

    public CheckpointsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 4, lang, FallZonesMenu.text(Lang.raw(lang, "auto.checkpoints_menu.checkpoints_menu.1")));
        this.activity = activity;
        this.level = activity.getLevel();
        this.render();
    }

    @NonNull
    private GameSettings getGameSettings() {
        return this.level.getLevelSettings().getGameSettings();
    }

    /**
     * Чекпоинты в порядке прохождения уровня. Строитель ставит их как попало,
     * а трек резать надо строго по возрастанию времени.
     */
    @NonNull
    private List<Checkpoint> getOrderedCheckpoints() {
        List<Checkpoint> list = new ArrayList<>(this.level.getLightShow().getCheckpoints());
        list.sort(Comparator.comparingDouble(checkpoint ->
            LightShowPositions.getSignedDistance(this.level, checkpoint.getPosition())));
        return list;
    }

    @NonNull
    private List<Integer> getOffsetsMillis() {
        List<Integer> offsets = new ArrayList<>();
        for (Checkpoint checkpoint : this.getOrderedCheckpoints()) {
            if (!checkpoint.isEnabled()) continue;
            offsets.add(LightShowPositions.toTimeMillis(this.level, checkpoint.getPosition()));
        }
        return offsets;
    }

    private void render() {
        this.clearInventory();
        this.drawBorders();

        List<Checkpoint> checkpoints = this.getOrderedCheckpoints();
        for (int i = 0; i < checkpoints.size() && i < CHECKPOINT_SLOTS.length; i++) {
            Checkpoint checkpoint = checkpoints.get(i);
            int number = i + 1;
            this.setItem(CHECKPOINT_SLOTS[i], this.buildCheckpointIcon(checkpoint, number),
                event -> this.onCheckpointClick(event, checkpoint));
        }

        for (int i = checkpoints.size(); i < CHECKPOINT_SLOTS.length; i++) {
            int number = i + 1;
            this.setItem(CHECKPOINT_SLOTS[i], ItemUtils.create(Material.LIGHT_GRAY_STAINED_GLASS_PANE, meta -> {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.render.1") + number));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.render.2")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.render.3")));
                meta.lore(lore);
            }), null);
        }

        this.setItem(20, this.buildAddItem(), this::addCheckpoint);
        this.setItem(22, this.buildSliceItem(), this::requestSlice);
        this.setItem(24, this.buildClearItem(), this::clearCheckpoints);
        this.setItem(16, this.buildAttemptsItem(), this::cycleAttempts);

        this.setItem(31, RegularItems.closeInventory(this.lang),
            event -> new EditorMainMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    @NonNull
    private org.bukkit.inventory.ItemStack buildCheckpointIcon(@NonNull Checkpoint checkpoint, int number) {
        int millis = LightShowPositions.toTimeMillis(this.level, checkpoint.getPosition());
        boolean enabled = checkpoint.isEnabled();

        return ItemUtils.create(enabled ? Material.LIME_BANNER : Material.GRAY_BANNER, meta -> {
            meta.displayName(FallZonesMenu.text((enabled ? "&a" : "&7") + Lang.raw(this.lang, "auto.checkpoints_menu.build_checkpoint_icon.1") + number));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_checkpoint_icon.2") + TimeUtils.formatTimecode(millis)));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_checkpoint_icon.3") + checkpoint.format()));
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(enabled ? Lang.raw(this.lang, "auto.checkpoints_menu.build_checkpoint_icon.4") : Lang.raw(this.lang, "auto.checkpoints_menu.build_checkpoint_icon.5")));
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_checkpoint_icon.6")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_checkpoint_icon.7")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_checkpoint_icon.8")));
            meta.lore(lore);
        });
    }

    @NonNull
    private org.bukkit.inventory.ItemStack buildAddItem() {
        int amount = this.level.getLightShow().getCheckpointsAmount();
        boolean full = amount >= LightShowSettings.MAX_CHECKPOINTS;

        return ItemUtils.create(full ? Material.BARRIER : Material.LIME_DYE, meta -> {
            meta.displayName(FallZonesMenu.text(full
                ? Lang.raw(this.lang, "auto.checkpoints_menu.build_add_item.1")
                : Lang.raw(this.lang, "auto.checkpoints_menu.build_add_item.2")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_add_item.3") + amount
                + "&7/&f" + LightShowSettings.MAX_CHECKPOINTS));
            meta.lore(lore);
        });
    }

    @NonNull
    private org.bukkit.inventory.ItemStack buildClearItem() {
        return ItemUtils.create(Material.LAVA_BUCKET, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_clear_item.1")));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_clear_item.2")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_clear_item.3")));
            meta.lore(lore);
        });
    }

    @NonNull
    private org.bukkit.inventory.ItemStack buildSliceItem() {
        List<Integer> offsets = this.getOffsetsMillis();
        GameSettings settings = this.getGameSettings();
        MusicTrack track = settings.getMusicTrack();

        boolean noTrack = track == null;
        boolean pieces = settings.isUseTrackPieces();
        boolean noCheckpoints = offsets.isEmpty();
        boolean ready = settings.hasUsableSlices(offsets.size());
        boolean outdated = settings.isSliceOutdated(offsets);
        boolean slicing = this.plugin.get(TrackSlicerBridge.class).isSlicing(this.level.getUniqueId());

        Material material;
        if (noTrack || pieces || noCheckpoints) material = Material.STRUCTURE_VOID;
        else if (slicing) material = Material.CLOCK;
        else if (ready && !outdated) material = Material.MUSIC_DISC_PIGSTEP;
        else material = Material.SHEARS;

        int confirmations = this.sliceConfirmations;

        return ItemUtils.create(material, meta -> {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());

            if (noTrack) {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.1")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.2")));
                meta.lore(lore);
                return;
            }
            if (pieces) {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.3")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.4")));
                meta.lore(lore);
                return;
            }
            if (noCheckpoints) {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.5")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.6")));
                meta.lore(lore);
                return;
            }
            if (slicing) {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.7")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.8")));
                meta.lore(lore);
                return;
            }
            if (ready && !outdated) {
                meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.9")));
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.10") + settings.getSliceDurationsMillis().size()));
                lore.add(Component.empty());
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.11")));
                meta.lore(lore);
                return;
            }

            meta.displayName(FallZonesMenu.text(confirmations == 0
                ? Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.12")
                : Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.13") + (3 - confirmations) + Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.14")));

            if (outdated && settings.getSlicedPlaylistId() != null) {
                lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.15")));
            }
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.16")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.17") + (offsets.size() + 1)));
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(confirmations == 0
                ? Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.18")
                : Lang.raw(this.lang, "auto.checkpoints_menu.build_slice_item.19") + (3 - confirmations)));
            meta.lore(lore);
        });
    }

    @NonNull
    private org.bukkit.inventory.ItemStack buildAttemptsItem() {
        int attempts = this.getGameSettings().getCheckpointAttempts();
        return ItemUtils.create(Material.TOTEM_OF_UNDYING, meta -> {
            meta.displayName(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_attempts_item.1") + attempts));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_attempts_item.2")));
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_attempts_item.3")));
            lore.add(Component.empty());
            lore.add(FallZonesMenu.text(Lang.raw(this.lang, "auto.checkpoints_menu.build_attempts_item.4")
                + GameSettings.MAX_CHECKPOINT_ATTEMPTS + ")"));
            meta.lore(lore);
        });
    }

    private void cycleAttempts(@NonNull ClickEvent event) {
        GameSettings settings = this.getGameSettings();
        int next = settings.getCheckpointAttempts() + (event.isLeft() ? 1 : -1);
        if (next > GameSettings.MAX_CHECKPOINT_ATTEMPTS) next = GameSettings.MIN_CHECKPOINT_ATTEMPTS;
        if (next < GameSettings.MIN_CHECKPOINT_ATTEMPTS) next = GameSettings.MAX_CHECKPOINT_ATTEMPTS;
        settings.setCheckpointAttempts(next);
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        this.render();
    }

    private void onCheckpointClick(@NonNull ClickEvent event, @NonNull Checkpoint checkpoint) {
        Player player = event.getPlayer();

        if (!event.isLeft()) {
            this.level.getLightShow().removeCheckpoint(checkpoint);
            this.sliceConfirmations = 0;
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.on_checkpoint_click.1")));
            this.render();
            return;
        }

        if (event.isShift()) {
            checkpoint.setEnabled(!checkpoint.isEnabled());
            this.sliceConfirmations = 0;
            player.sendMessage(FallZonesMenu.text(checkpoint.isEnabled()
                ? Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.on_checkpoint_click.2")
                : Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.on_checkpoint_click.3")));
            this.render();
            return;
        }

        player.closeInventory();
        Location target = checkpoint.toLocation(this.level.getWorld());
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        TeleportUtils.teleportAsync(this.plugin, player, target);
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.on_checkpoint_click.4") + checkpoint.format()));
    }

    private void addCheckpoint(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        LightShowSettings lightShow = this.level.getLightShow();

        if (lightShow.getCheckpointsAmount() >= LightShowSettings.MAX_CHECKPOINTS) {
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.add_checkpoint.1")
                + LightShowSettings.MAX_CHECKPOINTS + Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.add_checkpoint.2")));
            return;
        }

        Location location = player.getLocation();
        if (location.getWorld() != this.level.getWorld()) {
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.add_checkpoint.3")));
            return;
        }

        double distance = LightShowPositions.getSignedDistance(this.level, location);
        if (distance <= 0.5D) {
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.add_checkpoint.4")));
            return;
        }

        lightShow.addCheckpoint(new Checkpoint(location.toVector()));
        this.sliceConfirmations = 0;

        int millis = LightShowPositions.toTimeMillis(this.level, location);
        player.playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.add_checkpoint.5")
            + TimeUtils.formatTimecode(millis)));
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.add_checkpoint.6")));
        this.render();
    }

    private void clearCheckpoints(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        GameSettings settings = this.getGameSettings();

        String playlistId = settings.getSlicedPlaylistId();
        if (playlistId != null) {
            this.plugin.get(TrackSlicerBridge.class)
                .requestDrop(player, this.level.getUniqueId(), playlistId);
        }

        this.level.getLightShow().clearCheckpoints();
        settings.clearSliceResult();
        this.sliceConfirmations = 0;

        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.clear_checkpoints.1")));
        this.render();
    }

    private void requestSlice(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        GameSettings settings = this.getGameSettings();
        MusicTrack track = settings.getMusicTrack();

        if (track == null || settings.isUseTrackPieces()) return;

        List<Integer> offsets = this.getOffsetsMillis();
        if (offsets.isEmpty()) return;

        TrackSlicerBridge bridge = this.plugin.get(TrackSlicerBridge.class);
        if (bridge.isSlicing(this.level.getUniqueId())) {
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.request_slice.1")));
            return;
        }

        this.sliceConfirmations++;
        if (this.sliceConfirmations < 3) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.request_slice.2")
                + (3 - this.sliceConfirmations)));
            this.render();
            return;
        }
        this.sliceConfirmations = 0;

        String playlistId = buildPlaylistId(track.getId(), settings.getUniqueNumber());
        boolean sent = bridge.requestSlice(player, this.level.getUniqueId(),
            track.getId(), playlistId, offsets);

        if (!sent) {
            player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.request_slice.3")));
            this.render();
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
        player.sendMessage(FallZonesMenu.text(Lang.raw(PlayerLang.of(player), "auto.checkpoints_menu.request_slice.4")));
        this.render();
    }

    /**
     * Плейлист нарезки делается СВОЙ НА КАЖДЫЙ УРОВЕНЬ. Один и тот же трек может стоять
     * на десятке уровней с разными чекпоинтами, и резать исходник на месте нельзя —
     * первый же сосед остался бы без музыки.
     */
    @NonNull
    public static String buildPlaylistId(@NonNull String trackId, int levelNumber) {
        String safe = trackId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe + "__cp" + levelNumber;
    }

    private void drawBorders() {
        org.bukkit.inventory.ItemStack glass = ItemUtils.create(Material.GRAY_STAINED_GLASS_PANE,
            meta -> meta.displayName(Component.empty()));
        for (int i = 0; i < 36; i++) {
            boolean border = i < 9 || i % 9 == 0 || i % 9 == 8 || i >= 27;
            if (border) this.setItem(i, glass, null);
        }
    }
}
