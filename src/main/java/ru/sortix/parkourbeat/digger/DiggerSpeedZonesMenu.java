package ru.sortix.parkourbeat.digger;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.DirectionChecker;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * МЕНЮ ЗОН СКОРОСТИ.
 * <p>
 * Зона - это отрезок трека, который едется быстрее или медленнее остального. Здесь их
 * видно списком, здесь же они правятся и удаляются.
 * <p>
 * ЗОНА ДОБАВЛЯЕТСЯ ОТТУДА, ГДЕ СТОИТ СТРОИТЕЛЬ. Вводить таймкод руками бессмысленно:
 * человек и так стоит в том месте тоннеля, которое хочет ускорить, а плагин умеет
 * перевести его положение в таймкод точнее, чем он сам посчитает в уме. Длина новой
 * зоны - восемь секунд, дальше он подгонит её кнопками.
 * <p>
 * ЛЮБАЯ ПРАВКА МЕНЯЕТ ГЕОМЕТРИЮ УЖЕ РАЗЛОЖЕННОЙ КАРТЫ. Расстояние до ноты - это
 * интеграл скорости по времени, поэтому сдвинув зону, строитель сдвигает все ноты
 * после неё. Об этом сказано прямо в подписи: молча переставлять чужую работу нельзя.
 */
public class DiggerSpeedZonesMenu extends ParkourBeatInventory {

    /** На сколько меняется множитель одним кликом. */
    private static final double STEP = 0.05D;

    /** На сколько двигаются границы зоны одним кликом, мс. */
    private static final int EDGE_STEP = 1000;

    /** Длина зоны, которая создаётся кнопкой. */
    private static final int NEW_ZONE_MILLIS = 8000;

    private final @NonNull Level level;
    private final @NonNull DiggerLevelSettings settings;

    public DiggerSpeedZonesMenu(@NonNull ParkourBeat plugin, String lang, @NonNull Level level) {
        super(plugin, 6, lang, PbText.of("&8Копатель: зоны скорости"));
        this.level = level;
        this.settings = level.getLevelSettings().getGameSettings().getDiggerSettings();
        this.updateItems();
    }

    private void updateItems() {
        this.clearInventory();

        List<DiggerSpeedZone> zones = this.settings.getSpeedZones();
        for (int i = 0; i < zones.size() && i < 36; i++) {
            DiggerSpeedZone zone = zones.get(i);
            int row = 1 + i / 9;
            int column = 1 + i % 9;
            this.setItem(row, column, this.zoneItem(zone), event -> this.onZoneClick(event.getPlayer(), zone, event.isShift(), !event.isLeft()));
        }

        this.setItem(6, 1, ItemUtils.create(Material.LIME_DYE, meta -> {
            meta.displayName(PbText.item("&a◆ Добавить зону здесь"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Зона на &f" + NEW_ZONE_MILLIS / 1000 + " секунд &7от того места,"));
            lore.add(PbText.item("&7где вы сейчас стоите в тоннеле."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8Множитель новой зоны: ×1.10"));
            meta.lore(lore);
        }), event -> this.addHere(event.getPlayer()));

        this.setItem(6, 5, ItemUtils.create(Material.PAPER, meta -> {
            meta.displayName(PbText.item("&f◆ Как это работает"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Зон: &f" + this.settings.getSpeedZonesCount()
                + "&7 из &f" + DiggerLevelSettings.MAX_SPEED_ZONES));
            lore.add(PbText.item("&7Базовая скорость: &f"
                + String.format("%.2f", this.settings.resolveSpeed()) + " бл/с"));
            lore.add(Component.empty());
            lore.add(PbText.item("&cПравка зоны двигает все ноты после неё:"));
            lore.add(PbText.item("&cрасстояние зависит от скорости до него."));
            lore.add(PbText.item("&7После правок прогоните тест."));
            meta.lore(lore);
        }), event -> {
        });

        this.setItem(6, 9, ItemUtils.create(Material.BARRIER, meta -> {
            meta.displayName(PbText.item("&c◆ Удалить все зоны"));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Карта поедет одной скоростью,"));
            lore.add(PbText.item("&7как до появления зон."));
            lore.add(Component.empty());
            lore.add(PbText.item("&8Shift + клик"));
            meta.lore(lore);
        }), event -> {
            if (!event.isShift()) {
                event.getPlayer().sendActionBar(PbText.of("&7Нужен &fShift &7- это необратимо."));
                return;
            }
            int removed = this.settings.clearSpeedZones();
            event.getPlayer().sendMessage(PbText.of("&cУдалено зон: &f" + removed));
            event.getPlayer().playSound(event.getPlayer().getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            this.updateItems();
        });
    }

    @NonNull
    private org.bukkit.inventory.ItemStack zoneItem(@NonNull DiggerSpeedZone zone) {
        boolean faster = zone.getMultiplier() > 1.0D;
        Material material = faster ? Material.REDSTONE_BLOCK : Material.LIGHT_BLUE_CONCRETE;

        return ItemUtils.create(material, meta -> {
            meta.displayName(PbText.item((faster ? "&c" : "&b") + zone.describe()));
            List<Component> lore = new ArrayList<>();
            lore.add(PbText.item("&7Скорость здесь: &f"
                + String.format("%.2f", this.settings.resolveSpeed() * zone.getMultiplier())
                + " бл/с"));
            lore.add(PbText.item("&7Длина: &f" + zone.lengthMillis() / 1000 + " с"));
            lore.add(Component.empty());
            lore.add(PbText.item("&eЛКМ &7- быстрее на " + String.format("%.2f", STEP)));
            lore.add(PbText.item("&eПКМ &7- медленнее на " + String.format("%.2f", STEP)));
            lore.add(PbText.item("&eShift + ЛКМ &7- сдвинуть конец на +1 с"));
            lore.add(PbText.item("&eShift + ПКМ &7- удалить зону"));
            meta.lore(lore);
        });
    }

    private void onZoneClick(@NonNull Player player,
                             @NonNull DiggerSpeedZone zone,
                             boolean shift,
                             boolean right
    ) {
        if (shift && right) {
            this.settings.removeSpeedZone(zone);
            player.sendActionBar(PbText.of("&cЗона удалена"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            this.updateItems();
            return;
        }

        if (shift) {
            zone.setEndMillis(zone.getEndMillis() + EDGE_STEP);
        } else {
            zone.setMultiplier(zone.getMultiplier() + (right ? -STEP : STEP));
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f,
            (float) Math.max(0.5D, Math.min(2.0D, zone.getMultiplier())));
        player.sendActionBar(PbText.of("&7Зона: &f" + zone.describe()));
        this.updateItems();
    }

    /**
     * Создать зону там, где стоит строитель.
     * <p>
     * Положение переводится в таймкод тем же профилем, по которому едет игрок - иначе
     * зона, поставленная «вот здесь», оказалась бы в другом месте трека ровно настолько,
     * насколько предыдущие зоны отклонились от базовой скорости.
     */
    private void addHere(@NonNull Player player) {
        DirectionChecker checker = this.level.getLevelSettings().getDirectionChecker();
        int sign = checker.isNegative() ? -1 : 1;

        org.bukkit.Location origin = this.settings.getOrigin(this.level.getWorld());
        if (origin == null) origin = DiggerAutoMapper.defaultOrigin(this.level);

        double distance = (checker.getCoordinate(player.getLocation())
            - checker.getCoordinate(origin)) * sign;
        if (distance < 0.0D) distance = 0.0D;

        int start = (int) Math.round(this.settings.speedProfile().millisAt(distance));
        DiggerSpeedZone zone = new DiggerSpeedZone(start, start + NEW_ZONE_MILLIS, 1.10D);

        if (!this.settings.addSpeedZone(zone)) {
            player.sendMessage(PbText.of("&cПредел: " + DiggerLevelSettings.MAX_SPEED_ZONES + " зон."));
            return;
        }

        player.sendMessage(PbText.of("&aЗона добавлена&7: " + zone.describe()));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.5f);
        this.updateItems();
    }
}
