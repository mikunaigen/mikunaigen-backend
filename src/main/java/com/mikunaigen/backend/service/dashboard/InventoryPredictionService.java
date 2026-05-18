package com.mikunaigen.backend.service.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.model.nosql.AiModelConfig;
import com.mikunaigen.backend.model.sql.Inventory;
import com.mikunaigen.backend.repository.nosql.AiModelConfigRepository;
import com.mikunaigen.backend.repository.sql.InventoryMovementRepository;
import com.mikunaigen.backend.repository.sql.InventoryRepository;
import com.mikunaigen.backend.repository.sql.RestaurantOrderRepository;
import com.mikunaigen.backend.service.AiModelSlot3GridFsService;
import com.mikunaigen.backend.service.ContextoInteligenciaService;

@Service
public class InventoryPredictionService {

    @Value("${ai.inference.token:}")
    private String hfToken;

    private static final Logger log = LoggerFactory.getLogger(InventoryPredictionService.class);
    private static final String CONFIG_ID = "GLOBAL_AI_CONFIG";
    private static final ZoneId Z = ZoneId.of("America/Lima");
    private static final double EPS = 1e-8;

    private static final Map<String, Integer> WEATHER_MAP = Map.ofEntries(
            Map.entry("SOLEADO", 0),
            Map.entry("PARCIALMENTE_NUBLADO", 1),
            Map.entry("NUBLADO", 2),
            Map.entry("LLUVIOSO", 3),
            Map.entry("TORMENTA", 4),
            Map.entry("OTRO", 5),
            Map.entry("DESCONOCIDO", 5)
    );

    private static final Map<String, Integer> MOMENT_MAP = Map.ofEntries(
            Map.entry("MADRUGADA", 0),
            Map.entry("MAÑANA", 1),
            Map.entry("TARDE", 2),
            Map.entry("NOCHE", 3),
            Map.entry("DESCONOCIDO", 2)
    );

    private final AiModelConfigRepository aiModelConfigRepository;
    private final AiModelSlot3GridFsService aiModelSlot3GridFsService;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final RestaurantOrderRepository orderRepository;
    private final ContextoInteligenciaService contextoInteligenciaService;
    private final ObjectMapper objectMapper;
    private final RestTemplate hfLongTimeout;

    @Value("${ai.inference.url}")
    private String hfBaseUrl;

    public InventoryPredictionService(
            AiModelConfigRepository aiModelConfigRepository,
            AiModelSlot3GridFsService aiModelSlot3GridFsService,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            RestaurantOrderRepository orderRepository,
            ContextoInteligenciaService contextoInteligenciaService,
            ObjectMapper objectMapper
    ) {
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.aiModelSlot3GridFsService = aiModelSlot3GridFsService;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.orderRepository = orderRepository;
        this.contextoInteligenciaService = contextoInteligenciaService;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(120_000);
        this.hfLongTimeout = new RestTemplate(f);
    }

    public Map<String, Object> ejecutarPrediccionInventario() {
        AiModelConfig config = aiModelConfigRepository.findById(CONFIG_ID)
                .orElseThrow(() -> new IllegalStateException("No hay configuración de modelos IA."));
        AiModelConfig.ModelSlot slot3 = config.getSlots().stream()
                .filter(s -> s.getSlotNumber() == 3)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Slot 3 no configurado."));
        if (!slot3.isSlotEnabled()) {
            throw new IllegalStateException("El Slot 3 de predicción de inventario está desactivado.");
        }
        String modelFileB64 = resolveSlot3ModelB64(slot3);
        String metaModeloB64 = resolveSlot3MetaB64(slot3);
        if (!"ACTIVO".equalsIgnoreCase(slot3.getStatus()) || isBlank(modelFileB64) || isBlank(metaModeloB64)) {
            throw new IllegalStateException("El modelo del Slot 3 no está cargado o no está activo.");
        }
        if (isBlank(slot3.getModelFileBase64())) {
            log.debug("[SLOT3] Predicción: binarios modelo/meta cargados desde GridFS");
        }

        MetaSlot3 meta = parseMeta(metaModeloB64);
        log.info(
                "[DASHBOARD] inventario-prediccion pipeline meta seqLen={} horizontes={} cols={} modelChars={} metaChars={}",
                meta.seqLen, meta.horizonKeys.size(), meta.featureCols.size(),
                modelFileB64 != null ? modelFileB64.length() : 0,
                metaModeloB64 != null ? metaModeloB64.length() : 0
        );
        LocalDate today = LocalDate.now(Z);
        LocalDate startHistory = today.minusDays(200);
        LocalDateTime fromDt = startHistory.atStartOfDay(Z).toLocalDateTime();
        LocalDateTime toDt = today.plusDays(1).atStartOfDay(Z).toLocalDateTime();

        Map<LocalDate, Integer> ordersByDay = loadOrdersPerDay(fromDt, toDt);
        int maxOrders = ordersByDay.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        Map<LocalDate, WeatherDay> weather = loadWeatherArchive(startHistory, today);

        List<Object[]> agg = movementRepository.aggregateDailyByInventory(fromDt, toDt);
        Map<Integer, TreeMap<LocalDate, double[]>> byInv = new HashMap<>();
        for (Object[] row : agg) {
            int invId = ((Number) row[0]).intValue();
            LocalDate d = toLocalDate(row[1]);
            double cons = toDouble(row[2]);
            double rest = toDouble(row[3]);
            byInv.computeIfAbsent(invId, k -> new TreeMap<>()).put(d, new double[]{cons, rest});
        }

        log.info(
                "[DASHBOARD] inventario-prediccion datos diasConPedidos={} filasAggMovimiento={} insumosDistintosEnSerie={}",
                ordersByDay.size(), agg.size(), byInv.size()
        );

        List<Inventory> inventarios = inventoryRepository.findAllByIsDeletedFalse();
        ContextoInteligenciaService.ContextoInteligencia ctxNow = contextoInteligenciaService.contextoActual();

        List<Integer> ingredientIds = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> units = new ArrayList<>();
        List<Double> stocks = new ArrayList<>();
        List<double[][]> batchRaw = new ArrayList<>();

        for (Inventory inv : inventarios) {
            if (inv.getId() == null) continue;
            double stockAct = inv.getStockQuantity() == null ? 0d : inv.getStockQuantity();
            TreeMap<LocalDate, double[]> series = byInv.getOrDefault(inv.getId(), new TreeMap<>());
            double[][] seq = buildSequenceRaw(
                    today,
                    meta.seqLen,
                    series,
                    ordersByDay,
                    maxOrders,
                    weather,
                    stockAct,
                    ctxNow,
                    meta.featureCols
            );
            if (seq == null) continue;
            ingredientIds.add(inv.getId());
            names.add(inv.getName() == null ? ("ID " + inv.getId()) : inv.getName());
            units.add(inv.getUnit() == null ? "" : inv.getUnit());
            stocks.add(stockAct);
            batchRaw.add(seq);
        }

        log.info(
                "[DASHBOARD] inventario-prediccion ventana inventariosActivos={} lotesListosInferencia={}",
                inventarios.size(), ingredientIds.size()
        );

        if (ingredientIds.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("mensaje", "No hay insumos con historial suficiente para formar la ventana de predicción.");
            empty.put("items", List.of());
            empty.put("alertasCriticas", List.of());
            empty.put("heatmap", Map.of("filas", List.of(), "columnas", meta.horizonKeys, "valores", List.of()));
            empty.put("horizonKeys", meta.horizonKeys);
            empty.put("horizonLabels", horizonLabelsEs());
            log.warn(
                    "[DASHBOARD] inventario-prediccion respuesta vacia items=0 inventariosActivos={}",
                    inventarios.size()
            );
            return empty;
        }

        List<List<List<Double>>> inputs = new ArrayList<>();
        for (double[][] raw : batchRaw) {
            inputs.add(scaleSequence(raw, meta.featMin, meta.featMax));
        }

        String modelId = "slot3|" + slot3.getUploadedAt() + "|" + slot3.getModelFileName();
        log.info("[DASHBOARD] inventario-prediccion llamando HF inputsLotes={} modelId={}", inputs.size(), modelId);
        List<List<Double>> predictions = callHfPredictInventory(modelFileB64, modelId, inputs);

        if (predictions.size() != ingredientIds.size()) {
            throw new IllegalStateException("La respuesta del servicio de inferencia no coincide con el lote enviado.");
        }
        log.info("[DASHBOARD] inventario-prediccion HF ok predicciones={}", predictions.size());

        List<Map<String, Object>> items = new ArrayList<>();
        List<List<Double>> heatmapVals = new ArrayList<>();

        for (int i = 0; i < ingredientIds.size(); i++) {
            List<Double> ySc = predictions.get(i);
            if (ySc == null || ySc.size() < meta.yMin.length) {
                throw new IllegalStateException("Predicción incompleta para un insumo.");
            }
            double[] yReal = new double[meta.yMin.length];
            for (int j = 0; j < meta.yMin.length; j++) {
                double scaled = ySc.get(j);
                yReal[j] = inverseMinMax1(scaled, meta.yMin[j], meta.yMax[j]);
                if (yReal[j] < 0) yReal[j] = 0;
            }

            double stockAct = stocks.get(i);
            Map<String, Object> predPorHorizonte = new LinkedHashMap<>();
            List<Double> heatRow = new ArrayList<>();

            for (int h = 0; h < meta.horizonKeys.size(); h++) {
                String key = meta.horizonKeys.get(h);
                int dias = meta.horizonDays.get(h);
                double consumo = yReal[h];
                double stockEst = Math.max(0d, stockAct - consumo);
                double pct = Math.min(100d, (consumo / (stockAct + EPS)) * 100d);
                double diasAgot = consumo > EPS ? stockAct / (consumo / dias) : 9999d;
                if (diasAgot > 9999d) diasAgot = 9999d;
                boolean alerta = pct >= 80d;

                Map<String, Object> hm = new LinkedHashMap<>();
                hm.put("diasHorizonte", dias);
                hm.put("consumoEstimado", round3(consumo));
                hm.put("stockRestanteEstimado", round3(stockEst));
                hm.put("pctConsumido", round1(pct));
                hm.put("diasHastaAgotamiento", round1(diasAgot));
                hm.put("alerta", alerta);
                predPorHorizonte.put(key, hm);
                heatRow.add(round1(pct));
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ingredientId", ingredientIds.get(i));
            row.put("name", names.get(i));
            row.put("unit", units.get(i));
            row.put("stockActual", round3(stockAct));
            row.put("predicciones", predPorHorizonte);
            items.add(row);
            heatmapVals.add(heatRow);
        }

        List<Map<String, Object>> alertas = new ArrayList<>();
        String defaultHorizon = meta.horizonKeys.isEmpty() ? null : meta.horizonKeys.get(0);
        for (Map<String, Object> row : items) {
            @SuppressWarnings("unchecked")
            Map<String, Object> preds = (Map<String, Object>) row.get("predicciones");
            if (defaultHorizon == null || preds == null) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> h0 = (Map<String, Object>) preds.get(defaultHorizon);
            if (h0 == null) continue;
            boolean alerta = Boolean.TRUE.equals(h0.get("alerta"));
            if (!alerta) continue;
            double dias = ((Number) h0.get("diasHastaAgotamiento")).doubleValue();
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("ingredientId", row.get("ingredientId"));
            a.put("name", row.get("name"));
            a.put("horizonte", defaultHorizon);
            a.put("alerta", true);
            a.put("diasOrden", dias);
            if (dias >= 9980d) {
                a.put("mensaje", "Riesgo alto de agotamiento en el horizonte seleccionado.");
            } else {
                a.put("mensaje", "Te quedan " + round1(dias) + " días para que se agote este ingrediente.");
            }
            alertas.add(a);
        }
        alertas.sort(Comparator.comparingDouble(a -> ((Number) a.get("diasOrden")).doubleValue()));
        List<Map<String, Object>> alertasOut = new ArrayList<>();
        for (Map<String, Object> a : alertas.stream().limit(20).toList()) {
            Map<String, Object> copy = new LinkedHashMap<>(a);
            copy.remove("diasOrden");
            alertasOut.add(copy);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("horizonKeys", meta.horizonKeys);
        out.put("horizonLabels", horizonLabelsEs());
        out.put("defaultHorizon", defaultHorizon);
        out.put("items", items);
        out.put("alertasCriticas", alertasOut);
        out.put("heatmap", Map.of(
                "filas", names,
                "columnas", meta.horizonKeys.stream().map(k -> horizonLabelsEs().getOrDefault(k, k)).toList(),
                "valores", heatmapVals,
                "keys", meta.horizonKeys
        ));
        log.info(
                "[DASHBOARD] inventario-prediccion respuesta items={} alertasCriticas={} heatmapFilas={} heatmapCols={}",
                items.size(), alertasOut.size(), names.size(), meta.horizonKeys.size()
        );
        return out;
    }

    private Map<String, String> horizonLabelsEs() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("1_semana", "Próxima 1 semana");
        m.put("2_semanas", "Próximas 2 semanas");
        m.put("3_semanas", "Próximas 3 semanas");
        m.put("1_mes", "Próximo 1 mes");
        m.put("2_meses", "Próximos 2 meses");
        m.put("3_meses", "Próximos 3 meses");
        return m;
    }

    private List<List<Double>> callHfPredictInventory(String modelB64, String modelId, List<List<List<Double>>> inputs) {
        String url = hfBaseUrl.endsWith("/") ? hfBaseUrl + "predict-inventory" : hfBaseUrl + "/predict-inventory";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (hfToken != null && !hfToken.isBlank()) {
        headers.setBearerAuth(hfToken);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model_base64", modelB64);
        body.put("model_id", modelId);
        body.put("inputs", inputs);
        try {
            HfInvResp resp = hfLongTimeout.postForObject(url, new HttpEntity<>(body, headers), HfInvResp.class);
            if (resp == null || resp.predictions() == null) {
                throw new IllegalStateException("Respuesta vacía del servicio de inferencia.");
            }
            return resp.predictions();
        } catch (Exception e) {
            throw new IllegalStateException("Fallo al llamar al modelo en Hugging Face: " + e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HfInvResp(@JsonProperty("predictions") List<List<Double>> predictions) {
    }

    private Map<LocalDate, Integer> loadOrdersPerDay(LocalDateTime from, LocalDateTime toEx) {
        Map<LocalDate, Integer> map = new HashMap<>();
        for (Object[] row : orderRepository.countOrdersPerDay(from, toEx)) {
            LocalDate d = toLocalDate(row[0]);
            int n = ((Number) row[1]).intValue();
            map.put(d, n);
        }
        return map;
    }

    private Map<LocalDate, WeatherDay> loadWeatherArchive(LocalDate start, LocalDate end) {
        Map<LocalDate, WeatherDay> out = new HashMap<>();
        try {
            String url = String.format(Locale.US,
                    "https://archive-api.open-meteo.com/v1/archive?latitude=-12.0686&longitude=-75.2102&start_date=%s&end_date=%s&daily=weathercode,temperature_2m_max",
                    start, end);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = new RestTemplate().getForObject(url, Map.class);
            if (root == null || !root.containsKey("daily")) return fillDefault(out, start, end);
            @SuppressWarnings("unchecked")
            Map<String, Object> daily = (Map<String, Object>) root.get("daily");
            @SuppressWarnings("unchecked")
            List<String> times = (List<String>) daily.get("time");
            @SuppressWarnings("unchecked")
            List<Number> codes = (List<Number>) daily.get("weathercode");
            @SuppressWarnings("unchecked")
            List<Number> temps = (List<Number>) daily.get("temperature_2m_max");
            if (times == null || codes == null) return fillDefault(out, start, end);
            for (int i = 0; i < times.size(); i++) {
                LocalDate d = LocalDate.parse(times.get(i));
                int code = i < codes.size() && codes.get(i) != null ? codes.get(i).intValue() : -1;
                double temp = i < temps.size() && temps.get(i) != null ? temps.get(i).doubleValue() : 18d;
                out.put(d, new WeatherDay(mapWeatherCode(code), normTemp(temp)));
            }
        } catch (Exception ignored) {
            return fillDefault(out, start, end);
        }
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            out.putIfAbsent(d, new WeatherDay("DESCONOCIDO", 0.36d));
        }
        return out;
    }

    private Map<LocalDate, WeatherDay> fillDefault(Map<LocalDate, WeatherDay> out, LocalDate start, LocalDate end) {
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            out.put(d, new WeatherDay("DESCONOCIDO", 0.36d));
        }
        return out;
    }

    private String mapWeatherCode(int code) {
        if (code < 0) return "DESCONOCIDO";
        if (code == 0) return "SOLEADO";
        if (code == 1 || code == 2) return "PARCIALMENTE_NUBLADO";
        if (code == 3 || code == 45 || code == 48) return "NUBLADO";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "LLUVIOSO";
        if (code >= 95) return "TORMENTA";
        return "OTRO";
    }

    private double normTemp(double tempC) {
        double v = (tempC + 5d) / 55d;
        return Math.max(0d, Math.min(1d, v));
    }

    private double[][] buildSequenceRaw(
            LocalDate today,
            int seqLen,
            TreeMap<LocalDate, double[]> series,
            Map<LocalDate, Integer> ordersByDay,
            int maxOrders,
            Map<LocalDate, WeatherDay> weatherByDay,
            double currentStock,
            ContextoInteligenciaService.ContextoInteligencia ctxNow,
            List<String> featureCols
    ) {
        List<LocalDate> days = new ArrayList<>();
        for (int i = seqLen - 1; i >= 0; i--) {
            days.add(today.minusDays(i));
        }

        int n = days.size();
        double[] cons = new double[n];
        double[] rest = new double[n];
        for (int i = 0; i < n; i++) {
            LocalDate d = days.get(i);
            double[] cr = series.get(d);
            if (cr != null) {
                cons[i] = cr[0];
                rest[i] = cr[1];
            }
        }

        double[] stockEnd = new double[n];
        stockEnd[n - 1] = Math.max(0d, currentStock);
        for (int i = n - 2; i >= 0; i--) {
            stockEnd[i] = stockEnd[i + 1] + cons[i + 1] - rest[i + 1];
            if (stockEnd[i] < 0) stockEnd[i] = 0;
        }

        double stockMax = Arrays.stream(stockEnd).max().orElse(0d);
        if (stockMax < EPS) stockMax = 1d;

        double[][] rows = new double[n][featureCols.size()];
        for (int t = 0; t < n; t++) {
            LocalDate d = days.get(t);
            WeatherDay w = weatherByDay.getOrDefault(d, new WeatherDay("DESCONOCIDO", 0.36));
            boolean isToday = d.equals(today);
            String cond = isToday && ctxNow.condition() != null ? ctxNow.condition() : w.condition();
            double tempN = isToday && ctxNow.temp() != null ? normTemp(ctxNow.temp()) : w.tempNorm();
            String moment = isToday ? mapSegmentFromCtx(ctxNow.segment()) : "MAÑANA";

            int dow0 = (d.getDayOfWeek().getValue() + 6) % 7;
            double dowSin = Math.sin(2 * Math.PI * dow0 / 7d);
            double dowCos = Math.cos(2 * Math.PI * dow0 / 7d);
            int month = d.getMonthValue();
            double monthSin = Math.sin(2 * Math.PI * month / 12d);
            double monthCos = Math.cos(2 * Math.PI * month / 12d);
            int woy = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            double woySin = Math.sin(2 * Math.PI * woy / 52d);
            double woyCos = Math.cos(2 * Math.PI * woy / 52d);
            int weatherEnc = WEATHER_MAP.getOrDefault(normCond(cond), 5);
            int momentEnc = MOMENT_MAP.getOrDefault(moment == null ? "DESCONOCIDO" : moment.trim().toUpperCase(Locale.ROOT), 2);
            int isWeekend = dow0 >= 5 ? 1 : 0;
            int hadRestock = rest[t] > EPS ? 1 : 0;
            double ordersNorm = ordersByDay.getOrDefault(d, 0) / (double) Math.max(1, maxOrders);

            for (int fi = 0; fi < featureCols.size(); fi++) {
                String col = featureCols.get(fi);
                rows[t][fi] = featureValue(
                        col, t, cons, rest, stockEnd, stockMax,
                        dowSin, dowCos, monthSin, monthCos, woySin, woyCos,
                        weatherEnc, momentEnc, tempN, isWeekend, hadRestock, ordersNorm
                );
            }
        }
        return rows;
    }

    private String mapSegmentFromCtx(String segment) {
        if (segment == null) return "DESCONOCIDO";
        return segment.trim().toUpperCase(Locale.ROOT);
    }

    private String normCond(String c) {
        if (c == null) return "DESCONOCIDO";
        String u = c.trim().toUpperCase(Locale.ROOT);
        if (WEATHER_MAP.containsKey(u)) return u;
        return "DESCONOCIDO";
    }

    private double featureValue(
            String col,
            int t,
            double[] cons,
            double[] rest,
            double[] stockEnd,
            double stockMax,
            double dowSin,
            double dowCos,
            double monthSin,
            double monthCos,
            double woySin,
            double woyCos,
            int weatherEnc,
            int momentEnc,
            double tempNorm,
            int isWeekend,
            int hadRestock,
            double ordersNorm
    ) {
        return switch (col) {
            case "consumption" -> cons[t];
            case "dow_sin" -> dowSin;
            case "dow_cos" -> dowCos;
            case "month_sin" -> monthSin;
            case "month_cos" -> monthCos;
            case "woy_sin" -> woySin;
            case "woy_cos" -> woyCos;
            case "weather_enc" -> weatherEnc;
            case "moment_enc" -> momentEnc;
            case "temp_norm" -> tempNorm;
            case "is_weekend" -> isWeekend;
            case "had_restock" -> hadRestock;
            case "orders_norm" -> ordersNorm;
            case "lag_1" -> lag(cons, t, 1);
            case "lag_2" -> lag(cons, t, 2);
            case "lag_3" -> lag(cons, t, 3);
            case "lag_7" -> lag(cons, t, 7);
            case "lag_14" -> lag(cons, t, 14);
            case "lag_30" -> lag(cons, t, 30);
            case "roll_mean_7" -> rollMean(cons, t, 7);
            case "roll_std_7" -> rollStd(cons, t, 7);
            case "roll_max_7" -> rollMax(cons, t, 7);
            case "roll_mean_14" -> rollMean(cons, t, 14);
            case "roll_std_14" -> rollStd(cons, t, 14);
            case "roll_max_14" -> rollMax(cons, t, 14);
            case "roll_mean_30" -> rollMean(cons, t, 30);
            case "roll_std_30" -> rollStd(cons, t, 30);
            case "roll_max_30" -> rollMax(cons, t, 30);
            case "trend_7" -> trend7(cons, t);
            case "stock_norm" -> stockEnd[t] / (stockMax + EPS);
            case "stock_lag1" -> t > 0 ? stockEnd[t - 1] : 0d;
            case "stock_lag7" -> t >= 7 ? stockEnd[t - 7] : 0d;
            case "stock_pct_chg" -> stockPctChg(stockEnd, t);
            case "days_until_depletion" -> {
                double roll7 = rollMean(cons, t, 7);
                yield Math.min(365d, stockEnd[t] / (roll7 + EPS));
            }
            default -> throw new IllegalArgumentException("Columna de feature no soportada en inferencia: " + col);
        };
    }

    private double stockPctChg(double[] stock, int t) {
        if (t <= 0) return 0d;
        double prev = stock[t - 1];
        if (Math.abs(prev) < EPS) return 0d;
        double raw = (stock[t] - prev) / prev;
        return Math.max(-1d, Math.min(5d, raw));
    }

    private double lag(double[] cons, int t, int L) {
        int i = t - L;
        return i >= 0 ? cons[i] : 0d;
    }

    private double rollMean(double[] cons, int t, int w) {
        int from = Math.max(0, t - w);
        int to = t - 1;
        if (to < from) return 0d;
        double s = 0;
        int c = 0;
        for (int i = from; i <= to; i++) {
            s += cons[i];
            c++;
        }
        return c == 0 ? 0d : s / c;
    }

    private double rollStd(double[] cons, int t, int w) {
        int from = Math.max(0, t - w);
        int to = t - 1;
        if (to < from) return 0d;
        double m = rollMean(cons, t, w);
        double acc = 0;
        int c = 0;
        for (int i = from; i <= to; i++) {
            double d = cons[i] - m;
            acc += d * d;
            c++;
        }
        if (c <= 1) return 0d;
        return Math.sqrt(acc / (c - 1));
    }

    private double rollMax(double[] cons, int t, int w) {
        int from = Math.max(0, t - w);
        int to = t - 1;
        if (to < from) return 0d;
        double mx = 0;
        for (int i = from; i <= to; i++) mx = Math.max(mx, cons[i]);
        return mx;
    }

    private double trend7(double[] cons, int t) {
        int from = Math.max(0, t - 7);
        int to = t - 1;
        if (to < from + 1) return 0d;
        int n = to - from + 1;
        double sumX = 0, sumY = 0, sumXX = 0, sumXY = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = cons[from + i];
            sumX += x;
            sumY += y;
            sumXX += x * x;
            sumXY += x * y;
        }
        double den = n * sumXX - sumX * sumX;
        if (Math.abs(den) < EPS) return 0d;
        return (n * sumXY - sumX * sumY) / den;
    }

    private List<List<Double>> scaleSequence(double[][] raw, double[] featMin, double[] featMax) {
        List<List<Double>> out = new ArrayList<>();
        for (double[] row : raw) {
            List<Double> line = new ArrayList<>();
            for (int j = 0; j < row.length; j++) {
                line.add(minMax1(row[j], featMin[j], featMax[j]));
            }
            out.add(line);
        }
        return out;
    }

    private double minMax1(double x, double min, double max) {
        double den = max - min;
        if (Math.abs(den) < EPS) return 0d;
        double v = (x - min) / den;
        return Math.max(0d, Math.min(1d, v));
    }

    private double inverseMinMax1(double x, double min, double max) {
        double den = max - min;
        if (Math.abs(den) < EPS) return min;
        return x * den + min;
    }

    private MetaSlot3 parseMeta(String metaB64) {
        log.debug("[SLOT3-UPLOAD] Meta: parseMeta inicio");
        try {
            byte[] raw = decodeB64(metaB64);
            log.info("[SLOT3-UPLOAD] Meta: JSON decodificado, {} bytes", raw.length);
            JsonNode root = objectMapper.readTree(raw);
            log.debug("[SLOT3-UPLOAD] Meta: árbol JSON raíz OK");

            JsonNode cols = root.get("feature_cols");
            if (cols == null || !cols.isArray() || cols.isEmpty()) {
                log.warn("[SLOT3-UPLOAD] Meta: feature_cols ausente o vacío");
                throw new IllegalArgumentException("meta_modelo.json debe contener feature_cols.");
            }
            List<String> featureCols = new ArrayList<>();
            for (JsonNode c : cols) {
                featureCols.add(c.asText());
            }
            log.info("[SLOT3-UPLOAD] Meta: feature_cols n={} primeras={}…",
                    featureCols.size(),
                    featureCols.size() > 3 ? featureCols.subList(0, Math.min(3, featureCols.size())) : featureCols);

            int seqLen = root.path("seq_len").asInt(60);
            log.info("[SLOT3-UPLOAD] Meta: seq_len={}", seqLen);

            JsonNode hor = root.get("horizontes");
            if (hor == null || !hor.isObject()) {
                log.warn("[SLOT3-UPLOAD] Meta: horizontes ausente o no es objeto");
                throw new IllegalArgumentException("meta_modelo.json debe contener horizontes.");
            }
            List<String> keys = new ArrayList<>();
            List<Integer> dayVals = new ArrayList<>();
            List<Map.Entry<String, Integer>> tmp = new ArrayList<>();
            var it = hor.fields();
            while (it.hasNext()) {
                var e = it.next();
                tmp.add(Map.entry(e.getKey(), e.getValue().asInt()));
            }
            tmp.sort(Comparator.comparingInt(Map.Entry::getValue));
            for (var e : tmp) {
                keys.add(e.getKey());
                dayVals.add(e.getValue());
            }
            log.info("[SLOT3-UPLOAD] Meta: horizontes ordenados n={} keys={}", keys.size(), keys);

            log.debug("[SLOT3-UPLOAD] Meta: leyendo feat_data_min/max (esperado {})", featureCols.size());
            double[] featMin = readDoubleArray(root, "feat_data_min", featureCols.size());
            double[] featMax = readDoubleArray(root, "feat_data_max", featureCols.size());
            log.debug("[SLOT3-UPLOAD] Meta: leyendo y_data_min/max (esperado {})", keys.size());
            double[] yMin = readDoubleArray(root, "y_data_min", keys.size());
            double[] yMax = readDoubleArray(root, "y_data_max", keys.size());

            log.info("[SLOT3-UPLOAD] Meta: escaladores presentes y longitudes correctas");
            return new MetaSlot3(seqLen, featureCols, keys, dayVals, featMin, featMax, yMin, yMax);
        } catch (IllegalArgumentException e) {
            log.warn("[SLOT3-UPLOAD] Meta: IllegalArgumentException {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[SLOT3-UPLOAD] Meta: error al parsear JSON", e);
            throw new IllegalArgumentException("No se pudo leer meta_modelo.json: " + e.getMessage());
        }
    }

    private double[] readDoubleArray(JsonNode root, String field, int expectedLen) {
        JsonNode arr = root.get(field);
        if (arr == null || !arr.isArray() || arr.size() != expectedLen) {
            int actual = (arr == null || !arr.isArray()) ? -1 : arr.size();
            log.warn("[SLOT3-UPLOAD] Meta: campo '{}' esperaba {} elementos, actual={}", field, expectedLen, actual);
            throw new IllegalArgumentException(
                    "meta_modelo.json debe incluir " + field + " como arreglo numérico de longitud " + expectedLen
                            + " (exporte data_min_/data_max_ del MinMaxScaler al guardar el meta).");
        }
        double[] out = new double[expectedLen];
        for (int i = 0; i < expectedLen; i++) {
            out[i] = arr.get(i).asDouble();
        }
        return out;
    }

    private byte[] decodeB64(String value) {
        if (isBlank(value)) return new byte[0];
        String raw = value.trim();
        int comma = raw.indexOf(',');
        if (raw.startsWith("data:") && comma >= 0) {
            raw = raw.substring(comma + 1);
        }
        return java.util.Base64.getDecoder().decode(raw);
    }

    private LocalDate toLocalDate(Object sqlDate) {
        if (sqlDate instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (sqlDate instanceof LocalDate ld) {
            return ld;
        }
        return LocalDate.parse(String.valueOf(sqlDate));
    }

    private double toDouble(Object o) {
        if (o == null) return 0d;
        if (o instanceof BigDecimal b) return b.doubleValue();
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    private double round1(double v) {
        return Math.round(v * 10d) / 10d;
    }

    private double round3(double v) {
        return Math.round(v * 1000d) / 1000d;
    }

    private String resolveSlot3ModelB64(AiModelConfig.ModelSlot s) {
        if (!isBlank(s.getModelFileBase64())) {
            return s.getModelFileBase64();
        }
        return aiModelSlot3GridFsService.readAsDataUrlBase64(s.getModelFileGridFsId());
    }

    private String resolveSlot3MetaB64(AiModelConfig.ModelSlot s) {
        if (!isBlank(s.getMetaModeloBase64())) {
            return s.getMetaModeloBase64();
        }
        return aiModelSlot3GridFsService.readAsDataUrlBase64(s.getMetaModeloGridFsId());
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private record MetaSlot3(
            int seqLen,
            List<String> featureCols,
            List<String> horizonKeys,
            List<Integer> horizonDays,
            double[] featMin,
            double[] featMax,
            double[] yMin,
            double[] yMax
    ) {
    }

    private record WeatherDay(String condition, double tempNorm) {
    }

    public void validarMetaModeloInventario(String metaModeloBase64) {
        int len = metaModeloBase64 == null ? 0 : metaModeloBase64.length();
        log.info("[SLOT3-UPLOAD] Meta: validarMetaModeloInventario llamada, base64 length={}", len);
        try {
            parseMeta(metaModeloBase64);
            log.info("[SLOT3-UPLOAD] Meta: validarMetaModeloInventario OK");
        } catch (IllegalArgumentException e) {
            log.warn("[SLOT3-UPLOAD] Meta: validarMetaModeloInventario rechazó: {}", e.getMessage());
            throw e;
        }
    }
}
