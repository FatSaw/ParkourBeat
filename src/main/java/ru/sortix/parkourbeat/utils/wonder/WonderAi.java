package ru.sortix.parkourbeat.utils.wonder;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.sortix.parkourbeat.levels.wonder.WonderCategory;
import ru.sortix.parkourbeat.levels.wonder.WonderEffect;
import ru.sortix.parkourbeat.levels.wonder.WonderPreset;
import ru.sortix.parkourbeat.levels.wonder.WonderPresets;
import ru.sortix.parkourbeat.utils.TimeUtils;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Встроенный помощник: строитель пишет словами, что должно происходить в песне,
 * и получает готовый список команд /pbllmeffects — с таймкодами, правками и удалениями.
 * <p>
 * Модель ничего не выполняет сама: она только предлагает план, а строитель нажимает
 * «Применить». Так ни один ответ ИИ не может молча переписать уровень.
 */
public final class WonderAi implements ru.sortix.parkourbeat.lifecycle.PluginManager {

    private static final String API_URL = "https://fptools.onrender.com/api/ai";
    private static final String API_KEY = "fptoolsdim";

    private final @NonNull Plugin plugin;
    private final Set<UUID> busy = new HashSet<>();

    public WonderAi(@NonNull Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void disable() {
        this.busy.clear();
    }

    public boolean isBusy(@NonNull Player player) {
        return this.busy.contains(player.getUniqueId());
    }

    /** Результат: план из команд плюс короткое пояснение для строителя. */
    @Getter
    public static final class Plan {
        private final @NonNull List<String> commands = new ArrayList<>();
        private final @NonNull List<String> notes = new ArrayList<>();
        private @Nullable String error;

        public boolean isEmpty() {
            return this.commands.isEmpty();
        }
    }

    public interface Callback {
        void done(@NonNull Plan plan);
    }

    /**
     * @param request      что просит строитель, своими словами
     * @param existing     текущие эффекты уровня — чтобы модель могла что-то поправить или удалить
     * @param songName     название трека, помогает модели попадать в настроение
     * @param songLengthMs длина трека, чтобы таймкоды не улетали за конец
     */
    public void ask(@NonNull Player player,
                    @NonNull String request,
                    @NonNull List<WonderEffect> existing,
                    @Nullable String songName,
                    int songLengthMs,
                    @NonNull Callback callback
    ) {
        if (!this.busy.add(player.getUniqueId())) return;

        final String prompt = buildUserPrompt(request, existing, songName, songLengthMs);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            Plan plan = new Plan();
            try {
                plan = parse(post(prompt));
            } catch (Throwable t) {
                plan.error = t.getMessage() == null ? t.toString() : t.getMessage();
            }
            final Plan result = plan;
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                this.busy.remove(player.getUniqueId());
                callback.done(result);
            });
        });
    }

    // ------------------------------------------------------------------ промпт

    @NonNull
    private static String buildUserPrompt(@NonNull String request,
                                          @NonNull List<WonderEffect> existing,
                                          @Nullable String songName,
                                          int songLengthMs
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Track: ").append(songName == null ? "unknown" : songName);
        if (songLengthMs > 0) sb.append(" (length ").append(TimeUtils.formatTimecode(songLengthMs)).append(")");
        sb.append("\n\nCurrent effects on the timeline:\n");
        if (existing.isEmpty()) {
            sb.append("  (none yet)\n");
        } else {
            for (int i = 0; i < existing.size(); i++) {
                WonderEffect e = existing.get(i);
                sb.append("  #").append(i + 1)
                    .append(' ').append(e.getStartTimecode())
                    .append(" - ").append(e.getEndTimecode())
                    .append("  ").append(e.getDisplayName(ru.sortix.parkourbeat.utils.lang.PlayerLang.DEFAULT_LOCALE))
                    .append("  [preset ").append(e.getPresetId().isEmpty() ? "custom" : e.getPresetId())
                    .append(", anchor ").append(e.getAnchor().name().toLowerCase(Locale.ROOT))
                    .append("]\n");
            }
        }
        sb.append("\nBuilder asks: ").append(request.trim());
        sb.append("\n\nReply with NOTE:, then CMD: lines, then END. Nothing else.");
        return sb.toString();
    }

    /**
     * Вся механика вшита прямо сюда: модель на том конце ничего не знает ни про ParkourBeat,
     * ни про LightShow, поэтому язык, ограничения и каталог пресетов приходится давать целиком.
     */
    @NonNull
    private static String systemPrompt() {
        StringBuilder presets = new StringBuilder();
        for (WonderCategory category : WonderCategory.values()) {
            List<WonderPreset> list = WonderPresets.byCategory(category);
            if (list.isEmpty()) continue;
            presets.append("  ").append(category.name().toLowerCase(Locale.ROOT)).append(": ");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) presets.append(", ");
                presets.append(list.get(i).getId());
            }
            presets.append('\n');
        }

        return String.join("\n",
            "You place particle effects on the timeline of a Minecraft parkour level that runs in sync",
            "with a song. The player runs forward while the track plays; effects fire at exact timecodes.",
            "You answer with command lines and nothing else. No markdown, no code fences, no chatter.",
            "",
            "== OUTPUT FORMAT, NOTHING ELSE IS READ ==",
            "NOTE: <one short sentence in Russian describing the plan>",
            "CMD: /pbllmeffects <command>",
            "CMD: /pbllmeffects <command>",
            "END",
            "",
            "== THE ONLY COMMANDS THAT EXIST ==",
            "/pbllmeffects preset <start> <end> <presetId> [key:value ...]   ready-made, USE THIS BY DEFAULT",
            "/pbllmeffects text <start> <end> <WORDS> [key:value ...]        underscores become spaces",
            "/pbllmeffects add <start> <end> <formula> @ <params>            only when no preset fits",
            "/pbllmeffects edit <index> <key:value ...>                      index is the # from the list above",
            "/pbllmeffects del <index>",
            "/pbllmeffects clear",
            "",
            "== COPY THE SHAPE OF THESE LINES EXACTLY ==",
            "CMD: /pbllmeffects preset 00:12 00:18 stars_fall height:12",
            "CMD: /pbllmeffects preset 01:04.250 01:04.800 hit_flash dist:10 side:-4",
            "CMD: /pbllmeffects preset 01:20 01:32 aura anchor:follow",
            "CMD: /pbllmeffects text 01:33 01:36.500 ВПЕРЁД height:6 dist:16",
            "CMD: /pbllmeffects text 02:10 02:14 МЫ_УЖЕ_БЛИЗКО height:7 particle:soul_fire",
            "CMD: /pbllmeffects add 00:30 00:34 x=4*cos(t);y=4*sin(t);z=0 @ steps:150 particle:flame refresh:6",
            "CMD: /pbllmeffects edit 3 dist:18 height:7 scale:1.4",
            "CMD: /pbllmeffects del 2",
            "",
            "== SEVEN RULES. BREAKING ONE KILLS THE WHOLE LINE ==",
            "1. Parameters are separated by ONE SPACE. Never by '*', never by ',', never by ';'.",
            "   '*' means multiplication inside a formula and nothing else, ever.",
            "   WRONG: radius:3*sides:8*motion:flow",
            "   RIGHT: radius:3 sides:8 motion:flow",
            "2. Every parameter is key:value, no spaces around the colon. Never key=value.",
            "3. A preset id may follow ONLY the word 'preset'. It is not a spec and never goes into 'add'.",
            "   WRONG: /pbllmeffects add 00:10 00:14 aura mspeed:0.3 motion:flow",
            "   RIGHT: /pbllmeffects preset 00:10 00:14 aura",
            "4. Only the ids listed below exist. Never invent one and never guess a similar-sounding name.",
            "5. In 'add', everything before ' @ ' is a formula and every piece of it is named:",
            "   x=... ; y=... ; z=... or let name=... , joined by ';'. Parameters go AFTER the ' @ '.",
            "   WRONG: x=3*cos(t);y=3*sin(t);steps:40",
            "   RIGHT: x=3*cos(t);y=3*sin(t) @ steps:40",
            "6. One command per CMD: line. Never wrap a command onto a second line.",
            "7. Never invent a key, a command or a particle name. When unsure, use preset or text.",
            "",
            "== TIMECODES ==",
            "Accepted forms: 93, 93.5, 1:33, 01:33.250, 01:34.565. Always give BOTH start and end.",
            "If the builder says 'from 01:33 to 01:34.565', that is start 01:33 and end 01:34.565 exactly.",
            "If only one moment is given, choose a sensible end: words 2-4 s, hits 0.3-0.8 s, backgrounds 6-12 s.",
            "Never place an effect past the end of the track.",
            "",
            "== PLACEMENT KEYS (work with preset/text/add/edit) ==",
            "anchor:ahead     appears in front of the runner, he runs straight into it (default)",
            "anchor:overhead  hangs above the path, good for the sky",
            "anchor:follow    travels with the player, for auras",
            "anchor:fixed     stays where the builder placed it",
            "dist:14          how far ahead, in blocks      height:3      how high",
            "side:0           shift left(-) / right(+)      scale:1       overall size",
            "color:#00FFAA    only affects dust-based layers",
            "particle:flame   steps:150   refresh:6   font:pixel   px:0.3   words:НОВЫЕ_СЛОВА",
            "",
            "== READY-MADE PRESETS: THIS IS THE WHOLE LIST ==",
            presets.toString().trim(),
            "",
            "== TEXT IS RENDERED LETTER BY LETTER ==",
            "Words go straight after the two timecodes, spaces written as underscores, capitalisation kept.",
            "in:letters      each letter flies in on its own, one after another",
            "in:popletters   each letter grows on its own beat",
            "in:typeletters  letters appear one by one without flying",
            "out:letters     letters scatter one by one",
            "Text always lights up in a single frame, never loading in strips.",
            "",
            "== RAW SPEC, ONLY FOR 'add' ==",
            "Layers separated by |, layer params after @. Three kinds of layer geometry:",
            "  text:WORDS                 rendered text",
            "  pix:0110/1111              pixel blocks, rows separated by /",
            "  x=...;y=...;z=...          math formula",
            "Formula variables: t (curve parameter), u (second parameter), T (seconds since the effect started),",
            "  i (point index), n (points). Own variables: let r=...; Never name one x, y, z, e, t, u, i, n, T, p.",
            "Functions: sin cos tan atan2 sqrt abs sign floor ceil round frac exp ln min max pow hypot mod clamp",
            "  lerp step smooth ease saw tri sq pulse noise if rectx recty cellx celly step4",
            "Layer params (all of them go after the @, separated by spaces):",
            "  steps: mode:(curve|tube|surface|fill) radius: sides: t: u: particle: color: psize:",
            "  refresh: motion:(out|in|up|down|flow|spin|to_player|random) mspeed: vx: vy: vz: trail: tgap:",
            "  jitter: chance: ox: oy: oz: zoom: rotx: roty: rotz: px: font: lgap: align: outline:",
            "  in: out: int: outt: flyd: face: spin: cull: view:",
            "  in: values fly fade type wipe rise drop explode scale spiral",
            "  out: values fly fade scatter fall dissolve wipe implode shrink",
            "",
            "== PERFORMANCE, THIS IS NOT OPTIONAL ==",
            "A particle lives ~60 ticks and cannot be moved or deleted after it is sent.",
            "live particles = points x 60 / refresh. Static layer -> refresh:12..20. Animated/moving -> refresh:3..5.",
            "Keep one effect under ~600 points. A smooth circle needs 120-200 steps, not 800.",
            "end_rod is the default and the base of everything. Use particle:dust only for small coloured accents:",
            "at most ~150 points in a dust layer, refresh:18 or higher, never two dust layers in the same second.",
            "Anything longer than 40 blocks needs cull:40.",
            "",
            "== PARTICLES: DO NOT USE ONLY end_rod ==",
            "A whole level of white sticks is boring. Vary it: flame and soul_fire for warmth, spark and crit",
            "for hits, enchant and witch for magic, portal and dragon for something otherworldly, cloud and",
            "smoke for weight, totem for celebration.",
            "",
            "== drift, approach, key: USE THEM IN 'add' ONLY ==",
            "drift:x,y,z gives the WHOLE figure a velocity in world axes, blocks per tick, and costs nothing:",
            "the client carries the particles itself. Always pair it with burst:true and driftt:<how long>.",
            "  Example: a word that leaps up and to the right: drift:0,5,3 driftt:5s burst:true",
            "approach:<distance> makes the whole figure travel from dist: to that distance over its lifetime.",
            "  Use dist:55 approach:6 for something that comes at the runner from far away.",
            "thick:1..3 fattens every line, and each step costs about four times the particles.",
            "key(T, 0,0, 2,10, 4,3) gives keyframes with soft transitions; bez(x,a,b,c,d) is a cubic bezier;",
            "curve(x,in,out) is an easing. Use them inside ox/oy/oz/zoom/rotz instead of stacking lerp and smooth.",
            "",
            "== THIS IS A PARKOUR LEVEL, NOT A CONCERT SCREEN ==",
            "The player is jumping. Effects must never sit where he lands or where he looks for the next block.",
            "Put anything large at height:5 or higher, or use anchor:overhead. Small accents may go to the sides",
            "with side:-4 or side:4. Never cover the path itself with a dense wall of particles.",
            "Two effects that overlap in time must differ in place: one overhead, one to the side.",
            "",
            "== TIMING, THE MOST COMMON MISTAKE ==",
            "Never stretch one effect across the whole song. If the builder says 'effects for two minutes',",
            "that means MANY effects spread over two minutes, roughly one every 3-8 seconds, not a single",
            "00:00-02:00 monster. Each effect gets its own short window.",
            "Text needs longer than the phrase it illustrates: it must still be readable after the line is sung.",
            "Give a word at least 2 seconds, and let it end 1-2 seconds after the vocal phrase ends.",
            "If the builder gives one window for a batch of effects, that window is when they START,",
            "the end of each effect you choose yourself by its kind.",
            "",
            "== WHEN THE BUILDER ASKS FOR NO ANIMATION ==",
            "'резко', 'без анимаций', 'сразу' means in:none and out:none, and a short refresh so the shape",
            "appears in one or two ticks. Write it explicitly: in:none out:none.",
            "Do not add fly/scale/spiral to anything when this was asked.",
            "",
            "== HOW TO THINK ==",
            "A level is a show, not a pile of effects. Give the run a shape: quiet background during verses,",
            "words on the vocal lines, hits on the beat, one big moment on the drop.",
            "Do not stack more than two or three effects on the same second.",
            "Reuse the builder's own words verbatim in text effects, keeping their capitalisation.",
            "If the builder asks to change or remove something, use edit/del with the indexes from the list.",
            "",
            "== ANSWER LIKE THIS AND ONLY LIKE THIS ==",
            "NOTE: Ставлю звездопад на припев и надпись на строчке.",
            "CMD: /pbllmeffects preset 01:12 01:20 stars_fall height:12",
            "CMD: /pbllmeffects text 01:33 01:36.500 ВПЕРЁД height:6",
            "END");
    }

    // ------------------------------------------------------------------ сеть

    @NonNull
    private static String post(@NonNull String userPrompt) throws Exception {
        String payload = "{\"messages\":[{\"role\":\"system\",\"content\":" + json(systemPrompt())
            + "},{\"role\":\"user\",\"content\":" + json(userPrompt)
            + "}],\"modelName\":\"ChatGPT 4o\",\"currentPagePath\":\"/chatgpt-4o\"}";

        HttpURLConnection con = (HttpURLConnection) new URL(API_URL).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Authorization", "Bearer " + API_KEY);
        con.setRequestProperty("User-Agent", "ParkourBeat-Wonder/1.0");
        con.setConnectTimeout(10_000);
        con.setReadTimeout(45_000);
        con.setDoOutput(true);
        try (OutputStream os = con.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = con.getResponseCode();
        String body = read(code >= 400 ? con.getErrorStream() : con.getInputStream());
        if (code >= 500) throw new Exception("сервер ИИ временно недоступен (HTTP " + code + ")");
        if (code >= 400) throw new Exception("HTTP " + code);
        String response = field(body, "response");
        if (response == null) throw new Exception("пустой ответ от сервера");
        return response;
    }

    @NonNull
    private static String read(@Nullable InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) bo.write(buffer, 0, read);
        in.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    @NonNull
    private static String json(@NonNull String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    @Nullable
    private static String field(@NonNull String json, @NonNull String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) return null;
        int i = json.indexOf(':', k + needle.length());
        if (i < 0) return null;
        while (i + 1 < json.length() && Character.isWhitespace(json.charAt(i + 1))) i++;
        if (i + 1 >= json.length() || json.charAt(i + 1) != '"') return null;
        StringBuilder sb = new StringBuilder();
        for (int j = i + 2; j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '\\' && j + 1 < json.length()) {
                char n = json.charAt(++j);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 'r': break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (j + 4 < json.length()) {
                            try {
                                sb.append((char) Integer.parseInt(json.substring(j + 1, j + 5), 16));
                            } catch (NumberFormatException ignored) {
                            }
                            j += 4;
                        }
                        break;
                    default: sb.append(n);
                }
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @NonNull
    private static Plan parse(@NonNull String body) {
        Plan plan = new Plan();
        int dropped = 0;
        for (String raw : body.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("```")) continue;
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("NOTE:")) {
                plan.notes.add(line.substring(5).trim());
            } else if (upper.startsWith("CMD:")) {
                String command = line.substring(4).trim();
                if (command.startsWith("`")) command = command.replace("`", "").trim();
                if (!command.startsWith("/")) command = "/" + command;
                if (!command.toLowerCase(Locale.ROOT).startsWith("/pbllmeffects")) continue;

                String fixed = WonderFix.repair(command);
                if (fixed == null) {
                    dropped++;
                    continue;
                }
                plan.commands.add(fixed);
            } else if (upper.startsWith("END")) {
                break;
            }
        }
        if (dropped > 0) {
            String tail = "Часть команд пришла с ошибкой, выкинуто: " + dropped + ".";
            if (plan.notes.isEmpty()) plan.notes.add(tail);
            else plan.notes.set(0, plan.notes.get(0) + " " + tail);
        }
        if (plan.commands.isEmpty() && plan.error == null) {
            plan.error = dropped > 0
                ? "модель написала команды с ошибками, попробуйте ещё раз"
                : "модель ответила не по формату";
        }
        return plan;
    }
}
