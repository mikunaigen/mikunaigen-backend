package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "auditoria_exportaciones")
public class AuditoriaExportacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inferencia_id", nullable = false)
    private UUID inferenciaId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "rol_usuario_momento", nullable = false, length = 50)
    private String rolUsuarioMomento;

    @Column(name = "formato_exportacion", nullable = false, length = 10)
    private String formatoExportacion;

    @Column(name = "fecha_exportacion")
    private LocalDateTime fechaExportacion = LocalDateTime.now();
}
