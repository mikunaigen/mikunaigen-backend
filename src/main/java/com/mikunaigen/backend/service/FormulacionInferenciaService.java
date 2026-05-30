package com.mikunaigen.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.model.sql.*;
import com.mikunaigen.backend.repository.sql.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class FormulacionInferenciaService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final List<String> ORDEN_PERFIL = List.of(
            "energia_kcal", "agua_g", "proteinas_g", "grasa_total_g",
            "carbohidratos_disponibles_g", "fibra_dietaria_g", "cenizas_g",
            "calcio_mg", "fosforo_mg", "zinc_mg", "hierro_mg",
            "beta_caroteno_ug", "vitamina_a_ug", "tiamina_mg", "riboflavina_mg",
            "niacina_mg", "vitamina_c_mg", "acido_folico_ug",
            "sodio_mg", "potasio_mg", "costo_kg_soles"
    );

    private static final Map<String, String> CATEGORIA_A_GRUPO = Map.ofEntries(
            Map.entry("Cereales", "A"),
            Map.entry("Verduras", "B"),
            Map.entry("Frutas", "C"),
            Map.entry("Grasas", "D"),
            Map.entry("Pescados", "E"),
            Map.entry("Carnes", "F"),
            Map.entry("Leche", "G"),
            Map.entry("Bebidas", "H"),
            Map.entry("Huevos", "J"),
            Map.entry("Azucarados", "K"),
            Map.entry("Preparados", "S"),
            Map.entry("Leguminosas", "T"),
            Map.entry("Tubérculos", "U")
    );

    private static final Map<String, String> CABEZA_A_CODIGO = Map.of(
            ParametrizacionFormulacionService.ENFOQUE_PRECISION, "A",
            ParametrizacionFormulacionService.ENFOQUE_COSTO, "B",
            ParametrizacionFormulacionService.ENFOQUE_BIODIVERSIDAD, "C"
    );

    private static final Map<String, String> CODIGO_A_MODO = Map.of(
            "A", ParametrizacionFormulacionService.ENFOQUE_PRECISION,
            "B", ParametrizacionFormulacionService.ENFOQUE_COSTO,
            "C", ParametrizacionFormulacionService.ENFOQUE_BIODIVERSIDAD
    );

    private final ConfiguracionIaRepository configuracionIaRepo;
    private final PreferenciasUsuarioRepository preferenciasRepo;
    private final RestriccionIngredienteRepository restriccionRepo;
    private final AlimentoDatasetRepository alimentoRepo;
    private final InferenciaRecetaRepository inferenciaRepo;
    private final ComposicionRecetaRepository composicionRepo;
    private final CalificacionRecetaRepository calificacionRepo;
    private final UserRepository userRepo;
    private final ObjetivoNutricionalService objetivoService;
    private final FormulacionCuotaService cuotaService;
    private final B2PresignedUrlService presignedUrlService;
    private final HuggingFaceInferenciaClient hfClient;
    private final ObjectMapper objectMapper;
    private final ParametrizacionFormulacionService parametrizacionFormulacionService;
    private final LimitesNormativosService limitesNormativosService;

    private static final Map<String, String> ETIQUETAS_SEMAFORO = Map.of(
            "sodio_mg", "Sodio",
            "grasa_total_g", "Grasa saturada (aprox.)",
            "carbohidratos_disponibles_g", "Azúcares (aprox.)",
            "energia_kcal", "Energía / calorías"
    );

    private static final Map<String, String> OCTOGONOS_LEY = Map.of(
            "sodio_mg", "Octógono: Alto en sodio",
            "grasa_total_g", "Octógono: Alto en grasas saturadas",
            "carbohidratos_disponibles_g", "Octógono: Alto en azúcares",
            "energia_kcal", "Octógono: Alto en calorías"
    );

    public FormulacionInferenciaService(
            ConfiguracionIaRepository configuracionIaRepo,
            PreferenciasUsuarioRepository preferenciasRepo,
            RestriccionIngredienteRepository restriccionRepo,
            AlimentoDatasetRepository alimentoRepo,
            InferenciaRecetaRepository inferenciaRepo,
            ComposicionRecetaRepository composicionRepo,
            CalificacionRecetaRepository calificacionRepo,
            UserRepository userRepo,
            ObjetivoNutricionalService objetivoService,
            FormulacionCuotaService cuotaService,
            B2PresignedUrlService presignedUrlService,
            HuggingFaceInferenciaClient hfClient,
            ObjectMapper objectMapper,
            ParametrizacionFormulacionService parametrizacionFormulacionService,
            LimitesNormativosService limitesNormativosService
    ) {
        this.configuracionIaRepo = configuracionIaRepo;
        this.preferenciasRepo = preferenciasRepo;
        this.restriccionRepo = restriccionRepo;
        this.alimentoRepo = alimentoRepo;
        this.inferenciaRepo = inferenciaRepo;
        this.composicionRepo = composicionRepo;
        this.calificacionRepo = calificacionRepo;
        this.userRepo = userRepo;
        this.objetivoService = objetivoService;
        this.cuotaService = cuotaService;
        this.presignedUrlService = presignedUrlService;
        this.hfClient = hfClient;
        this.objectMapper = objectMapper;
        this.parametrizacionFormulacionService = parametrizacionFormulacionService;
        this.limitesNormativosService = limitesNormativosService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preparacion(UUID usuarioId) {
        User user = cargarUsuario(usuarioId);
        ConfiguracionIa cfg = configuracionActual();
        Map<String, Object> out = new LinkedHashMap<>();
        boolean modeloDisponible = cfg != null
                && cfg.isIaActiva()
                && notBlank(cfg.getFormulacionModeloB2Key())
                && notBlank(cfg.getFormulacionEscaladorB2Key());
        out.put("modeloDisponible", modeloDisponible);
        out.put("mensajeModelo", modeloDisponible
                ? null
                : "No hay modelo de generación de recetas de superalimentos disponible");
        out.put("cuota", cuotaService.estadoCuota(user));
        out.put("rol", cuotaService.normalizarRol(user));
        out.put("descargoAceptado", user.isAceptoDescargo());
        return out;
    }

    @Transactional
    public Map<String, Object> aceptarDescargo(UUID usuarioId) {
        User user = cargarUsuario(usuarioId);
        user.setAceptoDescargo(true);
        user.setActualizadoEn(LocalDateTime.now());
        userRepo.save(user);
        return Map.of("message", "Descargo de responsabilidad aceptado.", "descargoAceptado", true);
    }

    @Transactional
    public Map<String, Object> calificarReceta(UUID usuarioId, UUID inferenciaId, Map<String, Object> body) {
        verificarDescargoAceptado(usuarioId);
        InferenciaReceta inf = inferenciaRepo.findById(inferenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receta no encontrada."));
        if (!inf.getUsuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado.");
        }
        if (!"generada".equals(inf.getEstado()) && !"guardada_historial".equals(inf.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se pueden calificar recetas generadas.");
        }
        if (calificacionRepo.existsByInferenciaId(inferenciaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta receta ya fue calificada.");
        }

        int estrellas = parseEstrellas(body.get("estrellas"));
        if (estrellas < 1 || estrellas > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar entre 1 y 5 estrellas.");
        }

        String comentario = body.get("comentario") != null ? String.valueOf(body.get("comentario")).trim() : "";
        if (comentario.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El comentario no puede superar 500 caracteres.");
        }

        CalificacionReceta cal = new CalificacionReceta();
        cal.setInferenciaId(inferenciaId);
        cal.setUsuarioId(usuarioId);
        cal.setEstrellas(estrellas);
        cal.setComentario(comentario.isEmpty() ? null : comentario);
        calificacionRepo.save(cal);

        return Map.of(
                "message", "Gracias por su calificación. Tu opinión ayuda a mejorar el modelo.",
                "calificada", true,
                "estrellas", estrellas
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estadoCalificacion(UUID usuarioId, UUID inferenciaId) {
        InferenciaReceta inf = inferenciaRepo.findById(inferenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receta no encontrada."));
        if (!inf.getUsuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado.");
        }
        Optional<CalificacionReceta> cal = calificacionRepo.findByInferenciaId(inferenciaId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("calificada", cal.isPresent());
        cal.ifPresent(c -> {
            out.put("estrellas", c.getEstrellas());
            out.put("comentario", c.getComentario());
        });
        return out;
    }

    private void verificarDescargoAceptado(UUID usuarioId) {
        User user = cargarUsuario(usuarioId);
        if (!user.isAceptoDescargo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Debes aceptar el descargo de responsabilidad antes de usar el módulo de formulación.");
        }
    }

    private int parseEstrellas(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ultimaConfiguracionFormulacion(UUID usuarioId) {
        Map<String, Object> out = new LinkedHashMap<>();
        Optional<InferenciaReceta> ultima = inferenciaRepo.findFirstByUsuarioIdAndEstadoInOrderByFechaGeneracionDesc(
                usuarioId, List.of("generada", "guardada_historial"));
        if (ultima.isEmpty()) {
            out.put("disponible", false);
            out.put("message", "No hay formulaciones anteriores registradas.");
            return out;
        }
        InferenciaReceta inf = ultima.get();
        Map<String, Object> input = inf.getInputParametros();
        out.put("disponible", true);
        out.put("fechaUltimaFormulacion", inf.getFechaGeneracion());
        if (input != null && input.get("objetivo") != null) {
            out.put("objetivo", input.get("objetivo"));
        }
        if (input != null && input.get("parametrizacion") != null) {
            out.put("parametrizacion", input.get("parametrizacion"));
        } else {
            out.put("parametrizacion", parametrizacionFormulacionService.mapaParametrizacionUsuario(usuarioId));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> ejecutar(UUID usuarioId, Map<String, Object> body) {
        verificarDescargoAceptado(usuarioId);
        User user = cargarUsuario(usuarioId);
        ConfiguracionIa cfg = configuracionActual();
        if (cfg == null || !cfg.isIaActiva()
                || !notBlank(cfg.getFormulacionModeloB2Key())
                || !notBlank(cfg.getFormulacionEscaladorB2Key())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No hay modelo de generación de recetas de superalimentos disponible");
        }

        boolean forzar = Boolean.TRUE.equals(body.get("forzar"));
        Map<String, Object> objetivo = mapObjetivo(body.get("objetivo"));
        Map<String, Object> validacion = objetivoService.validar(objetivo);
        if (!Boolean.TRUE.equals(validacion.get("valido"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.valueOf(validacion.getOrDefault("message", "Objetivo nutricional inválido.")));
        }

        PreferenciasUsuario pref = preferencias(usuarioId);
        String hash = calcularHashParametros(objetivo, pref, usuarioId, cfg);

        if (!forzar) {
            Optional<InferenciaReceta> cache = inferenciaRepo.buscarCachePorHash(usuarioId, hash);
            if (cache.isPresent() && cache.get().getInputParametros() != null) {
                String sesionCache = String.valueOf(cache.get().getInputParametros().get("sesionId"));
                Map<String, Object> sesionRecuperada = obtenerSesion(usuarioId, sesionCache);
                sesionRecuperada.put("recuperada", true);
                sesionRecuperada.put("mensajeRecuperacion",
                        "Resultados recuperados de una inferencia anterior. No se descontó cuota.");
                sesionRecuperada.put("forzarDisponible", true);
                sesionRecuperada.put("cuota", cuotaService.estadoCuota(user));
                return sesionRecuperada;
            }
        }

        cuotaService.verificarCuotaDisponible(user);

        List<Double> perfil = construirPerfil(objetivo);
        List<String> cabezas = cabezasCodigo(pref, user);
        List<String> excluidos = nombresRestriccion(usuarioId, "excluir");
        List<String> priorizados = nombresRestriccion(usuarioId, "priorizar");
        List<String> grupos = gruposPermitidos(usuarioId, pref, user);
        String mes = mesFiltro(pref, user);

        Map<String, Object> payloadHf = new LinkedHashMap<>();
        payloadHf.put("model_url", presignedUrlService.presignedGetUrl(cfg.getFormulacionModeloB2Key(), null));
        payloadHf.put("scaler_url", presignedUrlService.presignedGetUrl(cfg.getFormulacionEscaladorB2Key(), null));
        String datasetKey = notBlank(cfg.getEntrenamientoDatasetB2Key())
                ? cfg.getEntrenamientoDatasetB2Key()
                : java.time.LocalDate.now(ZONA_LIMA).format(java.time.format.DateTimeFormatter.ofPattern("yyyy_MM_dd")) + "_dataset.zip";
        payloadHf.put("dataset_url", presignedUrlService.presignedGetUrl(datasetKey, null));
        payloadHf.put("model_id", cfg.getFormulacionModeloB2Key() + "|" + cfg.getFormulacionEscaladorB2Key());
        payloadHf.put("perfil_usuario", perfil);
        payloadHf.put("grupos_permitidos", grupos);
        payloadHf.put("ingredientes_excluidos", excluidos);
        payloadHf.put("ingredientes_priorizados", priorizados);
        payloadHf.put("cabezas_solicitadas", cabezas);
        if (mes != null) {
            payloadHf.put("mes", mes);
        }
        if (pref.getPresupuestoMaximo() != null && cuotaService.normalizarRol(user).equals("estudiante") == false) {
            payloadHf.put("presupuesto_maximo", pref.getPresupuestoMaximo().doubleValue());
        }
        payloadHf.put("normativas", limitesNormativosService.mapaNormativasParaInferencia());

        Map<String, Object> respuestaHf;
        try {
            respuestaHf = hfClient.formular(payloadHf);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Falló la inferencia en Hugging Face: " + e.getMessage());
        }

        if (!Boolean.TRUE.equals(respuestaHf.get("ok"))) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "La inferencia no produjo una respuesta válida.");
        }

        String sesionId = UUID.randomUUID().toString();
        Map<String, Object> inputBase = new LinkedHashMap<>();
        inputBase.put("sesionId", sesionId);
        inputBase.put("hashParametros", hash);
        inputBase.put("objetivo", objetivo);
        inputBase.put("cabezas", cabezas);
        inputBase.put("parametrizacion", parametrizacionFormulacionService.mapaParametrizacionUsuario(usuarioId));
        inputBase.put("modeloB2Key", cfg.getFormulacionModeloB2Key());
        inputBase.put("escaladorB2Key", cfg.getFormulacionEscaladorB2Key());

        @SuppressWarnings("unchecked")
        Map<String, Object> alternativas = (Map<String, Object>) respuestaHf.get("alternativas");
        List<Map<String, Object>> alternativasValidas = new ArrayList<>();
        int guardadas = 0;

        if (alternativas != null) {
            for (Map.Entry<String, Object> entry : alternativas.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> alt = (Map<String, Object>) entry.getValue();
                String codigo = extraerCodigoCabeza(entry.getKey());
                String modo = CODIGO_A_MODO.getOrDefault(codigo, ParametrizacionFormulacionService.ENFOQUE_PRECISION);
                boolean valida = Boolean.TRUE.equals(alt.get("valida"));
                InferenciaReceta ent = new InferenciaReceta();
                ent.setUsuarioId(usuarioId);
                ent.setModoOptimizacion(modo);
                ent.setEstado(valida ? "generada" : "descartada_seguridad");
                ent.setInputParametros(new LinkedHashMap<>(inputBase));
                if (valida) {
                    ent.setCostoEstimadoKg(decimal(alt.get("costo_estimado_kg")));
                    ent.setMargenErrorMae(decimal(alt.get("mae")));
                    ent.setOutputNutricionalLogrado(alt);
                    ent.setSuperoLimiteSeguridad(false);
                    inferenciaRepo.save(ent);
                    persistirComposicion(ent.getId(), alt);
                    alternativasValidas.add(mapAlternativa(ent, alt, user));
                    guardadas++;
                } else {
                    ent.setSuperoLimiteSeguridad(true);
                    ent.setComponenteInfractor(truncar(str(alt.get("nutriente_infractor")), 50));
                    ent.setValorInfractor(decimal(alt.get("valor_infractor")));
                    inferenciaRepo.save(ent);
                }
            }
        }

        if (guardadas == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No fue posible formular una receta segura con los parámetros indicados. Intenta ajustar los valores.");
        }

        Map<String, Object> sesion = new LinkedHashMap<>();
        sesion.put("sesionId", sesionId);
        sesion.put("recuperada", false);
        sesion.put("forzarDisponible", true);
        sesion.put("alternativas", alternativasValidas);
        sesion.put("cuota", cuotaService.estadoCuota(user));
        if (respuestaHf.get("descartados_estacionalidad") != null) {
            sesion.put("descartadosEstacionalidad", respuestaHf.get("descartados_estacionalidad"));
        }
        if (respuestaHf.get("mensaje_estacionalidad") != null) {
            sesion.put("mensajeEstacionalidad", respuestaHf.get("mensaje_estacionalidad"));
        }
        return sesion;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerSesion(UUID usuarioId, String sesionId) {
        List<InferenciaReceta> todas = inferenciaRepo.listarPorSesion(usuarioId, sesionId);
        if (todas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada.");
        }
        User user = cargarUsuario(usuarioId);
        List<Map<String, Object>> alts = new ArrayList<>();
        for (InferenciaReceta inf : todas) {
            alts.add(mapAlternativaDesdeEntidad(inf, user));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sesionId", sesionId);
        out.put("alternativas", alts);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> evaluarGuardadoHistorial(UUID usuarioId, UUID inferenciaId) {
        User user = cargarUsuario(usuarioId);
        String rol = cuotaService.normalizarRol(user);
        int limite = cuotaService.limiteHistorial(rol);
        long historial = inferenciaRepo.contarHistorial(usuarioId);

        InferenciaReceta inf = inferenciaRepo.findById(inferenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receta no encontrada."));
        if (!inf.getUsuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado.");
        }
        if ("guardada_historial".equals(inf.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta receta ya está guardada en el historial.");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("limiteHistorial", limite);
        out.put("historialUsado", historial);
        out.put("historialBloqueadoPorPlan", historial > limite);
        out.put("puedeGuardarDirecto", historial < limite);
        out.put("requiereReemplazo", historial >= limite && historial <= limite);

        if (historial > limite) {
            out.put("mensaje",
                    "Tu historial supera el límite de tu plan actual. Elimina recetas manualmente antes de guardar nuevas.");
            return out;
        }

        if (historial >= limite) {
            if ("estudiante".equals(rol)) {
                InferenciaReceta masAntigua = inferenciaRepo
                        .findFirstByUsuarioIdAndEstadoOrderByFechaGeneracionAsc(usuarioId, "guardada_historial")
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                "No se encontró receta antigua para reemplazar."));
                out.put("modoReemplazo", "automatico");
                out.put("recetaAReemplazar", mapItemHistorial(masAntigua, user));
                out.put("mensaje",
                        "Has alcanzado el límite de tu plan (" + limite + " recetas). "
                                + "La receta \"" + masAntigua.getNombrePersonalizado()
                                + "\" será reemplazada automáticamente por la nueva.");
            } else {
                out.put("modoReemplazo", "manual");
                out.put("opcionesReemplazo", listarHistorial(usuarioId, null));
                out.put("mensaje",
                        "Has alcanzado el límite de tu plan (" + limite + " recetas). "
                                + "Selecciona una receta del historial para reemplazar.");
            }
        }
        return out;
    }

    @Transactional
    public Map<String, Object> guardarHistorial(UUID usuarioId, UUID inferenciaId, Map<String, Object> body) {
        String nombre = body.get("nombre") != null ? String.valueOf(body.get("nombre")).trim() : "";
        if (nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre no puede estar en blanco.");
        }
        if (nombre.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre no puede superar 100 caracteres.");
        }

        UUID reemplazarId = null;
        if (body.get("reemplazarId") != null && !String.valueOf(body.get("reemplazarId")).isBlank()) {
            reemplazarId = UUID.fromString(String.valueOf(body.get("reemplazarId")));
        }
        boolean confirmarReemplazoAutomatico = Boolean.TRUE.equals(body.get("confirmarReemplazoAutomatico"));

        User user = cargarUsuario(usuarioId);
        String rol = cuotaService.normalizarRol(user);
        int limite = cuotaService.limiteHistorial(rol);
        long historial = inferenciaRepo.contarHistorial(usuarioId);

        if (historial > limite) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tu historial supera el límite de tu plan actual. Elimina recetas manualmente antes de guardar nuevas.");
        }

        InferenciaReceta inf = inferenciaRepo.findById(inferenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receta no encontrada."));
        if (!inf.getUsuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado.");
        }
        if ("guardada_historial".equals(inf.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta receta ya está guardada en el historial.");
        }

        String recetaReemplazadaNombre = null;
        if (historial >= limite) {
            if ("estudiante".equals(rol)) {
                if (!confirmarReemplazoAutomatico) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Debes confirmar el reemplazo automático de la receta más antigua.");
                }
                InferenciaReceta masAntigua = inferenciaRepo
                        .findFirstByUsuarioIdAndEstadoOrderByFechaGeneracionAsc(usuarioId, "guardada_historial")
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                "No se encontró receta antigua para reemplazar."));
                recetaReemplazadaNombre = masAntigua.getNombrePersonalizado();
                eliminarHistorialInterno(usuarioId, masAntigua.getId());
            } else {
                if (reemplazarId == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Debes seleccionar una receta del historial para reemplazar.");
                }
                InferenciaReceta aReemplazar = inferenciaRepo.findById(reemplazarId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Receta a reemplazar no encontrada."));
                if (!aReemplazar.getUsuarioId().equals(usuarioId)
                        || !"guardada_historial".equals(aReemplazar.getEstado())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receta a reemplazar no válida.");
                }
                recetaReemplazadaNombre = aReemplazar.getNombrePersonalizado();
                eliminarHistorialInterno(usuarioId, reemplazarId);
            }
        }

        inf.setNombrePersonalizado(nombre);
        inf.setEstado("guardada_historial");
        inferenciaRepo.save(inf);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("message", recetaReemplazadaNombre != null
                ? "Receta guardada correctamente. Se reemplazó \"" + recetaReemplazadaNombre + "\"."
                : "Receta guardada correctamente.");
        respuesta.put("id", inf.getId());
        if (recetaReemplazadaNombre != null) {
            respuesta.put("recetaReemplazada", recetaReemplazadaNombre);
        }
        return respuesta;
    }

    private Map<String, Object> mapItemHistorial(InferenciaReceta inf, User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inf.getId());
        m.put("nombre", inf.getNombrePersonalizado());
        m.put("fecha", inf.getFechaGeneracion());
        m.put("modoOptimizacion", inf.getModoOptimizacion());
        m.put("tituloModo", tituloModo(inf.getModoOptimizacion()));
        m.put("mostrarCosto", !"estudiante".equals(cuotaService.normalizarRol(user)));
        m.put("costoEstimadoKg", inf.getCostoEstimadoKg());
        return m;
    }

    private void eliminarHistorialInterno(UUID usuarioId, UUID inferenciaId) {
        InferenciaReceta inf = inferenciaRepo.findById(inferenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receta no encontrada."));
        if (!inf.getUsuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado.");
        }
        composicionRepo.deleteByInferenciaId(inferenciaId);
        inferenciaRepo.delete(inf);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarHistorial(UUID usuarioId, String q) {
        User user = cargarUsuario(usuarioId);
        List<InferenciaReceta> lista = inferenciaRepo.findByUsuarioIdAndEstadoOrderByFechaGeneracionDesc(
                usuarioId, "guardada_historial");
        return lista.stream()
                .filter(i -> q == null || q.isBlank()
                        || (i.getNombrePersonalizado() != null
                        && i.getNombrePersonalizado().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))))
                .map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", i.getId());
                    m.put("nombre", i.getNombrePersonalizado());
                    m.put("fecha", i.getFechaGeneracion());
                    m.put("modoOptimizacion", i.getModoOptimizacion());
                    m.put("tituloModo", tituloModo(i.getModoOptimizacion()));
                    m.put("mostrarCosto", !"estudiante".equals(cuotaService.normalizarRol(user)));
                    m.put("costoEstimadoKg", i.getCostoEstimadoKg());
                    return m;
                }).toList();
    }

    @Transactional
    public void eliminarHistorial(UUID usuarioId, UUID inferenciaId) {
        eliminarHistorialInterno(usuarioId, inferenciaId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalleReceta(UUID usuarioId, UUID inferenciaId) {
        InferenciaReceta inf = inferenciaRepo.findById(inferenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receta no encontrada."));
        if (!inf.getUsuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado.");
        }
        User user = cargarUsuario(usuarioId);
        Map<String, Object> out = mapAlternativaDesdeEntidad(inf, user);
        if (inf.getInputParametros() != null && inf.getInputParametros().get("sesionId") != null) {
            out.put("sesionId", inf.getInputParametros().get("sesionId"));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> editarReceta(UUID usuarioId, UUID inferenciaId, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ingredientes = (List<Map<String, Object>>) body.get("ingredientes");
        if (ingredientes == null || ingredientes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe enviar la lista de ingredientes.");
        }

        double suma = ingredientes.stream()
                .mapToDouble(i -> {
                    Object p = i.get("porcentaje");
                    return p != null ? Double.parseDouble(String.valueOf(p)) : 0.0;
                })
                .sum();
        double diff = Math.abs(suma - 100.0);
        if (diff > 0.05) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("La suma de porcentajes es %.2f%%. Debe ser 100%% (diferencia %.2f%%).",
                            suma, suma - 100.0));
        }

        InferenciaReceta inf = inferenciaRepo.findById(inferenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receta no encontrada."));
        if (!inf.getUsuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado.");
        }

        List<Double> perfilReal = new ArrayList<>(Collections.nCopies(ORDEN_PERFIL.size(), 0.0));
        double costo = 0.0;
        composicionRepo.deleteByInferenciaId(inferenciaId);

        for (Map<String, Object> ing : ingredientes) {
            int alimentoId = Integer.parseInt(String.valueOf(ing.get("alimentoId")));
            double pct = Double.parseDouble(String.valueOf(ing.get("porcentaje"))) / 100.0;
            AlimentoDataset alimento = alimentoRepo.findById(alimentoId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingrediente no válido."));
            for (int i = 0; i < ORDEN_PERFIL.size(); i++) {
                Double val = valorNutriente(alimento, ORDEN_PERFIL.get(i));
                if (val != null) {
                    perfilReal.set(i, perfilReal.get(i) + val * pct);
                }
            }
            costo += alimento.getCostoKgSoles().doubleValue() * pct;

            ComposicionReceta comp = new ComposicionReceta();
            comp.setInferenciaId(inferenciaId);
            comp.setAlimentoId(alimentoId);
            comp.setPorcentaje(BigDecimal.valueOf(pct * 100.0).setScale(2, RoundingMode.HALF_UP));
            composicionRepo.save(comp);
        }

        String nutrienteInfractor = verificarCodexLocal(perfilReal);
        if (nutrienteInfractor != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La modificación supera el límite de " + nutrienteInfractor
                            + ". No se puede guardar ni exportar.");
        }

        Map<String, Object> output = inf.getOutputNutricionalLogrado() != null
                ? new LinkedHashMap<>(inf.getOutputNutricionalLogrado())
                : new LinkedHashMap<>();
        output.put("perfil_real", construirComparacionPerfil(inf, perfilReal));
        output.put("semaforo", evaluarSemaforoLocal(perfilReal));
        output.put("semaforo_detalle", evaluarSemaforoDetalleLocal(perfilReal));
        output.put("costo_estimado_kg", costo);

        inf.setCostoEstimadoKg(BigDecimal.valueOf(costo).setScale(2, RoundingMode.HALF_UP));
        inf.setOutputNutricionalLogrado(output);
        inf.setEsModificadaManualmente(true);
        inf.setSuperoLimiteSeguridad(false);
        inferenciaRepo.save(inf);

        User user = cargarUsuario(usuarioId);
        return mapAlternativaDesdeEntidad(inf, user);
    }

    private Map<String, Object> construirComparacionPerfil(InferenciaReceta inf, List<Double> perfilReal) {
        Map<String, Object> comparacion = new LinkedHashMap<>();
        Map<String, Object> objetivo = inf.getInputParametros() != null
                ? (Map<String, Object>) inf.getInputParametros().get("objetivo")
                : null;
        for (int i = 0; i < ORDEN_PERFIL.size(); i++) {
            String col = ORDEN_PERFIL.get(i);
            double obj = 0.0;
            if (objetivo != null && objetivo.get(col) != null) {
                obj = Double.parseDouble(String.valueOf(objetivo.get(col)));
            } else if (objetivo != null && "fibra_dietaria_g".equals(col) && objetivo.get("fibra_g") != null) {
                obj = Double.parseDouble(String.valueOf(objetivo.get("fibra_g")));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("objetivo", obj);
            item.put("logrado", perfilReal.get(i));
            item.put("desviacion", perfilReal.get(i) - obj);
            comparacion.put(col, item);
        }
        return comparacion;
    }

    private Double valorNutriente(AlimentoDataset a, String col) {
        return switch (col) {
            case "energia_kcal" -> a.getEnergiaKcal() != null ? a.getEnergiaKcal().doubleValue() : null;
            case "agua_g" -> a.getAguaG() != null ? a.getAguaG().doubleValue() : null;
            case "proteinas_g" -> a.getProteinasG() != null ? a.getProteinasG().doubleValue() : null;
            case "grasa_total_g" -> a.getGrasaTotalG() != null ? a.getGrasaTotalG().doubleValue() : null;
            case "carbohidratos_disponibles_g" -> a.getCarbohidratosDisponiblesG() != null
                    ? a.getCarbohidratosDisponiblesG().doubleValue() : null;
            case "fibra_dietaria_g" -> a.getFibraG() != null ? a.getFibraG().doubleValue() : null;
            case "cenizas_g" -> a.getCenizasG() != null ? a.getCenizasG().doubleValue() : null;
            case "calcio_mg" -> a.getCalcioMg() != null ? a.getCalcioMg().doubleValue() : null;
            case "fosforo_mg" -> a.getFosforoMg() != null ? a.getFosforoMg().doubleValue() : null;
            case "zinc_mg" -> a.getZincMg() != null ? a.getZincMg().doubleValue() : null;
            case "hierro_mg" -> a.getHierroMg() != null ? a.getHierroMg().doubleValue() : null;
            case "beta_caroteno_ug" -> a.getBetaCarotenoUg() != null ? a.getBetaCarotenoUg().doubleValue() : null;
            case "vitamina_a_ug" -> a.getVitaminaAUg() != null ? a.getVitaminaAUg().doubleValue() : null;
            case "tiamina_mg" -> a.getTiaminaMg() != null ? a.getTiaminaMg().doubleValue() : null;
            case "riboflavina_mg" -> a.getRiboflavinaMg() != null ? a.getRiboflavinaMg().doubleValue() : null;
            case "niacina_mg" -> a.getNiacinaMg() != null ? a.getNiacinaMg().doubleValue() : null;
            case "vitamina_c_mg" -> a.getVitaminaCMg() != null ? a.getVitaminaCMg().doubleValue() : null;
            case "acido_folico_ug" -> a.getAcidoFolicoUg() != null ? a.getAcidoFolicoUg().doubleValue() : null;
            case "sodio_mg" -> a.getSodioMg() != null ? a.getSodioMg().doubleValue() : null;
            case "potasio_mg" -> a.getPotasioMg() != null ? a.getPotasioMg().doubleValue() : null;
            case "costo_kg_soles" -> a.getCostoKgSoles() != null ? a.getCostoKgSoles().doubleValue() : null;
            default -> null;
        };
    }

    private String verificarCodexLocal(List<Double> perfil) {
        Map<String, Double> limites = limitesNormativosService.mapaLimitesCodex();
        for (int i = 0; i < ORDEN_PERFIL.size(); i++) {
            Double lim = limites.get(ORDEN_PERFIL.get(i));
            if (lim != null && perfil.get(i) > lim) {
                return ORDEN_PERFIL.get(i);
            }
        }
        return null;
    }

    private Map<String, String> evaluarSemaforoLocal(List<Double> perfil) {
        Map<String, Double> umbrales = limitesNormativosService.mapaUmbralesLey30021();
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < ORDEN_PERFIL.size(); i++) {
            String col = ORDEN_PERFIL.get(i);
            Double umbral = umbrales.get(col);
            if (umbral == null) continue;
            double val = perfil.get(i);
            if (val > umbral) out.put(col, "ROJO");
            else if (val >= umbral * 0.8) out.put(col, "AMARILLO");
            else out.put(col, "VERDE");
        }
        return out;
    }

    private Map<String, Object> evaluarSemaforoDetalleLocal(List<Double> perfil) {
        Map<String, String> colores = evaluarSemaforoLocal(perfil);
        Map<String, Object> detalle = new LinkedHashMap<>();
        Map<String, Double> umbrales = limitesNormativosService.mapaUmbralesLey30021();
        for (Map.Entry<String, String> e : colores.entrySet()) {
            int idx = ORDEN_PERFIL.indexOf(e.getKey());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("etiqueta", ETIQUETAS_SEMAFORO.getOrDefault(e.getKey(), e.getKey()));
            item.put("color", e.getValue());
            item.put("valor", perfil.get(idx));
            item.put("umbral", umbrales.get(e.getKey()));
            item.put("referencia", "Ley N° 30021");
            detalle.put(e.getKey(), item);
        }
        return detalle;
    }

    private Map<String, Object> reconstruirSesion(String sesionId, boolean recuperada, String mensaje) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sesionId", sesionId);
        out.put("recuperada", recuperada);
        out.put("mensajeRecuperacion", mensaje);
        out.put("forzarDisponible", true);
        return out;
    }

    private Map<String, Object> mapAlternativa(InferenciaReceta ent, Map<String, Object> alt, User user) {
        Map<String, Object> m = mapAlternativaDesdeEntidad(ent, user);
        m.put("detalleHf", alt);
        return m;
    }

    private Map<String, Object> mapAlternativaDesdeEntidad(InferenciaReceta ent, User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ent.getId());
        m.put("modoOptimizacion", ent.getModoOptimizacion());
        m.put("tituloModo", tituloModo(ent.getModoOptimizacion()));
        m.put("costoEstimadoKg", ent.getCostoEstimadoKg());
        m.put("mae", ent.getMargenErrorMae());
        m.put("esSegura", true);
        m.put("mostrarCosto", !"estudiante".equals(cuotaService.normalizarRol(user)));
        m.put("rol", cuotaService.normalizarRol(user));
        m.put("ingredientes", composicionRepo.findByInferenciaIdOrderByPorcentajeDesc(ent.getId()).stream()
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    alimentoRepo.findById(c.getAlimentoId()).ifPresent(a -> {
                        item.put("alimentoId", a.getId());
                        item.put("nombre", a.getNombre());
                        item.put("categoria", a.getCategoria());
                    });
                    item.put("porcentaje", c.getPorcentaje());
                    return item;
                }).toList());
        if (ent.getOutputNutricionalLogrado() != null) {
            m.put("semaforo", ent.getOutputNutricionalLogrado().get("semaforo"));
            m.put("semaforoDetalle", ent.getOutputNutricionalLogrado().get("semaforo_detalle"));
            m.put("perfilNutricional", ent.getOutputNutricionalLogrado().get("perfil_real"));
            if ("nutricionista".equals(cuotaService.normalizarRol(user))) {
                m.put("semaforoExtendido", construirSemaforoExtendido(ent.getOutputNutricionalLogrado()));
            }
        }
        calificacionRepo.findByInferenciaId(ent.getId()).ifPresentOrElse(cal -> {
            m.put("calificada", true);
            m.put("calificacionEstrellas", cal.getEstrellas());
            m.put("calificacionComentario", cal.getComentario());
        }, () -> m.put("calificada", false));
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> construirSemaforoExtendido(Map<String, Object> output) {
        Map<String, Object> extendido = new LinkedHashMap<>();
        Object perfilRaw = output.get("perfil_real");
        if (!(perfilRaw instanceof Map<?, ?> perfilMap)) {
            return extendido;
        }

        Map<String, Double> limitesCodex = limitesNormativosService.mapaLimitesCodex();
        Map<String, Double> umbralesLey = limitesNormativosService.mapaUmbralesLey30021();

        List<Map<String, Object>> codex = new ArrayList<>();
        for (Map.Entry<String, Double> entry : limitesCodex.entrySet()) {
            Object itemRaw = perfilMap.get(entry.getKey());
            double logrado = extraerLogrado(itemRaw);
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("nutriente", entry.getKey());
            fila.put("logrado", logrado);
            fila.put("limiteCodex", entry.getValue());
            fila.put("cumple", logrado <= entry.getValue());
            fila.put("referencia", "Codex Alimentarius");
            codex.add(fila);
        }
        extendido.put("verificacionCodex", codex);

        List<Map<String, Object>> ley = new ArrayList<>();
        for (Map.Entry<String, Double> entry : umbralesLey.entrySet()) {
            Object itemRaw = perfilMap.get(entry.getKey());
            double logrado = extraerLogrado(itemRaw);
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("nutriente", entry.getKey());
            fila.put("logrado", logrado);
            fila.put("umbralLey", entry.getValue());
            fila.put("activaOctogono", logrado > entry.getValue());
            fila.put("octogono", OCTOGONOS_LEY.get(entry.getKey()));
            fila.put("referencia", "Ley N° 30021");
            ley.add(fila);
        }
        extendido.put("verificacionLey30021", ley);
        return extendido;
    }

    private double extraerLogrado(Object itemRaw) {
        if (itemRaw instanceof Map<?, ?> item) {
            Object log = item.get("logrado");
            if (log instanceof Number n) {
                return n.doubleValue();
            }
            if (log != null) {
                return Double.parseDouble(String.valueOf(log));
            }
        }
        return 0.0;
    }

    private void persistirComposicion(UUID inferenciaId, Map<String, Object> alt) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ingredientes = (List<Map<String, Object>>) alt.get("ingredientes");
        if (ingredientes == null) {
            return;
        }
        for (Map<String, Object> ing : ingredientes) {
            String nombre = str(ing.get("nombre"));
            alimentoRepo.findByNombreIgnoreCase(nombre).ifPresent(a -> {
                ComposicionReceta c = new ComposicionReceta();
                c.setInferenciaId(inferenciaId);
                c.setAlimentoId(a.getId());
                double pct = ing.get("participacion") instanceof Number n
                        ? n.doubleValue() * 100.0
                        : Double.parseDouble(String.valueOf(ing.get("participacion"))) * 100.0;
                c.setPorcentaje(BigDecimal.valueOf(pct).setScale(2, RoundingMode.HALF_UP));
                composicionRepo.save(c);
            });
        }
    }

    private String tituloModo(String modo) {
        if (ParametrizacionFormulacionService.ENFOQUE_COSTO.equals(modo)) {
            return "Mínimo costo de producción";
        }
        if (ParametrizacionFormulacionService.ENFOQUE_BIODIVERSIDAD.equals(modo)) {
            return "Máxima biodiversidad local";
        }
        return "Máxima precisión nutricional";
    }

    private List<String> cabezasCodigo(PreferenciasUsuario pref, User user) {
        String rol = cuotaService.normalizarRol(user);
        List<String> cabezas = pref.getCabezasOptimizacion() != null
                ? Arrays.asList(pref.getCabezasOptimizacion())
                : List.of(ParametrizacionFormulacionService.ENFOQUE_PRECISION);
        List<String> codigos = new ArrayList<>();
        for (String c : cabezas) {
            String cod = CABEZA_A_CODIGO.get(c);
            if (cod != null) {
                codigos.add(cod);
            }
        }
        if ("estudiante".equals(rol)) {
            return List.of("A");
        }
        if ("emprendedor".equals(rol) && codigos.size() > 2) {
            return codigos.subList(0, 2);
        }
        return codigos.isEmpty() ? List.of("A") : codigos;
    }

    private List<String> gruposPermitidos(UUID usuarioId, PreferenciasUsuario pref, User user) {
        Set<String> grupos = new HashSet<>();
        List<AlimentoDataset> todos = alimentoRepo.findAll();
        Set<Integer> excluidos = restriccionRepo.findByUsuarioIdAndTipoIgnoreCase(usuarioId, "excluir").stream()
                .map(RestriccionIngrediente::getAlimentoId).collect(java.util.stream.Collectors.toSet());
        for (AlimentoDataset a : todos) {
            if (excluidos.contains(a.getId())) {
                continue;
            }
            String letra = CATEGORIA_A_GRUPO.get(a.getCategoria());
            if (letra != null) {
                grupos.add(letra);
            }
        }
        return new ArrayList<>(grupos);
    }

    private String mesFiltro(PreferenciasUsuario pref, User user) {
        if (!pref.isFiltroEstacionalidadActivo()) {
            return null;
        }
        if ("estudiante".equals(cuotaService.normalizarRol(user))) {
            return null;
        }
        String[] meses = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
                "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        int idx = java.time.LocalDate.now(ZONA_LIMA).getMonthValue() - 1;
        return meses[idx];
    }

    private List<String> nombresRestriccion(UUID usuarioId, String tipo) {
        return restriccionRepo.findByUsuarioIdAndTipoIgnoreCase(usuarioId, tipo).stream()
                .map(r -> alimentoRepo.findById(r.getAlimentoId()).map(AlimentoDataset::getNombre).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Double> construirPerfil(Map<String, Object> objetivo) {
        Map<String, Object> normalizado = new LinkedHashMap<>(objetivo);
        if (normalizado.containsKey("fibra_g") && !normalizado.containsKey("fibra_dietaria_g")) {
            normalizado.put("fibra_dietaria_g", normalizado.get("fibra_g"));
        }
        List<Double> perfil = new ArrayList<>();
        for (String key : ORDEN_PERFIL) {
            Object v = normalizado.get(key);
            if (key.equals("fibra_dietaria_g") && v == null) {
                v = normalizado.get("fibra_g");
            }
            perfil.add(v != null ? Double.parseDouble(String.valueOf(v)) : 0.0);
        }
        return perfil;
    }

    private Map<String, Object> mapObjetivo(Object raw) {
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe enviar el objetivo nutricional.");
    }

    private String calcularHashParametros(
            Map<String, Object> objetivo,
            PreferenciasUsuario pref,
            UUID usuarioId,
            ConfiguracionIa cfg
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("objetivo", objetivo);
            payload.put("cabezas", pref.getCabezasOptimizacion());
            payload.put("estacionalidad", pref.isFiltroEstacionalidadActivo());
            payload.put("presupuesto", pref.getPresupuestoMaximo());
            payload.put("excluidos", restriccionRepo.findByUsuarioIdAndTipoIgnoreCase(usuarioId, "excluir"));
            payload.put("priorizados", restriccionRepo.findByUsuarioIdAndTipoIgnoreCase(usuarioId, "priorizar"));
            payload.put("modelo", cfg.getFormulacionModeloB2Key());
            payload.put("escalador", cfg.getFormulacionEscaladorB2Key());
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private PreferenciasUsuario preferencias(UUID usuarioId) {
        return preferenciasRepo.findById(usuarioId).orElseGet(() -> {
            PreferenciasUsuario p = new PreferenciasUsuario();
            p.setUsuarioId(usuarioId);
            return preferenciasRepo.save(p);
        });
    }

    private ConfiguracionIa configuracionActual() {
        return configuracionIaRepo.findById(1).orElse(null);
    }

    private User cargarUsuario(UUID id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
    }

    private String extraerCodigoCabeza(String etiqueta) {
        if (etiqueta == null) {
            return "A";
        }
        if (etiqueta.startsWith("A")) {
            return "A";
        }
        if (etiqueta.startsWith("B")) {
            return "B";
        }
        if (etiqueta.startsWith("C")) {
            return "C";
        }
        return "A";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o).trim() : "";
    }

    private static String truncar(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(o));
    }
}
