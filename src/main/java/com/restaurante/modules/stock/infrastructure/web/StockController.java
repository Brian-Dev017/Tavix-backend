package com.restaurante.modules.stock.infrastructure.web;

import com.restaurante.modules.stock.infrastructure.persistence.InsumoEntity;
import com.restaurante.modules.stock.infrastructure.persistence.InsumoJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final InsumoJpaRepo insumoRepo;

    public StockController(InsumoJpaRepo insumoRepo) {
        this.insumoRepo = insumoRepo;
    }

    @GetMapping("/insumos")
    public ResponseEntity<ApiResponse<List<InsumoEntity>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(insumoRepo.findAllByOrderByNombreAsc()));
    }

    @PostMapping("/insumos")
    public ResponseEntity<ApiResponse<InsumoEntity>> crear(@RequestBody InsumoEntity body) {
        if (body.getNombre() == null || body.getNombre().isBlank()) {
            throw new BusinessException("El nombre es obligatorio", HttpStatus.BAD_REQUEST);
        }
        if (body.getUnidad() == null || body.getUnidad().isBlank()) {
            throw new BusinessException("La unidad es obligatoria", HttpStatus.BAD_REQUEST);
        }
        if (body.getCategoria() == null || body.getCategoria().isBlank()) {
            throw new BusinessException("La categoria es obligatoria", HttpStatus.BAD_REQUEST);
        }
        body.setId(null);
        body.setActivo(true);
        InsumoEntity saved = insumoRepo.save(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Insumo creado", saved));
    }

    @PutMapping("/insumos/{id}")
    public ResponseEntity<ApiResponse<InsumoEntity>> actualizar(
            @PathVariable Long id,
            @RequestBody InsumoEntity body) {
        InsumoEntity entity = insumoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Insumo no encontrado", HttpStatus.NOT_FOUND));
        if (body.getNombre() != null && !body.getNombre().isBlank()) {
            entity.setNombre(body.getNombre());
        }
        if (body.getUnidad() != null && !body.getUnidad().isBlank()) {
            entity.setUnidad(body.getUnidad());
        }
        if (body.getCategoria() != null && !body.getCategoria().isBlank()) {
            entity.setCategoria(body.getCategoria());
        }
        entity.setFechaVencimiento(body.getFechaVencimiento());
        if (body.getStockActual() != null) {
            entity.setStockActual(body.getStockActual());
        }
        if (body.getStockMinimo() != null) {
            entity.setStockMinimo(body.getStockMinimo());
        }
        return ResponseEntity.ok(ApiResponse.ok("Insumo actualizado", insumoRepo.save(entity)));
    }

    @PatchMapping("/insumos/{id}/stock")
    public ResponseEntity<ApiResponse<InsumoEntity>> ajustarStock(
            @PathVariable Long id,
            @RequestBody Map<String, Double> body) {
        InsumoEntity entity = insumoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Insumo no encontrado", HttpStatus.NOT_FOUND));
        Double ajuste = body.get("ajuste");
        if (ajuste == null) {
            throw new BusinessException("El campo 'ajuste' es obligatorio", HttpStatus.BAD_REQUEST);
        }
        double nuevoStock = entity.getStockActual() + ajuste;
        if (nuevoStock < 0) {
            throw new BusinessException("El stock no puede quedar negativo", HttpStatus.BAD_REQUEST);
        }
        entity.setStockActual(nuevoStock);
        return ResponseEntity.ok(ApiResponse.ok("Stock ajustado", insumoRepo.save(entity)));
    }

    @DeleteMapping("/insumos/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        InsumoEntity entity = insumoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Insumo no encontrado", HttpStatus.NOT_FOUND));
        entity.setActivo(false);
        insumoRepo.save(entity);
        return ResponseEntity.ok(ApiResponse.ok("Insumo desactivado", null));
    }
}
