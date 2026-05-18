package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.nosql.ConfiguracionSistema;
import com.mikunaigen.backend.model.sql.VerificationCode;
import com.mikunaigen.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.mikunaigen.backend.repository.sql.VerificationCodeRepository;
import com.mikunaigen.backend.exception.EmailDispatchException;
import com.mikunaigen.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;


@RestController
@RequestMapping("/api/configuracion")
public class ConfiguracionController {

    @Autowired private ConfiguracionSistemaRepository configNoSqlRepo;
    @Autowired private VerificationCodeRepository codeSqlRepo;
    @Autowired private EmailService emailService;

    @GetMapping("/estado")
    public ResponseEntity<Map<String, Boolean>> estadoConfiguracion() {
        boolean completa = configNoSqlRepo.findById("GLOBAL_CONFIG")
                .map(ConfiguracionSistema::isConfiguracionCompleta)
                .orElse(false);
        return ResponseEntity.ok(Map.of("configuracionCompleta", completa));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> obtenerConfiguracion() {
        Optional<ConfiguracionSistema> opt = configNoSqlRepo.findById("GLOBAL_CONFIG");
        if (opt.isEmpty()) {
            Map<String, Object> vacia = new HashMap<>();
            vacia.put("configuracionCompleta", false);
            vacia.put("emailSmtp", "");
            vacia.put("smtpPasswordConfigured", false);
            vacia.put("smtpCredentialsInvalid", false);
            vacia.put("nombreNegocio", "");
            vacia.put("telefonoNegocio", "");
            vacia.put("terminosCondiciones", "");
            vacia.put("logoBase64", "");
            vacia.put("mediosPago", Map.of(
                    "yapeActivo", false,
                    "yapeTelefono", "",
                    "plinActivo", false,
                    "plinTelefono", "",
                    "transferenciaActiva", false,
                    "transferencias", List.of()
            ));
            return ResponseEntity.ok(vacia);
        }
        ConfiguracionSistema c = opt.get();
        ConfiguracionSistema.MediosPago mp = c.getMediosPago() != null ? c.getMediosPago() : new ConfiguracionSistema.MediosPago();
        Map<String, Object> m = new HashMap<>();
        m.put("configuracionCompleta", c.isConfiguracionCompleta());
        m.put("emailSmtp", c.getEmailSmtp() != null ? c.getEmailSmtp() : "");
        m.put("smtpPasswordConfigured", c.getPasswordSmtp() != null && !c.getPasswordSmtp().isBlank());
        m.put("smtpCredentialsInvalid", c.isSmtpCredentialsInvalid());
        m.put("nombreNegocio", c.getNombreNegocio() != null ? c.getNombreNegocio() : "");
        m.put("telefonoNegocio", c.getTelefonoNegocio() != null ? c.getTelefonoNegocio() : "");
        m.put("terminosCondiciones", c.getTerminosCondiciones() != null ? c.getTerminosCondiciones() : "");
        m.put("logoBase64", c.getLogoBase64() != null ? c.getLogoBase64() : "");
        m.put("mediosPago", Map.of(
                "yapeActivo", mp.isYapeActivo(),
                "yapeTelefono", mp.getYapeTelefono() != null ? mp.getYapeTelefono() : "",
                "plinActivo", mp.isPlinActivo(),
                "plinTelefono", mp.getPlinTelefono() != null ? mp.getPlinTelefono() : "",
                "transferenciaActiva", mp.isTransferenciaActiva(),
                "transferencias", mp.getTransferencias() != null ? mp.getTransferencias() : List.of()
        ));
        return ResponseEntity.ok(m);
    }

    @PostMapping("/enviar-verificacion")
    public ResponseEntity<?> enviarVerificacion(@RequestBody Map<String, String> request) {
        String email = request.get("emailSmtp");
        String pass = request.get("passwordSmtp");

        if (email == null || !email.endsWith("@gmail.com")) 
            return ResponseEntity.badRequest().body(Map.of("message", "Debe ser un correo @gmail.com"));
        
        if (pass == null || pass.length() < 16) 
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña de aplicación de Google debe tener 16 caracteres"));

        String numCode = String.format("%06d", new Random().nextInt(999999));
        VerificationCode vCode = new VerificationCode();
        vCode.setEmail(email);
        vCode.setCode(numCode);
        vCode.setExpirationTime(LocalDateTime.now().plusMinutes(1)); 
        codeSqlRepo.save(vCode);

        try {
            emailService.enviarCodigoVerificacion(
                    email,
                    numCode,
                    email,
                    pass,
                    EmailService.TipoCodigoCorreo.SETUP_SMTP,
                    "Mikunaigen",
                    null
            );
            return ResponseEntity.ok(Map.of("message", "Código enviado correctamente a " + email));
        } catch (EmailDispatchException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "message",
                    "Error al enviar el correo. Ref: " + e.trackingId() + " (" + e.stage() + ")"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "No se pudo enviar el correo. Verifica tu contraseña de aplicación."));
        }
    }

    @PostMapping("/validar-y-guardar")
    public ResponseEntity<?> validarYGuardar(@RequestBody Map<String, Object> data) {
        String email = (String) data.get("emailSmtp");
        if (email == null || email.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "El correo SMTP es obligatorio."));

        String newPassRaw = (String) data.get("passwordSmtp");
        if (newPassRaw != null && newPassRaw.isBlank())
            newPassRaw = null;

        Optional<ConfiguracionSistema> existingOpt = configNoSqlRepo.findById("GLOBAL_CONFIG");
        ConfiguracionSistema existing = existingOpt.orElse(null);

        boolean primeraConfig = existing == null || !existing.isConfiguracionCompleta();
        boolean requiereCodigo;
        if (primeraConfig) {
            requiereCodigo = true;
            if (newPassRaw == null || newPassRaw.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("message", "La contraseña de aplicación de Google es obligatoria en la primera configuración."));
        } else {
            boolean emailCambiado = existing.getEmailSmtp() == null || !Objects.equals(existing.getEmailSmtp(), email);
            boolean passNuevo = newPassRaw != null && !newPassRaw.isEmpty();
            requiereCodigo = emailCambiado || passNuevo;
        }

        if (requiereCodigo) {
            String codeIn = (String) data.get("codigoVerificacion");
            if (codeIn == null || codeIn.isBlank())
                return ResponseEntity.badRequest().body(Map.of("message", "Debes ingresar el código de verificación enviado al correo SMTP."));

            VerificationCode vCode = codeSqlRepo.findFirstByEmailAndUsedOrderByExpirationTimeDesc(email, false)
                    .orElse(null);

            if (vCode == null || !vCode.getCode().equals(codeIn))
                return ResponseEntity.badRequest().body(Map.of("message", "El código ingresado es incorrecto."));

            if (LocalDateTime.now().isAfter(vCode.getExpirationTime()))
                return ResponseEntity.badRequest().body(Map.of("message", "El código ha expirado. Solicita uno nuevo."));

            vCode.setUsed(true);
            codeSqlRepo.save(vCode);
        }

        ConfiguracionSistema config = existing != null ? existing : new ConfiguracionSistema();
        if (config.getId() == null)
            config.setId("GLOBAL_CONFIG");

        config.setEmailSmtp(email);
        if (newPassRaw != null && !newPassRaw.isEmpty()) {
            config.setPasswordSmtp(newPassRaw);
        } else if (existing != null && existing.getPasswordSmtp() != null) {
            config.setPasswordSmtp(existing.getPasswordSmtp());
        } else if (primeraConfig) {
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña de aplicación es obligatoria."));
        }

        config.setNombreNegocio((String) data.get("nombreNegocio"));
        config.setTelefonoNegocio((String) data.get("telefonoNegocio"));
        config.setTerminosCondiciones((String) data.get("terminosCondiciones"));
        config.setMediosPago(parseMediosPago(data.get("mediosPago")));

        String logo = (String) data.get("logoBase64");
        if (logo != null && !logo.isBlank()) {
            config.setLogoBase64(logo);
        } else if (existing != null && existing.getLogoBase64() != null) {
            config.setLogoBase64(existing.getLogoBase64());
        }

        config.setConfiguracionCompleta(true);
        config.setSmtpCredentialsInvalid(false);
        configNoSqlRepo.save(config);

        return ResponseEntity.ok(Map.of("message", "¡Configuración guardada con éxito!"));
    }

    private ConfiguracionSistema.MediosPago parseMediosPago(Object raw) {
        ConfiguracionSistema.MediosPago mp = new ConfiguracionSistema.MediosPago();
        if (!(raw instanceof Map<?, ?> map)) return mp;

        mp.setYapeActivo(Boolean.TRUE.equals(map.get("yapeActivo")));
        mp.setYapeTelefono(safeStr(map.get("yapeTelefono")));
        mp.setPlinActivo(Boolean.TRUE.equals(map.get("plinActivo")));
        mp.setPlinTelefono(safeStr(map.get("plinTelefono")));
        mp.setTransferenciaActiva(Boolean.TRUE.equals(map.get("transferenciaActiva")));

        List<ConfiguracionSistema.TransferenciaBancaria> bancos = new ArrayList<>();
        Object rawTransferencias = map.get("transferencias");
        if (rawTransferencias instanceof List<?> lista) {
            for (Object item : lista) {
                if (!(item instanceof Map<?, ?> bm)) continue;
                ConfiguracionSistema.TransferenciaBancaria b = new ConfiguracionSistema.TransferenciaBancaria();
                b.setBanco(safeStr(bm.get("banco")));
                b.setNumeroCuenta(safeStr(bm.get("numeroCuenta")));
                b.setCci(safeStr(bm.get("cci")));
                bancos.add(b);
            }
        }
        mp.setTransferencias(bancos);
        return mp;
    }

    private String safeStr(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}