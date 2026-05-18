package com.mikunaigen.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "configuracion_sistema")
public class ConfiguracionSistema {
    @Id
    private String id;
    private String emailSmtp;
    private String passwordSmtp;
    private String nombreNegocio;
    private String logoBase64;
    private String telefonoNegocio;
    private String terminosCondiciones;
    private MediosPago mediosPago = new MediosPago();
    private boolean configuracionCompleta = false;
    private boolean smtpCredentialsInvalid = false;

    @Data
    public static class MediosPago {
        private boolean yapeActivo = false;
        private String yapeTelefono = "";
        private boolean plinActivo = false;
        private String plinTelefono = "";
        private boolean transferenciaActiva = false;
        private List<TransferenciaBancaria> transferencias = new ArrayList<>();
    }

    @Data
    public static class TransferenciaBancaria {
        private String banco = "";
        private String numeroCuenta = "";
        private String cci = "";
    }
}