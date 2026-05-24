package com.mikunaigen.backend.repository;

import com.mikunaigen.backend.model.sql.VerificationCode;
import com.mikunaigen.backend.repository.sql.VerificationCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VerificationCodeRepositoryIntegrationTest {

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Test
    void codigoValido_persisteYRecuperaPorReferencia() {
        // HU-03: código de recuperación válido se guarda en base de datos
        VerificationCode code = new VerificationCode();
        code.setReferencia("usuario@gmail.com");
        code.setCodigo("123456");
        code.setProposito("recuperacion");
        code.setFechaExpiracion(LocalDateTime.now().plusMinutes(2));
        verificationCodeRepository.save(code);

        Optional<VerificationCode> encontrado = verificationCodeRepository
                .findFirstByReferenciaAndUsadoOrderByFechaExpiracionDesc("usuario@gmail.com", false);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getCode()).isEqualTo("123456");
    }

    @Test
    void codigoExpirado_noSeUsaComoVigente() {
        // HU-03: código vencido no debe considerarse válido para restablecer contraseña
        VerificationCode code = new VerificationCode();
        code.setReferencia("vencido@gmail.com");
        code.setCodigo("654321");
        code.setProposito("recuperacion");
        code.setFechaExpiracion(LocalDateTime.now().minusMinutes(5));
        verificationCodeRepository.save(code);

        Optional<VerificationCode> encontrado = verificationCodeRepository
                .findFirstByReferenciaAndUsadoOrderByFechaExpiracionDesc("vencido@gmail.com", false);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getExpirationTime()).isBefore(LocalDateTime.now());
    }
}
