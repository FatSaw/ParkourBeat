package ru.sortix.parkourbeat.commands;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.sortix.parkourbeat.ParkourBeat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.sortix.parkourbeat.utils.lang.Lang;
import ru.sortix.parkourbeat.utils.lang.PlayerLang;
import ru.sortix.parkourbeat.utils.text.PbText;
/**
 * Корневая команда {@code /parkourbeat} с коротким алиасом {@code /pb}.
 * <p>
 * Своей логики у подкоманд здесь нет: они как были, так и остаются самостоятельными
 * командами верхнего уровня, а этот класс лишь переадресует к ним. Благодаря этому
 * {@code /menu} и {@code /pb menu} — это буквально одно и то же, права и подсказки
 * работают сами собой, и новая команда плагина получает префикс без единой правки.
 * <p>
 * Без аргументов печатает холодную справку — тот же список, что и в меню.
 */
public class CommandRoot implements CommandExecutor, TabCompleter {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Строка справки: сама команда, аргументы и описание.
     */
    private record Entry(@NonNull String command, @Nullable String permission) {
        /**
         * Аргументы и описание берутся из lang.yml по имени самой команды, а не
         * хранятся здесь: список статический и собирается при загрузке класса,
         * когда языка ещё нет.
         */
        @NonNull
        String args(String locale) {
            String key = "command.help." + this.command + ".args";
            String value = Lang.raw(locale, key);
            return value.equals(key) ? "" : value;
        }

        @NonNull
        String description(String locale) {
            return Lang.raw(locale, "command.help." + this.command + ".description");
        }
    }

    /**
     * Единый список команд. Раньше он был разрезан на «Игроку / Строителю / Администратору»,
     * но деление ничего не давало: команды всё равно скрываются по правам, а искать нужную
     * в трёх коротких блоках неудобнее, чем в одном алфавитном списке.
     */
    private static final List<Entry> COMMANDS = List.of(
        new Entry("menu", "parkourbeat.command.menu"),
        new Entry("play", "parkourbeat.command.play"),
        new Entry("join", "parkourbeat.command.play"),
        new Entry("friend", "parkourbeat.command.play"),
        new Entry("tptoggle", "parkourbeat.command.play"),
        new Entry("spawn", "parkourbeat.command.spawn"),
        new Entry("stat", "parkourbeat.command.play"),
        new Entry("top", "parkourbeat.command.play"),
        new Entry("lvlstat", "parkourbeat.command.play"),
        new Entry("autolook", "parkourbeat.command.autolook"),
        new Entry("create", "parkourbeat.command.create"),
        new Entry("edit", "parkourbeat.command.edit"),
        new Entry("delete", "parkourbeat.command.delete"),
        new Entry("template", "parkourbeat.command.template"),
        new Entry("updatetrack", "parkourbeat.command.updatetrack"),
        new Entry("moderate", "parkourbeat.command.moderate"),
        new Entry("status", "parkourbeat.command.status"),
        new Entry("statreset", "parkourbeat.command.statreset"),
        new Entry("lookangle", "parkourbeat.command.lookangle"),
        new Entry("backtol", "parkourbeat.command.backtol"),
        new Entry("debugmode", "parkourbeat.command.debugmode"),
        new Entry("physicsdebug", "parkourbeat.command.physicsdebug"),
        new Entry("tptoworld", "parkourbeat.command.tptoworld"),
        new Entry("bypassprivate", "parkourbeat.command.bypassprivate"),
        new Entry("convertdata", "parkourbeat.command.convertdata")
    );

    private final @NonNull ParkourBeat plugin;

    public CommandRoot(@NonNull ParkourBeat plugin) {
        this.plugin = plugin;
    }

    /**
     * Подключить команду. Регистрация мягкая: если в plugin.yml секции нет, плагин
     * просто продолжит работать без префиксной команды, а не упадёт при старте.
     */
    public static void register(@NonNull ParkourBeat plugin) {
        org.bukkit.command.PluginCommand command = plugin.getCommand("parkourbeat");
        if (command == null) {
            plugin.getLogger().warning("Команда /parkourbeat не объявлена в plugin.yml,"
                + " префикс /pb работать не будет");
            return;
        }
        CommandRoot root = new CommandRoot(plugin);
        command.setExecutor(root);
        command.setTabCompleter(root);
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command,
                             @NonNull String label, @NonNull String[] args) {
        if (args.length == 0) {
            this.sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help") || sub.equals("?") || sub.equals("справка")) {
            this.sendHelp(sender);
            return true;
        }

        // Переадресация: /pb menu превращается в /menu со всеми исходными аргументами.
        StringBuilder line = new StringBuilder(sub);
        for (int i = 1; i < args.length; i++) {
            line.append(' ').append(args[i]);
        }
        Bukkit.dispatchCommand(sender, line.toString());
        return true;
    }

    private void sendHelp(@NonNull CommandSender sender) {
        sender.sendMessage(Component.empty());
        String lang = PlayerLang.of(sender);
        sender.sendMessage(Lang.text(lang, "command.help.header"));
        sender.sendMessage(Lang.text(lang, "command.help.prefixnote"));
        sender.sendMessage(Component.empty());

        // Команды, на которые у отправителя нет прав, не показываются:
        // обычному игроку незачем видеть админский список.
        for (Entry entry : COMMANDS) {
            if (entry.permission() != null && !sender.hasPermission(entry.permission())) continue;
            String args = entry.args(lang).isEmpty() ? "" : " &7" + entry.args(lang);
            sender.sendMessage(PbText.of("&8 \u2022 &f/" + entry.command() + args
                + " &8- &7" + entry.description(lang)));
        }

        sender.sendMessage(Component.empty());
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command,
                                      @NonNull String label, @NonNull String[] args) {
        if (args.length != 1) return List.of();

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Entry entry : COMMANDS) {
            if (entry.permission() != null && !sender.hasPermission(entry.permission())) continue;
            if (entry.command().startsWith(prefix)) result.add(entry.command());
        }
        return result;
    }
}
