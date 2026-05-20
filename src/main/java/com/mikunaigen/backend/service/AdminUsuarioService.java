package com.mikunaigen.backend.service;

import com.mikunaigen.backend.exception.EmailDispatchException;
import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.model.sql.SolicitudCambioRol;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.repository.sql.RoleRepository;
import com.mikunaigen.backend.repository.sql.SolicitudCambioRolRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminUsuarioService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final SolicitudCambioRolRepository solicitudRepo;
    private final ConfiguracionGlobalRepository configRepo;
    private final EmailService emailService;
    private final CuentaUsuarioPushService cuentaPushService;

    public AdminUsuarioService(
            UserRepository userRepo,
            RoleRepository roleRepo,
            SolicitudCambioRolRepository solicitudRepo,
            ConfiguracionGlobalRepository configRepo,
            EmailService emailService,
            CuentaUsuarioPushService cuentaPushService
    ) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.solicitudRepo = solicitudRepo;
        this.configRepo = configRepo;
        this.emailService = emailService;
        this.cuentaPushService = cuentaPushService;
    }

    public List<Map<String, Object>> listarUsuarios(String busqueda, String rolFiltro, String planFiltro) {
        String q = busqueda != null ? busqueda.trim().toLowerCase(Locale.ROOT) : "";
        String rol = rolFiltro != null ? rolFiltro.trim().toLowerCase(Locale.ROOT) : "";
        String plan = planFiltro != null ? planFiltro.trim().toLowerCase(Locale.ROOT) : "";

        Map<UUID, SolicitudCambioRol> pendientes = solicitudRepo
                .findByEstadoIgnoreCaseOrderByFechaSolicitudDesc("pendiente")
                .stream()
                .collect(Collectors.toMap(
                        SolicitudCambioRol::getUsuarioId,
                        s -> s,
                        (a, b) -> a
                ));

        return userRepo.findAll().stream()
                .filter(this::esUsuarioGestionable)
                .filter(u -> coincideBusqueda(u, q))
                .filter(u -> rol.isBlank() || rol.equalsIgnoreCase(nombreRol(u)))
                .filter(u -> plan.isBlank() || plan.equalsIgnoreCase(nombreRol(u)))
                .sorted(Comparator.comparing(User::getFechaRegistro, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(u -> mapUsuarioAdmin(u, pendientes.get(u.getId())))
                .toList();
    }

    @Transactional
    public ResponseEntity<?> renovarSuscripcion(UUID userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        String rolNombre = nombreRol(user);
        if (!"emprendedor".equals(rolNombre) && !"nutricionista".equals(rolNombre)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "La renovación solo aplica a usuarios con plan Emprendedor o Nutricionista."));
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fin = user.getFechaFinPlan();
        if (fin == null || fin.isBefore(ahora)) {
            user.setFechaInicioPlan(ahora);
            user.setFechaFinPlan(ahora.plusDays(30));
        } else {
            user.setFechaFinPlan(fin.plusDays(30));
        }
        user.setActualizadoEn(ahora);
        userRepo.save(user);

        enviarCorreo(
                user,
                "Renovación de suscripción - Mikunaigen",
                "Tu suscripción al plan " + etiquetaRol(rolNombre)
                        + " fue renovada. Nueva fecha de vencimiento: "
                        + user.getFechaFinPlan().toLocalDate() + "."
        );

        return ResponseEntity.ok(Map.of(
                "message",
                "Renovación registrada. Vencimiento: " + user.getFechaFinPlan().toLocalDate(),
                "fechaFinPlan", user.getFechaFinPlan()
        ));
    }

    @Transactional
    public ResponseEntity<?> desactivarCuenta(UUID userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        if (!esUsuarioGestionable(user)) {
            return ResponseEntity.badRequest().body(Map.of("message", "No se puede desactivar esta cuenta."));
        }
        if ("suspendido".equalsIgnoreCase(user.getEstado())) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cuenta ya está suspendida."));
        }
        user.setDeleted(true);
        user.setActualizadoEn(LocalDateTime.now());
        userRepo.save(user);
        cuentaPushService.notificarCuentaSuspendida(user.getId());
        return ResponseEntity.ok(Map.of("message", "Cuenta desactivada correctamente."));
    }

    @Transactional
    public ResponseEntity<?> reactivarCuenta(UUID userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"suspendido".equalsIgnoreCase(user.getEstado())) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cuenta no está suspendida."));
        }
        user.setDeleted(false);
        if ("pendiente".equalsIgnoreCase(user.getEstado())) {
            user.setEstado("activo");
        }
        user.setActualizadoEn(LocalDateTime.now());
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", "Cuenta reactivada correctamente."));
    }

    private boolean esUsuarioGestionable(User u) {
        if (u == null || u.getRole() == null) {
            return false;
        }
        String r = nombreRol(u);
        return "estudiante".equals(r) || "emprendedor".equals(r) || "nutricionista".equals(r);
    }

    private boolean coincideBusqueda(User u, String q) {
        if (q.isBlank()) {
            return true;
        }
        String nombre = u.getFullName() != null ? u.getFullName().toLowerCase(Locale.ROOT) : "";
        String email = u.getEmail() != null ? u.getEmail().toLowerCase(Locale.ROOT) : "";
        String tel = u.getTelefono() != null ? u.getTelefono().toLowerCase(Locale.ROOT) : "";
        return nombre.contains(q) || email.contains(q) || tel.contains(q);
    }

    private Map<String, Object> mapUsuarioAdmin(User u, SolicitudCambioRol solicitudPendiente) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId().toString());
        m.put("nombre", u.getFullName());
        m.put("email", u.getEmail());
        m.put("telefono", u.getTelefono());
        m.put("rol", nombreRol(u));
        m.put("estadoSuscripcion", etiquetaRol(nombreRol(u)));
        m.put("estadoCuenta", estadoCuentaEtiqueta(u.getEstado()));
        m.put("estadoCuentaCodigo", u.getEstado());
        m.put("fechaRegistro", u.getFechaRegistro());
        m.put("fechaInicioPlan", u.getFechaInicioPlan());
        m.put("fechaFinPlan", u.getFechaFinPlan());
        m.put("puedeRenovar", puedeRenovar(u));
        m.put("suspendido", u.isDeleted());
        if (solicitudPendiente != null) {
            Map<String, Object> sol = new LinkedHashMap<>();
            sol.put("id", solicitudPendiente.getId());
            sol.put("estado", solicitudPendiente.getEstado());
            sol.put("justificacion", solicitudPendiente.getJustificacion());
            sol.put("fechaSolicitud", solicitudPendiente.getFechaSolicitud());
            sol.put("tieneComprobante", solicitudPendiente.getComprobantePagoBytea() != null
                    && solicitudPendiente.getComprobantePagoBytea().length > 0);
            roleRepo.findById(solicitudPendiente.getRolSolicitadoId())
                    .ifPresent(r -> sol.put("rolSolicitado", r.getNombre()));
            m.put("solicitudPendiente", sol);
        } else {
            m.put("solicitudPendiente", null);
        }
        return m;
    }

    private boolean puedeRenovar(User u) {
        String r = nombreRol(u);
        return ("emprendedor".equals(r) || "nutricionista".equals(r))
                && "activo".equalsIgnoreCase(u.getEstado());
    }

    private String nombreRol(User u) {
        if (u.getRole() == null || u.getRole().getNombre() == null) {
            return "";
        }
        String r = u.getRole().getNombre().trim().toLowerCase(Locale.ROOT);
        if ("cliente".equals(r)) {
            return "estudiante";
        }
        return r;
    }

    private String etiquetaRol(String rol) {
        return switch (rol) {
            case "estudiante" -> "Estudiante";
            case "emprendedor" -> "Emprendedor";
            case "nutricionista" -> "Nutricionista";
            default -> rol;
        };
    }

    private String estadoCuentaEtiqueta(String estado) {
        if (estado == null) {
            return "—";
        }
        return switch (estado.toLowerCase(Locale.ROOT)) {
            case "activo" -> "Activa";
            case "suspendido" -> "Suspendida";
            case "pendiente" -> "Pendiente activación";
            default -> estado;
        };
    }

    private void enviarCorreo(User user, String asunto, String cuerpo) {
        if (user == null || user.getEmail() == null) {
            return;
        }
        ConfiguracionGlobal config = configRepo.findById(1).orElse(null);
        if (config == null || config.getSmtpEmail() == null || config.getSmtpEmail().isBlank()
                || !"activo".equalsIgnoreCase(config.getSmtpEstado())) {
            return;
        }
        try {
            emailService.enviarCorreoTextoPlano(
                    user.getEmail(),
                    asunto,
                    cuerpo,
                    config.getSmtpEmail(),
                    config.getSmtpContrasenaApp(),
                    user.getId().toString()
            );
        } catch (EmailDispatchException | IllegalStateException ignored) {
        }
    }
}
