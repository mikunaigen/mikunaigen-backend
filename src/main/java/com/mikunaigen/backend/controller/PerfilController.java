package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.model.sql.HistorialTelefono;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.model.sql.VerificationCode;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.repository.sql.HistorialTelefonoRepository;
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
    private final HistorialTelefonoRepository historialTelefonoRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final EmailService emailService;

    public PerfilController(
            UserRepository userRepository,
            ConfiguracionGlobalRepository configuracionGlobalRepository,
            HistorialTelefonoRepository historialTelefonoRepository,
            VerificationCodeRepository verificationCodeRepository,
            EmailService emailService
    ) {
        this.userRepository = userRepository;
        this.configuracionGlobalRepository = configuracionGlobalRepository;
        this.historialTelefonoRepository = historialTelefonoRepository;
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
        String phone = trimToNull(String.valueOf(body.getOrDefault("phone", body.getOrDefault("telefono", ""))));

        if (nombres == null && body.get("fullName") != null) {
            String full = trimToNull(String.valueOf(body.get("fullName")));
            if (full != null) {
                String[] p = full.split("\\s+", 2);
                nombres = p[0];
                apellidos = p.length > 1 ? p[1] : "";
            }
        }

        if (nombres == null || !nombres.matches("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü\\s]+$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Los nombres solo pueden contener letras y tildes."));
        }
        if (apellidos == null || !apellidos.matches("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü\\s]+$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Los apellidos solo pueden contener letras y tildes."));
        }
        if (phone == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El teléfono es obligatorio."));
        }
        String phoneDigits = phone.replaceAll("\\D", "");
        if (phoneDigits.length() != 9 || !phoneDigits.startsWith("9")) {
            return ResponseEntity.badRequest().body(Map.of("message", "El teléfono debe tener 9 dígitos y empezar con 9."));
        }

        if (userRepository.existsByTelefonoAndIdNot(phoneDigits, user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese número de teléfono."));
        }

        boolean telefonoCambio = !phoneDigits.equals(user.getTelefono());
        if (telefonoCambio) {
            HistorialTelefono h = new HistorialTelefono();
            h.setUsuarioId(user.getId());
            h.setNumeroTelefono(user.getTelefono());
            historialTelefonoRepository.save(h);
            user.setTelefono(phoneDigits);
        }

        user.setNombres(nombres);
        user.setApellidos(apellidos);
        user.setActualizadoEn(LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Datos Actualizados Correctamente", "perfil", buildPerfilResponse(user)));
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
        out.put("email", user.getEmail());
        out.put("role", user.getRole() == null ? null : user.getRole().getName());
        out.put("modoOscuro", user.isModoOscuro());
        return out;
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
