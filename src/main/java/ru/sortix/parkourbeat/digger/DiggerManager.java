package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.lifecycle.PluginManager;
import ru.sortix.parkourbeat.utils.text.PbText;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ВСЯ ЖИЗНЬ «КОПАТЕЛЯ» В ОДНОМ МЕНЕДЖЕРЕ.
 * <p>
 * Держит забеги, тикает их, ловит разрушения и взмахи, обслуживает строителя в
 * редакторе и, главное, стережёт творческий режим.
 * <p>
 * ПРО ТВОРЧЕСКИЙ РЕЖИМ. Он выбран не ради удобства, а ради честности: только он даёт
 * мгновенное разрушение руды, одинаковое у человека с любым железом и любым каналом.
 * Взамен он открывает половину читов сразу, поэтому здесь вырезано всё, что игрок
 * мог бы использовать: полёт, творческий инвентарь, выбрасывание предметов, слезание
 * с носителя и постройка блоков. Из творческого режима у игрока остаётся ровно одна
 * способность - ломать руду в один клик.
 */
public class DiggerManager implements PluginManager, Listener {

    private final @NonNull ParkourBeat plugin;
    private final @NonNull DiggerEditor editor;
    private final @NonNull DiggerBlockHider blockHider;
    private final @NonNull DiggerEntityHider entityHider;
    private final Map<UUID, DiggerGame> games = new HashMap<>();
    private final @Nullable BukkitTask task;

    public DiggerManager(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.editor = new DiggerEditor(plugin);
        this.blockHider = new DiggerBlockHider(plugin, this);
        this.entityHider = new DiggerEntityHider(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    @Override
    public void disable() {
        if (this.task != null) this.task.cancel();
        for (DiggerGame game : new HashMap<>(this.games).values()) {
            game.abort();
        }
        this.games.clear();
        this.editor.disable();
        this.blockHider.disable();
        this.entityHider.disable();
        HandlerList.unregisterAll(this);
    }

    // ==================== ЗАБЕГИ ====================

    /** Карта «Копателя»? Проверка живёт здесь же, где 2D-шная, и так же не бросает. */
    public static boolean isDigger(@Nullable Level level) {
        if (level == null) return false;
        try {
            return level.getLevelSettings().getGameSettings().getLevelMode().isDigger();
        } catch (Throwable t) {
            return false;
        }
    }

    @Nullable
    public DiggerGame getGame(@NonNull Player player) {
        return this.games.get(player.getUniqueId());
    }

    public boolean isPlaying(@NonNull Player player) {
        DiggerGame game = this.getGame(player);
        return game != null && game.getState() != DiggerGame.State.FINISHED;
    }

    /** Идёт ли забег ПРЯМО СЕЙЧАС, а не готовится и не завершился. */
    public boolean isRunning(@NonNull Player player) {
        DiggerGame game = this.getGame(player);
        return game != null && game.getState() == DiggerGame.State.RUNNING;
    }

    /**
     * Запустить забег.
     *
     * @return false, если карта не годится: не тот режим или на ней нет ни одной руды
     */
    public boolean start(@NonNull Player player, @NonNull Level level) {
        return this.start(player, level, false);
    }

    /**
     * @param ranked писать ли результат в статистику; тест из редактора - никогда
     */
    public boolean start(@NonNull Player player, @NonNull Level level, boolean ranked) {
        if (!level.getLevelSettings().getGameSettings().isDiggerLevel()) return false;
        if (level.getLevelSettings().getGameSettings().getDiggerSettings().getNotesCount() == 0) {
            player.sendMessage(PbText.of("&cНа этой карте нет ни одной руды-ноты."));
            return false;
        }

        this.stop(player);

        DiggerGame game = new DiggerGame(this.plugin, player, level, ranked);
        this.games.put(player.getUniqueId(), game);

        // Упавший запуск не имеет права оставить забег висеть: пока он числится живым,
        // строитель не может ни ставить блоки, ни ломать руды.
        try {
            game.start();
        } catch (Throwable t) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Копатель: не удалось запустить забег", t);
            game.abort();
            this.games.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void stop(@NonNull Player player) {
        DiggerGame game = this.games.remove(player.getUniqueId());
        if (game != null) game.abort();
    }

    private void tick() {
        if (this.games.isEmpty()) {
            this.editor.tick();
            return;
        }

        for (Map.Entry<UUID, DiggerGame> entry : new HashMap<>(this.games).entrySet()) {
            DiggerGame game = entry.getValue();
            if (!game.getPlayer().isOnline()) {
                game.abort();
                this.games.remove(entry.getKey());
                continue;
            }
            game.tick();
            if (game.getState() == DiggerGame.State.FINISHED) {
                this.games.remove(entry.getKey());
            }
        }

        this.editor.tick();
    }

    // ==================== СОБЫТИЯ ЗАБЕГА ====================

    /**
     * УДАР ПО РУДЕ ЛОВИТСЯ НА ЛЕВОМ КЛИКЕ, А НЕ НА РАЗРУШЕНИИ БЛОКА.
     * <p>
     * Так пришлось сделать не от хорошей жизни. Общий слушатель игр запрещает трогать
     * мир любому, кто не строит, и ставит клику по блоку {@code DENY}. Сервер в ответ
     * ПРЕРЫВАЕТ разрушение целиком: в творческом режиме блок ломается сразу, а раз клик
     * запрещён, то ломать нечего - и {@code BlockBreakEvent} не рождается вообще.
     * Именно поэтому в отладке не было ни одной строки про удар: события просто не
     * существовало, и чинить судейство было бессмысленно.
     * <p>
     * Левый клик приходит всегда и до всех запретов. Он и есть удар: руде в «Копателе»
     * ломаться по-настоящему не нужно - блок остаётся в мире для остальных участников
     * заезда, а игроку отправляется поддельный воздух.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLeftClick(@NonNull PlayerInteractEvent event) {
        // Время фиксируем ПЕРВЫМ делом: любая работа до этого уедет в отклонение удара.
        long arrival = System.currentTimeMillis();

        if (event.getHand() != null && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        // ВЫХОД ПРОВЕРЯЕТСЯ РАНЬШЕ ВСЕГО ОСТАЛЬНОГО.
        //
        // Он ловится на ЛЮБОЕ действие - и по воздуху, и по блоку, и левой кнопкой, и
        // правой. Игрок, который хочет выйти, жмёт не глядя, и требовать от него
        // попасть определённой кнопкой по определённой цели значит заставить его
        // тыкать ещё несколько раз.
        //
        // Отмена события здесь обязательна: без неё левый клик этим предметом ушёл бы
        // дальше по методу и был бы засчитан как удар по руде - то есть последним, что
        // видит уходящий игрок, стал бы сорванный комбо.
        if (DiggerExitItem.isQuit(event.getItem())) {
            event.setCancelled(true);
            DiggerGame quitting = this.getGame(event.getPlayer());
            if (quitting != null) quitting.quit();
            return;
        }

        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        org.bukkit.block.Block block = event.getClickedBlock();
        if (block == null) return;

        DiggerGame game = this.getGame(event.getPlayer());
        if (game == null) return;

        game.debugBreakArrived(event.useInteractedBlock() == Event.Result.DENY);
        game.onBreak(block, arrival);

        // Блок не ломается по-настоящему никогда: игрок увидит поддельный воздух,
        // остальные - целую руду.
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
    }

    /**
     * РАЗРУШЕНИЕ РУДЫ.
     * <p>
     * {@code ignoreCancelled} здесь СПЕЦИАЛЬНО выключен, и это главное в методе.
     * Общий слушатель игр запрещает менять мир любому, кто не строит: во время забега
     * (и во время теста из редактора) он отменяет разрушение раньше нас. С
     * {@code ignoreCancelled = true} наш обработчик до события просто не доходил -
     * руда не ломалась, ни один удар не засчитывался, а каждый клик уходил в холостые.
     * Выглядело это ровно как приваченный регион, потому что механизм тот же.
     * <p>
     * Отмена нам и не мешает: руда в «Копателе» и не должна ломаться по-настоящему,
     * блок остаётся в мире для остальных участников заезда. Нам нужен только сам факт
     * удара и его момент.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(@NonNull BlockBreakEvent event) {
        // Время фиксируем ПЕРВЫМ делом: любая работа до этого уедет в отклонение удара.
        long arrival = System.currentTimeMillis();

        Player player = event.getPlayer();
        DiggerGame game = this.getGame(player);
        if (game != null) {
            // Запасной путь: на сборках, где разрушение всё-таки проходит, удар уже
            // засчитан левым кликом, и повторно он не считается - нота помнит, что
            // её разобрали.
            event.setDropItems(false);
            event.setExpToDrop(0);
            game.onBreak(event.getBlock(), arrival);
            event.setCancelled(true);
            return;
        }

        // Строителя чужая отмена касается: если ему запретили ломать, ноту из карты
        // убирать нельзя.
        if (event.isCancelled()) return;
        this.editor.onBreak(event);
    }

    /** Взмах в пустоту. Если он ни во что не попал, комбо рушится. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSwing(@NonNull PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        DiggerGame game = this.getGame(event.getPlayer());
        if (game == null) return;

        // Разрушение приходит тем же тиком, но раньше взмаха не всегда - поэтому
        // решение откладывается на тик, и к нему уже видно, попал удар или нет.
        Bukkit.getScheduler().runTask(this.plugin, () -> game.onSwing(System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(@NonNull BlockPlaceEvent event) {
        if (this.isRunning(event.getPlayer())) {
            // В забеге игрок не строит: творческий режим здесь только ради скорости ломки.
            // Проверяется именно идущий забег: подготовка не должна запирать редактор.
            event.setCancelled(true);
            return;
        }
        this.editor.onPlace(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(@NonNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (this.isPlaying((Player) event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(@NonNull PlayerDropItemEvent event) {
        if (this.isPlaying(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlight(@NonNull PlayerToggleFlightEvent event) {
        if (!this.isPlaying(event.getPlayer())) return;
        event.setCancelled(true);
        event.getPlayer().setFlying(false);
        event.getPlayer().setAllowFlight(false);
    }

    /**
     * Творческий инвентарь целиком на клиенте: без этого игрок доставал бы себе
     * что угодно прямо в забеге.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventory(@NonNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (this.isRunning((Player) event.getWhoClicked())) event.setCancelled(true);
    }

    /** Слезать с носителя посреди трека нельзя: без него игрок просто останется стоять. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDismount(@NonNull org.spigotmc.event.entity.EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        DiggerGame game = this.getGame((Player) event.getEntity());
        if (game == null || game.getState() == DiggerGame.State.FINISHED) return;
        if (game.getCarrier() != null && game.getCarrier().equals(event.getDismounted())) {
            event.setCancelled(true);
        }
    }

    /**
     * ПАДЕНИЕ СО СПАВНА ВОЗВРАЩАЕТ НА СПАВН, А НЕ УБИВАЕТ.
     * <p>
     * Площадка «Копателя» - один блок в пустом мире на высоте сто двадцать. Шаг мимо
     * него - и строитель летит в бездну: смерть, экран возрождения, возврат неизвестно
     * куда. Ничего полезного в этом нет, это не игровая ситуация, а обрыв работы.
     * <p>
     * Ловим ДО опасной высоты, а не по смерти: к моменту смерти игрок уже пролетел
     * сотню блоков и увидел падение, а так его подхватывает почти сразу.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFall(@NonNull org.bukkit.event.player.PlayerMoveEvent event) {
        org.bukkit.Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        Level level = this.plugin.get(ru.sortix.parkourbeat.levels.LevelsManager.class)
            .getLoadedLevel(to.getWorld());
        if (level == null || !isDigger(level)) return;

        // ПОРОГ СЧИТАЕТСЯ ОТ СПАВНА КАРТЫ, А НЕ ОТ ОБЩЕЙ НАСТРОЙКИ.
        //
        // Высота площадки теперь выбирается при создании: у карты с полным небом это
        // сто двадцать, у карты с половиной - пятнадцать. Общий порог «spawn_y минус
        // двадцать» на второй означал бы, что ловушка стоит ВЫШЕ самой карты и хватает
        // строителя при каждом шаге с блока.
        //
        // Уровень ищется первым: это обычный поиск по карте загруженных миров, и стоит
        // он дешевле, чем ошибиться высотой.
        org.bukkit.Location spawn = level.getSpawn();
        double floor = spawn != null ? spawn.getY() : DiggerTuning.SPAWN_Y;

        // Двадцать блоков ниже пола: обычный прыжок и провал в декоре сюда не попадут,
        // а настоящее падение в пустоту попадёт обязательно.
        if (to.getY() > floor - 20) return;

        // Во время заезда падать некуда - игрок сидит в кресле. Если он всё же
        // оказался внизу, забег всё равно надо оборвать, иначе кресло уедет без него.
        this.stop(player);

        if (spawn == null) spawn = DiggerWorldTemplate.builderSpawn(to.getWorld());

        player.setFallDistance(0.0f);
        player.setVelocity(new org.bukkit.util.Vector());
        player.teleport(spawn);
    }

    @EventHandler
    public void onQuit(@NonNull PlayerQuitEvent event) {
        this.stop(event.getPlayer());
        DiggerEntityHider.forgetAll(event.getPlayer().getUniqueId());
        this.editor.forget(event.getPlayer());
    }

    // ==================== РЕДАКТОР ====================

    @NonNull
    public DiggerEditor getEditor() {
        return this.editor;
    }

    /** Уровень, который строитель правит прямо сейчас, если это карта «Копателя». */
    @Nullable
    public Level getEditedDiggerLevel(@NonNull Player player) {
        UserActivity activity = this.plugin.get(ActivityManager.class).getActivity(player);
        if (!(activity instanceof EditActivity)) return null;
        Level level = activity.getLevel();
        if (level == null) return null;
        return level.getLevelSettings().getGameSettings().isDiggerLevel() ? level : null;
    }
}
