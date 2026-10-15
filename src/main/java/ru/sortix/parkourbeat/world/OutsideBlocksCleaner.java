package ru.sortix.parkourbeat.world;

import lombok.NonNull;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Стирает блоки, поставленные за границей области уровня.
 * <p>
 * Раньше расчёт был на то, что чанки за границей просто не будут сохранены: перед
 * {@code world.save()} они отгружались через {@code chunk.unload(false)}. Приём не работает,
 * когда рядом стоит сам строитель: чанк с игроком отгрузить нельзя, {@code unload} возвращает
 * false, и следом {@code world.save()} честно пишет его на диск. А WorldEdit почти всегда
 * применяют рядом с собой - именно поэтому блоки за границей переживали перезаход, хотя
 * плагин обещал обратное.
 * <p>
 * Поэтому содержимое таких чанков теперь удаляется явно, а не «не сохраняется». Это
 * заодно чистит и то, что успело попасть на диск раньше: пустой чанк перезаписывает
 * старую запись в региональном файле.
 */
public final class OutsideBlocksCleaner {
    private OutsideBlocksCleaner() {
    }

    /**
     * Убирает все блоки чанка.
     * <p>
     * Пустые чанки (а их за границей подавляющее большинство) отсеиваются по карте высот,
     * так что обычный проход стоит 256 обращений к ней и ничего больше.
     *
     * @return true, если в чанке действительно были блоки и они удалены
     */
    public static boolean clearChunk(@NonNull Chunk chunk) {
        World world = chunk.getWorld();
        int minY = getMinHeight(world);
        int maxY = world.getMaxHeight() - 1;

        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        int topY = Integer.MIN_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int columnTop = world.getHighestBlockYAt(baseX + x, baseZ + z);
                if (columnTop > topY) topY = columnTop;
            }
        }

        if (topY < minY) return false;
        if (topY > maxY) topY = maxY;

        // Проходим все столбцы до самой высокой найденной отметки, а не каждый до своей:
        // карта высот не видит блоки, сквозь которые можно пройти (факелы, ковры, растения),
        // и такой столбец выглядел бы пустым. Заодно верхняя граница держит объём работы
        // на уровне реально застроенной высоты, а не всей высоты мира.
        boolean cleared = false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = topY; y >= minY; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType().isAir()) continue;
                    // false - без обновления физики: соседних блоков всё равно не остаётся,
                    // а рассыпающиеся песок и вода только растянули бы удаление на тики.
                    block.setType(Material.AIR, false);
                    cleared = true;
                }
            }
        }
        return cleared;
    }

    private static int getMinHeight(@NonNull World world) {
        try {
            // getMinHeight() появился только в 1.17: на 1.16 мир всегда начинается с нуля.
            return world.getMinHeight();
        } catch (Throwable t) {
            return 0;
        }
    }
}
