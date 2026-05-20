package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "restricciones_ingredientes")
public class RestriccionIngrediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "alimento_id", nullable = false)
    private Integer alimentoId;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
