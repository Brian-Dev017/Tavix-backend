package com.restaurante.modules.caja.infrastructure.web;

import com.restaurante.modules.auth.infrastructure.persistence.UsuarioJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/caja/arqueos")
public class ArqueoController {

    private final ArqueoJpaRepo arqueoRepo;
    private final ComprobanteJpaRepo comprobanteRepo;
    private final UsuarioJpaRepo usuarioRepo;

    public ArqueoController(ArqueoJpaRepo arqueoRepo,
                            ComprobanteJpaRepo comprobanteRepo,
                            UsuarioJpaRepo usuarioRepo) {
        this.arqueoRepo = arqueoRepo;
        this.comprobanteRepo = comprobanteRepo;
        this.usuarioRepo = usuarioRepo;
    }

    // ──────────────── DTOs ────────────────

    public record AbrirArqueoRequest(Long cajeroId, BigDecimal montoApertura, String notas) {}

    public record CerrarArqueoRequest(BigDecimal montoCierre, String notas) {}

    // ──────────────── Endpoints ────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<ArqueoEntity>>> listar() {
        List<ArqueoEntity> arqueos = arqueoRepo.findTop10ByOrderByAperturaEnDesc();
        return ResponseEntity.ok(ApiResponse.ok(arqueos));
    }

    @GetMapping("/activo")
    public ResponseEntity<ApiResponse<ArqueoEntity>> activo() {
        ArqueoEntity arqueo = arqueoRepo
                .findTopByEstadoOrderByAperturaEnDesc(ArqueoEntity.EstadoArqueo.ABIERTO)
                .orElseThrow(() -> new BusinessException("No hay arqueo abierto", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(arqueo));
    }

    @PostMapping("/abrir")
    public ResponseEntity<ApiResponse<ArqueoEntity>> abrir(@RequestBody AbrirArqueoRequest req) {
        arqueoRepo.findTopByEstadoOrderByAperturaEnDesc(ArqueoEntity.EstadoArqueo.ABIERTO)
                .ifPresent(a -> { throw new BusinessException("Ya existe un arqueo abierto"); });

        String nombreCajero = usuarioRepo.findById(req.cajeroId())
                .map(u -> u.getNombre() + " " + u.getApellido())
                .orElse("Cajero #" + req.cajeroId());

        ArqueoEntity arqueo = new ArqueoEntity();
        arqueo.setCajeroId(req.cajeroId());
        arqueo.setNombreCajero(nombreCajero);
        arqueo.setMontoApertura(req.montoApertura());
        arqueo.setNotas(req.notas());
        arqueo.setAperturaEn(LocalDateTime.now());
        arqueo.setEstado(ArqueoEntity.EstadoArqueo.ABIERTO);

        ArqueoEntity guardado = arqueoRepo.save(arqueo);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Arqueo abierto", guardado));
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<ApiResponse<ArqueoEntity>> cerrar(@PathVariable Long id,
                                                             @RequestBody CerrarArqueoRequest req) {
        ArqueoEntity arqueo = arqueoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Arqueo no encontrado", HttpStatus.NOT_FOUND));

        if (arqueo.getEstado() == ArqueoEntity.EstadoArqueo.CERRADO) {
            throw new BusinessException("El arqueo ya está cerrado");
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime apertura = arqueo.getAperturaEn();

        BigDecimal totalVentas = comprobanteRepo.findAll().stream()
                .filter(c -> c.getEstado() == ComprobanteEntity.EstadoComprobante.COMPLETADO)
                .filter(c -> c.getPagadoEn() != null
                        && !c.getPagadoEn().isBefore(apertura)
                        && !c.getPagadoEn().isAfter(ahora))
                .map(ComprobanteEntity::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        arqueo.setMontoCierre(req.montoCierre());
        arqueo.setTotalVentas(totalVentas);
        arqueo.setCierreEn(ahora);
        arqueo.setEstado(ArqueoEntity.EstadoArqueo.CERRADO);
        if (req.notas() != null) arqueo.setNotas(req.notas());

        ArqueoEntity guardado = arqueoRepo.save(arqueo);
        return ResponseEntity.ok(ApiResponse.ok("Arqueo cerrado", guardado));
    }
}
