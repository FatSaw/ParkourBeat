package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.Heads;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.RegularItems;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.player.music.MusicTrack;
import ru.sortix.parkourbeat.player.music.MusicTracksManager;
import ru.sortix.parkourbeat.player.music.platform.MusicPlatform;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SelectSongMenu extends PaginatedMenu<ParkourBeat, MusicTrack> {
    public static final ItemStack JUKEBOX_BLOCK =
        new ItemStack(Material.JUKEBOX);
    public static final ItemStack NOTE_HEAD =
        Heads.getHeadByHash("f22e40b4bfbcc0433044d86d67685f0567025904271d0a74996afbe3f9be2c0f");

    private final Level level;

    public SelectSongMenu(@NonNull ParkourBeat plugin, @NonNull Level level) {
        super(plugin, 6, Component.text("Выбрать музыку"), 0, 5 * 9);
        this.level = level;
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<MusicTrack> getAllItems() {
        return this.plugin.get(MusicTracksManager.class).getPlatform().getAllTracks();
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@Nullable MusicTrack musicTrack) {
        boolean isSameTrack = this.level.getLevelSettings().getGameSettings().getMusicTrack() == musicTrack;
        return ItemUtils.modifyMeta((isSameTrack ? JUKEBOX_BLOCK : NOTE_HEAD).clone(), meta -> {
            meta.displayName(Component.text(
                musicTrack == null ? "Без музыки" : musicTrack.getName()
            ).colorIfAbsent(NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            if (isSameTrack) {
                lore.add(Component.text(musicTrack == null ? "Трек не выбран" : "Трек выбран", NamedTextColor.GRAY));
            } else {
                lore.add(Component.text(musicTrack == null ? "ЛКМ - Отключить музыку" : "ЛКМ - Выбрать трек", NamedTextColor.YELLOW));
            }
            if (musicTrack != null) {
                lore.add(Component.text("ПКМ - прослушать трек", NamedTextColor.YELLOW));
            }
            meta.lore(lore);
        });
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setItem(6, 5, RegularItems.closeInventory(), clickEvent -> clickEvent
            .getPlayer()
            .closeInventory());
        this.setPreviousPageItem(6, 7);

        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        MusicTrack musicTrack = settings.getMusicTrack();

        this.setItem(6, 1,
            this.createItemDisplay(null),
            event -> {
                settings.setMusicTrack(null);
                settings.setUseTrackPieces(false);
                this.updateAllItems();
            }
        );
        this.setItem(6, 9,
            ItemUtils.modifyMeta((settings.isUseTrackPieces() ? NOTE_HEAD : JUKEBOX_BLOCK).clone(),
                meta -> {
                    meta.displayName(Component.text("Режим воспроизведения").colorIfAbsent(NamedTextColor.GOLD));
                    List<Component> lore = new ArrayList<>();
                    if (settings.isUseTrackPieces()) {
                        lore.add(Component.text("Выбран режим с привязкой музыки", NamedTextColor.YELLOW));
                        lore.add(Component.text("к позиции игрока на уровне.", NamedTextColor.YELLOW));
                        lore.add(Component.empty());
                        lore.add(Component.text("Нажмите, чтобы выбрать", NamedTextColor.YELLOW));
                        lore.add(Component.text("классический режим", NamedTextColor.YELLOW));
                    } else {
                        if (musicTrack == null) {
                            lore.add(Component.text("Выберите трек, чтобы", NamedTextColor.GRAY));
                            lore.add(Component.text("изменить режим воспроизведения", NamedTextColor.GRAY));
                        } else if (!musicTrack.isPiecesSupported()) {
                            lore.add(Component.text("Выбран классический режим.", NamedTextColor.GRAY));
                            lore.add(Component.empty());
                            lore.add(Component.text("Выбранный трек не поддерживает", NamedTextColor.GRAY));
                            lore.add(Component.text("режим с привязкой музыки", NamedTextColor.GRAY));
                            lore.add(Component.text("к позиции игрока на уровне", NamedTextColor.GRAY));
                        } else {
                            lore.add(Component.text("Выбран классический режим.", NamedTextColor.YELLOW));
                            lore.add(Component.empty());
                            lore.add(Component.text("Нажмите, чтобы выбрать", NamedTextColor.YELLOW));
                            lore.add(Component.text("режим с привязкой музыки", NamedTextColor.YELLOW));
                            lore.add(Component.text("к позиции игрока на уровне", NamedTextColor.YELLOW));
                        }
                    }
                    meta.lore(lore);
                }),
            event -> {
                boolean useTrackPieces = !settings.isUseTrackPieces();
                if (useTrackPieces && (musicTrack == null || !musicTrack.isPiecesSupported())) {
                    return;
                }
                settings.setUseTrackPieces(useTrackPieces);
                this.updateAllItems();
            }
        );
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull MusicTrack musicTrack) {
        boolean playMusicNow = !event.isLeft();
        if (playMusicNow) {
            this.startTrackDownloading(event.getPlayer(), musicTrack);
        } else {
            GameSettings settings = this.level.getLevelSettings().getGameSettings();
            settings.setMusicTrack(musicTrack);
            if (!musicTrack.isPiecesSupported()) {
                settings.setUseTrackPieces(false);
            }
            this.updateAllItems();
        }
    }

    @Override
    protected void onClose(@NonNull Player player) {
        this.stopTrack(player);
    }

    private void stopTrack(@Nonnull Player player) {
        this.plugin.get(MusicTracksManager.class).getPlatform().stopPlayingTrackFull(player);
    }

    private void startTrackDownloading(@Nonnull Player player, @NonNull MusicTrack track) {
        this.stopTrack(player);
        try {
            MusicPlatform musicPlatform = this.plugin.get(MusicTracksManager.class).getPlatform();
            musicPlatform.setResourcepackTrack(player, track);
        } catch (Throwable t) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Unable to start track " + track.getId() + " for player " + player.getName(), t);
        }
    }

    public void on(@NonNull PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();

        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                // Аix client-side inventory closing on resourcepack downloading. Must be called before starting
                // track playing as .open() method closes previously open server-side inventory
                new SelectSongMenu(this.plugin, this.activity).open(player);

                MusicPlatform musicPlatform = this.plugin.get(MusicTracksManager.class).getPlatform();
                musicPlatform.disableRepeatMode(player);
                musicPlatform.startPlayingTrackFull(player);
            }
            case DECLINED -> {
                player.sendMessage("Вы отказались от загрузки трека");
            }
            case FAILED_DOWNLOAD -> {
                player.sendMessage("Не удалось загрузить трек");
            }
            case ACCEPTED -> {
            }
        }
    }
}
