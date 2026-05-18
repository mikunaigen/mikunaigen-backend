package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.AiDatasetWebhookService;
import com.mikunaigen.backend.service.BackupAutomatizacionService;
import com.mikunaigen.backend.service.DashboardReportWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class BackupWebhookController {

    private final BackupAutomatizacionService backupAutomatizacionService;
    private final AiDatasetWebhookService aiDatasetWebhookService;
    private final DashboardReportWebhookService dashboardReportWebhookService;

    public BackupWebhookController(
            BackupAutomatizacionService backupAutomatizacionService,
            AiDatasetWebhookService aiDatasetWebhookService,
            DashboardReportWebhookService dashboardReportWebhookService
    ) {
        this.backupAutomatizacionService = backupAutomatizacionService;
        this.aiDatasetWebhookService = aiDatasetWebhookService;
        this.dashboardReportWebhookService = dashboardReportWebhookService;
    }

    @PostMapping("/backup-workflow")
    public ResponseEntity<Map<String, Object>> workflow(
            @RequestHeader(value = "X-Backup-Signature", required = false) String signature,
            @RequestBody(required = false) Map<String, Object> body) {
        if (!backupAutomatizacionService.verifyNotifySecret(signature)) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        String status = body != null && body.get("status") != null ? String.valueOf(body.get("status")) : "unknown";
        String op = body != null && body.get("operation") != null ? String.valueOf(body.get("operation")) : null;
        String db = body != null && body.get("db_type") != null ? String.valueOf(body.get("db_type")) : null;
        String detail = body != null && body.get("detail") != null ? String.valueOf(body.get("detail")) : null;
        String extra = (op != null ? "op=" + op + " " : "") + (db != null ? "db=" + db + " " : "");
        backupAutomatizacionService.recordWorkflowResult(status, (extra + (detail != null ? detail : "")).trim());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/dataset-workflow")
    public ResponseEntity<Map<String, Object>> datasetWorkflow(
            @RequestHeader(value = "X-Backup-Signature", required = false) String signature,
            @RequestBody(required = false) Map<String, Object> body) {
        if (!aiDatasetWebhookService.verifySignature(signature)) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        aiDatasetWebhookService.handleWorkflowResult(body);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/dashboard-report-workflow")
    public ResponseEntity<Map<String, Object>> dashboardReportWorkflow(
            @RequestHeader(value = "X-Backup-Signature", required = false) String signature,
            @RequestBody(required = false) Map<String, Object> body) {
        if (!dashboardReportWebhookService.verifySignature(signature)) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        dashboardReportWebhookService.handleWorkflowResult(body);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/backup-cron")
    public ResponseEntity<Map<String, Object>> cron(
            @RequestHeader(value = "X-Cron-Secret", required = false) String secret) {
        try {
            backupAutomatizacionService.runFromExternalCron(secret);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
    }
}
