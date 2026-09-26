package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.duel.DuelSide;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

/**
 * Выбор стороны перед тестовым забегом на дуэльной карте.
 * <p>
 * Спрашиваем именно здесь, а не в настройках уровня: сторона нужна ровно в момент
 * запуска теста и меняется от забега к забегу - строитель проверяет то одну трассу,
 * то другую. Настройкой уровня это быть не может, у неё другой срок жизни.
 */
public class DuelTestSideMenu extends ParkourBeatInventory {
    private final @NonNull EditActivity activity;

    public DuelTestSideMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 1, lang, PbText.of("&8За кого тестируем?"));
        this.activity = activity;

        this.setSide(3, DuelSide.FIRST, Material.LAPIS_BLOCK);
        this.setSide(7, DuelSide.SECOND, Material.REDSTONE_BLOCK);
    }

    private void setSide(int column, @NonNull DuelSide side, @NonNull Material material) {
        int points = this.activity.getLevel().getLevelSettings().getWorldSettings()
            .getWaypoints(side).size();

        this.setItem(1, column, ItemUtils.create(material, (meta) -> {
            meta.displayName(PbText.item("&8◆ " + side.getColoredName()));
            meta.lore(java.util.List.of(
                PbText.item("&eВы побежите по этой трассе"),
                PbText.item("&7Точек на ней: &f" + points),
                PbText.item("&7Прыжки засчитываются только здесь"),
                PbText.item("&8Нажмите, чтобы начать забег")
            ));
        }), event -> {
            Player player = event.getPlayer();
            player.closeInventory();

            if (this.activity.isTesting()) {
                player.sendMessage(PbText.of("&cТестовый забег уже идёт."));
                return;
            }

            this.activity.setDuelTestSide(side);
            player.sendMessage(PbText.of("&7Тест за " + side.getColoredName()));
            this.activity.startTesting();
        });
    }

}
