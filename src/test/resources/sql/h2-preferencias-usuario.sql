CREATE TABLE IF NOT EXISTS preferencias_usuario (
    usuario_id UUID NOT NULL PRIMARY KEY,
    enfoque_principal VARCHAR(50),
    presupuesto_maximo NUMERIC(10, 2),
    filtro_estacionalidad_activo BOOLEAN,
    preferencias_completadas BOOLEAN,
    cabezas_optimizacion VARCHAR ARRAY
);
