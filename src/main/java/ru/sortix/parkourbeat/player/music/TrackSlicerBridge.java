package ru.sortix.parkourbeat.player.music;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import ru.sortix.parkourbeat.utils.text.PbText;
/**
 * Нарезка трека живёт НЕ здесь. Сами ogg-файлы лежат на прокси, в músic-папке AMusic,
 * поэтому резать их может только плагин прокси. Этот класс — тонкий заказчик:
 * отправляет «нарежь трек X по отметкам [...]» и принимает ответ с реальными
 * длительностями кусков.
 */
public class TrackSlicerBridge implements PluginManager, PluginMessageListener {
    public static final String CHANNEL = "parkourbeat:slicer";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Разрешённые символы id плейлиста. Строка приходит по каналу плагин-сообщений и
     * уходит прямо в файловый путь на прокси — всё, что похоже на обход каталога,
     * отсекается здесь ещё до отправки и повторно на прокси.
     */
    private static final java.util.regex.Pattern SAFE_ID =
        java.util.regex.Pattern.compile("^[\\p{L}\\p{N} _-]{1,80}$");

    /**
     * Ожидающий ответа заказ. Хранится не только время, но и КТО заказал и ЧТО именно:
     * входящие плагин-сообщения на Bukkit приходят в том числе от самого клиента, и без
     * этой сверки любой игрок мог бы прислать поддельный ответ "нарезка готова" с чужим
     * id уровня и произвольным плейлистом — то есть сломать музыку на любом уровне сервера.
     */
    private static final class Pending {
        final UUID requesterId;
        final String playlistId;
        final List<Integer> requestedOffsets;
        final long startedAt;

        Pending(UUID requesterId, String playlistId, List<Integer> requestedOffsets) {
            this.requesterId = requesterId;
            this.playlistId = playlistId;
            this.requestedOffsets = new ArrayList<>(requestedOffsets);
            this.startedAt = System.currentTimeMillis();
        }
    }

    /**
     * ЗАКАЗЫ РАЗБОРА ТРЕКА для режима «Копатель».
     * <p>
     * Файлы музыки лежат на прокси, а не на игровом сервере, поэтому ни длину трека,
     * ни его темп прочитать локально нельзя. Прокси всё это и так считает - длительности
     * кусков он возвращает при нарезке, - поэтому спрашиваем у него же.
     */
    private final Map<String, java.util.function.Consumer<
        ru.sortix.parkourbeat.digger.DiggerAnalysis>> pendingAnalyze = new ConcurrentHashMap<>();

    private final Map<UUID, Pending> pendingByLevel = new ConcurrentHashMap<>();
    private static final long PENDING_TIMEOUT_MILLIS = 5L * 60L * 1000L;
    /** Больше пяти чекпоинтов не бывает, значит кусков максимум шесть. */
    private static final int MAX_SLICES = 6;

    /** Столько ударов максимум приезжает в разборе трека. Совпадает с потолком на прокси. */
    private static final int MAX_ONSETS = 1500;

    @NonNull
    private static ru.sortix.parkourbeat.digger.DiggerAnalysis.Band bandOf(int band) {
        switch (band) {
            case 0:
                return ru.sortix.parkourbeat.digger.DiggerAnalysis.Band.LOW;
            case 2:
                return ru.sortix.parkourbeat.digger.DiggerAnalysis.Band.HIGH;
            default:
                return ru.sortix.parkourbeat.digger.DiggerAnalysis.Band.MID;
        }
    }

    private final @NonNull ParkourBeat plugin;
    private volatile boolean bridgeSeen = false;

    public TrackSlicerBridge(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    /**
     * Прокси хоть раз отвечал по этому каналу за время работы сервера.
     * <p>
     * Проверять это ПЕРЕД отправкой заказа нельзя: до первого ответа флаг всегда false,
     * а первого ответа не будет, пока заказ не уйдёт. Годится только для диагностики.
     */
    public boolean isBridgeAvailable() {
        return this.bridgeSeen;
    }

    /**
     * Сколько ждём ответа прокси, прежде чем сказать игроку, что нарезчик не отвечает.
     * Перекодирование длинной песни — это десятки секунд, поэтому запас большой.
     */
    private static final long ANSWER_TIMEOUT_TICKS = 20L * 180L;

    public boolean isSlicing(@NonNull UUID levelId) {
        Pending pending = this.pendingByLevel.get(levelId);
        if (pending == null) return false;
        if (System.currentTimeMillis() - pending.startedAt > PENDING_TIMEOUT_MILLIS) {
            this.pendingByLevel.remove(levelId);
            return false;
        }
        return true;
    }

    /**
     * @return true, если строка безопасна для использования как имя папки
     */
    public static boolean isSafeId(@Nullable String id) {
        if (id == null || id.isEmpty()) return false;
        if (id.contains("..") || id.contains("/") || id.contains("\\")) return false;
        if (!id.equals(id.trim())) return false;
        return SAFE_ID.matcher(id).matches();
    }

    /**
     * Заказать нарезку. Отправляется от имени игрока: плагин-сообщения на прокси
     * ходят только через соединение игрока, ответ вернётся ему же.
     *
     * @param offsetsMillis отметки чекпоинтов от начала трека, строго по возрастанию
     * @return false, если заказ отправить не удалось
     */
    public boolean requestSlice(@NonNull Player player,
                                @NonNull UUID levelId,
                                @NonNull String sourceTrackId,
                                @NonNull String targetPlaylistId,
                                @NonNull List<Integer> offsetsMillis) {
        if (offsetsMillis.isEmpty() || offsetsMillis.size() >= MAX_SLICES) return false;
        if (this.isSlicing(levelId)) return false;
        if (!isSafeId(sourceTrackId) || !isSafeId(targetPlaylistId)) {
            this.plugin.getLogger().warning("Отклонён небезопасный id для нарезки: "
                + sourceTrackId + " -> " + targetPlaylistId);
            return false;
        }
        for (int offset : offsetsMillis) {
            if (offset <= 0) return false;
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF("slice");
                out.writeUTF(levelId.toString());
                out.writeUTF(sourceTrackId);
                out.writeUTF(targetPlaylistId);
                out.writeInt(offsetsMillis.size());
                for (int offset : offsetsMillis) out.writeInt(offset);
            }
            player.sendPluginMessage(this.plugin, CHANNEL, bytes.toByteArray());
            Pending pending = new Pending(player.getUniqueId(), targetPlaylistId, offsetsMillis);
            this.pendingByLevel.put(levelId, pending);

            // Ответа может не быть вообще: плагина на прокси нет, он старой версии или
            // упал. Молча оставлять заказ висеть нельзя — уровень навсегда останется
            // "нарезка идёт", и повторный заказ будет отклоняться.
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                // Сверяем именно тот заказ: за три минуты игрок мог заказать нарезку
                // ещё раз, и гасить чужой свежий заказ нельзя.
                if (!this.pendingByLevel.remove(levelId, pending)) return;
                if (!player.isOnline()) return;
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.request_slice.1")));
                player.sendMessage(PbText.of(
                    Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.request_slice.2")));
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.request_slice.3")));
            }, ANSWER_TIMEOUT_TICKS);
            return true;
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING, Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.request_slice.4"), e);
            return false;
        }
    }

    /**
     * Снести нарезку уровня на прокси: чекпоинты убрали, файлы больше не нужны.
     */
    public void requestDrop(@NonNull Player player, @NonNull UUID levelId, @NonNull String playlistId) {
        if (!isSafeId(playlistId)) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF("drop");
                out.writeUTF(levelId.toString());
                out.writeUTF(playlistId);
            }
            player.sendPluginMessage(this.plugin, CHANNEL, bytes.toByteArray());
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING, Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.request_drop.1"), e);
        }
    }

    /**
     * Заказать разбор трека у прокси.
     * <p>
     * Музыка лежит рядом с прокси, там же стоит ffmpeg - значит и спектр считать надо
     * там. Обратно приезжают длина, темп и список ударов с силой и частотной полосой,
     * из которых уже раскладывается карта.
     * <p>
     * Ответа может не быть вовсе: мост старой версии или прокси без ffmpeg. Поэтому у
     * заказа свой таймаут, после которого обработчик вызывается с null.
     *
     * @return false, если заказ даже не удалось отправить
     */
    public boolean requestAnalysis(@NonNull Player player,
                                   @NonNull String trackId,
                                   @NonNull java.util.function.Consumer<
                                       ru.sortix.parkourbeat.digger.DiggerAnalysis> callback
    ) {
        if (!isSafeId(trackId)) return false;

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF("analyze");
                out.writeUTF(trackId);
            }
            player.sendPluginMessage(this.plugin, CHANNEL, bytes.toByteArray());
        } catch (Exception e) {
            return false;
        }

        this.pendingAnalyze.put(trackId, callback);
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            java.util.function.Consumer<ru.sortix.parkourbeat.digger.DiggerAnalysis> pending =
                this.pendingAnalyze.remove(trackId);
            if (pending != null) pending.accept(null);
        }, ANALYZE_TIMEOUT_TICKS);
        return true;
    }

    /** Разбор трека - работа на секунды, ждём соответственно. */
    private static final long ANALYZE_TIMEOUT_TICKS = 20L * 40L;

    @Override
    public void onPluginMessageReceived(@NonNull String channel, @NonNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        this.bridgeSeen = true;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String action = in.readUTF();

            // Ответы про трек не привязаны к уровню, поэтому разбираются до чтения id.
            if (action.equals("analysis")) {
                String trackId = in.readUTF();
                int durationMillis = in.readInt();
                int bpmHundredths = in.readInt();
                int firstBeatMillis = in.readInt();
                int count = in.readInt();
                if (count < 0 || count > MAX_ONSETS) return;

                ru.sortix.parkourbeat.digger.DiggerAnalysis analysis =
                    new ru.sortix.parkourbeat.digger.DiggerAnalysis(trackId,
                        bpmHundredths / 100.0D, firstBeatMillis);
                analysis.setDurationMillis(durationMillis);

                for (int i = 0; i < count; i++) {
                    int millis = in.readInt();
                    int strength = in.readByte() & 0xFF;
                    int band = in.readByte() & 0xFF;
                    analysis.addOnset(new ru.sortix.parkourbeat.digger.DiggerAnalysis.Onset(
                        millis, strength / 100.0D, bandOf(band)));
                }
                analysis.sort();

                java.util.function.Consumer<ru.sortix.parkourbeat.digger.DiggerAnalysis> callback =
                    this.pendingAnalyze.remove(trackId);
                if (callback == null) return;
                this.plugin.getServer().getScheduler().runTask(this.plugin,
                    () -> callback.accept(analysis));
                return;
            }

            if (action.equals("analysis_failed")) {
                String trackId = in.readUTF();
                String reason = sanitizeText(in.readUTF());
                java.util.function.Consumer<ru.sortix.parkourbeat.digger.DiggerAnalysis> callback =
                    this.pendingAnalyze.remove(trackId);
                this.plugin.getLogger().warning("Разбор трека " + trackId + " не удался: " + reason);
                if (callback == null) return;
                this.plugin.getServer().getScheduler().runTask(this.plugin, () -> callback.accept(null));
                return;
            }

            UUID levelId = UUID.fromString(in.readUTF());

            switch (action) {
                case "sliced": {
                    String playlistId = in.readUTF();
                    int offsetsCount = in.readInt();
                    if (offsetsCount < 0 || offsetsCount >= MAX_SLICES) return;
                    List<Integer> offsets = new ArrayList<>(offsetsCount);
                    for (int i = 0; i < offsetsCount; i++) offsets.add(in.readInt());
                    int durationsCount = in.readInt();
                    if (durationsCount < 0 || durationsCount > MAX_SLICES) return;
                    List<Integer> durations = new ArrayList<>(durationsCount);
                    for (int i = 0; i < durationsCount; i++) durations.add(in.readInt());

                    // Ответ принимается только на СВОЙ незакрытый заказ и только от того
                    // же игрока. Всё остальное — подделка с клиента.
                    Pending pending = this.pendingByLevel.get(levelId);
                    if (!this.isTrustedAnswer(pending, player, playlistId)) {
                        this.warnSpoof(player, "sliced", levelId);
                        return;
                    }
                    if (durations.size() != offsets.size() + 1) return;
                    for (int duration : durations) {
                        if (duration <= 0) return;
                    }
                    for (int offset : offsets) {
                        if (offset <= 0) return;
                    }

                    this.plugin.getServer().getScheduler().runTask(this.plugin,
                        () -> this.onSliced(player, levelId, playlistId, offsets, durations));
                    return;
                }
                case "slice_failed": {
                    String reason = in.readUTF();
                    Pending pending = this.pendingByLevel.get(levelId);
                    if (pending == null || !pending.requesterId.equals(player.getUniqueId())) {
                        this.warnSpoof(player, "slice_failed", levelId);
                        return;
                    }
                    String safeReason = sanitizeText(reason);
                    this.plugin.getServer().getScheduler().runTask(this.plugin,
                        () -> this.onFailed(player, levelId, safeReason));
                    return;
                }
                case "slice_progress": {
                    String text = in.readUTF();
                    Pending pending = this.pendingByLevel.get(levelId);
                    if (pending == null || !pending.requesterId.equals(player.getUniqueId())) return;
                    String safeText = sanitizeText(text);
                    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                        if (player.isOnline()) player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_plugin_message_received.1") + safeText));
                    });
                    return;
                }
                case "dropped":
                    return;
                default:
                    this.plugin.getLogger().warning("Неизвестный ответ нарезчика: " + action);
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING, Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_plugin_message_received.2"), e);
        }
    }

    /**
     * Ответ настоящий: заказ по этому уровню открыт, его делал именно этот игрок, и
     * плейлист тот самый, который мы просили нарезать.
     */
    private boolean isTrustedAnswer(@Nullable Pending pending,
                                    @NonNull Player player,
                                    @NonNull String playlistId) {
        if (pending == null) return false;
        if (!pending.requesterId.equals(player.getUniqueId())) return false;
        if (!pending.playlistId.equals(playlistId)) return false;
        return isSafeId(playlistId);
    }

    private void warnSpoof(@NonNull Player player, @NonNull String action, @NonNull UUID levelId) {
        this.plugin.getLogger().warning("Отклонён неожиданный ответ нарезчика '" + action
            + Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.warn_spoof.1") + player.getName() + Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.warn_spoof.2") + levelId
            + Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.warn_spoof.3"));
    }

    /**
     * Текст из сообщения уходит игроку через legacy-сериализатор. Секции цвета и переводы
     * строк оттуда вычищаются, иначе присланная строка может подделать чужие сообщения.
     */
    @NonNull
    private static String sanitizeText(@Nullable String text) {
        if (text == null) return "неизвестная причина";
        String result = text.replace('&', ' ').replace('\u00a7', ' ')
            .replace('\n', ' ').replace('\r', ' ');
        return result.length() > 200 ? result.substring(0, 200) : result;
    }

    private void onSliced(@NonNull Player player,
                          @NonNull UUID levelId,
                          @NonNull String playlistId,
                          @NonNull List<Integer> offsets,
                          @NonNull List<Integer> durations) {
        Pending pending = this.pendingByLevel.remove(levelId);
        if (pending == null) return;

        GameSettings settings = this.findSettings(levelId);
        if (settings == null) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.1")));
            return;
        }

        // СОХРАНЯЕМ ЗАКАЗАННЫЕ ОТМЕТКИ, А НЕ ФАКТИЧЕСКИЕ.
        //
        // Прокси возвращает реальные границы кусков, а они отличаются от заказанных на
        // десятки миллисекунд: ogg режется по границам страниц. Проверка "нарезка
        // устарела" сравнивает сохранённые отметки с пересчитанными по чекпоинтам,
        // и от этого расхождения уровень после перезагрузки всегда считался неприменённым.
        // Для воспроизведения важны только длительности, они по-прежнему берутся с прокси.
        List<Integer> offsetsToStore = offsets.size() == pending.requestedOffsets.size()
            ? pending.requestedOffsets
            : offsets;

        settings.setSliceResult(playlistId, offsetsToStore, durations);
        this.saveLevel(levelId);

        // Плейлист нарезки только что появился на прокси, в списке треков его ещё нет.
        // Просим платформу подтянуть его и сразу собрать пак: иначе первый игрок будет
        // ждать сборку на входе в уровень.
        try {
            this.plugin.get(MusicTracksManager.class).getPlatform()
                .tryToLoadOrUpdateResourcepackFile(playlistId, track -> {
                });
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.2") + playlistId, e);
        }

        if (player.isOnline()) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.3")));
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.4") + durations.size()
                + Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.5") + offsets.size() + ")"));
            if (offsets.size() < pending.requestedOffsets.size()) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.6")));
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.7")
                    + pending.requestedOffsets.size() + Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.8") + offsets.size()));
                player.sendMessage(PbText.of(
                    Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.9")));
                player.sendMessage(PbText.of(
                    Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.10")));
            }
            player.sendMessage(PbText.of(
                Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_sliced.11")));
        }
    }

    private void onFailed(@NonNull Player player, @NonNull UUID levelId, @NonNull String reason) {
        this.pendingByLevel.remove(levelId);
        if (!player.isOnline()) return;
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_failed.1")));
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.track_slicer_bridge.on_failed.2") + reason));
    }

    @Nullable
    private GameSettings findSettings(@NonNull UUID levelId) {
        try {
            LevelsManager manager = this.plugin.get(LevelsManager.class);
            Level level = manager.getLoadedLevel(levelId);
            if (level != null) return level.getLevelSettings().getGameSettings();
            return manager.getAvailableLevelSettings(levelId);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveLevel(@NonNull UUID levelId) {
        try {
            LevelsManager manager = this.plugin.get(LevelsManager.class);
            Level level = manager.getLoadedLevel(levelId);
            if (level != null) manager.saveLevelSettingsAndBlocks(level);
            else manager.saveLevelSettings(levelId);
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Не удалось сохранить уровень после нарезки", e);
        }
    }

    @Override
    public void disable() {
        try {
            this.plugin.getServer().getMessenger().unregisterIncomingPluginChannel(this.plugin, CHANNEL, this);
            this.plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(this.plugin, CHANNEL);
        } catch (Exception ignored) {
        }
    }
}
