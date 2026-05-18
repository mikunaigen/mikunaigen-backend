package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.*;
import com.mikunaigen.backend.repository.sql.*;
import com.mikunaigen.backend.service.EmailService;
import com.mikunaigen.backend.security.JwtService;
import com.mikunaigen.backend.model.nosql.ConfiguracionSistema;
import com.mikunaigen.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.mikunaigen.backend.exception.EmailDispatchException;
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
    @Autowired private LoginAuditRepository loginAuditRepo;
    @Autowired private EmailService emailService;
    @Autowired private ConfiguracionSistemaRepository configRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ShoppingCartService shoppingCartService;
    @Autowired private JwtService jwtService;

    private static final int MAX_INTENTOS_FALLIDOS = 3;
    private static final long BLOQUEO_MINUTOS = 60;

    @GetMapping("/check-admin")
    public ResponseEntity<?> checkAdmin() {
        boolean hasAdmin = userRepo.count() > 0;
        return ResponseEntity.ok(Map.of("hasAdmin", hasAdmin));
    }

    @PostMapping("/enviar-codigo-registro")
    public ResponseEntity<?> enviarCodigo(@RequestBody Map<String, String> request) {
        String email = trimToNull(request.get("email"));
        String dni = trimToNull(request.get("dni"));
        String phone = trimToNull(request.get("phone"));
        String fullName = trimToNull(request.get("fullName"));

        if (email == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El correo electrónico es obligatorio."));
        }

        ResponseEntity<?> duplicado = validarDuplicadosPrevioCodigo(email, dni, phone, fullName);
        if (duplicado != null) return duplicado;
        
        ConfiguracionSistema config = configRepo.findById("GLOBAL_CONFIG").orElse(null);
        if (config == null) 
            return ResponseEntity.badRequest().body(Map.of("message", "El sistema no ha sido configurado aún."));

        return generarYEnviarCodigo(email, config, EmailService.TipoCodigoCorreo.REGISTRO_USUARIO);
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrarUsuario(@RequestBody Map<String, String> data) {
        return registrarUsuarioClienteInterno(data);
    }

    @PostMapping("/registrar-admin")
    public ResponseEntity<?> registrarAdmin(@RequestBody Map<String, String> data) {
        return registrarUsuarioClienteInterno(data);
    }

    @PostMapping("/registrar-cliente")
    public ResponseEntity<?> registrarCliente(@RequestBody Map<String, String> data) {
        return registrarUsuarioClienteInterno(data);
    }

    private ResponseEntity<?> registrarUsuarioClienteInterno(Map<String, String> data) {
        String email = data.get("email");
        String codeIn = data.get("codigo");

        VerificationCode vCode = codeRepo.findFirstByEmailAndUsedOrderByExpirationTimeDesc(email, false).orElse(null);
        if (vCode == null || !vCode.getCode().equals(codeIn)) 
            return ResponseEntity.badRequest().body(Map.of("message", "Código incorrecto."));
        
        if (LocalDateTime.now().isAfter(vCode.getExpirationTime()))
            return ResponseEntity.badRequest().body(Map.of("message", "El código ha expirado."));

        String fullName = trimToNull(data.get("fullName"));
        String dni = trimToNull(data.get("dni"));
        String phone = trimToNull(data.get("phone"));
        String address = trimToNull(data.get("address"));
        if (fullName == null || dni == null || phone == null || address == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todos los campos son obligatorios."));
        }

        ResponseEntity<?> duplicado = validarDuplicadosUsuario(email, dni, phone, fullName);
        if (duplicado != null) return duplicado;

        String password = data.get("password");
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@!¡¿?#$%/&])[A-Za-z\\d@!¡¿?#$%/&]{8,}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña no cumple los requisitos de seguridad."));
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setDni(dni);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setAddress(address);
        user.setFirstLogin(false);

        if (userRepo.count() == 0) {
            user.setRole(roleRepo.findByName("ADMIN").get());
            user.setFirstLogin(false);
        } else {
            user.setRole(roleRepo.findByName("CLIENTE").get());
            user.setFirstLogin(false);
        }

        userRepo.save(user);
        vCode.setUsed(true);
        codeRepo.save(vCode);

        return ResponseEntity.ok(Map.of("message", "Cuenta creada exitosamente."));
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
        if (user == null || user.isDeleted()) {
            registrarAuditoriaLogin(emailAudit, ip, userAgent, "FAILED", "Correo no existe");
            return ResponseEntity.status(401).body(Map.of("message", "El correo electrónico no existe."));
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
        
        ConfiguracionSistema config = configRepo.findById("GLOBAL_CONFIG").orElse(null);
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
        
        ConfiguracionSistema config = configRepo.findById("GLOBAL_CONFIG").orElse(null);
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
            ConfiguracionSistema config,
            EmailService.TipoCodigoCorreo tipo
    ) {
        String code = String.format("%06d", new Random().nextInt(999999));
        VerificationCode vCode = new VerificationCode();
        vCode.setEmail(email);
        vCode.setCode(code);
        vCode.setExpirationTime(LocalDateTime.now().plusMinutes(1));
        codeRepo.save(vCode);

        try {
            emailService.enviarCodigoVerificacion(
                    email,
                    code,
                    config.getEmailSmtp(),
                    config.getPasswordSmtp(),
                    tipo,
                    config.getNombreNegocio(),
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

    private void registrarAuditoriaLogin(
            String userEmail,
            String ipAddress,
            String userAgent,
            String status,
            String failureReason
    ) {
        LoginAudit audit = new LoginAudit();
        audit.setUserEmail(userEmail);
        audit.setIpAddress(ipAddress);
        audit.setUserAgent(userAgent != null ? userAgent : "");
        audit.setStatus(status);
        audit.setFailureReason(failureReason);
        loginAuditRepo.save(audit);
    }
}
