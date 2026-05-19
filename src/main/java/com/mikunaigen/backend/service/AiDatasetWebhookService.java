package com.mikunaigen.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.model.nosql.DatasetExportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
public class AiDatasetWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AiDatasetWebhookService.class);

    private final BackupAutomatizacionService backupAutomatizacionService;
    private final B2PresignedUrlService b2PresignedUrlService;
    private final AiDatasetJobService aiDatasetJobService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public AiDatasetWebhookService(
            BackupAutomatizacionService backupAutomatizacionService,
            B2PresignedUrlService b2PresignedUrlService,
            AiDatasetJobService aiDatasetJobService,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper
    ) {
        this.backupAutomatizacionService = backupAutomatizacionService;
        this.b2PresignedUrlService = b2PresignedUrlService;
        this.aiDatasetJobService = aiDatasetJobService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean verifySignature(String signature) {
        return backupAutomatizacionService.verifyNotifySecret(signature);
    }

    public void handleWorkflowResult(Map<String, Object> body) {
        String status = body != null && body.get("status") != null ? String.valueOf(body.get("status")) : "unknown";
        String jobId = body != null && body.get("job_id") != null ? String.valueOf(body.get("job_id")) : null;
        String backupKey = body != null && body.get("backup_key") != null ? String.valueOf(body.get("backup_key")) : null;
        int slot = parseSlot(body != null ? body.get("slot") : null);
        String fileName = body != null && body.get("file_name") != null
                ? String.valueOf(body.get("file_name"))
                : String.format("dataset_modelo_%02d.zip", slot);

        if ("success".equalsIgnoreCase(status)) {
            if (backupKey == null || backupKey.isBlank()) {
                completarFallido(jobId, backupKey, slot, fileName, "No se recibió la clave del archivo en B2.");
                return;
            }
            try {
                String downloadUrl = b2PresignedUrlService.presignedGetUrl(backupKey, Duration.ofHours(24));
                DatasetExportJob job = aiDatasetJobService.marcarListo(jobId, backupKey, downloadUrl);
                publicarListo(job);
                log.info("[DATASET] listo jobId={} slot={}", job.getId(), job.getSlot());
            } catch (Exception e) {
                log.warn("[DATASET] error URL presignada jobId={}: {}", jobId, e.getMessage());
                completarFallido(jobId, backupKey, slot, fileName, "No se pudo generar el enlace de descarga.");
            }
            return;
        }

        String detail = body != null && body.get("detail") != null ? String.valueOf(body.get("detail")) : null;
        completarFallido(
                jobId,
                backupKey,
                slot,
                fileName,
                detail != null && !detail.isBlank()
                        ? detail
                        : "La generación del dataset falló en GitHub Actions."
        );
    }

    private void completarFallido(String jobId, String backupKey, int slot, String fileName, String message) {
        try {
            DatasetExportJob job = aiDatasetJobService.marcarFallido(jobId, backupKey, message);
            publicarFallido(job, message);
        } catch (Exception e) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("kind", "dataset_failed");
            msg.put("slot", slot);
            msg.put("fileName", fileName);
            msg.put("message", message);
            enviarWs(msg);
        }
    }

    private void publicarListo(DatasetExportJob job) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("kind", "dataset_ready");
        msg.put("jobId", job.getId());
        msg.put("slot", job.getSlot());
        msg.put("fileName", job.getFileName());
        msg.put("downloadUrl", job.getDownloadUrl());
        enviarWs(msg);
    }

    private void publicarFallido(DatasetExportJob job, String message) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("kind", "dataset_failed");
        msg.put("jobId", job.getId());
        msg.put("slot", job.getSlot());
        msg.put("fileName", job.getFileName());
        msg.put("message", message);
        enviarWs(msg);
    }

    private void enviarWs(Map<String, Object> msg) {
        try {
            messagingTemplate.convertAndSend("/topic/admin/dataset", objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("[DATASET] No se pudo publicar por WebSocket", e);
        }
    }

    private int parseSlot(Object raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
