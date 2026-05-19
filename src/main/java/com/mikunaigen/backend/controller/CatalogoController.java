package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.dto.ActualizarIngredienteRequest;
import com.mikunaigen.backend.dto.ProductoRequest;
import com.mikunaigen.backend.model.nosql.Producto;
import com.mikunaigen.backend.service.AiModelService;
import com.mikunaigen.backend.model.sql.Inventory;
import com.mikunaigen.backend.model.sql.InventoryMovement;
import com.mikunaigen.backend.model.sql.Recipe;
import com.mikunaigen.backend.repository.nosql.ProductoRepository;
import com.mikunaigen.backend.repository.sql.InventoryMovementRepository;
import com.mikunaigen.backend.repository.sql.InventoryRepository;
import com.mikunaigen.backend.repository.sql.OrderItemRepository;
import com.mikunaigen.backend.repository.sql.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@RestController
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
@RequestMapping("/api/catalogo")
public class CatalogoController {

    private static final Set<String> CATEGORIAS_PRODUCTO = Set.of(
            "Entrada", "Plato Principal", "Postres", "Bebidas"
    );

    private static final Set<String> CATEGORIAS_INGREDIENTE = Set.of(
            "Verduras", "Carnes", "Huevos", "Marinos", "Abarrotes", "Lácteos", "Bebidas", "Frutas", "Panadería"
    );

    private static final Set<String> UNIDADES_INGREDIENTE = Set.of("UNIDADES", "GR", "ML");

    private static final Set<String> ESTADOS_ORDEN_BLOQUEAN_ELIMINACION = Set.of(
            "PENDIENTE_PAGO", "VALIDANDO_PAGO", "PAGO_VALIDADO", "EN_COCINA", "EN_CAMINO"
    );

    private static final String MSG_NO_ELIMINAR_PRODUCTO_ORDEN =
            "No es posible eliminar este producto porque aún está en proceso de ser entregado. "
                    + "Intente nuevamente cuando no haya órdenes pendientes con este producto";

    private static final String MSG_NO_ELIMINAR_INSUMO_ORDEN =
            "No es posible eliminar este insumo porque pertenece a un producto que aún está en proceso de ser entregado. "
                    + "Intente nuevamente cuando no haya órdenes pendientes con este insumo";

    @Autowired private ProductoRepository productoMongoRepo;
    @Autowired private InventoryRepository inventorySqlRepo;
    @Autowired private RecipeRepository recipeSqlRepo;
    @Autowired private InventoryMovementRepository inventoryMovementRepo;
    @Autowired private OrderItemRepository orderItemRepo;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private AiModelService aiModelService;

    @GetMapping("/ingredientes")
    public List<Inventory> listarIngredientes() {
        return inventorySqlRepo.findAllByIsDeletedFalse();
    }

    @PostMapping("/ingredientes/{id}/abastecer")
    @Transactional
    public ResponseEntity<?> abastecerInsumo(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Inventory inv = inventorySqlRepo.findById(id).orElse(null);
        if (inv == null || inv.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Insumo no encontrado."));
        }

        Object qObj = body.get("quantity");
        if (qObj == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cantidad de abastecimiento es obligatoria."));
        }
        Double qty = toPositiveDouble(qObj);
        if (qty == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cantidad inválida. No uses notación científica (e)."));
        }

        ResponseEntity<?> qtyErr = validarCantidadAbastecimiento(qty, inv.getUnit());
        if (qtyErr != null) return qtyErr;

        Double unitCost = null;
        Object uc = body.get("unitCost");
        if (uc != null && !(uc instanceof String s && s.trim().isEmpty())) {
            Double parsed = toNonNegativeDouble(uc);
            if (parsed == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Costo unitario de compra inválido."));
            }
            ResponseEntity<?> costErr = validarNoNegativoMax3Dec(parsed, "El costo unitario de compra");
            if (costErr != null) return costErr;
            unitCost = parsed;
        }

        String reason = trimToNull(body.get("reason") == null ? null : String.valueOf(body.get("reason")));

        double prev = inv.getStockQuantity() != null ? inv.getStockQuantity() : 0.0;
        double newStock = prev + qty;

        inv.setStockQuantity(newStock);
        inventorySqlRepo.save(inv);

        InventoryMovement m = new InventoryMovement();
        m.setInventoryId(id);
        m.setQuantity(scale2(qty));
        m.setPreviousStock(scale2(prev));
        m.setNewStock(scale2(newStock));
        m.setUnitCost(unitCost != null ? scale3(unitCost) : null);
        m.setMovementType("ABASTECIMIENTO");
        m.setReason(reason);
        m.setCreatedAt(LocalDateTime.now());
        inventoryMovementRepo.save(m);

        return ResponseEntity.ok(Map.of(
                "message", "Abastecimiento registrado.",
                "newStock", newStock
        ));
    }

    @PostMapping("/ingredientes")
    public ResponseEntity<?> guardarIngrediente(@RequestBody Inventory item) {
        ResponseEntity<?> err = validarInventario(item);
        if (err != null) return err;

        String nombre = item.getName().trim();
        if (inventorySqlRepo.existsByNameIgnoreCaseAndIsDeletedFalse(nombre)) {
            return ResponseEntity.badRequest().body(Map.of("message", "El insumo ya existe"));
        }

        item.setName(nombre);
        item.setDeleted(false);
        inventorySqlRepo.save(item);
        return ResponseEntity.ok(Map.of("message", "Ingrediente guardado en SQL"));
    }

    @PutMapping("/ingredientes/{id}")
    @Transactional
    public ResponseEntity<?> actualizarIngrediente(@PathVariable Integer id, @RequestBody ActualizarIngredienteRequest req) {
        Inventory existente = inventorySqlRepo.findById(id).orElse(null);
        if (existente == null || existente.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Insumo no encontrado."));
        }

        Inventory item = new Inventory();
        item.setName(req.getName());
        item.setCategory(req.getCategory());
        item.setUnit(req.getUnit());
        item.setStockQuantity(req.getStockQuantity());
        item.setPrice(req.getPrice());
        item.setImageBase64(req.getImageBase64());

        ResponseEntity<?> err = validarInventario(item);
        if (err != null) return err;

        String nombre = item.getName();
        if (inventorySqlRepo.existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(nombre, id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "El nombre de insumo ya existe"));
        }

        String unidadAnterior = existente.getUnit() != null ? existente.getUnit().trim() : "";
        String unidadNueva = item.getUnit().trim();
        if (!unidadAnterior.equalsIgnoreCase(unidadNueva)) {
            List<String> mongoIds = recipeSqlRepo.findDistinctActiveMongoProductIdsByIngredientId(id);
            if (!mongoIds.isEmpty() && !Boolean.TRUE.equals(req.getConfirmarCambioUnidad())) {
                List<String> nombresProductos = new ArrayList<>();
                for (String mid : mongoIds) {
                    productoMongoRepo.findById(mid).ifPresent(prod -> {
                        if (!prod.isDeleted()) {
                            nombresProductos.add(prod.getName());
                        }
                    });
                }
                return ResponseEntity.status(409).body(Map.of(
                        "code", "UNIT_CHANGE_WARNING",
                        "message",
                        "Este insumo se usa en recetas activas. Al cambiar la unidad, revisa las cantidades en esas recetas.",
                        "productos", nombresProductos
                ));
            }
        }

        existente.setName(nombre);
        existente.setCategory(item.getCategory());
        existente.setUnit(unidadNueva);
        existente.setStockQuantity(item.getStockQuantity());
        existente.setPrice(item.getPrice());
        existente.setImageBase64(item.getImageBase64());
        inventorySqlRepo.save(existente);
        messagingTemplate.convertAndSend("/topic/catalogo", "ingrediente_actualizado");

        return ResponseEntity.ok(Map.of("message", "Insumo actualizado."));
    }

    @GetMapping("/productos/{id}/edicion")
    public ResponseEntity<?> obtenerProductoParaEdicion(@PathVariable String id) {
        Producto p = productoMongoRepo.findById(id).orElse(null);
        if (p == null || p.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Producto no encontrado."));
        }
        boolean restringido = tieneOrdenBloqueanteParaProducto(id);
        List<Recipe> lineas = recipeSqlRepo.findByMongoProductIdAndIsDeletedFalse(id);
        List<Map<String, Object>> receta = new ArrayList<>();
        for (Recipe r : lineas) {
            Inventory ing = r.getIngredient();
            if (ing == null) continue;
            Map<String, Object> fila = new HashMap<>();
            fila.put("ingredientId", ing.getId());
            fila.put("quantity", r.getQuantityToSubtract());
            fila.put("name", ing.getName());
            fila.put("unit", ing.getUnit());
            receta.add(fila);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("producto", p);
        body.put("receta", receta);
        body.put("edicionRestringidaPorOrden", restringido);
        return ResponseEntity.ok(body);
    }

    @PutMapping("/productos/{id}")
    @Transactional
    public ResponseEntity<?> actualizarProducto(@PathVariable String id, @RequestBody ProductoRequest request) {
        Producto existente = productoMongoRepo.findById(id).orElse(null);
        if (existente == null || existente.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Producto no encontrado."));
        }

        boolean restringido = tieneOrdenBloqueanteParaProducto(id);
        Producto incoming = request.getProducto();
        if (incoming == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Solicitud inválida."));
        }

        if (restringido) {
            if (incoming.getDescription() != null) {
                existente.setDescription(incoming.getDescription());
            }
            if (incoming.getImagesBase64() != null && !incoming.getImagesBase64().isEmpty()) {
                existente.setImagesBase64(incoming.getImagesBase64());
            }
            productoMongoRepo.save(existente);
            messagingTemplate.convertAndSend("/topic/catalogo", "producto_actualizado");
            return ResponseEntity.ok(Map.of(
                    "message",
                    "Producto actualizado (solo descripción e imágenes; hay pedidos en curso)."
            ));
        }

        String nombre = trimToNull(incoming.getName());
        if (nombre == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El nombre del producto es obligatorio."));
        }
        if (!nombre.equalsIgnoreCase(existente.getName())
                && productoMongoRepo.existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(nombre, id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "El nombre del producto ya existe"));
        }
        incoming.setName(nombre);

        String cat = incoming.getCategory();
        if (cat == null || !CATEGORIAS_PRODUCTO.contains(cat.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Categoría de producto no válida."));
        }
        incoming.setCategory(cat.trim());

        ResponseEntity<?> precioErr = validarPrecioVenta(incoming.getPrice());
        if (precioErr != null) return precioErr;

        if (incoming.getImagesBase64() == null || incoming.getImagesBase64().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debe incluir al menos una imagen del producto."));
        }

        if (request.getReceta() == null || request.getReceta().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La receta debe incluir al menos un insumo."));
        }

        for (ProductoRequest.RecetaItemDTO item : request.getReceta()) {
            if (item.getIngredientId() == null || item.getQuantity() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cada línea de receta requiere insumo y cantidad."));
            }
            Inventory ing = inventorySqlRepo.findById(item.getIngredientId()).orElse(null);
            if (ing == null || ing.isDeleted()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Insumo inválido en la receta."));
            }
            ResponseEntity<?> qErr = validarCantidadReceta(item.getQuantity(), ing.getUnit());
            if (qErr != null) return qErr;
        }

        existente.setName(incoming.getName());
        existente.setCategory(incoming.getCategory());
        existente.setPrice(incoming.getPrice());
        existente.setDescription(incoming.getDescription() != null ? incoming.getDescription() : "");
        existente.setImagesBase64(incoming.getImagesBase64());
        existente.setActive(true);
        productoMongoRepo.save(existente);

        List<Recipe> viejas = recipeSqlRepo.findByMongoProductId(id);
        recipeSqlRepo.deleteAll(viejas);

        for (ProductoRequest.RecetaItemDTO item : request.getReceta()) {
            Inventory ing = inventorySqlRepo.findById(item.getIngredientId()).orElseThrow();
            Recipe r = new Recipe();
            r.setMongoProductId(id);
            r.setIngredient(ing);
            r.setQuantityToSubtract(item.getQuantity());
            r.setDeleted(false);
            recipeSqlRepo.save(r);
        }
        messagingTemplate.convertAndSend("/topic/catalogo", "producto_actualizado");

        return ResponseEntity.ok(Map.of("message", "Producto y receta actualizados."));
    }

    @GetMapping("/productos")
    public List<Producto> listarProductos() {
        return productoMongoRepo.findByIsDeletedFalse();
    }

    @GetMapping("/productos/menu")
    public ResponseEntity<?> listarProductosConSugerencias(@RequestParam(required = false) String userId) {
        List<Producto> productos = productoMongoRepo.findByIsDeletedFalse();
        List<String> recomendados = aiModelService.recomendarTop3(userId);
        Set<String> ids = new HashSet<>(recomendados);

        Map<String, Object> body = new HashMap<>();
        body.put("productos", productos);
        body.put("recommendedProductIds", recomendados);
        body.put("showRecommendations", !recomendados.isEmpty());
        body.put("recommendationsTitle", "Sugerencias para ti");
        body.put("highlightedProducts", productos.stream().filter(p -> ids.contains(p.getId())).toList());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/productos/menu/base")
    public ResponseEntity<?> listarProductosMenuBase() {
        List<Producto> productos = productoMongoRepo.findByIsDeletedFalse();
        Map<String, Object> body = new HashMap<>();
        body.put("productos", productos);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/productos/menu/recomendaciones")
    public ResponseEntity<?> obtenerRecomendacionesMenu(@RequestParam(required = false) String userId) {
        List<Producto> productos = productoMongoRepo.findByIsDeletedFalse();
        List<String> recomendados = aiModelService.recomendarTop3(userId);
        Set<String> ids = new HashSet<>(recomendados);

        Map<String, Object> body = new HashMap<>();
        body.put("recommendedProductIds", recomendados);
        body.put("showRecommendations", !recomendados.isEmpty());
        body.put("recommendationsTitle", "Sugerencias para ti");
        body.put("highlightedProducts", productos.stream().filter(p -> ids.contains(p.getId())).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/productos")
    public ResponseEntity<?> guardarProducto(@RequestBody ProductoRequest request) {
        Producto guardado = null;
        try {
            ResponseEntity<?> err = validarProductoRequest(request);
            if (err != null) return err;

            Producto p = request.getProducto();
            p.setActive(true);
            p.setDeleted(false);

            String nombreProducto = p.getName().trim();
            if (productoMongoRepo.existsByNameIgnoreCaseAndIsDeletedFalse(nombreProducto)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Esa receta ya existe"));
            }
            p.setName(nombreProducto);

            guardado = productoMongoRepo.save(p);
            String mongoId = guardado.getId();

            for (ProductoRequest.RecetaItemDTO item : request.getReceta()) {
                Inventory ing = inventorySqlRepo.findById(item.getIngredientId())
                        .orElseThrow(() -> new IllegalArgumentException("Insumo no encontrado."));
                if (ing.isDeleted()) {
                    throw new IllegalArgumentException("Insumo no disponible.");
                }
                ResponseEntity<?> qErr = validarCantidadReceta(item.getQuantity(), ing.getUnit());
                if (qErr != null) {
                    productoMongoRepo.deleteById(mongoId);
                    return qErr;
                }

                Recipe r = new Recipe();
                r.setMongoProductId(mongoId);
                r.setIngredient(ing);
                r.setQuantityToSubtract(item.getQuantity());
                r.setDeleted(false);
                recipeSqlRepo.save(r);
            }
            messagingTemplate.convertAndSend("/topic/catalogo", "producto_creado");

            return ResponseEntity.ok(Map.of("message", "Producto y Receta creados exitosamente"));
        } catch (IllegalArgumentException e) {
            if (guardado != null && guardado.getId() != null) {
                try {
                    productoMongoRepo.deleteById(guardado.getId());
                } catch (Exception ignored) {
                }
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            if (guardado != null && guardado.getId() != null) {
                try {
                    productoMongoRepo.deleteById(guardado.getId());
                } catch (Exception ignored) {
                }
            }
            return ResponseEntity.internalServerError().body(Map.of("message", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/productos/{id}/eliminar-precheck")
    public ResponseEntity<?> precheckEliminarProducto(@PathVariable String id) {
        Producto p = productoMongoRepo.findById(id).orElse(null);
        if (p == null || p.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("allowed", false, "message", "Producto no encontrado."));
        }
        if (tieneOrdenBloqueanteParaProducto(id)) {
            return ResponseEntity.ok(Map.of("allowed", false, "message", MSG_NO_ELIMINAR_PRODUCTO_ORDEN));
        }
        return ResponseEntity.ok(Map.of("allowed", true));
    }

    @GetMapping("/ingredientes/{id}/eliminar-precheck")
    public ResponseEntity<?> precheckEliminarIngrediente(@PathVariable Integer id) {
        Inventory inv = inventorySqlRepo.findById(id).orElse(null);
        if (inv == null || inv.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("allowed", false, "message", "Insumo no encontrado."));
        }
        List<String> mongoIds = recipeSqlRepo.findDistinctActiveMongoProductIdsByIngredientId(id);
        for (String mid : mongoIds) {
            if (tieneOrdenBloqueanteParaProducto(mid)) {
                return ResponseEntity.ok(Map.of("allowed", false, "message", MSG_NO_ELIMINAR_INSUMO_ORDEN));
            }
        }
        if (mongoIds.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "allowed", true,
                    "requiereAdvertenciaRecetas", false
            ));
        }
        List<String> nombres = new ArrayList<>();
        for (String mid : mongoIds) {
            productoMongoRepo.findById(mid).ifPresent(prod -> {
                if (!prod.isDeleted()) {
                    nombres.add(prod.getName());
                }
            });
        }
        return ResponseEntity.ok(Map.of(
                "allowed", true,
                "requiereAdvertenciaRecetas", true,
                "productosAfectados", nombres
        ));
    }

    @DeleteMapping("/productos/{id}")
    @Transactional
    public ResponseEntity<?> eliminarProducto(@PathVariable String id) {
        Producto p = productoMongoRepo.findById(id).orElse(null);
        if (p == null || p.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Producto no encontrado."));
        }
        if (tieneOrdenBloqueanteParaProducto(id)) {
            return ResponseEntity.status(409).body(Map.of("message", MSG_NO_ELIMINAR_PRODUCTO_ORDEN));
        }
        p.setDeleted(true);
        productoMongoRepo.save(p);

        List<Recipe> recetas = recipeSqlRepo.findByMongoProductId(id);
        recetas.forEach(r -> r.setDeleted(true));
        recipeSqlRepo.saveAll(recetas);
        messagingTemplate.convertAndSend("/topic/catalogo", "producto_eliminado");

        return ResponseEntity.ok(Map.of("message", "Producto eliminado lógicamente"));
    }

    @DeleteMapping("/ingredientes/{id}")
    @Transactional
    public ResponseEntity<?> eliminarIngrediente(@PathVariable Integer id) {
        Inventory inv = inventorySqlRepo.findById(id).orElse(null);
        if (inv == null || inv.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Insumo no encontrado."));
        }
        List<String> mongoIds = recipeSqlRepo.findDistinctActiveMongoProductIdsByIngredientId(id);
        for (String mid : mongoIds) {
            if (tieneOrdenBloqueanteParaProducto(mid)) {
                return ResponseEntity.status(409).body(Map.of("message", MSG_NO_ELIMINAR_INSUMO_ORDEN));
            }
        }

        inv.setDeleted(true);
        inventorySqlRepo.save(inv);

        for (String mid : mongoIds) {
            productoMongoRepo.findById(mid).ifPresent(prod -> {
                prod.setDeleted(true);
                productoMongoRepo.save(prod);
            });
            List<Recipe> lineas = recipeSqlRepo.findByMongoProductId(mid);
            lineas.forEach(r -> r.setDeleted(true));
            recipeSqlRepo.saveAll(lineas);
        }
        messagingTemplate.convertAndSend("/topic/catalogo", "ingrediente_eliminado");

        return ResponseEntity.ok(Map.of("message", "Insumo eliminado lógicamente"));
    }

    private boolean tieneOrdenBloqueanteParaProducto(String mongoProductId) {
        return orderItemRepo.existsByMongoProductIdAndOrderStatusIn(mongoProductId, ESTADOS_ORDEN_BLOQUEAN_ELIMINACION);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private ResponseEntity<?> validarInventario(Inventory item) {
        if (item == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cuerpo inválido."));
        }
        String nombre = trimToNull(item.getName());
        if (nombre == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El nombre del insumo es obligatorio."));
        }
        item.setName(nombre);

        String cat = item.getCategory();
        if (cat == null || !CATEGORIAS_INGREDIENTE.contains(cat.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Categoría de insumo no válida."));
        }
        item.setCategory(cat.trim());

        String unit = item.getUnit();
        if (unit == null || !UNIDADES_INGREDIENTE.contains(unit.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Unidad de medida no válida."));
        }
        item.setUnit(unit.trim());

        if (item.getStockQuantity() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El stock inicial es obligatorio."));
        }
        ResponseEntity<?> stockErr = validarStockOCosto(item.getStockQuantity(), item.getUnit(), true);
        if (stockErr != null) return stockErr;

        if (item.getPrice() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El costo unitario es obligatorio."));
        }
        ResponseEntity<?> costErr = validarNoNegativoMax3Dec(item.getPrice(), "El costo unitario");
        if (costErr != null) return costErr;

        String foto = trimToNull(item.getImageBase64());
        if (foto == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "La foto del insumo es obligatoria."));
        }
        item.setImageBase64(foto);

        return null;
    }

    private ResponseEntity<?> validarStockOCosto(Double value, String unit, boolean esStock) {
        ResponseEntity<?> base = validarNoNegativoMax2Dec(value, esStock ? "El stock inicial" : "El valor");
        if (base != null) return base;
        if ("UNIDADES".equalsIgnoreCase(unit)) {
            if (!esEntero(value)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message",
                        esStock ? "Con unidad Unidades el stock no puede tener decimales." : "Valor inválido."));
            }
        } else {
            if (!maxDosDecimales(value)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message",
                        esStock ? "Con gramos o mililitros el stock admite como máximo dos decimales." : "Máximo dos decimales."));
            }
        }
        return null;
    }

    private ResponseEntity<?> validarCantidadReceta(Double quantity, String unitIngrediente) {
        if (quantity == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cantidad en la receta es obligatoria."));
        }
        if (quantity < 0 || Double.isNaN(quantity) || Double.isInfinite(quantity)) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cantidad en la receta no puede ser negativa."));
        }
        return validarStockOCosto(quantity, unitIngrediente, true);
    }

    private ResponseEntity<?> validarCantidadAbastecimiento(Double quantity, String unitIngrediente) {
        if (quantity == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cantidad es obligatoria."));
        }
        if (quantity <= 0 || Double.isNaN(quantity) || Double.isInfinite(quantity)) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cantidad de abastecimiento debe ser mayor que cero."));
        }
        return validarStockOCosto(quantity, unitIngrediente, true);
    }

    private static BigDecimal scale2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }

    private static Double toPositiveDouble(Object o) {
        Double d = toDoubleLoose(o);
        if (d == null || d <= 0) return null;
        return d;
    }

    private static Double toNonNegativeDouble(Object o) {
        Double d = toDoubleLoose(o);
        if (d == null || d < 0) return null;
        return d;
    }

    private static Double toDoubleLoose(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return null;
            return d;
        }
        if (o instanceof String s) {
            String t = s.trim().replace(',', '.');
            if (t.isEmpty()) return null;
            if (t.contains("e") || t.contains("E")) return null;
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private ResponseEntity<?> validarNoNegativoMax2Dec(Double value, String campo) {
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return ResponseEntity.badRequest().body(Map.of("message", campo + " no puede ser negativo."));
        }
        if (!maxDosDecimales(value)) {
            return ResponseEntity.badRequest().body(Map.of("message", campo + " admite como máximo dos decimales."));
        }
        return null;
    }

    private ResponseEntity<?> validarNoNegativoMax3Dec(Double value, String campo) {
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return ResponseEntity.badRequest().body(Map.of("message", campo + " no puede ser negativo."));
        }
        BigDecimal redondeado = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
        boolean max3Dec = BigDecimal.valueOf(value).subtract(redondeado).abs().compareTo(new BigDecimal("0.0000001")) <= 0;
        
        if (!max3Dec) {
            return ResponseEntity.badRequest().body(Map.of("message", campo + " admite como máximo tres decimales."));
        }
        return null;
    }

    private static boolean maxDosDecimales(double v) {
        BigDecimal redondeado = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(v).subtract(redondeado).abs().compareTo(new BigDecimal("0.0000001")) <= 0;
    }

    private static boolean esEntero(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9;
    }

    private ResponseEntity<?> validarPrecioVenta(Double price) {
        if (price == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El precio de venta es obligatorio."));
        }
        if (price < 0.10 - 1e-9 || Double.isNaN(price) || Double.isInfinite(price)) {
            return ResponseEntity.badRequest().body(Map.of("message", "El precio de venta mínimo es 0.10."));
        }
        return validarNoNegativoMax2Dec(price, "El precio de venta");
    }

    private ResponseEntity<?> validarProductoRequest(ProductoRequest request) {
        if (request == null || request.getProducto() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Solicitud inválida."));
        }
        Producto p = request.getProducto();
        String nombre = trimToNull(p.getName());
        if (nombre == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El nombre del producto es obligatorio."));
        }
        p.setName(nombre);

        String cat = p.getCategory();
        if (cat == null || !CATEGORIAS_PRODUCTO.contains(cat.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Categoría de producto no válida."));
        }
        p.setCategory(cat.trim());

        ResponseEntity<?> precioErr = validarPrecioVenta(p.getPrice());
        if (precioErr != null) return precioErr;

        if (p.getImagesBase64() == null || p.getImagesBase64().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debe incluir al menos una imagen del producto."));
        }

        if (request.getReceta() == null || request.getReceta().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La receta debe incluir al menos un insumo."));
        }

        for (ProductoRequest.RecetaItemDTO item : request.getReceta()) {
            if (item.getIngredientId() == null || item.getQuantity() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cada línea de receta requiere insumo y cantidad."));
            }
            Inventory ing = inventorySqlRepo.findById(item.getIngredientId()).orElse(null);
            if (ing == null || ing.isDeleted()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Insumo inválido en la receta."));
            }
            ResponseEntity<?> qErr = validarCantidadReceta(item.getQuantity(), ing.getUnit());
            if (qErr != null) return qErr;
        }

        return null;
    }
}
