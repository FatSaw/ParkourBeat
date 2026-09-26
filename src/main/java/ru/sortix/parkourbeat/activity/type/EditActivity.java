package ru.sortix.parkourbeat.activity.type;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.EditorSessionsManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.inventory.type.editor.EditLevelMenu;
import ru.sortix.parkourbeat.item.ItemsManager;
import ru.sortix.parkourbeat.item.editor.EditorItem;
import ru.sortix.parkourbeat.item.editor.type.EditTrackPointsItem;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.LightShowPositions;
import ru.sortix.parkourbeat.levels.LightShowRunner;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.levels.settings.GlowMode;
import ru.sortix.parkourbeat.levels.settings.LevelBossBarColor;
import ru.sortix.parkourbeat.levels.settings.LightShowElement;
import ru.sortix.parkourbeat.levels.settings.SkyType;
import ru.sortix.parkourbeat.physics.CustomPhysicsManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.world.TeleportUtils;
import ru.sortix.parkourbeat.worldedit.WorldEditAccessManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class EditActivity extends UserActivity {
    @Getter
    @Setter
    private @NonNull Color currentColor = EditTrackPointsItem.DEFAULT_PARTICLES_COLOR;
    @Getter
    @Setter
    private @Nullable Color currentJumpColor = null;
    @Getter
    @Setter
    private double currentHeight = 0;
    @Getter
    @Setter
    private boolean infiniteTesting = true;
    @Getter
    @Setter
    private @Nullable LightShowElement selectedElement = null;
    @Getter
    @Setter
    private @Nullable ru.sortix.parkourbeat.levels.settings.Portal selectedPortal = null;
    @Getter
    @Setter
    private @Nullable ru.sortix.parkourbeat.levels.settings.AutoDoor selectedAutoDoor = null;
    private @Nullable ru.sortix.parkourbeat.levels.PortalRunner portalRunner = null;
    @Getter
    @Setter
    private @NonNull GlowMode glowMode = GlowMode.DEFAULT;
    @Getter
    private boolean previewEnabled = true;
    private @Nullable LightShowRunner previewRunner = null;
    private @Nullable LevelBossBarColor previewBarColor = null;
    private @Nullable PlayActivity testingActivity = null;
    private @Nullable Location creativePosition = null;
    private @Nullable ItemStack[] creativeInventoryContents = null;
    /** Автоматическая постановка маркера на каждый прыжок во время теста. */
    @Getter
    @Setter
    private boolean autoJumpMarkers = false;
    private boolean sessionRestorePending = true;
    private @Nullable Location pendingRestoreLocation = null;
    private int pendingRestoreTicks = 0;
    private final CustomPhysicsManager physicsManager;

    private static final Particle.DustOptions START_MARKER_DUST = new Particle.DustOptions(Color.GREEN, 3.5f);

    /**
     * Маркеры дуэльных трасс - в 1.8 раза мельче обычных: их четыре штуки вместо двух,
     * и стоят они рядом.
     */
    private static final float DUEL_MARKER_SIZE = 3.5f / 1.8f;
    private static final Particle.DustOptions DUEL_START_MARKER_DUST =
        new Particle.DustOptions(Color.GREEN, DUEL_MARKER_SIZE);
    private static final Particle.DustOptions DUEL_FINISH_MARKER_DUST =
        new Particle.DustOptions(Color.RED, DUEL_MARKER_SIZE);
    private static final Particle.DustOptions FINISH_MARKER_DUST = new Particle.DustOptions(Color.RED, 3.5f);

    /**
     * Раз в сколько тиков перерисовываются маркеры старта и финиша.
     * <p>
     * Раньше они спавнились каждый тик: частица живёт около секунды, поэтому в одной
     * точке одновременно висело два десятка частиц - получалось плотное мигающее пятно,
     * которое засвечивало всё вокруг. Четырёх раз в секунду достаточно, чтобы маркер
     * выглядел непрерывным, но не превращался в засветку.
     */
    private static final int MARKER_RENDER_PERIOD_TICKS = 5;

    private int editorMarkerTick = 0;

    private EditActivity(@NonNull ParkourBeat plugin, @NonNull Player player, @NonNull Level level) {
        super(plugin, player, level);
        LangOptions.level_editor_success_start.sendMsg(player, new Placeholders("%level%", ((TextComponent)this.level.getDisplayName()).content()));
        this.level.applyViewDistances();
        this.level.getLevelSettings().updateParticleLocations();
        this.level.setEditing(true);
        this.physicsManager = plugin.get(CustomPhysicsManager.class);

        Placeholders namePlaceholder = new Placeholders("%name%", player.getName());
        for (Player editor : this.getAllEditors()) {
            if (editor == player) continue;
            LangOptions.level_editor_coeditor_joined.sendMsg(editor, namePlaceholder);
        }
    }

    @Nullable
    public PlayActivity getTestingActivity() {
        return this.testingActivity;
    }

    @NonNull
    public static CompletableFuture<EditActivity> createAsync(@NonNull ParkourBeat plugin,
                                                              @NonNull Player player,
                                                              @NonNull Level level
    ) {
        UserActivity activity = plugin.get(ActivityManager.class).getActivity(player);
        if (activity instanceof EditActivity
            && activity.getLevel().getUniqueId().equals(level.getUniqueId())) {
            return CompletableFuture.completedFuture((EditActivity) activity);
        }

        if (!level.isLevelAccessibleForEditing(player, true, true)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<EditActivity> result = new CompletableFuture<>();
        Game.createAsync(plugin, player, level.getUniqueId(), false).thenAccept(game -> {
            if (game == null) {
                result.complete(null);
                return;
            }

            result.complete(new EditActivity(plugin, player, level));
        });
        return result;
    }

    /**
     * За какую сторону строитель бежит тестовый забег на дуэльной карте.
     * <p>
     * Обе трассы надо проверять, а прыжковые кольца засчитываются только на своей
     * стороне - значит, сторону нужно уметь выбирать. Переключается в меню редактора.
     */
    @Getter
    private @NonNull ru.sortix.parkourbeat.duel.DuelSide duelTestSide =
        ru.sortix.parkourbeat.duel.DuelSide.FIRST;

    public void setDuelTestSide(@NonNull ru.sortix.parkourbeat.duel.DuelSide side) {
        this.duelTestSide = side;
    }

    @Override
    public void startActivity() {
        physicsManager.addPlayer(player, level);
        if (this.testingActivity != null) {
            this.testingActivity.startActivity();
        } else {
            this.player.setGameMode(GameMode.CREATIVE);
            this.player.setFlying(true);

            this.player.getInventory().clear();
            this.restoreEditorSession();
            this.plugin.get(ItemsManager.class).putAllItems(this.player, EditorItem.class);
            // Маркеры ставятся только в тесте, в режиме постройки предмет не нужен.
            this.player.getInventory().setItem(4, null);

            boolean digger = ru.sortix.parkourbeat.digger.DiggerManager.isDigger(this.level);

            // Путь из частиц не рисуется там, где пути нет: на 2D его заменяет линия,
            // на «Копателе» - прямой тоннель, который строитель ведёт сам.
            if (!ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(this.level) && !digger) {
                this.level.getLevelSettings().getParticleController().startSpawnParticles(this.player);
            }

            if (digger) {
                // Опора под ногами восстанавливается при каждом входе в редактор.
                // Мир «Копателя» пустой, и снести единственный блок можно случайно -
                // после этого строитель проваливался бы в пустоту без всякого шанса
                // что-то построить.
                //
                // Обе высоты берутся у самого уровня: она выбирается при создании
                // (полное небо - высоко, половина - низко), и общая настройка spawn_y
                // на карте с половиной неба положила бы лишний камень в сотне блоков
                // над тоннелем, оставив строителя стоять над пустотой.
                org.bukkit.Location diggerSpawn = this.level.getSpawn();
                if (diggerSpawn != null) {
                    ru.sortix.parkourbeat.digger.DiggerWorldTemplate.restorePlatform(
                        this.level.getWorld(), diggerSpawn.getBlockY() - 1);
                }
                ru.sortix.parkourbeat.digger.DiggerWorldTemplate.ensurePlatformUnder(this.level.getSpawn());

                // Палочка пути здесь бесполезна: точки трассы не расставляются, руды
                // ставятся руками по сетке бита. Отдельных предметов режима нет
                // намеренно - всё живёт в меню параметров уровня, чтобы не плодить
                // хотбар из пяти кнопок, каждую из которых надо помнить.
                this.player.getInventory().setItem(2, null);
            }

            if (ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(this.level)) {
                ru.sortix.parkourbeat.twod.TwoDCoins.refresh(this.plugin, this.level, true);
                // Палочка пути на 2D-уровне своя: она тянет линию, а не ставит точки.
                this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class)
                    .giveEditorItems(this.player);
            }

            if (ru.sortix.parkourbeat.threesixty.ThreeSixtyManager.isThreeSixty(this.level)) {
                // Путей на 360-уровне нет, палочка пути там не нужна: вместо неё
                // выдаётся палочка финиша.
                this.plugin.get(ru.sortix.parkourbeat.threesixty.ThreeSixtyManager.class)
                    .giveEditorItems(this.player);
            }

            if (ru.sortix.parkourbeat.duel.DuelManager.isDuel(this.level)) {
                // Вместо одной палочки пути - две, по одной на сторону. И строитель
                // видит сразу обе трассы: иначе не понять, где они расходятся.
                ru.sortix.parkourbeat.duel.DuelManager duelManager =
                    this.plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class);
                duelManager.giveEditorItems(this.player);
                duelManager.showBothPaths(this.level, this.player);
            }

            this.startPreview();

            int blockLimit = this.isOwner() || this.getGameSettings().isTrusted(this.player.getUniqueId()) ? 90000 : 5000;
            this.plugin.get(WorldEditAccessManager.class).grant(this.player, blockLimit);
        }
    }

    private void restoreEditorSession() {
        if (!this.sessionRestorePending) return;
        this.sessionRestorePending = false;

        EditorSessionsManager.Session session;
        try {
            session = this.plugin.get(EditorSessionsManager.class)
                .get(this.level.getUniqueId(), this.player.getUniqueId());
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Unable to read editor session of player " + this.player.getName(), e);
            return;
        }
        if (session == null) return;

        try {
            ItemStack[] target = this.player.getInventory().getContents();
            ItemStack[] saved = session.getContents();
            for (int slot = 0; slot < target.length; slot++) {
                target[slot] = slot < saved.length ? saved[slot] : null;
            }
            this.player.getInventory().setContents(target);
            this.player.getInventory().setHeldItemSlot(session.getHeldSlot());
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Unable to restore editor inventory of player " + this.player.getName(), e);
        }

        Location saved = session.toLocation(this.level.getWorld());
        if (saved != null && this.level.isLocationInside(saved)) {
            this.pendingRestoreLocation = saved;
            this.pendingRestoreTicks = 0;
        }
    }

    public void saveEditorSession() {
        this.saveEditorSession(!this.plugin.isEnabled());
    }

    public void saveEditorSession(boolean stillActive) {
        if (this.player.getWorld() != this.level.getWorld() && this.creativeInventoryContents == null) return;

        ItemStack[] contents = this.creativeInventoryContents != null
            ? this.creativeInventoryContents
            : this.player.getInventory().getContents();

        Location location;
        if (this.creativePosition != null) {
            location = this.creativePosition;
        } else if (this.pendingRestoreLocation != null) {
            location = this.pendingRestoreLocation;
        } else {
            location = this.player.getLocation();
        }
        if (location.getWorld() != this.level.getWorld()) location = null;

        try {
            this.plugin.get(EditorSessionsManager.class).snapshot(
                this.level.getUniqueId(),
                this.player.getUniqueId(),
                contents,
                this.player.getInventory().getHeldItemSlot(),
                location,
                stillActive);
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Unable to save editor session of player " + this.player.getName(), e);
        }
    }

    private void tickSessionRestore() {
        Location target = this.pendingRestoreLocation;
        if (target == null) return;

        if (++this.pendingRestoreTicks > 200) {
            this.pendingRestoreLocation = null;
            return;
        }

        if (this.player.getWorld() != this.level.getWorld()) return;
        if (this.pendingRestoreTicks < 5) return;

        this.pendingRestoreLocation = null;
        TeleportUtils.teleportAsync(this.plugin, this.player, target);
    }

    public void setPreviewEnabled(boolean previewEnabled) {
        if (this.previewEnabled == previewEnabled) return;
        this.previewEnabled = previewEnabled;
        if (this.isTesting()) return;
        if (previewEnabled) {
            this.startPreview();
        } else {
            this.stopPreview();
            SkyType.reset(this.player);
        }
    }

    /** Версия светового шоу, на которой собран текущий предпросмотр. */
    private int previewRevision = -1;

    private void startPreview() {
        this.stopPreview();
        if (!this.previewEnabled) return;
        this.previewRevision = this.level.getLightShow().getRevision();
        this.previewRunner = new LightShowRunner(
            this.plugin, this.player, this.level.getLightShow(), barColor -> this.previewBarColor = barColor);
        this.previewRunner.startShow();
    }

    private void stopPreview() {
        if (this.previewRunner == null) return;
        this.previewRunner.shutdown();
        this.previewRunner = null;
        this.previewBarColor = null;
    }

    private void tickPreview() {
        LightShowRunner runner = this.previewRunner;
        if (runner == null) return;
        if (this.player.getWorld() != this.level.getWorld()) return;

        // Строитель что-то добавил или удалил: показ пересобираем прямо сейчас,
        // а не при следующем заходе на уровень.
        if (this.previewRevision != this.level.getLightShow().getRevision()) {
            this.startPreview();
            return;
        }

        // Идёт 2D-тест: там свой актионбар и своё оформление, лезть туда нельзя.
        if (this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class).isPlaying(this.player)) {
            return;
        }

        // ТО ЖЕ САМОЕ ДЛЯ ЗАЕЗДА В «КОПАТЕЛЕ».
        //
        // Тест запускается прямо из редактора, активность при этом остаётся
        // редакторской - и предпросмотр продолжает крутиться. А у забега теперь свой
        // проигрыватель шоу, со своими часами: они отсчитывают время ТРЕКА, тогда как
        // предпросмотр идёт по своим. Два проигрывателя ставили игроку время неба
        // каждый тик, каждый своё, и небо металось между двумя значениями туда-обратно
        // по нескольку сотен тиков в секунду.
        //
        // Хозяин здесь забег: он показывает то, что увидит игрок на самом деле.
        if (this.plugin.get(ru.sortix.parkourbeat.digger.DiggerManager.class).isPlaying(this.player)) {
            return;
        }

        boolean onLevel = LightShowPositions.getSignedDistance(this.level, this.player.getLocation()) >= 0.0D;
        int timeMillis = LightShowPositions.toTimeMillis(this.level, this.player.getLocation());
        try {
            runner.tick(onLevel ? timeMillis : -1L);
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Unable to tick lightshow preview of player " + this.player.getName(), e);
            this.stopPreview();
            return;
        }

        // Уведомление от инструмента держит актионбар за собой пару секунд.
        if (onLevel && !ru.sortix.parkourbeat.utils.text.ActionBarPriority.isBusy(this.player)) {
            this.player.sendActionBar(this.buildPreviewActionBar(timeMillis));
        }
    }

    /**
     * Прогресс для строителя: во время теста - по кубику, вне теста - по тому, где
     * стоит сам строитель относительно линии уровня.
     */
    private double twoDEditorProgress() {
        ru.sortix.parkourbeat.twod.TwoDManager manager =
            this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class);
        if (manager.isPlaying(this.player)) return manager.getProgress(this.player);

        try {
            org.bukkit.Location spawn = ru.sortix.parkourbeat.twod.TwoDGeometry.resolveCubeSpawn(this.level);
            org.bukkit.util.Vector forward = ru.sortix.parkourbeat.twod.TwoDGeometry.forwardVector(
                this.level.getLevelSettings().getDirectionChecker().direction());

            double dx = this.player.getLocation().getX() - spawn.getX();
            double dz = this.player.getLocation().getZ() - spawn.getZ();
            double projected = dx * forward.getX() + dz * forward.getZ();

            double length = this.level.getLevelSettings().getGameSettings()
                .getTwoDSettings().getLineLength();
            if (length <= 0) return 0;
            return Math.max(0, Math.min(1, projected / length));
        } catch (Throwable t) {
            return 0;
        }
    }

    @NonNull
    private net.kyori.adventure.text.Component buildPreviewActionBar(int timeMillis) {
        LevelBossBarColor barColor = this.previewBarColor != null
            ? this.previewBarColor
            : this.getGameSettings().getBossBarColor();

        double fraction;
        if (ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(this.level)) {
            // На 2D-уровне длина считается по линии, а положение - по кубику.
            // Позиция строителя тут ни при чём: раньше из-за неё бар всегда упирался
            // в 100%, потому что путь уровня для 2D не задан вообще.
            fraction = this.twoDEditorProgress();
        } else {
            double total = this.level.getLevelSettings().getTotalLevelDistance();
            double passed = Math.abs(this.level.getLevelSettings().getDirectionChecker()
                .getCoordinate(this.player.getLocation()) - this.level.getLevelSettings().getStartPosition());
            fraction = total <= 0 ? 0 : Math.max(0, Math.min(1, passed / total));
        }
        String percent = String.format(java.util.Locale.ROOT, "%.2f", fraction * 100);
        int m = Math.max(0, timeMillis / 60000);
        int s = Math.max(0, (timeMillis / 1000) % 60);
        int ms = Math.max(0, timeMillis % 1000);
        String preciseTimecode = String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", m, s, ms);

        return net.kyori.adventure.text.Component.text(percent + "%")
            .color(barColor.getTextColor())
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
            .append(net.kyori.adventure.text.Component.text(" - ")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false))
            .append(net.kyori.adventure.text.Component.text(preciseTimecode)
                .color(barColor.getTextColor())
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
    }

    public void applyBaseSky() {
        this.startPreview();
    }

    public void applyBaseSkyToAllEditors() {
        ActivityManager activityManager = this.plugin.get(ActivityManager.class);
        for (Player editor : this.getAllEditors()) {
            if (!(activityManager.getActivity(editor) instanceof EditActivity editActivity)) continue;
            if (editActivity.isTesting()) continue;
            editActivity.applyBaseSky();
        }
    }

    @Override
    public void on(@NonNull PlayerMoveEvent event) {
        if (this.testingActivity != null) this.testingActivity.on(event);
    }

    @Override
    public void onTick() {
        if (this.testingActivity != null) {
            this.testingActivity.onTick();
            return;
        }

        if (ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(this.level)) {
            // Линию видит только строитель, и только пока он строит: в тесте она
            // мешает так же, как мешала бы в обычной игре.
            this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class)
                .tickEditorLine(this.player, this.level);
        }

        this.tickSessionRestore();
        this.tickPreview();
        this.renderEditorMarkers();
        this.renderPortals();
        this.renderAutoDoors();
        this.renderHelperMarkers();
        this.renderCheckpoints();
    }

    private static final org.bukkit.Particle.DustOptions MARKER_LIME =
        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0x9BFF3C), 1.2f);
    private static final org.bukkit.Particle.DustOptions MARKER_BLUE =
        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0x2E6BFF), 1.2f);
    private static final org.bukkit.Particle.DustOptions MARKER_RED =
        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0xFF2020), 1.2f);
    private static final org.bukkit.Particle.DustOptions MARKER_WHITE =
        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFFFFF), 1.2f);
    private static final double MARKER_VIEW_DISTANCE = 64.0D;

    private int markerBlinkTick = 0;

    /**
     * Маркеры мигают быстро и рисуются крупнее частиц пути, чтобы их нельзя было
     * перепутать с самим путём. Рисуются каждый тик, поэтому мигание видно.
     */
    private void renderHelperMarkers() {
        if (this.player.getWorld() != this.level.getWorld()) return;

        java.util.List<ru.sortix.parkourbeat.levels.settings.HelperMarker> markers =
            this.level.getLightShow().getHelperMarkers();
        if (markers.isEmpty()) return;

        this.markerBlinkTick++;
        boolean firstPhase = (this.markerBlinkTick / 2) % 2 == 0;

        org.bukkit.Location playerLocation = this.player.getLocation();
        double maxDistanceSquared = MARKER_VIEW_DISTANCE * MARKER_VIEW_DISTANCE;

        for (ru.sortix.parkourbeat.levels.settings.HelperMarker helperMarker : markers) {
            org.bukkit.util.Vector marker = helperMarker.getPosition();

            double dx = marker.getX() - playerLocation.getX();
            double dy = marker.getY() - playerLocation.getY();
            double dz = marker.getZ() - playerLocation.getZ();
            if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) continue;

            org.bukkit.Particle.DustOptions dust;
            if (helperMarker.getKind()
                == ru.sortix.parkourbeat.levels.settings.HelperMarker.Kind.RIGHT) {
                dust = firstPhase ? MARKER_RED : MARKER_WHITE;
            } else {
                dust = firstPhase ? MARKER_LIME : MARKER_BLUE;
            }

            org.bukkit.Location at = new org.bukkit.Location(this.level.getWorld(),
                marker.getX(), marker.getY() + 0.2D, marker.getZ());
            this.player.spawnParticle(Particle.REDSTONE, at, 1, 0, 0, 0, 0, dust);
            this.player.spawnParticle(Particle.REDSTONE,
                at.clone().add(0, 0.6D, 0), 1, 0, 0, 0, 0, dust);
        }
    }

    private static final org.bukkit.Particle.DustOptions CHECKPOINT_DUST =
        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0x00E676), 1.6f);
    private static final org.bukkit.Particle.DustOptions CHECKPOINT_DISABLED_DUST =
        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0x9E9E9E), 1.6f);
    private static final double CHECKPOINT_VIEW_DISTANCE = 96.0D;
    private static final double CHECKPOINT_COLUMN_HEIGHT = 4.0D;

    /**
     * Чекпоинт рисуется вертикальным столбом: строителю нужно видеть его издалека,
     * чтобы понимать, на какой момент песни игрока откатит.
     */
    private void renderCheckpoints() {
        if (this.player.getWorld() != this.level.getWorld()) return;

        java.util.List<ru.sortix.parkourbeat.levels.settings.Checkpoint> checkpoints =
            this.level.getLightShow().getCheckpoints();
        if (checkpoints.isEmpty()) return;

        org.bukkit.Location playerLocation = this.player.getLocation();
        double maxDistanceSquared = CHECKPOINT_VIEW_DISTANCE * CHECKPOINT_VIEW_DISTANCE;

        for (ru.sortix.parkourbeat.levels.settings.Checkpoint checkpoint : checkpoints) {
            org.bukkit.util.Vector position = checkpoint.getPosition();

            double dx = position.getX() - playerLocation.getX();
            double dz = position.getZ() - playerLocation.getZ();
            if (dx * dx + dz * dz > maxDistanceSquared) continue;

            org.bukkit.Particle.DustOptions dust = checkpoint.isEnabled()
                ? CHECKPOINT_DUST : CHECKPOINT_DISABLED_DUST;

            for (double height = 0.0D; height <= CHECKPOINT_COLUMN_HEIGHT; height += 0.4D) {
                org.bukkit.Location at = new org.bukkit.Location(this.level.getWorld(),
                    position.getX(), position.getY() + height, position.getZ());
                this.player.spawnParticle(Particle.REDSTONE, at, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private static final org.bukkit.Particle.DustOptions DOOR_RING_DUST =
        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFAA00), 1.0f);
    private static final org.bukkit.Particle.DustOptions DOOR_RING_SELECTED_DUST =
        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0x00FF88), 1.2f);
    private static final double DOOR_PREVIEW_DISTANCE = 48.0D;

    /**
     * Радиус рисуется кольцом прямо в мире: иначе строителю пришлось бы держать число
     * в голове и на глаз прикидывать, где дверь сработает.
     * Точек берём пропорционально длине окружности, чтобы кольцо не разъезжалось на больших радиусах.
     */
    private void renderAutoDoors() {
        if (this.player.getWorld() != this.level.getWorld()) return;

        java.util.List<ru.sortix.parkourbeat.levels.settings.AutoDoor> doors =
            this.level.getLightShow().getAutoDoors();
        if (doors.isEmpty()) return;

        org.bukkit.Location playerLocation = this.player.getLocation();

        for (ru.sortix.parkourbeat.levels.settings.AutoDoor door : doors) {
            org.bukkit.Location center = door.getCenter(this.level.getWorld());
            if (center.distanceSquared(playerLocation) > DOOR_PREVIEW_DISTANCE * DOOR_PREVIEW_DISTANCE) {
                continue;
            }

            boolean selected = this.selectedAutoDoor == door;
            org.bukkit.Particle.DustOptions dust = !door.isEnabled()
                ? DOOR_RING_DUST
                : (selected ? DOOR_RING_SELECTED_DUST : DOOR_RING_DUST);

            double radius = door.getRadius();
            int points = Math.max(16, Math.min(96, (int) Math.round(radius * 8.0D)));
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                org.bukkit.Location point = center.clone().add(
                    Math.cos(angle) * radius, 0.1D, Math.sin(angle) * radius);
                this.player.spawnParticle(Particle.REDSTONE, point, 1, 0, 0, 0, 0, dust);
            }

            // Столбик над самой дверью, чтобы её было видно среди построек.
            for (double dy = 0.2D; dy <= 2.2D; dy += 0.5D) {
                this.player.spawnParticle(Particle.REDSTONE,
                    center.clone().add(0, dy, 0), 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private void renderPortals() {
        if (this.player.getWorld() != this.level.getWorld()) return;
        // ПОРТАЛОВ НА КАРТЕ «КОПАТЕЛЯ» НЕ БЫВАЕТ.
        //
        // Портал - это переход между участками трассы из точек, а трассы из точек у
        // режима нет вовсе. Рисовать их строителю означает показывать ему рамки, в
        // которые он никогда не проедет, прямо посреди тоннеля.
        if (ru.sortix.parkourbeat.digger.DiggerManager.isDigger(this.level)) return;

        if (this.portalRunner == null) {
            this.portalRunner = new ru.sortix.parkourbeat.levels.PortalRunner(this.plugin, this.level, this.player);
        }
        this.portalRunner.tick(false);
    }

    private void renderEditorMarkers() {
        // На 2D-уровне пути из частиц нет: старт и финиш там задаёт линия, и её
        // собственные столбики стоят в других местах. Старые маркеры только путают.
        if (ru.sortix.parkourbeat.twod.TwoDManager.isTwoD(this.level)) return;

        // На «Копателе» пути тоже нет: старт задан точкой отсчёта карты, финиша не
        // существует вовсе - забег кончается на последней ноте.
        if (ru.sortix.parkourbeat.digger.DiggerManager.isDigger(this.level)) return;

        if (this.player.getWorld() != this.level.getWorld()) return;

        if (++this.editorMarkerTick < MARKER_RENDER_PERIOD_TICKS) return;
        this.editorMarkerTick = 0;

        boolean threeSixty = ru.sortix.parkourbeat.threesixty.ThreeSixtyManager.isThreeSixty(this.level);

        // На 360-уровне старта нет: забег начинается с первого движения, и маркер
        // старта только сбивал бы с толку. Финиш показываем - его ставят вручную,
        // и видеть, где он, обязательно.
        if (!threeSixty) {
            Location startLoc = this.level.getLevelSettings().getStartWaypointLoc().clone().add(0, 1.5, 0);
            this.player.spawnParticle(Particle.REDSTONE, startLoc, 1, 0, 0, 0, 0, START_MARKER_DUST);
        }

        // На 360-уровне финиш подсвечивает ThreeSixtyManager - фиолетовым, как важные
        // точки в spawn-tools. Второй маркер поверх него не нужен.
        if (threeSixty) return;

        // У дуэльной карты трассы две, и концы у каждой свои. Маркеры делаем мельче
        // обычных: на четырёх чанках их сразу четыре, и крупные точки сливались бы
        // в кашу.
        if (ru.sortix.parkourbeat.duel.DuelManager.isDuel(this.level)) {
            for (ru.sortix.parkourbeat.duel.DuelSide side
                : ru.sortix.parkourbeat.duel.DuelSide.values()) {
                java.util.List<ru.sortix.parkourbeat.levels.Waypoint> path =
                    this.level.getLevelSettings().getWorldSettings().getWaypoints(side);
                if (path.isEmpty()) continue;

                Location duelStart = path.get(0).getLocation().clone().add(0, 1.5, 0);
                this.player.spawnParticle(Particle.REDSTONE, duelStart, 1, 0, 0, 0, 0,
                    DUEL_START_MARKER_DUST);

                if (path.size() < 2) continue;
                Location duelFinish = path.get(path.size() - 1).getLocation().clone().add(0, 1.5, 0);
                this.player.spawnParticle(Particle.REDSTONE, duelFinish, 1, 0, 0, 0, 0,
                    DUEL_FINISH_MARKER_DUST);
            }
            return;
        }

        Location finishLoc = this.level.getLevelSettings().getFinishWaypointLoc().clone().add(0, 1.5, 0);
        this.player.spawnParticle(Particle.REDSTONE, finishLoc, 1, 0, 0, 0, 0, FINISH_MARKER_DUST);
    }

    @Override
    public void on(@NonNull PlayerToggleSprintEvent event) {
        if (this.testingActivity != null) this.testingActivity.on(event);
    }

    @Override
    public void on(@NonNull PlayerToggleSneakEvent event) {
        if (this.testingActivity != null) this.testingActivity.on(event);
    }

    @Override
    public boolean isEditorMode() {
        return true;
    }

    @Override
    public int getFallHeight() {
        if (this.testingActivity != null) return this.testingActivity.getFallHeight();
        return this.getFallHeight(true);
    }

    @Override
    public void onPlayerFall() {
        if (this.testingActivity != null) {
            this.testingActivity.onPlayerFall();
        } else {
            TeleportUtils.teleportAsync(this.getPlugin(), this.player, this.level.getSpawn());
        }
    }

    @Override
    public void endActivity() {
        this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class)
            .stopGame(this.player, false);
        this.saveEditorSession();
        this.plugin.get(WorldEditAccessManager.class).revoke(this.player);
        physicsManager.purgePlayer(player);
        if (this.testingActivity != null) this.testingActivity.endActivity();

        this.player.setGameMode(GameMode.ADVENTURE);
        this.player.getInventory().clear();
        for (org.bukkit.potion.PotionEffect effect : this.player.getActivePotionEffects()) {
            this.player.removePotionEffect(effect.getType());
        }
        this.stopPreview();
        SkyType.reset(this.player);

        this.level.getLevelSettings().getParticleController().stopSpawnParticlesForPlayer(this.player);
        ru.sortix.parkourbeat.duel.DuelManager duelManagerOnStop =
            this.plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class);
        duelManagerOnStop.hideBothPaths(this.level, this.player);
        duelManagerOnStop.release(this.player);

        LangOptions.level_editor_success_stop.sendMsg(player, new Placeholders("%level%", ((TextComponent)this.level.getDisplayName()).content()));

        Collection<Player> remainingEditors = this.getOtherEditors();

        Placeholders namePlaceholder = new Placeholders("%name%", this.player.getName());
        for (Player editor : remainingEditors) {
            LangOptions.level_editor_coeditor_left.sendMsg(editor, namePlaceholder);
        }

        if (!remainingEditors.isEmpty()) {
            return;
        }

        this.level.getLevelSettings().getParticleController().stopSpawnParticles();

        if (this.level.isEditing()) {
            this.level.setEditing(false);
            this.plugin.get(LevelsManager.class).saveLevelSettingsAndBlocks(this.level);
        }
    }

    public void startTesting() {
        if (this.testingActivity != null) throw new IllegalArgumentException("Testing already started");

        this.plugin.get(WorldEditAccessManager.class).revoke(this.player);

        // Сторону выбираем ДО создания забега: по ней собираются прыжковые кольца
        // и проверка точности движения.
        if (ru.sortix.parkourbeat.duel.DuelManager.isDuel(this.level)) {
            this.plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class)
                .setSide(this.player, this.duelTestSide);
        }

        PlayActivity.createAsync(this.plugin, this.player, this.level.getUniqueId(), true)
            .thenAccept(playActivity -> {
                if (playActivity == null) {
                    LangOptions.level_editor_test_fail_start.sendMsg(player);
                    return;
                }

                this.creativePosition = this.player.getLocation();
                TeleportUtils.teleportAsync(this.plugin, this.player, this.level.getSpawn()).thenAccept(success -> {
                    if (!success) {
                        LangOptions.level_editor_test_fail_start.sendMsg(player);
                        return;
                    }

                    this.creativeInventoryContents = this.player.getInventory().getContents();
                    this.player.getInventory().clear();

                    this.level.getLevelSettings().getParticleController().stopSpawnParticlesForPlayer(this.player);
                    this.plugin.get(ru.sortix.parkourbeat.duel.DuelManager.class)
                        .hideBothPaths(this.level, this.player);
                    this.stopPreview();

                    this.testingActivity = playActivity;
                    this.testingActivity.getGame().setAllowEndlessRun(this.infiniteTesting);
                    this.testingActivity.startActivity();

                    // ЛЬГОТА НА ВХОД В ТЕСТ.
                    //
                    // Запуск теста меняет режим игры и телепортирует строителя. Сервер при
                    // этом сбрасывает у себя флаг спринта и шлёт "перестал бежать", а клиент
                    // об этом не знает и бежать не переставал — поэтому пакета "снова бегу"
                    // от него никогда не придёт. Наказание включалось навсегда и снималось
                    // только вручную, шифтом. Первые секунды теста за бег не наказываем.
                    try {
                        this.testingActivity.getGame().getGameMoveHandler().applyTeleportGrace(3000L);
                    } catch (Exception ignored) {
                    }

                    // Предметы выдаются ПОСЛЕ старта тестовой активности: она сама чистит
                    // инвентарь, и всё выданное до неё пропадало.
                    this.giveTestingItems();

                    LangOptions.level_editor_test_success_start.sendMsgActionbar(player);
                });
            });
    }

    private void giveTestingItems() {
        this.plugin.get(ItemsManager.class).putAllItems(this.player, EditorItem.class);
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 0 || slot == 4) continue;
            this.player.getInventory().setItem(slot, null);
        }
    }

    public void endTesting() {
        if (this.testingActivity == null) throw new IllegalArgumentException("Testing not started");

        Game testingGame = this.testingActivity.getGame();
        long millis = testingGame.getSongTimeMillis();
        String timecode = String.format(java.util.Locale.ROOT, "%02d:%02d.%03d",
            millis / 60000L, (millis / 1000L) % 60L, millis % 1000L);
        String coordinate = String.format(java.util.Locale.ROOT, "%.2f",
            this.level.getLevelSettings().getDirectionChecker().getCoordinate(this.player.getLocation()));

        TeleportUtils.teleportAsync(
            this.plugin,
            this.player,
            this.creativePosition == null ? this.level.getSpawn() : this.creativePosition
        ).thenAccept(success -> {
            this.creativePosition = null;

            if (!success) {
                LangOptions.level_editor_test_fail_stop.sendMsg(player);
                return;
            }

            this.testingActivity.endActivity();
            this.testingActivity = null;
            this.startActivity();

            this.player.getInventory().setContents(this.creativeInventoryContents);
            this.creativeInventoryContents = null;

            LangOptions.level_editor_test_success_stop.sendMsgActionbar(player);
            LangOptions.level_editor_test_success_stoptime.sendMsg(player,
                new Placeholders("%time%", timecode),
                new Placeholders("%coord%", coordinate));
        });
    }

    public boolean isTesting() {
        return this.testingActivity != null;
    }

    /**
     * Считается ли игрок хозяином уровня В РЕДАКТОРЕ.
     * <p>
     * Администратор с правом править чужие уровни получает те же кнопки, что и хозяин:
     * приватность и переименование, соредакторов, копию, удаление. Иначе он попадал бы
     * в редактор чужого уровня и не мог там ничего, кроме блоков, - а именно за этим
     * его в чужой уровень и пускают.
     */
    public boolean isOwner() {
        if (this.getGameSettings().isOwner(this.player.getUniqueId())) return true;
        return this.player.hasPermission(
            ru.sortix.parkourbeat.constant.PermissionConstants.EDIT_OTHERS_LEVELS);
    }

    @NonNull
    public GameSettings getGameSettings() {
        return this.level.getLevelSettings().getGameSettings();
    }

    @NonNull
    public Collection<Player> getAllEditors() {
        List<Player> result = new ArrayList<>();
        ActivityManager activityManager = this.plugin.get(ActivityManager.class);
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (!(activityManager.getActivity(player) instanceof EditActivity editActivity)) continue;
            if (editActivity.getLevel() != this.level) continue;
            result.add(player);
        }
        return result;
    }

    @NonNull
    public Collection<Player> getOtherEditors() {
        List<Player> result = new ArrayList<>();
        for (Player editor : this.getAllEditors()) {
            if (editor == this.player) continue;
            result.add(editor);
        }
        return result;
    }

    public <T extends EditLevelMenu> void updateInventoriesOfAllEditors(@NonNull Class<T> menuClass,
                                                                        @NonNull Consumer<T> updater
    ) {
        for (Player editor : this.getAllEditors()) {
            InventoryHolder holder = editor.getOpenInventory().getTopInventory().getHolder();
            if (holder == null) continue;
            if (!menuClass.isAssignableFrom(holder.getClass())) continue;
            updater.accept(menuClass.cast(holder));
        }
    }
}
