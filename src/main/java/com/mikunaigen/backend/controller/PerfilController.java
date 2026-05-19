package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.model.sql.VerificationCode;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.repository.sql.RoleRepository;
import com.mikunaigen.backend.repository.sql.SolicitudCambioRolRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.repository.sql.VerificationCodeRepository;
import com.mikunaigen.backend.exception.EmailDispatchException;
import com.mikunaigen.backend.service.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final UserRepository userRepository;
    private final ConfiguracionGlobalRepository configuracionGlobalRepository;
    private final SolicitudCambioRolRepository solicitudCambioRolRepository;
    private final RoleRepository roleRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final EmailService emailService;

    public PerfilController(
            UserRepository userRepository,
            ConfiguracionGlobalRepository configuracionGlobalRepository,
            SolicitudCambioRolRepository solicitudCambioRolRepository,
            RoleRepository roleRepository,
            VerificationCodeRepository verificationCodeRepository,
            EmailService emailService
    ) {
        this.userRepository = userRepository;
        this.configuracionGlobalRepository = configuracionGlobalRepository;
        this.solicitudCambioRolRepository = solicitudCambioRolRepository;
        this.roleRepository = roleRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.emailService = emailService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> obtenerPerfil() {
        User user = obtenerUsuarioAutenticado();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        return ResponseEntity.ok(buildPerfilResponse(user));
    }

    @PutMapping("/me")
    public ResponseEntity<?> actualizarPerfil(@RequestBody Map<String, Object> body) {
        User user = obtenerUsuarioAutenticado();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }

        String nombres = trimToNull(String.valueOf(body.getOrDefault("nombres", body.getOrDefault("fullName", ""))));
        String apellidos = trimToNull(String.valueOf(body.getOrDefault("apellidos", "")));

        if (nombres == null && body.get("fullName") != null) {
            String full = trimToNull(String.valueOf(body.get("fullName")));
            if (full != null) {
                String[] p = full.split("\\s+", 2);
                nombres = p[0];
                apellidos = p.length > 1 ? p[1] : "";
            }
        }

        if (nombres == null || nombres.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Los nombres no pueden estar en blanco."));
        }
        if (apellidos == null || apellidos.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Los apellidos no pueden estar en blanco."));
        }
        if (nombres.matches(".*\\d.*")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Los nombres no pueden contener números."));
        }
        if (apellidos.matches(".*\\d.*")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Los apellidos no pueden contener números."));
        }
        if (!nombres.matches("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü\\s]+$")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Los nombres solo pueden contener letras del abecedario español y tildes."));
        }
        if (!apellidos.matches("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü\\s]+$")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Los apellidos solo pueden contener letras del abecedario español y tildes."));
        }

        user.setNombres(nombres.trim());
        user.setApellidos(apellidos.trim());
        user.setActualizadoEn(LocalDateTime.now());
        userRepository.save(user);

        Map<String, Object> respuesta = buildPerfilResponse(user);
        respuesta.put("message", "Datos Actualizados Correctamente");
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/me/cambiar-password/enviar-codigo")
    public ResponseEntity<?> enviarCodigoCambioPassword() {
        User user = obtenerUsuarioAutenticado();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        ConfiguracionGlobal config = configuracionGlobalRepository.findById(1).orElse(null);
        if (config == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "El sistema no está configurado."));
        }
        String code = String.format("%06d", new Random().nextInt(999999));
        VerificationCode vCode = new VerificationCode();
        vCode.setReferencia(user.getEmail());
        vCode.setCodigo(code);
        vCode.setProposito("recuperacion");
        vCode.setFechaExpiracion(LocalDateTime.now().plusMinutes(2));
        verificationCodeRepository.save(vCode);
        try {
            emailService.enviarCodigoVerificacion(
                    user.getEmail(),
                    code,
                    config.getSmtpEmail(),
                    config.getSmtpContrasenaApp(),
                    EmailService.TipoCodigoCorreo.RECUPERACION_PASSWORD,
                    config.getNombrePlataforma(),
                    user.getId() != null ? user.getId().toString() : null
            );
        } catch (EmailDispatchException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "message",
                    "Error al enviar el correo. Ref: " + e.trackingId() + " (" + e.stage() + ")"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al enviar el correo."));
        }
        return ResponseEntity.ok(Map.of("message", "Código enviado al correo.", "email", user.getEmail()));
    }

    private Map<String, Object> buildPerfilResponse(User user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", user.getId() == null ? null : user.getId().toString());
        out.put("nombres", user.getNombres());
        out.put("apellidos", user.getApellidos());
        out.put("fullName", user.getFullName());
        out.put("phone", user.getTelefono());
        out.put("dni", user.getDni() != null ? user.getDni() : "");
        out.put("email", user.getEmail());
        out.put("role", user.getRole() == null ? null : user.getRole().getName());
        out.put("modoOscuro", user.isModoOscuro());
        solicitudCambioRolRepository.findFirstByUsuarioIdAndEstadoIgnoreCase(user.getId(), "pendiente")
                .ifPresent(s -> {
                    out.put("solicitudPlanEnRevision", true);
                    out.put("solicitudPlanRol", roleRepoNombre(s.getRolSolicitadoId()));
                });
        if (!out.containsKey("solicitudPlanEnRevision")) {
            out.put("solicitudPlanEnRevision", false);
        }
        return out;
    }

    private String roleRepoNombre(Integer rolId) {
        return roleRepository.findById(rolId).map(r -> r.getNombre()).orElse("");
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

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
