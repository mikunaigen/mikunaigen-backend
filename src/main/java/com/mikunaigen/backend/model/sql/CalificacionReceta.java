package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "calificaciones_recetas")
public class CalificacionReceta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inferencia_id", nullable = false, unique = true)
    private UUID inferenciaId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false)
    private Integer estrellas;

    @Column(length = 500)
    private String comentario;

    @Column(name = "fecha_calificacion")
    private LocalDateTime fechaCalificacion = LocalDateTime.now();
}
