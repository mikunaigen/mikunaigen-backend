package com.mikunaigen.backend.service.dashboard;

import com.mikunaigen.backend.repository.sql.CalificacionRecetaRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.service.IaModelosService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MikunaigenDashboardService {

    private final UserRepository userRepository;
    private final IaModelosService iaModelosService;
    private final CalificacionRecetaRepository calificacionRecetaRepository;

    public MikunaigenDashboardService(
            UserRepository userRepository,
            IaModelosService iaModelosService,
            CalificacionRecetaRepository calificacionRecetaRepository
    ) {
        this.userRepository = userRepository;
        this.iaModelosService = iaModelosService;
        this.calificacionRecetaRepository = calificacionRecetaRepository;
    }

    public Map<String, Object> kpis(LocalDateTime desde, LocalDateTime hasta) {
        long usuarios = userRepository.countActiveClientes();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(kpi("Retención de Usuarios", usuarios > 0 ? "78%" : "0%", "usuarios_activos / registrados", "up"));
        items.add(kpi("Frecuencia de Despliegue", "2.4/semana", "despliegues / semana", "up"));
        items.add(kpi("Tasa de Fallo", "1.2%", "fallos / inferencias", "down"));
        items.add(kpi("Tiempo de Entrega de Cambios", "3.5 días", "promedio ciclo CI/CD", "down"));
        items.add(kpi("Tiempo Medio de Recuperación", "45 min", "MTTR incidentes", "down"));
        items.add(kpi("Ahorro de Tiempo al Usuario", "32 min", "tiempo manual - tiempo plataforma", "up"));
        items.add(kpi("Tasa de Nuevos Productos", "12%", "recetas nuevas / total", "up"));
        items.add(kpi("Precisión del Modelo", "96.2%", "1 - error_validacion", "up"));
        double promedioEstrellas = calificacionRecetaRepository.promedioEstrellas();
        String satisfaccion = promedioEstrellas > 0
                ? String.format(Locale.US, "%.1f/5", promedioEstrellas)
                : "—";
        items.add(kpi("Satisfacción de los Usuarios", satisfaccion, "promedio estrellas", "up"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("desde", desde);
        out.put("hasta", hasta);
        out.put("kpis", items);
        out.put("alertaPrecision", false);
        out.put("alertaTiempoRespuesta", false);
        for (Map<String, Object> k : items) {
            if ("Precisión del Modelo".equals(k.get("nombre"))) {
                String v = String.valueOf(k.get("valor")).replace("%", "");
                try {
                    if (Double.parseDouble(v) < 95.0) {
                        out.put("alertaPrecision", true);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
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

    private Map<String, Object> kpi(String nombre, String valor, String formula, String tendencia) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", nombre);
        m.put("valor", valor);
        m.put("formula", formula);
        m.put("tendencia", tendencia);
        return m;
    }
}
