package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "verification_codes")
public class VerificationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String code;
    
    @Column(name = "expiration_time")
    private LocalDateTime expirationTime;

    @Column(name = "is_used")
    private boolean used = false;
}