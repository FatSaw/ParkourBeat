package ru.sortix.parkourbeat.packrelay.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class UploadServer {
    public interface UploadListener {
        void onUploaded(UUID playerId, String trackId);
    }

    public interface TextureListener {
        void onTextureUploaded(UUID playerId, String levelId, long sizeBytes, int fileCount);
    }

    private final Logger logger;
    private final WebConfig config;
    private final UploadTokens tokens;
    private final TrackRegistry registry;
    private final Path musicDirectory;
    private final Consumer<String> auditLog;

    private HttpServer server;
    private UploadListener listener;
    private TextureListener textureListener;
    private TextureRegistry textures;
    private java.util.function.Function<String, String> levelNames = id -> id;

    public UploadServer(Logger logger, WebConfig config, UploadTokens tokens,
                        TrackRegistry registry, Path musicDirectory, Consumer<String> auditLog) {
        this.logger = logger;
        this.config = config;
        this.tokens = tokens;
        this.registry = registry;
        this.musicDirectory = musicDirectory;
        this.auditLog = auditLog;
    }

    public void setListener(UploadListener listener) {
        this.listener = listener;
    }

    public void setTextureSupport(TextureRegistry textures,
                                 TextureListener listener,
                                 java.util.function.Function<String, String> levelNames) {
        this.textures = textures;
        this.textureListener = listener;
        if (levelNames != null) this.levelNames = levelNames;
    }

    public String buildTextureUrl(String token) {
        String base = this.config.publicBaseUrl;
        if (!base.endsWith("/")) base = base + "/";
        return base + "textures/" + token;
    }

    public void start() throws Exception {
        InetSocketAddress address = new InetSocketAddress(this.config.bindIp, this.config.port);

        if (this.config.httpsEnabled) {
            HttpsServer https = HttpsServer.create(address, 0);
            https.setHttpsConfigurator(new HttpsConfigurator(this.buildSslContext()));
            this.server = https;
        } else {
            this.server = HttpServer.create(address, 0);
        }

        this.server.createContext("/", new Router());
        this.server.setExecutor(Executors.newFixedThreadPool(4));
        this.server.start();
        this.logger.info("Upload web server listening on {}:{} ({}), public base {}",
            this.config.bindIp, this.config.port,
            this.config.httpsEnabled ? "https" : "http", this.config.publicBaseUrl);
    }

    private javax.net.ssl.SSLContext buildSslContext() throws Exception {
        char[] password = this.config.httpsPassword == null
            ? new char[0] : this.config.httpsPassword.toCharArray();

        java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
        try (java.io.InputStream in = Files.newInputStream(Path.of(this.config.httpsKeystore))) {
            keyStore.load(in, password);
        }

        javax.net.ssl.KeyManagerFactory keyManagers =
            javax.net.ssl.KeyManagerFactory.getInstance(
                javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, password);

        javax.net.ssl.SSLContext context = javax.net.ssl.SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        return context;
    }

    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }
    }

    public String buildUploadUrl(String token) {
        String base = this.config.publicBaseUrl;
        if (!base.endsWith("/")) base = base + "/";
        return base + "upload/" + token;
    }

    private final class Router implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.startsWith("/textures/")) {
                    String token = path.substring("/textures/".length());
                    if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                        UploadServer.this.handleTextureForm(exchange, token);
                    } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                        UploadServer.this.handleTextureUpload(exchange, token);
                    } else {
                        respond(exchange, 405, Pages.message("Метод не поддерживается", null));
                    }
                    return;
                }
                if (path.startsWith("/upload/")) {
                    String token = path.substring("/upload/".length());
                    if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                        UploadServer.this.handleForm(exchange, token);
                    } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                        UploadServer.this.handleUpload(exchange, token);
                    } else {
                        respond(exchange, 405, Pages.message("Метод не поддерживается", null));
                    }
                    return;
                }
                respond(exchange, 404, Pages.message("Страница не найдена",
                    "Ссылку на загрузку выдаёт игра, кнопкой в меню музыки."));
            } catch (Throwable t) {
                UploadServer.this.logger.warn("Upload request failed", t);
                try {
                    respond(exchange, 500, Pages.message("Внутренняя ошибка", null));
                } catch (IOException ignored) {
                }
            } finally {
                exchange.close();
            }
        }
    }

    private void handleForm(HttpExchange exchange, String token) throws IOException {
        UploadTokens.Session session = this.tokens.get(token);
        if (session == null) {
            respond(exchange, 410, Pages.message("Ссылка недействительна",
                "Она живёт недолго. Нажмите кнопку в меню музыки ещё раз."));
            return;
        }

        int used = this.registry.countOwnedBy(session.playerId);
        if (used >= this.config.maxTracksPerPlayer) {
            respond(exchange, 403, Pages.message("Достигнут лимит треков",
                "У вас уже " + used + " из " + this.config.maxTracksPerPlayer
                    + ". Удалите лишний трек в меню музыки."));
            return;
        }

        respond(exchange, 200, Pages.form(token, session.playerName, used,
            this.config.maxTracksPerPlayer, this.config.maxTrackSizeBytes));
    }

    private void handleUpload(HttpExchange exchange, String token) throws IOException {
        UploadTokens.Session session = this.tokens.get(token);
        if (session == null) {
            respond(exchange, 410, Pages.message("Ссылка недействительна", null));
            return;
        }

        int used = this.registry.countOwnedBy(session.playerId);
        if (used >= this.config.maxTracksPerPlayer) {
            respond(exchange, 403, Pages.message("Достигнут лимит треков", null));
            return;
        }

        MultipartReader multipart;
        try {
            multipart = MultipartReader.read(exchange.getRequestBody(),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                this.config.maxTrackSizeBytes + 65536L);
        } catch (IOException e) {
            respond(exchange, 413, Pages.message("Файл слишком большой",
                "Лимит " + (this.config.maxTrackSizeBytes / 1048576L) + " МБ."));
            return;
        }

        MultipartReader.Part file = multipart.file();
        String author = clean(multipart.field("author"), 48);
        String title = clean(multipart.field("title"), 48);

        if (file == null || file.data == null || file.data.length == 0) {
            respond(exchange, 400, Pages.message("Файл не выбран", null));
            return;
        }
        if (file.data.length > this.config.maxTrackSizeBytes) {
            respond(exchange, 413, Pages.message("Файл слишком большой",
                "Лимит " + (this.config.maxTrackSizeBytes / 1048576L) + " МБ."));
            return;
        }
        OggValidator.Result check = OggValidator.check(file.data, file.fileName);
        if (!check.isOk()) {
            this.respondInvalid(exchange, check);
            return;
        }
        if (author == null || author.isEmpty() || title == null || title.isEmpty()) {
            respond(exchange, 400, Pages.message("Заполните автора и название", null));
            return;
        }

        String trackId = buildTrackId(author, title);
        Path folder = this.musicDirectory.resolve(trackId);
        if (Files.exists(folder)) {
            respond(exchange, 409, Pages.message("Трек с таким названием уже есть",
                "Измените название или автора."));
            return;
        }

        byte[] clean = OggValidator.stripMetadata(file.data);

        Files.createDirectories(folder);
        Files.write(folder.resolve("track.ogg"), clean);

        TrackRegistry.Entry entry = new TrackRegistry.Entry();
        entry.trackId = trackId;
        entry.ownerUuid = session.playerId.toString();
        entry.ownerName = session.playerName;
        entry.author = author;
        entry.title = title;
        entry.sizeBytes = file.data.length;
        entry.createdAt = System.currentTimeMillis();
        this.registry.put(entry);

        this.auditLog.accept("Track uploaded: " + trackId + " by " + session.playerName
            + " (" + clean.length / 1024 + " KB, " + OggValidator.describe(check) + ")");

        UploadListener current = this.listener;
        if (current != null) current.onUploaded(session.playerId, trackId);

        respond(exchange, 200, Pages.message("Трек загружен",
            "Вернитесь в игру, он появится в меню музыки."));
    }

    private void respondInvalid(HttpExchange exchange, OggValidator.Result check) throws IOException {
        switch (check.status) {
            case NBS_EXTENSION:
                respond(exchange, 415, Pages.messageWithSteps("Это файл .nbs, а нужен .ogg", null, new String[]{
                    "1) Найдите оригинал песни в одном из стандартных форматов: mp3, wav и подобных,"
                        + " после чего конвертируйте в ogg при помощи любого онлайн-конвертера."
                        + " Например: https://convertio.co",
                    "2) Если оригинал найти не удалось, то используйте данное приложение"
                        + " для конвертации из nbs в ogg: https://github.com/IoeCmcomc/NBSTool"
                }));
                return;
            case NOT_OGG_EXTENSION:
                respond(exchange, 415, Pages.messageWithSteps("Нужен файл .ogg", null, new String[]{
                    "Конвертируйте ваш файл в ogg при помощи любого онлайн-конвертера."
                        + " Например: https://convertio.co"
                }));
                return;
            case BAD_MAGIC:
                respond(exchange, 415, Pages.messageWithSteps(
                    "Расширение .ogg, но содержимое другое", check.detail, new String[]{
                        "Скорее всего файл просто переименовали. Сконвертируйте оригинал в ogg:"
                            + " https://convertio.co"
                    }));
                return;
            case NOT_VORBIS:
                respond(exchange, 415, Pages.messageWithSteps(
                    "Внутри не Vorbis", check.detail, new String[]{
                        "Игра умеет только Ogg Vorbis. Пересохраните трек именно в этом формате:"
                            + " https://convertio.co"
                    }));
                return;
            default:
                respond(exchange, 415, Pages.message("Файл повреждён", check.detail));
        }
    }

    private static final long MAX_TEXTURE_BYTES = 100L * 1024L * 1024L;

    private void handleTextureForm(HttpExchange exchange, String token) throws IOException {
        UploadTokens.Session session = this.tokens.get(token);
        if (session == null || !"texture".equals(session.kind) || this.textures == null) {
            respond(exchange, 410, Pages.message("Ссылка недействительна",
                "Нажмите кнопку в меню редактора ещё раз."));
            return;
        }

        TextureRegistry.Entry existing = this.textures.get(session.levelId);
        String range = existing == null || existing.versionRange == null
            ? "не выбран" : existing.versionRange;
        respond(exchange, 200, Pages.textureForm(token, session.playerName,
            this.levelNames.apply(session.levelId), range, MAX_TEXTURE_BYTES));
    }

    private void handleTextureUpload(HttpExchange exchange, String token) throws IOException {
        UploadTokens.Session session = this.tokens.get(token);
        if (session == null || !"texture".equals(session.kind) || this.textures == null) {
            respond(exchange, 410, Pages.message("Ссылка недействительна", null));
            return;
        }

        MultipartReader multipart;
        try {
            multipart = MultipartReader.read(exchange.getRequestBody(),
                exchange.getRequestHeaders().getFirst("Content-Type"), MAX_TEXTURE_BYTES + 65536L);
        } catch (IOException e) {
            respond(exchange, 413, Pages.message("Архив слишком большой", "Лимит 100 МБ."));
            return;
        }

        MultipartReader.Part file = multipart.file();
        if (file == null || file.data == null || file.data.length == 0) {
            respond(exchange, 400, Pages.message("Файл не выбран", null));
            return;
        }
        if (file.data.length > MAX_TEXTURE_BYTES) {
            respond(exchange, 413, Pages.message("Архив слишком большой", "Лимит 100 МБ."));
            return;
        }

        TexturePackValidator.Result check = TexturePackValidator.check(file.data, file.fileName);
        if (!check.isOk()) {
            respond(exchange, 415, Pages.message(describeTextureError(check.status), check.detail));
            return;
        }

        // Чиним pack.mcmeta до записи: паки от свежих версий обязаны нести min_format
        // и max_format, иначе клиент отвергает метаданные и уходит в fallback,
        // а вместе с паком отваливается и музыка уровня.
        byte[] payload = file.data;
        PackMetaFixer.Result meta = PackMetaFixer.fix(payload, null);
        if (meta.changed) {
            payload = meta.data;
            this.auditLog.accept("Texture pack meta fixed for level " + session.levelId
                + ": " + meta.note);
        }

        Files.write(this.textures.zipOf(session.levelId), payload);

        TextureRegistry.Entry previous = this.textures.get(session.levelId);
        TextureRegistry.Entry entry = new TextureRegistry.Entry();
        entry.levelId = session.levelId;
        entry.ownerUuid = session.playerId.toString();
        entry.ownerName = session.playerName;
        entry.versionRange = previous == null ? null : previous.versionRange;
        entry.sizeBytes = payload.length;
        entry.fileCount = check.fileCount;
        entry.createdAt = System.currentTimeMillis();
        this.textures.put(entry);

        this.auditLog.accept("Texture pack uploaded for level " + session.levelId
            + " by " + session.playerName + " (" + file.data.length / 1024 + " KB, "
            + check.fileCount + " files, unpacked " + check.unpackedSize / 1024 + " KB)");

        TextureListener current = this.textureListener;
        if (current != null) {
            current.onTextureUploaded(session.playerId, session.levelId,
                file.data.length, check.fileCount);
        }

        respond(exchange, 200, Pages.message("Текстуры загружены",
            "Вернитесь в игру, уровень пересоберётся автоматически."));
    }

    private static String describeTextureError(TexturePackValidator.Status status) {
        switch (status) {
            case NOT_ZIP:
                return "Нужен архив .zip";
            case NO_MCMETA:
                return "В архиве нет pack.mcmeta";
            case UNSAFE_PATH:
                return "В архиве недопустимый путь";
            case TOO_MANY_FILES:
                return "Слишком много файлов в архиве";
            case TOO_BIG_UNPACKED:
                return "Архив слишком большой в распакованном виде";
            default:
                return "Архив повреждён";
        }
    }

    private static String clean(String value, int maxLength) {
        if (value == null) return null;
        String result = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (result.length() > maxLength) result = result.substring(0, maxLength);
        return result;
    }

    private static String buildTrackId(String author, String title) {
        String base = (author + " - " + title).replaceAll("[^\\p{L}\\p{N} \\-_]", "").trim();
        if (base.isEmpty()) base = "track";
        if (base.length() > 60) base = base.substring(0, 60).trim();
        return base;
    }

    private static void respond(HttpExchange exchange, int code, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
