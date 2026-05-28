package com.restaurante.modules.pedidos.infrastructure.web;

import com.restaurante.modules.pedidos.application.PedidoService;
import com.restaurante.modules.pedidos.infrastructure.web.dto.AgregarItemRequest;
import com.restaurante.modules.pedidos.infrastructure.web.dto.CrearPedidoRequest;
import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemPedidoDTO;
import com.restaurante.shared.audit.AuditoriaContextoFactory;
import com.restaurante.shared.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;
    private final AuditoriaContextoFactory auditoriaContextoFactory;

    public PedidoController(PedidoService pedidoService, AuditoriaContextoFactory auditoriaContextoFactory) {
        this.pedidoService = pedidoService;
        this.auditoriaContextoFactory = auditoriaContextoFactory;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> crear(@Valid @RequestBody CrearPedidoRequest request,
                                                                Authentication auth,
                                                                HttpServletRequest httpRequest) {
        Long id = pedidoService.crearPedido(
                request,
                usuarioId(auth),
                esAdmin(auth),
                auditoriaContextoFactory.from(httpRequest, auth)
        );
        return ResponseEntity.ok(ApiResponse.ok("Pedido creado", Map.of("id", id)));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<ItemPedidoDTO>> agregarItem(
            @PathVariable Long id,
            @Valid @RequestBody AgregarItemRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok("Item agregado",
                pedidoService.agregarItem(
                        id,
                        request,
                        usuarioId(auth),
                        esAdmin(auth),
                        auditoriaContextoFactory.from(httpRequest, auth)
                )));
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<ApiResponse<List<ItemPedidoDTO>>> getItems(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(pedidoService.getDetalle(id, usuarioId(auth), esAdmin(auth))));
    }

    private Long usuarioId(Authentication auth) {
        return Long.parseLong(auth.getName());
    }

    private boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_AD".equals(authority.getAuthority()));
    }
}
