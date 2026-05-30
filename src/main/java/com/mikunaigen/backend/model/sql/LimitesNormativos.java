package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "limites_normativos")
public class LimitesNormativos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String normativa;

    @Column(nullable = false, length = 50)
    private String nutriente;

    @Column(name = "valor_maximo", nullable = false, precision = 10, scale = 4)
    private BigDecimal valorMaximo;

    @Column(nullable = false, length = 10)
    private String unidad;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por")
    private UUID actualizadoPor;
}
