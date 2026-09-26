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
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.text.PbText;
import ru.sortix.parkourbeat.utils.wonder.WonderAi;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.utils.wonder.WonderCommands;

import java.util.ArrayList;
import java.util.List;

/**
 * Помощник: строитель описывает происходящее словами, модель возвращает план из команд.
 * <p>
 * План показывается ДО применения, каждую строку можно выполнить отдельно или выкинуть.
 * Ни один ответ модели не попадает на уровень без явного нажатия — иначе одна неудачная
 * формулировка могла бы снести чужую работу.
 */
public class WonderAiMenu extends ParkourBeatInventory implements EditLevelMenu {

    private final @NonNull EditActivity activity;
    private final @NonNull Level level;
    private final @NonNull List<String> plan = new ArrayList<>();
    private @NonNull String note = "";
    private boolean waiting = false;
    private int previewIndex = 0;

    public WonderAiMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 5, lang, PbText.of(Lang.raw(lang, "auto.wonder_ai_menu.wonder_ai_menu.1")));
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateItems();
    }

    public void updateItems() {
        this.clearInventory();

        this.setItem(2, 5, ItemUtils.create(this.waiting ? Material.CLOCK : Material.PRISMARINE_CRYSTALS, meta -> {
            meta.displayName(PbText.of(this.waiting ? Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.1") : Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.2"))
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (this.waiting) {
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.3")));
            } else {
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.4")));
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.5")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.6")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.7")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.8")));
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.9")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.10")));
            }
            meta.lore(lore);
        }), this::askAi);

        if (!this.plan.isEmpty()) {
            this.setItem(3, 3, ItemUtils.create(Material.LIME_CONCRETE, meta -> {
                meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.11")).decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.12")));
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.13")));
                meta.lore(lore);
            }), event -> {
                if (!event.isLeft()) {
                    this.previewPlan(event.getPlayer());
                    return;
                }
                this.applyAll(event);
            });

            this.setItem(3, 5, ItemUtils.create(Material.ENDER_EYE, meta -> {
                meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.14")).decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                for (String piece : wrap(this.note.isEmpty() ? Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.15") : this.note)) {
                    lore.add(line("&f" + piece));
                }
                lore.add(Component.empty());
                for (String piece : this.summary()) lore.add(line("&7" + piece));
                lore.add(Component.empty());
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.16")));
                lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.17")));
                meta.lore(lore);
            }), event -> {
                if (!event.isLeft()) this.previewPlan(event.getPlayer());
            });

            this.setItem(3, 7, ItemUtils.create(Material.RED_CONCRETE, meta -> {
                meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.18")).decoration(TextDecoration.ITALIC, false));
                meta.lore(one(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.19")));
            }), event -> {
                this.plan.clear();
                this.note = "";
                this.updateItems();
            });
        }

        this.setItem(4, 1, ItemUtils.create(Material.BOOK, meta -> {
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.20")).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.21")));
            lore.add(line(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.22")));
            meta.lore(lore);
        }), event -> {
            event.getPlayer().closeInventory();
            WonderManual.send(event.getPlayer());
        });

        this.fillBorder();

        this.setItem(5, 5, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(PbText.of(Lang.raw(this.lang, "auto.wonder_ai_menu.update_items.23")).decoration(TextDecoration.ITALIC, false))
        ), event -> new WonderEffectsMenu(this.plugin, this.lang, this.activity).open(event.getPlayer()));
    }

    private void askAi(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        if (this.waiting) return;

        player.closeInventory();
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) return;

        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_ai_menu.ask_ai.1")));
        manager.requestChatInput(player, 20 * 60).thenAccept(message -> {
            if (message == null) return;
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                this.waiting = true;
                this.updateItems();
                this.open(player);

                ru.sortix.parkourbeat.player.music.MusicTrack track =
                    this.level.getLevelSettings().getGameSettings().getMusicTrack();
                this.plugin.get(WonderAi.class).ask(
                    player,
                    message,
                    this.level.getLightShow().getWonderEffects(),
                    track == null ? null : track.getName(),
                    ru.sortix.parkourbeat.utils.wonder.WonderTimeline.levelDurationMillis(this.level),
                    plan -> {
                        this.waiting = false;
                        if (plan.getError() != null) {
                            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_ai_menu.ask_ai.2") + plan.getError()));
                        } else {
                            this.plan.clear();
                            this.plan.addAll(plan.getCommands());
                            this.note = plan.getNotes().isEmpty() ? "" : plan.getNotes().get(0);
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
                        }
                        this.updateItems();
                        this.open(player);
                    });
            });
        });
    }

    private void applyAll(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        for (String command : new ArrayList<>(this.plan)) this.run(player, command);
        this.plan.clear();
        this.updateItems();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    private void run(@NonNull Player player, @NonNull String command) {
        String payload = command.trim();
        if (payload.toLowerCase().startsWith("/pbllmeffects")) {
            payload = payload.substring("/pbllmeffects".length()).trim();
        }
        WonderCommands.Result result = WonderCommands.execute(player, this.level.getLightShow(), payload);
        for (String messageLine : result.message.split("\n")) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_ai_menu.run.1") + messageLine));
        }
    }

    /** Человеческое описание плана: сколько добавит, сколько поправит, сколько уберёт. */
    @NonNull
    private List<String> summary() {
        int added = 0, edited = 0, removed = 0;
        String first = null, last = null;
        for (String command : this.plan) {
            String body = command.replace("/pbllmeffects ", "").trim();
            String action = body.split("\\s+")[0].toLowerCase();
            if (action.equals("preset") || action.equals("text") || action.equals("add")) {
                added++;
                String[] parts = body.split("\\s+");
                if (parts.length > 1) {
                    if (first == null) first = parts[1];
                    last = parts.length > 2 ? parts[2] : parts[1];
                }
            } else if (action.equals("edit")) {
                edited++;
            } else if (action.startsWith("del") || action.equals("clear")) {
                removed++;
            }
        }

        List<String> out = new ArrayList<>();
        if (added > 0) out.add(Lang.raw(this.lang, "auto.wonder_ai_menu.summary.1") + added);
        if (edited > 0) out.add(Lang.raw(this.lang, "auto.wonder_ai_menu.summary.2") + edited);
        if (removed > 0) out.add(Lang.raw(this.lang, "auto.wonder_ai_menu.summary.3") + removed);
        if (first != null && last != null) out.add(Lang.raw(this.lang, "auto.wonder_ai_menu.summary.4") + first + Lang.raw(this.lang, "auto.wonder_ai_menu.summary.5") + last);
        if (out.isEmpty()) out.add(Lang.raw(this.lang, "auto.wonder_ai_menu.summary.6"));
        return out;
    }

    /** Показ первого добавляемого эффекта: строитель видит настроение, ничего не приняв. */
    /** Каждый повторный ПКМ показывает следующий пункт плана, по кругу. */
    private void previewPlan(@NonNull Player player) {
        List<WonderEffect> samples = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String command : this.plan) {
            String body = command.replace("/pbllmeffects ", "").trim();
            WonderEffect sample = WonderCommands.preview(body, this.level.getLightShow().getWonderEffects());
            if (sample == null) continue;
            samples.add(sample);
            labels.add(body.split("\\s+")[0].toLowerCase());
        }
        if (samples.isEmpty()) {
            player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_ai_menu.preview_plan.1")));
            return;
        }

        if (this.previewIndex >= samples.size()) this.previewIndex = 0;
        int index = this.previewIndex;
        this.previewIndex++;

        String kind = labels.get(index);
        String what = kind.startsWith("del") ? Lang.raw(PlayerLang.of(player), "auto.wonder_ai_menu.preview_plan.2")
            : kind.equals("edit") ? Lang.raw(PlayerLang.of(player), "auto.wonder_ai_menu.preview_plan.3") : Lang.raw(PlayerLang.of(player), "auto.wonder_ai_menu.preview_plan.4");
        player.sendMessage(PbText.of(Lang.raw(PlayerLang.of(player), "auto.wonder_ai_menu.preview_plan.5") + (index + 1) + Lang.raw(PlayerLang.of(player), "auto.wonder_ai_menu.preview_plan.6")
            + samples.size() + "&7, " + what + Lang.raw(PlayerLang.of(player), "auto.wonder_ai_menu.preview_plan.7")));

        // Открываем ЭТОТ же экран, а не новый: иначе весь план обнулялся после показа
        WonderPreview.show(this.plugin, player, this.level, samples.get(index), this::open);
    }

    /** Лор узкий, поэтому длинную мысль режем по словам. */
    @NonNull
    private static List<String> wrap(@NonNull String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (current.length() + word.length() > 38) {
                out.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    private static Component line(@NonNull String text) {
        return PbText.of(text).decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> one(@NonNull String text) {
        List<Component> lore = new ArrayList<>();
        lore.add(line(text));
        return lore;
    }
}
