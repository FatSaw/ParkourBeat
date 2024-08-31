package ru.sortix.parkourbeat.utils;

import lombok.NonNull;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class GeometricUtils {
    @NonNull
    public static List<Vector> createCircleOffsets(double radius, int pointsAmount) {
        List<Vector> result = new ArrayList<>();
        double angle = 360d / pointsAmount;
        for (int angleIndex = 0; angleIndex < pointsAmount; angleIndex++) {
            result.add(new Vector(
                radius * Math.cos(angleIndex * angle),
                0,
                radius * Math.sin(angleIndex * angle)
            ));
        }
        return result;
    }
}
