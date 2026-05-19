package com.mikunaigen.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.model.nosql.DashboardExportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
public class DashboardReportWebhookService {

    private static final Logger log = LoggerFactory.getLogger(DashboardReportWebhookService.class);

    private final BackupAutomatizacionService backupAutomatizacionService;
    private final B2PresignedUrlService b2PresignedUrlService;
    private final DashboardExportJobService dashboardExportJobService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public DashboardReportWebhookService(
            BackupAutomatizacionService backupAutomatizacionService,
            B2PresignedUrlService b2PresignedUrlService,
            DashboardExportJobService dashboardExportJobService,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper
    ) {
        this.backupAutomatizacionService = backupAutomatizacionService;
        this.b2PresignedUrlService = b2PresignedUrlService;
        this.dashboardExportJobService = dashboardExportJobService;
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
        String tab = body != null && body.get("tab") != null ? String.valueOf(body.get("tab")) : "";
        String fileName = body != null && body.get("file_name") != null
                ? String.valueOf(body.get("file_name"))
                : "reporte_dashboard.pdf";

        if ("success".equalsIgnoreCase(status)) {
            if (backupKey == null || backupKey.isBlank()) {
                completarFallido(jobId, backupKey, tab, fileName, "No se recibió la clave del archivo en B2.");
                return;
            }
            try {
                String downloadUrl = b2PresignedUrlService.presignedGetUrl(backupKey, Duration.ofHours(24));
                DashboardExportJob job = dashboardExportJobService.marcarListo(jobId, backupKey, downloadUrl);
                publicarListo(job);
                log.info("[DASHBOARD-REPORT] listo jobId={} tab={}", job.getId(), job.getTab());
            } catch (Exception e) {
                log.warn("[DASHBOARD-REPORT] error URL presignada jobId={}: {}", jobId, e.getMessage());
                completarFallido(jobId, backupKey, tab, fileName, "No se pudo generar el enlace de descarga.");
            }
            return;
        }

        String detail = body != null && body.get("detail") != null ? String.valueOf(body.get("detail")) : null;
        completarFallido(
                jobId,
                backupKey,
                tab,
                fileName,
                detail != null && !detail.isBlank()
                        ? detail
                        : "La generación del reporte falló en GitHub Actions."
        );
    }

    private void completarFallido(String jobId, String backupKey, String tab, String fileName, String message) {
        try {
            DashboardExportJob job = dashboardExportJobService.marcarFallido(jobId, backupKey, message);
            publicarFallido(job, message);
        } catch (Exception e) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("kind", "dashboard_report_failed");
            msg.put("tab", tab);
            msg.put("fileName", fileName);
            msg.put("message", message);
            enviarWs(msg);
        }
    }

    private void publicarListo(DashboardExportJob job) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("kind", "dashboard_report_ready");
        msg.put("jobId", job.getId());
        msg.put("tab", job.getTab());
        msg.put("tabLabel", job.getTabLabel());
        msg.put("fileName", job.getFileName());
        msg.put("format", job.getFormat());
        msg.put("downloadUrl", job.getDownloadUrl());
        enviarWs(msg);
    }

    private void publicarFallido(DashboardExportJob job, String message) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("kind", "dashboard_report_failed");
        msg.put("jobId", job.getId());
        msg.put("tab", job.getTab());
        msg.put("tabLabel", job.getTabLabel());
        msg.put("fileName", job.getFileName());
        msg.put("message", message);
        enviarWs(msg);
    }

    private void enviarWs(Map<String, Object> msg) {
        try {
            messagingTemplate.convertAndSend("/topic/admin/dashboard-report", objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("[DASHBOARD-REPORT] No se pudo publicar por WebSocket", e);
        }
    }
}
