package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.Role;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.model.sql.VerificationCode;
import com.mikunaigen.backend.repository.sql.RoleRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.repository.sql.VerificationCodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RegistroTelegramService {

    public static final String CODIGO_PREFIJO = "MIKUNA-VALTEL-";
    public static final String MSG_TELEFONO_NO_COINCIDE = "Esta cuenta de telegram no coincide con el número ingresado";
    /** Debe coincidir con el CHECK de PostgreSQL en codigos_verificacion.proposito */
    public static final String PROPOSITO_ACTIVACION = "registro_telegram";
    private static final int ACTIVACION_MINUTOS = 2;
    private static final Pattern CODIGO_PATTERN = Pattern.compile("MIKUNA-VALTEL-(\\d{6})", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final VerificationCodeRepository codeRepo;
    private final PasswordEncoder passwordEncoder;
    private final RegistroActivacionPushService pushService;
    private final ConcurrentHashMap<String, ActivacionPendiente> activacionesPendientes = new ConcurrentHashMap<>();

    @Value("${telegram.bot.username:}")
    private String botUsername;

    public RegistroTelegramService(
            UserRepository userRepo,
            RoleRepository roleRepo,
            VerificationCodeRepository codeRepo,
            PasswordEncoder passwordEncoder,
            RegistroActivacionPushService pushService
    ) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.codeRepo = codeRepo;
        this.passwordEncoder = passwordEncoder;
        this.pushService = pushService;
    }

    public String getBotUsername() {
        return botUsername == null ? "" : botUsername.trim();
    }

    @Transactional
    public ResponseEntity<?> registrarPendiente(Map<String, String> data) {
        String fullName = trimToNull(data.get("fullName"));
        String dni = trimToNull(data.get("dni"));
        String phone = trimToNull(data.get("phone"));
        String email = trimToNull(data.get("email"));
        String password = data.get("password");

        if (fullName == null || phone == null || email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todos los campos obligatorios deben completarse."));
        }

        if (!parseBoolean(data.get("aceptoTerminos"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes aceptar los términos y condiciones de uso."));
        }
        if (!parseBoolean(data.get("aceptoDescargo"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes aceptar el descargo de responsabilidad."));
        }

        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@!¡¿?#$%/&])[A-Za-z\\d@!¡¿?#$%/&]{8,}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña no cumple los requisitos de seguridad."));
        }

        Optional<User> existenteOpt = userRepo.findByEmailIgnoreCase(email);
        User user;
        boolean primerUsuario = userRepo.count() == 0;

        if (existenteOpt.isPresent()) {
            user = existenteOpt.get();
            if ("activo".equalsIgnoreCase(user.getEstado())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese correo electrónico."));
            }
            if (!"pendiente".equalsIgnoreCase(user.getEstado())) {
                return ResponseEntity.badRequest().body(Map.of("message", "El correo no puede usarse para un nuevo registro."));
            }
            ResponseEntity<?> duplicado = validarDuplicadosParaActualizacion(email, dni, phone, fullName, user.getId());
            if (duplicado != null) {
                return duplicado;
            }
        } else {
            ResponseEntity<?> duplicado = validarDuplicadosNuevo(email, dni, phone, fullName);
            if (duplicado != null) {
                return duplicado;
            }
            user = new User();
            user.setEmail(email);
        }

        user.setFullName(fullName);
        user.setDni(dni);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setAceptoTerminos(true);
        user.setAceptoDescargo(true);
        user.setEstado("pendiente");
        user.setTelegramId(null);
        user.setFirstLogin(false);

        Role rol;
        if (primerUsuario) {
            rol = roleRepo.findByNombre("administrador").orElseGet(() ->
                    roleRepo.findByName("ADMIN").orElseThrow());
        } else {
            rol = roleRepo.findByNombre("estudiante").orElseGet(() ->
                    roleRepo.findByName("CLIENTE").orElseThrow());
        }
        user.setRole(rol);
        user.setActualizadoEn(LocalDateTime.now());
        user = userRepo.save(user);

        invalidarCodigosActivacion(user.getId());
        VerificationCode vCode = crearCodigoActivacion(user.getId());

        return ResponseEntity.ok(construirRespuestaRegistro(user, vCode, primerUsuario));
    }

    @Transactional
    public ResponseEntity<?> cancelarRegistroPendiente(UUID userId) {
        Optional<User> userOpt = userRepo.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No había registro pendiente activo."));
        }

        User user = userOpt.get();
        if (!"pendiente".equalsIgnoreCase(user.getEstado())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Solo se puede cancelar una cuenta en estado pendiente."));
        }

        codeRepo.deleteByReferencia(userId.toString());
        activacionesPendientes.entrySet().removeIf(e -> userId.equals(e.getValue().userId()));
        userRepo.delete(user);

        return ResponseEntity.ok(Map.of(
                "message", "Registro pendiente eliminado. Puedes registrarte de nuevo con tus datos actualizados."));
    }

    @Transactional
    public ResponseEntity<?> renovarCodigoActivacion(UUID userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "No se encontró la cuenta de registro."));
        }
        if (!"pendiente".equalsIgnoreCase(user.getEstado())) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cuenta ya no está pendiente de activación."));
        }

        invalidarCodigosActivacion(userId);
        activacionesPendientes.keySet().removeIf(k -> {
            ActivacionPendiente p = activacionesPendientes.get(k);
            return p != null && userId.equals(p.userId());
        });

        VerificationCode vCode = crearCodigoActivacion(userId);
        Map<String, Object> body = construirRespuestaRegistro(user, vCode, false);
        body.put("message", "Se generó un nuevo código de activación. Tienes 2 minutos para validarlo.");
        return ResponseEntity.ok(body);
    }

    public Map<String, Object> estadoActivacion(UUID userId) {
        Optional<User> userOpt = userRepo.findById(userId);
        if (userOpt.isEmpty()) {
            return Map.of("estado", "no_encontrado", "activo", false, "codigoVigente", false);
        }

        User user = userOpt.get();
        boolean activo = "activo".equalsIgnoreCase(user.getEstado());
        LocalDateTime now = LocalDateTime.now();

        Optional<VerificationCode> codeOpt = codeRepo
                .findFirstByReferenciaAndPropositoAndUsadoOrderByFechaExpiracionDesc(
                        userId.toString(), PROPOSITO_ACTIVACION, false);

        boolean codigoVigente = false;
        long segundosRestantes = 0;
        String digitos = "";

        if (codeOpt.isPresent()) {
            VerificationCode code = codeOpt.get();
            digitos = code.getCodigo();
            if (!now.isAfter(code.getFechaExpiracion())) {
                codigoVigente = true;
                segundosRestantes = ChronoUnit.SECONDS.between(now, code.getFechaExpiracion());
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId().toString());
        body.put("estado", user.getEstado() != null ? user.getEstado() : "pendiente");
        body.put("activo", activo);
        body.put("codigoVigente", codigoVigente);
        body.put("codigoExpirado", !activo && !codigoVigente);
        body.put("segundosRestantes", Math.max(0, segundosRestantes));
        body.put("digitosCodigo", digitos);
        body.put("prefijoCodigo", CODIGO_PREFIJO);
        return body;
    }

    @Transactional
    public void procesarActivacionTelegram(
            String startPayload,
            String telegramUserId,
            String telefonoTelegram,
            Long chatId,
            AbsSender bot
    ) {
        String digitos = extraerDigitosCodigo(startPayload);
        if (digitos == null) {
            enviarMensaje(bot, chatId,
                    "Envía el código de activación. Usa el enlace «Verificar mi cuenta con Telegram» en la plataforma.",
                    false);
            return;
        }

        ContextoActivacion ctx = resolverContextoActivacion(digitos, telegramUserId, chatId, bot);
        if (ctx == null) {
            return;
        }

        if (telefonoTelegram != null && !telefonoTelegram.isBlank()) {
            if (!telefonosCoinciden(ctx.user().getPhone(), telefonoTelegram)) {
                rechazarTelefonoNoCoincide(ctx, bot);
                return;
            }
            completarActivacion(ctx, bot);
            return;
        }

        activacionesPendientes.put(telegramUserId, new ActivacionPendiente(ctx.user().getId(), digitos, chatId));
        enviarMensaje(bot, chatId,
                "Para verificar que eres el titular del número registrado, comparte tu contacto de Telegram "
                        + "con el botón de abajo.",
                true);
    }

    @Transactional
    public void procesarContactoTelegram(
            String telegramUserId,
            String telefonoContacto,
            Long chatId,
            AbsSender bot
    ) {
        ActivacionPendiente pendiente = activacionesPendientes.get(telegramUserId);
        if (pendiente == null) {
            enviarMensaje(bot, chatId,
                    "Primero abre el enlace de activación desde la plataforma web (botón «Verificar mi cuenta con Telegram»).",
                    false);
            return;
        }

        ContextoActivacion ctx = resolverContextoActivacion(pendiente.digitos(), telegramUserId, chatId, bot);
        if (ctx == null) {
            activacionesPendientes.remove(telegramUserId);
            return;
        }

        if (telefonoContacto == null || telefonoContacto.isBlank()) {
            enviarMensaje(bot, chatId, "No se pudo leer el número del contacto. Intenta compartirlo nuevamente.", true);
            return;
        }

        if (!telefonosCoinciden(ctx.user().getPhone(), telefonoContacto)) {
            rechazarTelefonoNoCoincide(ctx, bot);
            activacionesPendientes.remove(telegramUserId);
            return;
        }

        activacionesPendientes.remove(telegramUserId);
        completarActivacion(ctx, bot);
    }

    private void rechazarTelefonoNoCoincide(ContextoActivacion ctx, AbsSender bot) {
        enviarMensaje(bot, ctx.chatId(), MSG_TELEFONO_NO_COINCIDE, false);
        pushService.notificarTelefonoNoCoincide(ctx.user().getId());
    }

    private void completarActivacion(ContextoActivacion ctx, AbsSender bot) {
        User user = ctx.user();
        VerificationCode vCode = ctx.vCode();

        Optional<User> otroConTelegram = userRepo.findByTelegramId(ctx.telegramUserId());
        if (otroConTelegram.isPresent()
                && !otroConTelegram.get().getId().equals(user.getId())
                && "activo".equalsIgnoreCase(otroConTelegram.get().getEstado())) {
            enviarMensaje(bot, ctx.chatId(),
                    "Este Telegram ya está vinculado a otra cuenta activa. No se puede completar la activación.",
                    false);
            return;
        }

        user.setTelegramId(ctx.telegramUserId());
        user.setEstado("activo");
        user.setActualizadoEn(LocalDateTime.now());
        userRepo.save(user);

        vCode.setUsado(true);
        codeRepo.save(vCode);
        activacionesPendientes.entrySet().removeIf(e -> user.getId().equals(e.getValue().userId()));

        enviarMensaje(bot, ctx.chatId(),
                "¡Cuenta activada correctamente! Ya puedes iniciar sesión en Mikunaigen con tu correo y contraseña.",
                false);

        pushService.notificarActivacion(user.getId());
    }

    private ContextoActivacion resolverContextoActivacion(
            String digitos,
            String telegramUserId,
            Long chatId,
            AbsSender bot
    ) {
        VerificationCode vCode = codeRepo
                .findFirstByCodigoAndPropositoAndUsadoOrderByFechaExpiracionDesc(digitos, PROPOSITO_ACTIVACION, false)
                .orElse(null);

        if (vCode == null) {
            enviarMensaje(bot, chatId, "El código de activación no es válido o ya fue utilizado.", false);
            return null;
        }

        if (LocalDateTime.now().isAfter(vCode.getFechaExpiracion())) {
            enviarMensaje(bot, chatId,
                    "El código de activación expiró. Solicita un nuevo código en la plataforma web.",
                    false);
            return null;
        }

        UUID userId;
        try {
            userId = UUID.fromString(vCode.getReferencia());
        } catch (IllegalArgumentException e) {
            enviarMensaje(bot, chatId, "No se pudo validar el código de activación.", false);
            return null;
        }

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            enviarMensaje(bot, chatId, "No se encontró la cuenta asociada a este código.", false);
            return null;
        }

        if ("activo".equalsIgnoreCase(user.getEstado())) {
            enviarMensaje(bot, chatId, "Esta cuenta ya está activa. Puedes iniciar sesión en la plataforma.", false);
            return null;
        }

        return new ContextoActivacion(user, vCode, telegramUserId, chatId);
    }

    private VerificationCode crearCodigoActivacion(UUID userId) {
        String digitos = String.format("%06d", new Random().nextInt(1_000_000));
        VerificationCode vCode = new VerificationCode();
        vCode.setReferencia(userId.toString());
        vCode.setCodigo(digitos);
        vCode.setProposito(PROPOSITO_ACTIVACION);
        vCode.setUsado(false);
        vCode.setFechaExpiracion(LocalDateTime.now().plusMinutes(ACTIVACION_MINUTOS));
        return codeRepo.save(vCode);
    }

    private void invalidarCodigosActivacion(UUID userId) {
        codeRepo.findFirstByReferenciaAndPropositoAndUsadoOrderByFechaExpiracionDesc(
                        userId.toString(), PROPOSITO_ACTIVACION, false)
                .ifPresent(c -> {
                    c.setUsado(true);
                    codeRepo.save(c);
                });
    }

    private Map<String, Object> construirRespuestaRegistro(User user, VerificationCode vCode, boolean primerUsuario) {
        String digitos = vCode.getCodigo();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Registro pendiente. Activa tu cuenta con Telegram en los próximos 2 minutos.");
        body.put("userId", user.getId().toString());
        body.put("codigoActivacion", CODIGO_PREFIJO + digitos);
        body.put("prefijoCodigo", CODIGO_PREFIJO);
        body.put("digitosCodigo", digitos);
        body.put("telegramBotUsername", getBotUsername());
        body.put("primerUsuario", primerUsuario);
        body.put("role", user.getRole().getName());
        body.put("minutosValidez", ACTIVACION_MINUTOS);
        body.put("segundosRestantes", ACTIVACION_MINUTOS * 60L);
        body.put("codigoVigente", true);
        return body;
    }

    static boolean telefonosCoinciden(String telefonoRegistrado, String telefonoTelegram) {
        String a = normalizarTelefonoPeru(telefonoRegistrado);
        String b = normalizarTelefonoPeru(telefonoTelegram);
        return !a.isEmpty() && a.equals(b);
    }

    static String normalizarTelefonoPeru(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("51") && digits.length() > 9) {
            digits = digits.substring(2);
        }
        if (digits.length() > 9) {
            digits = digits.substring(digits.length() - 9);
        }
        return digits;
    }

    private String extraerDigitosCodigo(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("/start")) {
            t = t.substring(6).trim();
        }
        Matcher m = CODIGO_PATTERN.matcher(t);
        if (m.find()) {
            return m.group(1);
        }
        if (t.matches("\\d{6}")) {
            return t;
        }
        return null;
    }

    private void enviarMensaje(AbsSender bot, Long chatId, String texto, boolean solicitarContacto) {
        if (bot == null || chatId == null) {
            return;
        }
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(texto);
        if (solicitarContacto) {
            KeyboardButton btn = new KeyboardButton("Compartir mi número de Telegram");
            btn.setRequestContact(true);
            KeyboardRow row = new KeyboardRow();
            row.add(btn);
            ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
            keyboard.setKeyboard(List.of(row));
            keyboard.setResizeKeyboard(true);
            keyboard.setOneTimeKeyboard(true);
            msg.setReplyMarkup(keyboard);
        } else {
            msg.setReplyMarkup(new ReplyKeyboardRemove(true));
        }
        try {
            bot.execute(msg);
        } catch (TelegramApiException ignored) {
        }
    }

    private ResponseEntity<?> validarDuplicadosNuevo(String email, String dni, String phone, String fullName) {
        if (userRepo.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese correo electrónico."));
        }
        if (dni != null && userRepo.existsByDni(dni)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese DNI."));
        }
        if (userRepo.existsByTelefono(phone)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese número de teléfono."));
        }
        if (existeNombreCompletoDuplicado(fullName)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con el mismo nombre y apellidos."));
        }
        return null;
    }

    private ResponseEntity<?> validarDuplicadosParaActualizacion(
            String email,
            String dni,
            String phone,
            String fullName,
            UUID userId
    ) {
        if (userRepo.existsByTelefonoAndIdNot(phone, userId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese número de teléfono."));
        }
        if (existeNombreCompletoDuplicado(fullName, userId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con el mismo nombre y apellidos."));
        }
        return null;
    }

    private boolean existeNombreCompletoDuplicado(String fullName) {
        return existeNombreCompletoDuplicado(fullName, null);
    }

    private boolean existeNombreCompletoDuplicado(String fullName, UUID excludeId) {
        String n = normalizarNombreCompleto(fullName);
        if (n.isEmpty()) {
            return false;
        }
        return userRepo.findAll().stream()
                .filter(u -> excludeId == null || !u.getId().equals(excludeId))
                .anyMatch(u -> normalizarNombreCompleto(u.getFullName()).equals(n));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    private static String normalizarNombreCompleto(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record ActivacionPendiente(UUID userId, String digitos, Long chatId) {}

    private record ContextoActivacion(User user, VerificationCode vCode, String telegramUserId, Long chatId) {}
}
