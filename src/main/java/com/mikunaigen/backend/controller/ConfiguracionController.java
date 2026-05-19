package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.exception.EmailDispatchException;
import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.model.sql.VerificationCode;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.repository.sql.VerificationCodeRepository;
import com.mikunaigen.backend.service.EmailService;
import com.mikunaigen.backend.util.ConfiguracionPlataformaMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping("/api/configuracion")
public class ConfiguracionController {

    @Autowired
    private ConfiguracionGlobalRepository configRepo;

    @Autowired
    private VerificationCodeRepository codeSqlRepo;

    @Autowired
    private EmailService emailService;

    @GetMapping("/estado")
    public ResponseEntity<Map<String, Boolean>> estadoConfiguracion() {
        boolean completa = configRepo.findById(1)
                .map(ConfiguracionGlobal::isConfiguracionCompleta)
                .orElse(false);
        return ResponseEntity.ok(Map.of("configuracionCompleta", completa));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> obtenerConfiguracion() {
        return ResponseEntity.ok(ConfiguracionPlataformaMapper.aMapaPublico(
                configRepo.findById(1).orElse(null)));
    }

    @PostMapping("/enviar-verificacion")
    public ResponseEntity<?> enviarVerificacion(@RequestBody Map<String, String> request) {
        String email = request.get("emailSmtp");
        String pass = request.get("passwordSmtp");

        if (email == null || !email.endsWith("@gmail.com")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debe ser un correo @gmail.com"));
        }

        if (pass == null || pass.length() != 16) {
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña de aplicación de Google debe tener 16 caracteres"));
        }

        String numCode = String.format("%06d", new Random().nextInt(999999));
        VerificationCode vCode = new VerificationCode();
        vCode.setReferencia(email);
        vCode.setCodigo(numCode);
        vCode.setProposito("smtp_test");
        vCode.setFechaExpiracion(LocalDateTime.now().plusMinutes(2));
        codeSqlRepo.save(vCode);

        try {
            emailService.enviarCodigoVerificacion(
                    email,
                    numCode,
                    email,
                    pass,
                    EmailService.TipoCodigoCorreo.SETUP_SMTP,
                    "Mikunaigen",
                    null);
            return ResponseEntity.ok(Map.of("message", "Código enviado a " + email));
        } catch (EmailDispatchException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "message",
                    "Error al enviar el correo. Ref: " + e.trackingId() + " (" + e.stage() + ")"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "No se pudo enviar el correo. Verifica tu contraseña de aplicación."));
        }
    }

    @PostMapping("/validar-y-guardar")
    public ResponseEntity<?> validarYGuardar(@RequestBody Map<String, Object> data) {
        String email = (String) data.get("emailSmtp");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El correo SMTP es obligatorio."));
        }

        String newPassRaw = (String) data.get("passwordSmtp");
        if (newPassRaw != null && newPassRaw.isBlank()) {
            newPassRaw = null;
        }

        ConfiguracionGlobal existing = configRepo.findById(1).orElse(null);
        boolean primeraConfig = existing == null || !existing.isConfiguracionCompleta();
        boolean requiereCodigo;
        if (primeraConfig) {
            requiereCodigo = true;
            if (newPassRaw == null || newPassRaw.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "La contraseña de aplicación de Google es obligatoria en la primera configuración."));
            }
        } else {
            boolean emailCambiado = existing.getSmtpEmail() == null || !Objects.equals(existing.getSmtpEmail(), email);
            boolean passNuevo = newPassRaw != null && !newPassRaw.isEmpty();
            requiereCodigo = emailCambiado || passNuevo;
        }

        if (requiereCodigo) {
            String codeIn = (String) data.get("codigoVerificacion");
            if (codeIn == null || codeIn.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Debes ingresar el código de verificación enviado al correo SMTP."));
            }

            VerificationCode vCode = codeSqlRepo.findFirstByReferenciaAndUsadoOrderByFechaExpiracionDesc(email, false)
                    .orElse(null);

            if (vCode == null || !vCode.getCode().equals(codeIn)) {
                return ResponseEntity.badRequest().body(Map.of("message", "El código ingresado es incorrecto."));
            }

            if (LocalDateTime.now().isAfter(vCode.getExpirationTime())) {
                return ResponseEntity.badRequest().body(Map.of("message", "El código ha expirado. Solicita uno nuevo."));
            }

            vCode.setUsed(true);
            codeSqlRepo.save(vCode);
        }

        ConfiguracionGlobal config = existing != null ? existing : new ConfiguracionGlobal();
        config.setId(1);
        config.setSmtpEmail(email);
        if (newPassRaw != null && !newPassRaw.isEmpty()) {
            config.setSmtpContrasenaApp(newPassRaw);
        } else if (existing != null && existing.getSmtpContrasenaApp() != null) {
            config.setSmtpContrasenaApp(existing.getSmtpContrasenaApp());
        } else if (primeraConfig) {
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña de aplicación es obligatoria."));
        }

        String nombre = (String) data.get("nombreNegocio");
        if (nombre == null || nombre.isBlank()) {
            nombre = (String) data.get("nombrePlataforma");
        }
        config.setNombrePlataforma(nombre != null ? nombre : "Mikunaigen");
        config.setTelefonoContacto((String) data.get("telefonoNegocio"));

        Object mpRaw = data.get("mediosPago");
        if (mpRaw instanceof Map<?, ?> mp) {
            config.setNumeroYape(safeStr(mp.get("yapeTelefono")));
            config.setNumeroPlin(safeStr(mp.get("plinTelefono")));
            Object transferencias = mp.get("transferencias");
            if (transferencias instanceof java.util.List<?> lista && !lista.isEmpty() && lista.get(0) instanceof Map<?, ?> b) {
                config.setBancoNombre(safeStr(b.get("banco")));
                config.setCuentaBancaria(safeStr(b.get("numeroCuenta")));
                config.setCci(safeStr(b.get("cci")));
            }
        }

        String logo = (String) data.get("logoBase64");
        if (logo != null && !logo.isBlank()) {
            config.setLogoBytea(ConfiguracionPlataformaMapper.decodificarLogo(logo));
        } else if (existing != null && existing.getLogoBytea() != null) {
            config.setLogoBytea(existing.getLogoBytea());
        }

        boolean tieneYape = config.getNumeroYape() != null && !config.getNumeroYape().isBlank();
        boolean tienePlin = config.getNumeroPlin() != null && !config.getNumeroPlin().isBlank();
        boolean tieneTransferencia = config.getBancoNombre() != null && !config.getBancoNombre().isBlank()
                && config.getCuentaBancaria() != null && !config.getCuentaBancaria().isBlank()
                && config.getCci() != null && !config.getCci().isBlank();
        if (!tieneYape && !tienePlin && !tieneTransferencia) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Debe activar y completar al menos un medio de pago (Yape, Plin o transferencia)."));
        }

        config.setSmtpEstado("activo");
        config.setSmtpFechaConfiguracion(LocalDateTime.now());
        config.setSmtpCredencialesInvalidas(false);
        config.setActualizadoEn(LocalDateTime.now());
        if (primeraConfig || !config.isSetupCompletado()) {
            if (config.isConfiguracionCompleta()) {
                config.setSetupCompletado(true);
            }
        }
        configRepo.save(config);

        return ResponseEntity.ok(Map.of(
                "message", "Configuración guardada con éxito.",
                "configuracionCompleta", config.isConfiguracionCompleta()));
    }

    @PostMapping("/plataforma")
    public ResponseEntity<?> guardarPlataforma(@RequestBody Map<String, Object> data) {
        String nombre = safeStr(data.get("nombrePlataforma"));
        if (nombre.isBlank()) {
            nombre = safeStr(data.get("nombreNegocio"));
        }
        String telefono = safeStr(data.get("telefonoContacto"));
        if (telefono.isBlank()) {
            telefono = safeStr(data.get("telefonoNegocio"));
        }
        String yape = safeStr(data.get("numeroYape"));
        String plin = safeStr(data.get("numeroPlin"));
        String banco = safeStr(data.get("bancoNombre"));
        String cuenta = safeStr(data.get("cuentaBancaria"));
        String cci = safeStr(data.get("cci"));
        String logo = (String) data.get("logoBase64");

        if (nombre.isBlank() || telefono.isBlank() || yape.isBlank() || plin.isBlank()
                || banco.isBlank() || cuenta.isBlank() || cci.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todos los campos son obligatorios."));
        }
        if (!yape.matches("^9\\d{8}$") || !plin.matches("^9\\d{8}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Yape y Plin deben tener 9 dígitos y empezar en 9."));
        }
        if (!cuenta.matches("^\\d{1,20}$") || !cci.matches("^\\d{1,20}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cuenta y CCI deben ser numéricos de hasta 20 dígitos."));
        }

        ConfiguracionGlobal config = configRepo.findById(1).orElse(new ConfiguracionGlobal());
        config.setId(1);
        config.setNombrePlataforma(nombre);
        config.setTelefonoContacto(telefono);
        config.setNumeroYape(yape);
        config.setNumeroPlin(plin);
        config.setBancoNombre(banco);
        config.setCuentaBancaria(cuenta);
        config.setCci(cci);
        if (logo != null && !logo.isBlank()) {
            byte[] bytes = ConfiguracionPlataformaMapper.decodificarLogo(logo);
            if (bytes != null && bytes.length > 2 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("message", "El logo no debe superar 2 MB."));
            }
            config.setLogoBytea(bytes);
        }
        config.setActualizadoEn(LocalDateTime.now());
        if (config.isConfiguracionCompleta()) {
            config.setSetupCompletado(true);
        }
        configRepo.save(config);
        return ResponseEntity.ok(Map.of(
                "message", "Configuración de la plataforma guardada correctamente.",
                "configuracionCompleta", config.isConfiguracionCompleta()));
    }

    private String safeStr(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
