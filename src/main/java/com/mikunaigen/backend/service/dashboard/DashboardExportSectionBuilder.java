package com.mikunaigen.backend.service.dashboard;

import java.util.*;

public final class DashboardExportSectionBuilder {

    private DashboardExportSectionBuilder() {
    }

    public static List<Map<String, Object>> buildSections(
            Map<String, Object> data,
            boolean includeKpis,
            boolean includeCharts,
            boolean includeTables
    ) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        Object kpisRaw = data.get("kpis");
        if (includeKpis && kpisRaw instanceof Map<?, ?> kpis) {
            sections.add(kpiSection("Indicadores clave (KPIs)", kpis));
        }
        for (Map.Entry<String, Object> e : data.entrySet()) {
            String key = e.getKey();
            if ("kpis".equals(key)) {
                continue;
            }
            Object val = e.getValue();
            String title = humanizeKey(key);
            if ("heatmap".equals(key) && val instanceof Map<?, ?> hm && includeCharts) {
                sections.add(heatmapSection(title, hm));
                continue;
            }
            if (val instanceof List<?> list) {
                if (!includeTables && !includeCharts) {
                    continue;
                }
                if (list.isEmpty()) {
                    continue;
                }
                if (list.get(0) instanceof Map<?, ?>) {
                    if (!includeTables && !includeCharts) {
                        continue;
                    }
                    String kind = includeCharts ? "chart" : "table";
                    Map<String, Object> section = tableFromMaps(title, list, kind);
                    section.put("sourceKey", key);
                    sections.add(section);
                }
                continue;
            }
            if (val instanceof Map<?, ?> map) {
                if (isNumericMap(map)) {
                    if (includeCharts) {
                        Map<String, Object> section = mapToTwoColSection(title, map, "chart");
                        section.put("sourceKey", key);
                        sections.add(section);
                    } else if (includeTables) {
                        Map<String, Object> section = mapToTwoColSection(title, map, "table");
                        section.put("sourceKey", key);
                        sections.add(section);
                    }
                } else if (includeTables) {
                    sections.add(nestedMapSection(title, map));
                }
            } else if (val != null && includeTables && !"productoMasVistoNombre".equals(key)) {
                sections.add(singleValueSection(title, val));
            }
        }
        return sections;
    }

    private static Map<String, Object> kpiSection(String title, Map<?, ?> kpis) {
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<?, ?> e : kpis.entrySet()) {
            rows.add(List.of(humanizeKey(String.valueOf(e.getKey())), formatValue(e.getValue())));
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", "kpi");
        s.put("title", title);
        s.put("columns", List.of("Indicador", "Valor"));
        s.put("rows", rows);
        return s;
    }

    private static Map<String, Object> heatmapSection(String title, Map<?, ?> hm) {
        List<String> columns = new ArrayList<>();
        columns.add("Insumo");
        Object cols = hm.get("columnas");
        if (cols instanceof List<?> colList) {
            for (Object c : colList) {
                columns.add(String.valueOf(c));
            }
        }
        List<List<String>> rows = new ArrayList<>();
        Object filas = hm.get("filas");
        Object valores = hm.get("valores");
        if (filas instanceof List<?> rowNames && valores instanceof List<?> matrix) {
            for (int i = 0; i < rowNames.size(); i++) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rowNames.get(i)));
                if (i < matrix.size() && matrix.get(i) instanceof List<?> vals) {
                    for (Object v : vals) {
                        row.add(formatValue(v));
                    }
                }
                rows.add(row);
            }
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", "chart");
        s.put("chartType", "heatmap");
        s.put("title", title);
        s.put("columns", columns);
        s.put("rows", rows);
        return s;
    }

    private static Map<String, Object> tableFromMaps(String title, List<?> list, String kind) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> maps = (List<Map<String, Object>>) (List<?>) list;
        LinkedHashSet<String> colSet = new LinkedHashSet<>();
        for (Map<String, Object> m : maps) {
            colSet.addAll(m.keySet());
        }
        List<String> columns = new ArrayList<>(colSet);
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            List<String> row = new ArrayList<>();
            for (String c : columns) {
                row.add(formatValue(m.get(c)));
            }
            rows.add(row);
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", kind);
        if ("chart".equals(kind)) {
            s.put("chartType", inferChartType(title, columns));
        }
        s.put("title", title);
        s.put("columns", columns.stream().map(DashboardExportSectionBuilder::humanizeKey).toList());
        s.put("rows", rows);
        return s;
    }

    private static Map<String, Object> mapToTwoColSection(String title, Map<?, ?> map, String kind) {
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            rows.add(List.of(String.valueOf(e.getKey()), formatValue(e.getValue())));
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", kind);
        if ("chart".equals(kind)) {
            s.put("chartType", inferChartType(title, List.of("Etiqueta", "Valor")));
        }
        s.put("title", title);
        s.put("columns", List.of("Etiqueta", "Valor"));
        s.put("rows", rows);
        return s;
    }

    private static String inferChartType(String title, List<String> columns) {
        String t = title != null ? title.toLowerCase(Locale.ROOT) : "";
        String cols = String.join(" ", columns).toLowerCase(Locale.ROOT);
        if (t.contains("mapa de calor") || t.contains("heatmap")) {
            return "heatmap";
        }
        if (t.contains("clima") && (cols.contains("temp") || cols.contains("monto"))) {
            return "scatter";
        }
        if (t.contains("por hora") || cols.contains("hora") || t.contains("embudo")) {
            return "line";
        }
        if (t.contains("distribución") || t.contains("por estado") || t.contains("frecuencia")) {
            return "pie";
        }
        if (t.contains("top ") || cols.contains("unidades") || cols.contains("interacciones")) {
            return "horizontal_bar";
        }
        return "bar";
    }

    private static Map<String, Object> nestedMapSection(String title, Map<?, ?> map) {
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            rows.add(List.of(String.valueOf(e.getKey()), formatValue(e.getValue())));
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", "table");
        s.put("title", title);
        s.put("columns", List.of("Clave", "Valor"));
        s.put("rows", rows);
        return s;
    }

    private static Map<String, Object> singleValueSection(String title, Object val) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", "table");
        s.put("title", title);
        s.put("columns", List.of("Valor"));
        s.put("rows", List.of(List.of(formatValue(val))));
        return s;
    }

    private static boolean isNumericMap(Map<?, ?> map) {
        if (map.isEmpty()) {
            return false;
        }
        for (Object v : map.values()) {
            if (!(v instanceof Number) && v != null) {
                return false;
            }
        }
        return true;
    }

    private static String formatValue(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Double d) {
            if (Math.abs(d - Math.rint(d)) < 0.0001) {
                return String.valueOf((long) Math.rint(d));
            }
            return String.format(Locale.ROOT, "%.2f", d);
        }
        if (v instanceof Float f) {
            return String.format(Locale.ROOT, "%.2f", f);
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (Math.abs(d - Math.rint(d)) < 0.0001) {
                return String.valueOf((long) Math.rint(d));
            }
            return String.format(Locale.ROOT, "%.2f", d);
        }
        return String.valueOf(v);
    }

    private static String humanizeKey(String key) {
        return switch (key) {
            case "ventasPorDia" -> "Ventas por día";
            case "ingresoPorHora" -> "Ingreso por hora";
            case "pedidosPorEstado" -> "Pedidos por estado";
            case "pedidosPorDiaSemana" -> "Pedidos por día de semana";
            case "evolucionTicketSemanal" -> "Evolución ticket semanal";
            case "heatmapHoraDia" -> "Mapa de calor hora × día";
            case "climaTemperaturaVsMonto" -> "Clima (temperatura vs monto)";
            case "stockPorInsumo" -> "Stock por insumo";
            case "topConsumoInsumo" -> "Top consumo por insumo";
            case "movimientosAbastecimientoPorSemana" -> "Abastecimiento por semana";
            case "consumoPorCategoria" -> "Consumo por categoría";
            case "margenBrutoProductos" -> "Margen bruto por producto";
            case "topProductos" -> "Top productos";
            case "ingresosPorCategoria" -> "Ingresos por categoría";
            case "distribucionEstrellas" -> "Distribución de estrellas";
            case "topClientesGasto" -> "Top clientes por gasto";
            case "frecuenciaPedidosHistograma" -> "Frecuencia de pedidos";
            case "histogramaTiemposEntrega" -> "Histograma tiempos de entrega";
            case "entregasPorRepartidor" -> "Entregas por repartidor";
            case "cajeroValidadosVsRechazados" -> "Cajero: validados vs rechazados";
            case "embudoPorHora" -> "Embudo por hora";
            case "intentosPorHora" -> "Intentos de login por hora";
            case "ipsMasFallos" -> "IPs con más fallos";
            case "distribucionAcciones" -> "Distribución de acciones";
            case "topProductosInteraccion" -> "Top productos por interacción";
            case "porCondicionClimaYAccion" -> "Por clima y acción";
            case "porSegmentoDia" -> "Por segmento del día";
            case "items" -> "Predicción por insumo";
            case "alertasCriticas" -> "Alertas críticas";
            case "heatmap" -> "Mapa de calor de predicción";
            case "totalVentas" -> "Total ventas (S/)";
            case "numPedidos" -> "N.º pedidos";
            case "ticketPromedio" -> "Ticket promedio (S/)";
            default -> {
                String s = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
                if (s.isEmpty()) {
                    yield key;
                }
                yield s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
            }
        };
    }
}
