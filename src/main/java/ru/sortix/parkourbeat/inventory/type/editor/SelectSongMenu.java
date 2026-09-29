package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
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
import ru.sortix.parkourbeat.player.music.OwnTracksManager;
import ru.sortix.parkourbeat.player.music.platform.MusicPlatform;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

import ru.sortix.parkourbeat.utils.text.PbText;
public class SelectSongMenu extends PaginatedMenu<ParkourBeat, MusicTrack> implements EditLevelMenu {
    public static final ItemStack JUKEBOX_BLOCK =
        new ItemStack(Material.JUKEBOX);
    public static final ItemStack NOTE_HEAD =
        Heads.getHeadByHash("f22e40b4bfbcc0433044d86d67685f0567025904271d0a74996afbe3f9be2c0f");

    private static final ItemStack BLACK_GLASS =
        new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    private static final long RAINBOW_PERIOD_TICKS = 7L;
    private static final NamedTextColor[] RAINBOW = {
        NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW,
        NamedTextColor.GREEN, NamedTextColor.AQUA, NamedTextColor.BLUE,
        NamedTextColor.LIGHT_PURPLE
    };

    private final @NonNull EditActivity activity;
    private final @NonNull Level level;
    private boolean showAllTracks = false;
    private int rainbowStep = 0;
    private BukkitTask rainbowTask = null;

    public SelectSongMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        this(plugin, lang, activity, false);
    }

    public SelectSongMenu(@NonNull ParkourBeat plugin, String lang,
                          @NonNull EditActivity activity, boolean showAllTracks) {
        super(plugin, 6, lang, LangOptions.inventory_editorsong_title.getComponent(lang), 0, 5 * 9);
        this.activity = activity;
        this.level = activity.getLevel();
        this.showAllTracks = showAllTracks;

        OwnTracksManager ownTracks = this.ownTracks();
        if (ownTracks == null) {
            this.showAllTracks = true;
        } else {
            ownTracks.requestOwnedTracks(activity.getPlayer());
        }

        this.updateAllItems();
        this.startRainbow();
    }

    private void startRainbow() {
        this.rainbowTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            Player viewer = this.activity.getPlayer();
            if (!viewer.isOnline()
                || viewer.getOpenInventory().getTopInventory().getHolder() != this) {
                if (this.rainbowTask != null) {
                    this.rainbowTask.cancel();
                    this.rainbowTask = null;
                }
                return;
            }
            this.rainbowStep++;
            this.setUploadItem();
        }, RAINBOW_PERIOD_TICKS, RAINBOW_PERIOD_TICKS);
    }

    @Override
    protected @NonNull Collection<MusicTrack> getAllItems() {
        Collection<MusicTrack> allTracks =
            this.plugin.get(MusicTracksManager.class).getPlatform().getAllTracks();
        if (this.showAllTracks) return allTracks;

        OwnTracksManager ownTracks = this.ownTracks();
        if (ownTracks == null) return allTracks;

        Player viewer = this.activity.getPlayer();
        java.util.List<MusicTrack> own = new java.util.ArrayList<>();
        for (MusicTrack track : allTracks) {
            if (ownTracks.owns(viewer, track.getId())) own.add(track);
        }
        return own;
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull MusicTrack musicTrack) {
        return this.createItemDisplay0(musicTrack);
    }

    protected @NonNull ItemStack createItemDisplay0(@Nullable MusicTrack musicTrack) {
        boolean isSameTrack = this.level.getLevelSettings().getGameSettings().getMusicTrack() == musicTrack;
        return ItemUtils.modifyMeta((isSameTrack ? JUKEBOX_BLOCK : NOTE_HEAD).clone(), meta -> {
            if (musicTrack == null) {
                meta.displayName(LangOptions.inventory_editorsong_nomusic_name.getComponent(lang));
                if (isSameTrack) {
                    meta.lore(LangOptions.inventory_editorsong_nomusic_lore_selected.getComponents(lang));
                } else {
                    meta.lore(LangOptions.inventory_editorsong_nomusic_lore_notselected.getComponents(lang));
                }
            } else {
                meta.displayName(LangOptions.inventory_editorsong_selectmusic_name.getComponent(lang,
                    new Placeholders("%track%", musicTrack.getName())));

                if (isSameTrack) {
                    meta.lore(LangOptions.inventory_editorsong_selectmusic_lore_selected.getComponents(lang));
                } else {
                    meta.lore(LangOptions.inventory_editorsong_selectmusic_lore_notselected.getComponents(lang));
                }
            }
        });
    }

    @Override
    protected void onPageDisplayed() {
        for (int column = 1; column <= 9; column++) {
            this.setItem(6, column, ItemUtils.modifyMeta(BLACK_GLASS.clone(),
                meta -> meta.displayName(Component.text(" "))), null);
        }

        this.setNextPageItem(6, 3);
        this.setItem(6, 5, RegularItems.closeInventory(lang), clickEvent -> clickEvent
            .getPlayer()
            .closeInventory());
        this.setPreviousPageItem(6, 7);
        this.setScopeItem();
        this.setUploadItem();

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

    /**
     * Менеджер может быть не зарегистрирован (например, при частичном обновлении плагина).
     * В этом случае меню работает по-старому, показывая все треки, вместо падения при открытии.
     */
    @javax.annotation.Nullable
    private OwnTracksManager ownTracks() {
        try {
            return this.plugin.get(OwnTracksManager.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void setScopeItem() {
        if (this.ownTracks() == null) return;

        this.setItem(6, 2, ItemUtils.create(
            this.showAllTracks ? Material.CHEST : Material.ENDER_CHEST, meta -> {
                meta.displayName(text(this.showAllTracks
                    ? Lang.raw(this.lang, "auto.select_song_menu.set_scope_item.1") : Lang.raw(this.lang, "auto.select_song_menu.set_scope_item.2")));
                java.util.List<Component> lore = new java.util.ArrayList<>();
                lore.add(Component.empty());
                if (this.showAllTracks) {
                    lore.add(text(Lang.raw(this.lang, "auto.select_song_menu.set_scope_item.3")));
                    lore.add(text(Lang.raw(this.lang, "auto.select_song_menu.set_scope_item.4")));
                } else {
                    lore.add(text(Lang.raw(this.lang, "auto.select_song_menu.set_scope_item.5")));
                    lore.add(text(Lang.raw(this.lang, "auto.select_song_menu.set_scope_item.6")));
                }
                meta.lore(lore);
            }), event -> {
            this.showAllTracks = !this.showAllTracks;
            this.updateAllItems();
        });
    }

    private void setUploadItem() {
        if (this.ownTracks() == null) return;

        NamedTextColor color = RAINBOW[Math.floorMod(this.rainbowStep, RAINBOW.length)];

        this.setItem(6, 4, ItemUtils.create(Material.MUSIC_DISC_CAT, meta -> {
            meta.displayName(Component.text(Lang.raw(this.lang, "auto.select_song_menu.set_upload_item.1"), color)
                .decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

            java.util.List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.empty());
            lore.add(text(Lang.raw(this.lang, "auto.select_song_menu.set_upload_item.2")));
            // ЛИМИТ БЕРЁТСЯ У ПРОКСИ, А НЕ ИЗ ЯЗЫКОВОГО ФАЙЛА.
            //
            // В строке перевода число было вписано руками, и настоящий лимит живёт
            // совсем в другом месте - в max-tracks-per-player на прокси. Два числа,
            // которые никак не связаны, разъезжаются при первой же правке, и меню
            // начинает уверенно врать.
            //
            // Пока прокси не назвала лимит, строки нет вовсе: промолчать честнее, чем
            // показать выдуманное число.
            OwnTracksManager tracksManager = this.ownTracks();
            int limit = tracksManager == null ? -1 : tracksManager.getTrackLimit();
            if (limit > 0) {
                lore.add(text(Lang.raw(this.lang, "auto.select_song_menu.set_upload_item.3") + limit));
            }
            lore.add(Component.empty());
            lore.add(text(Lang.raw(this.lang, "auto.select_song_menu.set_upload_item.4")));
            meta.lore(lore);
        }), event -> {
            Player player = event.getPlayer();
            OwnTracksManager manager = this.ownTracks();
            if (manager == null) return;
            player.closeInventory();
            manager.requestUploadLink(player);
        });
    }

    @NonNull
    private static Component text(@NonNull String legacy) {
        return PbText.of(legacy)
            .decoration(TextDecoration.ITALIC, false);
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
        if (this.rainbowTask != null) {
            this.rainbowTask.cancel();
            this.rainbowTask = null;
        }
        this.stopTrack(player);
    }

    private void stopTrack(@Nonnull Player player) {
        this.plugin.get(MusicTracksManager.class).getPlatform().stopPlayingTrackFull(player);
    }

    private void startTrackDownloading(@Nonnull Player player, @NonNull MusicTrack track) {
        MusicPlatform musicPlatform = this.plugin.get(MusicTracksManager.class).getPlatform();

        musicPlatform.setResourcepackTrack(player, track, success -> {
            // Выполняем действия с инвентарём и звуками строго в основном потоке
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (success) {
                    new SelectSongMenu(SelectSongMenu.this.plugin, lang, SelectSongMenu.this.activity).open(player);
                    musicPlatform.disableRepeatMode(player);
                    musicPlatform.startPlayingTrackFull(player);
                } else {
                    player.sendMessage(LangOptions.inventory_editorsong_resourcepackstatus_failed.getComponent(lang));
                    SelectSongMenu.this.plugin.getLogger().log(java.util.logging.Level.SEVERE, Lang.raw(PlayerLang.of(player), "auto.select_song_menu.start_track_downloading.1") + track.getName() + Lang.raw(PlayerLang.of(player), "auto.select_song_menu.start_track_downloading.2") + player.getName());
                }
            });
        });
    }

    private void updateAllItemsForAllEditors() {
        this.activity.updateInventoriesOfAllEditors(SelectSongMenu.class, PaginatedMenu::updateAllItems);
    }
}
