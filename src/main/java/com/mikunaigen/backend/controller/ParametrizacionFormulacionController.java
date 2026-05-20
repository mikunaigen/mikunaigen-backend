package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.service.ParametrizacionFormulacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/formulacion/parametrizacion")
public class ParametrizacionFormulacionController {

    private final ParametrizacionFormulacionService parametrizacionService;
    private final UserRepository userRepository;

    public ParametrizacionFormulacionController(
            ParametrizacionFormulacionService parametrizacionService,
            UserRepository userRepository
    ) {
        this.parametrizacionService = parametrizacionService;
        this.userRepository = userRepository;
    }

    @GetMapping("/contexto")
    public ResponseEntity<Map<String, Object>> contexto() {
        return ResponseEntity.ok(parametrizacionService.obtenerContexto(usuarioId()));
    }

    @PutMapping
    public ResponseEntity<?> guardar(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(parametrizacionService.guardar(usuarioId(), body));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", ex.getReason() != null ? ex.getReason() : "Error de validación."
                ));
            }
            throw ex;
        }
    }

    @GetMapping("/alimentos")
    public ResponseEntity<List<Map<String, Object>>> buscarAlimentos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoria
    ) {
        return ResponseEntity.ok(parametrizacionService.buscarAlimentos(q, categoria));
    }

    @GetMapping("/alimentos/{id}/composicion")
    public ResponseEntity<Map<String, Object>> composicion(@PathVariable Integer id) {
        return ResponseEntity.ok(parametrizacionService.detalleAlimento(id));
    }

    private UUID usuarioId() {
        verificarUsuarioFormulacion();
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado."));
        return user.getId();
    }

    private void verificarUsuarioFormulacion() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado."));
        String rol = user.getRole() != null ? user.getRole().getNombre() : "";
        if ("administrador".equalsIgnoreCase(rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta función es para usuarios de formulación.");
        }
    }
}
