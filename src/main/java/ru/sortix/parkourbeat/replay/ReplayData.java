// ФАЙЛ: src/main/java/ru/sortix/parkourbeat/replay/ReplayData.java
package ru.sortix.parkourbeat.replay;

import lombok.Getter;
import lombok.NonNull;
import ru.sortix.parkourbeat.rating.JumpResult;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class ReplayData {
    public static final int FORMAT_VERSION = 3;
    public static final int MAX_FRAMES = 20 * 60 * 12;

    private final @NonNull UUID playerId;
    private final @NonNull String playerName;
    private final @NonNull UUID levelId;
    private final long recordedAt;
    private final @NonNull List<ReplayFrame> frames;
    private final @NonNull List<ReplayJump> jumps;

    public ReplayData(@NonNull UUID playerId, @NonNull String playerName, @NonNull UUID levelId,
                      long recordedAt, @NonNull List<ReplayFrame> frames, @NonNull List<ReplayJump> jumps) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.levelId = levelId;
        this.recordedAt = recordedAt;
        this.frames = frames;
        this.jumps = jumps;
    }

    public int getFrameCount() {
        return this.frames.size();
    }

    public long getDurationMillis() {
        return this.frames.size() * 50L;
    }

    public void write(@NonNull DataOutputStream out) throws IOException {
        out.writeInt(FORMAT_VERSION);
        out.writeLong(this.playerId.getMostSignificantBits());
        out.writeLong(this.playerId.getLeastSignificantBits());
        out.writeUTF(this.playerName);
        out.writeLong(this.levelId.getMostSignificantBits());
        out.writeLong(this.levelId.getLeastSignificantBits());
        out.writeLong(this.recordedAt);

        out.writeInt(this.frames.size());
        for (ReplayFrame frame : this.frames) {
            out.writeInt(toFixed(frame.getX()));
            out.writeInt(toFixed(frame.getY()));
            out.writeInt(toFixed(frame.getZ()));
            out.writeByte(toAngle(frame.getYaw()));
            out.writeByte(toAngle(frame.getPitch()));
            int flags = (frame.isSneaking() ? 1 : 0) | (frame.isSprinting() ? 2 : 0) | (frame.isSwinging() ? 4 : 0);
            out.writeByte(flags);
        }

        out.writeInt(this.jumps.size());
        for (ReplayJump jump : this.jumps) {
            out.writeInt(jump.getFrameIndex());
            out.writeUTF(jump.getResult().name());
        }
    }

    private static int toFixed(double value) {
        return (int) Math.round(value * 1000.0D);
    }

    private static double fromFixed(int value) {
        return value / 1000.0D;
    }

    private static byte toAngle(float value) {
        return (byte) Math.round(value * 256.0F / 360.0F);
    }

    private static float fromAngle(byte value) {
        return value * 360.0F / 256.0F;
    }

    @Nullable
    public static ReplayData read(@NonNull DataInputStream in) throws IOException {
        int version = in.readInt();
        if (version < 1 || version > 3) return null;

        UUID playerId = new UUID(in.readLong(), in.readLong());
        String playerName = in.readUTF();
        UUID levelId = new UUID(in.readLong(), in.readLong());
        long recordedAt = in.readLong();

        int size = in.readInt();
        if (size < 0 || size > MAX_FRAMES) return null;

        List<ReplayFrame> frames = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double x = fromFixed(in.readInt());
            double y = fromFixed(in.readInt());
            double z = fromFixed(in.readInt());
            float yaw = fromAngle(in.readByte());
            float pitch = fromAngle(in.readByte());
            int flags = in.readByte();

            boolean swing = version >= 3 && (flags & 4) != 0;
            frames.add(new ReplayFrame(x, y, z, yaw, pitch, (flags & 1) != 0, (flags & 2) != 0, swing));
        }

        List<ReplayJump> jumps = new ArrayList<>();
        if (version >= 2) {
            int jumpsSize = in.readInt();
            for (int i = 0; i < jumpsSize; i++) {
                int frameIndex = in.readInt();
                JumpResult res = JumpResult.valueOf(in.readUTF());
                jumps.add(new ReplayJump(frameIndex, res));
            }
        }

        return new ReplayData(playerId, playerName, levelId, recordedAt, frames, jumps);
    }
}
