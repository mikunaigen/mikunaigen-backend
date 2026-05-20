package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.service.PreferenciasUsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/preferencias")
public class PreferenciasController {

    private final PreferenciasUsuarioService preferenciasService;
    private final UserRepository userRepository;

    public PreferenciasController(
            PreferenciasUsuarioService preferenciasService,
            UserRepository userRepository
    ) {
        this.preferenciasService = preferenciasService;
        this.userRepository = userRepository;
    }

    @GetMapping("/contexto")
    public ResponseEntity<Map<String, Object>> contexto() {
        UUID userId = obtenerUsuarioId();
        verificarRolFormulacion(userId);
        return ResponseEntity.ok(preferenciasService.obtenerContexto(userId));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> obtener() {
        UUID userId = obtenerUsuarioId();
        verificarRolFormulacion(userId);
        return ResponseEntity.ok(preferenciasService.obtenerPreferencias(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> guardar(@RequestBody Map<String, Object> body) {
        UUID userId = obtenerUsuarioId();
        verificarRolFormulacion(userId);
        return ResponseEntity.ok(preferenciasService.guardarPreferencias(userId, body));
    }

    @GetMapping("/alimentos")
    public ResponseEntity<List<Map<String, Object>>> buscarAlimentos(@RequestParam String q) {
        UUID userId = obtenerUsuarioId();
        verificarRolFormulacion(userId);
        return ResponseEntity.ok(preferenciasService.buscarAlimentos(q));
    }

    private UUID obtenerUsuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado."));
        return user.getId();
    }

    private void verificarRolFormulacion(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado."));
        String rol = user.getRole() != null ? user.getRole().getNombre() : "";
        if ("administrador".equalsIgnoreCase(rol) || "ADMIN".equalsIgnoreCase(rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta función no está disponible para administradores.");
        }
    }
}
