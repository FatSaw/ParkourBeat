package ru.sortix.parkourbeat.commands;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.twod.TwoDTuning;
import ru.sortix.parkourbeat.utils.text.PbText;

/**
 * Крутилки 2D-режима прямо на сервере: посмотреть все значения, поменять любое и
 * сохранить в config.yml. Именно этим выравнивается поворот кубика, если текстура
 * в ресурспаке лежит не на той стороне блока.
 */
@Command(name = "2d")
@RequiredArgsConstructor
public class CommandTwoD {

    private final ParkourBeat plugin;

    @Execute(name = "list")
    @Permission("parkourbeat.command.2d")
    public void onList(@Context CommandSender sender) {
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_list.1")));
        for (String key : TwoDTuning.getKeys()) {
            sender.sendMessage(PbText.of("&8- &7" + key + "&8: &f" + TwoDTuning.getValue(key)));
        }
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_list.2")));
    }

    @Execute(name = "get")
    @Permission("parkourbeat.command.2d")
    public void onGet(@Context CommandSender sender, @Arg("key") String key) {
        String value = TwoDTuning.getValue(key);
        if (value == null) {
            sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_get.1") + key));
            return;
        }
        sender.sendMessage(PbText.of("&7" + key + "&8: &f" + value));
    }

    @Execute(name = "set")
    @Permission("parkourbeat.command.2d")
    public void onSet(@Context CommandSender sender, @Arg("key") String key, @Arg("value") String value) {
        if (!TwoDTuning.setValue(key, value)) {
            sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_set.1") + key + Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_set.2") + value));
            sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_set.3")));
            return;
        }
        TwoDTuning.save(this.plugin);
        sender.sendMessage(PbText.of("&a" + key + " &8= &f" + TwoDTuning.getValue(key)));
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_set.4")));
    }

    /**
     * Диагностика фиксации камеры: какой способ разворота реально доступен на этой
     * сборке. Гадать вслепую больше не нужно - команда пробует все по очереди.
     */
    @Execute(name = "diag")
    @Permission("parkourbeat.command.2d")
    public void onDiag(@Context CommandSender sender) {
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.1")));
        sender.sendMessage(PbText.of("&7camera_mode&8: &f" + TwoDTuning.CAMERA_MODE));
        sender.sendMessage(PbText.of("&7lock_camera&8: &f" + TwoDTuning.LOCK_CAMERA
            + Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.2") + TwoDTuning.CAMERA_LOCK_PERIOD));
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.3")
            + ru.sortix.parkourbeat.twod.TwoDEntityUtils.getLastRotationMethod()));

        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.4")));
            return;
        }

        ru.sortix.parkourbeat.twod.TwoDEntityUtils.resetRotationMethods();

        float yaw = player.getLocation().getYaw() + 45f;
        float pitch = player.getLocation().getPitch();

        boolean protocolLib = ru.sortix.parkourbeat.twod.TwoDEntityUtils
            .sendRotationPacket(player, yaw, pitch);
        boolean nms = ru.sortix.parkourbeat.twod.TwoDEntityUtils
            .sendNmsRotation(player, yaw, pitch);
        boolean paper = ru.sortix.parkourbeat.twod.TwoDEntityUtils
            .paperRotate(player, yaw, pitch);

        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.5") + (protocolLib ? Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.6") : Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.7"))));
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.8") + (nms ? Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.9") : Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.10"))));
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.11") + (paper ? Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.12") : Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.13"))));
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.14")
            + Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.15")));
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_diag.16")));
    }

    /**
     * Проверка фиксации камеры: разворачивает вас на 90 градусов и говорит, каким
     * способом это удалось сделать. Нужна ровно тогда, когда камера не держится.
     */
    @Execute(name = "rotate")
    @Permission("parkourbeat.command.2d")
    public void onRotate(@Context CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_rotate.1")));
            return;
        }

        float yaw = player.getLocation().getYaw() + 90f;
        float pitch = 0f;

        // Точку для взгляда считаем ПО УГЛУ и уносим далеко: клиент вычисляет угол
        // от своей позиции, и на близкой точке любая рассинхронизация превращается
        // в перекос. Раньше сюда передавалась позиция самого игрока - отсюда и
        // случайные градусы при шаге в сторону.
        double distance = ru.sortix.parkourbeat.twod.TwoDTuning.LOOK_TARGET_DISTANCE;
        double radians = Math.toRadians(yaw);
        double targetX = player.getLocation().getX() - Math.sin(radians) * distance;
        double targetZ = player.getLocation().getZ() + Math.cos(radians) * distance;
        double targetY = player.getEyeLocation().getY();

        ru.sortix.parkourbeat.twod.TwoDEntityUtils.resetRotationMethods();
        ru.sortix.parkourbeat.twod.TwoDEntityUtils.lockRotation(player,
            targetX, targetY, targetZ, yaw, pitch);

        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_rotate.2")
            + ru.sortix.parkourbeat.twod.TwoDEntityUtils.getLastRotationMethod()));
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_rotate.3")));
    }

    /**
     * Сбросить настройки на значения по умолчанию.
     * <p>
     * Нужна потому, что любая правка через {@code set} записывает в config.yml ВЕСЬ
     * набор ключей разом. После этого новые значения по умолчанию из обновлений
     * плагина уже не применяются - в конфиге лежат старые.
     */
    @Execute(name = "reset")
    @Permission("parkourbeat.command.2d")
    public void onReset(@Context CommandSender sender) {
        this.plugin.getConfig().set("two_d", null);
        this.plugin.saveConfig();

        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_reset.1")));
        sender.sendMessage(PbText.of(Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_reset.2")
            + Lang.raw(PlayerLang.of(sender), "auto.command_two_d.on_reset.3")));
    }

    @Execute(name = "face")
    @Permission("parkourbeat.command.2d")
    public void onFace(@Context CommandSender sender, @Arg("face") String face) {
        this.onSet(sender, "cube_face", face);
    }

    @Execute
    @Permission("parkourbeat.command.2d")
    public void onDefault(@Context CommandSender sender) {
        this.onList(sender);
    }

    @NonNull
    public ParkourBeat getPlugin() {
        return this.plugin;
    }
}
