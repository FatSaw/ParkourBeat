package ru.sortix.parkourbeat.item.editor.type;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.item.editor.EditorItem;
import ru.sortix.parkourbeat.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

import ru.sortix.parkourbeat.utils.text.PbText;
public class CreateMarkerItem extends EditorItem {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public CreateMarkerItem(@NonNull ParkourBeat plugin, String lang, int slot) {
        super(plugin, lang, slot, 0, ItemUtils.create(Material.NOTE_BLOCK, meta -> {
            meta.displayName(PbText.of(Lang.raw(lang, "auto.create_marker_item.create_marker_item.1"))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(PbText.of(Lang.raw(lang, "auto.create_marker_item.create_marker_item.2"))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(PbText.of(Lang.raw(lang, "auto.create_marker_item.create_marker_item.3"))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(PbText.of(Lang.raw(lang, "auto.create_marker_item.create_marker_item.4"))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(PbText.of(Lang.raw(lang, "auto.create_marker_item.create_marker_item.5"))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(lore);
        }));
    }

    @Override
    public void onUse(@NonNull PlayerInteractEvent event, @NonNull EditActivity activity) {
        Player player = event.getPlayer();

        // 2D-тест идёт не через обычный тестовый забег, поэтому проверка isTesting()
        // тут не сработает. Ставим маркер по позиции кубика.
        ru.sortix.parkourbeat.twod.TwoDGame twoDGame =
            this.plugin.get(ru.sortix.parkourbeat.twod.TwoDManager.class).getGame(player);
        if (twoDGame != null && twoDGame.isActive()) {
            this.createTwoDMarker(player, activity, event, twoDGame);
            return;
        }

        if (!activity.isTesting()) {
            player.sendActionBar(PbText.of(
                Lang.raw(PlayerLang.of(player), "auto.create_marker_item.on_use.1")));
            return;
        }

        org.bukkit.event.block.Action action = event.getAction();
        boolean rightClick = action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
            || action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

        ru.sortix.parkourbeat.levels.settings.HelperMarker marker =
            new ru.sortix.parkourbeat.levels.settings.HelperMarker(
                player.getLocation().toVector(),
                rightClick
                    ? ru.sortix.parkourbeat.levels.settings.HelperMarker.Kind.RIGHT
                    : ru.sortix.parkourbeat.levels.settings.HelperMarker.Kind.LEFT);

        if (!activity.getLevel().getLightShow().addHelperMarker(marker)) {
            player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.create_marker_item.on_use.2")));
            return;
        }

        long millis = activity.getTestingActivity().getGame().getSongTimeMillis();
        player.sendActionBar(PbText.of(Lang.raw(PlayerLang.of(player), "auto.create_marker_item.on_use.3")
            + TimeUtils.formatTimecode((int) Math.max(0L, millis))
            + Lang.raw(PlayerLang.of(player), "auto.create_marker_item.on_use.4") + activity.getLevel().getLightShow().getHelperMarkers().size() + ")"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f,
            rightClick ? 1.5f : 1.9f);
    }

    /**
     * Маркер во время теста 2D-уровня.
     * <p>
     * Позиция берётся у кубика: игрок в это время сидит на камере сбоку от трассы,
     * и его собственные координаты к уровню отношения не имеют.
     */
    private void createTwoDMarker(@NonNull Player player,
                                  @NonNull EditActivity activity,
                                  @NonNull PlayerInteractEvent event,
                                  @NonNull ru.sortix.parkourbeat.twod.TwoDGame game) {
        org.bukkit.event.block.Action action = event.getAction();
        boolean rightClick = action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
            || action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

        ru.sortix.parkourbeat.levels.settings.HelperMarker marker =
            new ru.sortix.parkourbeat.levels.settings.HelperMarker(
                game.getCubeLocation().toVector(),
                rightClick
                    ? ru.sortix.parkourbeat.levels.settings.HelperMarker.Kind.RIGHT
                    : ru.sortix.parkourbeat.levels.settings.HelperMarker.Kind.LEFT);

        if (!activity.getLevel().getLightShow().addHelperMarker(marker)) {
            ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
                PbText.of(Lang.raw(PlayerLang.of(player), "auto.create_marker_item.create_two_d_marker.1")));
            return;
        }

        ru.sortix.parkourbeat.utils.text.ActionBarPriority.notice(player,
            PbText.of(Lang.raw(PlayerLang.of(player), "auto.create_marker_item.create_two_d_marker.2")
                + ru.sortix.parkourbeat.utils.TimeUtils.formatTimecode(game.getAttemptMillis())
                + Lang.raw(PlayerLang.of(player), "auto.create_marker_item.create_two_d_marker.3") + activity.getLevel().getLightShow().getHelperMarkers().size() + ")"));
    }
}
