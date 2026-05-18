package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.nosql.DatasetExportJob;
import com.mikunaigen.backend.util.PostgresConnInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AiDatasetDispatchService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    @Value("${app.backup.b2-bucket}")
    private String bucket;

    @Value("${app.backup.b2-key-id}")
    private String keyId;

    @Value("${app.backup.b2-app-key}")
    private String appKey;

    @Value("${app.backup.b2-endpoint}")
    private String endpoint;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String pgUser;

    @Value("${spring.datasource.password}")
    private String pgPassword;

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database}")
    private String mongoDatabase;

    @Value("${app.github.token:}")
    private String githubToken;

    @Value("${app.github.owner:}")
    private String githubOwner;

    @Value("${app.github.repo:}")
    private String githubRepo;

    @Value("${app.public.api.base-url:}")
    private String publicApiBaseUrl;

    @Value("${app.backup.notify-secret:}")
    private String notifySecret;

    private final AiDatasetJobService aiDatasetJobService;

    public AiDatasetDispatchService(AiDatasetJobService aiDatasetJobService) {
        this.aiDatasetJobService = aiDatasetJobService;
    }

    public Map<String, Object> solicitarGeneracion(int slot) {
        if (slot < 1 || slot > 3) {
            throw new IllegalArgumentException("Slot de dataset no válido.");
        }
        if (githubToken == null || githubToken.isBlank() || githubOwner.isBlank() || githubRepo.isBlank()) {
            throw new IllegalArgumentException("Credenciales de GitHub no configuradas en el servidor.");
        }
        if (publicApiBaseUrl == null || publicApiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("URL pública del API no configurada.");
        }
        if (notifySecret == null || notifySecret.isBlank()) {
            throw new IllegalArgumentException("Secreto de notificación no configurado.");
        }

        String fileName = String.format("dataset_modelo_%02d.zip", slot);
        String timestamp = TS.format(LocalDateTime.now());
        String backupKey = "dataset_export_" + String.format("%02d", slot) + "/" + fileName.replace(".zip", "") + "_" + timestamp + ".zip";

        PostgresConnInfo c = PostgresConnInfo.fromJdbc(jdbcUrl);

        Map<String, Object> b2 = new HashMap<>();
        b2.put("bucket", bucket);
        b2.put("key_id", keyId);
        b2.put("app_key", appKey);
        b2.put("endpoint", endpoint);

        Map<String, Object> pg = new HashMap<>();
        pg.put("host", c.host);
        pg.put("port", String.valueOf(c.port));
        pg.put("name", c.db);
        pg.put("user", pgUser);
        pg.put("pass", pgPassword);

        Map<String, Object> dbPayload = new HashMap<>();
        dbPayload.put("pg", pg);
        dbPayload.put("mongo_uri", mongoUri);
        dbPayload.put("mongo_database", mongoDatabase);

        String notifyUrl = publicApiBaseUrl.replaceAll("/+$", "") + "/api/webhooks/dataset-workflow";

        DatasetExportJob job = aiDatasetJobService.crearPendiente(slot, fileName, backupKey);

        Map<String, Object> clientPayload = new HashMap<>();
        clientPayload.put("operation", "generate-dataset");
        clientPayload.put("job_id", job.getId());
        clientPayload.put("slot", slot);
        clientPayload.put("backup_key", backupKey);
        clientPayload.put("file_name", fileName);
        clientPayload.put("b2", b2);
        clientPayload.put("db", dbPayload);
        clientPayload.put("notify_url", notifyUrl);
        clientPayload.put("notify_secret", notifySecret);

        Map<String, Object> payload = Map.of(
                "event_type", "trigger-dataset",
                "client_payload", clientPayload
        );

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        String url = String.format("https://api.github.com/repos/%s/%s/dispatches", githubOwner, githubRepo);
        try {
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo iniciar la generación en GitHub Actions." + e.getMessage());
        }

        Map<String, Object> out = new HashMap<>();
        out.put("accepted", true);
        out.put("jobId", job.getId());
        out.put("slot", slot);
        out.put("fileName", fileName);
        out.put("status", AiDatasetJobService.STATUS_PENDING);
        out.put("message", "Generando dataset... Te avisaremos cuando esté listo.");
        return out;
    }
}
