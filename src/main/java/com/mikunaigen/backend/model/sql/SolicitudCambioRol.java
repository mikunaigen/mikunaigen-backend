package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "solicitudes_cambio_rol")
public class SolicitudCambioRol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "rol_solicitado_id", nullable = false)
    private Integer rolSolicitadoId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String justificacion;

    @Column(name = "comprobante_pago_bytea", nullable = false, columnDefinition = "BYTEA")
    private byte[] comprobantePagoBytea;

    @Column(length = 20)
    private String estado = "pendiente";

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @Column(name = "fecha_solicitud")
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;
}
