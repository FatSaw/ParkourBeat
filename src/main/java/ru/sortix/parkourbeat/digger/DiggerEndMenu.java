package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.UIHeads;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.text.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * ЭКРАН ПОСЛЕ ЗАЕЗДА.
 * <p>
 * Обычный сундук на три ряда. Стол картографа пришлось оставить: у него ванильная
 * логика рецепта, которая после каждого изменения пересчитывает слоты и выбрасывает
 * всё, что не подходит под рецепт. Кнопки в нём жить не могут в принципе - ни компас,
 * ни жемчуг, ни карта с чужой подписью там не удерживаются дольше одного тика.
 * <p>
 * Три кнопки стоят СИММЕТРИЧНО вокруг центра: слева «другие уровни», в центре «сыграть
 * ещё раз» головой из хотбара лобби, справа «вернуться на спавн». Главное действие в
 * середине потому, что курсор после закрытия предыдущего окна оказывается именно там.
 * <p>
 * ОКНО НЕ ЗАКРЫВАЕТСЯ. Забег кончился, игрок стоит в пустом тоннеле, и любой выход
 * отсюда - это одна из трёх кнопок. Попытка закрыть окно возвращает его обратно.
 */
public class DiggerEndMenu extends ParkourBeatInventory {

    private static final int ROWS = 3;

    private static final int SLOT_LEVELS = 11;
    private static final int SLOT_AGAIN = 13;
    private static final int SLOT_SPAWN = 15;

    /**
     * Волна по рамке, от середины к краям.
     * <p>
     * Цвета идут от тёмного к яркому, так что гребень волны читается как движение, а не
     * как случайная смена картинки.
     */
    private static final Material[] WAVE = {
        Material.BLACK_STAINED_GLASS_PANE,
        Material.BLACK_STAINED_GLASS_PANE,
        Material.GRAY_STAINED_GLASS_PANE,
        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE,
        Material.MAGENTA_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE,
        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        Material.GRAY_STAINED_GLASS_PANE,
    };

    /**
     * Раз во сколько тиков волна сдвигается на шаг.
     * <p>
     * Шесть тиков - это примерно три кадра волны в секунду. Медленно намеренно: каждый
     * шаг переписывает два десятка слотов и шлёт их игроку, и делать это каждый тик
     * означало бы гнать по сети полтора килобайта в секунду ради движущейся рамки.
     */
    private static final long WAVE_PERIOD_TICKS = 6L;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull Level level;

    private @javax.annotation.Nullable BukkitTask waveTask = null;
    private int wavePhase = 0;

    /** Закрывать окно можно только изнутри - по кнопке. */
    boolean allowClose = false;

    public DiggerEndMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Level level) {
        super(plugin, ROWS, lang, title(level));
        this.plugin = plugin;
        this.level = level;
        this.updateItems();
    }

    /** Заголовок окна - название уровня, обрезанное до ширины полосы заголовка. */
    @NonNull
    private static Component title(@NonNull Level level) {
        // PlainComponentSerializer, а не PlainTextComponentSerializer: в той версии
        // Adventure, что идёт с этим сервером, второго класса ещё нет.
        String name = net.kyori.adventure.text.serializer.plain.PlainComponentSerializer
            .plain().serialize(level.getDisplayName());
        if (name.isEmpty()) name = "Уровень";
        if (name.length() > 24) name = name.substring(0, 23) + "…";
        return PbText.of(Theme.V_GRAY + name);
    }

    private void updateItems() {
        this.drawBorder();

        // Описаний у кнопок нет намеренно: их всего три, названия говорят сами за
        // себя, а строка пояснения под каждой только удлиняет подсказку и заставляет
        // читать вместо того, чтобы нажимать.
        this.setItem(SLOT_LEVELS, ItemUtils.create(Material.COMPASS, meta -> {
            meta.displayName(PbText.item(Theme.V_AQUA + "&lДругие уровни"));
        }), event -> {
            this.click(event.getPlayer());
            this.openLevelsList(event.getPlayer());
        });

        this.setItem(SLOT_AGAIN, ItemUtils.modifyMeta(UIHeads.PLAY.clone(), meta -> {
            meta.displayName(PbText.item(Theme.V_GREEN + "&lСыграть ещё раз"));
        }), event -> {
            this.click(event.getPlayer());
            this.playAgain(event.getPlayer());
        });

        this.setItem(SLOT_SPAWN, ItemUtils.create(Material.ENDER_PEARL, meta -> {
            meta.displayName(PbText.item(Theme.V_YELLOW + "&lВернуться на спавн"));
        }), event -> {
            this.click(event.getPlayer());
            this.toLobby(event.getPlayer());
        });
    }

    // ==================== РАМКА ====================

    /**
     * Слоты рамки: всё, кроме трёх кнопок и клеток вплотную к ним.
     * <p>
     * Считается один раз и не меняется, поэтому хранится как есть.
     */
    private static final int[] BORDER = buildBorder();

    private static int[] buildBorder() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < ROWS * 9; slot++) {
            if (slot == SLOT_LEVELS || slot == SLOT_AGAIN || slot == SLOT_SPAWN) continue;
            slots.add(slot);
        }
        int[] result = new int[slots.size()];
        for (int i = 0; i < result.length; i++) result[i] = slots.get(i);
        return result;
    }

    /**
     * ВОЛНА СИММЕТРИЧНА ОТНОСИТЕЛЬНО СЕРЕДИНЫ.
     * <p>
     * Цвет клетки зависит не от её номера, а от РАССТОЯНИЯ ПО СТОЛБЦУ до центрального:
     * значит левая и правая половины всегда зеркальны, и волна расходится от кнопки
     * «сыграть ещё раз» в обе стороны сразу. Несимметричная бегущая строка тянула бы
     * взгляд в одну сторону - ровно то, чего на экране выбора делать не надо.
     */
    private void drawBorder() {
        int centerColumn = SLOT_AGAIN % 9;

        for (int slot : BORDER) {
            int distance = Math.abs(slot % 9 - centerColumn);
            int index = Math.floorMod(distance - this.wavePhase, WAVE.length);

            this.setItem(slot, ItemUtils.create(WAVE[index], meta -> {
                // Пустое имя, а не название блока: подпись «Чёрная стеклянная панель»
                // на каждой клетке рамки - это шум, который читается вместо кнопок.
                meta.displayName(Component.empty());
            }), null);
        }
    }

    @Override
    public void open(@NonNull Player player) {
        super.open(player);

        this.stopWave();
        this.waveTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (!player.isOnline()) {
                this.stopWave();
                return;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() != this) {
                this.stopWave();
                return;
            }
            this.wavePhase++;
            this.drawBorder();
        }, WAVE_PERIOD_TICKS, WAVE_PERIOD_TICKS);
    }

    private void stopWave() {
        if (this.waveTask == null) return;
        this.waveTask.cancel();
        this.waveTask = null;
    }

    /**
     * ЗАКРЫТЬ ОКНО МОЖНО ТОЛЬКО КНОПКОЙ.
     * <p>
     * Открывается оно следующим тиком, а не сразу: клиент в момент закрытия ещё держит
     * старое окно, и попытка открыть новое в том же тике им игнорируется.
     */
    @Override
    protected void onClose(@NonNull Player player) {
        if (this.allowClose) {
            this.stopWave();
            return;
        }
        if (!player.isOnline()) {
            this.stopWave();
            return;
        }

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline() || this.allowClose) return;
            this.open(player);
        }, 1L);
    }

    // ==================== ДЕЙСТВИЯ ====================

    private void click(@NonNull Player player) {
        // Разрешение закрыть выдаётся ДО закрытия: иначе обработчик закрытия успеет
        // открыть окно заново прямо поверх выбранного действия.
        this.allowClose = true;
        this.stopWave();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        player.closeInventory();
    }

    /**
     * ЗАЙТИ НА УРОВЕНЬ ЗАНОВО, А НЕ ПЕРЕЗАПУСТИТЬ ЗАБЕГ.
     * <p>
     * Заезд на финише глушит трек, а запрашивает музыку ВХОД НА УРОВЕНЬ, а не сам
     * забег. Прямой перезапуск поэтому и жаловался, что трек не загрузился, - при том
     * что файл давно у клиента.
     */
    private void playAgain(@NonNull Player player) {
        ru.sortix.parkourbeat.activity.ActivityManager activities =
            this.plugin.get(ru.sortix.parkourbeat.activity.ActivityManager.class);

        // СНАЧАЛА ВЫЙТИ, ПОТОМ ВОЙТИ.
        //
        // Игрок после финиша всё ещё числится игроком ЭТОГО уровня. Переключение с
        // одной игры на другую игру того же уровня менеджер считает почти ничем: он
        // подравнивает положение и на этом успокаивается - вход на уровень со всей его
        // подготовкой (ресурспак, запрос трека, запуск забега) не выполняется вовсе.
        // Именно так это и выглядело: меню закрылось, игрока чуть подвинуло, и всё.
        //
        // Поэтому сначала снимаем активность полностью и только следующим тиком заходим
        // заново - для менеджера это будет честный вход с нуля.
        activities.switchActivity(player, null, null)
            .thenAccept(left -> Bukkit.getScheduler().runTaskLater(this.plugin, () ->
                ru.sortix.parkourbeat.activity.type.PlayActivity
                    .createAsync(this.plugin, player, this.level.getUniqueId(), false)
                    .thenAccept(activity -> {
                        if (!player.isOnline()) return;
                        if (activity == null) {
                            player.sendMessage(PbText.of("&cУровень не загрузился, попробуйте ещё раз."));
                            return;
                        }
                        activities.switchActivity(player, activity, activity.getLevel().getSpawn());
                    }), 2L));
    }

    /** Увести в лобби командой /hub: куда именно она ведёт, знает прокси, а не мы. */
    private void toLobby(@NonNull Player player) {
        this.plugin.get(ru.sortix.parkourbeat.activity.ActivityManager.class)
            .switchActivity(player, null, null)
            .thenAccept(success -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (!player.isOnline()) return;

                // Небо и погоду возвращаем ПЕРЕД уходом: на другом сервере наши пакеты
                // до игрока уже не дойдут, и подменённое время осталось бы висеть там,
                // пока хаб не пришлёт своё.
                player.resetPlayerTime();
                player.resetPlayerWeather();
                player.performCommand("hub");
            }));
    }

    /**
     * ОТКРЫТЬ СПИСОК УРОВНЕЙ ПОВЕРХ, НЕ ВЫХОДЯ С КАРТЫ.
     * <p>
     * Раньше здесь снималась активность - то есть игрок уходил с уровня ещё до того,
     * как что-то выбрал. Закрыв список, он оставался в пустом мире без единой кнопки.
     * <p>
     * Теперь список открывается прямо поверх, а его закрытие возвращает сюда: список -
     * это ветка выбора, а не выход. Выход отсюда только один - кнопка «на спавн» или
     * выбранный в списке уровень, который сам сменит активность.
     */
    private void openLevelsList(@NonNull Player player) {
        new ru.sortix.parkourbeat.inventory.type.LevelsListMenu(
            this.plugin, this.lang,
            ru.sortix.parkourbeat.inventory.type.LevelsListMenu.DisplayMode.RANKED,
            player, player.getUniqueId()) {

            @Override
            protected void onClose(@NonNull Player viewer) {
                super.onClose(viewer);
                Bukkit.getScheduler().runTaskLater(DiggerEndMenu.this.plugin, () -> {
                    // Уровень уже выбран и активность сменилась - значит человек ушёл
                    // играть, и возвращать ему экран финиша прошлой карты незачем.
                    if (!viewer.isOnline()) return;
                    if (!viewer.getOpenInventory().getType()
                        .equals(org.bukkit.event.inventory.InventoryType.CRAFTING)) return;
                    if (DiggerEndMenu.this.plugin
                        .get(ru.sortix.parkourbeat.activity.ActivityManager.class)
                        .getActivity(viewer) == null) return;

                    DiggerEndMenu.this.allowClose = false;
                    DiggerEndMenu.this.open(viewer);
                }, 2L);
            }
        }.open(player);
    }
}
