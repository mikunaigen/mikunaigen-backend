package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.service.LimitesNormativosService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/limites-normativos")
public class AdminLimitesNormativosController {

    private final LimitesNormativosService limitesNormativosService;
    private final UserRepository userRepository;

    public AdminLimitesNormativosController(
            LimitesNormativosService limitesNormativosService,
            UserRepository userRepository) {
        this.limitesNormativosService = limitesNormativosService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listar() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", limitesNormativosService.listarParaAdmin());
        return ResponseEntity.ok(body);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> guardar(@RequestBody Map<String, Object> body) {
        UUID adminId = obtenerAdminId();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cambios = body != null && body.get("cambios") instanceof List<?>
                ? (List<Map<String, Object>>) body.get("cambios")
                : null;
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("message", "Límites normativos actualizados.");
        respuesta.put("items", limitesNormativosService.actualizarDesdeAdmin(adminId, cambios));
        return ResponseEntity.ok(respuesta);
    }

    private UUID obtenerAdminId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof String email) || email.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado.");
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado.");
        }
        return user.getId();
    }
}
