package com.mikunaigen.backend.service;

import com.mikunaigen.backend.dto.*;
import com.mikunaigen.backend.model.nosql.CartItemMongo;
import com.mikunaigen.backend.model.nosql.Producto;
import com.mikunaigen.backend.model.nosql.ShoppingCart;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.nosql.ProductoRepository;
import com.mikunaigen.backend.repository.nosql.ShoppingCartRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.util.UsuarioCompradorValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShoppingCartService {

    private static final int MAX_QTY = 10;
    private static final double EPS = 0.015;

    @Autowired
    private ShoppingCartRepository shoppingCartRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserInteractionService userInteractionService;
    @Autowired
    private AiModelService aiModelService;

    public record LoginCartPayload(CarritoResponse cart, List<String> removedItems) {}

    public LoginCartPayload loadSanitizeAndEnrich(String userId) {
        ShoppingCart cart = getOrCreate(userId);
        List<String> removed = sanitizeAndPersist(cart);
        CarritoResponse dto = enrich(cart);
        return new LoginCartPayload(dto, removed);
    }

    public CarritoResponse obtenerCarrito(String userId) {
        validarUsuarioCliente(userId);
        ShoppingCart cart = getOrCreate(userId);
        List<String> removed = sanitizeAndPersist(cart);
        CarritoResponse base = enrich(cart);
        return new CarritoResponse(base.items(), removed);
    }

    public CarritoResponse agregarUnidad(String userId, String productId) {
        validarUsuarioCliente(userId);
        Producto p = productoRepository.findById(productId).orElseThrow(
                () -> new IllegalArgumentException("Producto no encontrado."));
        if (p.isDeleted()) {
            throw new IllegalArgumentException("Producto no disponible.");
        }
        ShoppingCart cart = getOrCreate(userId);
        CartItemMongo line = findLine(cart, productId);
        if (line == null) {
            cart.getItems().add(new CartItemMongo(productId, 1));
            registrarInteraccionSeguro(userId, productId, "ADD_TO_CART", null);
        } else {
            if (line.getQuantity() >= MAX_QTY) {
                return enrich(cart);
            }
            line.setQuantity(line.getQuantity() + 1);
            registrarInteraccionSeguro(userId, productId, "INCREMENT_QUANTITY", null);
        }
        shoppingCartRepository.save(cart);
        return enrich(cart);
    }

    public CarritoResponse incrementar(String userId, String productId) {
        return agregarUnidad(userId, productId);
    }

    public CarritoResponse decrementar(String userId, String productId) {
        validarUsuarioCliente(userId);
        ShoppingCart cart = getOrCreate(userId);
        CartItemMongo line = findLine(cart, productId);
        if (line == null) {
            return enrich(cart);
        }
        if (line.getQuantity() <= 1) {
            cart.getItems().removeIf(i -> productId.equals(i.getProductId()));
            registrarInteraccionSeguro(userId, productId, "REMOVE_FROM_CART", null);
        } else {
            line.setQuantity(line.getQuantity() - 1);
            registrarInteraccionSeguro(userId, productId, "DECREMENT_QUANTITY", null);
        }
        shoppingCartRepository.save(cart);
        return enrich(cart);
    }

    public CarritoResponse eliminarLinea(String userId, String productId) {
        validarUsuarioCliente(userId);
        ShoppingCart cart = getOrCreate(userId);
        cart.getItems().removeIf(i -> productId.equals(i.getProductId()));
        registrarInteraccionSeguro(userId, productId, "REMOVE_FROM_CART", null);
        shoppingCartRepository.save(cart);
        return enrich(cart);
    }

    public VerificarPreciosResponse verificarPrecios(VerificarPreciosRequest req) {
        validarUsuarioCliente(req.userId());
        ShoppingCart cart = getOrCreate(req.userId());
        List<String> removed = sanitizeAndPersist(cart);

        Map<String, LineaClientePrecio> clientePorProducto = req.lineasCliente() == null
                ? Map.of()
                : req.lineasCliente().stream()
                .collect(Collectors.toMap(LineaClientePrecio::productId, lc -> lc, (a, b) -> a));

        double totalNuevo = 0;
        List<ItemCambioPrecio> cambios = new ArrayList<>();

        for (CartItemMongo line : cart.getItems()) {
            Producto p = productoRepository.findById(line.getProductId()).orElse(null);
            if (p == null || p.isDeleted()) {
                continue;
            }
            double precioActual = safePrice(p.getPrice());
            totalNuevo += precioActual * line.getQuantity();

            LineaClientePrecio lc = clientePorProducto.get(line.getProductId());
            if (lc != null && Math.abs(lc.precioUnitario() - precioActual) > EPS) {
                cambios.add(new ItemCambioPrecio(p.getName(), lc.precioUnitario(), precioActual));
            }
        }

        double totalAnterior = req.totalCliente();
        boolean totalDistinto = Math.abs(totalAnterior - totalNuevo) > EPS;
        boolean preciosCambiaron = !cambios.isEmpty() || totalDistinto;

        CarritoResponse actualizado = enrich(cart);
        return new VerificarPreciosResponse(preciosCambiaron, totalAnterior, totalNuevo, cambios,
                new CarritoResponse(actualizado.items(), removed));
    }

    public List<CarritoLineaResponse> obtenerSugerenciasVentaCruzada(String userId) {
        validarUsuarioCliente(userId);
        ShoppingCart cart = getOrCreate(userId);
        sanitizeAndPersist(cart);
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            return List.of();
        }
        double total = 0d;
        List<String> ids = new ArrayList<>();
        for (CartItemMongo line : cart.getItems()) {
            Producto p = productoRepository.findById(line.getProductId()).orElse(null);
            if (p == null || p.isDeleted()) continue;
            ids.add(p.getId());
            total += safePrice(p.getPrice()) * line.getQuantity();
        }
        if (ids.isEmpty()) return List.of();
        List<String> sugeridos = aiModelService.recomendarCrossSellTop3(ids, total);
        if (sugeridos.isEmpty()) return List.of();

        List<CarritoLineaResponse> result = new ArrayList<>();
        for (String id : sugeridos) {
            Producto p = productoRepository.findById(id).orElse(null);
            if (p == null || p.isDeleted()) continue;
            String thumb = "assets/no-image.png";
            if (p.getImagesBase64() != null && !p.getImagesBase64().isEmpty() && p.getImagesBase64().get(0) != null) {
                thumb = p.getImagesBase64().get(0);
            }
            result.add(new CarritoLineaResponse(
                    p.getId(),
                    1,
                    p.getName() == null ? "" : p.getName(),
                    safePrice(p.getPrice()),
                    thumb
            ));
        }
        return result;
    }

    private double safePrice(Double price) {
        return price == null ? 0.0 : price;
    }

    private void validarUsuarioCliente(String userId) {
        UsuarioCompradorValidator.requerirUsuarioComprador(userRepository, userId);
    }

    public ShoppingCart getOrCreate(String userId) {
        return shoppingCartRepository.findById(userId).orElseGet(() -> {
            ShoppingCart c = new ShoppingCart();
            c.setUserId(userId);
            c.setItems(new ArrayList<>());
            return shoppingCartRepository.save(c);
        });
    }

    public List<String> sanitizeAndPersist(ShoppingCart cart) {
        if (cart.getItems() == null) {
            cart.setItems(new ArrayList<>());
        }
        List<String> removedNames = new ArrayList<>();
        Iterator<CartItemMongo> it = cart.getItems().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            CartItemMongo line = it.next();
            Optional<Producto> opt = productoRepository.findById(line.getProductId());
            if (opt.isEmpty()) {
                removedNames.add("Producto no disponible");
                it.remove();
                changed = true;
            } else if (opt.get().isDeleted()) {
                removedNames.add(opt.get().getName() != null ? opt.get().getName() : "Producto");
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            shoppingCartRepository.save(cart);
        }
        return removedNames;
    }

    public CarritoResponse enrich(ShoppingCart cart) {
        List<CarritoLineaResponse> lines = new ArrayList<>();
        if (cart.getItems() == null) {
            return new CarritoResponse(lines);
        }
        for (CartItemMongo line : cart.getItems()) {
            Producto p = productoRepository.findById(line.getProductId()).orElse(null);
            if (p == null || p.isDeleted()) {
                continue;
            }
            String thumb = "assets/no-image.png";
            if (p.getImagesBase64() != null && !p.getImagesBase64().isEmpty() && p.getImagesBase64().get(0) != null) {
                thumb = p.getImagesBase64().get(0);
            }
            double unit = safePrice(p.getPrice());
            lines.add(new CarritoLineaResponse(
                    line.getProductId(),
                    line.getQuantity(),
                    p.getName() != null ? p.getName() : "",
                    unit,
                    thumb
            ));
        }
        return new CarritoResponse(lines);
    }

    private CartItemMongo findLine(ShoppingCart cart, String productId) {
        for (CartItemMongo i : cart.getItems()) {
            if (productId.equals(i.getProductId())) {
                return i;
            }
        }
        return null;
    }

    private void registrarInteraccionSeguro(String userId, String productId, String action, Integer dwellTimeSeconds) {
        try {
            userInteractionService.registrar(userId, productId, action, dwellTimeSeconds);
        } catch (Exception ignored) {
        }
    }
}
