package ru.sortix.parkourbeat.inventory.type.editor;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.wonder.WonderAnchor;
import ru.sortix.parkourbeat.levels.wonder.WonderBridge;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.levels.wonder.WonderPreset;
import ru.sortix.parkourbeat.levels.wonder.WonderPresets;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.TimeUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Настройка одного эффекта. Время, место и размер лежат отдельными кнопками,
 * а показ висит ровно посередине — чтобы после любой правки можно было сразу посмотреть.
 */
public class WonderEffectMenu extends LightShowElementMenu<WonderEffect> {

    public WonderEffectMenu(@NonNull ParkourBeat plugin,
                            String lang,
                            @NonNull EditActivity activity,
                            @NonNull WonderEffect effect
    ) {
        super(plugin, lang, activity, effect, PbText.of(Lang.raw(lang, "auto.wonder_effect_menu.wonder_effect_menu.1") + effect.getStartTimecode()));
        this.updateItems();
    }

    @Override
    protected void addSpecificItems() {
        WonderPreset preset = WonderPresets.byId(this.element.getPresetId());
        Material icon = preset == null ? Material.NAME_TAG : preset.getIcon();

        this.setItem(1, 5, ItemUtils.create(icon, meta -> {
            meta.displayName(PbText.of("&d" + this.element.getDisplayName(this.lang)).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (preset != null) {
                lore.add(line("&7" + preset.getHint(this.lang)));
                lore.add(Component.empty());
            }
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.1") + TimeUtils.formatSeconds(this.element.getDurationMillis()) + Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.2")));
            int points = WonderBridge.estimatePoints(this.element);
            if (points > 0) lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.3") + points));
            String problem = WonderBridge.validate(this.element);
            if (problem != null) {
                lore.add(Component.empty());
                lore.add(line("&c⚠ " + problem));
            }
            meta.lore(lore);
        }), null);

        this.setItem(2, 3, ItemUtils.create(Material.BOOKSHELF, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.4")).decoration(TextDecoration.ITALIC, false));
            meta.lore(one(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.5") + WonderPresets.amount() + Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.6")));
        }), event -> new WonderPresetsMenu(this.plugin, this.lang, this.activity, this.element)
            .open(event.getPlayer()));

        WonderAnchor anchor = this.element.getAnchor();
        this.setItem(2, 5, ItemUtils.create(anchor.getIcon(), meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.7") + anchor.getDisplay(this.lang))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line("&7" + anchor.getHint(this.lang)));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.8")));
            if (anchor == WonderAnchor.FIXED) {
                lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.9")));
                lore.add(line(this.element.getFixedLocation() == null
                    ? Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.10")
                    : Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.11") + fmt(this.element.getFixedLocation().getX())
                    + " " + fmt(this.element.getFixedLocation().getY())
                    + " " + fmt(this.element.getFixedLocation().getZ())));
            }
            meta.lore(lore);
        }), this::switchAnchor);

        this.setItem(2, 7, ItemUtils.create(Material.BLAZE_POWDER, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.12")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            String particle = ru.sortix.parkourbeat.utils.wonder.WonderSpec.get(this.element.getSpec(), "particle");
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.13") + (particle == null ? "end_rod" : particle)));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.14")));
            meta.lore(lore);
        }), event -> new WonderParticlesMenu(this.plugin, this.lang, this.activity, this.element)
            .open(event.getPlayer()));

        this.setItem(2, 4, ItemUtils.create(Material.PAINTING, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.15")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.16")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.17")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.18")));
            meta.lore(lore);
        }), event -> new WonderDrawMenu(this.plugin, this.lang, this.activity, this.element)
            .open(event.getPlayer()));

        this.setItem(3, 5, ItemUtils.create(Material.ENDER_EYE, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.19")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.20")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.21")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.22")));
            meta.lore(lore);
        }), event -> WonderPreview.show(this.plugin, event.getPlayer(), this.level, this.element,
            who -> new WonderEffectMenu(this.plugin, this.lang, this.activity, this.element).open(who)));

        this.setItem(2, 8, ItemUtils.create(Material.WRITABLE_BOOK, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.23")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.24")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.25")));
            meta.lore(lore);
        }), event -> this.publish(event.getPlayer()));

        this.setItem(4, 2, ItemUtils.create(Material.LEAD, meta -> {
            double turn = this.element.getTurn();
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.26") + fmt(this.element.getSide()))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.27")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.28") + (Double.isNaN(turn) ? Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.29")
                : Math.abs(turn) < 0.01D ? Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.30") : fmt(turn) + Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.31"))));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.32")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.33")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.34")));
            meta.lore(lore);
        }), event -> {
            if (event.isShift() && event.isLeft()) {
                this.cycleTurn();
            } else if (event.isShift()) {
                this.requestNumber(event.getPlayer(), Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.35"), value -> this.element.setSide(value));
                return;
            } else {
                this.element.setSide(this.element.getSide() + (event.isLeft() ? 1.0D : -1.0D));
            }
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });
        this.numberItem(4, 4, Material.ARROW, Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.36"), this.element.getDistance(),
            Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.37"), value -> this.element.setDistance(value));
        this.numberItem(4, 6, Material.FEATHER, Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.38"), this.element.getHeight(),
            Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.39"), value -> this.element.setHeight(value));
        if (ru.sortix.parkourbeat.utils.wonder.WonderSpec.isText(this.element.getSpec())) {
            this.setItem(2, 2, ItemUtils.create(Material.WRITABLE_BOOK, meta -> {
                meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.40")).decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.41")
                    + ru.sortix.parkourbeat.utils.wonder.WonderSpec.words(this.element.getSpec())));
                String font = ru.sortix.parkourbeat.utils.wonder.WonderSpec.get(this.element.getSpec(), "font");
                lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.42") + (font == null ? "pixel" : font.replace('_', ' '))));
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.43")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.44")));
                meta.lore(lore);
            }), event -> {
                if (event.isLeft()) this.editWords(event.getPlayer());
                else new WonderFontsMenu(this.plugin, this.lang, this.activity, this.element).open(event.getPlayer());
            });
        }

        this.setItem(1, 3, ItemUtils.create(Material.SLIME_BALL, meta -> {
            int thick = this.element.getThick();
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.45")
                    + (thick == 0 ? Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.46") : thick == 1 ? Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.47") : thick == 2 ? Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.48") : Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.49")))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.50")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.51")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.52")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.53") + (1 + thick * 4) + Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.54")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.55")));
            meta.lore(lore);
        }), event -> {
            int thick = this.element.getThick() + (event.isLeft() ? 1 : -1);
            this.element.setThick(Math.max(0, Math.min(3, thick)));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        this.setItem(1, 7, ItemUtils.create(Material.COMPASS, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.56")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.57")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.58")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.59")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.60")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.61")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.62")));
            meta.lore(lore);
        }), this::placeHere);

        this.setItem(4, 9, ItemUtils.create(Material.ELYTRA, meta -> {
            double approach = this.element.getApproach();
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.63")
                    + (approach < 0 ? Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.64") : Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.65") + fmt(approach) + Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.66")))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.67")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.68")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.69")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.70")));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.71") + this.element.approachDirName()));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.72")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.73")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.74")));
            meta.lore(lore);
        }), event -> {
            double approach = this.element.getApproach();
            if (event.isShift() && event.isLeft()) {
                this.element.setApproachDir(
                    (this.element.getApproachDir() + 1) % WonderEffect.APPROACH_DIRS.length);
            } else if (event.isShift()) {
                this.element.setApproach(-1.0D);
            } else if (approach < 0) {
                this.element.setApproach(10.0D);
            } else {
                this.element.setApproach(Math.max(1.0D, approach + (event.isLeft() ? 2.0D : -2.0D)));
            }
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });

        this.numberItem(4, 8, Material.HEART_OF_THE_SEA, Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.75"), this.element.getScale(),
            Lang.raw(this.lang, "auto.wonder_effect_menu.add_specific_items.76"), value -> this.element.setScale(Math.max(0.05D, value)));
    }

    private void numberItem(int row, int column, @NonNull Material material, @NonNull String title,
                            double current, @NonNull String hint, @NonNull java.util.function.DoubleConsumer setter) {
        this.setItem(row, column, ItemUtils.create(material, meta -> {
            meta.displayName(PbText.of("&e" + title + ": &f" + fmt(current))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line("&7" + hint));
            lore.add(Component.empty());
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.number_item.1")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.number_item.2")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_effect_menu.number_item.3")));
            meta.lore(lore);
        }), event -> {
            if (event.isShift() && !event.isLeft()) {
                this.requestNumber(event.getPlayer(), title, setter);
                return;
            }
            double step = event.isShift() ? 2.0D : 0.5D;
            setter.accept(current + (event.isLeft() ? step : -step));
            this.click(event.getPlayer());
            this.reopen(event.getPlayer());
        });
    }

    private void requestNumber(@NonNull Player player, @NonNull String title,
                               @NonNull java.util.function.DoubleConsumer setter) {
        player.closeInventory();
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) return;

        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_effect_menu.request_number.1") + title + "&7»:"));
        manager.requestChatInput(player, 20 * 30).thenAccept(message -> {
            if (message == null) return;
            double value;
            try {
                value = Double.parseDouble(message.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_effect_menu.request_number.2")));
                return;
            }
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                setter.accept(value);
                this.reopen(player);
            });
        });
    }

    private void switchAnchor(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        if (!event.isLeft() && this.element.getAnchor() == WonderAnchor.FIXED) {
            this.element.setFixedLocation(player.getLocation().clone());
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_effect_menu.switch_anchor.1")));
        } else {
            this.element.setAnchor(this.element.getAnchor().next());
        }
        this.click(player);
        this.reopen(player);
    }

    /** Выключен, сам наискось, и дальше фиксированные углы. */
    private void cycleTurn() {
        double turn = this.element.getTurn();
        if (Double.isNaN(turn)) {
            this.element.setTurn(20.0D);
        } else if (Math.abs(turn) < 0.01D) {
            this.element.setTurn(Double.NaN);
        } else if (turn >= 40.0D) {
            this.element.setTurn(0.0D);
        } else {
            this.element.setTurn(turn + 20.0D);
        }
    }

    /** Отдельное перемещение: время остаётся, а место становится каким угодно. */
    private void placeHere(@NonNull ClickEvent event) {
        Player player = event.getPlayer();

        if (event.isShift() && !event.isLeft()) {
            this.element.setAnchor(ru.sortix.parkourbeat.levels.wonder.WonderAnchor.PATH);
            this.element.setFixedLocation(null);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_effect_menu.place_here.1")));
        } else if (!event.isLeft()) {
            // Поворот по взгляду: горизонтальный доворот, чтобы эффект смотрел туда же, куда вы
            float yaw = player.getLocation().getYaw();
            this.element.setTurn(-yaw);
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_effect_menu.place_here.2")));
        } else {
            this.element.setAnchor(ru.sortix.parkourbeat.levels.wonder.WonderAnchor.FIXED);
            this.element.setFixedLocation(player.getLocation().clone());
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_effect_menu.place_here.3")));
        }
        this.click(player);
        this.reopen(player);
    }

    private void editWords(@NonNull Player player) {
        player.closeInventory();
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) return;

        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_effect_menu.edit_words.1")));
        manager.requestChatInput(player, 20 * 60).thenAccept(message -> {
            if (message == null) return;
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                this.element.setSpec(ru.sortix.parkourbeat.utils.wonder.WonderSpec
                    .withWords(this.element.getSpec(), message));
                this.reopen(player);
            });
        });
    }

    private void publish(@NonNull Player player) {
        player.closeInventory();
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) return;

        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_effect_menu.publish.1")));
        manager.requestChatInput(player, 20 * 60).thenAccept(message -> {
            if (message == null) return;
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                this.plugin.get(ru.sortix.parkourbeat.utils.wonder.WonderLibrary.class)
                    .publish(player, message.trim(), this.element);
                player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_effect_menu.publish.2")));
                this.reopen(player);
            });
        });
    }

    private void click(@NonNull Player player) {
        this.persist();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
    }

    /** Любая правка сразу уходит на диск: перезагрузка плагина больше ничего не теряет. */
    protected void persist() {
        ru.sortix.parkourbeat.utils.wonder.WonderSave.now(this.plugin, this.activity.getLevel());
    }

    private static Component line(@NonNull String text) {
        return PbText.of(text).decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> one(@NonNull String text) {
        List<Component> lore = new ArrayList<>();
        lore.add(line(text));
        return lore;
    }

    private static String fmt(double value) {
        return value == Math.rint(value)
            ? String.valueOf((long) value)
            : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    protected boolean removeElement() {
        return this.getLightShow().removeWonderEffect(this.element);
    }

    @Override
    protected void openListMenu(@NonNull Player player) {
        new WonderEffectsMenu(this.plugin, this.lang, this.activity).open(player);
    }

    @Override
    protected void reopen(@NonNull Player player) {
        new WonderEffectMenu(this.plugin, this.lang, this.activity, this.element).open(player);
    }
}
