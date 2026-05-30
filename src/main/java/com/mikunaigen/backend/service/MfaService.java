package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.model.sql.MfaBackupCode;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.repository.sql.MfaBackupCodeRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MfaService {

    private static final int CANTIDAD_CODIGOS_RESPALDO = 10;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UserRepository userRepository;
    private final MfaBackupCodeRepository codigoRespaldoRepository;
    private final TotpMfaService totpMfaService;
    private final PasswordEncoder passwordEncoder;
    private final ConfiguracionGlobalRepository configuracionRepository;

    public MfaService(
            UserRepository userRepository,
            MfaBackupCodeRepository codigoRespaldoRepository,
            TotpMfaService totpMfaService,
            PasswordEncoder passwordEncoder,
            ConfiguracionGlobalRepository configuracionRepository) {
        this.userRepository = userRepository;
        this.codigoRespaldoRepository = codigoRespaldoRepository;
        this.totpMfaService = totpMfaService;
        this.passwordEncoder = passwordEncoder;
        this.configuracionRepository = configuracionRepository;
    }

    public record ResultadoInicioMfa(String otpAuthUri, String secretPlain) {
    }

    public ResultadoInicioMfa iniciarConfiguracion(User usuario) {
        if (usuario.isMfaEnabled()) {
            throw new IllegalStateException("La autenticación de doble factor ya está activa.");
        }
        String secreto = totpMfaService.generarSecreto();
        usuario.setMfaSecret(secreto);
        usuario.setMfaEnabled(false);
        userRepository.save(usuario);
        String emisor = obtenerNombreEmisor();
        String uri = totpMfaService.construirUriOtpAuth(usuario.getEmail(), secreto, emisor);
        return new ResultadoInicioMfa(uri, secreto);
    }

    @Transactional
    public List<String> confirmarActivacion(User usuario, String codigo) {
        if (usuario.getMfaSecret() == null || usuario.getMfaSecret().isBlank()) {
            throw new IllegalStateException("Debes iniciar la configuración de doble factor primero.");
        }
        if (!totpMfaService.verificarCodigo(usuario.getMfaSecret(), codigo)) {
            throw new IllegalArgumentException("El código ingresado no es válido o ha expirado.");
        }
        usuario.setMfaEnabled(true);
        userRepository.save(usuario);
        codigoRespaldoRepository.deleteByUserId(usuario.getId());
        return generarYGuardarCodigosRespaldo(usuario.getId());
    }

    @Transactional
    public void desactivar(User usuario, String contrasena, String codigo) {
        if (!usuario.isMfaEnabled()) {
            throw new IllegalStateException("La autenticación de doble factor no está activa.");
        }
        if (!passwordEncoder.matches(contrasena, usuario.getPassword())) {
            throw new IllegalArgumentException("La contraseña es incorrecta.");
        }
        if (!totpMfaService.verificarCodigo(usuario.getMfaSecret(), codigo)) {
            throw new IllegalArgumentException("El código de autenticador no es válido.");
        }
        limpiarMfa(usuario);
    }

    public boolean requiereMfa(User usuario) {
        return usuario != null
                && usuario.isMfaEnabled()
                && usuario.getMfaSecret() != null
                && !usuario.getMfaSecret().isBlank();
    }

    public boolean verificarCodigoIngreso(User usuario, String codigoTotp) {
        return totpMfaService.verificarCodigo(usuario.getMfaSecret(), codigoTotp);
    }

    public boolean verificarCodigoRespaldo(User usuario, String codigoPlano) {
        if (codigoPlano == null || codigoPlano.isBlank()) {
            return false;
        }
        String normalizado = codigoPlano.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        List<MfaBackupCode> activos = codigoRespaldoRepository.findByUserIdAndUsedAtIsNull(usuario.getId());
        for (MfaBackupCode fila : activos) {
            if (passwordEncoder.matches(normalizado, fila.getCodeHash())) {
                fila.setUsedAt(LocalDateTime.now());
                codigoRespaldoRepository.save(fila);
                return true;
            }
        }
        return false;
    }

    public User buscarPorId(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).orElse(null);
    }

    private void limpiarMfa(User usuario) {
        usuario.setMfaEnabled(false);
        usuario.setMfaSecret(null);
        userRepository.save(usuario);
        codigoRespaldoRepository.deleteByUserId(usuario.getId());
    }

    private List<String> generarYGuardarCodigosRespaldo(UUID userId) {
        List<String> codigosPlano = new ArrayList<>();
        for (int i = 0; i < CANTIDAD_CODIGOS_RESPALDO; i++) {
            String plano = generarCodigoRespaldo();
            codigosPlano.add(plano);
            MfaBackupCode entidad = new MfaBackupCode();
            entidad.setUserId(userId);
            entidad.setCodeHash(passwordEncoder.encode(plano));
            codigoRespaldoRepository.save(entidad);
        }
        return codigosPlano;
    }

    private String generarCodigoRespaldo() {
        String caracteres = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(caracteres.charAt(ALEATORIO.nextInt(caracteres.length())));
        }
        return sb.substring(0, 4) + "-" + sb.substring(4);
    }

    private String obtenerNombreEmisor() {
        return configuracionRepository.findById(1)
                .map(ConfiguracionGlobal::getNombrePlataforma)
                .filter(n -> n != null && !n.isBlank())
                .orElse(null);
    }
}
