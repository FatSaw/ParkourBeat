// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/replay/ReplayFrame.java
package ru.sortix.parkourbeat.replay;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.World;

@Getter
public class ReplayFrame {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final boolean sneaking;
    private final boolean sprinting;
    private final boolean swinging;

    public ReplayFrame(double x, double y, double z, float yaw, float pitch,
                       boolean sneaking, boolean sprinting, boolean swinging) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.sneaking = sneaking;
        this.sprinting = sprinting;
        this.swinging = swinging;
    }

    @NonNull
    public static ReplayFrame of(@NonNull Location location, boolean sneaking, boolean sprinting, boolean swinging) {
        return new ReplayFrame(location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch(), sneaking, sprinting, swinging);
    }

    @NonNull
    public Location toLocation(@NonNull World world) {
        return new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
    }

    @NonNull
    public ReplayFrame interpolate(@NonNull ReplayFrame next, double progress) {
        double t = Math.max(0.0D, Math.min(1.0D, progress));
        return new ReplayFrame(
            this.x + (next.x - this.x) * t,
            this.y + (next.y - this.y) * t,
            this.z + (next.z - this.z) * t,
            lerpAngle(this.yaw, next.yaw, (float) t),
            this.pitch + (next.pitch - this.pitch) * (float) t,
            this.sneaking,
            this.sprinting,
            this.swinging);
    }

    private static float lerpAngle(float from, float to, float t) {
        float delta = ((to - from) % 360f + 540f) % 360f - 180f;
        return from + delta * t;
    }
}
