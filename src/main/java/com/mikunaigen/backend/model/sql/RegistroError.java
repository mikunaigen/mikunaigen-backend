package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "registro_errores")
public class RegistroError {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String origen;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    @Column(name = "pila_error", columnDefinition = "TEXT")
    private String pilaError;

    @Column(name = "ruta_url", length = 255)
    private String rutaUrl;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "fecha_registro")
    private OffsetDateTime fechaRegistro = OffsetDateTime.now();
}
