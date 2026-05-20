package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.AlimentoDataset;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.AlimentoDatasetRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
@Service
public class AdminAlimentoDatasetService {

    public static final List<String> CSV_COLUMNAS = List.of(
            "codigo", "grupo", "nombre_alimento", "energia_kcal", "agua_g", "proteinas_g", "grasa_total_g",
            "carbohidratos_totales_g", "carbohidratos_disponibles_g", "fibra_dietaria_g", "cenizas_g", "calcio_mg",
            "fosforo_mg", "zinc_mg", "hierro_mg", "beta_caroteno_ug", "vitamina_a_ug", "tiamina_mg", "riboflavina_mg",
            "niacina_mg", "vitamina_c_mg", "acido_folico_ug", "sodio_mg", "potasio_mg", "costo_kg_soles",
            "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
            "noviembre", "diciembre"
    );

    private static final Set<String> CATEGORIAS = Set.of(
            "Cereales", "Verduras", "Frutas", "Grasas", "Pescados", "Carnes",
            "Leche", "Huevos", "Azucarados", "Leguminosas", "Tubérculos"
    );

    private static final List<String> CAMPOS_NUMERICOS = List.of(
            "energia_kcal", "agua_g", "proteinas_g", "grasa_total_g", "carbohidratos_totales_g",
            "carbohidratos_disponibles_g", "fibra_g", "cenizas_g", "calcio_mg", "fosforo_mg", "zinc_mg", "hierro_mg",
            "beta_caroteno_ug", "vitamina_a_ug", "tiamina_mg", "riboflavina_mg", "niacina_mg", "vitamina_c_mg",
            "acido_folico_ug", "sodio_mg", "potasio_mg", "costo_kg_soles"
    );

    private static final long MAX_CSV_BYTES = 5L * 1024 * 1024;

    private final AlimentoDatasetRepository alimentoRepo;
    private final UserRepository userRepo;

    public AdminAlimentoDatasetService(AlimentoDatasetRepository alimentoRepo, UserRepository userRepo) {
        this.alimentoRepo = alimentoRepo;
        this.userRepo = userRepo;
    }

    public Map<String, Object> estado() {
        long total = alimentoRepo.count();
        return Map.of(
                "vacio", total == 0,
                "total", total,
                "columnasCsv", CSV_COLUMNAS
        );
    }

    public Map<String, Object> metadatosFiltros() {
        List<String> grupos = alimentoRepo.findAll().stream()
                .map(AlimentoDataset::getCategoria)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return Map.of(
                "grupos", grupos.isEmpty() ? CATEGORIAS.stream().sorted().toList() : grupos,
                "categoriasPermitidas", CATEGORIAS.stream().sorted().toList(),
                "camposNutricionales", CAMPOS_NUMERICOS,
                "rangosFiltro", List.of(
                        Map.of("id", "todos", "etiqueta", "Todos"),
                        Map.of("id", "cero", "etiqueta", "= 0"),
                        Map.of("id", "bajo", "etiqueta", "0 – 10"),
                        Map.of("id", "medio", "etiqueta", "10 – 50"),
                        Map.of("id", "alto", "etiqueta", "> 50")
                )
        );
    }

    public List<Map<String, Object>> listar(
            String nombre,
            String grupo,
            String campoNutricional,
            String rangoFiltro,
            BigDecimal minPersonalizado,
            BigDecimal maxPersonalizado
    ) {
        return alimentoRepo.findAll().stream()
                .filter(a -> coincideNombre(a, nombre))
                .filter(a -> coincideGrupo(a, grupo))
                .filter(a -> coincideNutricional(a, campoNutricional, rangoFiltro, minPersonalizado, maxPersonalizado))
                .sorted(Comparator.comparing(AlimentoDataset::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(this::aMapaCompleto)
                .toList();
    }

    @Transactional
    public Map<String, Object> crear(Map<String, Object> body, UUID adminId) {
        AlimentoDataset a = new AlimentoDataset();
        aplicarBody(body, a);
        validarEntidad(a);
        a.setModificadoPor(adminId);
        a.setFechaModificacion(LocalDateTime.now());
        alimentoRepo.save(a);
        return Map.of("message", "Alimento registrado correctamente.", "alimento", aMapaCompleto(a));
    }

    @Transactional
    public Map<String, Object> actualizar(Integer id, Map<String, Object> body, UUID adminId) {
        AlimentoDataset a = alimentoRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimento no encontrado."));
        aplicarBody(body, a);
        validarEntidad(a);
        a.setModificadoPor(adminId);
        a.setFechaModificacion(LocalDateTime.now());
        alimentoRepo.save(a);
        return Map.of("message", "Alimento actualizado correctamente.", "alimento", aMapaCompleto(a));
    }

    @Transactional
    public Map<String, Object> guardarLote(List<Map<String, Object>> items, UUID adminId) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay registros para guardar.");
        }
        int creados = 0;
        int actualizados = 0;
        LocalDateTime ahora = LocalDateTime.now();
        for (Map<String, Object> item : items) {
            Object idObj = item.get("id");
            AlimentoDataset a;
            if (idObj != null && !String.valueOf(idObj).isBlank()) {
                Integer id = Integer.parseInt(String.valueOf(idObj));
                a = alimentoRepo.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimento no encontrado: " + id));
                actualizados++;
            } else {
                a = new AlimentoDataset();
                creados++;
            }
            aplicarBody(item, a);
            validarEntidad(a);
            a.setModificadoPor(adminId);
            a.setFechaModificacion(ahora);
            alimentoRepo.save(a);
        }
        return Map.of(
                "message", "Cambios guardados correctamente.",
                "creados", creados,
                "actualizados", actualizados
        );
    }

    @Transactional
    public Map<String, Object> importarCsv(MultipartFile archivo, UUID adminId) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar un archivo CSV.");
        }
        if (archivo.getSize() > MAX_CSV_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no puede superar 5 MB.");
        }
        String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (!nombre.endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe ser formato CSV.");
        }

        List<AlimentoDataset> importados = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo CSV está vacío.");
            }
            validarCabecera(headerLine);
            String linea;
            int fila = 1;
            while ((linea = reader.readLine()) != null) {
                fila++;
                if (linea.isBlank()) {
                    continue;
                }
                String[] cols = parsearLineaCsv(linea);
                if (cols.length != CSV_COLUMNAS.size()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Fila " + fila + ": se esperaban " + CSV_COLUMNAS.size() + " columnas.");
                }
                importados.add(parsearFilaCsv(cols));
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el CSV: " + e.getMessage());
        }

        if (importados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CSV no contiene filas de datos.");
        }

        for (AlimentoDataset a : importados) {
            validarEntidad(a);
            a.setModificadoPor(adminId);
            a.setFechaModificacion(ahora);
        }

        if (alimentoRepo.count() == 0) {
            alimentoRepo.saveAll(importados);
        } else {
            for (AlimentoDataset a : importados) {
                Optional<AlimentoDataset> existente = a.getCodigoMinsa() != null && !a.getCodigoMinsa().isBlank()
                        ? alimentoRepo.findByCodigoMinsa(a.getCodigoMinsa())
                        : alimentoRepo.findByNombreIgnoreCase(a.getNombre());
                if (existente.isPresent()) {
                    AlimentoDataset dest = existente.get();
                    copiarDatos(a, dest);
                    dest.setModificadoPor(adminId);
                    dest.setFechaModificacion(ahora);
                    alimentoRepo.save(dest);
                } else {
                    alimentoRepo.save(a);
                }
            }
        }

        return Map.of(
                "message", "Dataset importado correctamente.",
                "registrosProcesados", importados.size(),
                "total", alimentoRepo.count()
        );
    }

    private void copiarDatos(AlimentoDataset origen, AlimentoDataset dest) {
        dest.setCodigoMinsa(origen.getCodigoMinsa());
        dest.setNombre(origen.getNombre());
        dest.setCategoria(origen.getCategoria());
        dest.setEnergiaKcal(origen.getEnergiaKcal());
        dest.setAguaG(origen.getAguaG());
        dest.setProteinasG(origen.getProteinasG());
        dest.setGrasaTotalG(origen.getGrasaTotalG());
        dest.setCarbohidratosTotalesG(origen.getCarbohidratosTotalesG());
        dest.setCarbohidratosDisponiblesG(origen.getCarbohidratosDisponiblesG());
        dest.setFibraG(origen.getFibraG());
        dest.setCenizasG(origen.getCenizasG());
        dest.setCalcioMg(origen.getCalcioMg());
        dest.setFosforoMg(origen.getFosforoMg());
        dest.setZincMg(origen.getZincMg());
        dest.setHierroMg(origen.getHierroMg());
        dest.setBetaCarotenoUg(origen.getBetaCarotenoUg());
        dest.setVitaminaAUg(origen.getVitaminaAUg());
        dest.setTiaminaMg(origen.getTiaminaMg());
        dest.setRiboflavinaMg(origen.getRiboflavinaMg());
        dest.setNiacinaMg(origen.getNiacinaMg());
        dest.setVitaminaCMg(origen.getVitaminaCMg());
        dest.setAcidoFolicoUg(origen.getAcidoFolicoUg());
        dest.setSodioMg(origen.getSodioMg());
        dest.setPotasioMg(origen.getPotasioMg());
        dest.setCostoKgSoles(origen.getCostoKgSoles());
        dest.setMesesDisponibilidad(origen.getMesesDisponibilidad());
    }

    private AlimentoDataset parsearFilaCsv(String[] cols) {
        AlimentoDataset a = new AlimentoDataset();
        a.setCodigoMinsa(vacioANull(cols[0]));
        a.setCategoria(normalizarCategoria(cols[1]));
        a.setNombre(cols[2].trim());
        a.setEnergiaKcal(parseNumObligatorio(cols[3], "energia_kcal"));
        a.setAguaG(parseNumOpcional(cols[4]));
        a.setProteinasG(parseNumOpcional(cols[5]));
        a.setGrasaTotalG(parseNumOpcional(cols[6]));
        a.setCarbohidratosTotalesG(parseNumOpcional(cols[7]));
        a.setCarbohidratosDisponiblesG(parseNumOpcional(cols[8]));
        a.setFibraG(parseNumOpcional(cols[9]));
        a.setCenizasG(parseNumOpcional(cols[10]));
        a.setCalcioMg(parseNumOpcional(cols[11]));
        a.setFosforoMg(parseNumOpcional(cols[12]));
        a.setZincMg(parseNumOpcional(cols[13]));
        a.setHierroMg(parseNumOpcional(cols[14]));
        a.setBetaCarotenoUg(parseNumOpcional(cols[15]));
        a.setVitaminaAUg(parseNumOpcional(cols[16]));
        a.setTiaminaMg(parseNumOpcional(cols[17]));
        a.setRiboflavinaMg(parseNumOpcional(cols[18]));
        a.setNiacinaMg(parseNumOpcional(cols[19]));
        a.setVitaminaCMg(parseNumOpcional(cols[20]));
        a.setAcidoFolicoUg(parseNumOpcional(cols[21]));
        a.setSodioMg(parseNumOpcional(cols[22]));
        a.setPotasioMg(parseNumOpcional(cols[23]));
        a.setCostoKgSoles(parseNumObligatorio(cols[24], "costo_kg_soles"));
        List<Integer> meses = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            if (mesActivo(cols[25 + i])) {
                meses.add(i + 1);
            }
        }
        a.setMesesDisponibilidad(meses.toArray(new Integer[0]));
        return a;
    }

    private boolean mesActivo(String valor) {
        if (valor == null) {
            return false;
        }
        String v = valor.trim().toLowerCase(Locale.ROOT);
        return v.equals("1") || v.equals("x") || v.equals("si") || v.equals("sí") || v.equals("true");
    }

    private void validarCabecera(String headerLine) {
        String limpia = headerLine.replace("\uFEFF", "").trim();
        String[] headers = parsearLineaCsv(limpia);
        List<String> normalizados = Arrays.stream(headers)
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .toList();
        List<String> esperados = CSV_COLUMNAS.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        if (!normalizados.equals(esperados)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las columnas del CSV no coinciden. Deben ser exactamente: " + String.join(",", CSV_COLUMNAS));
        }
    }

    private String[] parsearLineaCsv(String linea) {
        List<String> partes = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                entreComillas = !entreComillas;
            } else if (c == ',' && !entreComillas) {
                partes.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        partes.add(actual.toString().trim());
        return partes.toArray(new String[0]);
    }

    private void aplicarBody(Map<String, Object> body, AlimentoDataset a) {
        if (body.get("codigo_minsa") != null) {
            a.setCodigoMinsa(vacioANull(String.valueOf(body.get("codigo_minsa"))));
        }
        if (body.get("codigo") != null) {
            a.setCodigoMinsa(vacioANull(String.valueOf(body.get("codigo"))));
        }
        if (body.get("nombre") != null || body.get("nombre_alimento") != null) {
            Object n = body.get("nombre") != null ? body.get("nombre") : body.get("nombre_alimento");
            a.setNombre(String.valueOf(n).trim());
        }
        if (body.get("categoria") != null || body.get("grupo") != null) {
            Object g = body.get("categoria") != null ? body.get("categoria") : body.get("grupo");
            a.setCategoria(normalizarCategoria(String.valueOf(g)));
        }
        setNumBody(body, "energia_kcal", a::setEnergiaKcal);
        setNumBody(body, "agua_g", a::setAguaG);
        setNumBody(body, "proteinas_g", a::setProteinasG);
        setNumBody(body, "grasa_total_g", a::setGrasaTotalG);
        setNumBody(body, "carbohidratos_totales_g", a::setCarbohidratosTotalesG);
        setNumBody(body, "carbohidratos_disponibles_g", a::setCarbohidratosDisponiblesG);
        setNumBody(body, "fibra_g", a::setFibraG);
        setNumBody(body, "fibra_dietaria_g", a::setFibraG);
        setNumBody(body, "cenizas_g", a::setCenizasG);
        setNumBody(body, "calcio_mg", a::setCalcioMg);
        setNumBody(body, "fosforo_mg", a::setFosforoMg);
        setNumBody(body, "zinc_mg", a::setZincMg);
        setNumBody(body, "hierro_mg", a::setHierroMg);
        setNumBody(body, "beta_caroteno_ug", a::setBetaCarotenoUg);
        setNumBody(body, "vitamina_a_ug", a::setVitaminaAUg);
        setNumBody(body, "tiamina_mg", a::setTiaminaMg);
        setNumBody(body, "riboflavina_mg", a::setRiboflavinaMg);
        setNumBody(body, "niacina_mg", a::setNiacinaMg);
        setNumBody(body, "vitamina_c_mg", a::setVitaminaCMg);
        setNumBody(body, "acido_folico_ug", a::setAcidoFolicoUg);
        setNumBody(body, "sodio_mg", a::setSodioMg);
        setNumBody(body, "potasio_mg", a::setPotasioMg);
        setNumBody(body, "costo_kg_soles", a::setCostoKgSoles);
        if (body.get("meses_disponibilidad") != null) {
            a.setMesesDisponibilidad(parsearMeses(body.get("meses_disponibilidad")));
        } else {
            Integer[] desdeMeses = parsearMesesDesdeMapa(body);
            if (desdeMeses != null) {
                a.setMesesDisponibilidad(desdeMeses);
            }
        }
    }

    private Integer[] parsearMesesDesdeMapa(Map<String, Object> body) {
        String[] nombres = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
                "septiembre", "octubre", "noviembre", "diciembre"};
        List<Integer> meses = new ArrayList<>();
        for (int i = 0; i < nombres.length; i++) {
            Object v = body.get(nombres[i]);
            if (v != null && mesActivo(String.valueOf(v))) {
                meses.add(i + 1);
            }
        }
        return meses.isEmpty() && body.keySet().stream().noneMatch(k -> Arrays.asList(nombres).contains(k))
                ? null : meses.toArray(new Integer[0]);
    }

    private Integer[] parsearMeses(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(o -> Integer.parseInt(String.valueOf(o)))
                    .toArray(Integer[]::new);
        }
        if (raw instanceof Integer[] arr) {
            return arr;
        }
        return new Integer[0];
    }

    private void setNumBody(Map<String, Object> body, String key, java.util.function.Consumer<BigDecimal> setter) {
        if (!body.containsKey(key) || body.get(key) == null || "".equals(String.valueOf(body.get(key)).trim())) {
            return;
        }
        setter.accept(parseNumObligatorio(String.valueOf(body.get(key)), key));
    }

    private void validarEntidad(AlimentoDataset a) {
        if (a.getNombre() == null || a.getNombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del alimento es obligatorio.");
        }
        if (a.getCategoria() == null || !CATEGORIAS.contains(a.getCategoria())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Grupo inválido. Valores permitidos: " + String.join(", ", CATEGORIAS));
        }
        validarNoNegativo(a.getEnergiaKcal(), "energia_kcal");
        validarNoNegativo(a.getCostoKgSoles(), "costo_kg_soles");
        validarNoNegativo(a.getAguaG(), "agua_g");
        validarNoNegativo(a.getProteinasG(), "proteinas_g");
        validarNoNegativo(a.getGrasaTotalG(), "grasa_total_g");
        validarNoNegativo(a.getCarbohidratosTotalesG(), "carbohidratos_totales_g");
        validarNoNegativo(a.getCarbohidratosDisponiblesG(), "carbohidratos_disponibles_g");
        validarNoNegativo(a.getFibraG(), "fibra_g");
        validarNoNegativo(a.getCenizasG(), "cenizas_g");
        validarNoNegativo(a.getCalcioMg(), "calcio_mg");
        validarNoNegativo(a.getFosforoMg(), "fosforo_mg");
        validarNoNegativo(a.getZincMg(), "zinc_mg");
        validarNoNegativo(a.getHierroMg(), "hierro_mg");
        validarNoNegativo(a.getBetaCarotenoUg(), "beta_caroteno_ug");
        validarNoNegativo(a.getVitaminaAUg(), "vitamina_a_ug");
        validarNoNegativo(a.getTiaminaMg(), "tiamina_mg");
        validarNoNegativo(a.getRiboflavinaMg(), "riboflavina_mg");
        validarNoNegativo(a.getNiacinaMg(), "niacina_mg");
        validarNoNegativo(a.getVitaminaCMg(), "vitamina_c_mg");
        validarNoNegativo(a.getAcidoFolicoUg(), "acido_folico_ug");
        validarNoNegativo(a.getSodioMg(), "sodio_mg");
        validarNoNegativo(a.getPotasioMg(), "potasio_mg");
        if (a.getMesesDisponibilidad() == null) {
            a.setMesesDisponibilidad(new Integer[0]);
        }
    }

    private void validarNoNegativo(BigDecimal valor, String campo) {
        if (valor != null && valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El campo " + campo + " debe ser numérico y no negativo.");
        }
    }

    private String normalizarCategoria(String grupo) {
        if (grupo == null || grupo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El grupo es obligatorio.");
        }
        String g = grupo.trim();
        for (String cat : CATEGORIAS) {
            if (cat.equalsIgnoreCase(g)) {
                return cat;
            }
        }
        String sinTilde = g.replace("é", "e").replace("É", "E");
        for (String cat : CATEGORIAS) {
            if (cat.replace("é", "e").equalsIgnoreCase(sinTilde)) {
                return cat;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupo no reconocido: " + grupo);
    }

    private BigDecimal parseNumObligatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor obligatorio en " + campo);
        }
        try {
            BigDecimal n = new BigDecimal(valor.trim().replace(",", "."));
            if (n.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El campo " + campo + " no puede ser negativo.");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valor no numérico en " + campo + ": " + valor);
        }
    }

    private BigDecimal parseNumOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return parseNumObligatorio(valor, "campo");
    }

    private String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private boolean coincideNombre(AlimentoDataset a, String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return true;
        }
        String q = nombre.trim().toLowerCase(Locale.ROOT);
        return a.getNombre().toLowerCase(Locale.ROOT).contains(q)
                || (a.getCodigoMinsa() != null && a.getCodigoMinsa().toLowerCase(Locale.ROOT).contains(q));
    }

    private boolean coincideGrupo(AlimentoDataset a, String grupo) {
        if (grupo == null || grupo.isBlank()) {
            return true;
        }
        return a.getCategoria().equalsIgnoreCase(grupo.trim());
    }

    private boolean coincideNutricional(
            AlimentoDataset a,
            String campo,
            String rango,
            BigDecimal minCustom,
            BigDecimal maxCustom
    ) {
        if (campo == null || campo.isBlank() || "todos".equalsIgnoreCase(rango)) {
            return true;
        }
        if (!CAMPOS_NUMERICOS.contains(campo)) {
            return true;
        }
        BigDecimal valor = obtenerValorNutricional(a, campo);
        if (valor == null) {
            return "cero".equals(rango);
        }
        if (minCustom != null || maxCustom != null) {
            if (minCustom != null && valor.compareTo(minCustom) < 0) {
                return false;
            }
            return maxCustom == null || valor.compareTo(maxCustom) <= 0;
        }
        if (rango == null || rango.isBlank() || "todos".equals(rango)) {
            return true;
        }
        return switch (rango) {
            case "cero" -> valor.compareTo(BigDecimal.ZERO) == 0;
            case "bajo" -> valor.compareTo(BigDecimal.ZERO) > 0 && valor.compareTo(new BigDecimal("10")) <= 0;
            case "medio" -> valor.compareTo(new BigDecimal("10")) > 0 && valor.compareTo(new BigDecimal("50")) <= 0;
            case "alto" -> valor.compareTo(new BigDecimal("50")) > 0;
            default -> true;
        };
    }

    private BigDecimal obtenerValorNutricional(AlimentoDataset a, String campo) {
        return switch (campo) {
            case "energia_kcal" -> a.getEnergiaKcal();
            case "agua_g" -> a.getAguaG();
            case "proteinas_g" -> a.getProteinasG();
            case "grasa_total_g" -> a.getGrasaTotalG();
            case "carbohidratos_totales_g" -> a.getCarbohidratosTotalesG();
            case "carbohidratos_disponibles_g" -> a.getCarbohidratosDisponiblesG();
            case "fibra_g" -> a.getFibraG();
            case "cenizas_g" -> a.getCenizasG();
            case "calcio_mg" -> a.getCalcioMg();
            case "fosforo_mg" -> a.getFosforoMg();
            case "zinc_mg" -> a.getZincMg();
            case "hierro_mg" -> a.getHierroMg();
            case "beta_caroteno_ug" -> a.getBetaCarotenoUg();
            case "vitamina_a_ug" -> a.getVitaminaAUg();
            case "tiamina_mg" -> a.getTiaminaMg();
            case "riboflavina_mg" -> a.getRiboflavinaMg();
            case "niacina_mg" -> a.getNiacinaMg();
            case "vitamina_c_mg" -> a.getVitaminaCMg();
            case "acido_folico_ug" -> a.getAcidoFolicoUg();
            case "sodio_mg" -> a.getSodioMg();
            case "potasio_mg" -> a.getPotasioMg();
            case "costo_kg_soles" -> a.getCostoKgSoles();
            default -> null;
        };
    }

    private Map<String, Object> aMapaCompleto(AlimentoDataset a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("codigo_minsa", a.getCodigoMinsa());
        m.put("nombre", a.getNombre());
        m.put("categoria", a.getCategoria());
        m.put("grupo", a.getCategoria());
        m.put("energia_kcal", a.getEnergiaKcal());
        m.put("agua_g", a.getAguaG());
        m.put("proteinas_g", a.getProteinasG());
        m.put("grasa_total_g", a.getGrasaTotalG());
        m.put("carbohidratos_totales_g", a.getCarbohidratosTotalesG());
        m.put("carbohidratos_disponibles_g", a.getCarbohidratosDisponiblesG());
        m.put("fibra_g", a.getFibraG());
        m.put("fibra_dietaria_g", a.getFibraG());
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
        m.put("meses_disponibilidad", a.getMesesDisponibilidad() != null ? a.getMesesDisponibilidad() : new Integer[0]);
        for (int i = 1; i <= 12; i++) {
            m.put(nombreMes(i), mesActivo(a, i));
        }
        m.put("fecha_modificacion", a.getFechaModificacion());
        m.put("modificado_por", a.getModificadoPor());
        if (a.getModificadoPor() != null) {
            userRepo.findById(a.getModificadoPor()).ifPresent(u -> m.put("modificado_por_nombre", nombreUsuario(u)));
        }
        return m;
    }

    private String nombreUsuario(User u) {
        if (u.getFullName() != null && !u.getFullName().isBlank()) {
            return u.getFullName();
        }
        return u.getEmail();
    }

    private String nombreMes(int numero) {
        return switch (numero) {
            case 1 -> "enero";
            case 2 -> "febrero";
            case 3 -> "marzo";
            case 4 -> "abril";
            case 5 -> "mayo";
            case 6 -> "junio";
            case 7 -> "julio";
            case 8 -> "agosto";
            case 9 -> "septiembre";
            case 10 -> "octubre";
            case 11 -> "noviembre";
            case 12 -> "diciembre";
            default -> "mes";
        };
    }

    private boolean mesActivo(AlimentoDataset a, int mes) {
        if (a.getMesesDisponibilidad() == null) {
            return false;
        }
        for (Integer m : a.getMesesDisponibilidad()) {
            if (m != null && m == mes) {
                return true;
            }
        }
        return false;
    }
}
