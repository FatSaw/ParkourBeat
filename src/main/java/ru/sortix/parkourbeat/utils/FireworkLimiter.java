package ru.sortix.parkourbeat.utils;

import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * ОГРАНИЧЕНИЕ ФЕЙЕРВЕРКОВ.
 * <p>
 * Строители в креативе могли пускать фейерверки без ограничений и летать на элитрах
 * по всей карте - сервер падал до 15 TPS: каждая ракета - сущность, а быстрый полёт
 * ещё и грузит чанки. Здесь:
 * <ul>
 *   <li>не больше {@link #PER_PLAYER} ракет одного игрока в воздухе одновременно;</li>
 *   <li>не больше {@link #PER_WORLD} ракет в одном мире;</li>
 *   <li>после запуска - откат {@link #COOLDOWN_TICKS} тиков на предмет (видно на хотбаре);</li>
 *   <li>не больше {@link #MAX_EXPLOSIONS} взрывов в одной ракете (лишние срезаются).</li>
 * </ul>
 * Ракеты, которые запускает сам плагин (без игрока), не трогаются. Выключить: {@link #ENABLED}.
 */
public final class FireworkLimiter implements Listener {
    public static volatile boolean ENABLED = true;
    public static int PER_PLAYER = 4;
    public static int PER_WORLD = 40;
    public static int COOLDOWN_TICKS = 10;
    public static int MAX_EXPLOSIONS = 3;

    private static boolean registered;

    private FireworkLimiter() {
    }

    public static void register(@NonNull Plugin plugin) {
        if (registered) return;
        registered = true;
        plugin.getServer().getPluginManager().registerEvents(new FireworkLimiter(), plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof Firework)) return;
        Firework firework = (Firework) event.getEntity();
        UUID spawner = firework.getSpawningEntity();
        if (spawner == null) return; // плагин/механика - не трогаем
        Player player = org.bukkit.Bukkit.getPlayer(spawner);
        if (player == null) return;

        World world = firework.getWorld();
        int inWorld = 0, mine = 0;
        for (Entity entity : world.getEntitiesByClass(Firework.class)) {
            if (entity == firework) continue;
            inWorld++;
            if (spawner.equals(((Firework) entity).getSpawningEntity())) mine++;
        }
        if (mine >= PER_PLAYER || inWorld >= PER_WORLD) {
            event.setCancelled(true);
            return;
        }

        try {
            FireworkMeta meta = firework.getFireworkMeta();
            if (meta.getEffectsSize() > MAX_EXPLOSIONS) {
                java.util.List<org.bukkit.FireworkEffect> keep =
                    new java.util.ArrayList<>(meta.getEffects().subList(0, MAX_EXPLOSIONS));
                meta.clearEffects();
                meta.addEffects(keep);
                firework.setFireworkMeta(meta);
            }
        } catch (Throwable ignored) {
        }

        try {
            if (!player.hasCooldown(Material.FIREWORK_ROCKET)) {
                player.setCooldown(Material.FIREWORK_ROCKET, COOLDOWN_TICKS);
            }
        } catch (Throwable ignored) {
        }
    }
}
