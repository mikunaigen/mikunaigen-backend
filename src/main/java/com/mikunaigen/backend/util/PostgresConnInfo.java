package com.mikunaigen.backend.util;

public final class PostgresConnInfo {

    public final String host;
    public final int port;
    public final String db;

    private PostgresConnInfo(String host, int port, String db) {
        this.host = host;
        this.port = port;
        this.db = db;
    }

    public static PostgresConnInfo fromJdbc(String jdbc) {
        String raw = jdbc != null ? jdbc.trim() : "";
        String s = raw.startsWith("jdbc:") ? raw.substring(5) : raw;
        if (!s.startsWith("postgresql://")) {
            throw new IllegalArgumentException("JDBC inválido.");
        }
        s = s.substring("postgresql://".length());
        int slash = s.indexOf('/');
        String hostPort = slash >= 0 ? s.substring(0, slash) : s;
        String dbPart = slash >= 0 ? s.substring(slash + 1) : "";
        String db = dbPart;
        int q = db.indexOf('?');
        if (q >= 0) {
            db = db.substring(0, q);
        }
        String host = hostPort;
        int port = 5432;
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0) {
            host = hostPort.substring(0, colon);
            try {
                port = Integer.parseInt(hostPort.substring(colon + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return new PostgresConnInfo(host, port, db.isBlank() ? "postgres" : db);
    }
}
