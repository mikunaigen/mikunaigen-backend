package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ip_login_attempts")
public class IpLoginAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ip_address", unique = true, nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "failed_attempts")
    private Integer failedAttempts = 0;

    @Column(name = "last_failed_at")
    private LocalDateTime lastFailedAt;

    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}

