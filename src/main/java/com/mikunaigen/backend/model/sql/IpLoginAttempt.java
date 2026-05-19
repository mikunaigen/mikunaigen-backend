package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bloqueos_ip")
public class IpLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "direccion_ip", unique = true, nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "intentos_fallidos")
    private Integer failedAttempts = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime blockedUntil;

    @Column(name = "ultimo_intento")
    private LocalDateTime lastFailedAt = LocalDateTime.now();

    public LocalDateTime getCreatedAt() {
        return lastFailedAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.lastFailedAt = createdAt;
    }
}
