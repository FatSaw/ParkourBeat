package ru.sortix.parkourbeat.packrelay.slicer;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Приём заказов на нарезку трека от бэкенда.
 * <p>
 * Сама нарезка идёт в отдельном однопоточном пуле: ffmpeg перекодирует песню целиком,
 * это секунды, и держать на этом сетевой поток прокси нельзя. Один поток выбран
 * намеренно — параллельные ffmpeg на игровой машине съедят всё CPU разом.
 */
public final class SlicerBridge {
    public static final ChannelIdentifier CHANNEL =
        MinecraftChannelIdentifier.create("parkourbeat", "slicer");

    private final Object plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final TrackSlicer slicer;
    private final AMusicIndexer indexer;
    private final TrackAnalyzer analyzer;

    /** Разборы, которые уже считаются: повторный заказ того же трека игнорируется. */
    private final Set<String> analyzing = ConcurrentHashMap.newKeySet();

    /**
     * Готовые разборы. Считать спектр трёхминутной песни - это секунда работы и
     * несколько сотен мегабайт памяти на время прохода, а строитель жмёт кнопку по
     * десять раз подряд, подбирая сложность.
     */
    private final java.util.Map<String, TrackAnalyzer.Result> analysisCache = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ParkourBeat-TrackSlicer");
        thread.setDaemon(true);
        return thread;
    });

    /** Уровни, для которых нарезка уже крутится: повторный заказ игнорируется. */
    private final Set<String> slicing = ConcurrentHashMap.newKeySet();

    /**
     * Ограничители нагрузки. Нарезка — это полное перекодирование песни через ffmpeg;
     * без потолка десяток строителей, зажавших кнопку, положили бы CPU прокси, а очередь
     * однопоточного пула росла бы неограниченно.
     */
    private static final int MAX_QUEUED = 4;
    private static final long PLAYER_COOLDOWN_MILLIS = 20_000L;
    private final java.util.concurrent.atomic.AtomicInteger queued =
        new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.Map<UUID, Long> lastRequestByPlayer = new ConcurrentHashMap<>();

    /** Максимальная длина строк из сети: защита от мусорных пакетов. */
    private static final int MAX_ID_LENGTH = 80;

    public SlicerBridge(Object plugin, ProxyServer server, Logger logger,
                        TrackSlicer slicer, AMusicIndexer indexer, TrackAnalyzer analyzer) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        this.slicer = slicer;
        this.indexer = indexer;
        this.analyzer = analyzer;
    }

    public void register() {
        this.server.getChannelRegistrar().register(CHANNEL);
        this.logger.info("Slicer channel {} registered", CHANNEL.getId());
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.getId().equals(event.getIdentifier().getId())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection connection)) {
            // Сообщение пришло от КЛИЕНТА, а не от бэкенда. Заказы принимаются только
            // от сервера, но знать о таких попытках полезно.
            this.logger.warn("Ignored slicer message from a non-server source");
            return;
        }
        Player player = connection.getPlayer();

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String action = in.readUTF();
            switch (action) {
                case "slice": {
                    String levelId = in.readUTF();
                    String sourceTrackId = in.readUTF();
                    String playlistId = in.readUTF();
                    int count = in.readInt();
                    // Размер приходит из сети: без потолка отрицательное или огромное
                    // значение уронило бы прокси на выделении списка.
                    if (count <= 0 || count > TrackSlicer.MAX_CHECKPOINTS) return;
                    List<Integer> offsets = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) offsets.add(in.readInt());
                    this.logger.info("Slice request from {}: track={} playlist={} marks={}",
                        player.getUsername(), sourceTrackId, playlistId, offsets.size());
                    this.handleSlice(player, levelId, sourceTrackId, playlistId, offsets);
                    return;
                }
                case "analyze": {
                    String trackId = in.readUTF();
                    this.handleAnalyze(player, trackId);
                    return;
                }
                case "drop": {
                    String levelId = in.readUTF();
                    String playlistId = in.readUTF();
                    this.handleDrop(player, levelId, playlistId);
                    return;
                }
                default:
                    this.logger.warn("Unknown slicer action: {}", action);
            }
        } catch (Exception e) {
            this.logger.warn("Unable to handle slicer message", e);
        }
    }

    private void handleSlice(Player player, String levelId, String sourceTrackId,
                             String playlistId, List<Integer> offsets) {
        if (offsets.isEmpty()) {
            this.writeFailed(player, levelId, "не передано ни одной отметки");
            return;
        }
        if (sourceTrackId.length() > MAX_ID_LENGTH || playlistId.length() > MAX_ID_LENGTH
            || levelId.length() > 64) {
            this.writeFailed(player, levelId, "слишком длинный идентификатор");
            return;
        }

        UUID playerId = player.getUniqueId();

        Long last = this.lastRequestByPlayer.get(playerId);
        long now = System.currentTimeMillis();
        if (last != null && now - last < PLAYER_COOLDOWN_MILLIS) {
            this.writeFailed(player, levelId, "слишком часто, подождите "
                + ((PLAYER_COOLDOWN_MILLIS - (now - last)) / 1000 + 1) + " сек");
            return;
        }
        if (this.queued.get() >= MAX_QUEUED) {
            this.writeFailed(player, levelId, "очередь нарезки переполнена, попробуйте позже");
            return;
        }
        if (!this.slicing.add(levelId)) {
            this.writeFailed(player, levelId, "нарезка этого уровня уже идёт");
            return;
        }
        this.lastRequestByPlayer.put(playerId, now);
        this.queued.incrementAndGet();
        this.writeProgress(player, levelId, "режем трек, это займёт несколько секунд...");

        this.executor.execute(() -> {
            TrackSlicer.Result result = null;
            try {
                if (!this.slicer.isAvailable()) {
                    result = null;
                } else {
                    result = this.slicer.slice(sourceTrackId, playlistId, offsets);
                }
            } catch (Throwable t) {
                this.logger.warn("Slicing of {} failed", sourceTrackId, t);
                result = TrackSlicer.Result.fail("внутренняя ошибка: " + t.getMessage());
            } finally {
                this.slicing.remove(levelId);
                this.queued.decrementAndGet();
            }

            // Плейлист только что появился на диске, а AMusic наполняет свой список
            // ровно один раз при старте. Без принудительной индексации пак по нему
            // не соберётся, и игроку пришлось бы перезаходить на сервер.
            if (result != null && result.success) {
                try {
                    this.indexer.index(playlistId);
                } catch (Throwable t) {
                    this.logger.warn("Unable to index playlist {}", playlistId, t);
                }
            }

            final TrackSlicer.Result finalResult = result;
            this.server.getScheduler().buildTask(this.plugin, () -> {
                Optional<Player> target = this.server.getPlayer(playerId);
                if (target.isEmpty()) return;

                if (finalResult == null) {
                    this.writeFailed(target.get(), levelId,
                        "ffmpeg не найден на прокси, обратитесь к администрации");
                    return;
                }
                if (!finalResult.success) {
                    this.writeFailed(target.get(), levelId, finalResult.error);
                    return;
                }
                this.writeSliced(target.get(), levelId, playlistId,
                    finalResult.offsetsMillis, finalResult.durationsMillis);
            }).schedule();
        });
    }

    /**
     * Разбор трека для ритм-режима.
     * <p>
     * Считается в том же однопоточном пуле, что и нарезка: обе задачи тяжёлые, и
     * запускать их одновременно на игровой машине незачем.
     */
    private void handleAnalyze(Player player, String trackId) {
        if (trackId.length() > MAX_ID_LENGTH) return;

        TrackAnalyzer.Result cached = this.analysisCache.get(trackId);
        if (cached != null) {
            this.writeAnalysis(player, trackId, cached);
            return;
        }

        if (!this.analyzing.add(trackId)) {
            this.writeAnalysisFailed(player, trackId, "разбор этого трека уже идёт");
            return;
        }

        UUID playerId = player.getUniqueId();
        this.logger.info("Analyze request from {}: track={}", player.getUsername(), trackId);

        this.executor.execute(() -> {
            TrackAnalyzer.Result result;
            try {
                result = this.analyzer.analyze(trackId);
            } catch (Throwable t) {
                this.logger.warn("Analysis of {} failed", trackId, t);
                result = null;
            } finally {
                this.analyzing.remove(trackId);
            }

            if (result != null && result.success) this.analysisCache.put(trackId, result);

            final TrackAnalyzer.Result finalResult = result;
            this.server.getScheduler().buildTask(this.plugin, () -> {
                Optional<Player> target = this.server.getPlayer(playerId);
                if (target.isEmpty()) return;

                if (finalResult == null) {
                    this.writeAnalysisFailed(target.get(), trackId, "внутренняя ошибка разбора");
                } else if (!finalResult.success) {
                    this.writeAnalysisFailed(target.get(), trackId, finalResult.error);
                } else {
                    this.writeAnalysis(target.get(), trackId, finalResult);
                }
            }).schedule();
        });
    }

    /**
     * Ответ с разбором.
     * <p>
     * Сила удара едет одним байтом (0..100), полоса - тоже одним. Дробные типы в
     * протоколе не нужны: на раскладку нот такой точности хватает с запасом, а пакет
     * получается втрое короче.
     */
    private void writeAnalysis(Player player, String trackId, TrackAnalyzer.Result result) {
        this.write(player, out -> {
            out.writeUTF("analysis");
            out.writeUTF(trackId);
            out.writeInt(result.durationMillis);
            out.writeInt((int) Math.round(result.bpm * 100.0D));
            out.writeInt(result.firstBeatMillis);
            out.writeInt(result.onsets.size());
            for (TrackAnalyzer.Onset onset : result.onsets) {
                out.writeInt(onset.millis);
                out.writeByte((int) Math.round(onset.strength * 100.0D));
                out.writeByte(onset.band);
            }
        });
    }

    private void writeAnalysisFailed(Player player, String trackId, String reason) {
        this.logger.warn("Analysis failed for {}: {}", trackId, reason);
        this.write(player, out -> {
            out.writeUTF("analysis_failed");
            out.writeUTF(trackId);
            out.writeUTF(reason == null ? "неизвестная причина" : reason);
        });
    }

    private void handleDrop(Player player, String levelId, String playlistId) {
        if (playlistId.length() > MAX_ID_LENGTH) return;
        UUID playerId = player.getUniqueId();
        this.executor.execute(() -> {
            this.slicer.drop(playlistId);
            this.server.getScheduler().buildTask(this.plugin, () -> {
                Optional<Player> target = this.server.getPlayer(playerId);
                target.ifPresent(value -> this.write(value, out -> {
                    out.writeUTF("dropped");
                    out.writeUTF(levelId);
                }));
            }).schedule();
        });
    }

    private void writeSliced(Player player, String levelId, String playlistId,
                             List<Integer> offsets, List<Integer> durations) {
        this.write(player, out -> {
            out.writeUTF("sliced");
            out.writeUTF(levelId);
            out.writeUTF(playlistId);
            out.writeInt(offsets.size());
            for (int offset : offsets) out.writeInt(offset);
            out.writeInt(durations.size());
            for (int duration : durations) out.writeInt(duration);
        });
    }

    private void writeFailed(Player player, String levelId, String reason) {
        this.logger.warn("Slice failed for {} (level {}): {}", player.getUsername(), levelId, reason);
        this.write(player, out -> {
            out.writeUTF("slice_failed");
            out.writeUTF(levelId);
            out.writeUTF(reason == null ? "неизвестная причина" : reason);
        });
    }

    private void writeProgress(Player player, String levelId, String text) {
        this.write(player, out -> {
            out.writeUTF("slice_progress");
            out.writeUTF(levelId);
            out.writeUTF(text);
        });
    }

    private interface Writer {
        void write(DataOutputStream out) throws Exception;
    }

    private void write(Player player, Writer writer) {
        Optional<ServerConnection> connection = player.getCurrentServer();
        if (connection.isEmpty()) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writer.write(out);
            }
            if (!connection.get().sendPluginMessage(CHANNEL, bytes.toByteArray())) {
                this.logger.warn("Backend refused slicer reply for {}", player.getUsername());
            }
        } catch (Exception e) {
            this.logger.warn("Unable to send slicer message", e);
        }
    }
}
