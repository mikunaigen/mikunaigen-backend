package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.service.EmailService;
import com.mikunaigen.backend.service.MaintenanceModeService;
import com.mikunaigen.backend.service.AuditoriaRestauracionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class MaintenanceWebhookController {

    private final MaintenanceModeService maintenanceModeService;
    private final ConfiguracionGlobalRepository configRepository;
    private final EmailService emailService;
    private final AuditoriaRestauracionService auditoriaRestauracionService;

    public MaintenanceWebhookController(
            MaintenanceModeService maintenanceModeService,
            ConfiguracionGlobalRepository configRepository,
            EmailService emailService,
            AuditoriaRestauracionService auditoriaRestauracionService
    ) {
        this.maintenanceModeService = maintenanceModeService;
        this.configRepository = configRepository;
        this.emailService = emailService;
        this.auditoriaRestauracionService = auditoriaRestauracionService;
    }

    @PostMapping("/maintenance-end")
    public ResponseEntity<Map<String, Object>> maintenanceEnd(
            @RequestHeader(value = "X-Maintenance-Secret", required = false) String secret,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (!maintenanceModeService.verifySecret(secret)) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        String op = body != null && body.get("operation") != null ? String.valueOf(body.get("operation")) : "";
        String db = body != null && body.get("db_type") != null ? String.valueOf(body.get("db_type")) : "";
        String status = body != null && body.get("status") != null ? String.valueOf(body.get("status")) : "unknown";
        if ("restore".equalsIgnoreCase(op)) {
            maintenanceModeService.markRestoreDone(db);
            if ("success".equalsIgnoreCase(status) || "ok".equalsIgnoreCase(status)) {
                auditoriaRestauracionService.marcarExitosa(maintenanceModeService.restauracionAuditoriaId());
            } else if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                auditoriaRestauracionService.marcarFallida(maintenanceModeService.restauracionAuditoriaId());
            }
        }
        if (!maintenanceModeService.isMaintenance()) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        if (maintenanceModeService.isRestoreComplete()) {
            maintenanceModeService.endMaintenance();
            if (maintenanceModeService.shouldNotifyAdmin()) {
                enviarNotificacion(maintenanceModeService.notifyEmail(), status);
            }
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/maintenance-status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "maintenance", maintenanceModeService.isMaintenance(),
                "startedAt", maintenanceModeService.startedAt() != null ? maintenanceModeService.startedAt().toString() : null
        ));
    }

    private void enviarNotificacion(String to, String workflowStatus) {
        ConfiguracionGlobal cfg = configRepository.findById(1).orElse(null);
        if (cfg == null) {
            return;
        }
        String em = cfg.getSmtpEmail();
        String pw = cfg.getSmtpContrasenaApp();
        if (em == null || em.isBlank() || pw == null || pw.isBlank()) {
            return;
        }
        String nb = cfg.getNombrePlataforma() != null && !cfg.getNombrePlataforma().isBlank()
                ? cfg.getNombrePlataforma().trim()
                : "Mikunaigen";
        String st = workflowStatus != null ? workflowStatus.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
        String asunto = "Mantenimiento finalizado — " + nb;
        String cuerpo = "La restauración de la base de datos finalizó.\n\nEstado: " + st + "\n\nHora: " + Instant.now();
        emailService.enviarCorreoTextoPlano(to, asunto, cuerpo, em, pw, null);
    }
}
