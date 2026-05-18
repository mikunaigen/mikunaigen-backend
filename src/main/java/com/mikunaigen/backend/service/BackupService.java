package com.mikunaigen.backend.service;

import com.mikunaigen.backend.dto.BackupItemDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class BackupService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.ROOT);

    private final S3Client s3;

    @Value("${app.backup.b2-bucket}")
    private String bucket;

    @Value("${app.backup.b2-key-id}")
    private String keyId;

    @Value("${app.backup.b2-app-key}")
    private String appKey;

    @Value("${app.backup.b2-endpoint}")
    private String endpoint;

    @Value("${app.backup.encryption-key}")
    private String encryptionKey;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String pgUser;

    @Value("${spring.datasource.password}")
    private String pgPassword;

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${app.github.token:}")
    private String githubToken;

    @Value("${app.github.owner:}")
    private String githubOwner;

    @Value("${app.github.repo:}")
    private String githubRepo;

    public BackupService(S3Client s3) {
        this.s3 = s3;
    }

    public List<BackupItemDto> list(String db) {
        String prefix = prefixFor(db);
        List<BackupItemDto> out = new ArrayList<>();
        String token = null;
        do {
            ListObjectsV2Request req = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(token)
                    .build();
            ListObjectsV2Response res = s3.listObjectsV2(req);
            for (S3Object o : res.contents()) {
                LocalDateTime lm = o.lastModified() != null
                        ? LocalDateTime.ofInstant(o.lastModified(), ZoneId.systemDefault())
                        : null;
                out.add(new BackupItemDto(o.key(), o.size() != null ? o.size() : 0, lm));
            }
            token = res.isTruncated() ? res.nextContinuationToken() : null;
        } while (token != null);
        out.sort(Comparator.comparing(BackupItemDto::lastModified, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    public void generatePairedBackups() {
        String timestamp = TS.format(LocalDateTime.now());
        generateWithTimestamp("postgresql", timestamp);
        generateWithTimestamp("mongodb", timestamp);
    }

    public java.util.Map<String, String> generatePairedBackupKeys() {
        String timestamp = TS.format(LocalDateTime.now());
        String pg = generateWithTimestamp("postgresql", timestamp).key();
        String mg = generateWithTimestamp("mongodb", timestamp).key();
        return java.util.Map.of("postgresKey", pg, "mongoKey", mg);
    }

    public BackupItemDto generate(String db) {
        return generateWithTimestamp(db, TS.format(LocalDateTime.now()));
    }

    public BackupItemDto generateWithTimestamp(String db, String timestamp) {
        if (!isPostgres(db) && !isMongo(db)) {
            throw new IllegalArgumentException("DB inválida.");
        }
        if (githubToken == null || githubToken.isBlank() || githubOwner.isBlank() || githubRepo.isBlank()) {
            throw new IllegalArgumentException("Credenciales de GitHub no configuradas en el servidor.");
        }

        PgConn c = PgConn.fromJdbc(jdbcUrl);
        String extension = isMongo(db) ? ".archive.gz.enc" : ".dump.enc";
        String key = prefixFor(db) + timestamp + extension;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");

        Map<String, Object> b2 = new java.util.HashMap<>();
        b2.put("bucket", bucket);
        b2.put("key_id", keyId);
        b2.put("app_key", appKey);
        b2.put("endpoint", endpoint);

        Map<String, Object> dbPayload = new java.util.HashMap<>();
        Map<String, Object> pg = new java.util.HashMap<>();
        pg.put("host", c.host);
        pg.put("port", String.valueOf(c.port));
        pg.put("name", c.db);
        pg.put("user", pgUser);
        pg.put("pass", pgPassword);
        dbPayload.put("pg", pg);
        dbPayload.put("mongo_uri", mongoUri);

        Map<String, Object> clientPayload = new java.util.HashMap<>();
        clientPayload.put("operation", "generate");
        clientPayload.put("db_type", db);
        clientPayload.put("backup_key", key);
        clientPayload.put("encryption_key", encryptionKey);
        clientPayload.put("b2", b2);
        clientPayload.put("db", dbPayload);

        Map<String, Object> payload = Map.of(
                "event_type", "trigger-generate",
                "client_payload", clientPayload
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        String url = String.format("https://api.github.com/repos/%s/%s/dispatches", githubOwner, githubRepo);
        try {
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error con GitHub Actions");
        }
        return new BackupItemDto(key, 0, LocalDateTime.now());
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key requerida.");
        }
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public void restore(String db, String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key requerida.");
        }
        
        if (githubToken == null || githubToken.isBlank() || githubOwner.isBlank() || githubRepo.isBlank()) {
            throw new IllegalArgumentException("Credenciales de GitHub no configuradas en el servidor.");
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");

        PgConn c = PgConn.fromJdbc(jdbcUrl);
        Map<String, Object> b2 = new java.util.HashMap<>();
        b2.put("bucket", bucket);
        b2.put("key_id", keyId);
        b2.put("app_key", appKey);
        b2.put("endpoint", endpoint);

        Map<String, Object> dbPayload = new java.util.HashMap<>();
        Map<String, Object> pg = new java.util.HashMap<>();
        pg.put("host", c.host);
        pg.put("port", String.valueOf(c.port));
        pg.put("name", c.db);
        pg.put("user", pgUser);
        pg.put("pass", pgPassword);
        dbPayload.put("pg", pg);
        dbPayload.put("mongo_uri", mongoUri);

        Map<String, Object> clientPayload = new java.util.HashMap<>();
        clientPayload.put("operation", "restore");
        clientPayload.put("db_type", db);
        clientPayload.put("backup_key", key);
        clientPayload.put("encryption_key", encryptionKey);
        clientPayload.put("b2", b2);
        clientPayload.put("db", dbPayload);

        Map<String, Object> payload = Map.of(
            "event_type", "trigger-restore",
            "client_payload", clientPayload
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        String url = String.format("https://api.github.com/repos/%s/%s/dispatches", githubOwner, githubRepo);

        try {
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error con GitHub Actions");
        }
    }

    private String prefixFor(String db) {
        if (isPostgres(db)) return "backup_postgresql_";
        if (isMongo(db)) return "backup_mongodb_";
        throw new IllegalArgumentException("DB inválida.");
    }

    private boolean isPostgres(String db) {
        return db != null && (db.equalsIgnoreCase("postgresql") || db.equalsIgnoreCase("postgres"));
    }

    private boolean isMongo(String db) {
        return db != null && db.equalsIgnoreCase("mongodb");
    }

    private static final class PgConn {
        final String host;
        final int port;
        final String db;

        private PgConn(String host, int port, String db) {
            this.host = host;
            this.port = port;
            this.db = db;
        }

        static PgConn fromJdbc(String jdbc) {
            String raw = jdbc != null ? jdbc.trim() : "";
            String s = raw.startsWith("jdbc:") ? raw.substring(5) : raw;
            if (!s.startsWith("postgresql://")) {
                throw new IllegalArgumentException("JDBC inválido.");
            }
            s = s.substring("postgresql://".length());
            String hostPort;
            String dbPart;
            int slash = s.indexOf('/');
            hostPort = slash >= 0 ? s.substring(0, slash) : s;
            dbPart = slash >= 0 ? s.substring(slash + 1) : "";
            String db = dbPart;
            int q = db.indexOf('?');
            if (q >= 0) db = db.substring(0, q);
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
            return new PgConn(host, port, db.isBlank() ? "postgres" : db);
        }
    }

}

