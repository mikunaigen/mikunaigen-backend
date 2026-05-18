package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.nosql.ConfiguracionSistema;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    public enum TipoCodigoCorreo {
        SETUP_SMTP,
        REGISTRO_USUARIO,
        ACTIVACION_EMPLEADO,
        RECUPERACION_PASSWORD
    }

    private final GithubEmailDispatchService githubEmailDispatchService;
    private final ConfiguracionSistemaRepository configRepository;
    private final UserRepository userRepository;

    public EmailService(
            GithubEmailDispatchService githubEmailDispatchService,
            ConfiguracionSistemaRepository configRepository,
            UserRepository userRepository) {
        this.githubEmailDispatchService = githubEmailDispatchService;
        this.configRepository = configRepository;
        this.userRepository = userRepository;
    }

    public void enviarCodigoVerificacion(
            String destino,
            String codigo,
            String emisor,
            String passwordSmtp,
            TipoCodigoCorreo tipo,
            String nombreNegocio,
            String notifyUserId
    ) {
        if (destino != null && usuarioRebotado(destino)) {
            throw new IllegalStateException("No se puede enviar correo a esta dirección.");
        }
        assertSmtpConfigPermiteEnvio(tipo);

        String negocio = (nombreNegocio == null || nombreNegocio.isBlank())
                ? "Mikunaigen" : nombreNegocio.trim();
        String subject;
        String body;

        switch (tipo) {
            case SETUP_SMTP -> {
                subject = "Código de verificación SMTP - " + negocio;
                body = "Estás validando el correo SMTP de " + negocio + ".\n\n"
                        + "Código: " + codigo + "\n"
                        + "Este código expira en 1 minuto.";
            }
            case REGISTRO_USUARIO -> {
                subject = "Código de registro de cuenta - " + negocio;
                body = "Recibimos una solicitud de registro en " + negocio + ".\n\n"
                        + "Código de verificación: " + codigo + "\n"
                        + "Este código expira en 1 minuto.\n"
                        + "Si no realizaste esta acción, ignora este mensaje.";
            }
            case ACTIVACION_EMPLEADO -> {
                subject = "Código para activar tu cuenta de personal - " + negocio;
                body = "Tu cuenta de personal fue creada y requiere activación.\n\n"
                        + "Código de activación: " + codigo + "\n"
                        + "Este código expira en 1 minuto.";
            }
            case RECUPERACION_PASSWORD -> {
                subject = "Código para restablecer contraseña - " + negocio;
                body = "Recibimos una solicitud para restablecer tu contraseña.\n\n"
                        + "Código de recuperación: " + codigo + "\n"
                        + "Este código expira en 1 minuto.\n"
                        + "Si no solicitaste este cambio, ignora este correo.";
            }
            default -> {
                subject = "Código de verificación - " + negocio;
                body = "Tu código de verificación es: " + codigo
                        + "\nEste código expira en 1 minuto.";
            }
        }

        githubEmailDispatchService.dispatchPlainEmail(
                destino,
                emisor,
                passwordSmtp,
                subject,
                body,
                notifyUserId
        );
    }

    public void enviarCorreoTextoPlano(
            String destino,
            String asunto,
            String cuerpo,
            String emisor,
            String passwordSmtp,
            String notifyUserId
    ) {
        if (destino == null || destino.isBlank() || emisor == null || emisor.isBlank()
                || passwordSmtp == null || passwordSmtp.isBlank()) {
            return;
        }
        if (usuarioRebotado(destino)) {
            return;
        }
        ConfiguracionSistema cfg = configRepository.findById("GLOBAL_CONFIG").orElse(null);
        if (cfg != null && cfg.isSmtpCredentialsInvalid()) {
            return;
        }

        githubEmailDispatchService.dispatchPlainEmail(
                destino,
                emisor,
                passwordSmtp,
                asunto,
                cuerpo,
                notifyUserId
        );
    }

    private void assertSmtpConfigPermiteEnvio(TipoCodigoCorreo tipo) {
        if (tipo == TipoCodigoCorreo.SETUP_SMTP) {
            return;
        }
        ConfiguracionSistema c = configRepository.findById("GLOBAL_CONFIG").orElse(null);
        if (c != null && c.isSmtpCredentialsInvalid()) {
            throw new IllegalStateException("Correo del sistema no disponible. Revisa la configuración SMTP.");
        }
    }

    private boolean usuarioRebotado(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(User::isEmailBounced)
                .orElse(false);
    }
}
