package com.mikunaigen.backend.service.dashboard;

import com.mikunaigen.backend.model.sql.ConfiguracionIa;
import com.mikunaigen.backend.repository.sql.*;
import com.mikunaigen.backend.service.IaModelosService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MikunaigenDashboardService {

    private static final double MINUTOS_AHORRO_POR_RECETA = 30.0;
    private static final double MINUTOS_PROCESAMIENTO_IA_ESTIMADO = 2.0;

    private final UserRepository userRepository;
    private final IaModelosService iaModelosService;
    private final CalificacionRecetaRepository calificacionRecetaRepository;
    private final AuditoriaAccesoRepository auditoriaAccesoRepository;
    private final RegistroErrorRepository registroErrorRepository;
    private final AuditoriaEntrenamientoIaRepository auditoriaEntrenamientoIaRepository;
    private final AuditoriaRestauracionRepository auditoriaRestauracionRepository;
    private final InferenciaRecetaRepository inferenciaRecetaRepository;
    private final AuditoriaExportacionRepository auditoriaExportacionRepository;
    private final ConfiguracionIaRepository configuracionIaRepository;

    public MikunaigenDashboardService(
            UserRepository userRepository,
            IaModelosService iaModelosService,
            CalificacionRecetaRepository calificacionRecetaRepository,
            AuditoriaAccesoRepository auditoriaAccesoRepository,
            RegistroErrorRepository registroErrorRepository,
            AuditoriaEntrenamientoIaRepository auditoriaEntrenamientoIaRepository,
            AuditoriaRestauracionRepository auditoriaRestauracionRepository,
            InferenciaRecetaRepository inferenciaRecetaRepository,
            AuditoriaExportacionRepository auditoriaExportacionRepository,
            ConfiguracionIaRepository configuracionIaRepository
    ) {
        this.userRepository = userRepository;
        this.iaModelosService = iaModelosService;
        this.calificacionRecetaRepository = calificacionRecetaRepository;
        this.auditoriaAccesoRepository = auditoriaAccesoRepository;
        this.registroErrorRepository = registroErrorRepository;
        this.auditoriaEntrenamientoIaRepository = auditoriaEntrenamientoIaRepository;
        this.auditoriaRestauracionRepository = auditoriaRestauracionRepository;
        this.inferenciaRecetaRepository = inferenciaRecetaRepository;
        this.auditoriaExportacionRepository = auditoriaExportacionRepository;
        this.configuracionIaRepository = configuracionIaRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> kpis(LocalDateTime desde, LocalDateTime hasta) {
        OffsetDateTime desdeUtc = aOffset(desde);
        OffsetDateTime hastaUtc = aOffset(hasta);

        long totalClientes = userRepository.countActiveClientes();
        long clientesActivos = auditoriaAccesoRepository.contarUsuariosClientesDistintos(desdeUtc, hastaUtc);
        double retencion = totalClientes > 0 ? (clientesActivos * 100.0) / totalClientes : 0.0;

        long diasRango = Math.max(1, ChronoUnit.DAYS.between(desde.toLocalDate(), hasta.toLocalDate()) + 1);
        long semanasRango = Math.max(1, diasRango / 7);
        long despliegues = auditoriaEntrenamientoIaRepository.contarDesplieguesProduccion(desde, hasta);
        double frecuenciaDespliegue = despliegues / (double) semanasRango;

        long errores = registroErrorRepository.contarEntre(desdeUtc, hastaUtc);
        long inferencias = inferenciaRecetaRepository.contarInferenciasEntre(desde, hasta);
        long exportaciones = auditoriaExportacionRepository.contarEntre(desde, hasta);
        long volumenUso = inferencias + exportaciones;
        double tasaFallo = volumenUso > 0 ? (errores * 100.0) / volumenUso : 0.0;

        Double leadTimeMin = auditoriaEntrenamientoIaRepository.promedioDuracionMinutos(desde, hasta);
        double leadTime = leadTimeMin != null ? leadTimeMin : 0.0;

        Double mttrMin = auditoriaRestauracionRepository.promedioMttrMinutos(desde, hasta);
        double mttr = mttrMin != null ? mttrMin : 0.0;

        long recetasGuardadas = inferenciaRecetaRepository.contarGuardadasHistorialEntre(desde, hasta);
        double minutosProcesamientoIa = inferencias * MINUTOS_PROCESAMIENTO_IA_ESTIMADO;
        double ahorroMinutos = (recetasGuardadas * MINUTOS_AHORRO_POR_RECETA) - minutosProcesamientoIa;

        long nuevasHistorial = recetasGuardadas;
        long totalHistorial = inferenciaRecetaRepository.contarTotalGuardadasHistorial();
        double tasaNuevosProductos = totalHistorial > 0 ? (nuevasHistorial * 100.0) / totalHistorial : 0.0;

        double precisionModelo = calcularPrecisionModelo();

        double promedioEstrellas = calificacionRecetaRepository.promedioEstrellasEntre(desde, hasta);
        String satisfaccionTexto = promedioEstrellas > 0
                ? String.format(Locale.US, "%.1f/5", promedioEstrellas)
                : "—";

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(kpi("Retención de Usuarios", formatearPorcentaje(retencion), retencion, "%",
                "usuarios activos / registrados (clientes)", tendencia(retencion, 50.0)));
        items.add(kpi("Frecuencia de Despliegue", String.format(Locale.US, "%.1f/semana", frecuenciaDespliegue),
                frecuenciaDespliegue, "/sem", "despliegues exitosos / semana", tendencia(frecuenciaDespliegue, 1.0)));
        items.add(kpi("Tasa de Fallo", formatearPorcentaje(tasaFallo), tasaFallo, "%",
                "errores / (inferencias + exportaciones)", tendenciaInversa(tasaFallo, 5.0)));
        items.add(kpi("Tiempo de Entrega de Cambios", String.format(Locale.US, "%.0f min", leadTime),
                leadTime, "min", "promedio duración entrenamiento IA", tendenciaInversa(leadTime, 120.0)));
        items.add(kpi("Tiempo Medio de Recuperación", String.format(Locale.US, "%.0f min", mttr),
                mttr, "min", "MTTR restauraciones exitosas", tendenciaInversa(mttr, 60.0)));
        items.add(kpi("Ahorro de Tiempo al Usuario", String.format(Locale.US, "%.0f min", Math.max(0, ahorroMinutos)),
                Math.max(0, ahorroMinutos), "min", "recetas guardadas × 30 min − procesamiento IA", tendencia(ahorroMinutos, 0)));
        items.add(kpi("Tasa de Nuevos Productos", formatearPorcentaje(tasaNuevosProductos), tasaNuevosProductos, "%",
                "recetas nuevas en historial / total historial", tendencia(tasaNuevosProductos, 10.0)));
        items.add(kpi("Precisión del Modelo", formatearPorcentaje(precisionModelo), precisionModelo, "%",
                "100% − (error validación × 100)", tendencia(precisionModelo, 95.0)));
        items.add(kpi("Satisfacción de los Usuarios", satisfaccionTexto,
                promedioEstrellas > 0 ? promedioEstrellas : 0, "/5", "promedio estrellas calificaciones", tendencia(promedioEstrellas, 3.5)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("desde", desde);
        out.put("hasta", hasta);
        out.put("fechaMinima", userRepository.findMinClienteCreatedAt().orElse(null));
        out.put("kpis", items);
        out.put("graficos", construirGraficos(items, desde, hasta, desdeUtc, hastaUtc));
        out.put("alertaPrecision", precisionModelo > 0 && precisionModelo < 95.0);
        out.put("alertaTiempoRespuesta", mttr > 60.0);
        return out;
    }

    public Map<String, Object> prediccionInventario() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean disponible = iaModelosService.slotEfectivo(3);
        out.put("disponible", disponible);
        if (!disponible) {
            out.put("items", List.of());
            out.put("alertasCriticas", List.of());
            out.put("heatmap", Map.of());
            return out;
        }
        out.put("items", List.of(
                Map.of("insumo", "Quinoa", "stockActual", 120, "prediccion7d", 95),
                Map.of("insumo", "Camu camu", "stockActual", 45, "prediccion7d", 52)
        ));
        out.put("alertasCriticas", List.of("Camu camu: stock bajo en 7 días"));
        out.put("heatmap", Map.of("Lunes", 12, "Martes", 18, "Miércoles", 9));
        return out;
    }

    private double calcularPrecisionModelo() {
        ConfiguracionIa cfg = configuracionIaRepository.findById(1).orElse(null);
        if (cfg == null || cfg.getEntrenamientoErrorVal() == null) {
            return 0.0;
        }
        double errorVal = cfg.getEntrenamientoErrorVal().doubleValue();
        double precision = 100.0 - (errorVal * 100.0);
        return Math.max(0.0, Math.min(100.0, precision));
    }

    private Map<String, Object> construirGraficos(
            List<Map<String, Object>> items,
            LocalDateTime desde,
            LocalDateTime hasta,
            OffsetDateTime desdeUtc,
            OffsetDateTime hastaUtc
    ) {
        List<String> etiquetasBarras = new ArrayList<>();
        List<Double> valoresBarras = new ArrayList<>();
        for (Map<String, Object> item : items) {
            etiquetasBarras.add(String.valueOf(item.get("nombre")));
            Object num = item.get("valorNumerico");
            valoresBarras.add(num instanceof Number n ? n.doubleValue() : 0.0);
        }

        Map<LocalDate, Long> accesos = mapaPorDia(auditoriaAccesoRepository.contarAccesosPorDia(desdeUtc, hastaUtc));
        Map<LocalDate, Long> inferencias = mapaPorDiaLocal(inferenciaRecetaRepository.contarInferenciasPorDia(desde, hasta));
        Map<LocalDate, Long> errores = mapaPorDia(registroErrorRepository.contarErroresPorDia(desdeUtc, hastaUtc));

        List<String> fechas = new ArrayList<>();
        List<Long> serieAccesos = new ArrayList<>();
        List<Long> serieInferencias = new ArrayList<>();
        List<Long> serieErrores = new ArrayList<>();

        LocalDate cursor = desde.toLocalDate();
        LocalDate fin = hasta.toLocalDate();
        while (!cursor.isAfter(fin)) {
            fechas.add(cursor.toString());
            serieAccesos.add(accesos.getOrDefault(cursor, 0L));
            serieInferencias.add(inferencias.getOrDefault(cursor, 0L));
            serieErrores.add(errores.getOrDefault(cursor, 0L));
            cursor = cursor.plusDays(1);
        }

        Map<String, Object> barras = new LinkedHashMap<>();
        barras.put("etiquetas", etiquetasBarras);
        barras.put("valores", valoresBarras);

        Map<String, Object> actividad = new LinkedHashMap<>();
        actividad.put("fechas", fechas);
        actividad.put("accesos", serieAccesos);
        actividad.put("inferencias", serieInferencias);
        actividad.put("errores", serieErrores);

        Map<String, Object> graficos = new LinkedHashMap<>();
        graficos.put("barras", barras);
        graficos.put("actividadDiaria", actividad);
        return graficos;
    }

    private Map<LocalDate, Long> mapaPorDia(List<Object[]> filas) {
        Map<LocalDate, Long> mapa = new LinkedHashMap<>();
        if (filas == null) {
            return mapa;
        }
        for (Object[] fila : filas) {
            if (fila.length < 2 || fila[0] == null) {
                continue;
            }
            LocalDate dia = fila[0] instanceof java.sql.Date d
                    ? d.toLocalDate()
                    : LocalDate.parse(String.valueOf(fila[0]).substring(0, 10));
            long total = fila[1] instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(fila[1]));
            mapa.put(dia, total);
        }
        return mapa;
    }

    private Map<LocalDate, Long> mapaPorDiaLocal(List<Object[]> filas) {
        return mapaPorDia(filas);
    }

    private Map<String, Object> kpi(
            String nombre,
            String valor,
            double valorNumerico,
            String unidad,
            String formula,
            String tendencia
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", nombre);
        m.put("valor", valor);
        m.put("valorNumerico", redondear(valorNumerico));
        m.put("unidad", unidad);
        m.put("formula", formula);
        m.put("tendencia", tendencia);
        return m;
    }

    private double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatearPorcentaje(double valor) {
        return String.format(Locale.US, "%.1f%%", valor);
    }

    private String tendencia(double valor, double umbral) {
        return valor >= umbral ? "up" : "down";
    }

    private String tendenciaInversa(double valor, double umbral) {
        return valor <= umbral ? "up" : "down";
    }

    private OffsetDateTime aOffset(LocalDateTime fecha) {
        return fecha.atOffset(ZoneOffset.UTC);
    }
}
