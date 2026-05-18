package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.nosql.ConfiguracionSistema;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.model.sql.VerificationCode;
import com.mikunaigen.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.mikunaigen.backend.repository.sql.RestaurantOrderRepository;
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

    private static final Set<String> ESTADOS_BLOQUEO_DIRECCION_CLIENTE = Set.of(
            "VALIDANDO_PAGO",
            "PAGO_VALIDADO",
            "EN_COCINA",
            "PREPARADO",
            "EN_CAMINO"
    );

    private final UserRepository userRepository;
    private final RestaurantOrderRepository restaurantOrderRepository;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final EmailService emailService;

    public PerfilController(
            UserRepository userRepository,
            RestaurantOrderRepository restaurantOrderRepository,
            ConfiguracionSistemaRepository configuracionSistemaRepository,
            VerificationCodeRepository verificationCodeRepository,
            EmailService emailService
    ) {
        this.userRepository = userRepository;
        this.restaurantOrderRepository = restaurantOrderRepository;
        this.configuracionSistemaRepository = configuracionSistemaRepository;
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
    public ResponseEntity<?> actualizarPerfil(
            @RequestBody Map<String, Object> body
    ) {
        User user = obtenerUsuarioAutenticado();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }

        String fullName = trimToNull(String.valueOf(body.getOrDefault("fullName", "")));
        String phone = trimToNull(String.valueOf(body.getOrDefault("phone", "")));
        String address = trimToNull(String.valueOf(body.getOrDefault("address", "")));

        if (fullName == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nombres y apellidos es obligatorio."));
        }
        if (phone == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El teléfono es obligatorio."));
        }
        if (address == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "La dirección es obligatoria."));
        }
        String phoneDigits = phone.replaceAll("\\D", "");
        if (phoneDigits.length() != 9 || !phoneDigits.startsWith("9")) {
            return ResponseEntity.badRequest().body(Map.of("message", "El teléfono debe tener 9 dígitos y empezar con 9."));
        }

        boolean canEditAddress = !bloquearEdicionDireccionCliente(user);
        if (!canEditAddress && !Objects.equals(normalizar(user.getAddress()), normalizar(address))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message",
                    "No puedes editar la dirección mientras tengas pedidos en curso."
            ));
        }

        if (userRepository.existsByPhoneAndIdNot(phoneDigits, user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese número de teléfono."));
        }
        if (existeNombreCompletoDuplicado(fullName, user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con esos nombres y apellidos."));
        }

        user.setFullName(fullName);
        user.setPhone(phoneDigits);
        if (canEditAddress) {
            user.setAddress(address);
        }
        userRepository.save(user);
        return ResponseEntity.ok(buildPerfilResponse(user));
    }

    @PostMapping("/me/cambiar-password/enviar-codigo")
    public ResponseEntity<?> enviarCodigoCambioPassword() {
        User user = obtenerUsuarioAutenticado();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        ConfiguracionSistema config = configuracionSistemaRepository.findById("GLOBAL_CONFIG").orElse(null);
        if (config == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "El sistema no está configurado."));
        }
        String code = String.format("%06d", new Random().nextInt(999999));
        VerificationCode vCode = new VerificationCode();
        vCode.setEmail(user.getEmail());
        vCode.setCode(code);
        vCode.setExpirationTime(LocalDateTime.now().plusMinutes(1));
        vCode.setUsed(false);
        verificationCodeRepository.save(vCode);
        try {
            emailService.enviarCodigoVerificacion(
                    user.getEmail(),
                    code,
                    config.getEmailSmtp(),
                    config.getPasswordSmtp(),
                    EmailService.TipoCodigoCorreo.RECUPERACION_PASSWORD,
                    config.getNombreNegocio(),
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
        return ResponseEntity.ok(Map.of(
                "message", "Código enviado al correo.",
                "email", user.getEmail()
        ));
    }

    private Map<String, Object> buildPerfilResponse(User user) {
        boolean canEditAddress = !bloquearEdicionDireccionCliente(user);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", user.getId() == null ? null : user.getId().toString());
        out.put("fullName", user.getFullName());
        out.put("phone", user.getPhone());
        out.put("address", user.getAddress());
        out.put("dni", user.getDni());
        out.put("email", user.getEmail());
        out.put("role", user.getRole() == null ? null : user.getRole().getName());
        out.put("canEditAddress", canEditAddress);
        return out;
    }

    private User obtenerUsuarioAutenticado() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof String email) || email.isBlank()) return null;
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.isDeleted()) return null;
        return user;
    }

    private boolean bloquearEdicionDireccionCliente(User user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        return restaurantOrderRepository.existsByClient_IdAndStatusIn(user.getId(), ESTADOS_BLOQUEO_DIRECCION_CLIENTE);
    }

    private boolean existeNombreCompletoDuplicado(String fullName, UUID ownId) {
        String normalized = normalizar(fullName);
        if (normalized.isBlank()) return false;
        return userRepository.findAll().stream()
                .filter(u -> !Boolean.TRUE.equals(u.isDeleted()))
                .filter(u -> u.getId() != null && !u.getId().equals(ownId))
                .anyMatch(u -> normalizar(u.getFullName()).equals(normalized));
    }

    private String normalizar(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
