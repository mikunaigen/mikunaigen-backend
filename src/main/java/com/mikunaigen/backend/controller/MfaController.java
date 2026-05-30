package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.service.MfaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/perfil/me/mfa")
public class MfaController {

    private final UserRepository userRepository;
    private final MfaService mfaService;

    public MfaController(UserRepository userRepository, MfaService mfaService) {
        this.userRepository = userRepository;
        this.mfaService = mfaService;
    }

    @GetMapping("/estado")
    public ResponseEntity<?> estado() {
        User usuario = obtenerUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        return ResponseEntity.ok(Map.of(
                "mfaEnabled", usuario.isMfaEnabled(),
                "pendingSetup", !usuario.isMfaEnabled()
                        && usuario.getMfaSecret() != null
                        && !usuario.getMfaSecret().isBlank()
        ));
    }

    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciar() {
        User usuario = obtenerUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        try {
            MfaService.ResultadoInicioMfa resultado = mfaService.iniciarConfiguracion(usuario);
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("otpAuthUri", resultado.otpAuthUri());
            cuerpo.put("secretPlain", resultado.secretPlain());
            cuerpo.put("email", usuario.getEmail());
            return ResponseEntity.ok(cuerpo);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/confirmar")
    public ResponseEntity<?> confirmar(@RequestBody Map<String, String> body) {
        User usuario = obtenerUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        String codigo = body != null ? body.get("code") : null;
        if (codigo == null || codigo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El código es obligatorio."));
        }
        try {
            usuario = userRepository.findByEmail(usuario.getEmail()).orElse(usuario);
            List<String> codigosRespaldo = mfaService.confirmarActivacion(usuario, codigo);
            return ResponseEntity.ok(Map.of(
                    "message", "Autenticación de doble factor activada.",
                    "mfaEnabled", true,
                    "backupCodes", codigosRespaldo
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/desactivar")
    public ResponseEntity<?> desactivar(@RequestBody Map<String, String> body) {
        User usuario = obtenerUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        usuario = userRepository.findByEmail(usuario.getEmail()).orElse(usuario);
        String contrasena = body != null ? body.get("password") : null;
        String codigo = body != null ? body.get("code") : null;
        if (contrasena == null || contrasena.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña es obligatoria."));
        }
        if (codigo == null || codigo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El código del autenticador es obligatorio."));
        }
        try {
            mfaService.desactivar(usuario, contrasena, codigo);
            return ResponseEntity.ok(Map.of(
                    "message", "Autenticación de doble factor desactivada.",
                    "mfaEnabled", false
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private User obtenerUsuarioAutenticado() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof String email) || email.isBlank()) {
            return null;
        }
        User usuario = userRepository.findByEmail(email).orElse(null);
        if (usuario == null || usuario.isDeleted()) {
            return null;
        }
        return usuario;
    }
}
