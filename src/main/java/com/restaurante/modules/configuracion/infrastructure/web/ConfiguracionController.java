package com.restaurante.modules.configuracion.infrastructure.web;

import com.restaurante.modules.configuracion.infrastructure.persistence.ImpresoraEntity;
import com.restaurante.modules.configuracion.infrastructure.persistence.ImpresoraJpaRepo;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigEntity;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigJpaRepo;
import com.restaurante.modules.configuracion.infrastructure.persistence.SerieComprobanteEntity;
import com.restaurante.modules.configuracion.infrastructure.persistence.SerieComprobanteJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config")
public class ConfiguracionController {

    private final NegocioConfigJpaRepo negocioRepo;
    private final SerieComprobanteJpaRepo serieRepo;
    private final ImpresoraJpaRepo impresoraRepo;

    public ConfiguracionController(NegocioConfigJpaRepo negocioRepo,
                                   SerieComprobanteJpaRepo serieRepo,
                                   ImpresoraJpaRepo impresoraRepo) {
        this.negocioRepo = negocioRepo;
        this.serieRepo = serieRepo;
        this.impresoraRepo = impresoraRepo;
    }

    // ── Negocio ───────────────────────────────────────────────────────────────

    @GetMapping("/negocio")
    public ResponseEntity<ApiResponse<NegocioConfigEntity>> getNegocio() {
        NegocioConfigEntity config = negocioRepo.findById(1L).orElseGet(() -> {
            NegocioConfigEntity def = new NegocioConfigEntity();
            def.setId(1L);
            return negocioRepo.save(def);
        });
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @PutMapping("/negocio")
    public ResponseEntity<ApiResponse<NegocioConfigEntity>> updateNegocio(@RequestBody NegocioConfigEntity body) {
        NegocioConfigEntity entity = negocioRepo.findById(1L).orElseGet(() -> {
            NegocioConfigEntity def = new NegocioConfigEntity();
            def.setId(1L);
            return def;
        });
        entity.setId(1L);
        if (body.getRucNegocio() != null) {
            entity.setRucNegocio(body.getRucNegocio());
        }
        if (body.getNombreComercial() != null) {
            entity.setNombreComercial(body.getNombreComercial());
        }
        if (body.getDireccion() != null) {
            entity.setDireccion(body.getDireccion());
        }
        if (body.getLogoUrl() != null) {
            entity.setLogoUrl(body.getLogoUrl());
        }
        return ResponseEntity.ok(ApiResponse.ok("Configuración de negocio actualizada", negocioRepo.save(entity)));
    }

    // ── Series de comprobante ─────────────────────────────────────────────────

    @GetMapping("/series")
    public ResponseEntity<ApiResponse<List<SerieComprobanteEntity>>> getSeries() {
        return ResponseEntity.ok(ApiResponse.ok(serieRepo.findAll()));
    }

    @PostMapping("/series")
    public ResponseEntity<ApiResponse<SerieComprobanteEntity>> crearSerie(@RequestBody SerieComprobanteEntity body) {
        if (body.getTipo() == null || body.getTipo().isBlank()) {
            throw new BusinessException("El tipo de comprobante es obligatorio (B/F/T)", HttpStatus.BAD_REQUEST);
        }
        if (body.getSerie() == null || body.getSerie().isBlank()) {
            throw new BusinessException("La serie es obligatoria", HttpStatus.BAD_REQUEST);
        }
        body.setId(null);
        SerieComprobanteEntity saved = serieRepo.save(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Serie creada", saved));
    }

    @PutMapping("/series/{id}")
    public ResponseEntity<ApiResponse<SerieComprobanteEntity>> actualizarSerie(
            @PathVariable Long id,
            @RequestBody SerieComprobanteEntity body) {
        SerieComprobanteEntity entity = serieRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Serie no encontrada", HttpStatus.NOT_FOUND));
        if (body.getTipo() != null && !body.getTipo().isBlank()) {
            entity.setTipo(body.getTipo());
        }
        if (body.getSerie() != null && !body.getSerie().isBlank()) {
            entity.setSerie(body.getSerie());
        }
        if (body.getCorrelativoActual() > 0) {
            entity.setCorrelativoActual(body.getCorrelativoActual());
        }
        entity.setActivo(body.isActivo());
        return ResponseEntity.ok(ApiResponse.ok("Serie actualizada", serieRepo.save(entity)));
    }

    @DeleteMapping("/series/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarSerie(@PathVariable Long id) {
        if (!serieRepo.existsById(id)) {
            throw new BusinessException("Serie no encontrada", HttpStatus.NOT_FOUND);
        }
        serieRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Serie eliminada", null));
    }

    // ── Impresoras ────────────────────────────────────────────────────────────

    @GetMapping("/impresoras")
    public ResponseEntity<ApiResponse<List<ImpresoraEntity>>> getImpresoras() {
        return ResponseEntity.ok(ApiResponse.ok(impresoraRepo.findAll()));
    }

    @PostMapping("/impresoras")
    public ResponseEntity<ApiResponse<ImpresoraEntity>> crearImpresora(@RequestBody ImpresoraEntity body) {
        if (body.getNombre() == null || body.getNombre().isBlank()) {
            throw new BusinessException("El nombre de la impresora es obligatorio", HttpStatus.BAD_REQUEST);
        }
        if (body.getTipo() == null || body.getTipo().isBlank()) {
            throw new BusinessException("El tipo de impresora es obligatorio (COCINA/CAJA/BARRA)", HttpStatus.BAD_REQUEST);
        }
        body.setId(null);
        ImpresoraEntity saved = impresoraRepo.save(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Impresora creada", saved));
    }

    @PutMapping("/impresoras/{id}")
    public ResponseEntity<ApiResponse<ImpresoraEntity>> actualizarImpresora(
            @PathVariable Long id,
            @RequestBody ImpresoraEntity body) {
        ImpresoraEntity entity = impresoraRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Impresora no encontrada", HttpStatus.NOT_FOUND));
        if (body.getNombre() != null && !body.getNombre().isBlank()) {
            entity.setNombre(body.getNombre());
        }
        if (body.getTipo() != null && !body.getTipo().isBlank()) {
            entity.setTipo(body.getTipo());
        }
        if (body.getHost() != null) {
            entity.setHost(body.getHost());
        }
        if (body.getPuerto() > 0) {
            entity.setPuerto(body.getPuerto());
        }
        entity.setActivo(body.isActivo());
        return ResponseEntity.ok(ApiResponse.ok("Impresora actualizada", impresoraRepo.save(entity)));
    }

    @DeleteMapping("/impresoras/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarImpresora(@PathVariable Long id) {
        ImpresoraEntity entity = impresoraRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Impresora no encontrada", HttpStatus.NOT_FOUND));
        entity.setActivo(false);
        impresoraRepo.save(entity);
        return ResponseEntity.ok(ApiResponse.ok("Impresora desactivada", null));
    }
}
