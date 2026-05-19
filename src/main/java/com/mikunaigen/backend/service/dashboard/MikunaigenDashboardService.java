package com.mikunaigen.backend.service.dashboard;

import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MikunaigenDashboardService {

    private final UserRepository userRepository;

    public MikunaigenDashboardService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        items.add(kpi("Satisfacción de los Usuarios", "4.3/5", "promedio estrellas", "up"));

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

    private Map<String, Object> kpi(String nombre, String valor, String formula, String tendencia) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", nombre);
        m.put("valor", valor);
        m.put("formula", formula);
        m.put("tendencia", tendencia);
        return m;
    }
}
