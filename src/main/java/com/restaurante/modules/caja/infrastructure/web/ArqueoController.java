package com.restaurante.modules.caja.infrastructure.web;

import com.restaurante.modules.auth.infrastructure.persistence.UsuarioJpaRepo;
import com.restaurante.modules.auth.infrastructure.persistence.UsuarioEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;
    private final PedidoJpaRepo pedidoRepo;

    public ArqueoController(ArqueoJpaRepo arqueoRepo,
                            ComprobanteJpaRepo comprobanteRepo,
                            UsuarioJpaRepo usuarioRepo,
                            PasswordEncoder passwordEncoder,
                            PedidoJpaRepo pedidoRepo) {
        this.arqueoRepo = arqueoRepo;
        this.comprobanteRepo = comprobanteRepo;
        this.usuarioRepo = usuarioRepo;
        this.passwordEncoder = passwordEncoder;
        this.pedidoRepo = pedidoRepo;
    }

    // ──────────────── DTOs ────────────────

    public record AbrirArqueoRequest(
            String usuario,
            String contrasena,
            BigDecimal montoApertura,
            String notas
    ) {}

    public record CerrarArqueoRequest(BigDecimal montoCierre, String notas) {}

    public record PrecierreArqueoRequest(String usuario, String contrasena, BigDecimal montoEfectivo, String notas) {}

    public record EstadoAperturaResponse(
            boolean cajaAbierta,
            long aperturasHoy,
            boolean requiereAdministrador
    ) {}

    public record ArqueoReporteResponse(
            Long arqueoId,
            Long cajeroId,
            String nombreCajero,
            BigDecimal montoApertura,
            BigDecimal totalVentas,
            BigDecimal totalEfectivo,
            BigDecimal totalDigital,
            BigDecimal totalRedondeo,
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
        arqueos.forEach(this::enriquecerArqueoConResumen);
        return ResponseEntity.ok(ApiResponse.ok(arqueos));
    }

    @GetMapping("/activo")
    public ResponseEntity<ApiResponse<ArqueoEntity>> activo(Authentication auth) {
        ArqueoEntity arqueo = (esAdmin(auth)
                ? arqueoRepo.findTopByEstadoOrderByAperturaEnDesc(ArqueoEntity.EstadoArqueo.ABIERTO)
                : arqueoRepo.findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(
                        usuarioId(auth), ArqueoEntity.EstadoArqueo.ABIERTO))
                .orElseThrow(() -> new BusinessException("No hay arqueo abierto", HttpStatus.NOT_FOUND));
        enriquecerArqueoConResumen(arqueo);
        return ResponseEntity.ok(ApiResponse.ok(arqueo));
    }

    @GetMapping("/estado-apertura")
    public ResponseEntity<ApiResponse<EstadoAperturaResponse>> estadoApertura(Authentication auth) {
        if (esAdmin(auth)) {
            throw new BusinessException("El administrador no puede aperturar una caja", HttpStatus.FORBIDDEN);
        }
        Long cajeroId = usuarioId(auth);
        long aperturasHoy = contarAperturasHoy(cajeroId);
        boolean cajaAbierta = arqueoRepo
                .findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(
                        cajeroId, ArqueoEntity.EstadoArqueo.ABIERTO)
                .isPresent();
        return ResponseEntity.ok(ApiResponse.ok(
                new EstadoAperturaResponse(cajaAbierta, aperturasHoy, aperturasHoy > 0)
        ));
    }

    @PostMapping("/abrir")
    public ResponseEntity<ApiResponse<ArqueoEntity>> abrir(@RequestBody AbrirArqueoRequest req, Authentication auth) {
        Long cajeroId = usuarioId(auth);
        // El administrador no apertura caja (solo puede cerrarla manualmente)
        if (esAdmin(auth)) {
            throw new BusinessException("El administrador no puede aperturar una caja", HttpStatus.FORBIDDEN);
        }
        // No puede tener una caja abierta simultáneamente
        arqueoRepo.findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(cajeroId, ArqueoEntity.EstadoArqueo.ABIERTO)
                .ifPresent(a -> { throw new BusinessException("Ya tienes una caja abierta"); });
        // Una sola apertura por día por usuario (esté abierta o ya cerrada)
        LocalDateTime inicioDia = java.time.LocalDate.now().atStartOfDay();
        LocalDateTime finDia = java.time.LocalDate.now().atTime(java.time.LocalTime.MAX);
        long aperturasHoy = arqueoRepo.countByCajeroIdAndAperturaEnBetween(cajeroId, inicioDia, finDia);
        if (req == null) {
            throw new BusinessException("Los datos de apertura son obligatorios", HttpStatus.BAD_REQUEST);
        }
        if (aperturasHoy == 0) {
            validarCredencialesCajero(req.usuario(), req.contrasena(), cajeroId);
        } else {
            validarCredencialesAdministrador(req.usuario(), req.contrasena());
        }
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

    @PostMapping("/{id}/precierre")
    public ResponseEntity<ApiResponse<ArqueoEntity>> registrarPrecierre(@PathVariable Long id,
                                                                        @RequestBody PrecierreArqueoRequest req,
                                                                        Authentication auth) {
        Long usuarioAuthId = usuarioId(auth);
        ArqueoEntity arqueo = arqueoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Arqueo no encontrado", HttpStatus.NOT_FOUND));
        if (!esAdmin(auth) && !arqueo.getCajeroId().equals(usuarioAuthId)) {
            throw new BusinessException("No puedes registrar el pre-cierre de otro cajero", HttpStatus.FORBIDDEN);
        }
        if (arqueo.getEstado() != ArqueoEntity.EstadoArqueo.ABIERTO) {
            throw new BusinessException(
                    "El pre-cierre solo se puede registrar en una caja abierta",
                    HttpStatus.CONFLICT);
        }
        long pagosPendientes = pedidoRepo.countByEstadoIn(List.of(
                PedidoEntity.EstadoPedido.ABIERTO,
                PedidoEntity.EstadoPedido.EN_COCINA,
                PedidoEntity.EstadoPedido.LISTO
        ));
        if (pagosPendientes > 0) {
            throw new BusinessException(
                    "No se puede registrar el pre-cierre porque existen "
                            + pagosPendientes + " pagos pendientes",
                    HttpStatus.CONFLICT
            );
        }
        validarCredencialesPrecierre(req, usuarioAuthId);

        BigDecimal montoEfectivo = req.montoEfectivo() == null ? BigDecimal.ZERO : req.montoEfectivo();
        if (montoEfectivo.signum() < 0) {
            throw new BusinessException("El monto de efectivo debe ser mayor o igual a cero", HttpStatus.BAD_REQUEST);
        }

        ResumenArqueo resumen = calcularResumen(arqueo, LocalDateTime.now());
        arqueo.setMontoCierre(montoEfectivo);
        arqueo.setTotalVentas(resumen.totalVentas());
        arqueo.setTotalEfectivo(resumen.totalEfectivo());
        arqueo.setTotalDigital(resumen.totalDigital());
        arqueo.setTotalRedondeo(resumen.totalRedondeo());
        arqueo.setMontoEsperado(resumen.montoEsperado());
        arqueo.setDiferencia(montoEfectivo.subtract(resumen.montoEsperado()));
        arqueo.setEstado(ArqueoEntity.EstadoArqueo.PRECIERRE);
        if (req.notas() != null) arqueo.setNotas(req.notas());

        ArqueoEntity guardado = arqueoRepo.save(arqueo);
        return ResponseEntity.ok(ApiResponse.ok("Pre-cierre registrado", guardado));
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<ApiResponse<ArqueoEntity>> cerrar(@PathVariable Long id,
                                                             @RequestBody CerrarArqueoRequest req,
                                                             Authentication auth) {
        if (!esAdmin(auth)) {
            throw new BusinessException("Solo un administrador puede cerrar la caja", HttpStatus.FORBIDDEN);
        }
        ArqueoEntity arqueo = arqueoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Arqueo no encontrado", HttpStatus.NOT_FOUND));

        if (arqueo.getEstado() != ArqueoEntity.EstadoArqueo.PRECIERRE) {
            throw new BusinessException(
                    "Solo se puede cerrar una caja con pre-cierre registrado",
                    HttpStatus.CONFLICT);
        }

        LocalDateTime ahora = LocalDateTime.now();
        ResumenArqueo resumen = calcularResumen(arqueo, ahora);

        BigDecimal montoCierre = req == null || req.montoCierre() == null
                ? BigDecimal.ZERO
                : req.montoCierre();
        if (montoCierre.signum() < 0) {
            throw new BusinessException("El monto de cierre debe ser mayor o igual a cero", HttpStatus.BAD_REQUEST);
        }

        arqueo.setMontoCierre(montoCierre);
        arqueo.setTotalVentas(resumen.totalVentas());
        arqueo.setTotalEfectivo(resumen.totalEfectivo());
        arqueo.setTotalDigital(resumen.totalDigital());
        arqueo.setTotalRedondeo(resumen.totalRedondeo());
        arqueo.setMontoEsperado(resumen.montoEsperado());
        arqueo.setDiferencia(montoCierre.subtract(resumen.montoEsperado()));
        arqueo.setCierreEn(ahora);
        arqueo.setEstado(ArqueoEntity.EstadoArqueo.CERRADO);
        if (req != null && req.notas() != null) arqueo.setNotas(req.notas());

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
                resumen.totalDigital(), resumen.totalRedondeo(), resumen.montoEsperado(), montoCierre, diferencia
        )));
    }

    private ResumenArqueo calcularResumen(ArqueoEntity arqueo, LocalDateTime hasta) {
        List<ComprobanteEntity> ventas = comprobanteRepo.findByArqueoCajaIdAndEstado(
                arqueo.getId(), ComprobanteEntity.EstadoComprobante.COMPLETADO);

        BigDecimal totalVentas = ventas.stream()
                .map(ComprobanteEntity::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEfectivo = ventas.stream()
                .filter(c -> c.getMetodoPago() == ComprobanteEntity.MetodoPago.EFECTIVO)
                .map(ComprobanteEntity::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRedondeo = ventas.stream()
                .filter(c -> c.getMetodoPago() == ComprobanteEntity.MetodoPago.EFECTIVO)
                .map(c -> {
                    BigDecimal subtotal = c.getSubtotal() == null ? BigDecimal.ZERO : c.getSubtotal();
                    BigDecimal descuento = c.getDescuento() == null ? BigDecimal.ZERO : c.getDescuento();
                    BigDecimal totalTeorico = subtotal.subtract(descuento);
                    BigDecimal totalReal = c.getTotal() == null ? BigDecimal.ZERO : c.getTotal();
                    BigDecimal redondeo = totalTeorico.subtract(totalReal);
                    return redondeo.signum() > 0 ? redondeo : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDigital = totalVentas.subtract(totalEfectivo);
        BigDecimal montoEsperado = (arqueo.getMontoApertura() == null ? BigDecimal.ZERO : arqueo.getMontoApertura())
                .add(totalEfectivo);
        return new ResumenArqueo(totalVentas, totalEfectivo, totalDigital, totalRedondeo, montoEsperado);
    }

    private record ResumenArqueo(BigDecimal totalVentas, BigDecimal totalEfectivo,
                                 BigDecimal totalDigital, BigDecimal totalRedondeo,
                                 BigDecimal montoEsperado) {}

    private void enriquecerArqueoConResumen(ArqueoEntity arqueo) {
        LocalDateTime fin = arqueo.getCierreEn() != null ? arqueo.getCierreEn() : LocalDateTime.now();
        ResumenArqueo resumen = calcularResumen(arqueo, fin);
        arqueo.setTotalVentas(resumen.totalVentas());
        arqueo.setTotalEfectivo(resumen.totalEfectivo());
        arqueo.setTotalDigital(resumen.totalDigital());
        arqueo.setTotalRedondeo(resumen.totalRedondeo());
        arqueo.setMontoEsperado(resumen.montoEsperado());
    }

    private Long usuarioId(Authentication auth) {
        return Long.parseLong(auth.getName());
    }

    private boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_AD".equals(authority.getAuthority()));
    }

    private void validarCredencialesPrecierre(PrecierreArqueoRequest req, Long usuarioAuthId) {
        if (req == null || req.usuario() == null || req.usuario().isBlank()
                || req.contrasena() == null || req.contrasena().isBlank()) {
            throw new BusinessException("Debes validar las credenciales del cajero actual", HttpStatus.BAD_REQUEST);
        }
        UsuarioEntity usuario = usuarioRepo.findByUsuario(req.usuario().trim())
                .orElseThrow(() -> new BusinessException("Las credenciales no pertenecen al cajero actual", HttpStatus.FORBIDDEN));
        if (!usuario.isActivo()
                || !usuario.getId().equals(usuarioAuthId)
                || !passwordEncoder.matches(req.contrasena(), usuario.getContrasenaHash())) {
            throw new BusinessException("Las credenciales no pertenecen al cajero actual", HttpStatus.FORBIDDEN);
        }
    }

    private long contarAperturasHoy(Long cajeroId) {
        LocalDateTime inicioDia = java.time.LocalDate.now().atStartOfDay();
        LocalDateTime finDia = java.time.LocalDate.now().atTime(java.time.LocalTime.MAX);
        return arqueoRepo.countByCajeroIdAndAperturaEnBetween(cajeroId, inicioDia, finDia);
    }

    private void validarCredencialesCajero(String username, String contrasena, Long cajeroId) {
        UsuarioEntity usuario = buscarUsuarioCredenciales(
                username, contrasena, "Las credenciales no pertenecen al cajero actual");
        if (!usuario.getId().equals(cajeroId) || !"CA".equals(usuario.getRolId())) {
            throw new BusinessException(
                    "Las credenciales no pertenecen al cajero actual",
                    HttpStatus.FORBIDDEN);
        }
    }

    private void validarCredencialesAdministrador(String username, String contrasena) {
        UsuarioEntity usuario = buscarUsuarioCredenciales(
                username, contrasena, "La reapertura requiere credenciales de un administrador");
        if (!"AD".equals(usuario.getRolId())) {
            throw new BusinessException(
                    "La reapertura requiere credenciales de un administrador",
                    HttpStatus.FORBIDDEN);
        }
    }

    private UsuarioEntity buscarUsuarioCredenciales(String username,
                                                     String contrasena,
                                                     String mensajeError) {
        if (username == null || username.isBlank() || contrasena == null || contrasena.isBlank()) {
            throw new BusinessException(mensajeError, HttpStatus.BAD_REQUEST);
        }
        UsuarioEntity usuario = usuarioRepo.findByUsuario(username.trim())
                .orElseThrow(() -> new BusinessException(mensajeError, HttpStatus.FORBIDDEN));
        if (!usuario.isActivo() || !passwordEncoder.matches(contrasena, usuario.getContrasenaHash())) {
            throw new BusinessException(mensajeError, HttpStatus.FORBIDDEN);
        }
        return usuario;
    }
}
