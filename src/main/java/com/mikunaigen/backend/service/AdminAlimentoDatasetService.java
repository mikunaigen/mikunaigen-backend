package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.AlimentoDataset;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.AlimentoDatasetRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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
            "Leche", "Bebidas", "Huevos", "Azucarados", "Preparados", "Leguminosas", "Tubérculos"
    );

    private static final Map<String, String> GRUPO_LETRA_MINSA = Map.ofEntries(
            Map.entry("A", "Cereales"),
            Map.entry("B", "Verduras"),
            Map.entry("C", "Frutas"),
            Map.entry("D", "Grasas"),
            Map.entry("E", "Pescados"),
            Map.entry("F", "Carnes"),
            Map.entry("G", "Leche"),
            Map.entry("H", "Bebidas"),
            Map.entry("J", "Huevos"),
            Map.entry("K", "Azucarados"),
            Map.entry("L", "Verduras"),
            Map.entry("Q", "Leche"),
            Map.entry("S", "Preparados"),
            Map.entry("T", "Leguminosas"),
            Map.entry("U", "Tubérculos")
    );

    private static final List<String> CAMPOS_NUMERICOS = List.of(
            "energia_kcal", "agua_g", "proteinas_g", "grasa_total_g", "carbohidratos_totales_g",
            "carbohidratos_disponibles_g", "fibra_g", "cenizas_g", "calcio_mg", "fosforo_mg", "zinc_mg", "hierro_mg",
            "beta_caroteno_ug", "vitamina_a_ug", "tiamina_mg", "riboflavina_mg", "niacina_mg", "vitamina_c_mg",
            "acido_folico_ug", "sodio_mg", "potasio_mg", "costo_kg_soles"
    );

    private static final long MAX_CSV_BYTES = 5L * 1024 * 1024;
    private static final int JDBC_BATCH_SIZE = 100;
    private static final int PERSISTIR_CSV_CHUNK = 100;

    private static final String INSERT_ALIMENTO_SQL = """
            INSERT INTO alimentos_dataset (
                codigo_minsa, nombre, categoria, energia_kcal, agua_g, proteinas_g, grasa_total_g,
                carbohidratos_totales_g, carbohidratos_disponibles_g, fibra_g, cenizas_g, calcio_mg,
                fosforo_mg, zinc_mg, hierro_mg, beta_caroteno_ug, vitamina_a_ug, tiamina_mg,
                riboflavina_mg, niacina_mg, vitamina_c_mg, acido_folico_ug, sodio_mg, potasio_mg,
                costo_kg_soles, meses_disponibilidad, modificado_por, fecha_modificacion
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private final AlimentoDatasetRepository alimentoRepo;
    private final UserRepository userRepo;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public AdminAlimentoDatasetService(
            AlimentoDatasetRepository alimentoRepo,
            UserRepository userRepo,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this.alimentoRepo = alimentoRepo;
        this.userRepo = userRepo;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
        return listarFiltrados(nombre, grupo, campoNutricional, rangoFiltro, minPersonalizado, maxPersonalizado);
    }

    public Map<String, Object> listarPaginado(
            String nombre,
            String grupo,
            String campoNutricional,
            String rangoFiltro,
            BigDecimal minPersonalizado,
            BigDecimal maxPersonalizado,
            int page,
            int size
    ) {
        int tamPagina = normalizarTamanoPagina(size);
        List<Map<String, Object>> todos = listarFiltrados(
                nombre, grupo, campoNutricional, rangoFiltro, minPersonalizado, maxPersonalizado
        );
        int total = todos.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / tamPagina);
        int pagina = Math.max(0, page);
        if (totalPages > 0 && pagina >= totalPages) {
            pagina = totalPages - 1;
        }
        int desde = pagina * tamPagina;
        int hasta = Math.min(desde + tamPagina, total);
        List<Map<String, Object>> paginaDatos = desde >= hasta ? List.of() : todos.subList(desde, hasta);
        return Map.of(
                "alimentos", paginaDatos,
                "total", total,
                "page", pagina,
                "size", tamPagina,
                "totalPages", totalPages
        );
    }

    private int normalizarTamanoPagina(int size) {
        if (size == 50 || size == 100) {
            return size;
        }
        return 20;
    }

    private List<Map<String, Object>> listarFiltrados(
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
        try (BufferedReader reader = new BufferedReader(new StringReader(leerTextoCsv(archivo)))) {
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
                String[] cols = ajustarColumnas(parsearLineaCsv(linea), fila);
                try {
                    importados.add(parsearFilaCsv(cols));
                } catch (ResponseStatusException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Fila " + fila + ": " + e.getReason());
                }
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el CSV: " + e.getMessage());
        }

        if (importados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CSV no contiene filas de datos.");
        }

        int registrosProcesados = persistirImportadosEnChunks(importados, adminId, ahora);

        return Map.of(
                "message", "Dataset importado correctamente.",
                "registrosProcesados", registrosProcesados,
                "total", alimentoRepo.count()
        );
    }

    public Map<String, Object> importarLineasCsv(List<String> lineasDatos, int filaInicio, UUID adminId) {
        if (lineasDatos == null || lineasDatos.isEmpty()) {
            return Map.of("procesadasEnLote", 0, "total", alimentoRepo.count());
        }
        Map<String, Object> res = importarLineasConProgreso(lineasDatos, adminId, null, filaInicio);
        return Map.of(
                "procesadasEnLote", res.get("registrosProcesados"),
                "total", res.get("total")
        );
    }

    public Map<String, Object> importarLineasConProgreso(
            List<String> lineasDatos,
            UUID adminId,
            CsvImportProgressListener listener
    ) {
        return importarLineasConProgreso(lineasDatos, adminId, listener, 2);
    }

    public Map<String, Object> importarLineasConProgreso(
            List<String> lineasDatos,
            UUID adminId,
            CsvImportProgressListener listener,
            int filaInicio
    ) {
        if (lineasDatos == null || lineasDatos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CSV no contiene filas de datos.");
        }
        LocalDateTime ahora = LocalDateTime.now();
        List<AlimentoDataset> lote = new ArrayList<>();
        int registrosProcesados = 0;
        for (int i = 0; i < lineasDatos.size(); i++) {
            String linea = lineasDatos.get(i);
            int fila = filaInicio + i;
            if (linea.isBlank()) {
                continue;
            }
            String[] cols = ajustarColumnas(parsearLineaCsv(linea), fila);
            try {
                lote.add(parsearFilaCsv(cols));
            } catch (ResponseStatusException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Fila " + fila + ": " + e.getReason());
            }
            if (lote.size() >= PERSISTIR_CSV_CHUNK) {
                registrosProcesados += guardarLoteImportacion(lote, adminId, ahora);
                lote.clear();
            }
        }
        if (!lote.isEmpty()) {
            registrosProcesados += guardarLoteImportacion(lote, adminId, ahora);
        }
        if (registrosProcesados == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CSV no contiene filas de datos.");
        }
        return Map.of(
                "message", "Dataset importado correctamente.",
                "registrosProcesados", registrosProcesados,
                "total", alimentoRepo.count()
        );
    }

    private int persistirImportadosEnChunks(List<AlimentoDataset> importados, UUID adminId, LocalDateTime ahora) {
        int total = 0;
        for (int i = 0; i < importados.size(); i += PERSISTIR_CSV_CHUNK) {
            int fin = Math.min(i + PERSISTIR_CSV_CHUNK, importados.size());
            List<AlimentoDataset> lote = new ArrayList<>(importados.subList(i, fin));
            total += guardarLoteImportacion(lote, adminId, ahora);
        }
        return total;
    }

    private int guardarLoteImportacion(List<AlimentoDataset> lote, UUID adminId, LocalDateTime ahora) {
        if (lote.isEmpty()) {
            return 0;
        }
        for (AlimentoDataset a : lote) {
            validarEntidad(a);
            a.setModificadoPor(adminId);
            a.setFechaModificacion(ahora);
        }
        transactionTemplate.executeWithoutResult(status -> persistirImportados(lote));
        return lote.size();
    }

    private void persistirImportados(List<AlimentoDataset> importados) {
        if (importados.isEmpty()) {
            return;
        }

        Map<String, AlimentoDataset> csvUnicos = new LinkedHashMap<>();
        Map<String, String> codigoANombre = new HashMap<>();
        
        for (AlimentoDataset a : importados) {
            String nombreNorm = normalizarNombreAlimento(a.getNombre());
            String codigoNorm = a.getCodigoMinsa() != null ? normalizarCodigoMinsa(a.getCodigoMinsa()) : null;
            
            if (codigoNorm != null && codigoANombre.containsKey(codigoNorm)) {
                String nombreExistente = codigoANombre.get(codigoNorm);
                csvUnicos.remove(nombreExistente);
            }
            
            csvUnicos.put(nombreNorm, a);
            if (codigoNorm != null) {
                codigoANombre.put(codigoNorm, nombreNorm);
            }
        }
        Collection<AlimentoDataset> importadosUnicos = csvUnicos.values();

        List<AlimentoDataset> existentes = alimentoRepo.findAll();
        
        Map<String, AlimentoDataset> porCodigo = new HashMap<>();
        Map<String, AlimentoDataset> porNombre = new HashMap<>();
        for (AlimentoDataset ext : existentes) {
            indexarAlimentoEnMapas(ext, porCodigo, porNombre);
        }

        List<AlimentoDataset> nuevosParaInsertar = new ArrayList<>();
        List<AlimentoDataset> existentesParaActualizar = new ArrayList<>();

        for (AlimentoDataset origen : importadosUnicos) {
            AlimentoDataset destino = resolverAlimentoExistente(origen, porCodigo, porNombre);
            if (destino != null) {
                copiarDatos(origen, destino);
                existentesParaActualizar.add(destino);
            } else {
                nuevosParaInsertar.add(origen);
                indexarAlimentoEnMapas(origen, porCodigo, porNombre);
            }
        }

        if (!nuevosParaInsertar.isEmpty()) {
            insertarMasivoJdbc(nuevosParaInsertar);
        }

        if (!existentesParaActualizar.isEmpty()) {
            alimentoRepo.saveAll(existentesParaActualizar);
        }
    }

    private void insertarMasivoJdbc(List<AlimentoDataset> lista) {
        if (lista.isEmpty()) {
            return;
        }
        for (int offset = 0; offset < lista.size(); offset += JDBC_BATCH_SIZE) {
            int fin = Math.min(offset + JDBC_BATCH_SIZE, lista.size());
            List<AlimentoDataset> chunk = lista.subList(offset, fin);
            jdbcTemplate.batchUpdate(INSERT_ALIMENTO_SQL, chunk, chunk.size(), this::bindAlimentoInsert);
        }
    }

    private void bindAlimentoInsert(PreparedStatement ps, AlimentoDataset a) throws SQLException {
        ps.setString(1, a.getCodigoMinsa());
        ps.setString(2, a.getNombre());
        ps.setString(3, a.getCategoria());
        setBigDecimal(ps, 4, a.getEnergiaKcal());
        setBigDecimal(ps, 5, a.getAguaG());
        setBigDecimal(ps, 6, a.getProteinasG());
        setBigDecimal(ps, 7, a.getGrasaTotalG());
        setBigDecimal(ps, 8, a.getCarbohidratosTotalesG());
        setBigDecimal(ps, 9, a.getCarbohidratosDisponiblesG());
        setBigDecimal(ps, 10, a.getFibraG());
        setBigDecimal(ps, 11, a.getCenizasG());
        setBigDecimal(ps, 12, a.getCalcioMg());
        setBigDecimal(ps, 13, a.getFosforoMg());
        setBigDecimal(ps, 14, a.getZincMg());
        setBigDecimal(ps, 15, a.getHierroMg());
        setBigDecimal(ps, 16, a.getBetaCarotenoUg());
        setBigDecimal(ps, 17, a.getVitaminaAUg());
        setBigDecimal(ps, 18, a.getTiaminaMg());
        setBigDecimal(ps, 19, a.getRiboflavinaMg());
        setBigDecimal(ps, 20, a.getNiacinaMg());
        setBigDecimal(ps, 21, a.getVitaminaCMg());
        setBigDecimal(ps, 22, a.getAcidoFolicoUg());
        setBigDecimal(ps, 23, a.getSodioMg());
        setBigDecimal(ps, 24, a.getPotasioMg());
        setBigDecimal(ps, 25, a.getCostoKgSoles());
        Integer[] meses = a.getMesesDisponibilidad();
        if (meses == null || meses.length == 0) {
            ps.setArray(26, ps.getConnection().createArrayOf("integer", new Integer[0]));
        } else {
            ps.setArray(26, ps.getConnection().createArrayOf("integer", meses));
        }
        if (a.getModificadoPor() != null) {
            ps.setObject(27, a.getModificadoPor(), Types.OTHER);
        } else {
            ps.setNull(27, Types.OTHER);
        }
        if (a.getFechaModificacion() != null) {
            ps.setTimestamp(28, Timestamp.valueOf(a.getFechaModificacion()));
        } else {
            ps.setNull(28, Types.TIMESTAMP);
        }
    }

    private void setBigDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    @FunctionalInterface
    public interface CsvImportProgressListener {
        void onProgress(int actual, int total);
    }

    private void indexarAlimentoEnMapas(
            AlimentoDataset alimento,
            Map<String, AlimentoDataset> porCodigo,
            Map<String, AlimentoDataset> porNombre
    ) {
        if (alimento.getCodigoMinsa() != null && !alimento.getCodigoMinsa().isBlank()) {
            porCodigo.put(normalizarCodigoMinsa(alimento.getCodigoMinsa()), alimento);
        }
        if (alimento.getNombre() != null && !alimento.getNombre().isBlank()) {
            porNombre.put(normalizarNombreAlimento(alimento.getNombre()), alimento);
        }
    }

    private AlimentoDataset resolverAlimentoExistente(
            AlimentoDataset origen,
            Map<String, AlimentoDataset> porCodigo,
            Map<String, AlimentoDataset> porNombre
    ) {
        if (origen.getCodigoMinsa() != null && !origen.getCodigoMinsa().isBlank()) {
            AlimentoDataset existentePorCodigo = porCodigo.get(normalizarCodigoMinsa(origen.getCodigoMinsa()));
            if (existentePorCodigo != null) {
                return existentePorCodigo;
            }
        }
        if (origen.getNombre() != null && !origen.getNombre().isBlank()) {
            return porNombre.get(normalizarNombreAlimento(origen.getNombre()));
        }
        return null;
    }

    private String normalizarCodigoMinsa(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizarNombreAlimento(String nombre) {
        return nombre.trim().toLowerCase(Locale.ROOT);
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
        return v.equals("1")
                || v.equals("x")
                || v.equals("si")
                || v.equals("sí")
                || v.equals("true")
                || v.equals("t")
                || v.equals("yes")
                || v.equals("verdadero");
    }

    private String leerTextoCsv(MultipartFile archivo) throws java.io.IOException {
        byte[] raw = archivo.getBytes();
        if (raw.length >= 3 && raw[0] == (byte) 0xEF && raw[1] == (byte) 0xBB && raw[2] == (byte) 0xBF) {
            raw = Arrays.copyOfRange(raw, 3, raw.length);
        }
        if (esUtf8Valido(raw)) {
            return new String(raw, StandardCharsets.UTF_8);
        }
        return new String(raw, StandardCharsets.ISO_8859_1);
    }

    private boolean esUtf8Valido(byte[] raw) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(raw));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private String[] ajustarColumnas(String[] cols, int fila) {
        if (cols.length > CSV_COLUMNAS.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fila " + fila + ": se esperaban " + CSV_COLUMNAS.size() + " columnas, se encontraron " + cols.length + ".");
        }
        if (cols.length == CSV_COLUMNAS.size()) {
            return cols;
        }
        String[] ajustadas = Arrays.copyOf(cols, CSV_COLUMNAS.size());
        Arrays.fill(ajustadas, cols.length, CSV_COLUMNAS.size(), "");
        return ajustadas;
    }

    private void validarCabecera(String headerLine) {
        String limpia = headerLine.replace("\uFEFF", "").replace("\r", "").trim();
        String[] headers = parsearLineaCsv(limpia);
        List<String> normalizados = Arrays.stream(headers)
                .map(this::normalizarNombreColumna)
                .toList();
        List<String> esperados = CSV_COLUMNAS.stream()
                .map(this::normalizarNombreColumna)
                .toList();
        if (normalizados.size() != esperados.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las columnas del CSV no coinciden. Deben ser: " + String.join(",", CSV_COLUMNAS));
        }
        for (int i = 0; i < esperados.size(); i++) {
            if (!normalizados.get(i).equals(esperados.get(i))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Columna " + (i + 1) + " incorrecta. Se esperaba \"" + CSV_COLUMNAS.get(i)
                                + "\", se encontró \"" + headers[i].trim() + "\".");
            }
        }
    }

    private String normalizarNombreColumna(String columna) {
        return columna.replace("\uFEFF", "").replace("\r", "").trim().toLowerCase(Locale.ROOT);
    }

    private String[] parsearLineaCsv(String linea) {
        String saneada = linea.replace("\r", "");
        List<String> partes = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < saneada.length(); i++) {
            char c = saneada.charAt(i);
            if (c == '"') {
                if (entreComillas && i + 1 < saneada.length() && saneada.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    entreComillas = !entreComillas;
                }
            } else if (c == ',' && !entreComillas) {
                partes.add(limpiarCelda(actual.toString()));
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        partes.add(limpiarCelda(actual.toString()));
        return partes.toArray(new String[0]);
    }

    private String limpiarCelda(String valor) {
        String v = valor.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1).trim();
        }
        return v.replace("\uFEFF", "");
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
        String g = limpiarCelda(grupo);
        if (g.length() == 1) {
            String letra = g.toUpperCase(Locale.ROOT);
            String mapeada = GRUPO_LETRA_MINSA.get(letra);
            if (mapeada != null) {
                return mapeada;
            }
        }
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
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Grupo no reconocido: " + grupo + ". Use letra MINSA (A, B, C, D, E, F, G, H, J, K, L, Q, S, T, U) o: "
                        + String.join(", ", CATEGORIAS));
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
