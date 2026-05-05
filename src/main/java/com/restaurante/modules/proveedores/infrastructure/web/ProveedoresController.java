package com.restaurante.modules.proveedores.infrastructure.web;

import com.restaurante.modules.proveedores.infrastructure.persistence.ProveedorEntity;
import com.restaurante.modules.proveedores.infrastructure.persistence.ProveedorJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedoresController {

    private final ProveedorJpaRepo proveedorRepo;

    public ProveedoresController(ProveedorJpaRepo proveedorRepo) {
        this.proveedorRepo = proveedorRepo;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProveedorEntity>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(proveedorRepo.findAllByOrderByNombreAsc()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProveedorEntity>> crear(@RequestBody ProveedorEntity body) {
        if (body.getNombre() == null || body.getNombre().isBlank()) {
            throw new BusinessException("El nombre del proveedor es obligatorio", HttpStatus.BAD_REQUEST);
        }
        body.setId(null);
        ProveedorEntity saved = proveedorRepo.save(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Proveedor creado", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProveedorEntity>> actualizar(
            @PathVariable Long id,
            @RequestBody ProveedorEntity body) {
        ProveedorEntity entity = proveedorRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Proveedor no encontrado", HttpStatus.NOT_FOUND));
        if (body.getNombre() != null && !body.getNombre().isBlank()) {
            entity.setNombre(body.getNombre());
        }
        if (body.getRuc() != null) {
            entity.setRuc(body.getRuc());
        }
        if (body.getTelefono() != null) {
            entity.setTelefono(body.getTelefono());
        }
        if (body.getCorreo() != null) {
            entity.setCorreo(body.getCorreo());
        }
        if (body.getContacto() != null) {
            entity.setContacto(body.getContacto());
        }
        entity.setActivo(body.isActivo());
        return ResponseEntity.ok(ApiResponse.ok("Proveedor actualizado", proveedorRepo.save(entity)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        ProveedorEntity entity = proveedorRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Proveedor no encontrado", HttpStatus.NOT_FOUND));
        entity.setActivo(false);
        proveedorRepo.save(entity);
        return ResponseEntity.ok(ApiResponse.ok("Proveedor desactivado", null));
    }
}
