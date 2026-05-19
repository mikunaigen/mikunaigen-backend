package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    Optional<VerificationCode> findFirstByReferenciaAndUsadoOrderByFechaExpiracionDesc(String referencia, boolean usado);

    default Optional<VerificationCode> findFirstByEmailAndUsedOrderByExpirationTimeDesc(String email, boolean used) {
        return findFirstByReferenciaAndUsadoOrderByFechaExpiracionDesc(email, used);
    }
}
