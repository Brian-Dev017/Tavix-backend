package com.restaurante.modules.reportes.infrastructure.web;

import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.DatosComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.DatosComprobanteJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigEntity;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.shared.response.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reportes")
public class ReportesController {

    private final ComprobanteJpaRepo comprobanteRepo;
    private final PedidoJpaRepo pedidoRepo;
    private final DetallePedidoJpaRepo detallePedidoRepo;
    private final ProductoJpaRepo productoRepo;
    private final DatosComprobanteJpaRepo datosComprobanteRepo;
    private final NegocioConfigJpaRepo negocioConfigRepo;

    @PersistenceContext
    private EntityManager em;

    public ReportesController(ComprobanteJpaRepo comprobanteRepo,
                               PedidoJpaRepo pedidoRepo,
                               DetallePedidoJpaRepo detallePedidoRepo,
                               ProductoJpaRepo productoRepo,
                               DatosComprobanteJpaRepo datosComprobanteRepo,
                               NegocioConfigJpaRepo negocioConfigRepo) {
        this.comprobanteRepo = comprobanteRepo;
        this.pedidoRepo = pedidoRepo;
        this.detallePedidoRepo = detallePedidoRepo;
        this.productoRepo = productoRepo;
        this.datosComprobanteRepo = datosComprobanteRepo;
        this.negocioConfigRepo = negocioConfigRepo;
    }

    // ──────────────── DTOs ────────────────

    public record VentaPorDia(String fecha, BigDecimal total, long cantidad) {}

    public record VentaPorMetodo(String metodo, BigDecimal total, long cantidad) {}

    public record ReporteVentasResponse(
            BigDecimal totalVentas,
            long cantidadComprobantes,
            BigDecimal promedioVenta,
            List<VentaPorDia> ventasPorDia,
            List<VentaPorMetodo> ventasPorMetodo
    ) {}

    public record ComprobanteResumen(
            Long id,
            Long pedidoId,
            String tipoComprobante,
            String serie,
            Integer numero,
            String metodoPago,
            BigDecimal total,
            String estado,
            LocalDateTime pagadoEn,
            LocalDateTime creadoEn
    ) {}

    public record PlatoVendido(Long productoId, String nombre, long cantidad) {}

    public record ItemDetalleReporte(String producto, int cantidad, BigDecimal precio, BigDecimal subtotal,
                                     String estado, String observaciones) {}

    public record PedidoDetalleReporte(
            Long comprobanteId,
            Long pedidoId,
            String tipoComprobante,
            String serie,
            Integer numero,
            String metodoPago,
            BigDecimal subtotal,
            BigDecimal igv,
            BigDecimal descuento,
            BigDecimal total,
            BigDecimal efectivoRecibido,
            BigDecimal vuelto,
            LocalDateTime pagadoEn,
            String clienteDocumento,
            String clienteNombre,
            String clienteDireccion,
            String negocioNombre,
            String negocioRuc,
            String negocioDireccion,
            String negocioLogoUrl,
            List<ItemDetalleReporte> items
    ) {}

    public record VentaPorCategoria(String categoria, BigDecimal total, long cantidad) {}

    public record DashboardResponse(
            BigDecimal ventasHoy,
            long pedidosHoy,
            List<VentaPorMetodo> ventasPorMetodo,
            List<PlatoVendido> platosVendidos,
            List<VentaPorDia> ventasPorDia,
            List<VentaPorCategoria> ventasPorCategoria
    ) {}

    // ──────────────── GET /ventas ────────────────

    @GetMapping("/ventas")
    public ResponseEntity<ApiResponse<ReporteVentasResponse>> ventas(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {

        LocalDate desdeDate = desde != null ? LocalDate.parse(desde.substring(0, 10)) : LocalDate.now();
        LocalDate hastaDate = hasta != null ? LocalDate.parse(hasta.substring(0, 10)) : LocalDate.now();
        LocalDateTime desdeTime = desdeDate.atStartOfDay();
        LocalDateTime hastaTime = hastaDate.atTime(LocalTime.MAX);

        List<ComprobanteEntity> completados = comprobanteRepo.findAll().stream()
                .filter(c -> c.getEstado() == ComprobanteEntity.EstadoComprobante.COMPLETADO)
                .filter(c -> c.getPagadoEn() != null
                        && !c.getPagadoEn().isBefore(desdeTime)
                        && !c.getPagadoEn().isAfter(hastaTime))
                .toList();

        BigDecimal totalVentas = completados.stream()
                .map(ComprobanteEntity::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long cantidad = completados.size();

        BigDecimal promedio = cantidad > 0
                ? totalVentas.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<VentaPorDia> ventasPorDia = completados.stream()
                .collect(Collectors.groupingBy(c -> c.getPagadoEn().toLocalDate()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new VentaPorDia(
                        e.getKey().format(dayFmt),
                        e.getValue().stream().map(ComprobanteEntity::getTotal)
                                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().size()
                ))
                .toList();

        List<VentaPorMetodo> ventasPorMetodo = completados.stream()
                .collect(Collectors.groupingBy(c -> c.getMetodoPago().name()))
                .entrySet().stream()
                .map(e -> new VentaPorMetodo(
                        e.getKey(),
                        e.getValue().stream().map(ComprobanteEntity::getTotal)
                                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().size()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(
                new ReporteVentasResponse(totalVentas, cantidad, promedio, ventasPorDia, ventasPorMetodo)));
    }

    // ──────────────── GET /historial ────────────────

    @GetMapping("/historial")
    public ResponseEntity<ApiResponse<Page<ComprobanteResumen>>> historial(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String estado) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "creadoEn"));

        Page<ComprobanteEntity> pageResult;
        if (estado != null && !estado.isBlank()) {
            ComprobanteEntity.EstadoComprobante filtro =
                    ComprobanteEntity.EstadoComprobante.valueOf(estado.toUpperCase());
            pageResult = comprobanteRepo.findByEstado(filtro, pageable);
        } else {
            pageResult = comprobanteRepo.findAll(pageable);
        }

        Page<ComprobanteResumen> resultado = pageResult
                .map(c -> new ComprobanteResumen(
                        c.getId(),
                        c.getPedidoId(),
                        nombreTipoComprobante(c.getTipoComprobanteId()),
                        c.getSerie(),
                        c.getNumero(),
                        c.getMetodoPago() != null ? c.getMetodoPago().name() : null,
                        c.getTotal(),
                        c.getEstado() != null ? c.getEstado().name() : null,
                        c.getPagadoEn(),
                        c.getCreadoEn()
                ));

        return ResponseEntity.ok(ApiResponse.ok(resultado));
    }

    // ──────────────── GET /dashboard ────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        LocalDate desdeDate = desde != null ? LocalDate.parse(desde.substring(0, 10)) : LocalDate.now();
        LocalDate hastaDate = hasta != null ? LocalDate.parse(hasta.substring(0, 10)) : LocalDate.now();
        LocalDateTime inicioDia = desdeDate.atStartOfDay();
        LocalDateTime finDia = hastaDate.atTime(LocalTime.MAX);

        // Ventas hoy
        List<ComprobanteEntity> comprobantesHoy = comprobanteRepo.findAll().stream()
                .filter(c -> c.getEstado() == ComprobanteEntity.EstadoComprobante.COMPLETADO)
                .filter(c -> c.getPagadoEn() != null
                        && !c.getPagadoEn().isBefore(inicioDia)
                        && !c.getPagadoEn().isAfter(finDia))
                .toList();

        BigDecimal ventasHoy = comprobantesHoy.stream()
                .map(ComprobanteEntity::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Pedidos hoy
        List<PedidoEntity> pedidosHoy = pedidoRepo.findAll().stream()
                .filter(p -> p.getCreadoEn() != null
                        && !p.getCreadoEn().isBefore(inicioDia)
                        && !p.getCreadoEn().isAfter(finDia))
                .toList();

        long cantidadPedidosHoy = pedidosHoy.size();

        // Ventas por método hoy
        List<VentaPorMetodo> ventasPorMetodo = comprobantesHoy.stream()
                .collect(Collectors.groupingBy(c -> c.getMetodoPago().name()))
                .entrySet().stream()
                .map(e -> new VentaPorMetodo(
                        e.getKey(),
                        e.getValue().stream().map(ComprobanteEntity::getTotal)
                                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().size()
                ))
                .toList();

        // Top 5 platos vendidos hoy
        Set<Long> pedidoIdsHoy = pedidosHoy.stream()
                .map(PedidoEntity::getId)
                .collect(Collectors.toSet());

        // Mapa productoId -> nombre (cargado lazy desde ProductoJpaRepo)
        Map<Long, String> nombresPorProducto = new HashMap<>();

        List<DetallePedidoEntity> detallesHoy = pedidoIdsHoy.isEmpty()
                ? List.of()
                : pedidoIdsHoy.stream()
                        .flatMap(pid -> detallePedidoRepo.findByPedidoId(pid).stream())
                        .toList();

        List<PlatoVendido> platosVendidos = detallesHoy.stream()
                .collect(Collectors.groupingBy(DetallePedidoEntity::getProductoId,
                        Collectors.summingLong(DetallePedidoEntity::getCantidad)))
                .entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Long productoId = e.getKey();
                    String nombre = nombresPorProducto.computeIfAbsent(productoId, pid ->
                            productoRepo.findById(pid)
                                    .map(p -> p.getNombre())
                                    .orElse("Producto #" + pid));
                    return new PlatoVendido(productoId, nombre, e.getValue());
                })
                .toList();

        // Serie diaria de ventas (para gráfico de barras real por día)
        List<VentaPorDia> ventasPorDia = comprobantesHoy.stream()
                .collect(Collectors.groupingBy(c -> c.getPagadoEn().toLocalDate()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new VentaPorDia(
                        e.getKey().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        e.getValue().stream().map(ComprobanteEntity::getTotal)
                                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().size()))
                .toList();

        // Ventas por categoría (gráfico de torta/barras por categoría)
        @SuppressWarnings("unchecked")
        List<Object[]> filasCat = em.createNativeQuery(
                "SELECT COALESCE(c.nombre,'Sin categoria') categoria, " +
                "ROUND(SUM(dp.cantidad*dp.precio_unitario),2) total, SUM(dp.cantidad) cantidad " +
                "FROM detalle_pedido dp " +
                "JOIN comprobante_venta cv ON cv.pedido_id=dp.pedido_id AND cv.estado='COMPLETADO' " +
                "JOIN producto pr ON pr.id=dp.producto_id " +
                "LEFT JOIN categoria c ON c.id=pr.categoria_id " +
                "WHERE dp.estado<>'CANCELADO' AND cv.pagado_en BETWEEN ?1 AND ?2 " +
                "GROUP BY c.nombre ORDER BY total DESC")
                .setParameter(1, inicioDia)
                .setParameter(2, finDia)
                .getResultList();
        List<VentaPorCategoria> ventasPorCategoria = filasCat.stream()
                .map(r -> new VentaPorCategoria(
                        (String) r[0],
                        r[1] == null ? BigDecimal.ZERO : (BigDecimal) r[1],
                        r[2] == null ? 0L : ((Number) r[2]).longValue()))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(
                new DashboardResponse(ventasHoy, cantidadPedidosHoy, ventasPorMetodo, platosVendidos,
                        ventasPorDia, ventasPorCategoria)));
    }

    @GetMapping("/historial/{comprobanteId}/detalle")
    public ResponseEntity<ApiResponse<PedidoDetalleReporte>> detalle(@PathVariable Long comprobanteId) {
        ComprobanteEntity comp = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new com.restaurante.shared.exception.BusinessException(
                        "Comprobante no encontrado", org.springframework.http.HttpStatus.NOT_FOUND));
        DatosComprobanteEntity datos = comp.getDatosComprobanteId() == null
                ? null
                : datosComprobanteRepo.findById(comp.getDatosComprobanteId()).orElse(null);
        NegocioConfigEntity negocio = negocioConfigRepo.findById(1L).orElse(null);
        List<ItemDetalleReporte> items = detallePedidoRepo.findByPedidoId(comp.getPedidoId()).stream()
                .map(d -> {
                    String nombre = productoRepo.findById(d.getProductoId())
                            .map(p -> p.getNombre())
                            .orElse("Producto #" + d.getProductoId());
                    BigDecimal subtotal = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
                    return new ItemDetalleReporte(nombre, d.getCantidad(), d.getPrecioUnitario(), subtotal,
                            d.getEstado().name(), d.getObservaciones());
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(new PedidoDetalleReporte(
                comp.getId(), comp.getPedidoId(), nombreTipoComprobante(comp.getTipoComprobanteId()),
                comp.getSerie(), comp.getNumero(), comp.getMetodoPago().name(), comp.getSubtotal(),
                comp.getIgv(), comp.getDescuento(), comp.getTotal(),
                comp.getEfectivoRecibido(), comp.getVuelto(), comp.getPagadoEn(),
                datos != null ? datos.getRucDni() : null,
                datos != null ? datos.getRazonSocial() : null,
                datos != null ? datos.getDireccion() : null,
                negocio != null ? negocio.getNombreComercial() : null,
                negocio != null ? negocio.getRucNegocio() : null,
                negocio != null ? negocio.getDireccion() : null,
                negocio != null ? negocio.getLogoUrl() : null,
                items
        )));
    }

    private String nombreTipoComprobante(String tipo) {
        return switch (tipo) {
            case "B" -> "Boleta";
            case "F" -> "Factura";
            default -> "Ticket";
        };
    }
}
