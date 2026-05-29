package com.mikunaigen.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class EntrenamientoDatasetGithubService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA_DATASET = DateTimeFormatter.ofPattern("yyyy_MM_dd", Locale.ROOT);

    @Value("${app.github.token:}")
    private String githubToken;

    @Value("${app.github.owner:}")
    private String githubOwner;

    @Value("${app.github.repo:}")
    private String githubRepo;

    @Value("${app.github.api-base-url:https://api.github.com}")
    private String githubApiBaseUrl;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String pgUser;

    @Value("${spring.datasource.password}")
    private String pgPassword;

    @Value("${app.backup.b2-bucket}")
    private String bucket;

    @Value("${app.backup.b2-key-id}")
    private String keyId;

    @Value("${app.backup.b2-app-key}")
    private String appKey;

    @Value("${app.backup.b2-endpoint}")
    private String endpoint;

    @Value("${app.backup.notify-secret}")
    private String notifySecret;

    @Value("${app.public.api.base-url}")
    private String publicApiBaseUrl;

    @Value("${app.kaggle.api.token:}")
    private String kaggleApiToken;

    @Value("${app.kaggle.kernel.propietario:}")
    private String kagglePropietario;

    @Value("${app.kaggle.kernel.slug:}")
    private String kaggleSlug;

    @Value("${app.kaggle.webhook.secret:}")
    private String kaggleWebhookSecret;

    public String claveDatasetHoy() {
        return FECHA_DATASET.format(ZonedDateTime.now(ZONA_LIMA)) + "_dataset.zip";
    }

    public void despacharExportacion(String jobId) {
        if (githubToken == null || githubToken.isBlank() || githubOwner.isBlank() || githubRepo.isBlank()) {
            throw new IllegalStateException("Credenciales de GitHub no configuradas.");
        }
        PgConn c = PgConn.fromJdbc(jdbcUrl);
        String datasetKey = claveDatasetHoy();
        String notifyUrl = publicApiBaseUrl.replaceAll("/$", "") + "/api/webhooks/entrenamiento-dataset";

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

        Map<String, Object> kaggle = new HashMap<>();
        kaggle.put("token", kaggleApiToken);
        kaggle.put("propietario", kagglePropietario);
        kaggle.put("slug", kaggleSlug);
        kaggle.put("webhook_url", publicApiBaseUrl.replaceAll("/$", "") + "/api/webhooks/kaggle-entrenamiento");
        kaggle.put("webhook_secret", kaggleWebhookSecret);

        Map<String, Object> clientPayload = new HashMap<>();
        clientPayload.put("job_id", jobId);
        clientPayload.put("dataset_key", datasetKey);
        clientPayload.put("notify_url", notifyUrl);
        clientPayload.put("notify_secret", notifySecret);
        clientPayload.put("pg", pg);
        clientPayload.put("b2", b2);
        clientPayload.put("kaggle", kaggle);

        Map<String, Object> payload = new HashMap<>();
        payload.put("event_type", "trigger-entrenamiento-dataset");
        payload.put("client_payload", clientPayload);

        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");

        String url = normalizeGithubBase(githubApiBaseUrl) + "/repos/" + githubOwner + "/" + githubRepo + "/dispatches";
        rest.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
    }

    private static String normalizeGithubBase(String base) {
        if (base == null || base.isBlank()) {
            return "https://api.github.com";
        }
        String t = base.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
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