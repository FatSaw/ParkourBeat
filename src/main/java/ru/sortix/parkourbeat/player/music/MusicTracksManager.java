package ru.sortix.parkourbeat.player.music;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.inventory.type.editor.SelectSongMenu;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.player.music.platform.AMusicPlatform;
import ru.sortix.parkourbeat.player.music.platform.MusicPlatform;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MusicTracksManager implements PluginManager {
    private static final int SOUND_PIECES_SENDING_PERIOD_MILLS = 1;

    private final @NonNull ParkourBeat plugin;
    @Getter
    private final @NonNull MusicPlatform platform;
    private final ScheduledExecutorService tracksPiecesSender = Executors.newSingleThreadScheduledExecutor();
    private final List<Game> gamesWithTrackPieces = new CopyOnWriteArrayList<>();

    public MusicTracksManager(@SuppressWarnings("unused") @NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.platform = new AMusicPlatform(plugin);
        this.platform.enable();
        this.reloadAllTracksListAndMenus();
        this.tracksPiecesSender.scheduleAtFixedRate(this::sendTracksPieces, SOUND_PIECES_SENDING_PERIOD_MILLS, SOUND_PIECES_SENDING_PERIOD_MILLS, TimeUnit.MILLISECONDS);
    }

    private void reloadAllTracksListAndMenus() {
        try {
        	this.platform.reloadAllTracksList(new Runnable() {
    			@Override
    			public void run() {
    				for (Player player : MusicTracksManager.this.plugin.getServer().getOnlinePlayers()) {
    		            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SelectSongMenu menu) {
    		                menu.updateAllItems();
    		            }
    		        }
    			}
    		});
        	
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Unable to update music tracks", e);
        }
    }

    public boolean updateTrackArchives(@Nullable CommandSender sender) {
    	List<MusicTrack> allTracks = this.platform.getAllTracks();
        if (sender != null) sender.sendMessage(Component.text(
            "Обновление всех треков (" + allTracks.size() + ")...", NamedTextColor.YELLOW));
        List<MusicTrack> failedTracks = new ArrayList<>();
        final int count = allTracks.size();
        AtomicInteger finished = new AtomicInteger();
        for (MusicTrack track : allTracks) {
        	String trackId = track.getId();
        	MusicTrack oldTrack = this.platform.getTrackById(trackId);
        	Consumer<MusicTrack> newTrackConsumer = new Consumer<MusicTrack>() {
				@Override
				public void accept(MusicTrack newTrack) {
					try {
						if (oldTrack == null) {
							
						} else {
							if (newTrack == null) {
								TextComponent msg = Component.text(
				                        "Ваш ресурспак устарел", NamedTextColor.YELLOW);
				                    MusicTracksManager.this.platform.getPlayersLoadedTrack(oldTrack, new Consumer<List<Player>>() {
										@Override
										public void accept(List<Player> players) {
											if(players == null) {
												return;
											}
											for(Player player : players) {
												player.sendMessage(msg);
											}
										}
				                    });
							} else {
								TextComponent msg = Component.text("Перезагрузка трека \"" + newTrack.getName() + "\"...", NamedTextColor.YELLOW);
			                    MusicTracksManager.this.platform.getPlayersLoadedTrack(oldTrack, new Consumer<List<Player>>() {
									@Override
									public void accept(List<Player> players) {
										if(players == null) {
											return;
										}
										for(Player player : players) {
											player.sendMessage(msg);
											MusicTracksManager.this.platform.setResourcepackTrack(player, newTrack, new Consumer<Boolean>() {
												@Override
												public void accept(Boolean success) {
													
												}
											});
										}
									}
			                    });
							}
			        	}
					} catch (Throwable t) {
		                if (sender != null) {
		                	sender.sendMessage(Component.text("Не удалось обновить трек \"" + trackId + "\": " + t.getMessage() + ". Подробности в консоли", NamedTextColor.RED));
		                }
		                MusicTracksManager.this.plugin.getLogger().log(Level.SEVERE, "Unable to update file of track \"" + trackId + "\"", t);
			            failedTracks.add(oldTrack);
					}
					if(count == finished.incrementAndGet()) {
						MusicTracksManager.this.reloadAllTracksListAndMenus();
						if (failedTracks.isEmpty()) {
				            if (sender != null) sender.sendMessage(Component.text(
				                "Обновление всех треков успешно завершено", NamedTextColor.GREEN));
				        } else {
				            if (sender != null) sender.sendMessage(Component.text(
				                "Не удалось обновить некоторые треки: "
				                    + failedTracks.stream().map(MusicTrack::getId).collect(Collectors.joining(";")),
				                NamedTextColor.RED));
				        }
					}
				}
        	};
        	
        	try {
				this.platform.tryToLoadOrUpdateResourcepackFile(trackId, newTrackConsumer);
			} catch (Exception e) {
			}
        	
        }
        
        return true;
    }
    
    public boolean updateTrackArchive(@Nullable CommandSender sender, @NonNull String trackId, final boolean reload) {
    	if (sender != null) sender.sendMessage(Component.text(
                "Обновление трека \"" + trackId + "\"...", NamedTextColor.YELLOW));
            try {
                MusicTrack oldTrack = this.platform.getTrackById(trackId);
                Consumer<MusicTrack> newTrackConsumer = new Consumer<MusicTrack>() {
    				@Override
    				public void accept(MusicTrack newTrack) {
    					if (oldTrack == null) {
    		                if (newTrack == null) {
    		                    if (sender != null) sender.sendMessage(Component.text(
    		                        "Трек \"" + trackId + "\" не обнаружен", NamedTextColor.GREEN));
    		                } else {
    		                    if (sender != null) sender.sendMessage(Component.text(
    		                        "Трек \"" + trackId + "\" загружен", NamedTextColor.GREEN));
    		                }
    		            } else {
    		                if (newTrack == null) {
    		                    if (sender != null) sender.sendMessage(Component.text(
    		                        "Трек \"" + trackId + "\" устарел", NamedTextColor.GREEN));

    		                    TextComponent msg = Component.text(
    		                        "Ваш ресурспак устарел", NamedTextColor.YELLOW);
    		                    MusicTracksManager.this.platform.getPlayersLoadedTrack(oldTrack, new Consumer<List<Player>>() {
    								@Override
    								public void accept(List<Player> players) {
    									if(players == null) {
    										return;
    									}
    									for(Player player : players) {
    										player.sendMessage(msg);
    									}
    								}
    		                    });
    		                } else {
    		                    if (sender != null) sender.sendMessage(Component.text(
    		                        "Трек \"" + trackId + "\" обновлён", NamedTextColor.GREEN));

    		                    TextComponent msg = Component.text("Перезагрузка трека \"" + newTrack.getName() + "\"...", NamedTextColor.YELLOW);
    		                    MusicTracksManager.this.platform.getPlayersLoadedTrack(oldTrack, new Consumer<List<Player>>() {
    								@Override
    								public void accept(List<Player> players) {
    									if(players == null) {
    										return;
    									}
    									for(Player player : players) {
    										player.sendMessage(msg);
    										MusicTracksManager.this.platform.setResourcepackTrack(player, newTrack, new Consumer<Boolean>() {
    											@Override
    											public void accept(Boolean success) {
    												
    											}
    										});
    									}
    								}
    		                    });
    		                }
    		            }
    		            if(reload) {
    		            	MusicTracksManager.this.reloadAllTracksListAndMenus();
    		            }
    				}
                };
                this.platform.tryToLoadOrUpdateResourcepackFile(trackId, newTrackConsumer);
                return true;
            } catch (Throwable t) {
                if (sender != null) sender.sendMessage(Component.text(
                    "Не удалось обновить трек \"" + trackId + "\": "
                        + t.getMessage() + ". Подробности в консоли", NamedTextColor.RED));
                this.plugin.getLogger().log(Level.SEVERE, "Unable to update file of track \"" + trackId + "\"", t);
                return false;
            }
    }

    public void setTrackPiecesSendingEnabled(@NonNull Game game, boolean enabled) {
        if (enabled) {
            this.gamesWithTrackPieces.add(game);
        } else {
            this.gamesWithTrackPieces.remove(game);
        }
    }

    private void sendTracksPieces() {
        try {
            for (Game game : this.gamesWithTrackPieces) {
                game.tryToSendTrackPiece();
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Unable to send track pieces:", e);
        }
    }

    @Override
    public void disable() {
        this.tracksPiecesSender.shutdown();
        this.gamesWithTrackPieces.clear();
        this.platform.disable();
    }
}
