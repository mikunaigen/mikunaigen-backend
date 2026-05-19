package com.mikunaigen.backend.service.dashboard;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.model.nosql.AiModelConfig;
import com.mikunaigen.backend.model.nosql.Producto;
import com.mikunaigen.backend.model.sql.LoginAudit;
import com.mikunaigen.backend.model.sql.RestaurantOrder;
import com.mikunaigen.backend.repository.nosql.AiModelConfigRepository;
import com.mikunaigen.backend.repository.nosql.ProductoRepository;
import com.mikunaigen.backend.repository.nosql.UserInteractionAnalyticsSupport;
import com.mikunaigen.backend.repository.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
public class AdminDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardService.class);

    private static final List<String> ESTADOS_EN_CURSO = List.of(
            "PENDIENTE_PAGO",
            "VALIDANDO_PAGO",
            "PAGO_VALIDADO",
            "EN_COCINA",
            "PREPARADO",
            "EN_CAMINO"
    );

    private static final List<String> POST_VALIDACION = List.of(
            "PAGO_VALIDADO",
            "EN_COCINA",
            "PREPARADO",
            "EN_CAMINO",
            "ENTREGADO"
    );

    private static final List<String> DIAS_SEMANA = List.of(
            "LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"
    );

    private final RestaurantOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final RecipeRepository recipeRepository;
    private final ProductoRepository productoRepository;
    private final OrderRatingRepository orderRatingRepository;
    private final UserRepository userRepository;
    private final LoginAuditRepository loginAuditRepository;
    private final IpLoginAttemptRepository ipLoginAttemptRepository;
    private final AiModelConfigRepository aiModelConfigRepository;
    private final DashboardOrderTupleSupport orderDashSupport;
    private final UserInteractionAnalyticsSupport userInteractionAnalyticsSupport;

    public AdminDashboardService(
            RestaurantOrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            RecipeRepository recipeRepository,
            ProductoRepository productoRepository,
            OrderRatingRepository orderRatingRepository,
            UserRepository userRepository,
            LoginAuditRepository loginAuditRepository,
            IpLoginAttemptRepository ipLoginAttemptRepository,
            AiModelConfigRepository aiModelConfigRepository,
            DashboardOrderTupleSupport orderDashSupport,
            UserInteractionAnalyticsSupport userInteractionAnalyticsSupport
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.recipeRepository = recipeRepository;
        this.productoRepository = productoRepository;
        this.orderRatingRepository = orderRatingRepository;
        this.userRepository = userRepository;
        this.loginAuditRepository = loginAuditRepository;
        this.ipLoginAttemptRepository = ipLoginAttemptRepository;
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.orderDashSupport = orderDashSupport;
        this.userInteractionAnalyticsSupport = userInteractionAnalyticsSupport;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ventasPedidos(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String status,
            String momentOfDay,
            String dayOfWeek,
            String weatherCondition
    ) {
        Specification<RestaurantOrder> spec = baseSpec(from, toExclusive, status, momentOfDay, dayOfWeek, weatherCondition);
        List<DashboardOrderTupleSupport.OrderDashRow> orders = orderDashSupport.fetch(spec);

        BigDecimal totalVentas = BigDecimal.ZERO;
        long nEntregados = 0;
        long nCancelados = 0;
        long nTotal = orders.size();
        Map<String, Long> porEstado = new HashMap<>();
        Map<String, BigDecimal> ventasPorDia = new TreeMap<>();
        Map<Integer, BigDecimal> ingresoPorHora = new TreeMap<>();
        Map<String, Long> pedidosPorDiaSemana = new LinkedHashMap<>();
        for (String d : DIAS_SEMANA) {
            pedidosPorDiaSemana.put(d, 0L);
        }
        double[][] heat = new double[24][7];
        Map<String, List<BigDecimal>> ticketPorSemana = new TreeMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDate hoy = now.toLocalDate();

        long pedidosEnCursoGlobal = orderRepository.count((root, q, cb) -> root.get("status").in(ESTADOS_EN_CURSO));

        for (DashboardOrderTupleSupport.OrderDashRow o : orders) {
            String st = o.status() != null ? o.status() : "";
            porEstado.merge(st, 1L, Long::sum);
            if ("ENTREGADO".equals(st)) {
                nEntregados++;
                BigDecimal tp = o.totalPrice() != null ? o.totalPrice() : BigDecimal.ZERO;
                totalVentas = totalVentas.add(tp);
                LocalDateTime ca = o.createdAt();
                if (ca != null) {
                    String dayKey = ca.toLocalDate().toString();
                    ventasPorDia.merge(dayKey, tp, BigDecimal::add);
                    int h = ca.getHour();
                    ingresoPorHora.merge(h, tp, BigDecimal::add);
                    String dw = o.dayOfWeek();
                    if (dw != null && pedidosPorDiaSemana.containsKey(dw)) {
                        pedidosPorDiaSemana.merge(dw, 1L, Long::sum);
                    }
                    int dowIdx;
                    if (dw != null && DIAS_SEMANA.contains(dw)) {
                        dowIdx = DIAS_SEMANA.indexOf(dw);
                    } else {
                        dowIdx = Math.max(0, Math.min(6, ca.getDayOfWeek().getValue() - 1));
                    }
                    heat[h][dowIdx] += tp.doubleValue();

                    LocalDate semana = ca.toLocalDate().with(java.time.DayOfWeek.MONDAY);
                    ticketPorSemana.computeIfAbsent(semana.toString(), k -> new ArrayList<>()).add(tp);
                }
            }
            if ("CANCELADO".equals(st)) {
                nCancelados++;
            }
        }

        double ticketPromedio = nEntregados > 0 ? totalVentas.divide(BigDecimal.valueOf(nEntregados), 2, RoundingMode.HALF_UP).doubleValue() : 0;
        double tasaCancelacion = nTotal > 0 ? round2(100.0 * nCancelados / nTotal) : 0;
        long cerradosOCancel = nEntregados + nCancelados;
        double conversion = cerradosOCancel > 0 ? round2(100.0 * nEntregados / cerradosOCancel) : 0;

        long pedidosHoy = orders.stream()
                .filter(o -> o.createdAt() != null && hoy.equals(o.createdAt().toLocalDate()))
                .count();
        BigDecimal ingresoHoy = BigDecimal.ZERO;
        for (DashboardOrderTupleSupport.OrderDashRow o : orders) {
            if (!"ENTREGADO".equals(o.status())) {
                continue;
            }
            LocalDate ref = o.deliveredAt() != null
                    ? o.deliveredAt().toLocalDate()
                    : (o.createdAt() != null ? o.createdAt().toLocalDate() : null);
            if (ref != null && ref.equals(hoy)) {
                ingresoHoy = ingresoHoy.add(o.totalPrice() != null ? o.totalPrice() : BigDecimal.ZERO);
            }
        }

        List<Map<String, Object>> ticketSemanal = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> e : ticketPorSemana.entrySet()) {
            BigDecimal sum = e.getValue().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            double avg = e.getValue().isEmpty() ? 0 : sum.divide(BigDecimal.valueOf(e.getValue().size()), 2, RoundingMode.HALF_UP).doubleValue();
            ticketSemanal.add(row("semana", e.getKey(), "ticketPromedio", avg));
        }

        List<Map<String, Object>> heatList = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            for (int d = 0; d < 7; d++) {
                heatList.add(row("hora", h, "diaIndex", d, "valor", heat[h][d]));
            }
        }

        List<Map<String, Object>> climaVsMonto = new ArrayList<>();
        for (DashboardOrderTupleSupport.OrderDashRow o : orders) {
            if (!"ENTREGADO".equals(o.status()) || o.weatherTempC() == null) {
                continue;
            }
            climaVsMonto.add(row(
                    "tempC", o.weatherTempC(),
                    "monto", o.totalPrice() != null ? o.totalPrice().doubleValue() : 0.0
            ));
            if (climaVsMonto.size() >= 400) {
                break;
            }
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalVentas", totalVentas.setScale(2, RoundingMode.HALF_UP).doubleValue());
        kpis.put("numPedidos", nTotal);
        kpis.put("ticketPromedio", ticketPromedio);
        kpis.put("tasaCancelacionPct", tasaCancelacion);
        kpis.put("conversionPct", conversion);
        kpis.put("pedidosHoy", pedidosHoy);
        kpis.put("ingresoHoy", ingresoHoy.setScale(2, RoundingMode.HALF_UP).doubleValue());
        kpis.put("pedidosEnCurso", pedidosEnCursoGlobal);

        log.info(
                "[DASHBOARD] ventas-pedidos from={} toEx={} pedidosFiltrados={} entregados={} cancelados={} "
                        + "ingresoPorHoraPts={} ventasPorDiaPts={} heatmapPts={} climaScatterPts={} ticketSemanalPts={}",
                from, toExclusive, nTotal, nEntregados, nCancelados,
                ingresoPorHora.size(), ventasPorDia.size(), heatList.size(), climaVsMonto.size(), ticketSemanal.size()
        );

        return Map.of(
                "kpis", kpis,
                "pedidosPorEstado", porEstado,
                "ventasPorDia", ventasPorDia,
                "ingresoPorHora", ingresoPorHora.entrySet().stream()
                        .map(e -> row("hora", e.getKey(), "monto", e.getValue().setScale(2, RoundingMode.HALF_UP).doubleValue()))
                        .toList(),
                "pedidosPorDiaSemana", pedidosPorDiaSemana,
                "evolucionTicketSemanal", ticketSemanal,
                "heatmapHoraDia", heatList,
                "climaTemperaturaVsMonto", climaVsMonto
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> inventarioCostos(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String categoriaInsumo,
            String tipoMovimiento,
            boolean soloStockBajo,
            double umbralStockBajo
    ) {
        String tipoNorm = (tipoMovimiento == null || tipoMovimiento.isBlank()) ? null : tipoMovimiento.trim();
        String catFilter = (categoriaInsumo == null || categoriaInsumo.isBlank()) ? null : categoriaInsumo.trim();

        List<InvProj> inv = new ArrayList<>();
        for (Object[] row : inventoryRepository.findAllStockProjection()) {
            if (row[0] == null) {
                continue;
            }
            int id = ((Number) row[0]).intValue();
            String name = row[1] != null ? String.valueOf(row[1]) : "";
            double stock = row[2] != null ? ((Number) row[2]).doubleValue() : 0;
            double price = row[3] != null ? ((Number) row[3]).doubleValue() : 0;
            String category = row[4] != null ? String.valueOf(row[4]) : "";
            if (catFilter != null && (category.isBlank() || !category.equalsIgnoreCase(catFilter))) {
                continue;
            }
            inv.add(new InvProj(id, name, stock, price, category));
        }

        BigDecimal valorInventario = BigDecimal.ZERO;
        long stockBajo = 0;
        List<Map<String, Object>> stockPorInsumo = new ArrayList<>();
        for (InvProj i : inv) {
            BigDecimal val = BigDecimal.valueOf(i.stock).multiply(BigDecimal.valueOf(i.price)).setScale(2, RoundingMode.HALF_UP);
            valorInventario = valorInventario.add(val);
            if (i.stock < umbralStockBajo) {
                stockBajo++;
            }
            stockPorInsumo.add(row(
                    "id", i.id,
                    "nombre", i.name,
                    "stock", i.stock,
                    "umbral", umbralStockBajo,
                    "categoria", i.category,
                    "valor", val.doubleValue()
            ));
        }
        if (soloStockBajo) {
            stockPorInsumo = stockPorInsumo.stream().filter(m -> (Double) m.get("stock") < umbralStockBajo).toList();
        }

        BigDecimal costoSalida = movementRepository.sumCostoSalida(from, toExclusive, tipoNorm);
        if (costoSalida == null) {
            costoSalida = BigDecimal.ZERO;
        }
        BigDecimal totalAbastecido = movementRepository.sumCostoAbastecimiento(from, toExclusive, tipoNorm);
        if (totalAbastecido == null) {
            totalAbastecido = BigDecimal.ZERO;
        }
        BigDecimal salidaQtyBd = movementRepository.sumCantidadSalida(from, toExclusive, tipoNorm);
        double salidaQty = salidaQtyBd != null ? salidaQtyBd.doubleValue() : 0;

        Set<Integer> movedIds = new HashSet<>(movementRepository.findDistinctInventoryIdsMovedInRange(from, toExclusive, tipoNorm));
        long sinMovimiento = inv.stream().filter(i -> !movedIds.contains(i.id)).count();

        Map<String, BigDecimal> consumoCat = new HashMap<>();
        for (Object[] r : movementRepository.sumConsumoPorCategoria(from, toExclusive, tipoNorm)) {
            String cat = r[0] != null ? String.valueOf(r[0]) : "";
            BigDecimal v = toBigDecimal(r[1]);
            consumoCat.merge(cat.isEmpty() ? "" : cat, v, BigDecimal::add);
        }

        Map<String, BigDecimal> abastPorSemana = new TreeMap<>();
        for (Object[] r : movementRepository.sumAbastecimientoPorSemana(from, toExclusive, tipoNorm)) {
            if (r[0] == null) {
                continue;
            }
            abastPorSemana.merge(String.valueOf(r[0]), toBigDecimal(r[1]), BigDecimal::add);
        }

        List<Map<String, Object>> topConsumo = new ArrayList<>();
        for (Object[] r : movementRepository.topConsumoInsumo(from, toExclusive, tipoNorm)) {
            if (r[0] == null) {
                continue;
            }
            double c = r[1] != null ? ((Number) r[1]).doubleValue() : 0;
            topConsumo.add(row("insumoId", String.valueOf(r[0]), "cantidad", c));
        }

        double stockPromedio = inv.isEmpty() ? 0 : inv.stream().mapToDouble(i -> i.stock).average().orElse(0);
        double rotacion = stockPromedio > 0 ? round2(salidaQty / stockPromedio) : 0;

        Map<String, Double> costoRecetaMap = costoRecetaPorProducto();
        List<Map<String, Object>> margenProductos = new ArrayList<>();
        for (Producto p : productoRepository.findByIsDeletedFalseLight()) {
            if (p.getId() == null) {
                continue;
            }
            double precio = p.getPrice() != null ? p.getPrice() : 0;
            double costoReceta = costoRecetaMap.getOrDefault(p.getId(), 0.0);
            margenProductos.add(row(
                    "productoId", p.getId(),
                    "nombre", p.getName() != null ? p.getName() : "",
                    "precioVenta", precio,
                    "costoReceta", round2(costoReceta),
                    "margenBruto", round2(precio - costoReceta)
            ));
        }
        margenProductos.sort((a, b) -> Double.compare((Double) b.get("margenBruto"), (Double) a.get("margenBruto")));

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("insumosStockBajo", stockBajo);
        kpis.put("valorTotalInventario", valorInventario.setScale(2, RoundingMode.HALF_UP).doubleValue());
        kpis.put("costoMateriaConsumida", costoSalida.setScale(2, RoundingMode.HALF_UP).doubleValue());
        kpis.put("totalAbastecidoPeriodo", totalAbastecido.setScale(2, RoundingMode.HALF_UP).doubleValue());
        kpis.put("insumosSinMovimiento", sinMovimiento);
        kpis.put("rotacionInventario", rotacion);

        log.info(
                "[DASHBOARD] inventario-costos from={} toEx={} insumosProyeccion={} stockFilas={} topConsumo={} "
                        + "semanasAbast={} categoriasConsumo={} margenProductosTop={}",
                from, toExclusive, inv.size(), stockPorInsumo.size(), topConsumo.size(),
                abastPorSemana.size(), consumoCat.size(), Math.min(20, margenProductos.size())
        );

        return Map.of(
                "kpis", kpis,
                "stockPorInsumo", stockPorInsumo,
                "movimientosAbastecimientoPorSemana", abastPorSemana,
                "topConsumoInsumo", topConsumo,
                "consumoPorCategoria", consumoCat,
                "margenBrutoProductos", margenProductos.stream().limit(20).toList()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> productos(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String categoriaProducto,
            Integer estrellasMin,
            Double precioMin,
            Double precioMax
    ) {
        Map<String, Integer> qtyPorProducto = new HashMap<>();
        Map<String, BigDecimal> ingresoPorProducto = new HashMap<>();
        for (Object[] r : orderItemRepository.aggregateVentasPorProductoEntregados(from, toExclusive)) {
            if (r[0] == null) {
                continue;
            }
            String pid = String.valueOf(r[0]);
            int q = r[1] != null ? ((Number) r[1]).intValue() : 0;
            BigDecimal ing = toBigDecimal(r[2]);
            qtyPorProducto.put(pid, q);
            ingresoPorProducto.put(pid, ing);
        }

        Map<String, Producto> prodMap = productoRepository.findByIsDeletedFalseLight().stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(Producto::getId, p -> p, (a, b) -> a));

        Map<String, Double> costoRecetaMap = costoRecetaPorProducto();
        String catProdNorm = (categoriaProducto == null || categoriaProducto.isBlank()) ? null : categoriaProducto.trim();

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Map.Entry<String, Integer> e : qtyPorProducto.entrySet()) {
            Producto p = prodMap.get(e.getKey());
            if (p == null) {
                continue;
            }
            if (catProdNorm != null
                    && (p.getCategory() == null || !p.getCategory().equalsIgnoreCase(catProdNorm))) {
                continue;
            }
            double price = p.getPrice() != null ? p.getPrice() : 0;
            if (precioMin != null && price < precioMin) {
                continue;
            }
            if (precioMax != null && price > precioMax) {
                continue;
            }
            double costoU = costoRecetaMap.getOrDefault(p.getId(), 0.0);
            double margen = price - costoU;
            ranking.add(row(
                    "productoId", p.getId(),
                    "nombre", p.getName() != null ? p.getName() : "",
                    "categoria", p.getCategory() != null ? p.getCategory() : "",
                    "unidadesVendidas", e.getValue(),
                    "ingresos", ingresoPorProducto.getOrDefault(p.getId(), BigDecimal.ZERO).doubleValue(),
                    "margenEstimado", round2(margen * e.getValue())
            ));
        }
        ranking.sort((a, b) -> Integer.compare((Integer) b.get("unidadesVendidas"), (Integer) a.get("unidadesVendidas")));

        String masVendido = ranking.isEmpty() ? "" : String.valueOf(ranking.get(0).get("nombre"));
        String masRentable = ranking.stream()
                .max(Comparator.comparingDouble(m -> (Double) m.get("margenEstimado")))
                .map(m -> String.valueOf(m.get("nombre")))
                .orElse("");

        Map<String, BigDecimal> ingresoPorCat = new HashMap<>();
        for (Map<String, Object> rw : ranking) {
            String cat = String.valueOf(rw.get("categoria"));
            BigDecimal ing = BigDecimal.valueOf((Double) rw.get("ingresos"));
            ingresoPorCat.merge(cat, ing, BigDecimal::add);
        }

        long activos = productoRepository.countByIsDeletedFalse();
        Double avgStars = orderRatingRepository.avgStarsBetween(from, toExclusive);
        double avgStarsVal = avgStars != null ? round2(avgStars) : 0;

        long entregadosCount = orderRepository.countEntregadosCreatedBetween(from, toExclusive);
        long ratedCount = orderRepository.countEntregadosRatedCreatedBetween(from, toExclusive);
        double tasaCalificados = entregadosCount > 0 ? round2(100.0 * ratedCount / entregadosCount) : 0;

        String catLider = ingresoPorCat.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("");

        Map<Integer, Long> distEstrellas = new TreeMap<>();
        for (int s = 1; s <= 5; s++) {
            distEstrellas.put(s, 0L);
        }
        for (Object[] pair : orderRatingRepository.countStarsGroupedBetween(from, toExclusive)) {
            int stars = (Integer) pair[0];
            long cnt = (Long) pair[1];
            if (stars >= 1 && stars <= 5) {
                distEstrellas.merge(stars, cnt, Long::sum);
            }
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("productosActivos", activos);
        kpis.put("productoMasVendido", masVendido);
        kpis.put("productoMasRentable", masRentable);
        kpis.put("calificacionPromedio", avgStarsVal);
        kpis.put("categoriaLiderVentas", catLider);
        kpis.put("tasaPedidosCalificadosPct", tasaCalificados);

        log.info(
                "[DASHBOARD] productos from={} toEx={} productosConVentasEnAgg={} rankingTrasFiltros={} topEnRespuesta={} "
                        + "categoriasIngreso={} distEstrellasClaves={}",
                from, toExclusive, qtyPorProducto.size(), ranking.size(),
                Math.min(15, ranking.size()), ingresoPorCat.size(), distEstrellas.size()
        );

        return Map.of(
                "kpis", kpis,
                "topProductos", ranking.stream().limit(15).toList(),
                "ingresosPorCategoria", ingresoPorCat,
                "distribucionEstrellas", distEstrellas
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> clientes(
            LocalDateTime from,
            LocalDateTime toExclusive,
            LocalDateTime regFrom,
            LocalDateTime regToExclusive,
            Integer estrellasFiltro,
            Boolean soloRecurrentes
    ) {
        long totalClientes = userRepository.countActiveClientes();

        LocalDateTime r0 = regFrom != null ? regFrom : from;
        LocalDateTime r1 = regToExclusive != null ? regToExclusive : toExclusive;
        long nuevos = userRepository.countByRole_NameAndIsDeletedFalseAndCreatedAtBetween("CLIENTE", r0, r1);

        Map<UUID, Long> pedidosPorCliente = new HashMap<>();
        Map<UUID, BigDecimal> gastoPorCliente = new HashMap<>();
        for (Object[] row : orderRepository.aggregateEntregadosPorCliente(from, toExclusive)) {
            UUID uid = toUuid(row[0]);
            if (uid == null) {
                continue;
            }
            long cnt = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            BigDecimal gasto = toBigDecimal(row[2]);
            pedidosPorCliente.put(uid, cnt);
            gastoPorCliente.put(uid, gasto);
        }

        long recurrentes = pedidosPorCliente.values().stream().filter(c -> c > 1).count();
        double tasaRecurrencia = totalClientes > 0 ? round2(100.0 * recurrentes / totalClientes) : 0;

        Double avgStars = orderRatingRepository.avgStarsBetween(from, toExclusive);
        long totalEntregados = pedidosPorCliente.values().stream().mapToLong(Long::longValue).sum();
        double pedidosPromedio = totalClientes > 0 ? round2((double) totalEntregados / totalClientes) : 0;

        List<UUID> orderedIds = gastoPorCliente.entrySet().stream()
                .filter(e -> !Boolean.TRUE.equals(soloRecurrentes) || pedidosPorCliente.getOrDefault(e.getKey(), 0L) > 1)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(Map.Entry::getKey)
                .limit(10)
                .toList();

        Map<UUID, String> names = new HashMap<>();
        if (!orderedIds.isEmpty()) {
            for (Object[] nr : userRepository.findNamesByIds(orderedIds)) {
                UUID id = toUuid(nr[0]);
                if (id != null) {
                    names.put(id, nr[1] != null ? String.valueOf(nr[1]) : "");
                }
            }
        }

        List<Map<String, Object>> topGasto = new ArrayList<>();
        for (UUID uid : orderedIds) {
            long ped = pedidosPorCliente.getOrDefault(uid, 0L);
            BigDecimal gasto = gastoPorCliente.getOrDefault(uid, BigDecimal.ZERO);
            topGasto.add(row(
                    "clienteId", uid.toString(),
                    "nombre", names.getOrDefault(uid, ""),
                    "pedidos", ped,
                    "gastoTotal", gasto.setScale(2, RoundingMode.HALF_UP).doubleValue()
            ));
        }

        Map<Integer, Long> distEstrellasCli = new TreeMap<>();
        for (int s = 1; s <= 5; s++) {
            distEstrellasCli.put(s, 0L);
        }
        for (Object[] pair : orderRatingRepository.countStarsGroupedBetween(from, toExclusive)) {
            int stars = (Integer) pair[0];
            long cnt = (Long) pair[1];
            if (stars >= 1 && stars <= 5) {
                distEstrellasCli.merge(stars, cnt, Long::sum);
            }
        }

        Map<String, Long> frecuenciaPedidosHistograma = histogramaPedidos(pedidosPorCliente);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalClientes", totalClientes);
        kpis.put("clientesNuevosPeriodo", nuevos);
        kpis.put("clientesRecurrentes", recurrentes);
        kpis.put("tasaRecurrenciaPct", tasaRecurrencia);
        kpis.put("calificacionPromedioGeneral", avgStars != null ? round2(avgStars) : 0);
        kpis.put("pedidosPromedioPorCliente", pedidosPromedio);

        log.info(
                "[DASHBOARD] clientes from={} toEx={} clientesActivos={} clientesConPedidosPeriodo={} topGastoFilas={} "
                        + "histogramaBuckets={}",
                from, toExclusive, totalClientes, pedidosPorCliente.size(), topGasto.size(),
                frecuenciaPedidosHistograma.size()
        );

        return Map.of(
                "kpis", kpis,
                "topClientesGasto", topGasto,
                "frecuenciaPedidosHistograma", frecuenciaPedidosHistograma,
                "distribucionEstrellas", distEstrellasCli
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> operacion(
            LocalDateTime from,
            LocalDateTime toExclusive,
            UUID cajeroId,
            UUID repartidorId
    ) {
        List<DashboardOrderTupleSupport.OrderDashRow> list = orderDashSupport.fetch(RestaurantOrderSpecs.createdBetween(from, toExclusive));
        if (cajeroId != null) {
            list = list.stream().filter(o -> cajeroId.equals(o.processedById())).toList();
        }
        if (repartidorId != null) {
            list = list.stream().filter(o -> repartidorId.equals(o.deliveryPersonId())).toList();
        }

        List<Double> minutosEntrega = new ArrayList<>();
        List<Double> minutosDecisionCajaCancel = new ArrayList<>();
        for (DashboardOrderTupleSupport.OrderDashRow o : list) {
            if ("ENTREGADO".equals(o.status()) && o.deliveredAt() != null && o.deliveryAssignedAt() != null) {
                long m = ChronoUnit.MINUTES.between(o.deliveryAssignedAt(), o.deliveredAt());
                if (m >= 0 && m < 24 * 60) {
                    minutosEntrega.add((double) m);
                }
            }
            if ("CANCELADO".equals(o.status()) && o.processedById() != null && o.createdAt() != null && o.processedAt() != null) {
                long mc = ChronoUnit.MINUTES.between(o.createdAt(), o.processedAt());
                if (mc >= 0 && mc < 7 * 24 * 60) {
                    minutosDecisionCajaCancel.add((double) mc);
                }
            }
        }

        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("0-20", 0L);
        buckets.put("20-40", 0L);
        buckets.put("40-60", 0L);
        buckets.put("60+", 0L);
        for (Double m : minutosEntrega) {
            if (m < 20) {
                buckets.merge("0-20", 1L, Long::sum);
            } else if (m < 40) {
                buckets.merge("20-40", 1L, Long::sum);
            } else if (m < 60) {
                buckets.merge("40-60", 1L, Long::sum);
            } else {
                buckets.merge("60+", 1L, Long::sum);
            }
        }

        Map<String, Long> entregasPorRepartidor = list.stream()
                .filter(o -> "ENTREGADO".equals(o.status()) && o.deliveryPersonId() != null)
                .collect(Collectors.groupingBy(o -> o.deliveryPersonName() != null ? o.deliveryPersonName() : "—", Collectors.counting()));

        Map<String, Long> validadosPorCajero = list.stream()
                .filter(o -> o.processedById() != null && POST_VALIDACION.contains(o.status()))
                .collect(Collectors.groupingBy(o -> o.processedByName() != null ? o.processedByName() : "—", Collectors.counting()));

        Map<String, Long> rechazadosPorCajero = list.stream()
                .filter(o -> o.processedById() != null && "CANCELADO".equals(o.status()))
                .collect(Collectors.groupingBy(o -> o.processedByName() != null ? o.processedByName() : "—", Collectors.counting()));

        Set<String> nombresCajero = new HashSet<>();
        nombresCajero.addAll(validadosPorCajero.keySet());
        nombresCajero.addAll(rechazadosPorCajero.keySet());
        List<Map<String, Object>> cajeroFilas = new ArrayList<>();
        for (String nombre : nombresCajero.stream().sorted().toList()) {
            cajeroFilas.add(row(
                    "cajero", nombre,
                    "validados", validadosPorCajero.getOrDefault(nombre, 0L),
                    "rechazados", rechazadosPorCajero.getOrDefault(nombre, 0L)
            ));
        }

        long enCocina = orderRepository.count((root, q, cb) -> cb.equal(root.get("status"), "EN_COCINA"));

        double avgEntrega = minutosEntrega.isEmpty() ? 0 : round2(minutosEntrega.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        double avgDecisionCancel = minutosDecisionCajaCancel.isEmpty() ? 0 : round2(minutosDecisionCajaCancel.stream().mapToDouble(Double::doubleValue).average().orElse(0));

        LocalDate hoy = LocalDate.now();
        long pedidosSuperaronValidacionHoy = list.stream()
                .filter(o -> o.createdAt() != null && hoy.equals(o.createdAt().toLocalDate()))
                .filter(o -> POST_VALIDACION.contains(o.status()))
                .count();

        Map<Integer, Map<String, Long>> porHoraEstado = new TreeMap<>();
        for (DashboardOrderTupleSupport.OrderDashRow o : list) {
            if (o.createdAt() == null || o.status() == null) {
                continue;
            }
            int hr = o.createdAt().getHour();
            porHoraEstado.computeIfAbsent(hr, k -> new HashMap<>())
                    .merge(o.status(), 1L, Long::sum);
        }
        List<Map<String, Object>> embudoPorHora = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Long>> e : porHoraEstado.entrySet()) {
            embudoPorHora.add(row("hora", e.getKey(), "porEstado", e.getValue()));
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("tiempoPromedioValidacionMin", avgDecisionCancel);
        kpis.put("tiempoPromedioCocinaMin", 0);
        kpis.put("tiempoPromedioEntregaMin", avgEntrega);
        kpis.put("pedidosSuperaronValidacionHoy", pedidosSuperaronValidacionHoy);
        kpis.put("pedidosEnCocinaAhora", enCocina);

        log.info(
                "[DASHBOARD] operacion from={} toEx={} pedidosVentana={} muestrasTiempoEntrega={} "
                        + "histogramaEntregaBuckets={} embudoHoras={} repartidoresConEntrega={} filasCajeroVsRechazo={}",
                from, toExclusive, list.size(), minutosEntrega.size(), buckets.size(), embudoPorHora.size(),
                entregasPorRepartidor.size(), cajeroFilas.size()
        );

        return Map.of(
                "kpis", kpis,
                "histogramaTiemposEntrega", buckets,
                "entregasPorRepartidor", entregasPorRepartidor,
                "cajeroValidadosVsRechazados", cajeroFilas,
                "embudoPorHora", embudoPorHora
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> seguridad(LocalDateTime from, LocalDateTime toExclusive, String status, String rol) {
        String st = (status == null || status.isBlank()) ? null : status.trim();
        String rl = (rol == null || rol.isBlank()) ? null : rol.trim();
        List<LoginAudit> audits = loginAuditRepository.findForDashboard(from, toExclusive, st, rl);

        long total = audits.size();
        long success = audits.stream().filter(a -> "SUCCESS".equalsIgnoreCase(a.getStatus())).count();
        long failed = audits.stream().filter(a -> "FAILED".equalsIgnoreCase(a.getStatus())).count();
        long blocked = audits.stream().filter(a -> "BLOCKED".equalsIgnoreCase(a.getStatus())).count();
        double tasaExito = total > 0 ? round2(100.0 * success / total) : 0;

        LocalDateTime now = LocalDateTime.now();
        long ipsBloqueadas = ipLoginAttemptRepository.countByBlockedUntilAfter(now);

        long usuariosUnicos = audits.stream()
                .filter(a -> "SUCCESS".equalsIgnoreCase(a.getStatus()))
                .map(LoginAudit::getUserEmail)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        Map<Integer, long[]> porHora = new HashMap<>();
        for (int h = 0; h < 24; h++) {
            porHora.put(h, new long[]{0, 0, 0});
        }
        for (LoginAudit a : audits) {
            if (a.getAttemptedAt() == null) {
                continue;
            }
            int h = a.getAttemptedAt().getHour();
            long[] arr = porHora.get(h);
            if ("SUCCESS".equalsIgnoreCase(a.getStatus())) {
                arr[0]++;
            } else if ("FAILED".equalsIgnoreCase(a.getStatus())) {
                arr[1]++;
            } else if ("BLOCKED".equalsIgnoreCase(a.getStatus())) {
                arr[2]++;
            }
        }

        List<Map<String, Object>> ipFallos = ipLoginAttemptRepository.findTop10ByFailedAttempts().stream()
                .map(ip -> row(
                        "ip", ip[0] != null ? String.valueOf(ip[0]) : "",
                        "fallos", ip[1] != null ? ((Number) ip[1]).intValue() : 0
                ))
                .toList();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalIntentos", total);
        kpis.put("tasaExitoPct", tasaExito);
        kpis.put("ipsBloqueadasActivas", ipsBloqueadas);
        kpis.put("intentosFallidos", failed);
        kpis.put("usuariosUnicosActivos", usuariosUnicos);
        kpis.put("eventosBloqueo", blocked);

        log.info(
                "[DASHBOARD] seguridad from={} toEx={} auditorias={} intentosPorHoraPts={} ipsMasFallos={}",
                from, toExclusive, total, porHora.size(), ipFallos.size()
        );

        return Map.of(
                "kpis", kpis,
                "intentosPorHora", porHora.entrySet().stream()
                        .map(e -> row(
                                "hora", e.getKey(),
                                "success", e.getValue()[0],
                                "failed", e.getValue()[1],
                                "blocked", e.getValue()[2]
                        ))
                        .toList(),
                "ipsMasFallos", ipFallos
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> interacciones(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String action,
            String condicionClima,
            String segmento,
            String userId
    ) {
        UserInteractionAnalyticsSupport.InteractionAgg agg = userInteractionAnalyticsSupport.aggregate(
                from, toExclusive, action, condicionClima, segmento, userId
        );

        long total = agg.total();
        Map<String, Long> porAccion = agg.porAccion();
        long views = porAccion.getOrDefault("VIEW_DETAIL", 0L);
        long adds = porAccion.getOrDefault("ADD_TO_CART", 0L);
        double tasaAdd = views > 0 ? round2(100.0 * adds / views) : 0;
        long rejects = porAccion.getOrDefault("REJECT_RECOMMENDATION", 0L);
        double tasaReject = total > 0 ? round2(100.0 * rejects / total) : 0;
        double dwellAvg = agg.dwellAvg();

        List<String> topIds = agg.topProductosOrdered().stream().map(Map.Entry::getKey).toList();
        Map<String, String> nombresProducto = nombresProductoPorIds(topIds);

        List<Map<String, Object>> topProductosInteraccion = agg.topProductosOrdered().stream()
                .map(e -> {
                    String pid = e.getKey();
                    return row(
                            "productoId", pid,
                            "nombre", nombresProducto.getOrDefault(pid, pid),
                            "interacciones", e.getValue()
                    );
                })
                .toList();

        String productoMasVistoNombre = "";
        if (!agg.topProductosOrdered().isEmpty()) {
            String pid = agg.topProductosOrdered().get(0).getKey();
            productoMasVistoNombre = nombresProducto.getOrDefault(pid, pid);
        }

        String slot1 = aiModelConfigRepository.findById("GLOBAL_AI_CONFIG")
                .map(cfg -> cfg.getSlots().stream().filter(s -> s.getSlotNumber() == 1).findFirst().map(AiModelConfig.ModelSlot::getStatus).orElse("VACIO"))
                .orElse("VACIO");

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("interaccionesTotales", total);
        kpis.put("tasaAddToCartPct", tasaAdd);
        kpis.put("tasaRechazoRecomendacionPct", tasaReject);
        kpis.put("dwellTimePromedioSeg", round2(dwellAvg));
        kpis.put("estadoSlot1Ia", slot1);

        log.info(
                "[DASHBOARD] interacciones from={} toEx={} total={} accionesDistintas={} topProductos={} "
                        + "climaPorAccionFilas={} segmentoDiaFilas={}",
                from, toExclusive, total, porAccion.size(), topProductosInteraccion.size(),
                agg.climaPorAccion().size(), agg.porSegmento().size()
        );

        return Map.of(
                "kpis", kpis,
                "distribucionAcciones", porAccion,
                "topProductosInteraccion", topProductosInteraccion,
                "productoMasVistoNombre", productoMasVistoNombre,
                "porCondicionClimaYAccion", agg.climaPorAccion(),
                "porSegmentoDia", agg.porSegmento()
        );
    }

    private Map<String, String> nombresProductoPorIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> nombres = new HashMap<>();
        for (Producto p : productoRepository.findAllById(ids)) {
            if (p.getId() == null) {
                continue;
            }
            String name = p.getName();
            nombres.put(p.getId(), name != null && !name.isBlank() ? name : p.getId());
        }
        for (String id : ids) {
            nombres.putIfAbsent(id, id);
        }
        return nombres;
    }

    private Map<String, Double> costoRecetaPorProducto() {
        Map<String, Double> m = new HashMap<>();
        for (Object[] row : recipeRepository.sumCostoRecetaActivaPorProducto()) {
            if (row[0] == null) {
                continue;
            }
            String pid = String.valueOf(row[0]);
            double v = 0;
            if (row[1] != null) {
                if (row[1] instanceof BigDecimal bd) {
                    v = bd.doubleValue();
                } else {
                    v = ((Number) row[1]).doubleValue();
                }
            }
            m.put(pid, v);
        }
        return m;
    }

    private static UUID toUuid(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof UUID u) {
            return u;
        }
        return UUID.fromString(o.toString());
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        return BigDecimal.valueOf(((Number) o).doubleValue());
    }

    private Specification<RestaurantOrder> baseSpec(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String status,
            String momentOfDay,
            String dayOfWeek,
            String weatherCondition
    ) {
        return Specification.where(RestaurantOrderSpecs.createdBetween(from, toExclusive))
                .and(RestaurantOrderSpecs.statusEquals(status))
                .and(RestaurantOrderSpecs.momentOfDayEquals(momentOfDay))
                .and(RestaurantOrderSpecs.dayOfWeekEquals(dayOfWeek))
                .and(RestaurantOrderSpecs.weatherConditionEquals(weatherCondition));
    }

    private static Map<String, Object> row(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Long> histogramaPedidos(Map<UUID, Long> pedidosPorCliente) {
        Map<String, Long> h = new LinkedHashMap<>();
        h.put("1", 0L);
        h.put("2", 0L);
        h.put("3", 0L);
        h.put("4+", 0L);
        for (long c : pedidosPorCliente.values()) {
            if (c <= 1) {
                h.merge("1", 1L, Long::sum);
            } else if (c == 2) {
                h.merge("2", 1L, Long::sum);
            } else if (c == 3) {
                h.merge("3", 1L, Long::sum);
            } else {
                h.merge("4+", 1L, Long::sum);
            }
        }
        return h;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record InvProj(int id, String name, double stock, double price, String category) {
    }

    @Transactional(readOnly = true)
    public Map<String, String> rangoFechasDisponible(String pestana) {
        String key = pestana == null ? "" : pestana.trim().toLowerCase(Locale.ROOT);
        LocalDate hoy = LocalDate.now();
        LocalDate min = resolverFechaMinimaPestana(key);
        if (min.isAfter(hoy)) {
            min = hoy;
        }
        log.info("[DASHBOARD] rango-fechas pestana={} fechaMinima={} fechaMaxima={}", key, min, hoy);
        return Map.of(
                "fechaMinima", min.toString(),
                "fechaMaxima", hoy.toString()
        );
    }

    private LocalDate resolverFechaMinimaPestana(String pestana) {
        LocalDate hoy = LocalDate.now();
        return switch (pestana) {
            case "ventas", "operacion", "productos" ->
                    toLocalDate(orderRepository.findMinCreatedAt()).orElse(hoy);
            case "inventario" ->
                    toLocalDate(movementRepository.findMinCreatedAt()).orElse(hoy);
            case "clientes" ->
                    earliestLocalDate(
                            orderRepository.findMinCreatedAt(),
                            userRepository.findMinClienteCreatedAt()
                    ).orElse(hoy);
            case "seguridad" ->
                    toLocalDate(loginAuditRepository.findMinAttemptedAt()).orElse(hoy);
            case "interacciones" ->
                    toLocalDate(userInteractionAnalyticsSupport.findMinTimestamp()).orElse(hoy);
            default -> hoy;
        };
    }

    private static Optional<LocalDate> toLocalDate(Optional<LocalDateTime> value) {
        return value.map(LocalDateTime::toLocalDate);
    }

    private static Optional<LocalDate> earliestLocalDate(Optional<LocalDateTime> a, Optional<LocalDateTime> b) {
        Optional<LocalDate> da = toLocalDate(a);
        Optional<LocalDate> db = toLocalDate(b);
        if (da.isEmpty()) {
            return db;
        }
        if (db.isEmpty()) {
            return da;
        }
        return Optional.of(da.get().isBefore(db.get()) ? da.get() : db.get());
    }
}
