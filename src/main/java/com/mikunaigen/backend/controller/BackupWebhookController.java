package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.BackupAutomatizacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class BackupWebhookController {

    private final BackupAutomatizacionService backupAutomatizacionService;

    public BackupWebhookController(BackupAutomatizacionService backupAutomatizacionService) {
        this.backupAutomatizacionService = backupAutomatizacionService;
    }

    @PostMapping("/backup-workflow")
    public ResponseEntity<Map<String, Object>> workflow(
            @RequestHeader(value = "X-Backup-Signature", required = false) String signature,
            @RequestBody(required = false) Map<String, Object> body) {
        if (!backupAutomatizacionService.verifyNotifySecret(signature)) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        String status = body != null && body.get("status") != null ? String.valueOf(body.get("status")) : "unknown";
        String op = body != null && body.get("operation") != null ? String.valueOf(body.get("operation")) : "";
        String db = body != null && body.get("db_type") != null ? String.valueOf(body.get("db_type")) : "";
        backupAutomatizacionService.recordWorkflowResult(status, op + " " + db);
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
