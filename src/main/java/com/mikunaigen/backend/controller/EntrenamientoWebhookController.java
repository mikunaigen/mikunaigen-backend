package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.EntrenamientoIaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class EntrenamientoWebhookController {

    private final EntrenamientoIaService entrenamientoIaService;

    public EntrenamientoWebhookController(EntrenamientoIaService entrenamientoIaService) {
        this.entrenamientoIaService = entrenamientoIaService;
    }

    @PostMapping("/entrenamiento-dataset")
    public ResponseEntity<Map<String, Object>> datasetWorkflow(
            @RequestHeader(value = "X-Backup-Signature", required = false) String firma,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (!entrenamientoIaService.verificarSecretoDataset(firma)) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        if (body != null) {
            entrenamientoIaService.procesarWebhookDataset(body);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/kaggle-entrenamiento")
    public ResponseEntity<Map<String, Object>> kaggleWebhook(
            @RequestHeader(value = "X-Kaggle-Signature", required = false) String firma,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (!entrenamientoIaService.verificarSecretoKaggle(firma)) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        boolean shouldCancel = false;
        if (body != null) {
            shouldCancel = entrenamientoIaService.procesarWebhookKaggle(body);
        }
        return ResponseEntity.ok(Map.of("ok", true, "should_cancel", shouldCancel));
    }
}
