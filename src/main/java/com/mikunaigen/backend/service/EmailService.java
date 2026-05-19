package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
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
    private final ConfiguracionGlobalRepository configRepository;
    private final UserRepository userRepository;

    public EmailService(
            GithubEmailDispatchService githubEmailDispatchService,
            ConfiguracionGlobalRepository configRepository,
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
                        + "Este código expira en 2 minutos.";
            }
            case REGISTRO_USUARIO -> {
                subject = "Código de registro de cuenta - " + negocio;
                body = "Recibimos una solicitud de registro en " + negocio + ".\n\n"
                        + "Código de verificación: " + codigo + "\n"
                        + "Este código expira en 2 minutos.\n"
                        + "Si no realizaste esta acción, ignora este mensaje.";
            }
            case ACTIVACION_EMPLEADO -> {
                subject = "Código para activar tu cuenta - " + negocio;
                body = "Tu cuenta requiere activación.\n\n"
                        + "Código de activación: " + codigo + "\n"
                        + "Este código expira en 2 minutos.";
            }
            case RECUPERACION_PASSWORD -> {
                subject = "Código para restablecer contraseña - " + negocio;
                body = "Recibimos una solicitud para restablecer tu contraseña.\n\n"
                        + "Código de recuperación: " + codigo + "\n"
                        + "Este código expira en 2 minutos.\n"
                        + "Si no solicitaste este cambio, ignora este correo.";
            }
            default -> {
                subject = "Código de verificación - " + negocio;
                body = "Tu código de verificación es: " + codigo
                        + "\nEste código expira en 2 minutos.";
            }
        }

        githubEmailDispatchService.enviar(
                destino,
                subject,
                body,
                emisor,
                passwordSmtp,
                notifyUserId
        );
    }

    public ConfiguracionGlobal obtenerConfiguracion() {
        return configRepository.findById(1).orElse(null);
    }

    private void assertSmtpConfigPermiteEnvio(TipoCodigoCorreo tipo) {
        if (tipo == TipoCodigoCorreo.SETUP_SMTP) {
            return;
        }
        ConfiguracionGlobal config = obtenerConfiguracion();
        if (config == null || config.getSmtpEmail() == null || config.getSmtpEmail().isBlank()) {
            throw new IllegalStateException("SMTP no configurado.");
        }
        if (!"activo".equalsIgnoreCase(config.getSmtpEstado())) {
            throw new IllegalStateException("SMTP inactivo.");
        }
    }

    private boolean usuarioRebotado(String email) {
        User u = userRepository.findByEmailIgnoreCase(email).orElse(null);
        return u != null && "suspendido".equalsIgnoreCase(u.getEstado());
    }
}
