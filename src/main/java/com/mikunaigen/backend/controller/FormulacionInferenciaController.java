package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.service.FormulacionExportacionService;
import com.mikunaigen.backend.service.FormulacionInferenciaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/formulacion/inferencia")
public class FormulacionInferenciaController {

    private final FormulacionInferenciaService inferenciaService;
    private final FormulacionExportacionService exportacionService;
    private final UserRepository userRepository;

    public FormulacionInferenciaController(
            FormulacionInferenciaService inferenciaService,
            FormulacionExportacionService exportacionService,
            UserRepository userRepository
    ) {
        this.inferenciaService = inferenciaService;
        this.exportacionService = exportacionService;
        this.userRepository = userRepository;
    }

    @GetMapping("/preparacion")
    public ResponseEntity<Map<String, Object>> preparacion() {
        return ResponseEntity.ok(inferenciaService.preparacion(usuarioId()));
    }

    @PostMapping("/ejecutar")
    public ResponseEntity<?> ejecutar(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(inferenciaService.ejecutar(usuarioId(), body));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/sesion/{sesionId}")
    public ResponseEntity<Map<String, Object>> sesion(@PathVariable String sesionId) {
        return ResponseEntity.ok(inferenciaService.obtenerSesion(usuarioId(), sesionId));
    }

    @GetMapping("/historial")
    public ResponseEntity<List<Map<String, Object>>> historial(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(inferenciaService.listarHistorial(usuarioId(), q));
    }

    @GetMapping("/receta/{id}")
    public ResponseEntity<Map<String, Object>> receta(@PathVariable UUID id) {
        return ResponseEntity.ok(inferenciaService.detalleReceta(usuarioId(), id));
    }

    @GetMapping("/historial/{id}/evaluar-guardado")
    public ResponseEntity<Map<String, Object>> evaluarGuardadoHistorial(@PathVariable UUID id) {
        return ResponseEntity.ok(inferenciaService.evaluarGuardadoHistorial(usuarioId(), id));
    }

    @PostMapping("/historial/{id}")
    public ResponseEntity<Map<String, Object>> guardarHistorial(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body
    ) {
        return ResponseEntity.ok(inferenciaService.guardarHistorial(usuarioId(), id, body));
    }

    @DeleteMapping("/historial/{id}")
    public ResponseEntity<Map<String, String>> eliminarHistorial(@PathVariable UUID id) {
        inferenciaService.eliminarHistorial(usuarioId(), id);
        return ResponseEntity.ok(Map.of("message", "Receta eliminada del historial."));
    }

    @PutMapping("/receta/{id}/editar")
    public ResponseEntity<?> editarReceta(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body
    ) {
        try {
            return ResponseEntity.ok(inferenciaService.editarReceta(usuarioId(), id, body));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", ex.getReason() != null ? ex.getReason() : "Datos inválidos."
                ));
            }
            throw ex;
        }
    }

    @GetMapping("/receta/{id}/exportar")
    public ResponseEntity<byte[]> exportar(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "xlsx") String formato
    ) {
        User user = cargarUsuario();
        byte[] archivo = exportacionService.exportarFicha(usuarioId(), id, formato, user);
        String nombre = exportacionService.nombreArchivo(formato);
        MediaType mediaType = "pdf".equalsIgnoreCase(formato)
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(mediaType)
                .body(archivo);
    }

    private UUID usuarioId() {
        verificarUsuarioFormulacion();
        return cargarUsuario().getId();
    }

    private User cargarUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado."));
    }

    private void verificarUsuarioFormulacion() {
        User user = cargarUsuario();
        String rol = user.getRole() != null ? user.getRole().getNombre() : "";
        if ("administrador".equalsIgnoreCase(rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta función es para usuarios de formulación.");
        }
    }
}
