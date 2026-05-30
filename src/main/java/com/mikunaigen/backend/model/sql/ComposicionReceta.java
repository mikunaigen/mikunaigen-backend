package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "composicion_recetas")
public class ComposicionReceta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inferencia_id", nullable = false)
    private UUID inferenciaId;

    @Column(name = "alimento_id", nullable = false)
    private Integer alimentoId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;
}
