package com.restaurante.modules.caja.infrastructure.web;

import com.restaurante.modules.caja.application.CajaService;
import com.restaurante.modules.caja.infrastructure.web.dto.ComprobanteResponseDTO;
import com.restaurante.modules.caja.infrastructure.web.dto.EmitirComprobanteRequest;
import com.restaurante.modules.caja.infrastructure.web.dto.PedidoResumenDTO;
import com.restaurante.shared.audit.AuditoriaContextoFactory;
import com.restaurante.shared.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final CajaService cajaService;
    private final AuditoriaContextoFactory auditoriaContextoFactory;

    public CajaController(CajaService cajaService, AuditoriaContextoFactory auditoriaContextoFactory) {
        this.cajaService = cajaService;
        this.auditoriaContextoFactory = auditoriaContextoFactory;
    }

    @GetMapping("/pedidos")
    public ResponseEntity<ApiResponse<List<PedidoResumenDTO>>> getPedidosActivos() {
        return ResponseEntity.ok(ApiResponse.ok(cajaService.getPedidosActivos()));
    }

    @PostMapping("/comprobante")
    public ResponseEntity<ApiResponse<ComprobanteResponseDTO>> emitirComprobante(
            @RequestBody EmitirComprobanteRequest request,
            HttpServletRequest httpRequest,
            Authentication auth) {
        Long cajeroId = Long.parseLong(auth.getName());
        boolean puedeAplicarDescuento = auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_AD".equals(authority.getAuthority()));
        return ResponseEntity.ok(ApiResponse.ok(
                cajaService.emitirComprobante(
                        cajeroId,
                        puedeAplicarDescuento,
                        request,
                        auditoriaContextoFactory.from(httpRequest, auth))));
    }

    @GetMapping("/comprobante/{id}/escpos")
    public ResponseEntity<byte[]> imprimirEscPos(@PathVariable Long id) {
        byte[] payload = cajaService.generarEscPos(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=comprobante-" + id + ".bin")
                .body(payload);
    }
}
