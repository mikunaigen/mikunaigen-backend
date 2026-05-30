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
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class ParametrizacionFormulacionService {

    public static final String ENFOQUE_PRECISION = "maxima_precision_nutricional";
    public static final String ENFOQUE_COSTO = "minimo_costo_produccion";
    public static final String ENFOQUE_BIODIVERSIDAD = "maxima_biodiversidad";
    private static final String TIPO_EXCLUIR = "excluir";
    private static final String TIPO_PRIORIZAR = "priorizar";

    private static final Set<String> CATEGORIAS = Set.of(
            "Cereales", "Verduras", "Frutas", "Grasas", "Pescados", "Carnes",
            "Leche", "Bebidas", "Huevos", "Azucarados", "Preparados", "Leguminosas", "Tubérculos"
    );

    private final PreferenciasUsuarioRepository preferenciasRepo;
    private final RestriccionIngredienteRepository restriccionRepo;
    private final AlimentoDatasetRepository alimentoRepo;
    private final UserRepository userRepo;

    public ParametrizacionFormulacionService(
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
        Map<String, Object> cap = capacidadesPorRol(rol);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rol", rol);
        out.put("capacidades", cap);
        out.put("parametrizacion", mapaParametrizacion(pref, usuarioId));
        out.put("categorias", CATEGORIAS.stream().sorted().toList());
        out.put("rangosPresupuesto", rangosPresupuesto());
        out.put("cabezasOptimizacion", definicionesCabezas());
        return out;
    }

    @Transactional
    public Map<String, Object> guardar(UUID usuarioId, Map<String, Object> body) {
        User user = cargarUsuario(usuarioId);
        String rol = normalizarRol(user.getRole() != null ? user.getRole().getNombre() : "estudiante");
        Map<String, Object> cap = capacidadesPorRol(rol);
        PreferenciasUsuario pref = obtenerOCrearPreferencias(usuarioId);

        List<String> cabezas = parsearCabezas(body.get("cabezasOptimizacion"));
        validarCabezas(cabezas, cap);
        pref.setCabezasOptimizacion(cabezas.toArray(new String[0]));
        pref.setEnfoquePrincipal(cabezas.get(0));

        Map<String, String> erroresPresupuesto = validarPresupuesto(body.get("presupuestoMaximo"), cap);
        if (!erroresPresupuesto.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Revisa el presupuesto máximo por kg."
            );
        }
        if (Boolean.TRUE.equals(cap.get("puedePresupuesto"))) {
            Object pres = body.get("presupuestoMaximo");
            pref.setPresupuestoMaximo(new BigDecimal(String.valueOf(pres).trim()));
        } else {
            pref.setPresupuestoMaximo(null);
        }

        if (Boolean.TRUE.equals(cap.get("puedeEstacionalidad"))) {
            pref.setFiltroEstacionalidadActivo(Boolean.TRUE.equals(body.get("filtroEstacionalidadActivo")));
        } else {
            pref.setFiltroEstacionalidadActivo(false);
        }

        guardarRestricciones(usuarioId, body, cap);
        pref.setPreferenciasCompletadas(true);
        preferenciasRepo.save(pref);

        return Map.of(
                "message", "Parametrización guardada correctamente.",
                "parametrizacion", mapaParametrizacion(pref, usuarioId)
        );
    }

    public List<Map<String, Object>> buscarAlimentos(String q, String categoria) {
        List<AlimentoDataset> lista;
        if (categoria != null && !categoria.isBlank()) {
            lista = alimentoRepo.findByCategoriaIgnoreCase(categoria.trim());
            if (q != null && !q.isBlank()) {
                String busqueda = q.trim().toLowerCase(Locale.ROOT);
                lista = lista.stream()
                        .filter(a -> a.getNombre().toLowerCase(Locale.ROOT).contains(busqueda)
                                || (a.getCodigoMinsa() != null && a.getCodigoMinsa().toLowerCase(Locale.ROOT).contains(busqueda)))
                        .toList();
            }
        } else if (q != null && !q.isBlank()) {
            lista = alimentoRepo.buscar(q.trim());
        } else {
            lista = List.of();
        }
        return lista.stream().limit(30).map(this::mapaAlimentoBusqueda).toList();
    }

    public Map<String, Object> detalleAlimento(Integer id) {
        AlimentoDataset a = alimentoRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingrediente no encontrado."));
        return mapaNutricionalCompleto(a);
    }

    private void guardarRestricciones(UUID usuarioId, Map<String, Object> body, Map<String, Object> cap) {
        if (!Boolean.TRUE.equals(cap.get("puedeExcluirIngredientes"))) {
            restriccionRepo.deleteByUsuarioIdAndTipoIgnoreCase(usuarioId, TIPO_EXCLUIR);
            restriccionRepo.deleteByUsuarioIdAndTipoIgnoreCase(usuarioId, TIPO_PRIORIZAR);
            return;
        }

        List<Integer> excluidos = parsearIds(body.get("ingredientesExcluidos"));
        List<Integer> priorizados = Boolean.TRUE.equals(cap.get("puedePriorizarIngredientes"))
                ? parsearIds(body.get("ingredientesPriorizados"))
                : List.of();

        int maxEx = (int) cap.get("maxExclusiones");
        int maxPri = (int) cap.get("maxPriorizados");
        if (excluidos.size() > maxEx) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Máximo " + maxEx + " ingredientes excluidos.");
        }
        if (priorizados.size() > maxPri) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Máximo " + maxPri + " ingredientes priorizados.");
        }

        Set<Integer> conflicto = new HashSet<>(excluidos);
        conflicto.retainAll(priorizados);
        if (!conflicto.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un ingrediente no puede estar excluido y priorizado a la vez.");
        }

        for (Integer id : excluidos) {
            validarAlimento(id);
        }
        for (Integer id : priorizados) {
            validarAlimento(id);
        }

        restriccionRepo.deleteByUsuarioIdAndTipoIgnoreCase(usuarioId, TIPO_EXCLUIR);
        restriccionRepo.deleteByUsuarioIdAndTipoIgnoreCase(usuarioId, TIPO_PRIORIZAR);

        for (Integer id : excluidos) {
            restriccionRepo.save(restriccion(usuarioId, id, TIPO_EXCLUIR));
        }
        for (Integer id : priorizados) {
            restriccionRepo.save(restriccion(usuarioId, id, TIPO_PRIORIZAR));
        }
    }

    private RestriccionIngrediente restriccion(UUID usuarioId, Integer alimentoId, String tipo) {
        RestriccionIngrediente r = new RestriccionIngrediente();
        r.setUsuarioId(usuarioId);
        r.setAlimentoId(alimentoId);
        r.setTipo(tipo);
        return r;
    }

    private void validarAlimento(Integer id) {
        if (!alimentoRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingrediente no válido: " + id);
        }
    }

    private Map<String, String> validarPresupuesto(Object raw, Map<String, Object> cap) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (!Boolean.TRUE.equals(cap.get("puedePresupuesto"))) {
            return errores;
        }
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            errores.put("presupuestoMaximo", "El costo máximo por kg es obligatorio.");
            return errores;
        }
        try {
            BigDecimal n = new BigDecimal(String.valueOf(raw).trim().replace(",", "."));
            if (n.compareTo(BigDecimal.ZERO) <= 0) {
                errores.put("presupuestoMaximo", "Debe ser un valor numérico positivo.");
            }
        } catch (NumberFormatException e) {
            errores.put("presupuestoMaximo", "Debe ser un valor numérico válido.");
        }
        return errores;
    }

    private void validarCabezas(List<String> cabezas, Map<String, Object> cap) {
        int max = (int) cap.get("maxCabezasOptimizacion");
        int min = (int) cap.get("minCabezasOptimizacion");
        if (cabezas.isEmpty() || cabezas.size() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes seleccionar entre " + min + " y " + max + " modo(s) de optimización.");
        }
        for (String c : cabezas) {
            if (ENFOQUE_COSTO.equals(c) && !Boolean.TRUE.equals(cap.get("puedeMinimoCosto"))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Mínimo Costo de Producción no está disponible en tu plan.");
            }
            if (ENFOQUE_BIODIVERSIDAD.equals(c) && !Boolean.TRUE.equals(cap.get("puedeMaximaBiodiversidad"))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Máxima Biodiversidad no está disponible en tu plan.");
            }
            if (!ENFOQUE_PRECISION.equals(c) && !ENFOQUE_COSTO.equals(c) && !ENFOQUE_BIODIVERSIDAD.equals(c)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modo de optimización no válido.");
            }
        }
    }

    private List<String> parsearCabezas(Object raw) {
        if (raw == null) {
            return List.of(ENFOQUE_PRECISION);
        }
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(String.valueOf(o).trim());
                }
            }
        }
        if (out.isEmpty()) {
            return List.of(ENFOQUE_PRECISION);
        }
        return out.stream().distinct().toList();
    }

    private Map<String, Object> mapaParametrizacion(PreferenciasUsuario pref, UUID usuarioId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cabezasOptimizacion", pref.getCabezasOptimizacion() != null
                ? Arrays.asList(pref.getCabezasOptimizacion())
                : List.of(ENFOQUE_PRECISION));
        m.put("enfoquePrincipal", pref.getEnfoquePrincipal());
        m.put("presupuestoMaximo", pref.getPresupuestoMaximo());
        m.put("filtroEstacionalidadActivo", pref.isFiltroEstacionalidadActivo());
        m.put("ingredientesExcluidos", mapaRestricciones(usuarioId, TIPO_EXCLUIR));
        m.put("ingredientesPriorizados", mapaRestricciones(usuarioId, TIPO_PRIORIZAR));
        m.put("mensajePresupuestoExcedido",
                "No fue posible formular una receta dentro del límite indicado. Intenta aumentar el presupuesto máximo por kg.");
        m.put("parametrizacionCompletada", pref.isPreferenciasCompletadas());
        return m;
    }

    private List<Map<String, Object>> mapaRestricciones(UUID usuarioId, String tipo) {
        return restriccionRepo.findByUsuarioIdAndTipoIgnoreCase(usuarioId, tipo).stream()
                .map(r -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("alimentoId", r.getAlimentoId());
                    alimentoRepo.findById(r.getAlimentoId()).ifPresent(a -> {
                        item.put("nombre", a.getNombre());
                        item.put("categoria", a.getCategoria());
                    });
                    return item;
                })
                .toList();
    }

    private Map<String, Object> capacidadesPorRol(String rol) {
        Map<String, Object> c = new LinkedHashMap<>();
        String mes = Month.from(java.time.LocalDate.now()).getDisplayName(TextStyle.FULL, new Locale("es", "PE"));
        mes = mes.substring(0, 1).toUpperCase(Locale.ROOT) + mes.substring(1);
        c.put("nombreMesActual", mes);
        c.put("mensajePlanBloqueado", "Disponible en Plan Emprendedor y Nutricionista");
        switch (rol) {
            case "emprendedor" -> {
                c.put("puedeMinimoCosto", true);
                c.put("puedeMaximaBiodiversidad", true);
                c.put("puedePresupuesto", true);
                c.put("puedeExcluirIngredientes", true);
                c.put("puedePriorizarIngredientes", true);
                c.put("puedeEstacionalidad", true);
                c.put("maxExclusiones", 30);
                c.put("maxPriorizados", 15);
                c.put("maxCabezasOptimizacion", 2);
                c.put("minCabezasOptimizacion", 1);
            }
            case "nutricionista" -> {
                c.put("puedeMinimoCosto", true);
                c.put("puedeMaximaBiodiversidad", true);
                c.put("puedePresupuesto", true);
                c.put("puedeExcluirIngredientes", true);
                c.put("puedePriorizarIngredientes", true);
                c.put("puedeEstacionalidad", true);
                c.put("maxExclusiones", 50);
                c.put("maxPriorizados", 25);
                c.put("maxCabezasOptimizacion", 3);
                c.put("minCabezasOptimizacion", 1);
            }
            default -> {
                c.put("puedeMinimoCosto", false);
                c.put("puedeMaximaBiodiversidad", false);
                c.put("puedePresupuesto", false);
                c.put("puedeExcluirIngredientes", true);
                c.put("puedePriorizarIngredientes", false);
                c.put("puedeEstacionalidad", false);
                c.put("maxExclusiones", 10);
                c.put("maxPriorizados", 0);
                c.put("maxCabezasOptimizacion", 1);
                c.put("minCabezasOptimizacion", 1);
            }
        }
        return c;
    }

    private List<Map<String, Object>> rangosPresupuesto() {
        return List.of(
                Map.of("tipo", "Económica", "valor", 8.00, "etiqueta", "Económica S/ 8.00"),
                Map.of("tipo", "Regular", "valor", 15.00, "etiqueta", "Regular S/ 15.00"),
                Map.of("tipo", "Premium", "valor", 20.00, "etiqueta", "Premium S/ 20.00")
        );
    }

    private List<Map<String, Object>> definicionesCabezas() {
        return List.of(
                cabeza(ENFOQUE_PRECISION, "Máxima Precisión Nutricional",
                        "Prioriza el ajuste exacto al objetivo nutricional.",
                        "Puede elevar el costo o reducir la variedad de ingredientes.",
                        "heroBeaker"),
                cabeza(ENFOQUE_COSTO, "Mínimo Costo de Producción",
                        "Busca la formulación más económica por kilogramo.",
                        "Puede alejarse del objetivo nutricional ideal.",
                        "heroBanknotes"),
                cabeza(ENFOQUE_BIODIVERSIDAD, "Máxima Biodiversidad",
                        "Favorece variedad de ingredientes nativos y andinos.",
                        "Puede aumentar costo o dificultar el ajuste nutricional estricto.",
                        "heroSparkles")
        );
    }

    private Map<String, Object> cabeza(String codigo, String titulo, String prioriza, String sacrifica, String icono) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("titulo", titulo);
        m.put("prioriza", prioriza);
        m.put("sacrifica", sacrifica);
        m.put("icon", icono);
        return m;
    }

    private Map<String, Object> mapaAlimentoBusqueda(AlimentoDataset a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("nombre", a.getNombre());
        m.put("categoria", a.getCategoria());
        m.put("codigo_minsa", a.getCodigoMinsa());
        return m;
    }

    private Map<String, Object> mapaNutricionalCompleto(AlimentoDataset a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("nombre", a.getNombre());
        m.put("categoria", a.getCategoria());
        m.put("energia_kcal", a.getEnergiaKcal());
        m.put("agua_g", a.getAguaG());
        m.put("proteinas_g", a.getProteinasG());
        m.put("grasa_total_g", a.getGrasaTotalG());
        m.put("carbohidratos_disponibles_g", a.getCarbohidratosDisponiblesG());
        m.put("fibra_g", a.getFibraG());
        m.put("cenizas_g", a.getCenizasG());
        m.put("calcio_mg", a.getCalcioMg());
        m.put("fosforo_mg", a.getFosforoMg());
        m.put("zinc_mg", a.getZincMg());
        m.put("hierro_mg", a.getHierroMg());
        m.put("beta_caroteno_ug", a.getBetaCarotenoUg());
        m.put("vitamina_a_ug", a.getVitaminaAUg());
        m.put("tiamina_mg", a.getTiaminaMg());
        m.put("riboflavina_mg", a.getRiboflavinaMg());
        m.put("niacina_mg", a.getNiacinaMg());
        m.put("vitamina_c_mg", a.getVitaminaCMg());
        m.put("acido_folico_ug", a.getAcidoFolicoUg());
        m.put("sodio_mg", a.getSodioMg());
        m.put("potasio_mg", a.getPotasioMg());
        m.put("costo_kg_soles", a.getCostoKgSoles());
        return m;
    }

    private PreferenciasUsuario obtenerOCrearPreferencias(UUID usuarioId) {
        return preferenciasRepo.findById(usuarioId).orElseGet(() -> {
            PreferenciasUsuario p = new PreferenciasUsuario();
            p.setUsuarioId(usuarioId);
            p.setEnfoquePrincipal(ENFOQUE_PRECISION);
            p.setCabezasOptimizacion(new String[]{ENFOQUE_PRECISION});
            return preferenciasRepo.save(p);
        });
    }

    private User cargarUsuario(UUID usuarioId) {
        return userRepo.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
    }

    private String normalizarRol(String rol) {
        if (rol == null) return "estudiante";
        String r = rol.trim().toLowerCase(Locale.ROOT);
        if ("cliente".equals(r)) return "estudiante";
        return r;
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
