package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.SolicitudCambioRolService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/solicitudes-cambio-rol")
public class AdminSolicitudPlanController {

    private final SolicitudCambioRolService solicitudService;

    public AdminSolicitudPlanController(SolicitudCambioRolService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(Map.of("solicitudes", solicitudService.listarParaAdmin(estado)));
    }

    @GetMapping("/{id}/comprobante")
    public ResponseEntity<byte[]> comprobante(@PathVariable Long id) {
        byte[] data = solicitudService.obtenerComprobante(id);
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .body(data);
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<?> aprobar(@PathVariable Long id) {
        return solicitudService.aprobar(id);
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<?> rechazar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return solicitudService.rechazar(id, body.get("motivo"));
    }
}
