package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.IaModelosService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/ia-modelos")
public class IaModelosController {

    private final IaModelosService iaModelosService;

    public IaModelosController(IaModelosService iaModelosService) {
        this.iaModelosService = iaModelosService;
    }

    @GetMapping("/estado")
    public ResponseEntity<Map<String, Object>> estado() {
        return ResponseEntity.ok(iaModelosService.estadoPublico());
    }

    @GetMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Map<String, Object>> configuracion() {
        return ResponseEntity.ok(iaModelosService.configuracionCompleta());
    }

    @PatchMapping("/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Map<String, Object>> toggleIa(@RequestBody Map<String, Object> body) {
        boolean iaActiva = Boolean.TRUE.equals(body.get("iaActiva"));
        return ResponseEntity.ok(iaModelosService.toggleIa(iaActiva));
    }

    @PatchMapping("/slot/{slotNumber}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Map<String, Object>> toggleSlot(
            @PathVariable int slotNumber,
            @RequestBody Map<String, Object> body
    ) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        try {
            return ResponseEntity.ok(iaModelosService.toggleSlot(slotNumber, enabled));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/slot-1/upload")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<?> uploadSlot1(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(iaModelosService.uploadSlot1(body));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "No se pudo guardar el modelo del slot 1."));
        }
    }

    @PostMapping("/slot-2/upload")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<?> uploadSlot2(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(iaModelosService.uploadSlot2(body));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "No se pudo guardar el paquete del slot 2."));
        }
    }

    @PostMapping("/slot-3/upload")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<?> uploadSlot3(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(iaModelosService.uploadSlot3(body));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "No se pudo guardar el paquete del slot 3."));
        }
    }

    @PostMapping("/dataset/{slot}/solicitar")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Map<String, Object>> solicitarDataset(@PathVariable int slot) {
        if (slot < 1 || slot > 3) {
            return ResponseEntity.badRequest().body(Map.of("message", "Slot inválido."));
        }
        return ResponseEntity.ok(iaModelosService.solicitarDataset(slot));
    }

    @GetMapping("/dataset/jobs/{jobId}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Map<String, Object>> estadoDataset(@PathVariable String jobId) {
        return ResponseEntity.ok(iaModelosService.estadoDatasetJob(jobId));
    }
}
