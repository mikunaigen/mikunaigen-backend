package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.InferenciaRecetaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FormulacionCuotaService {

    private final InferenciaRecetaRepository inferenciaRepo;

    public FormulacionCuotaService(InferenciaRecetaRepository inferenciaRepo) {
        this.inferenciaRepo = inferenciaRepo;
    }

    public Map<String, Object> estadoCuota(User user) {
        String rol = normalizarRol(user);
        int limite = limiteInferencias(rol);
        int limiteHistorial = limiteHistorial(rol);
        long usadas = inferenciaRepo.contarInferenciasMes(user.getId(), inicioMesActual());
        long historial = inferenciaRepo.contarHistorial(user.getId());
        YearMonth mes = YearMonth.now();
        LocalDate reinicio = mes.atEndOfMonth().plusDays(1);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rol", rol);
        out.put("limiteInferencias", limite);
        out.put("inferenciasUsadas", usadas);
        out.put("inferenciasDisponibles", Math.max(0, limite - usadas));
        out.put("cuotaAgotada", usadas >= limite);
        out.put("fechaReinicioCuota", reinicio.toString());
        out.put("limiteHistorial", limiteHistorial);
        out.put("historialUsado", historial);
        out.put("historialDisponible", Math.max(0, limiteHistorial - historial));
        out.put("historialLleno", historial >= limiteHistorial);
        out.put("historialBloqueadoPorPlan", historial > limiteHistorial);
        return out;
    }

    public void verificarCuotaDisponible(User user) {
        Map<String, Object> estado = estadoCuota(user);
        if (Boolean.TRUE.equals(estado.get("cuotaAgotada"))) {
            throw new IllegalStateException(
                    "Has alcanzado tu límite mensual de " + estado.get("limiteInferencias")
                            + " inferencias. Reinicio: " + estado.get("fechaReinicioCuota") + ".");
        }
    }

    public int limiteInferencias(String rol) {
        return switch (rol) {
            case "emprendedor" -> 20;
            case "nutricionista" -> 50;
            default -> 2;
        };
    }

    public int limiteHistorial(String rol) {
        return switch (rol) {
            case "emprendedor" -> 50;
            case "nutricionista" -> 100;
            default -> 2;
        };
    }

    public String normalizarRol(User user) {
        if (user.getRole() == null || user.getRole().getNombre() == null) {
            return "estudiante";
        }
        return user.getRole().getNombre().trim().toLowerCase();
    }

    private LocalDateTime inicioMesActual() {
        LocalDate hoy = LocalDate.now();
        return LocalDateTime.of(hoy.withDayOfMonth(1), LocalTime.MIN);
    }
}
