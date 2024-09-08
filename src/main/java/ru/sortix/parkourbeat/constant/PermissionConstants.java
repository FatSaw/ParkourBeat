package ru.sortix.parkourbeat.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PermissionConstants {

    private static final String PREFIX = "parkourbeat.";
    public static final String COMMAND_PERMISSION = PREFIX + "command.";

    public static final String CREATE_LEVEL
        = PREFIX + "player.createlevels";
    public static final String COLORED_CHAT
        = PREFIX + "staff.coloredchat";
    public static final String VIEW_TECH_LEVELS_INFO
        = PREFIX + "staff.techlevelsinfo";
    public static final String EDIT_LOBBY
        = PREFIX + "staff.editlobby";
    public static final String EDIT_OTHERS_LEVELS
        = PREFIX + "staff.editotherslevels";
    public static final String EDIT_OTHERS_LEVELS_ON_MODERATION
        = PREFIX + "staff.editmoderatinglevels";
    public static final String MODERATE_LEVELS
        = PREFIX + "staff.moderate";

}
