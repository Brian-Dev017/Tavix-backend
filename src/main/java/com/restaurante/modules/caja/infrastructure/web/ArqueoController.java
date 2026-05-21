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
import org.springframework.security.core.Authentication;
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

    public record ArqueoReporteResponse(
            Long arqueoId,
            Long cajeroId,
            String nombreCajero,
            BigDecimal montoApertura,
            BigDecimal totalVentas,
            BigDecimal totalEfectivo,
            BigDecimal totalDigital,
            BigDecimal montoEsperado,
            BigDecimal montoCierre,
            BigDecimal diferencia
    ) {}

    // ──────────────── Endpoints ────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<ArqueoEntity>>> listar(Authentication auth) {
        List<ArqueoEntity> arqueos = esAdmin(auth)
                ? arqueoRepo.findTop10ByOrderByAperturaEnDesc()
                : arqueoRepo.findAll().stream()
                        .filter(a -> a.getCajeroId().equals(usuarioId(auth)))
                        .sorted((a, b) -> b.getAperturaEn().compareTo(a.getAperturaEn()))
                        .limit(10)
                        .toList();
        return ResponseEntity.ok(ApiResponse.ok(arqueos));
    }

    @GetMapping("/activo")
    public ResponseEntity<ApiResponse<ArqueoEntity>> activo(Authentication auth) {
        ArqueoEntity arqueo = arqueoRepo
                .findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(usuarioId(auth), ArqueoEntity.EstadoArqueo.ABIERTO)
                .orElseThrow(() -> new BusinessException("No hay arqueo abierto", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(arqueo));
    }

    @PostMapping("/abrir")
    public ResponseEntity<ApiResponse<ArqueoEntity>> abrir(@RequestBody AbrirArqueoRequest req, Authentication auth) {
        Long cajeroId = usuarioId(auth);
        arqueoRepo.findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(cajeroId, ArqueoEntity.EstadoArqueo.ABIERTO)
                .ifPresent(a -> { throw new BusinessException("Ya tienes una caja abierta"); });
        if (req.montoApertura() == null || req.montoApertura().signum() < 0) {
            throw new BusinessException("El monto de apertura debe ser mayor o igual a cero", HttpStatus.BAD_REQUEST);
        }

        String nombreCajero = usuarioRepo.findById(cajeroId)
                .map(u -> u.getNombre() + " " + u.getApellido())
                .orElse("Cajero #" + cajeroId);

        ArqueoEntity arqueo = new ArqueoEntity();
        arqueo.setCajeroId(cajeroId);
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
                                                             @RequestBody CerrarArqueoRequest req,
                                                             Authentication auth) {
        ArqueoEntity arqueo = arqueoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Arqueo no encontrado", HttpStatus.NOT_FOUND));
        if (!esAdmin(auth) && !arqueo.getCajeroId().equals(usuarioId(auth))) {
            throw new BusinessException("No puedes cerrar la caja de otro cajero", HttpStatus.FORBIDDEN);
        }

        if (arqueo.getEstado() == ArqueoEntity.EstadoArqueo.CERRADO) {
            throw new BusinessException("El arqueo ya está cerrado");
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime apertura = arqueo.getAperturaEn();

        ResumenArqueo resumen = calcularResumen(arqueo, ahora);

        BigDecimal montoCierre = req.montoCierre() == null ? BigDecimal.ZERO : req.montoCierre();
        if (montoCierre.signum() < 0) {
            throw new BusinessException("El monto de cierre debe ser mayor o igual a cero", HttpStatus.BAD_REQUEST);
        }

        arqueo.setMontoCierre(montoCierre);
        arqueo.setTotalVentas(resumen.totalVentas());
        arqueo.setTotalEfectivo(resumen.totalEfectivo());
        arqueo.setTotalDigital(resumen.totalDigital());
        arqueo.setMontoEsperado(resumen.montoEsperado());
        arqueo.setDiferencia(montoCierre.subtract(resumen.montoEsperado()));
        arqueo.setCierreEn(ahora);
        arqueo.setEstado(ArqueoEntity.EstadoArqueo.CERRADO);
        if (req.notas() != null) arqueo.setNotas(req.notas());

        ArqueoEntity guardado = arqueoRepo.save(arqueo);
        return ResponseEntity.ok(ApiResponse.ok("Arqueo cerrado", guardado));
    }

    @GetMapping("/{id}/reporte")
    public ResponseEntity<ApiResponse<ArqueoReporteResponse>> reporte(@PathVariable Long id, Authentication auth) {
        ArqueoEntity arqueo = arqueoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Arqueo no encontrado", HttpStatus.NOT_FOUND));
        if (!esAdmin(auth) && !arqueo.getCajeroId().equals(usuarioId(auth))) {
            throw new BusinessException("No puedes ver la caja de otro cajero", HttpStatus.FORBIDDEN);
        }
        LocalDateTime fin = arqueo.getCierreEn() != null ? arqueo.getCierreEn() : LocalDateTime.now();
        ResumenArqueo resumen = calcularResumen(arqueo, fin);
        BigDecimal montoCierre = arqueo.getMontoCierre();
        BigDecimal diferencia = montoCierre == null ? BigDecimal.ZERO : montoCierre.subtract(resumen.montoEsperado());
        return ResponseEntity.ok(ApiResponse.ok(new ArqueoReporteResponse(
                arqueo.getId(), arqueo.getCajeroId(), arqueo.getNombreCajero(),
                arqueo.getMontoApertura(), resumen.totalVentas(), resumen.totalEfectivo(),
                resumen.totalDigital(), resumen.montoEsperado(), montoCierre, diferencia
        )));
    }

    private ResumenArqueo calcularResumen(ArqueoEntity arqueo, LocalDateTime hasta) {
        LocalDateTime apertura = arqueo.getAperturaEn();
        List<ComprobanteEntity> ventas = comprobanteRepo.findByCajeroIdAndEstadoAndPagadoEnBetween(
                arqueo.getCajeroId(), ComprobanteEntity.EstadoComprobante.COMPLETADO, apertura, hasta);

        BigDecimal totalVentas = ventas.stream()
                .map(ComprobanteEntity::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEfectivo = ventas.stream()
                .filter(c -> c.getMetodoPago() == ComprobanteEntity.MetodoPago.EFECTIVO)
                .map(ComprobanteEntity::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDigital = totalVentas.subtract(totalEfectivo);
        BigDecimal montoEsperado = (arqueo.getMontoApertura() == null ? BigDecimal.ZERO : arqueo.getMontoApertura())
                .add(totalEfectivo);
        return new ResumenArqueo(totalVentas, totalEfectivo, totalDigital, montoEsperado);
    }

    private record ResumenArqueo(BigDecimal totalVentas, BigDecimal totalEfectivo,
                                 BigDecimal totalDigital, BigDecimal montoEsperado) {}

    private Long usuarioId(Authentication auth) {
        return Long.parseLong(auth.getName());
    }

    private boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_AD".equals(authority.getAuthority()));
    }
}
