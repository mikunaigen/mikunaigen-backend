package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.InferenciaReceta;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.InferenciaRecetaRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class AdminAuditoriaSeguridadService {

    private final InferenciaRecetaRepository inferenciaRepo;
    private final UserRepository userRepo;

    public AdminAuditoriaSeguridadService(
            InferenciaRecetaRepository inferenciaRepo,
            UserRepository userRepo
    ) {
        this.inferenciaRepo = inferenciaRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> consultar(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String usuario,
            String componente
    ) {
        LocalDateTime desde = fechaDesde != null ? fechaDesde.atStartOfDay() : null;
        LocalDateTime hasta = fechaHasta != null ? fechaHasta.atTime(LocalTime.MAX) : null;
        String usuarioFiltro = usuario != null ? usuario.trim() : "";
        String componenteFiltro = componente != null ? componente.trim() : "";

        List<InferenciaReceta> registros = inferenciaRepo.listarDescartadasSeguridad(
                desde, hasta,
                usuarioFiltro.isEmpty() ? null : usuarioFiltro,
                componenteFiltro.isEmpty() ? null : componenteFiltro
        );

        Set<String> alertasUsuarioDia = cargarAlertasUsuarioDia();
        Map<UUID, User> usuarios = cargarUsuarios(registros);

        List<Map<String, Object>> filas = new ArrayList<>();
        for (InferenciaReceta inf : registros) {
            User u = usuarios.get(inf.getUsuarioId());
            LocalDate dia = inf.getFechaGeneracion() != null
                    ? inf.getFechaGeneracion().toLocalDate()
                    : null;
            String claveAlerta = inf.getUsuarioId() + "|" + (dia != null ? dia : "");
            filas.add(mapFila(inf, u, alertasUsuarioDia.contains(claveAlerta)));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registros", filas);
        out.put("total", filas.size());
        out.put("descartadasPorMes", mapearDescartadasPorMes());
        return out;
    }

    private List<Map<String, Object>> mapearDescartadasPorMes() {
        List<Map<String, Object>> meses = new ArrayList<>();
        for (Object[] row : inferenciaRepo.contarDescartadasPorMesRaw()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("mes", row[0]);
            item.put("total", row[1]);
            meses.add(item);
        }
        return meses;
    }

    private Set<String> cargarAlertasUsuarioDia() {
        Set<String> alertas = new HashSet<>();
        for (Object[] row : inferenciaRepo.usuariosConAlertaDescartadasDia()) {
            alertas.add(String.valueOf(row[0]) + "|" + String.valueOf(row[1]));
        }
        return alertas;
    }

    private Map<UUID, User> cargarUsuarios(List<InferenciaReceta> registros) {
        Set<UUID> ids = new HashSet<>();
        for (InferenciaReceta inf : registros) {
            ids.add(inf.getUsuarioId());
        }
        Map<UUID, User> mapa = new HashMap<>();
        if (ids.isEmpty()) {
            return mapa;
        }
        for (User u : userRepo.findAllById(ids)) {
            mapa.put(u.getId(), u);
        }
        return mapa;
    }

    private Map<String, Object> mapFila(InferenciaReceta inf, User u, boolean alertaUsuarioDia) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inf.getId());
        m.put("fecha", inf.getFechaGeneracion());
        m.put("usuarioId", inf.getUsuarioId());
        m.put("usuarioEmail", u != null ? u.getEmail() : null);
        m.put("usuarioNombre", nombreUsuario(u));
        m.put("parametrosIngresados", inf.getInputParametros());
        m.put("componenteInfractor", inf.getComponenteInfractor());
        m.put("valorInfractor", inf.getValorInfractor());
        m.put("modoOptimizacion", inf.getModoOptimizacion());
        m.put("alertaUsuarioDia", alertaUsuarioDia);
        return m;
    }

    private String nombreUsuario(User u) {
        if (u == null) {
            return "—";
        }
        if (u.getNombres() != null && u.getApellidos() != null) {
            return (u.getNombres() + " " + u.getApellidos()).trim();
        }
        return u.getFullName() != null ? u.getFullName() : u.getEmail();
    }
}
