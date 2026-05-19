package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.service.SolicitudCambioRolService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/planes")
public class PlanUsuarioController {

    private final SolicitudCambioRolService solicitudService;
    private final UserRepository userRepository;

    public PlanUsuarioController(SolicitudCambioRolService solicitudService, UserRepository userRepository) {
        this.solicitudService = solicitudService;
        this.userRepository = userRepository;
    }

    @GetMapping("/contexto")
    public ResponseEntity<?> contexto() {
        User user = obtenerUsuarioAutenticado();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        return ResponseEntity.ok(solicitudService.contextoUsuario(user));
    }

    @PostMapping(value = "/solicitud", consumes = "multipart/form-data")
    public ResponseEntity<?> crearSolicitud(
            @RequestParam("rolSolicitado") String rolSolicitado,
            @RequestParam("justificacion") String justificacion,
            @RequestParam("comprobante") MultipartFile comprobante
    ) {
        User user = obtenerUsuarioAutenticado();
        return solicitudService.crearSolicitud(user, rolSolicitado, justificacion, comprobante);
    }

    private User obtenerUsuarioAutenticado() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof String email) || email.isBlank()) {
            return null;
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.isDeleted()) {
            return null;
        }
        return user;
    }
}
