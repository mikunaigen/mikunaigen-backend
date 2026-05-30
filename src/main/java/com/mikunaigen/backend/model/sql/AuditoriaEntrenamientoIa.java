package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "auditoria_entrenamiento_ia")
public class AuditoriaEntrenamientoIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "administrador_id", nullable = false)
    private UUID administradorId;

    @Column(name = "epocas_procesadas", nullable = false)
    private Integer epocasProcesadas;

    @Column(name = "error_entrenamiento", nullable = false, precision = 5, scale = 4)
    private BigDecimal errorEntrenamiento;

    @Column(name = "error_validacion", nullable = false, precision = 5, scale = 4)
    private BigDecimal errorValidacion;

    @Column(name = "supero_umbral_overfitting", nullable = false)
    private boolean superoUmbralOverfitting;

    @Column(name = "desplegado_produccion", nullable = false)
    private boolean desplegadoProduccion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin = LocalDateTime.now();
}
