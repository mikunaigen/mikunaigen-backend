package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.AdminUsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/usuarios")
public class AdminUsuarioController {

    private final AdminUsuarioService adminUsuarioService;

    public AdminUsuarioController(AdminUsuarioService adminUsuarioService) {
        this.adminUsuarioService = adminUsuarioService;
    }

    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String plan
    ) {
        return ResponseEntity.ok(Map.of(
                "usuarios",
                adminUsuarioService.listarUsuarios(busqueda, rol, plan)
        ));
    }

    @PostMapping("/{id}/renovar-suscripcion")
    public ResponseEntity<?> renovar(@PathVariable UUID id) {
        return adminUsuarioService.renovarSuscripcion(id);
    }

    @PostMapping("/{id}/desactivar")
    public ResponseEntity<?> desactivar(@PathVariable UUID id) {
        return adminUsuarioService.desactivarCuenta(id);
    }

    @PostMapping("/{id}/reactivar")
    public ResponseEntity<?> reactivar(@PathVariable UUID id) {
        return adminUsuarioService.reactivarCuenta(id);
    }
}
