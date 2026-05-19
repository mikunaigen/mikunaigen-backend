package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "historial_telefonos")
public class HistorialTelefono {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "numero_telefono", nullable = false, length = 9)
    private String numeroTelefono;

    @Column(name = "fecha_cambio")
    private LocalDateTime fechaCambio = LocalDateTime.now();
}
