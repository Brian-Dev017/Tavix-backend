package com.restaurante.modules.cocina.infrastructure.web;

import com.restaurante.modules.cocina.application.CocinaService;
import com.restaurante.modules.cocina.infrastructure.web.dto.ActualizarEstadoRequest;
import com.restaurante.modules.cocina.infrastructure.web.dto.CancelarItemRequest;
import com.restaurante.modules.cocina.infrastructure.web.dto.ColaItemDTO;
import com.restaurante.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cocina")
public class CocinaController {

    private final CocinaService cocinaService;

    public CocinaController(CocinaService cocinaService) {
        this.cocinaService = cocinaService;
    }

    @GetMapping("/cola")
    public ResponseEntity<ApiResponse<List<ColaItemDTO>>> getCola() {
        return ResponseEntity.ok(ApiResponse.ok(cocinaService.getCola()));
    }

    @PatchMapping("/items/{id}/estado")
    public ResponseEntity<ApiResponse<Void>> actualizarEstado(
            @PathVariable Long id,
            @RequestBody ActualizarEstadoRequest request) {
        cocinaService.actualizarEstado(id, request.estado());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/items/{id}/cancelar")
    public ResponseEntity<ApiResponse<Void>> cancelarItem(
            @PathVariable Long id,
            @RequestBody CancelarItemRequest request) {
        cocinaService.cancelarItem(id, request.motivo());
        return ResponseEntity.ok(ApiResponse.ok("Item cancelado", null));
    }
}
