package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
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
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

public class SelectSongMenu extends PaginatedMenu<ParkourBeat, MusicTrack> implements EditLevelMenu {
    public static final ItemStack JUKEBOX_BLOCK =
        new ItemStack(Material.JUKEBOX);
    public static final ItemStack NOTE_HEAD =
        Heads.getHeadByHash("f22e40b4bfbcc0433044d86d67685f0567025904271d0a74996afbe3f9be2c0f");

    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    public SelectSongMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, LangOptions.inventory_editorsong_title.getComponent(lang), 0, 5 * 9);
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateAllItems();
    }

    @Override
    protected @NonNull Collection<MusicTrack> getAllItems() {
        return this.plugin.get(MusicTracksManager.class).getPlatform().getAllTracks();
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull MusicTrack musicTrack) {
        return this.createItemDisplay0(musicTrack);
    }

    protected @NonNull ItemStack createItemDisplay0(@Nullable MusicTrack musicTrack) {
        boolean isSameTrack = this.level.getLevelSettings().getGameSettings().getMusicTrack() == musicTrack;
        return ItemUtils.modifyMeta((isSameTrack ? JUKEBOX_BLOCK : NOTE_HEAD).clone(), meta -> {
            meta.displayName(
                musicTrack == null ? LangOptions.inventory_editorsong_nomusic_name.getComponent(lang) : LangOptions.inventory_editorsong_selectmusic_name.getComponent(lang, new Placeholders("%track%", musicTrack.getName())
            ));
            meta.lore((musicTrack == null ? isSameTrack ? LangOptions.inventory_editorsong_nomusic_lore_selected : LangOptions.inventory_editorsong_nomusic_lore_notselected : isSameTrack ? LangOptions.inventory_editorsong_selectmusic_lore_selected : LangOptions.inventory_editorsong_selectmusic_lore_notselected).getComponents(lang));
        });
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setItem(6, 5, RegularItems.closeInventory(lang), clickEvent -> clickEvent
            .getPlayer()
            .closeInventory());
        this.setPreviousPageItem(6, 7);

        GameSettings settings = this.level.getLevelSettings().getGameSettings();
        MusicTrack musicTrack = settings.getMusicTrack();

        this.setItem(6, 1,
            this.createItemDisplay0(null),
            event -> {
                settings.setMusicTrack(null);
                settings.setUseTrackPieces(false);
                this.updateAllItemsForAllEditors();
            }
        );
        this.setItem(6, 9,
            ItemUtils.modifyMeta((settings.isUseTrackPieces() ? NOTE_HEAD : JUKEBOX_BLOCK).clone(),
                meta -> {
                    meta.displayName(LangOptions.inventory_editorsong_splitmode_name.getComponent(lang));
                    meta.lore((settings.isUseTrackPieces() ? LangOptions.inventory_editorsong_splitmode_lore_pieces : musicTrack == null ? LangOptions.inventory_editorsong_splitmode_lore_notrack : musicTrack.isPiecesSupported() ? LangOptions.inventory_editorsong_splitmode_lore_single_toggleavilable : LangOptions.inventory_editorsong_splitmode_lore_single_toggleunavilable).getComponents(lang));
                }),
            event -> {
                boolean useTrackPieces = !settings.isUseTrackPieces();
                if (useTrackPieces && (musicTrack == null || !musicTrack.isPiecesSupported())) {
                    return;
                }
                settings.setUseTrackPieces(useTrackPieces);
                this.updateAllItemsForAllEditors();
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
            this.updateAllItemsForAllEditors();
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
                new SelectSongMenu(this.plugin, lang, this.activity).open(player);

                MusicPlatform musicPlatform = this.plugin.get(MusicTracksManager.class).getPlatform();
                musicPlatform.disableRepeatMode(player);
                musicPlatform.startPlayingTrackFull(player);
            }
            case DECLINED -> {
                player.sendMessage(LangOptions.inventory_editorsong_resourcepackstatus_declined.getComponent(lang));
            }
            case FAILED_DOWNLOAD -> {
            	player.sendMessage(LangOptions.inventory_editorsong_resourcepackstatus_failed.getComponent(lang));
            }
            case ACCEPTED -> {
            }
        }
    }

    private void updateAllItemsForAllEditors() {
        this.activity.updateInventoriesOfAllEditors(SelectSongMenu.class, PaginatedMenu::updateAllItems);
    }
}
