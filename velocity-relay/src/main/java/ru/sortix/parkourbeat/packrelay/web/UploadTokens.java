package ru.sortix.parkourbeat.packrelay.web;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UploadTokens {
    public static final class Session {
        public final String token;
        public final UUID playerId;
        public final String playerName;
        public final long expiresAt;
        public final String kind;
        public final String levelId;

        Session(String token, UUID playerId, String playerName, long expiresAt,
                String kind, String levelId) {
            this.kind = kind;
            this.levelId = levelId;
            this.token = token;
            this.playerId = playerId;
            this.playerName = playerName;
            this.expiresAt = expiresAt;
        }

        public boolean isValid() {
            return System.currentTimeMillis() < this.expiresAt;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final long lifetimeMillis;

    public UploadTokens(long lifetimeMillis) {
        this.lifetimeMillis = lifetimeMillis;
    }

    public Session issue(UUID playerId, String playerName) {
        return this.issue(playerId, playerName, "track", null);
    }

    public Session issue(UUID playerId, String playerName, String kind, String levelId) {
        this.purge();
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session(token, playerId, playerName,
            System.currentTimeMillis() + this.lifetimeMillis, kind, levelId);
        this.sessions.put(token, session);
        return session;
    }

    public Session get(String token) {
        if (token == null) return null;
        Session session = this.sessions.get(token);
        if (session == null) return null;
        if (!session.isValid()) {
            this.sessions.remove(token);
            return null;
        }
        return session;
    }

    private void purge() {
        long now = System.currentTimeMillis();
        this.sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }
}
