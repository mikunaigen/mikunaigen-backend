package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "auditoria_restauraciones")
public class AuditoriaRestauracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "administrador_id", nullable = false)
    private UUID administradorId;

    @Column(name = "backup_nombre_archivo", nullable = false, length = 255)
    private String backupNombreArchivo;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio = LocalDateTime.now();

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;
}
