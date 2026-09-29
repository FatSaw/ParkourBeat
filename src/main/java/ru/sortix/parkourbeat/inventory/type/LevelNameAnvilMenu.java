package ru.sortix.parkourbeat.inventory.type;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LevelNameAnvilMenu {
    public static final int MIN_NAME_LENGTH = 3;
    public static final int MAX_NAME_LENGTH = 30;
    private static final String DEFAULT_NAME = "Новый уровень";

    private static final Map<UUID, LevelNameAnvilMenu> ACTIVE_MENUS = new ConcurrentHashMap<>();
    private static boolean listenerRegistered = false;

    private final @NonNull ParkourBeat plugin;
    private final @NonNull String lang;
    private final Player player;
    private String currentName = DEFAULT_NAME;

    // Переменная для сохранения инвентаря игрока
    private ItemStack[] savedInventory;

    public LevelNameAnvilMenu(@NonNull ParkourBeat plugin, @NonNull String lang, Player player) {
        this.plugin = plugin;
        this.lang = lang;
        this.player = player;
        ensureListener(plugin);
    }

    public LevelNameAnvilMenu(@NonNull ParkourBeat plugin, @NonNull String lang) {
        this(plugin, lang, null);
    }

    private static synchronized void ensureListener(@NonNull ParkourBeat plugin) {
        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(new AnvilEventsListener(), plugin);
            listenerRegistered = true;
        }
    }

    public void open() {
        if (this.player == null) {
            throw new IllegalStateException("Player is null. Use open(Player) instead.");
        }
        this.open(this.player);
    }

    public void open(@NonNull Player target) {
        // Сохраняем и очищаем инвентарь (чтобы нижняя часть меню визуально была пустой)
        this.savedInventory = target.getInventory().getContents();
        target.getInventory().clear();

        InventoryView view = null;
        try {
            view = target.openAnvil(target.getLocation(), true);
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Не удалось открыть наковальню: " + t.getMessage());
        }

        if (view == null) {
            this.restoreInventory(target); // Возвращаем инвентарь при ошибке
            target.sendMessage(PbText.of("&cНе удалось открыть наковальню."));
            return;
        }

        Inventory top = view.getTopInventory();
        if (top instanceof AnvilInventory anvil) {
            anvil.setItem(0, createInitialItem());
            anvil.setItem(2, buildResult(DEFAULT_NAME));
            anvil.setRepairCost(0);
            anvil.setMaximumRepairCost(0);
        }

        this.currentName = DEFAULT_NAME;
        ACTIVE_MENUS.put(target.getUniqueId(), this);

        final InventoryView finalView = view;
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (!target.isOnline()) return;
            if (ACTIVE_MENUS.containsKey(target.getUniqueId()) && target.getOpenInventory().getTopInventory() instanceof AnvilInventory anvil) {
                anvil.setItem(0, createInitialItem());
                anvil.setItem(2, buildResult(this.currentName));
                anvil.setRepairCost(0);
                try {
                    finalView.setProperty(InventoryView.Property.REPAIR_COST, 0);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    // Метод возврата инвентаря
    public void restoreInventory(@NonNull Player target) {
        if (this.savedInventory != null) {
            target.getInventory().setContents(this.savedInventory);
            this.savedInventory = null;
            target.updateInventory();
        }
    }

    @NonNull
    private static ItemStack createInitialItem() {
        return ItemUtils.create(Material.PAPER, meta -> {
            meta.displayName(PbText.item("&f" + DEFAULT_NAME));
            meta.lore(List.of(
                PbText.item("&eВпишите название уровня сверху"),
                PbText.item("&7От " + MIN_NAME_LENGTH + " до " + MAX_NAME_LENGTH + " символов"),
                PbText.item("&8Затем нажмите на результат справа")
            ));
        });
    }

    @NonNull
    private static ItemStack buildResult(@NonNull String name) {
        boolean valid = isValid(name);
        return ItemUtils.create(valid ? Material.WRITABLE_BOOK : Material.BARRIER, meta -> {
            if (valid) {
                meta.displayName(PbText.item("&a&lСоздать: &f" + name));
                meta.lore(List.of(
                    PbText.item("&eНажмите, чтобы перейти к выбору режима"),
                    PbText.item("&7Название можно будет сменить потом")
                ));
            } else {
                meta.displayName(PbText.item("&cНекорректное название"));
                meta.lore(List.of(
                    PbText.item("&7Нужно от " + MIN_NAME_LENGTH
                        + " до " + MAX_NAME_LENGTH + " символов")
                ));
            }
        });
    }

    private static boolean isValid(@NonNull String name) {
        return name.length() >= MIN_NAME_LENGTH && name.length() <= MAX_NAME_LENGTH;
    }

    private static class AnvilEventsListener implements Listener {

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onPrepareAnvil(PrepareAnvilEvent event) {
            if (!(event.getView().getPlayer() instanceof Player player)) return;
            LevelNameAnvilMenu menu = ACTIVE_MENUS.get(player.getUniqueId());
            if (menu == null) return;

            String renameText = event.getInventory().getRenameText();
            if (renameText == null || renameText.trim().isEmpty()) {
                renameText = DEFAULT_NAME;
            } else {
                renameText = renameText.trim();
            }

            menu.currentName = renameText;

            event.setResult(buildResult(renameText));
            event.getInventory().setRepairCost(0);
            event.getInventory().setMaximumRepairCost(0);

            menu.plugin.getServer().getScheduler().runTask(menu.plugin, () -> {
                if (player.isOnline() && ACTIVE_MENUS.containsKey(player.getUniqueId())) {
                    event.getInventory().setRepairCost(0);
                    try {
                        event.getView().setProperty(InventoryView.Property.REPAIR_COST, 0);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            LevelNameAnvilMenu menu = ACTIVE_MENUS.get(player.getUniqueId());
            if (menu == null) return;

            if (event.getRawSlot() == 0 || event.getRawSlot() == 1) {
                event.setCancelled(true);
                return;
            }

            // Клик по результату
            if (event.getRawSlot() == 2) {
                event.setCancelled(true);

                String name = menu.currentName;
                if (event.getInventory() instanceof AnvilInventory anvil) {
                    String text = anvil.getRenameText();
                    if (text != null && !text.trim().isEmpty()) {
                        name = text.trim();
                    }
                }

                if (!isValid(name)) {
                    player.sendMessage(PbText.of("&cНазвание должно быть от " + MIN_NAME_LENGTH
                        + " до " + MAX_NAME_LENGTH + " символов."));
                    return;
                }

                ACTIVE_MENUS.remove(player.getUniqueId());
                player.closeInventory();

                // Возвращаем инвентарь игроку ПЕРЕД открытием следующего меню
                menu.restoreInventory(player);

                final String finalName = name;
                menu.plugin.getServer().getScheduler().runTask(menu.plugin, () -> {
                    if (player.isOnline()) {
                        new CreateLevelMenu(menu.plugin, menu.lang, finalName).open(player);
                    }
                });
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player player)) return;
            LevelNameAnvilMenu menu = ACTIVE_MENUS.remove(player.getUniqueId());
            if (menu != null) {
                menu.restoreInventory(player); // Обязательно возвращаем инвентарь, если игрок закрыл меню через ESC
                event.getInventory().clear();
            }
        }

        // Подстраховка: если игрок вышел с открытым меню
        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerQuit(PlayerQuitEvent event) {
            LevelNameAnvilMenu menu = ACTIVE_MENUS.remove(event.getPlayer().getUniqueId());
            if (menu != null) {
                menu.restoreInventory(event.getPlayer());
            }
        }
    }
}
