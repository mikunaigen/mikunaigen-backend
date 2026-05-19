package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "configuracion_global")
public class ConfiguracionGlobal {

    @Id
    private Integer id = 1;

    @Column(name = "nombre_plataforma", nullable = false)
    private String nombrePlataforma = "Mikunaigen";

    @Column(name = "logo_bytea")
    private byte[] logoBytea;

    @Column(name = "telefono_contacto", length = 15)
    private String telefonoContacto;

    @Column(name = "numero_yape", length = 9)
    private String numeroYape;

    @Column(name = "numero_plin", length = 9)
    private String numeroPlin;

    @Column(name = "banco_nombre", length = 100)
    private String bancoNombre;

    @Column(name = "cuenta_bancaria", length = 20)
    private String cuentaBancaria;

    @Column(name = "cci", length = 20)
    private String cci;

    @Column(name = "smtp_email")
    private String smtpEmail;

    @Column(name = "smtp_contrasena_app", columnDefinition = "TEXT")
    private String smtpContrasenaApp;

    @Column(name = "smtp_estado", length = 20)
    private String smtpEstado = "inactivo";

    @Column(name = "smtp_fecha_configuracion")
    private LocalDateTime smtpFechaConfiguracion;

    @Column(name = "programacion_backup", length = 20)
    private String programacionBackup = "ninguno";

    @Column(name = "modo_mantenimiento_activo")
    private boolean modoMantenimientoActivo = false;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    @Transient
    private boolean smtpCredencialesInvalidas = false;

    public boolean isConfiguracionCompleta() {
        return nombrePlataforma != null && !nombrePlataforma.isBlank()
                && logoBytea != null && logoBytea.length > 0
                && telefonoContacto != null && !telefonoContacto.isBlank()
                && numeroYape != null && !numeroYape.isBlank()
                && numeroPlin != null && !numeroPlin.isBlank()
                && bancoNombre != null && !bancoNombre.isBlank()
                && cuentaBancaria != null && !cuentaBancaria.isBlank()
                && cci != null && !cci.isBlank();
    }
}
