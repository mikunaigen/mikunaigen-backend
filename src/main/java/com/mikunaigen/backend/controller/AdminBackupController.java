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
    public ResponseEntity<Map<String, Object>> restaurarRespaldo(@RequestBody Map<String, Object> body) {
        String clave = body != null && body.get("postgresKey") != null ? String.valueOf(body.get("postgresKey")) : null;
        boolean notificar = body != null && Boolean.TRUE.equals(body.get("notifyWhenDone"));
        String correoNotificacion = body != null && body.get("notifyEmail") != null
                ? String.valueOf(body.get("notifyEmail"))
                : null;
        if (clave == null || clave.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "Key de respaldo requerida."));
        }
        maintenanceModeService.startRestore(notificar, correoNotificacion, Set.of("postgresql"));
        backupService.restore("postgresql", clave);
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
    public ResponseEntity<Map<String, Object>> generarRespaldo() {
        Map<String, String> claves = backupService.generatePairedBackupKeys();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "postgresKey", claves.get("postgresKey")
        ));
    }
}

