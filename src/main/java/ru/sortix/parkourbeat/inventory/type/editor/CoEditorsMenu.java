package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.activity.UserActivity;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.PaginatedMenu;
import ru.sortix.parkourbeat.inventory.RegularItems;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.utils.lang.LangOptions;
import ru.sortix.parkourbeat.utils.lang.LangOptions.Placeholders;
import ru.sortix.parkourbeat.worldedit.WorldEditAccessManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class CoEditorsMenu extends PaginatedMenu<ParkourBeat, UUID> implements EditLevelMenu {
    private final @NonNull EditActivity activity;
    private final @NonNull Level level;

    public CoEditorsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, LangOptions.inventory_editorcoeditors_title.getComponent(lang), 0, 5 * 9);
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateAllItems();
    }

    @NonNull
    private GameSettings getSettings() {
        return this.level.getLevelSettings().getGameSettings();
    }

    @Override
    protected @NonNull Collection<UUID> getAllItems() {
        return new ArrayList<>(this.getSettings().getCoEditors().keySet());
    }

    @Override
    protected @NonNull ItemStack createItemDisplay(@NonNull UUID coEditorId) {
        String coEditorName = this.getSettings().getCoEditorName(coEditorId);
        String displayedName = coEditorName == null ? coEditorId.toString() : coEditorName;
        boolean isTrusted = this.getSettings().isTrusted(coEditorId);
        LangOptions loreLimitOpt = isTrusted ? LangOptions.inventory_editorcoeditors_limit_trusted : LangOptions.inventory_editorcoeditors_limit_default;

        return ItemUtils.create(Material.NAME_TAG, meta -> {
            meta.displayName(LangOptions.inventory_editorcoeditors_entry_name.getComponent(
                lang, new Placeholders("%name%", displayedName)));
            if (isTrusted) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            meta.lore(LangOptions.inventory_editorcoeditors_entry_lore.getComponents(lang,
                new Placeholders("%limit%", loreLimitOpt.get(lang))));
        });
    }

    @Override
    protected void onPageDisplayed() {
        this.setNextPageItem(6, 3);
        this.setPreviousPageItem(6, 7);

        if (this.getSettings().getCoEditors().isEmpty()) {
            this.setItem(1, 5, ItemUtils.create(Material.BARRIER, meta -> {
                meta.displayName(LangOptions.inventory_editorcoeditors_empty_name.getComponent(lang));
                meta.lore(LangOptions.inventory_editorcoeditors_empty_lore.getComponents(lang));
            }), null);
        }

        this.setItem(6, 1, ItemUtils.create(Material.WRITABLE_BOOK, meta -> {
            meta.displayName(LangOptions.inventory_editorcoeditors_add_name.getComponent(lang));
            meta.lore(LangOptions.inventory_editorcoeditors_add_lore.getComponents(lang));
        }), this::addCoEditor);

        this.setItem(6, 5, RegularItems.closeInventory(lang), event -> event.getPlayer().closeInventory());

        this.setItem(6, 9, ItemUtils.create(Material.REDSTONE_TORCH, meta ->
            meta.displayName(LangOptions.inventory_editorcoeditors_back.getComponent(lang))
        ), event -> new EditorMainMenu(this.plugin, lang, this.activity).open(event.getPlayer()));
    }

    @Override
    protected void onClick(@NonNull ClickEvent event, @NonNull UUID coEditorId) {
        Player player = event.getPlayer();
        if (!this.checkOwner(player)) return;

        // Если это левый клик (ЛКМ)
        if (event.isLeft()) {
            if (event.isShift()) return; // Игнорируем Шифт + ЛКМ

            // Обычный ЛКМ — переключаем лимит (5000 / 90000)
            GameSettings settings = this.getSettings();
            boolean newState = !settings.isTrusted(coEditorId);
            settings.setTrusted(coEditorId, newState);

            Player targetPlayer = this.plugin.getServer().getPlayer(coEditorId);
            if (targetPlayer != null) {
                UserActivity targetActivity = this.plugin.get(ActivityManager.class).getActivity(targetPlayer);
                if (targetActivity instanceof EditActivity editActivity && !editActivity.isTesting()) {
                    this.plugin.get(WorldEditAccessManager.class).grant(targetPlayer, newState ? 90000 : 5000);
                }
            }
            this.activity.updateInventoriesOfAllEditors(CoEditorsMenu.class, PaginatedMenu::updateAllItems);
            return;
        }

        // Если код дошел сюда, значит это ПКМ (!event.isLeft()).
        // Нам нужен именно Шифт + ПКМ
        if (!event.isShift()) return;

        // Логика удаления (Шифт + ПКМ)
        GameSettings settings = this.getSettings();
        String coEditorName = settings.getCoEditorName(coEditorId);
        if (!settings.removeCoEditor(coEditorId)) return;

        String displayedName = coEditorName == null ? coEditorId.toString() : coEditorName;
        player.sendMessage(LangOptions.inventory_editorcoeditors_removed.getComponent(
            lang, new Placeholders("%name%", displayedName)));

        Player removedPlayer = this.plugin.getServer().getPlayer(coEditorId);
        if (removedPlayer != null) {
            LangOptions.inventory_editorcoeditors_notify_removed.sendMsg(removedPlayer,
                new Placeholders("%owner%", player.getName()),
                new Placeholders("%level%", ((net.kyori.adventure.text.TextComponent) this.level.getDisplayName()).content()));

            UserActivity targetActivity = this.plugin.get(ActivityManager.class).getActivity(removedPlayer);
            if (targetActivity instanceof EditActivity && targetActivity.getLevel() == this.level) {
                this.plugin.get(WorldEditAccessManager.class).revoke(removedPlayer);
            }
        }

        this.activity.updateInventoriesOfAllEditors(CoEditorsMenu.class, PaginatedMenu::updateAllItems);
    }

    private boolean checkOwner(@NonNull Player player) {
        if (this.getSettings().isOwner(player.getUniqueId())) return true;
        player.sendMessage(LangOptions.inventory_editorcoeditors_notowner.getComponent(lang));
        player.closeInventory();
        return false;
    }

    private void addCoEditor(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        if (!this.checkOwner(player)) return;
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage(LangOptions.inventory_editorcoeditors_add_unavilable.getComponent(lang));
            return;
        }

        player.sendMessage(LangOptions.inventory_editorcoeditors_add_request.getComponent(lang));
        manager.requestChatInput(player, 20 * 30).thenAccept(rawName -> {
            if (rawName == null) {
                player.sendMessage(LangOptions.inventory_editorcoeditors_add_timeout.getComponent(lang));
                return;
            }

            String name = rawName.trim();
            if (name.isEmpty() || name.length() > 16) {
                player.sendMessage(LangOptions.inventory_editorcoeditors_add_notfound.getComponent(
                    lang, new Placeholders("%name%", name)));
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () ->
                this.finishAddingCoEditor(player, name));
        });
    }

    @SuppressWarnings("deprecation")
    private void finishAddingCoEditor(@NonNull Player player, @NonNull String name) {
        if (!this.getSettings().isOwner(player.getUniqueId())) {
            player.sendMessage(LangOptions.inventory_editorcoeditors_notowner.getComponent(lang));
            return;
        }

        UUID targetId;
        String targetName;

        Player onlineTarget = this.plugin.getServer().getPlayerExact(name);
        if (onlineTarget != null) {
            targetId = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();
        } else {
            OfflinePlayer offlineTarget = this.plugin.getServer().getOfflinePlayer(name);
            if (!offlineTarget.hasPlayedBefore()) {
                player.sendMessage(LangOptions.inventory_editorcoeditors_add_notfound.getComponent(
                    lang, new Placeholders("%name%", name)));
                return;
            }
            targetId = offlineTarget.getUniqueId();
            targetName = offlineTarget.getName() == null ? name : offlineTarget.getName();
        }

        GameSettings settings = this.getSettings();

        if (settings.isOwner(targetId)) {
            player.sendMessage(LangOptions.inventory_editorcoeditors_add_owner.getComponent(lang));
            return;
        }
        if (settings.isCoEditor(targetId)) {
            player.sendMessage(LangOptions.inventory_editorcoeditors_add_already.getComponent(
                lang, new Placeholders("%name%", targetName)));
            return;
        }
        if (settings.getCoEditors().size() >= GameSettings.MAX_CO_EDITORS) {
            player.sendMessage(LangOptions.inventory_editorcoeditors_add_limit.getComponent(lang));
            return;
        }

        settings.addCoEditor(targetId, targetName);

        player.sendMessage(LangOptions.inventory_editorcoeditors_add_success.getComponent(
            lang, new Placeholders("%name%", targetName)));

        Player addedPlayer = this.plugin.getServer().getPlayer(targetId);
        if (addedPlayer != null) {
            LangOptions.inventory_editorcoeditors_notify_added.sendMsg(addedPlayer,
                new Placeholders("%owner%", player.getName()),
                new Placeholders("%level%", ((TextComponent) this.level.getDisplayName()).content()));
        }

        this.activity.updateInventoriesOfAllEditors(CoEditorsMenu.class, PaginatedMenu::updateAllItems);

        new CoEditorsMenu(this.plugin, this.lang, this.activity).open(player);
    }
}
