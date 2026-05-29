package com.mikunaigen.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.model.sql.ConfiguracionIa;
import com.mikunaigen.backend.repository.sql.ConfiguracionIaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class EntrenamientoIaService {

    public static final String ESTADO_IDLE = "IDLE";
    public static final String ESTADO_PREPARANDO = "PREPARANDO_DATASET";
    public static final String ESTADO_INVOCANDO = "INVOCANDO_KAGGLE";
    public static final String ESTADO_ENTRENANDO = "ENTRENANDO";
    public static final String ESTADO_COMPLETADO = "COMPLETADO";
    public static final String ESTADO_ERROR = "ERROR";

    private static final Set<String> ESTADOS_OCUPADOS = Set.of(
            ESTADO_PREPARANDO, ESTADO_INVOCANDO, ESTADO_ENTRENANDO
    );

    private final ConfiguracionIaRepository repo;
    private final EntrenamientoDatasetGithubService datasetGithub;
    private final EntrenamientoIaAsyncService asyncService;
    private final EntrenamientoIaPushService push;
    private final ObjectMapper objectMapper;

    @Value("${app.kaggle.webhook.secret:}")
    private String webhookSecret;

    @Value("${app.backup.notify-secret:}")
    private String datasetWebhookSecret;

    public EntrenamientoIaService(
            ConfiguracionIaRepository repo,
            EntrenamientoDatasetGithubService datasetGithub,
            EntrenamientoIaAsyncService asyncService,
            EntrenamientoIaPushService push,
            ObjectMapper objectMapper
    ) {
        this.repo = repo;
        this.datasetGithub = datasetGithub;
        this.asyncService = asyncService;
        this.push = push;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> iniciarEntrenamiento() {
        ConfiguracionIa c = getOrCreate();
        if (ESTADOS_OCUPADOS.contains(normalizarEstado(c.getEntrenamientoEstado()))) {
            throw new IllegalStateException("Ya hay un entrenamiento en curso.");
        }
        String jobId = UUID.randomUUID().toString();
        c.setEntrenamientoJobId(jobId);
        c.setEntrenamientoEstado(ESTADO_PREPARANDO);
        c.setEntrenamientoEpoca(0);
        c.setEntrenamientoEpocasTotales(50);
        c.setEntrenamientoErrorTrain(null);
        c.setEntrenamientoErrorVal(null);
        c.setEntrenamientoLossTrain(null);
        c.setEntrenamientoLossVal(null);
        c.setEntrenamientoMensaje("Generando dataset y subiendo a la nube...");
        c.setEntrenamientoDatasetB2Key(datasetGithub.claveDatasetHoy());
        c.setEntrenamientoCurvaJson("[]");
        c.setEntrenamientoIniciadoEn(LocalDateTime.now());
        c.setEntrenamientoFinalizadoEn(null);
        c.setActualizadoEn(LocalDateTime.now());
        repo.save(c);

        Map<String, Object> estado = estadoDesde(c);
        push.publicar(estado);
        asyncService.despacharDataset(jobId);
        return estado;
    }

    public void marcarErrorPublico(String jobId, String mensaje) {
        marcarError(jobId, mensaje);
    }

    @Transactional
    public void marcarInvocacionKaggleOk(String jobId) {
        ConfiguracionIa c = getOrCreate();
        if (!jobId.equals(c.getEntrenamientoJobId())) {
            return;
        }
        c.setEntrenamientoEstado(ESTADO_ENTRENANDO);
        c.setEntrenamientoMensaje("Entrenamiento en Kaggle en ejecución...");
        c.setActualizadoEn(LocalDateTime.now());
        repo.save(c);
        push.publicar(estadoDesde(c));
    }

    public boolean verificarSecretoDataset(String firma) {
        return datasetWebhookSecret != null && !datasetWebhookSecret.isBlank()
                && datasetWebhookSecret.equals(firma);
    }

    public boolean verificarSecretoKaggle(String firma) {
        return webhookSecret != null && !webhookSecret.isBlank() && webhookSecret.equals(firma);
    }

    @Transactional
    public void procesarWebhookDataset(Map<String, Object> body) {
        String status = str(body.get("status"));
        String jobId = str(body.get("job_id"));
        String datasetKey = str(body.get("dataset_key"));
        if (jobId.isBlank()) {
            return;
        }
        ConfiguracionIa c = getOrCreate();
        if (!jobId.equals(c.getEntrenamientoJobId())) {
            return;
        }
        if (!"success".equalsIgnoreCase(status)) {
            String detalle = str(body.get("detail"));
            marcarError(jobId, detalle.isBlank()
                    ? "Falló la generación del dataset en GitHub Actions."
                    : detalle);
            return;
        }
        if (!datasetKey.isBlank()) {
            c.setEntrenamientoDatasetB2Key(datasetKey);
        }
        c.setEntrenamientoMensaje("Dataset en B2. Invocando entrenamiento en Kaggle...");
        c.setEntrenamientoEstado(ESTADO_INVOCANDO);
        c.setActualizadoEn(LocalDateTime.now());
        repo.save(c);
        push.publicar(estadoDesde(c));
        asyncService.invocarKaggle(jobId);
    }

    @Transactional
    public boolean procesarWebhookKaggle(Map<String, Object> body) {
        String status = str(body.get("status"));
        ConfiguracionIa c = getOrCreate();
        String jobId = c.getEntrenamientoJobId();
        if (jobId == null || jobId.isBlank()) {
            return false;
        }

        if ("CANCEL_PENDING".equalsIgnoreCase(c.getEntrenamientoEstado())) {
            c.setEntrenamientoEstado(ESTADO_ERROR);
            c.setEntrenamientoMensaje("Entrenamiento cancelado correctamente.");
            c.setEntrenamientoFinalizadoEn(LocalDateTime.now());
            c.setActualizadoEn(LocalDateTime.now());
            repo.save(c);
            push.publicar(estadoDesde(c));
            return true; 
        }

        if ("IN_PROGRESS".equalsIgnoreCase(status)) {
            if (!ESTADOS_OCUPADOS.contains(normalizarEstado(c.getEntrenamientoEstado()))
                    && !ESTADO_ENTRENANDO.equals(normalizarEstado(c.getEntrenamientoEstado()))) {
                c.setEntrenamientoEstado(ESTADO_ENTRENANDO);
            }
            int epoca = intVal(body.get("epoch"));
            if (epoca > 0) {
                c.setEntrenamientoEpoca(epoca);
            }
            c.setEntrenamientoErrorTrain(decimal(body.get("trainError")));
            c.setEntrenamientoErrorVal(decimal(body.get("valError")));
            c.setEntrenamientoLossTrain(decimal(body.get("trainLoss")));
            c.setEntrenamientoLossVal(decimal(body.get("valLoss")));
            c.setEntrenamientoMensaje("Época " + c.getEntrenamientoEpoca() + " de "
                    + valorEntero(c.getEntrenamientoEpocasTotales(), 50));
            agregarPuntoCurva(c, epoca,
                    c.getEntrenamientoErrorTrain(), c.getEntrenamientoErrorVal());
            c.setActualizadoEn(LocalDateTime.now());
            repo.save(c);
            push.publicar(estadoDesde(c));
            return false;
        }

        if ("SUCCESS".equalsIgnoreCase(status)) {
            String modeloKey = str(body.get("modelFileKey"));
            String escaladorKey = str(body.get("scalerFileKey"));
            if (!modeloKey.isBlank()) {
                c.setFormulacionModeloB2Key(modeloKey);
            }
            if (!escaladorKey.isBlank()) {
                c.setFormulacionEscaladorB2Key(escaladorKey);
            }
            c.setEntrenamientoErrorTrain(decimal(body.get("trainError")));
            c.setEntrenamientoErrorVal(decimal(body.get("valError")));
            int epoca = intVal(body.get("epoch"));
            if (epoca > 0) {
                c.setEntrenamientoEpoca(epoca);
            }
            c.setEntrenamientoEstado(ESTADO_COMPLETADO);
            c.setEntrenamientoMensaje("Entrenamiento finalizado. Modelo listo en B2.");
            c.setEntrenamientoFinalizadoEn(LocalDateTime.now());
            c.setActualizadoEn(LocalDateTime.now());
            repo.save(c);
            push.publicar(estadoDesde(c));
            return false;
        }

        if ("OVERFITTING_WARNING".equalsIgnoreCase(status)) {
            c.setEntrenamientoEstado(ESTADO_ERROR);
            c.setEntrenamientoMensaje("El modelo no alcanzó la precisión esperada (MAE de validación > 0.05).");
            c.setEntrenamientoFinalizadoEn(LocalDateTime.now());
            c.setActualizadoEn(LocalDateTime.now());
            repo.save(c);
            push.publicar(estadoDesde(c));
            return false;
        }

        if ("FAILED".equalsIgnoreCase(status)) {
            String mensaje = str(body.get("message"));
            marcarError(jobId, mensaje.isBlank() ? "El entrenamiento en Kaggle falló." : mensaje);
        }
        
        return false;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estadoEntrenamiento() {
        return estadoDesde(getOrCreate());
    }

    @Transactional
    public Map<String, Object> cancelarEntrenamiento() {
        ConfiguracionIa c = getOrCreate();
        String estadoActual = normalizarEstado(c.getEntrenamientoEstado());
        
        if (!ESTADOS_OCUPADOS.contains(estadoActual) && !ESTADO_ENTRENANDO.equals(estadoActual)) {
            throw new IllegalStateException("No hay ningún entrenamiento activo para cancelar.");
        }
        
        c.setEntrenamientoEstado("CANCEL_PENDING");
        c.setEntrenamientoMensaje("Solicitud de cancelación enviada. Se aplicará al terminar la época actual...");
        c.setActualizadoEn(LocalDateTime.now());
        repo.save(c);

        push.publicar(estadoDesde(c));
        return estadoDesde(c);
    }

    private void marcarError(String jobId, String mensaje) {
        repo.findById(1).ifPresent(c -> {
            if (jobId != null && c.getEntrenamientoJobId() != null && !jobId.equals(c.getEntrenamientoJobId())) {
                return;
            }
            c.setEntrenamientoEstado(ESTADO_ERROR);
            c.setEntrenamientoMensaje(mensaje != null && mensaje.length() > 500
                    ? mensaje.substring(0, 500) : mensaje);
            c.setEntrenamientoFinalizadoEn(LocalDateTime.now());
            c.setActualizadoEn(LocalDateTime.now());
            repo.save(c);
            push.publicar(estadoDesde(c));
        });
    }

    private void agregarPuntoCurva(ConfiguracionIa c, int epoca, BigDecimal train, BigDecimal val) {
        if (epoca <= 0) {
            return;
        }
        try {
            List<Map<String, Object>> curva = new ArrayList<>();
            String json = c.getEntrenamientoCurvaJson();
            if (json != null && !json.isBlank()) {
                curva.addAll(objectMapper.readValue(json, new TypeReference<>() {}));
            }
            Map<String, Object> punto = new LinkedHashMap<>();
            punto.put("epoca", epoca);
            punto.put("trainError", train);
            punto.put("valError", val);
            curva.removeIf(p -> intVal(p.get("epoca")) == epoca);
            curva.add(punto);
            curva.sort(Comparator.comparingInt(p -> intVal(p.get("epoca"))));
            c.setEntrenamientoCurvaJson(objectMapper.writeValueAsString(curva));
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> estadoDesde(ConfiguracionIa c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "entrenamiento_estado");
        out.put("jobId", c.getEntrenamientoJobId());
        out.put("estado", normalizarEstado(c.getEntrenamientoEstado()));
        out.put("epoca", valorEntero(c.getEntrenamientoEpoca(), 0));
        out.put("epocasTotales", valorEntero(c.getEntrenamientoEpocasTotales(), 50));
        out.put("trainError", c.getEntrenamientoErrorTrain());
        out.put("valError", c.getEntrenamientoErrorVal());
        out.put("trainLoss", c.getEntrenamientoLossTrain());
        out.put("valLoss", c.getEntrenamientoLossVal());
        out.put("mensaje", c.getEntrenamientoMensaje());
        out.put("datasetB2Key", c.getEntrenamientoDatasetB2Key());
        out.put("modeloB2Key", c.getFormulacionModeloB2Key());
        out.put("escaladorB2Key", c.getFormulacionEscaladorB2Key());
        out.put("iniciadoEn", c.getEntrenamientoIniciadoEn());
        out.put("finalizadoEn", c.getEntrenamientoFinalizadoEn());
        out.put("enCurso", ESTADOS_OCUPADOS.contains(normalizarEstado(c.getEntrenamientoEstado()))
                || ESTADO_ENTRENANDO.equals(normalizarEstado(c.getEntrenamientoEstado())));
        try {
            String json = c.getEntrenamientoCurvaJson();
            if (json != null && !json.isBlank()) {
                out.put("curva", objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {}));
            } else {
                out.put("curva", List.of());
            }
        } catch (Exception e) {
            out.put("curva", List.of());
        }
        int epocas = valorEntero(c.getEntrenamientoEpocasTotales(), 50);
        int epoca = valorEntero(c.getEntrenamientoEpoca(), 0);
        out.put("progresoPorcentaje", epocas > 0 ? Math.min(100, (epoca * 100) / epocas) : 0);
        return out;
    }

    private ConfiguracionIa getOrCreate() {
        return repo.findById(1).orElseGet(() -> {
            ConfiguracionIa c = new ConfiguracionIa();
            c.setId(1);
            return repo.save(c);
        });
    }

    private static String normalizarEstado(String estado) {
        return estado != null && !estado.isBlank() ? estado.trim().toUpperCase(Locale.ROOT) : ESTADO_IDLE;
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o).trim() : "";
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(str(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int valorEntero(Integer v, int defecto) {
        return v != null ? v : defecto;
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
        try {
            return new BigDecimal(str(o));
        } catch (Exception e) {
            return null;
        }
    }
}
