package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.AdminAuditoriaSeguridadService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auditoria-seguridad")
public class AdminAuditoriaSeguridadController {

    private final AdminAuditoriaSeguridadService auditoriaService;

    public AdminAuditoriaSeguridadController(AdminAuditoriaSeguridadService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String componente
    ) {
        return ResponseEntity.ok(auditoriaService.consultar(fechaDesde, fechaHasta, usuario, componente));
    }
}
