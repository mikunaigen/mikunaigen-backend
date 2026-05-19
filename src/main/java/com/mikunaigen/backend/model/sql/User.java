package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "usuarios")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    private Role role;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false, length = 9)
    private String telefono;

    @Column(length = 8, unique = true)
    private String dni;

    @Column(name = "telegram_id", unique = true, length = 100)
    private String telegramId;

    @Column(nullable = false)
    private String contrasena;

    @Column(length = 20)
    private String estado = "pendiente";

    @Column(name = "intentos_fallidos_login")
    private Integer intentosFallidosLogin = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "limite_inferencias_usadas")
    private Integer limiteInferenciasUsadas = 0;

    @Column(name = "fecha_inicio_plan")
    private LocalDateTime fechaInicioPlan;

    @Column(name = "fecha_fin_plan")
    private LocalDateTime fechaFinPlan;

    @Column(name = "acepto_terminos", nullable = false)
    private boolean aceptoTerminos = false;

    @Column(name = "acepto_descargo", nullable = false)
    private boolean aceptoDescargo = false;

    @Column(name = "modo_oscuro", nullable = false)
    private boolean modoOscuro = false;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    @Transient
    private boolean firstLogin = false;

    @Transient
    private boolean deleted = false;

    public String getFullName() {
        return ((nombres != null ? nombres : "") + " " + (apellidos != null ? apellidos : "")).trim();
    }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            this.nombres = "";
            this.apellidos = "";
            return;
        }
        String[] partes = fullName.trim().split("\\s+", 2);
        this.nombres = partes[0];
        this.apellidos = partes.length > 1 ? partes[1] : "";
    }

    public String getPassword() {
        return contrasena;
    }

    public void setPassword(String password) {
        this.contrasena = password;
    }

    public String getPhone() {
        return telefono;
    }

    public void setPhone(String phone) {
        this.telefono = phone;
    }

    public boolean isDarkMode() {
        return modoOscuro;
    }

    public void setDarkMode(boolean darkMode) {
        this.modoOscuro = darkMode;
    }

    public boolean isDeleted() {
        return "suspendido".equalsIgnoreCase(estado);
    }

    public void setDeleted(boolean deleted) {
        if (deleted) {
            this.estado = "suspendido";
        } else if ("suspendido".equalsIgnoreCase(this.estado)) {
            this.estado = "activo";
        }
    }

    public boolean isFirstLogin() {
        return firstLogin;
    }

    public void setFirstLogin(boolean firstLogin) {
        this.firstLogin = firstLogin;
    }

    @Transient
    private String address;

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDateTime getCreatedAt() {
        return fechaRegistro;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.fechaRegistro = createdAt;
    }
}
