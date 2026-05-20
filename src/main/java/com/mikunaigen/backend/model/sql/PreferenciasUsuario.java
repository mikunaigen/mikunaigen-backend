package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "preferencias_usuario")
public class PreferenciasUsuario {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "enfoque_principal", length = 50)
    private String enfoquePrincipal = "maxima_precision_nutricional";

    @Column(name = "presupuesto_maximo", precision = 10, scale = 2)
    private BigDecimal presupuestoMaximo;

    @Column(name = "filtro_estacionalidad_activo")
    private boolean filtroEstacionalidadActivo = false;

    @Column(name = "preferencias_completadas")
    private boolean preferenciasCompletadas = false;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cabezas_optimizacion", columnDefinition = "text[]")
    private String[] cabezasOptimizacion = new String[]{"maxima_precision_nutricional"};
}
