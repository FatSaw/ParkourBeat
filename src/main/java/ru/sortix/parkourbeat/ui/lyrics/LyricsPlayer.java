package ru.sortix.parkourbeat.ui.lyrics;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.sortix.parkourbeat.ui.PacketHologram;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * ШОУ ИЗ СУБТИТРОВ: слова появляются перед игроком (TextDisplay через ViaVersion),
 * строго по времени трека.
 * <ul>
 *   <li><b>Держится с игроком.</b> Фраза «едет» пассажиром на игроке и поворачивается за
 *       взглядом по горизонтали: бежишь, прыгаешь, разворачиваешься - фраза перед тобой.
 *       Клиент двигает её сам каждый кадр, без сетевых рывков.</li>
 *   <li><b>Не в одной точке.</b> Каждая фраза - в своём месте относительно взгляда:
 *       случайно левее/правее, выше/ниже, на 4.2-5.4 блока впереди.</li>
 *   <li><b>Стили</b> проигрываются по кадрам (сервер шлёт сдвиг каждый тик, клиент
 *       сглаживает): POP раздувается, SLIDE влетает сбоку - каждый раз случайно слева или
 *       справа, RISE снизу, ZOOM издалека, NEON пульсирует. После влёта - еле заметный дрейф.</li>
 *   <li><b>Сквозь стены</b> и <b>печатание</b> (невидимые буквы-метки + шейдер РП).</li>
 * </ul>
 * Координаты сдвига - в системе текста: X - вправо, Y - вверх, -Z - вперёд по взгляду.
 */
public final class LyricsPlayer {
    private LyricsPlayer() {
    }

    public enum Style { POP, SLIDE, RISE, ZOOM, NEON }

    private static final int FADE_TICKS = 4;
    private static final TextColor INVISIBLE = TextColor.color(0x01FE03);

    private static final class Live {
        final LyricCue cue;
        final PacketHologram holo;
        final float[] from;   // x, y, z, scale
        final float[] to;
        final float[] drift;  // x, y
        final int intro;
        int age;
        int shown = -1;

        Live(LyricCue cue, PacketHologram holo, float[] from, float[] to, float[] drift, int intro) {
            this.cue = cue;
            this.holo = holo;
            this.from = from;
            this.to = to;
            this.drift = drift;
            this.intro = intro;
        }
    }

    public static void start(@NonNull Plugin plugin, @NonNull Player player, @NonNull List<LyricCue> cues, int styleIndex,
                             int reveal, @NonNull LongSupplier songTime, @NonNull BooleanSupplier playing) {
        if (cues.isEmpty() || !PacketHologram.canShowTo(player)) return;
        Style style = Style.values()[Math.max(0, Math.min(Style.values().length - 1, styleIndex))];
        new BukkitRunnable() {
            private int next = 0;
            private final List<Live> live = new ArrayList<>();

            @Override
            public void run() {
                if (!player.isOnline() || !playing.getAsBoolean()) {
                    for (Live l : this.live) l.holo.hide(player);
                    this.live.clear();
                    this.cancel();
                    return;
                }
                long now = songTime.getAsLong();
                while (this.next < cues.size() && cues.get(this.next).end < now) this.next++;
                while (this.next < cues.size() && cues.get(this.next).start <= now + 25) {
                    this.live.add(spawn(player, cues.get(this.next++), style, reveal, now));
                }
                for (Iterator<Live> it = this.live.iterator(); it.hasNext(); ) {
                    Live l = it.next();
                    l.age++;
                    long left = l.cue.end - now;
                    if (left <= -FADE_TICKS * 50L) {
                        l.holo.hide(player);
                        it.remove();
                        continue;
                    }
                    int visible = visibleChars(l.cue, now, reveal);
                    if (visible != l.shown) {
                        l.shown = visible;
                        l.holo.setText(render(l.cue.text, style, visible));
                    }
                    if (l.age <= l.intro) {
                        // влёт по кадрам: каждый тик - следующая точка (клиент сглаживает за 2 тика)
                        double t = ease(l.age / (double) l.intro);
                        l.holo.transform(lerp(l.from[0], l.to[0], t), lerp(l.from[1], l.to[1], t),
                            lerp(l.from[2], l.to[2], t), lerp(l.from[3], l.to[3], t), 2);
                    } else if (l.age == l.intro + 1 && style != Style.NEON) {
                        // еле заметный дрейф за всё оставшееся время
                        int rest = (int) Math.max(2, (l.cue.end - now) / 50L + FADE_TICKS);
                        l.holo.transform(l.to[0] + l.drift[0], l.to[1] + l.drift[1], l.to[2], l.to[3], rest);
                    } else if (style == Style.NEON && l.age % 6 == 0) {
                        l.holo.transform(l.to[0], l.to[1], l.to[2], l.age % 12 == 0 ? 1.15F : 1.32F, 6);
                    }
                    if (left < FADE_TICKS * 50L) {
                        int step = (int) Math.max(0, Math.min(FADE_TICKS, (FADE_TICKS * 50L - left) / 50L));
                        l.holo.setOpacity(255 - step * 55);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Новая фраза: своё место относительно взгляда (случайный разброс) и откуда влетает. */
    @NonNull
    private static Live spawn(@NonNull Player player, @NonNull LyricCue cue, @NonNull Style style, int reveal, long now) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float x = (float) r.nextDouble(-1.3, 1.3);
        float y = (float) r.nextDouble(-0.35, 0.55);
        float z = -(float) r.nextDouble(4.2, 5.4);
        float scale = scale(style);
        float[] to = {x, y, z, scale};
        float[] from;
        int intro;
        switch (style) {
            case POP: from = new float[]{x, y, z, 0.25F}; intro = 4; break;
            case SLIDE: from = new float[]{x + (r.nextBoolean() ? 3.2F : -3.2F), y, z, scale}; intro = 6; break; // то слева, то справа
            case RISE: from = new float[]{x, y - 1.4F, z, scale}; intro = 6; break;
            case ZOOM: from = new float[]{x * 2.0F, y, z - 9.0F, scale}; intro = 7; break;
            default: from = new float[]{x, y, z, 0.8F}; intro = 3; break;
        }
        float[] drift = {(float) r.nextDouble(-0.2, 0.2), (float) r.nextDouble(0.06, 0.2)};

        int visible = visibleChars(cue, now, reveal);
        PacketHologram holo = new PacketHologram(player.getEyeLocation(), render(cue.text, style, visible), from[3], background(style))
            .style(PacketHologram.BILLBOARD_VERTICAL, 0)
            .translation(from[0], from[1], from[2])
            .seeThrough(true);
        holo.show(player);
        holo.mount(player);
        Live l = new Live(cue, holo, from, to, drift, intro);
        l.shown = visible;
        return l;
    }

    private static float lerp(float a, float b, double t) {
        return (float) (a + (b - a) * t);
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - Math.pow(1 - t, 3);
    }

    /** Сколько символов уже «напечатано» (reveal 0 - сразу всё). */
    private static int visibleChars(@NonNull LyricCue cue, long now, int reveal) {
        int len = cue.text.length();
        if (reveal <= 0 || len <= 1) return len;
        long elapsed = Math.max(0, now - cue.start);
        long budget = Math.max(1, (long) ((cue.end - cue.start) * 0.6));
        if (reveal == 1) {
            String[] words = cue.text.split(" ");
            long perWord = Math.max(40, Math.min(170, budget / Math.max(1, words.length)));
            int count = (int) Math.min(words.length, 1 + elapsed / perWord);
            int chars = 0;
            for (int i = 0; i < count; i++) chars += words[i].length() + (i > 0 ? 1 : 0);
            return Math.min(len, chars);
        }
        long perChar = Math.max(15, Math.min(45, budget / len));
        return (int) Math.min(len, 1 + elapsed / perChar);
    }

    /** Строка целиком: напечатанная часть видна, остальное - невидимым цветом-меткой. */
    @NonNull
    private static Component render(@NonNull String line, @NonNull Style style, int visible) {
        Component shown = styled(Component.text(line.substring(0, visible)), style);
        if (visible >= line.length()) return shown;
        Component hidden = styled(Component.text(line.substring(visible)), style).color(INVISIBLE);
        return Component.text().append(shown).append(hidden).build();
    }

    @NonNull
    private static Component styled(@NonNull Component c, @NonNull Style style) {
        c = c.decoration(TextDecoration.ITALIC, false);
        switch (style) {
            case POP: case ZOOM: return c.color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true);
            case NEON: return c.color(TextColor.color(0xF36BFF)).decoration(TextDecoration.BOLD, true);
            default: return c.color(NamedTextColor.WHITE);
        }
    }

    /** Итоговый масштаб: дальше чем раньше - поэтому крупнее, чтобы читалось. */
    private static float scale(@NonNull Style style) {
        switch (style) {
            case POP: return 1.7F;
            case NEON: return 1.6F;
            case ZOOM: return 1.6F;
            default: return 1.5F;
        }
    }

    private static int background(@NonNull Style style) {
        switch (style) {
            case NEON: return 0x80140620;
            case POP: case ZOOM: return 0x00000000;
            default: return 0x50140620;
        }
    }
}
