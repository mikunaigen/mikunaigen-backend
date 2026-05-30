package com.mikunaigen.backend.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
public class TotpMfaService {

    private static final int TAMANO_VENTANA = 3;

    private final GoogleAuthenticator autenticadorGoogle;
    private final String emisorPorDefecto;

    public TotpMfaService(@Value("${app.mfa.issuer:Mikunaigen}") String emisorPorDefecto) {
        this.emisorPorDefecto = emisorPorDefecto;
        GoogleAuthenticatorConfig config = new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(30))
                .setWindowSize(TAMANO_VENTANA)
                .build();
        this.autenticadorGoogle = new GoogleAuthenticator(config);
    }

    public String generarSecreto() {
        GoogleAuthenticatorKey clave = autenticadorGoogle.createCredentials();
        return clave.getKey();
    }

    public String construirUriOtpAuth(String correo, String secreto, String emisor) {
        String issuer = emisor != null && !emisor.isBlank() ? emisor.trim() : emisorPorDefecto;
        String cuenta = correo != null ? correo.trim() : "";
        String secretoEnc = URLEncoder.encode(secreto, StandardCharsets.UTF_8);
        String issuerEnc = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String cuentaEnc = URLEncoder.encode(cuenta, StandardCharsets.UTF_8);
        return "otpauth://totp/" + issuerEnc + ":" + cuentaEnc + "?secret=" + secretoEnc + "&issuer=" + issuerEnc;
    }

    public boolean verificarCodigo(String secreto, String codigo) {
        if (secreto == null || secreto.isBlank() || codigo == null) {
            return false;
        }
        String digitos = codigo.replaceAll("\\D", "");
        if (digitos.length() != 6) {
            return false;
        }
        try {
            return autenticadorGoogle.authorize(secreto, Integer.parseInt(digitos));
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
