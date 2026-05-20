package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.service.ObjetivoNutricionalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/formulacion/objetivo-nutricional")
public class ObjetivoNutricionalController {

    private final ObjetivoNutricionalService objetivoService;
    private final UserRepository userRepository;

    public ObjetivoNutricionalController(
            ObjetivoNutricionalService objetivoService,
            UserRepository userRepository
    ) {
        this.objetivoService = objetivoService;
        this.userRepository = userRepository;
    }

    @GetMapping("/campos")
    public ResponseEntity<Map<String, Object>> campos() {
        verificarUsuarioFormulacion();
        return ResponseEntity.ok(Map.of("campos", ObjetivoNutricionalService.CAMPOS));
    }

    @GetMapping("/perfiles-ejemplo")
    public ResponseEntity<Map<String, Object>> perfilesEjemplo() {
        verificarUsuarioFormulacion();
        return ResponseEntity.ok(Map.of("perfiles", objetivoService.perfilesEjemplo()));
    }

    @PostMapping("/validar")
    public ResponseEntity<Map<String, Object>> validar(@RequestBody Map<String, Object> body) {
        verificarUsuarioFormulacion();
        Map<String, Object> resultado = objetivoService.validar(body);
        if (Boolean.FALSE.equals(resultado.get("valido"))) {
            return ResponseEntity.badRequest().body(resultado);
        }
        return ResponseEntity.ok(resultado);
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
