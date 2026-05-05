package com.restaurante.modules.pedidos.infrastructure.web;

import com.restaurante.modules.pedidos.application.PedidoService;
import com.restaurante.modules.pedidos.infrastructure.web.dto.AgregarItemRequest;
import com.restaurante.modules.pedidos.infrastructure.web.dto.CrearPedidoRequest;
import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemPedidoDTO;
import com.restaurante.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> crear(@Valid @RequestBody CrearPedidoRequest request) {
        Long id = pedidoService.crearPedido(request);
        return ResponseEntity.ok(ApiResponse.ok("Pedido creado", Map.of("id", id)));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<ItemPedidoDTO>> agregarItem(
            @PathVariable Long id,
            @Valid @RequestBody AgregarItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Ítem agregado", pedidoService.agregarItem(id, request)));
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<ApiResponse<List<ItemPedidoDTO>>> getItems(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(pedidoService.getDetalle(id)));
    }
}
