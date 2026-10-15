package ru.sortix.parkourbeat.ui.lyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLEncoder;
import lombok.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор субтитров для шоу. Форматы: SRT, LRC (в т.ч. пословный), VTT и ASS/SSA - формат
 * определяется по содержимому. Плюс ссылка на трек Spotify: по ней берётся синхронный текст
 * из открытой базы LRCLIB (lrclib.net).
 * <p>
 * Время в миллисекундах от начала трека. Если у строки нет конца - она живёт до начала
 * следующей (но не дольше 4 секунд).
 */
public final class LyricsParser {
    private LyricsParser() {
    }

    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_CUES = 4000;
    private static final long DEFAULT_LENGTH = 4000L;

    /**
     * Получить текст субтитров по ссылке игрока: прямая ссылка на файл (страницы файлообменников
     * поправляются сами) или ссылка на трек Spotify.
     */
    @NonNull
    public static String fetch(@NonNull String link) throws IOException {
        String url = link.trim();
        if (url.contains("open.spotify.com/") && url.contains("/track/") || url.startsWith("spotify:track:")) {
            return spotify(url);
        }
        String target = direct(url);
        // tmpfiles.org: ссылка ведёт на страницу, на ней кнопка
        // <a class="download" href="https://tmpfiles.org/dl/...">Download</a> - её и скачиваем.
        if (target.matches("^(https?://)?(www\\.)?tmpfiles\\.org/.*") && !target.contains("tmpfiles.org/dl/")) {
            String page = download(target.startsWith("http") ? target : "https://" + target);
            Matcher a = Pattern.compile("<a[^>]*class=\"download\"[^>]*href=\"([^\"]+)\"").matcher(page);
            if (!a.find()) a = Pattern.compile("href=\"(https?://tmpfiles\\.org/dl/[^\"]+)\"").matcher(page);
            if (a.find(0)) target = a.group(1).replace("&amp;", "&");
            else throw new IOException("на странице tmpfiles.org не найдена кнопка Download - файл мог истечь");
        }
        String text = download(target);
        String head = text.trim().toLowerCase(Locale.ROOT);
        if (head.startsWith("<!doctype") || head.startsWith("<html") || head.contains("<head>")) {
            throw new IOException("по ссылке веб-страница, а не файл. Нужна прямая ссылка (у tmpfiles.org - с /dl/)");
        }
        return text;
    }

    /** Ссылки на страницы популярных файлообменников -> прямые ссылки на файл. */
    @NonNull
    public static String direct(@NonNull String url) {
        Matcher paste = Pattern.compile("^(https?://)?(www\\.)?pastebin\\.com/(?!raw/)([A-Za-z0-9]+)$").matcher(url);
        if (paste.find()) return "https://pastebin.com/raw/" + paste.group(3);
        Matcher gh = Pattern.compile("^(https?://)?github\\.com/([^/]+)/([^/]+)/blob/(.+)$").matcher(url);
        if (gh.find()) return "https://raw.githubusercontent.com/" + gh.group(2) + "/" + gh.group(3) + "/" + gh.group(4);
        return url;
    }

    // ---------------------------------------------------------------- Spotify -> LRCLIB
    /**
     * Название, исполнитель и длительность - из публичных метаданных страницы трека Spotify,
     * синхронный текст - из LRCLIB (открытая база, построчный тайминг).
     */
    @NonNull
    private static String spotify(@NonNull String url) throws IOException {
        String id = url.startsWith("spotify:track:") ? url.substring(14)
            : url.replaceAll("^.*/track/([A-Za-z0-9]+).*$", "$1");
        String page = download("https://open.spotify.com/track/" + id);
        String title = meta(page, "og:title");
        String description = meta(page, "og:description");
        String artist = description == null ? null : description.split("\\s*\u00B7\\s*")[0];
        String duration = meta(page, "music:duration");
        if (title == null || artist == null) throw new IOException("не удалось прочитать трек Spotify");

        // Кандидаты: точное совпадение + поиск. В базе бывают мусорные записи (одна строка
        // «probe»), поэтому берём лучший: пословная синхронизация > совпадение длительности
        // (±3 с) > больше строк. Записи короче 5 строк не берём.
        List<JsonElement> candidates = new ArrayList<>();
        String q = "track_name=" + enc(title) + "&artist_name=" + enc(artist) + (duration != null ? "&duration=" + duration : "");
        try {
            candidates.add(new JsonParser().parse(download("https://lrclib.net/api/get?" + q)));
        } catch (IOException ignored) {
        }
        try {
            JsonElement found = new JsonParser().parse(download("https://lrclib.net/api/search?q=" + enc(artist + " " + title)));
            if (found.isJsonArray()) for (JsonElement e : found.getAsJsonArray()) candidates.add(e);
        } catch (IOException ignored) {
        }
        double wanted = duration == null ? -1 : Double.parseDouble(duration);
        String best = null;
        double bestScore = -1;
        for (JsonElement e : candidates) {
            String synced = synced(e);
            if (synced == null) continue;
            long lines = synced.lines().filter(l -> l.trim().startsWith("[")).count();
            if (lines < 5) continue;
            JsonObject o = e.getAsJsonObject();
            double score = lines;
            if (o.has("hasWordSync") && !o.get("hasWordSync").isJsonNull() && o.get("hasWordSync").getAsBoolean()) score += 10000;
            if (wanted > 0 && o.has("duration") && !o.get("duration").isJsonNull()
                && Math.abs(o.get("duration").getAsDouble() - wanted) <= 3.0) score += 1000;
            if (score > bestScore) {
                bestScore = score;
                best = synced;
            }
        }
        if (best != null) return best;
        throw new IOException("для «" + artist + " - " + title + "» нет синхронного текста в LRCLIB");
    }

    private static String synced(JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonElement s = e.getAsJsonObject().get("syncedLyrics");
        return s == null || s.isJsonNull() || s.getAsString().isBlank() ? null : s.getAsString();
    }

    private static String meta(String html, String name) {
        Matcher m = Pattern.compile("<meta (?:property|name)=\"" + Pattern.quote(name) + "\" content=\"([^\"]*)\"").matcher(html);
        if (!m.find()) return null;
        return m.group(1).replace("&amp;", "&").replace("&#x27;", "'").replace("&#39;", "'").replace("&quot;", "\"");
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    /** Скачать файл по прямой ссылке (не больше 512 КБ). */
    @NonNull
    public static String download(@NonNull String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestProperty("User-Agent", "ParkourBeat (https://parkourbeat.com) lyrics");
        try (InputStream in = con.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new IOException("file is larger than 512 KB");
            }
            String text = out.toString(StandardCharsets.UTF_8.name());
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        }
    }

    @NonNull
    public static List<LyricCue> parse(@NonNull String content) {
        String text = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<LyricCue> cues;
        if (text.startsWith("WEBVTT")) cues = parseSrtLike(text);
        else if (text.contains("[Events]") || text.contains("Dialogue:")) cues = parseAss(text);
        else if (Pattern.compile("(?m)^\\d+\\s*\\n\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3}\\s*-->").matcher(text).find()) cues = parseSrtLike(text);
        else if (Pattern.compile("(?m)^\\s*\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?\\]").matcher(text).find()) cues = parseLrc(text);
        else cues = new ArrayList<>();
        return finish(cues);
    }

    // ---------------------------------------------------------------- SRT / VTT
    private static final Pattern ARROW = Pattern.compile(
        "(\\d{1,2}:)?(\\d{1,2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}:)?(\\d{1,2}):(\\d{2})[,.](\\d{1,3})");

    private static List<LyricCue> parseSrtLike(String text) {
        List<LyricCue> out = new ArrayList<>();
        for (String block : text.split("\\n\\s*\\n")) {
            String[] lines = block.split("\n");
            for (int i = 0; i < lines.length; i++) {
                Matcher m = ARROW.matcher(lines[i]);
                if (!m.find()) continue;
                long start = hms(m.group(1), m.group(2), m.group(3), m.group(4));
                long end = hms(m.group(5), m.group(6), m.group(7), m.group(8));
                StringBuilder body = new StringBuilder();
                for (int j = i + 1; j < lines.length; j++) {
                    if (body.length() > 0) body.append(' ');
                    body.append(lines[j].replaceAll("<[^>]*>", "").trim());
                }
                if (body.length() > 0) out.add(new LyricCue(start, end, body.toString()));
                break;
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- LRC (+ пословный <мм:сс.хх>)
    private static final Pattern LRC_TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern LRC_WORD = Pattern.compile("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>([^<]*)");

    private static List<LyricCue> parseLrc(String text) {
        List<LyricCue> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            Matcher m = LRC_TAG.matcher(line);
            List<Long> starts = new ArrayList<>();
            int last = 0;
            while (m.find() && m.start() == last) {
                starts.add(mmss(m.group(1), m.group(2), m.group(3)));
                last = m.end();
            }
            if (starts.isEmpty()) continue;
            String body = line.substring(last).trim();
            Matcher w = LRC_WORD.matcher(body);
            boolean words = false;
            while (w.find()) {
                words = true;
                String word = w.group(4).trim();
                if (!word.isEmpty()) out.add(new LyricCue(mmss(w.group(1), w.group(2), w.group(3)), -1L, word));
            }
            if (!words && !body.isEmpty()) for (long s : starts) out.add(new LyricCue(s, -1L, body));
        }
        return out;
    }

    // ---------------------------------------------------------------- ASS / SSA
    private static List<LyricCue> parseAss(String text) {
        List<LyricCue> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.startsWith("Dialogue:")) continue;
            String[] parts = line.substring(9).split(",", 10);
            if (parts.length < 10) continue;
            String body = parts[9].replaceAll("\\{[^}]*}", "").replace("\\N", " ").replace("\\n", " ").trim();
            if (!body.isEmpty()) out.add(new LyricCue(clock(parts[1].trim()), clock(parts[2].trim()), body));
        }
        return out;
    }

    // ---------------------------------------------------------------- время
    /** «ч:мм:сс.ммм», «мм:сс.ммм», «сс.ммм» -> миллисекунды. */
    public static long clock(@NonNull String value) {
        String v = value.trim().replace(',', '.');
        String[] parts = v.split(":");
        double seconds = Double.parseDouble(parts[parts.length - 1]);
        long minutes = parts.length >= 2 ? Long.parseLong(parts[parts.length - 2]) : 0L;
        long hours = parts.length >= 3 ? Long.parseLong(parts[parts.length - 3]) : 0L;
        return Math.round(((hours * 60 + minutes) * 60 + seconds) * 1000.0D);
    }

    private static long hms(String h, String m, String s, String ms) {
        long hours = h == null ? 0 : Long.parseLong(h.substring(0, h.length() - 1));
        return ((hours * 60 + Long.parseLong(m)) * 60 + Long.parseLong(s)) * 1000L + fraction(ms);
    }

    private static long mmss(String m, String s, String frac) {
        return (Long.parseLong(m) * 60 + Long.parseLong(s)) * 1000L + (frac == null ? 0 : fraction(frac));
    }

    private static long fraction(String f) {
        if (f.length() == 1) return Long.parseLong(f) * 100;
        if (f.length() == 2) return Long.parseLong(f) * 10;
        return Long.parseLong(f.substring(0, 3));
    }

    private static List<LyricCue> finish(List<LyricCue> cues) {
        cues.removeIf(c -> c.text.isBlank() || c.start < 0);
        cues.sort((a, b) -> Long.compare(a.start, b.start));
        if (cues.size() > MAX_CUES) cues = new ArrayList<>(cues.subList(0, MAX_CUES));
        List<LyricCue> out = new ArrayList<>(cues.size());
        for (int i = 0; i < cues.size(); i++) {
            LyricCue c = cues.get(i);
            long end = c.end;
            if (end <= c.start) {
                long next = i + 1 < cues.size() ? cues.get(i + 1).start : c.start + DEFAULT_LENGTH;
                end = Math.min(next, c.start + DEFAULT_LENGTH);
                if (end <= c.start) end = c.start + 300;
            }
            String text = c.text.length() > 64 ? c.text.substring(0, 64) : c.text;
            out.add(new LyricCue(c.start, end, text.replace('|', '/')));
        }
        return out;
    }

    @NonNull
    public static String format(long millis) {
        return String.format(Locale.ROOT, "%02d:%02d.%03d", millis / 60000, (millis / 1000) % 60, millis % 1000);
    }
}
