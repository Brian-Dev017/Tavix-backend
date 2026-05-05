package com.restaurante.modules.caja.infrastructure.web;

import com.restaurante.modules.caja.application.CajaService;
import com.restaurante.modules.caja.infrastructure.web.dto.ComprobanteResponseDTO;
import com.restaurante.modules.caja.infrastructure.web.dto.EmitirComprobanteRequest;
import com.restaurante.modules.caja.infrastructure.web.dto.PedidoResumenDTO;
import com.restaurante.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @GetMapping("/pedidos")
    public ResponseEntity<ApiResponse<List<PedidoResumenDTO>>> getPedidosActivos() {
        return ResponseEntity.ok(ApiResponse.ok(cajaService.getPedidosActivos()));
    }

    @PostMapping("/comprobante")
    public ResponseEntity<ApiResponse<ComprobanteResponseDTO>> emitirComprobante(
            @RequestBody EmitirComprobanteRequest request,
            Authentication auth) {
        Long cajeroId = Long.parseLong(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(cajaService.emitirComprobante(cajeroId, request)));
    }
}
