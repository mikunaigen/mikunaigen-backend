package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.*;
import com.mikunaigen.backend.repository.sql.*;
import com.mikunaigen.backend.service.EmailService;
import com.mikunaigen.backend.security.JwtService;
import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.exception.EmailDispatchException;
import com.mikunaigen.backend.service.RegistroTelegramService;
import com.mikunaigen.backend.service.ShoppingCartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private UserRepository userRepo;
    @Autowired private RoleRepository roleRepo;
    @Autowired private VerificationCodeRepository codeRepo;
    @Autowired private IpLoginAttemptRepository ipLoginAttemptRepo;
    @Autowired private EmailService emailService;
    @Autowired private ConfiguracionGlobalRepository configRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ShoppingCartService shoppingCartService;
    @Autowired private JwtService jwtService;
    @Autowired private RegistroTelegramService registroTelegramService;

    private static final int MAX_INTENTOS_FALLIDOS = 3;
    private static final long BLOQUEO_MINUTOS = 60;

    @GetMapping("/check-admin")
    public ResponseEntity<?> checkAdmin() {
        return estadoUsuarios();
    }

    @GetMapping("/estado-usuarios")
    public ResponseEntity<?> estadoUsuarios() {
        boolean hayUsuarios = userRepo.count() > 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hayUsuarios", hayUsuarios);
        body.put("sinUsuarios", !hayUsuarios);
        body.put("hasAdmin", hayUsuarios);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/registrar-pendiente")
    public ResponseEntity<?> registrarPendiente(@RequestBody Map<String, String> data) {
        return registroTelegramService.registrarPendiente(data);
    }

    @PostMapping("/registrar-admin")
    public ResponseEntity<?> registrarAdmin(@RequestBody Map<String, String> data) {
        if (userRepo.count() > 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "El administrador inicial ya fue registrado. Usa inicio de sesión."));
        }
        return registroTelegramService.registrarPendiente(data);
    }

    @GetMapping("/estado-activacion/{userId}")
    public ResponseEntity<?> estadoActivacion(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(registroTelegramService.estadoActivacion(UUID.fromString(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Identificador de usuario inválido."));
        }
    }

    @PostMapping("/renovar-codigo-activacion/{userId}")
    public ResponseEntity<?> renovarCodigoActivacion(@PathVariable String userId) {
        try {
            return registroTelegramService.renovarCodigoActivacion(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Identificador de usuario inválido."));
        }
    }

    @DeleteMapping("/cancelar-registro-pendiente/{userId}")
    public ResponseEntity<?> cancelarRegistroPendiente(@PathVariable String userId) {
        try {
            return registroTelegramService.cancelarRegistroPendiente(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Identificador de usuario inválido."));
        }
    }

    @GetMapping("/info-telegram")
    public ResponseEntity<?> infoTelegram() {
        String bot = registroTelegramService.getBotUsername();
        if (bot.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "El bot de Telegram no está configurado."));
        }
        return ResponseEntity.ok(Map.of("telegramBotUsername", bot));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpServletRequest request) {
        String email = credentials.get("email");
        String password = credentials.get("password");
        String ip = obtenerIpCliente(request);
        String userAgent = request.getHeader("User-Agent");
        String emailAudit = (email == null || email.isBlank()) ? "(sin-email)" : email.trim();

        IpLoginAttempt intentoIp = ipLoginAttemptRepo.findByIpAddress(ip).orElseGet(() -> {
            IpLoginAttempt nuevo = new IpLoginAttempt();
            nuevo.setIpAddress(ip);
            return nuevo;
        });
        limpiarBloqueoExpirado(intentoIp);

        if (estaBloqueada(intentoIp)) {
            registrarAuditoriaLogin(emailAudit, ip, userAgent, "BLOCKED", "IP bloqueada temporalmente");
            return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of(
                    "message", "Tu IP está bloqueada temporalmente por múltiples intentos fallidos.",
                    "ipAddress", ip,
                    "remainingSeconds", segundosRestantes(intentoIp.getBlockedUntil()),
                    "blocked", true
            ));
        }

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) {
            registrarAuditoriaLogin(emailAudit, ip, userAgent, "FAILED", "Correo no existe");
            return ResponseEntity.status(401).body(Map.of("message", "El correo electrónico no existe."));
        }
        if (user.isDeleted()) {
            registrarAuditoriaLogin(emailAudit, ip, userAgent, "FAILED", "Cuenta suspendida");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", "Tu cuenta está suspendida. Contacta al administrador de la plataforma."));
        }

        if ("pendiente".equalsIgnoreCase(user.getEstado())) {
            registrarAuditoriaLogin(emailAudit, ip, userAgent, "FAILED", "Cuenta pendiente de activación");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", "Tu cuenta está pendiente de activación. Completa la verificación con Telegram."));
        }

        if (user.isFirstLogin()) {
            String token = jwtService.generateToken(
                    user.getEmail(),
                    user.getId().toString(),
                    user.getRole().getName()
            );
            Map<String, Object> bodyFirst = new LinkedHashMap<>();
            bodyFirst.put("token", token);
            bodyFirst.put("email", user.getEmail());
            bodyFirst.put("firstLogin", true);
            bodyFirst.put("role", user.getRole().getName());
            bodyFirst.put("darkMode", user.isDarkMode());
            bodyFirst.put("userId", user.getId().toString());
            ShoppingCartService.LoginCartPayload firstCart =
                    shoppingCartService.loadSanitizeAndEnrich(user.getId().toString());
            bodyFirst.put("cart", firstCart.cart());
            bodyFirst.put("removedItems", firstCart.removedItems());
            return ResponseEntity.ok(bodyFirst);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            int fallidos = (intentoIp.getFailedAttempts() == null ? 0 : intentoIp.getFailedAttempts()) + 1;
            intentoIp.setFailedAttempts(fallidos);
            intentoIp.setLastFailedAt(LocalDateTime.now());

            if (fallidos >= MAX_INTENTOS_FALLIDOS) {
                LocalDateTime bloqueadoHasta = LocalDateTime.now().plusMinutes(BLOQUEO_MINUTOS);
                intentoIp.setBlockedUntil(bloqueadoHasta);
                ipLoginAttemptRepo.save(intentoIp);
                registrarAuditoriaLogin(user.getEmail(), ip, userAgent, "BLOCKED", "3 intentos fallidos");
                return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of(
                        "message", "Tu IP ha sido restringida por 1 hora por varios intentos fallidos.",
                        "ipAddress", ip,
                        "remainingSeconds", segundosRestantes(bloqueadoHasta),
                        "failedAttempts", fallidos,
                        "blocked", true
                ));
            }

            ipLoginAttemptRepo.save(intentoIp);
            registrarAuditoriaLogin(user.getEmail(), ip, userAgent, "FAILED", "Contraseña incorrecta");
            return ResponseEntity.status(401).body(Map.of(
                    "message", "Contraseña incorrecta.",
                    "failedAttempts", fallidos,
                    "remainingAttempts", MAX_INTENTOS_FALLIDOS - fallidos,
                    "blocked", false
            ));
        }

        intentoIp.setFailedAttempts(0);
        intentoIp.setLastFailedAt(null);
        intentoIp.setBlockedUntil(null);
        ipLoginAttemptRepo.save(intentoIp);
        registrarAuditoriaLogin(user.getEmail(), ip, userAgent, "SUCCESS", null);

        String token = jwtService.generateToken(
                user.getEmail(),
                user.getId().toString(),
                user.getRole().getName()
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("fullName", user.getFullName());
        body.put("role", user.getRole().getName());
        body.put("email", user.getEmail());
        body.put("firstLogin", false);
        body.put("darkMode", user.isDarkMode());
        body.put("userId", user.getId().toString());
        ShoppingCartService.LoginCartPayload payload = shoppingCartService.loadSanitizeAndEnrich(user.getId().toString());
        body.put("cart", payload.cart());
        body.put("removedItems", payload.removedItems());
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/dark-mode")
    public ResponseEntity<?> updateDarkMode(@RequestBody Map<String, Object> body) {
        Object emailObj = body.get("email");
        Object darkObj = body.get("darkMode");
        if (!(emailObj instanceof String) || emailObj == null || ((String) emailObj).isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email requerido."));
        }
        if (darkObj == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "darkMode requerido."));
        }
        boolean dark = darkObj instanceof Boolean ? (Boolean) darkObj : Boolean.parseBoolean(String.valueOf(darkObj));
        String email = ((String) emailObj).trim();
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null || user.isDeleted()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Usuario no encontrado."));
        }
        user.setDarkMode(dark);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("darkMode", dark));
    }

    @GetMapping("/ip-status")
    public ResponseEntity<?> estadoIp(HttpServletRequest request) {
        String ip = obtenerIpCliente(request);
        IpLoginAttempt intentoIp = ipLoginAttemptRepo.findByIpAddress(ip).orElse(null);
        if (intentoIp == null) {
            return ResponseEntity.ok(Map.of(
                    "blocked", false,
                    "ipAddress", ip,
                    "remainingSeconds", 0
            ));
        }
        limpiarBloqueoExpirado(intentoIp);
        if (!estaBloqueada(intentoIp)) {
            return ResponseEntity.ok(Map.of(
                    "blocked", false,
                    "ipAddress", ip,
                    "remainingSeconds", 0
            ));
        }
        return ResponseEntity.ok(Map.of(
                "blocked", true,
                "ipAddress", ip,
                "remainingSeconds", segundosRestantes(intentoIp.getBlockedUntil())
        ));
    }

    @PostMapping("/crear-empleado")
    public ResponseEntity<?> crearEmpleado(@RequestBody Map<String, String> data) {
        String email = trimToNull(data.get("email"));
        String dni = trimToNull(data.get("dni"));
        String phone = trimToNull(data.get("phone"));
        String fullName = trimToNull(data.get("fullName"));
        String address = trimToNull(data.get("address"));

        if (email == null || dni == null || phone == null || fullName == null || address == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todos los campos son obligatorios."));
        }

        ResponseEntity<?> duplicado = validarDuplicadosUsuario(email, dni, phone, fullName);
        if (duplicado != null) return duplicado;

        User user = new User();
        user.setEmail(email);
        user.setDni(dni);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setAddress(address);
        user.setRole(roleRepo.findByName(data.get("role")).orElseThrow());
        
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); 
        user.setFirstLogin(true);

        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", "Empleado creado. Debe iniciar sesión con su correo para activar la cuenta."));
    }

    @PostMapping("/enviar-codigo-empleado")
    public ResponseEntity<?> enviarCodigoEmpleado(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        User user = userRepo.findByEmail(email).orElse(null);
        
        if (user == null || !user.isFirstLogin()) 
            return ResponseEntity.badRequest().body(Map.of("message", "Acción no permitida para este usuario."));
        
        ConfiguracionGlobal config = configRepo.findById(1).orElse(null);
        if (config == null) return ResponseEntity.internalServerError().body(Map.of("message", "El sistema no está configurado."));

        return generarYEnviarCodigo(email, config, EmailService.TipoCodigoCorreo.ACTIVACION_EMPLEADO);
    }

    @PostMapping("/confirmar-empleado")
    public ResponseEntity<?> confirmarEmpleado(@RequestBody Map<String, String> data) {
        String email = data.get("email");
        String codeIn = data.get("codigo");
        String password = data.get("password");

        VerificationCode vCode = codeRepo.findFirstByEmailAndUsedOrderByExpirationTimeDesc(email, false).orElse(null);
        
        if (vCode == null || !vCode.getCode().equals(codeIn)) return ResponseEntity.badRequest().body(Map.of("message", "Código incorrecto."));
        if (LocalDateTime.now().isAfter(vCode.getExpirationTime())) return ResponseEntity.badRequest().body(Map.of("message", "Código expirado."));

        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@!¡¿?#$%/&])[A-Za-z\\d@!¡¿?#$%/&]{8,}$")) 
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña es débil."));

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.badRequest().body(Map.of("message", "Usuario no encontrado."));

        user.setPassword(passwordEncoder.encode(password));
        user.setFirstLogin(false);
        userRepo.save(user);

        vCode.setUsed(true);
        codeRepo.save(vCode);

        return ResponseEntity.ok(Map.of("message", "Cuenta activada con éxito. Ya puedes ingresar."));
    }

    @PostMapping("/enviar-codigo-recuperacion")
    public ResponseEntity<?> enviarRecuperacion(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (!userRepo.existsByEmail(email)) return ResponseEntity.badRequest().body(Map.of("message", "Correo no registrado."));
        
        ConfiguracionGlobal config = configRepo.findById(1).orElse(null);
        if (config == null) return ResponseEntity.internalServerError().body(Map.of("message", "El sistema no está configurado."));

        return generarYEnviarCodigo(email, config, EmailService.TipoCodigoCorreo.RECUPERACION_PASSWORD);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> data) {
        String email = data.get("email");
        String codeIn = data.get("codigo");
        String newPassword = data.get("newPassword");

        VerificationCode vCode = codeRepo.findFirstByEmailAndUsedOrderByExpirationTimeDesc(email, false).orElse(null);
        if (vCode == null || !vCode.getCode().equals(codeIn)) return ResponseEntity.badRequest().body(Map.of("message", "Código incorrecto."));
        if (LocalDateTime.now().isAfter(vCode.getExpirationTime())) return ResponseEntity.badRequest().body(Map.of("message", "El código ha expirado."));
        if (!newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@!¡¿?#$%/&])[A-Za-z\\d@!¡¿?#$%/&]{8,}$")) return ResponseEntity.badRequest().body(Map.of("message", "La contraseña no cumple los requisitos de seguridad."));

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.badRequest().body(Map.of("message", "Usuario no encontrado."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        vCode.setUsed(true);
        codeRepo.save(vCode);

        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada exitosamente."));
    }

    private ResponseEntity<?> generarYEnviarCodigo(
            String email,
            ConfiguracionGlobal config,
            EmailService.TipoCodigoCorreo tipo
    ) {
        String code = String.format("%06d", new Random().nextInt(999999));
        VerificationCode vCode = new VerificationCode();
        vCode.setReferencia(email);
        vCode.setCodigo(code);
        vCode.setProposito(mapPropositoCodigo(tipo));
        vCode.setExpirationTime(LocalDateTime.now().plusMinutes(2));
        codeRepo.save(vCode);

        try {
            emailService.enviarCodigoVerificacion(
                    email,
                    code,
                    config.getSmtpEmail(),
                    config.getSmtpContrasenaApp(),
                    tipo,
                    config.getNombrePlataforma(),
                    null
            );
            return ResponseEntity.ok(Map.of("message", "Código enviado al correo."));
        } catch (EmailDispatchException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "message",
                    "Error al enviar el correo. Ref: " + e.trackingId() + " (" + e.stage() + ")"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error al enviar el correo."));
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean parseBoolean(String value) {
        if (value == null) return false;
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    private static String normalizarNombreCompleto(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private boolean existeNombreCompletoDuplicado(String fullNamePropuesto) {
        String n = normalizarNombreCompleto(fullNamePropuesto);
        if (n.isEmpty()) return false;
        return userRepo.findAll().stream()
                .anyMatch(u -> normalizarNombreCompleto(u.getFullName()).equals(n));
    }

    private ResponseEntity<?> validarDuplicadosUsuario(String email, String dni, String phone, String fullName) {
        if (userRepo.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese correo electrónico."));
        }
        if (userRepo.existsByDni(dni)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese DNI."));
        }
        if (userRepo.existsByPhone(phone)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese número de teléfono."));
        }
        if (existeNombreCompletoDuplicado(fullName)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con el mismo nombre y apellidos."));
        }
        return null;
    }

    private ResponseEntity<?> validarDuplicadosPrevioCodigo(String email, String dni, String phone, String fullName) {
        if (userRepo.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese correo electrónico."));
        }
        if (dni != null && userRepo.existsByDni(dni)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese DNI."));
        }
        if (phone != null && userRepo.existsByPhone(phone)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese número de teléfono."));
        }
        if (fullName != null && existeNombreCompletoDuplicado(fullName)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con el mismo nombre y apellidos."));
        }
        return null;
    }

    private static boolean estaBloqueada(IpLoginAttempt intentoIp) {
        LocalDateTime blockedUntil = intentoIp.getBlockedUntil();
        return blockedUntil != null && LocalDateTime.now().isBefore(blockedUntil);
    }

    private static long segundosRestantes(LocalDateTime blockedUntil) {
        if (blockedUntil == null) return 0;
        long s = ChronoUnit.SECONDS.between(LocalDateTime.now(), blockedUntil);
        return Math.max(0, s);
    }

    private static String obtenerIpCliente(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }

    private void limpiarBloqueoExpirado(IpLoginAttempt intentoIp) {
        LocalDateTime blockedUntil = intentoIp.getBlockedUntil();
        if (blockedUntil == null) return;
        if (LocalDateTime.now().isBefore(blockedUntil)) return;
        intentoIp.setFailedAttempts(0);
        intentoIp.setLastFailedAt(null);
        intentoIp.setBlockedUntil(null);
        ipLoginAttemptRepo.save(intentoIp);
    }

    private static String mapPropositoCodigo(EmailService.TipoCodigoCorreo tipo) {
        return switch (tipo) {
            case RECUPERACION_PASSWORD -> "recuperacion";
            case REGISTRO_USUARIO -> "registro_telegram";
            case SETUP_SMTP -> "smtp_test";
            default -> "recuperacion";
        };
    }

    private void registrarAuditoriaLogin(
            String userEmail,
            String ipAddress,
            String userAgent,
            String status,
            String failureReason
    ) {
    }
}
