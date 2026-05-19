package com.mikunaigen.backend.util;

import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class ConfiguracionPlataformaMapper {

    private ConfiguracionPlataformaMapper() {
    }

    public static Map<String, Object> aMapaPublico(ConfiguracionGlobal c) {
        Map<String, Object> m = new HashMap<>();
        if (c == null) {
            m.put("configuracionCompleta", false);
            m.put("nombrePlataforma", "Mikunaigen");
            m.put("nombreNegocio", "Mikunaigen");
            m.put("telefonoContacto", "");
            m.put("telefonoNegocio", "");
            m.put("terminosCondiciones", "");
            m.put("logoBase64", "");
            m.put("emailSmtp", "");
            m.put("smtpPasswordConfigured", false);
            m.put("smtpCredentialsInvalid", false);
            m.put("smtpEstado", "inactivo");
            m.put("smtpFechaConfiguracion", null);
            m.put("mediosPago", mediosPagoVacio());
            m.put("modoMantenimientoActivo", false);
            m.put("programacionBackup", "ninguno");
            return m;
        }
        m.put("configuracionCompleta", c.isConfiguracionCompleta());
        m.put("nombrePlataforma", c.getNombrePlataforma());
        m.put("nombreNegocio", c.getNombrePlataforma());
        m.put("telefonoContacto", c.getTelefonoContacto() != null ? c.getTelefonoContacto() : "");
        m.put("telefonoNegocio", c.getTelefonoContacto() != null ? c.getTelefonoContacto() : "");
        m.put("terminosCondiciones", c.getTerminosCondiciones() != null ? c.getTerminosCondiciones() : "");
        m.put("logoBase64", logoBase64(c));
        m.put("emailSmtp", c.getSmtpEmail() != null ? c.getSmtpEmail() : "");
        m.put("smtpPasswordConfigured", c.getSmtpContrasenaApp() != null && !c.getSmtpContrasenaApp().isBlank());
        m.put("smtpCredentialsInvalid", c.isSmtpCredencialesInvalidas());
        m.put("smtpEstado", c.getSmtpEstado() != null ? c.getSmtpEstado() : "inactivo");
        m.put("smtpFechaConfiguracion", c.getSmtpFechaConfiguracion());
        m.put("mediosPago", Map.of(
                "yapeActivo", c.getNumeroYape() != null && !c.getNumeroYape().isBlank(),
                "yapeTelefono", c.getNumeroYape() != null ? c.getNumeroYape() : "",
                "plinActivo", c.getNumeroPlin() != null && !c.getNumeroPlin().isBlank(),
                "plinTelefono", c.getNumeroPlin() != null ? c.getNumeroPlin() : "",
                "transferenciaActiva", c.getBancoNombre() != null && !c.getBancoNombre().isBlank(),
                "transferencias", java.util.List.of(Map.of(
                        "banco", c.getBancoNombre() != null ? c.getBancoNombre() : "",
                        "numeroCuenta", c.getCuentaBancaria() != null ? c.getCuentaBancaria() : "",
                        "cci", c.getCci() != null ? c.getCci() : ""
                ))
        ));
        m.put("modoMantenimientoActivo", c.isModoMantenimientoActivo());
        m.put("programacionBackup", c.getProgramacionBackup() != null ? c.getProgramacionBackup() : "ninguno");
        return m;
    }

    public static String logoBase64(ConfiguracionGlobal c) {
        if (c == null || c.getLogoBytea() == null || c.getLogoBytea().length == 0) {
            return "";
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(c.getLogoBytea());
    }

    public static byte[] decodificarLogo(String logoBase64) {
        if (logoBase64 == null || logoBase64.isBlank()) {
            return null;
        }
        String data = logoBase64.trim();
        int comma = data.indexOf(',');
        if (comma >= 0) {
            data = data.substring(comma + 1);
        }
        return Base64.getDecoder().decode(data);
    }

    private static Map<String, Object> mediosPagoVacio() {
        return Map.of(
                "yapeActivo", false,
                "yapeTelefono", "",
                "plinActivo", false,
                "plinTelefono", "",
                "transferenciaActiva", false,
                "transferencias", java.util.List.of()
        );
    }
}
