package com.restaurante.modules.mesas.infrastructure.web;

import com.restaurante.modules.mesas.application.MesaService;
import com.restaurante.modules.mesas.infrastructure.web.dto.AbrirSesionRequest;
import com.restaurante.modules.mesas.infrastructure.web.dto.MesaDTO;
import com.restaurante.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mesas")
public class MesaController {

    private final MesaService mesaService;

    public MesaController(MesaService mesaService) {
        this.mesaService = mesaService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MesaDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(mesaService.listarMesas()));
    }

    @PostMapping("/sesiones")
    public ResponseEntity<ApiResponse<Long>> abrirSesion(
            @Valid @RequestBody AbrirSesionRequest request,
            Authentication auth) {
        Long meseroId = Long.parseLong(auth.getName());
        Long sesionId = mesaService.abrirSesion(request.mesaId(), meseroId);
        return ResponseEntity.ok(ApiResponse.ok("Sesión iniciada", sesionId));
    }

    @PostMapping("/sesiones/{sesionId}/cerrar")
    public ResponseEntity<ApiResponse<Void>> cerrarSesion(
            @PathVariable Long sesionId,
            Authentication auth) {
        Long usuarioId = Long.parseLong(auth.getName());
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_AD".equals(authority.getAuthority()));
        mesaService.cerrarSesionSiVacia(sesionId, usuarioId, admin);
        return ResponseEntity.ok(ApiResponse.ok("Sesión cerrada", null));
    }
}
