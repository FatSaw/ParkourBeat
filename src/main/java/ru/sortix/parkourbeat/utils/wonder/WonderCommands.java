package ru.sortix.parkourbeat.utils.wonder;

import ru.sortix.parkourbeat.utils.lang.PlayerLang;

import ru.sortix.parkourbeat.utils.lang.Lang;

import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.levels.settings.LightShowSettings;
import ru.sortix.parkourbeat.levels.wonder.WonderAnchor;
import ru.sortix.parkourbeat.levels.wonder.WonderBridge;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.levels.wonder.WonderPreset;
import ru.sortix.parkourbeat.levels.wonder.WonderPresets;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Разбор и выполнение одной строки /pbllmeffects.
 * <p>
 * Через этот класс проходит всё: и то, что предложил встроенный ИИ, и то, что строитель
 * скопировал из внешней модели, и ручной ввод. Одна точка входа — одна логика проверок,
 * поэтому невозможно создать эффект, который не пережил бы валидацию.
 */
public final class WonderCommands {

    private WonderCommands() {
    }

    /** Итог выполнения одной строки: что сказать строителю и менялся ли уровень. */
    public static final class Result {
        public final boolean changed;
        public final @NonNull String message;

        private Result(boolean changed, @NonNull String message) {
            this.changed = changed;
            this.message = message;
        }

        static Result ok(@NonNull String message) {
            return new Result(true, "&a" + message);
        }

        static Result info(@NonNull String message) {
            return new Result(false, "&7" + message);
        }

        static Result error(@NonNull String message) {
            return new Result(false, "&c" + message);
        }
    }

    @NonNull
    public static Result execute(@NonNull Player player,
                                 @NonNull LightShowSettings lightShow,
                                 @NonNull String line
    ) {
        String fixed = WonderFix.repair(line);
        String[] args = (fixed == null ? line.trim() : fixed).split("\\s+");
        if (args.length == 0 || args[0].isEmpty()) return Result.error(Lang.raw(PlayerLang.of(player), "auto.wonder_commands.execute.1"));

        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "preset": return preset(lightShow, args);
                case "text": return text(lightShow, args);
                case "add": return add(lightShow, args);
                case "edit": return edit(player, lightShow, args);
                case "del": case "delete": case "remove": return delete(lightShow, args);
                case "clear": return clear(lightShow);
                case "list": return list(lightShow);
                default: return Result.error(Lang.raw(PlayerLang.of(player), "auto.wonder_commands.execute.2") + action);
            }
        } catch (RuntimeException e) {
            return Result.error(Lang.raw(PlayerLang.of(player), "auto.wonder_commands.execute.3") + (e.getMessage() == null ? action : e.getMessage()));
        }
    }

    // ------------------------------------------------------------- действия

    private static Result preset(@NonNull LightShowSettings lightShow, @NonNull String[] args) {
        if (args.length < 4) return Result.error("Нужно: preset <начало> <конец> <идентификатор> [ключ:значение]");
        int start = time(args[1]);
        int end = time(args[2]);
        if (start < 0 || end < 0) return Result.error("Не понял таймкод.");

        WonderPreset preset = WonderPresets.byId(args[3].toLowerCase(Locale.ROOT));
        if (preset == null) return Result.error("Нет такого пресета: " + args[3]);

        WonderEffect effect = preset.toEffect(start);
        effect.setEndMillis(end);
        applyKeys(effect, args, 4);
        return store(lightShow, effect, "Добавлен «" + preset.getDisplay(PlayerLang.DEFAULT_LOCALE) + "» на " + effect.getStartTimecode());
    }

    private static Result text(@NonNull LightShowSettings lightShow, @NonNull String[] args) {
        if (args.length < 4) return Result.error("Нужно: text <начало> <конец> <СЛОВА> [ключ:значение]");
        int start = time(args[1]);
        int end = time(args[2]);
        if (start < 0 || end < 0) return Result.error("Не понял таймкод.");

        StringBuilder words = new StringBuilder();
        int index = 3;
        for (; index < args.length; index++) {
            if (isKeyValue(args[index])) break;
            if (words.length() > 0) words.append(' ');
            words.append(args[index]);
        }
        if (words.length() == 0) return Result.error("Не хватает самого текста.");

        WonderPreset base = WonderPresets.byId("text_fly");
        String spec = "text:" + words.toString().replace(' ', '_') + " @ px:0.28 font:pixel";
        String params = base == null ? "in:fly int:22t out:fade outt:12t face:player" : base.getParams();

        WonderEffect effect = new WonderEffect(start, end, "", spec, params, WonderAnchor.AHEAD);
        applyKeys(effect, args, index);
        return store(lightShow, effect, "Добавлен текст «" + words + "» на " + effect.getStartTimecode());
    }

    private static Result add(@NonNull LightShowSettings lightShow, @NonNull String[] args) {
        if (args.length < 4) return Result.error("Нужно: add <начало> <конец> <описание эффекта>");
        int start = time(args[1]);
        int end = time(args[2]);
        if (start < 0 || end < 0) return Result.error("Не понял таймкод.");

        StringBuilder spec = new StringBuilder();
        List<String> keys = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            // Ключи размещения относятся к эффекту, а не к слою: отделяем их,
            // иначе они уехали бы в LightShow и там были бы просто проигнорированы.
            if (isPlacementKey(args[i])) {
                keys.add(args[i]);
                continue;
            }
            if (spec.length() > 0) spec.append(' ');
            spec.append(args[i]);
        }
        if (spec.length() == 0) return Result.error("Не хватает описания эффекта.");

        WonderEffect effect = new WonderEffect(start, end, "", spec.toString(), "", WonderAnchor.PATH);
        applyKeys(effect, keys.toArray(new String[0]), 0);
        return store(lightShow, effect, "Добавлен свой эффект на " + effect.getStartTimecode());
    }

    private static Result edit(@NonNull Player player,
                               @NonNull LightShowSettings lightShow,
                               @NonNull String[] args
    ) {
        if (args.length < 3) return Result.error(Lang.raw(PlayerLang.of(player), "auto.wonder_commands.edit.1"));
        List<WonderEffect> effects = lightShow.getWonderEffects();
        WonderEffect effect = byIndex(effects, args[1]);
        if (effect == null) return Result.error(Lang.raw(PlayerLang.of(player), "auto.wonder_commands.edit.2") + args[1]);

        applyKeys(effect, args, 2);
        lightShow.sort();
        return Result.ok(Lang.raw(PlayerLang.of(player), "auto.wonder_commands.edit.3") + effect.getStartTimecode() + Lang.raw(PlayerLang.of(player), "auto.wonder_commands.edit.4"));
    }

    private static Result delete(@NonNull LightShowSettings lightShow, @NonNull String[] args) {
        if (args.length < 2) return Result.error("Нужно: del <номер>");
        List<WonderEffect> effects = lightShow.getWonderEffects();
        WonderEffect effect = byIndex(effects, args[1]);
        if (effect == null) return Result.error("Нет эффекта под номером " + args[1]);
        lightShow.removeWonderEffect(effect);
        return Result.ok("Удалён эффект на " + effect.getStartTimecode() + ".");
    }

    private static Result clear(@NonNull LightShowSettings lightShow) {
        int amount = lightShow.getWonderEffectsAmount();
        if (amount == 0) return Result.info("Эффектов и так нет.");
        for (WonderEffect effect : new ArrayList<>(lightShow.getWonderEffects())) {
            lightShow.removeWonderEffect(effect);
        }
        return Result.ok("Удалено эффектов: " + amount);
    }

    private static Result list(@NonNull LightShowSettings lightShow) {
        List<WonderEffect> effects = lightShow.getWonderEffects();
        if (effects.isEmpty()) return Result.info("Эффектов пока нет.");
        StringBuilder sb = new StringBuilder("&7Эффекты уровня:");
        for (int i = 0; i < effects.size(); i++) {
            WonderEffect effect = effects.get(i);
            sb.append("\n&8 #").append(i + 1)
                .append(" &f").append(effect.getStartTimecode())
                .append(" &8- &f").append(effect.getEndTimecode())
                .append(" &8· &d").append(effect.getDisplayName(PlayerLang.DEFAULT_LOCALE));
        }
        return new Result(false, sb.toString());
    }

    // ------------------------------------------------------------- служебное

    /**
     * Собрать эффект из строки, НЕ добавляя его на таймлайн: нужно для предпросмотра плана,
     * который строитель ещё не принял.
     */
    @Nullable
    public static WonderEffect preview(@NonNull String line) {
        return preview(line, java.util.Collections.emptyList());
    }

    /**
     * Собрать то, что показывает строка плана.
     * <p>
     * Для добавления это будущий эффект, для правки — копия существующего с уже применёнными
     * изменениями, для удаления — то, что собираются убрать. Посмотреть можно на любое действие.
     */
    @Nullable
    public static WonderEffect preview(@NonNull String line, @NonNull List<WonderEffect> existing) {
        String fixed = WonderFix.repair(line);
        String[] args = (fixed == null ? line.trim() : fixed).split("\\s+");
        if (args.length < 4) return null;
        String action = args[0].toLowerCase(Locale.ROOT);

        if (action.equals("edit") || action.startsWith("del") || action.equals("remove")) {
            WonderEffect found = byIndex(existing, args[1]);
            if (found == null) return null;
            WonderEffect copy = found.copy();
            if (action.equals("edit")) applyKeys(copy, args, 2);
            guard(copy);
            return copy;
        }

        int start = time(args[1]);
        int end = time(args[2]);
        if (start < 0 || end < 0) return null;

        WonderEffect effect;
        if (action.equals("add")) {
            StringBuilder spec = new StringBuilder();
            List<String> keys = new ArrayList<>();
            for (int i = 3; i < args.length; i++) {
                if (isPlacementKey(args[i])) keys.add(args[i]);
                else {
                    if (spec.length() > 0) spec.append(' ');
                    spec.append(args[i]);
                }
            }
            if (spec.length() == 0) return null;
            effect = new WonderEffect(start, end, "", spec.toString(), "", WonderAnchor.PATH);
            applyKeys(effect, keys.toArray(new String[0]), 0);
            guard(effect);
            return effect;
        }
        if (action.equals("preset")) {
            WonderPreset preset = WonderPresets.byId(args[3].toLowerCase(Locale.ROOT));
            if (preset == null) return null;
            effect = preset.toEffect(start);
            effect.setEndMillis(end);
            applyKeys(effect, args, 4);
        } else if (action.equals("text")) {
            StringBuilder words = new StringBuilder();
            int index = 3;
            for (; index < args.length; index++) {
                if (isKeyValue(args[index])) break;
                if (words.length() > 0) words.append(' ');
                words.append(args[index]);
            }
            if (words.length() == 0) return null;
            effect = new WonderEffect(start, end, "",
                "text:" + words.toString().replace(' ', '_') + " @ px:0.28 font:pixel",
                "in:fly int:22t out:fade outt:14t face:player", WonderAnchor.PATH);
            applyKeys(effect, args, index);
        } else {
            return null;
        }
        guard(effect);
        return effect;
    }

    /**
     * Всё, что попадает на таймлайн, проходит здесь.
     * <p>
     * Три беды повторялись чаще всего: короткая вспышка не успевала собраться, потому что
     * точки раскидываются по refresh кадрам; текст исчезал раньше, чем его успевали прочитать;
     * и dust клали сервер, потому что он дороже end_rod и по трафику, и по отрисовке.
     */
    private static void guard(@NonNull WonderEffect effect) {
        effect.setSpec(WonderMath.normalizeSpec(effect.getSpec()));
        applyApproach(effect);
        applyTurn(effect);
        int duration = effect.getDurationMillis();

        // Эффект зажигается ровно refresh тиков. Полторы секунды жизни при refresh 15 значит,
        // что почти половину времени фигура ещё дособирается.
        int allowedRefresh = duration < 700 ? 2 : duration < 1500 ? 3 : duration < 3000 ? 6 : 20;
        if (WonderSpec.maxRefresh(effect.getSpec()) > allowedRefresh) {
            effect.setSpec(WonderSpec.capRefresh(effect.getSpec(), allowedRefresh));
        }

        // dust дороже, и его легко наставить столько, что сервер ляжет
        if (WonderSpec.usesDust(effect.getSpec())) {
            int points = WonderBridge.isAvailable() ? WonderBridge.estimatePoints(effect) : 0;
            if (points > 150) effect.setSpec(WonderSpec.capRefresh(effect.getSpec(), Math.max(18, allowedRefresh)));
        }

        // Слой из точек по умолчанию размазывался по refresh кадрам и надпись "прогружалась".
        // burst зажигает её целиком в один кадр: пакетов столько же, просто приходят разом.
        String low = effect.getSpec().toLowerCase(Locale.ROOT);
        if (low.startsWith("text:") || low.startsWith("pix:")) {
            effect.setSpec(WonderSpec.set(effect.getSpec(), "burst", "true"));
        }

        // Текст должен уходить плавно и позже самой фразы, иначе его не успевают прочитать
        if (WonderSpec.isText(effect.getSpec())) {
            if (duration < 1200) effect.setEndMillis(effect.getStartMillis() + 1200);
            String params = effect.getParams();
            if (!params.contains("out:")) {
                effect.setParams((params.trim() + " out:fade outt:16t").trim());
            }
        }
    }

    private static Result store(@NonNull LightShowSettings lightShow,
                                @NonNull WonderEffect effect,
                                @NonNull String message
    ) {
        guard(effect);
        String problem = WonderBridge.isAvailable() ? WonderBridge.validate(effect) : null;
        if (problem != null) return Result.error("Эффект не собрался: " + problem);

        if (!lightShow.addWonderEffect(effect)) return Result.error("Достигнут предел эффектов на уровне.");
        lightShow.sort();

        int points = WonderBridge.estimatePoints(effect);
        if (points > 2000) {
            lightShow.removeWonderEffect(effect);
            return Result.error("Слишком тяжёлый эффект: " + points + " точек. Уменьшите steps.");
        }
        return Result.ok(message + (points > 0 ? " &8(точек: " + points + ")" : ""));
    }

    @Nullable
    static WonderEffect byIndex(@NonNull List<WonderEffect> effects, @NonNull String raw) {
        try {
            int index = Integer.parseInt(raw.replace("#", "").trim());
            if (index < 1 || index > effects.size()) return null;
            return effects.get(index - 1);
        } catch (NumberFormatException e) {
            // Модель вполне может сослаться на эффект таймкодом, а не номером
            int millis = time(raw);
            if (millis < 0) return null;
            WonderEffect closest = null;
            int bestDelta = Integer.MAX_VALUE;
            for (WonderEffect effect : effects) {
                int delta = Math.abs(effect.getStartMillis() - millis);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    closest = effect;
                }
            }
            return bestDelta <= 1500 ? closest : null;
        }
    }

    private static boolean isKeyValue(@NonNull String token) {
        int colon = token.indexOf(':');
        return colon > 0 && colon < token.length() - 1 && !token.contains("=");
    }

    private static boolean isPlacementKey(@NonNull String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.startsWith("anchor:") || lower.startsWith("dist:") || lower.startsWith("height:")
            || lower.startsWith("side:") || lower.startsWith("scale:") || lower.startsWith("color:")
            || lower.startsWith("start:") || lower.startsWith("end:") || lower.startsWith("font:")
            || lower.startsWith("approach:") || lower.startsWith("turn:")
            || lower.startsWith("px:") || lower.startsWith("particle:") || lower.startsWith("steps:")
            || lower.startsWith("refresh:") || lower.startsWith("words:");
    }

    private static void applyKeys(@NonNull WonderEffect effect, @NonNull String[] args, int from) {
        for (int i = from; i < args.length; i++) {
            String token = args[i];
            int colon = token.indexOf(':');
            if (colon <= 0) continue;
            String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1);
            switch (key) {
                case "anchor": effect.setAnchor(WonderAnchor.byName(value, effect.getAnchor())); break;
                case "dist": effect.setDistance(number(value, effect.getDistance())); break;
                case "height": effect.setHeight(number(value, effect.getHeight())); break;
                case "side": effect.setSide(number(value, effect.getSide())); break;
                case "scale": effect.setScale(Math.max(0.05D, number(value, effect.getScale()))); break;
                case "approach": effect.setApproach(number(value, -1.0D)); break;
                case "turn":
                    effect.setTurn(value.equalsIgnoreCase("auto") ? Double.NaN : number(value, 0.0D));
                    break;
                case "color": effect.setColor(value); break;
                case "font": effect.setSpec(WonderSpec.set(effect.getSpec(), "font", value)); break;
                case "px": effect.setSpec(WonderSpec.set(effect.getSpec(), "px", value)); break;
                case "particle": effect.setSpec(WonderSpec.set(effect.getSpec(), "particle", value)); break;
                case "steps": effect.setSpec(WonderSpec.set(effect.getSpec(), "steps", value)); break;
                case "refresh": effect.setSpec(WonderSpec.set(effect.getSpec(), "refresh", value)); break;
                case "words": effect.setSpec(WonderSpec.withWords(effect.getSpec(), value.replace('_', ' '))); break;
                case "start": {
                    int millis = time(value);
                    if (millis >= 0) effect.setStartMillis(millis);
                    break;
                }
                case "end": {
                    int millis = time(value);
                    if (millis >= 0) effect.setEndMillis(millis);
                    break;
                }
                default: break;
            }
        }
    }

    /**
     * Подлёт к игроку.
     * <p>
     * Скоростью частицы это сделать нельзя: end_rod живёт около трёх секунд и за свою жизнь
     * пролетает speed * 35 блоков, то есть пятьдесят блоков он покроет только рывком.
     * Поэтому к игроку едет сам слой: в формулу смещения по оси взгляда подставляется плавный
     * разгон на всю длительность эффекта. Дополнительных частиц при этом не появляется,
     * их столько же, просто каждый кадр они рождаются ближе.
     */
    private static void applyApproach(@NonNull WonderEffect effect) {
        // Сам полёт собирается в WonderBridge: там известны и точка, и направление трассы,
        // а значит можно выдать движку честный вектор скорости в мировых осях.
        // Полёт полностью собирается в WonderBridge из двух слоёв.
        // Здесь только вычищаем следы старого способа, если они остались в сохранённом уровне.
        effect.setSpec(WonderSpec.set(effect.getSpec(), "drift", null));
        effect.setSpec(WonderSpec.set(effect.getSpec(), "driftt", null));
        if (effect.getApproach() >= 0) {
            effect.setSpec(WonderSpec.set(effect.getSpec(), "oz", null));
            effect.setSpec(WonderSpec.set(effect.getSpec(), "motion", null));
            effect.setSpec(WonderSpec.set(effect.getSpec(), "mspeed", null));
        }
    }

    /**
     * Доворот вокруг вертикали.
     * <p>
     * Сдвинутый вбок эффект висит боком к бегущему и читается плохо. При автоматическом
     * довороте плоскость разворачивается ровно настолько, чтобы смотреть на дорожку,
     * то есть встаёт наискось в зависимости от стороны.
     */
    private static void applyTurn(@NonNull WonderEffect effect) {
        double turn = effect.getTurn();
        if (!Double.isNaN(turn) && Math.abs(turn) < 0.01D) {
            effect.setSpec(WonderSpec.set(effect.getSpec(), "roty", null));
            return;
        }
        double degrees = turn;
        if (Double.isNaN(turn)) {
            double forward = Math.max(1.0D, effect.getDistance());
            degrees = -Math.toDegrees(Math.atan2(effect.getSide(), forward));
        }
        effect.setSpec(WonderSpec.set(effect.getSpec(), "roty", trim(degrees)));
    }

    private static String trim(double value) {
        return value == Math.rint(value)
            ? String.valueOf((long) value)
            : String.format(Locale.ROOT, "%.2f", value);
    }

    private static double number(@NonNull String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int time(@NonNull String raw) {
        return TimeUtils.parseTimecode(raw);
    }

    /** Показать точку, где встанет эффект — используется предпросмотром в меню. */
    @NonNull
    public static Location previewLocation(@NonNull Player player, @NonNull WonderEffect effect) {
        return WonderBridge.resolveLocation(player, effect);
    }
}
