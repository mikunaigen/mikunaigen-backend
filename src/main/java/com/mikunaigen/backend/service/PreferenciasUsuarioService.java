package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.AlimentoDataset;
import com.mikunaigen.backend.model.sql.PreferenciasUsuario;
import com.mikunaigen.backend.model.sql.RestriccionIngrediente;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.AlimentoDatasetRepository;
import com.mikunaigen.backend.repository.sql.PreferenciasUsuarioRepository;
import com.mikunaigen.backend.repository.sql.RestriccionIngredienteRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class PreferenciasUsuarioService {

    private static final String ENFOQUE_PRECISION = "maxima_precision_nutricional";
    private static final String ENFOQUE_COSTO = "minimo_costo_produccion";
    private static final String ENFOQUE_BIODIVERSIDAD = "maxima_biodiversidad";
    private static final String TIPO_EXCLUIR = "excluir";

    private final PreferenciasUsuarioRepository preferenciasRepo;
    private final RestriccionIngredienteRepository restriccionRepo;
    private final AlimentoDatasetRepository alimentoRepo;
    private final UserRepository userRepo;

    public PreferenciasUsuarioService(
            PreferenciasUsuarioRepository preferenciasRepo,
            RestriccionIngredienteRepository restriccionRepo,
            AlimentoDatasetRepository alimentoRepo,
            UserRepository userRepo
    ) {
        this.preferenciasRepo = preferenciasRepo;
        this.restriccionRepo = restriccionRepo;
        this.alimentoRepo = alimentoRepo;
        this.userRepo = userRepo;
    }

    public Map<String, Object> obtenerContexto(UUID usuarioId) {
        User user = cargarUsuario(usuarioId);
        String rol = normalizarRol(user.getRole() != null ? user.getRole().getNombre() : "estudiante");
        PreferenciasUsuario pref = obtenerOCrearPreferencias(usuarioId);
        Map<String, Object> capacidades = capacidadesPorRol(rol);
        return Map.of(
                "rol", rol,
                "requiereConfiguracion", !pref.isPreferenciasCompletadas(),
                "capacidades", capacidades,
                "preferencias", mapaPreferencias(pref, usuarioId)
        );
    }

    public Map<String, Object> obtenerPreferencias(UUID usuarioId) {
        User user = cargarUsuario(usuarioId);
        String rol = normalizarRol(user.getRole() != null ? user.getRole().getNombre() : "estudiante");
        PreferenciasUsuario pref = obtenerOCrearPreferencias(usuarioId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rol", rol);
        out.put("capacidades", capacidadesPorRol(rol));
        out.put("preferencias", mapaPreferencias(pref, usuarioId));
        out.put("requiereConfiguracion", !pref.isPreferenciasCompletadas());
        return out;
    }

    @Transactional
    public Map<String, Object> guardarPreferencias(UUID usuarioId, Map<String, Object> body) {
        User user = cargarUsuario(usuarioId);
        String rol = normalizarRol(user.getRole() != null ? user.getRole().getNombre() : "estudiante");
        Map<String, Object> cap = capacidadesPorRol(rol);

        PreferenciasUsuario pref = obtenerOCrearPreferencias(usuarioId);

        String enfoque = stringOrNull(body.get("enfoquePrincipal"));
        if (enfoque == null || enfoque.isBlank()) {
            enfoque = ENFOQUE_PRECISION;
        }
        validarEnfoque(enfoque, cap);
        pref.setEnfoquePrincipal(enfoque);

        if (Boolean.TRUE.equals(cap.get("puedePresupuesto"))) {
            Object pres = body.get("presupuestoMaximo");
            if (pres != null && !"".equals(String.valueOf(pres).trim())) {
                try {
                    BigDecimal valor = new BigDecimal(String.valueOf(pres).trim());
                    if (valor.compareTo(BigDecimal.ZERO) < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El presupuesto debe ser mayor o igual a cero.");
                    }
                    pref.setPresupuestoMaximo(valor);
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Presupuesto inválido.");
                }
            } else {
                pref.setPresupuestoMaximo(null);
            }
        } else {
            pref.setPresupuestoMaximo(null);
        }

        if (Boolean.TRUE.equals(cap.get("puedeEstacionalidad"))) {
            pref.setFiltroEstacionalidadActivo(Boolean.TRUE.equals(body.get("filtroEstacionalidadActivo")));
        } else {
            pref.setFiltroEstacionalidadActivo(false);
        }

        if (Boolean.TRUE.equals(cap.get("puedeExcluirIngredientes"))) {
            List<Integer> ids = parsearIds(body.get("ingredientesExcluidos"));
            int max = (int) cap.get("maxExclusiones");
            if (ids.size() > max) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Máximo " + max + " ingredientes excluidos para tu plan.");
            }
            for (Integer id : ids) {
                if (!alimentoRepo.existsById(id)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingrediente no válido: " + id);
                }
            }
            restriccionRepo.deleteByUsuarioIdAndTipoIgnoreCase(usuarioId, TIPO_EXCLUIR);
            for (Integer id : ids) {
                RestriccionIngrediente r = new RestriccionIngrediente();
                r.setUsuarioId(usuarioId);
                r.setAlimentoId(id);
                r.setTipo(TIPO_EXCLUIR);
                restriccionRepo.save(r);
            }
        }

        pref.setPreferenciasCompletadas(true);
        preferenciasRepo.save(pref);

        return Map.of(
                "message", "Preferencias guardadas correctamente.",
                "preferencias", mapaPreferencias(pref, usuarioId),
                "requiereConfiguracion", false
        );
    }

    public List<Map<String, Object>> buscarAlimentos(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return alimentoRepo.buscar(q.trim()).stream()
                .limit(25)
                .map(this::mapaAlimento)
                .toList();
    }

    @Transactional
    public void asegurarPreferenciasIniciales(UUID usuarioId) {
        if (!preferenciasRepo.existsById(usuarioId)) {
            PreferenciasUsuario p = new PreferenciasUsuario();
            p.setUsuarioId(usuarioId);
            p.setEnfoquePrincipal(ENFOQUE_PRECISION);
            p.setPreferenciasCompletadas(false);
            preferenciasRepo.save(p);
        }
    }

    private PreferenciasUsuario obtenerOCrearPreferencias(UUID usuarioId) {
        return preferenciasRepo.findById(usuarioId).orElseGet(() -> {
            PreferenciasUsuario p = new PreferenciasUsuario();
            p.setUsuarioId(usuarioId);
            p.setEnfoquePrincipal(ENFOQUE_PRECISION);
            p.setPreferenciasCompletadas(false);
            return preferenciasRepo.save(p);
        });
    }

    private User cargarUsuario(UUID usuarioId) {
        return userRepo.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
    }

    private Map<String, Object> mapaPreferencias(PreferenciasUsuario pref, UUID usuarioId) {
        List<Map<String, Object>> exclusiones = restriccionRepo
                .findByUsuarioIdAndTipoIgnoreCase(usuarioId, TIPO_EXCLUIR)
                .stream()
                .map(r -> {
                    Optional<AlimentoDataset> al = alimentoRepo.findById(r.getAlimentoId());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("alimentoId", r.getAlimentoId());
                    al.ifPresent(a -> {
                        m.put("nombre", a.getNombre());
                        m.put("categoria", a.getCategoria());
                    });
                    return m;
                })
                .toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enfoquePrincipal", pref.getEnfoquePrincipal() != null ? pref.getEnfoquePrincipal() : ENFOQUE_PRECISION);
        m.put("presupuestoMaximo", pref.getPresupuestoMaximo());
        m.put("filtroEstacionalidadActivo", pref.isFiltroEstacionalidadActivo());
        m.put("preferenciasCompletadas", pref.isPreferenciasCompletadas());
        m.put("ingredientesExcluidos", exclusiones);
        return m;
    }

    private Map<String, Object> mapaAlimento(AlimentoDataset a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("nombre", a.getNombre());
        m.put("categoria", a.getCategoria());
        return m;
    }

    private Map<String, Object> capacidadesPorRol(String rol) {
        Map<String, Object> c = new LinkedHashMap<>();
        switch (rol) {
            case "emprendedor" -> {
                c.put("puedeMinimoCosto", true);
                c.put("puedeMaximaBiodiversidad", true);
                c.put("puedePresupuesto", true);
                c.put("puedeExcluirIngredientes", true);
                c.put("puedeEstacionalidad", true);
                c.put("maxExclusiones", 30);
            }
            case "nutricionista" -> {
                c.put("puedeMinimoCosto", true);
                c.put("puedeMaximaBiodiversidad", true);
                c.put("puedePresupuesto", true);
                c.put("puedeExcluirIngredientes", true);
                c.put("puedeEstacionalidad", true);
                c.put("maxExclusiones", 50);
            }
            default -> {
                c.put("puedeMinimoCosto", false);
                c.put("puedeMaximaBiodiversidad", false);
                c.put("puedePresupuesto", false);
                c.put("puedeExcluirIngredientes", true);
                c.put("puedeEstacionalidad", false);
                c.put("maxExclusiones", 10);
            }
        }
        c.put("mensajePresupuestoBloqueado", "Disponible en Plan Emprendedor y Nutricionista");
        return c;
    }

    private void validarEnfoque(String enfoque, Map<String, Object> cap) {
        if (!ENFOQUE_PRECISION.equals(enfoque)
                && !ENFOQUE_COSTO.equals(enfoque)
                && !ENFOQUE_BIODIVERSIDAD.equals(enfoque)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enfoque principal no válido.");
        }
        if (ENFOQUE_COSTO.equals(enfoque) && !Boolean.TRUE.equals(cap.get("puedeMinimoCosto"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enfoque no disponible para tu plan.");
        }
        if (ENFOQUE_BIODIVERSIDAD.equals(enfoque) && !Boolean.TRUE.equals(cap.get("puedeMaximaBiodiversidad"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enfoque no disponible para tu plan.");
        }
    }

    private String normalizarRol(String rol) {
        if (rol == null) return "estudiante";
        String r = rol.trim().toLowerCase(Locale.ROOT);
        if ("cliente".equals(r)) return "estudiante";
        return r;
    }

    private String stringOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private List<Integer> parsearIds(Object raw) {
        if (raw == null) return List.of();
        List<Integer> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number n) {
                    ids.add(n.intValue());
                } else if (item instanceof Map<?, ?> map && map.get("alimentoId") != null) {
                    ids.add(Integer.parseInt(String.valueOf(map.get("alimentoId"))));
                } else if (item != null) {
                    ids.add(Integer.parseInt(String.valueOf(item)));
                }
            }
        }
        return ids.stream().distinct().toList();
    }
}
