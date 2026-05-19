package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.dto.BackupItemDto;
import com.mikunaigen.backend.service.BackupAutomatizacionService;
import com.mikunaigen.backend.service.BackupService;
import com.mikunaigen.backend.service.MaintenanceModeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/backups")
public class AdminBackupController {

    private final BackupService backupService;
    private final BackupAutomatizacionService backupAutomatizacionService;
    private final MaintenanceModeService maintenanceModeService;

    public AdminBackupController(
            BackupService backupService,
            BackupAutomatizacionService backupAutomatizacionService,
            MaintenanceModeService maintenanceModeService
    ) {
        this.backupService = backupService;
        this.backupAutomatizacionService = backupAutomatizacionService;
        this.maintenanceModeService = maintenanceModeService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<BackupItemDto>> list(@RequestParam String db) {
        return ResponseEntity.ok(backupService.list(db));
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestParam String db) {
        BackupItemDto item = backupService.generate(db);
        return ResponseEntity.ok(Map.of("ok", true, "item", item));
    }

    @PostMapping("/restore")
    public ResponseEntity<Map<String, Object>> restore(@RequestParam String db, @RequestParam String key) {
        backupService.restore(db, key);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/restore-pair")
    public ResponseEntity<Map<String, Object>> restorePair(@RequestBody Map<String, Object> body) {
        String pgKey = body != null && body.get("postgresKey") != null ? String.valueOf(body.get("postgresKey")) : null;
        String mgKey = body != null && body.get("mongoKey") != null ? String.valueOf(body.get("mongoKey")) : null;
        boolean notify = body != null && Boolean.TRUE.equals(body.get("notifyWhenDone"));
        String notifyEmail = body != null && body.get("notifyEmail") != null ? String.valueOf(body.get("notifyEmail")) : null;
        String key = pgKey != null && !pgKey.isBlank() ? pgKey : mgKey;
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "Key de respaldo requerida."));
        }
        maintenanceModeService.startRestore(notify, notifyEmail, Set.of("postgresql"));
        backupService.restore("postgresql", key);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestParam String key) {
        backupService.delete(key);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/automation")
    public ResponseEntity<Map<String, Object>> getAutomation() {
        return ResponseEntity.ok(backupAutomatizacionService.getForAdmin());
    }

    @PutMapping("/automation")
    public ResponseEntity<Map<String, Object>> putAutomation(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(backupAutomatizacionService.saveFromAdmin(body));
    }

    @PostMapping("/generate-pair")
    public ResponseEntity<Map<String, Object>> generatePair() {
        Map<String, String> keys = backupService.generatePairedBackupKeys();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "postgresKey", keys.get("postgresKey"),
                "mongoKey", keys.get("mongoKey")
        ));
    }
}

