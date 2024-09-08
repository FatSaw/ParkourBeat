package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.inventory.event.ClickEvent;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.ModerationStatus;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.world.TeleportUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PrivacySettingsMenu extends ParkourBeatInventory implements EditLevelMenu {
    private final EditActivity activity;
    private final Level level;

    protected PrivacySettingsMenu(@NonNull ParkourBeat plugin, @NonNull EditActivity activity) {
        super(plugin, 5, Component.text("Видимость и публикация"));
        this.activity = activity;
        this.level = activity.getLevel();
        this.updateItems();
    }

    private void updateItems() {
        this.clearInventory();

        this.updatePublicVisibilityItem();

        this.updateLevelNameItem();

        this.updateModerationStatusItem();

        this.setItem(
            4,
            5,
            ItemUtils.create(Material.REDSTONE_TORCH, (meta) -> {
                meta.displayName(Component.text("назад в настройки", NamedTextColor.GOLD));
            }),
            this::back);
    }

    private void updatePublicVisibilityItem() {
        boolean publicVisible = this.level.getLevelSettings().getGameSettings().isPublicVisible();
        List<Component> lore = Collections.singletonList(Component.text(
            "Доступ: " + (publicVisible ? "Публичный" : "Приватный"), NamedTextColor.YELLOW));
        this.setItem(
            2,
            3,
            ItemUtils.create(publicVisible ? Material.REDSTONE_LAMP : Material.BARRIER, (meta) -> {
                meta.displayName(Component.text("Видимость уровня", NamedTextColor.GOLD));
                meta.lore(lore);
            }),
            this::switchPublicVisibility);
    }

    private void switchPublicVisibility(@NonNull ClickEvent event) {
        GameSettings settings = this.level.getLevelSettings().getGameSettings();

        if (settings.getModerationStatus() == ModerationStatus.MODERATED) {
            if (settings.isPublicVisible()) {
                event.getPlayer().sendMessage("Невозможно скрыть рейтинговый уровень");
            } else {
                event.getPlayer().sendMessage("Ваш уровень был заблокирован");
            }
            event.getPlayer().closeInventory();
            return;
        }

        boolean publicVisibleNow = !settings.isPublicVisible();
        settings.setPublicVisible(publicVisibleNow);

        this.activity.updateInventoriesOfAllEditors(PrivacySettingsMenu.class,
            PrivacySettingsMenu::updatePublicVisibilityItem);

        for (Player editor : this.activity.getAllEditors()) {
            editor.sendMessage("Редактор " + event.getPlayer().getName()
                + " переключил доступность уровня на " + (publicVisibleNow ? "публичную" : "приватную"));
        }
    }

    private void updateLevelNameItem() {
        this.setItem(
            2,
            5,
            ItemUtils.create(Material.WRITABLE_BOOK, (meta) -> {
                meta.displayName(Component.text("Переименовать уровень", NamedTextColor.GOLD));
                meta.lore(Arrays.asList(
                    Component.text("Вам будет необходимо отправить", NamedTextColor.YELLOW),
                    Component.text("новое название в чат.", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Текущее название:", NamedTextColor.YELLOW),
                    this.level.getDisplayName()
                ));
            }),
            this::renameLevel);
    }

    private void renameLevel(@NonNull ClickEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();

        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) {
            player.sendMessage("Функция недоступна в данный момент");
            return;
        }

        player.sendMessage("У вас есть 30 сек, чтобы указать в чате новое название уровня");
        manager.requestChatInput(player, 20 * 30).thenAccept(newNameLegacy -> {
            if (newNameLegacy == null) {
                player.sendMessage("Новое название не введено");
                return;
            }

            Component newName = LegacyComponentSerializer.legacyAmpersand().deserialize(newNameLegacy);
            int nameLength = PlainComponentSerializer.plain().serialize(newName).length();

            if (nameLength < 3) {
                player.sendMessage("Название должно содержать от 3 до 30 символов");
                return;
            }

            if (nameLength > 30) {
                player.sendMessage("Название должно содержать от 5 до 30 символов");
                return;
            }

            Component oldName;
            oldName = this.level.getDisplayName();
            this.level.getLevelSettings().getGameSettings().setDisplayName(newName);
            newName = this.level.getDisplayName();

            player.sendMessage(Component.text()
                .append(Component.text("Название изменено с \"", NamedTextColor.WHITE))
                .append(oldName)
                .append(Component.text("\" на \"", NamedTextColor.WHITE))
                .append(newName)
                .append(Component.text("\"", NamedTextColor.WHITE))
            );

            this.activity.updateInventoriesOfAllEditors(PrivacySettingsMenu.class,
                PrivacySettingsMenu::updateLevelNameItem);
        });
    }

    private void updateModerationStatusItem() {
        List<Component> lore = new ArrayList<>();
        Material material = switch (this.level.getLevelSettings().getGameSettings().getModerationStatus()) {
            case NOT_MODERATED -> {
                lore.add(Component.text("Модерация: " + "Не пройдена", NamedTextColor.YELLOW));
                lore.add(Component.empty());
                lore.add(Component.text("Нажмите, чтобы запросить.", NamedTextColor.YELLOW));
                lore.add(Component.text("После прохождения модерации", NamedTextColor.YELLOW));
                lore.add(Component.text("уровень станет рейтинговым", NamedTextColor.YELLOW));
                yield Material.PAPER;
            }
            case ON_MODERATION -> {
                lore.add(Component.text("Модерация: " + "Запрошена", NamedTextColor.YELLOW));
                lore.add(Component.empty());
                lore.add(Component.text("Нажмите, чтобы отменить", NamedTextColor.YELLOW));
                lore.add(Component.text("запрос модерации", NamedTextColor.YELLOW));
                yield Material.PLAYER_HEAD;
            }
            case MODERATED -> {
                lore.add(Component.text("Модерация: " + "Завершена", NamedTextColor.GRAY));
                yield Material.BOOKSHELF;
            }
        };
        this.setItem(
            2,
            7,
            ItemUtils.create(material, (meta) -> {
                meta.displayName(Component.text("Статус модерации", NamedTextColor.GOLD));
                meta.lore(lore);
            }),
            this::switchModerationStatus);
    }

    private void switchModerationStatus(@NonNull ClickEvent event) {
        GameSettings settings = this.level.getLevelSettings().getGameSettings();

        switch (settings.getModerationStatus()) {
            case NOT_MODERATED -> {
                settings.setModerationStatus(ModerationStatus.ON_MODERATION);
                boolean changeVisibility = !settings.isPublicVisible();
                if (changeVisibility) {
                    settings.setPublicVisible(true);
                }
                for (Player editor : this.activity.getAllEditors()) {
                    TeleportUtils.teleportAsync(this.plugin, editor, Settings.getLobbySpawn()).whenComplete((success, throwable) -> {
                        if (success) {
                            editor.sendMessage("Редактор " + event.getPlayer().getName()
                                + " запросил модерацию для уровня, на котором вы находились"
                                + (changeVisibility ? ". Из-за этого уровень стал общедоступным" : ""));
                        }
                    });
                }
            }
            case ON_MODERATION -> {
                settings.setModerationStatus(ModerationStatus.NOT_MODERATED);
                this.activity.updateInventoriesOfAllEditors(PrivacySettingsMenu.class,
                    PrivacySettingsMenu::updateModerationStatusItem);
            }
            case MODERATED -> {
                event.getPlayer().sendMessage("Уровень уже прошёл модерацию");
                event.getPlayer().closeInventory();
            }
        }
    }

    private void back(@NonNull ClickEvent event) {
        new EditorMainMenu(this.plugin, this.activity).open(event.getPlayer());
    }
}
