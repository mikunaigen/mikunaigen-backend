package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "codigos_verificacion")
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String referencia;

    @Column(nullable = false, length = 6)
    private String codigo;

    @Column(nullable = false, length = 50)
    private String proposito;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(nullable = false)
    private boolean usado = false;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();

    public String getEmail() {
        return referencia;
    }

    public void setEmail(String email) {
        this.referencia = email;
    }

    public String getCode() {
        return codigo;
    }

    public void setCode(String code) {
        this.codigo = code;
    }

    public LocalDateTime getExpirationTime() {
        return fechaExpiracion;
    }

    public void setExpirationTime(LocalDateTime expirationTime) {
        this.fechaExpiracion = expirationTime;
    }

    public boolean isUsed() {
        return usado;
    }

    public void setUsed(boolean used) {
        this.usado = used;
    }
}
