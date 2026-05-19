package com.mikunaigen.backend.service;

import com.mikunaigen.backend.dto.BackupItemDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BackupService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.ROOT);
    private static final String DB_TIPO = "postgresql";

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
        generate(DB_TIPO);
    }

    public Map<String, String> generatePairedBackupKeys() {
        String key = generateWithTimestamp(DB_TIPO, TS.format(LocalDateTime.now())).key();
        return Map.of("postgresKey", key);
    }

    public BackupItemDto generate(String db) {
        return generateWithTimestamp(normalizarDb(db), TS.format(LocalDateTime.now()));
    }

    public BackupItemDto generateWithTimestamp(String db, String timestamp) {
        String tipo = normalizarDb(db);
        if (githubToken == null || githubToken.isBlank() || githubOwner.isBlank() || githubRepo.isBlank()) {
            throw new IllegalArgumentException("Credenciales de GitHub no configuradas en el servidor.");
        }

        PgConn c = PgConn.fromJdbc(jdbcUrl);
        String key = prefixFor(tipo) + timestamp + ".dump.enc";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");

        Map<String, Object> clientPayload = construirPayloadGithub("generate", tipo, key, c);

        Map<String, Object> payload = new HashMap<>();
        payload.put("event_type", "trigger-generate");
        payload.put("client_payload", clientPayload);

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
        String tipo = normalizarDb(db);

        Map<String, Object> clientPayload = construirPayloadGithub("restore", tipo, key, c);

        Map<String, Object> payload = new HashMap<>();
        payload.put("event_type", "trigger-restore");
        payload.put("client_payload", clientPayload);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        String url = String.format("https://api.github.com/repos/%s/%s/dispatches", githubOwner, githubRepo);
        try {
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error con GitHub Actions");
        }
    }

    private String prefixFor(String db) {
        return "backup_postgresql_";
    }

    private Map<String, Object> construirPayloadGithub(String operacion, String tipoDb, String claveRespaldo, PgConn c) {
        Map<String, Object> pg = new HashMap<>();
        pg.put("host", c.host);
        pg.put("port", String.valueOf(c.port));
        pg.put("name", c.db);
        pg.put("user", pgUser);
        pg.put("pass", pgPassword);

        Map<String, Object> b2 = new HashMap<>();
        b2.put("bucket", bucket);
        b2.put("key_id", keyId);
        b2.put("app_key", appKey);
        b2.put("endpoint", endpoint);

        Map<String, Object> payload = new HashMap<>();
        payload.put("operacion", operacion);
        payload.put("tipo_db", tipoDb);
        payload.put("clave_respaldo", claveRespaldo);
        payload.put("clave_cifrado", encryptionKey);
        payload.put("pg", pg);
        payload.put("b2", b2);
        return payload;
    }

    private String normalizarDb(String db) {
        if (db == null || db.isBlank()) {
            return DB_TIPO;
        }
        if (db.equalsIgnoreCase("postgres")) {
            return DB_TIPO;
        }
        return db.toLowerCase(Locale.ROOT);
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
            return new PgConn(host, port, db.isBlank() ? "postgres" : db);
        }
    }
}
