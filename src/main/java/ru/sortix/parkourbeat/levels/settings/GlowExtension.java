package ru.sortix.parkourbeat.levels.settings;

import org.bukkit.block.BlockFace;
import ru.sortix.parkourbeat.utils.lang.LangOptions;

public enum GlowExtension {
    UP(BlockFace.DOWN, LangOptions.direction_up, "↑"),
    DOWN(BlockFace.UP, LangOptions.direction_down, "↓"),
    NORTH(BlockFace.SOUTH, LangOptions.direction_north, "N"),
    SOUTH(BlockFace.NORTH, LangOptions.direction_south, "S"),
    WEST(BlockFace.EAST, LangOptions.direction_west, "W"),
    EAST(BlockFace.WEST, LangOptions.direction_east, "E");

    public final BlockFace attachedFace;
    public final LangOptions langOption;
    public final String arrow;

    GlowExtension(BlockFace attachedFace, LangOptions langOption, String arrow) {
        this.attachedFace = attachedFace;
        this.langOption = langOption;
        this.arrow = arrow;
    }

    public GlowExtension next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
