package com.restaurante.modules.reportes.infrastructure.web;

import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.DatosComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.DatosComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigEntity;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.shared.response.ApiResponse;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.modules.reportes.infrastructure.excel.ExcelReportBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reportes")
public class ReportesController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ComprobanteJpaRepo comprobanteRepo;
    private final PedidoJpaRepo pedidoRepo;
    private final DetallePedidoJpaRepo detallePedidoRepo;
    private final ProductoJpaRepo productoRepo;
    private final DatosComprobanteJpaRepo datosComprobanteRepo;
    private final NegocioConfigJpaRepo negocioConfigRepo;
    private final ArqueoJpaRepo arqueoRepo;

    @PersistenceContext
    private EntityManager em;

    public ReportesController(ComprobanteJpaRepo comprobanteRepo,
                               PedidoJpaRepo pedidoRepo,
                               DetallePedidoJpaRepo detallePedidoRepo,
                               ProductoJpaRepo productoRepo,
                               DatosComprobanteJpaRepo datosComprobanteRepo,
                               NegocioConfigJpaRepo negocioConfigRepo,
                               ArqueoJpaRepo arqueoRepo) {
        this.comprobanteRepo = comprobanteRepo;
        this.pedidoRepo = pedidoRepo;
        this.detallePedidoRepo = detallePedidoRepo;
        this.productoRepo = productoRepo;
        this.datosComprobanteRepo = datosComprobanteRepo;
        this.negocioConfigRepo = negocioConfigRepo;
        this.arqueoRepo = arqueoRepo;
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
            BigDecimal negocioIgvPorcentaje,
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

    private record DateRange(LocalDate desde, LocalDate hasta) {}

    // ──────────────── GET /ventas ────────────────

    @GetMapping("/ventas")
    public ResponseEntity<ApiResponse<ReporteVentasResponse>> ventas(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {

        DateRange range = parseDateRange(desde, hasta);
        LocalDate desdeDate = range.desde();
        LocalDate hastaDate = range.hasta();
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
        DateRange range = parseDateRange(desde, hasta);
        LocalDate desdeDate = range.desde();
        LocalDate hastaDate = range.hasta();
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
                negocio != null ? negocio.getIgvPorcentaje() : null,
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

    @GetMapping("/dashboard/excel")
    public ResponseEntity<byte[]> exportarDashboardExcel(
            @RequestParam String desde,
            @RequestParam String hasta) {
        DateRange range = parseDateRange(desde, hasta);
        DashboardResponse data = Objects.requireNonNull(
                dashboard(desde, hasta).getBody()
        ).data();

        try (ExcelReportBuilder builder = new ExcelReportBuilder()) {
            Map<String, Object> metadata = rangeMetadata(range);
            builder.createSummarySheet(
                    businessName(),
                    "REPORTE DE DASHBOARD",
                    metadata,
                    linkedMap(
                            "Ventas", data.ventasHoy(),
                            "Pedidos", data.pedidosHoy()
                    )
            );
            builder.createTableSheet(
                    "Ventas por día",
                    "VENTAS POR DÍA",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("Fecha", ExcelReportBuilder.ValueType.TEXT, 16),
                            ExcelReportBuilder.column("Comprobantes", ExcelReportBuilder.ValueType.INTEGER, 16),
                            ExcelReportBuilder.column("Total", ExcelReportBuilder.ValueType.MONEY, 18)
                    ),
                    data.ventasPorDia().stream()
                            .map(row -> new Object[]{row.fecha(), row.cantidad(), row.total()})
                            .toList(),
                    chart(ChartTypes.BAR, "Ventas por día", "Total vendido (S/)", 0, 2)
            );
            builder.createTableSheet(
                    "Métodos de pago",
                    "MÉTODOS DE PAGO",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("Método", ExcelReportBuilder.ValueType.TEXT, 20),
                            ExcelReportBuilder.column("Transacciones", ExcelReportBuilder.ValueType.INTEGER, 16),
                            ExcelReportBuilder.column("Total", ExcelReportBuilder.ValueType.MONEY, 18)
                    ),
                    data.ventasPorMetodo().stream()
                            .map(row -> new Object[]{row.metodo(), row.cantidad(), row.total()})
                            .toList(),
                    chart(ChartTypes.PIE, "Distribución de pagos", "Total por método", 0, 2)
            );
            builder.createTableSheet(
                    "Categorías",
                    "VENTAS POR CATEGORÍA",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("Categoría", ExcelReportBuilder.ValueType.TEXT, 24),
                            ExcelReportBuilder.column("Cantidad", ExcelReportBuilder.ValueType.INTEGER, 14),
                            ExcelReportBuilder.column("Total", ExcelReportBuilder.ValueType.MONEY, 18)
                    ),
                    data.ventasPorCategoria().stream()
                            .map(row -> new Object[]{row.categoria(), row.cantidad(), row.total()})
                            .toList(),
                    chart(ChartTypes.PIE, "Ventas por categoría", "Total por categoría", 0, 2)
            );
            builder.createTableSheet(
                    "Platos",
                    "PLATOS MÁS VENDIDOS",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("Producto", ExcelReportBuilder.ValueType.TEXT, 32),
                            ExcelReportBuilder.column("Cantidad", ExcelReportBuilder.ValueType.INTEGER, 14)
                    ),
                    data.platosVendidos().stream()
                            .map(row -> new Object[]{row.nombre(), row.cantidad()})
                            .toList(),
                    chart(ChartTypes.BAR, "Platos más vendidos", "Unidades vendidas", 0, 1)
            );
            return excelResponse(
                    builder.toBytes(),
                    "dashboard-" + range.desde() + "-" + range.hasta() + ".xlsx"
            );
        } catch (IOException error) {
            throw new BusinessException(
                    "No se pudo generar el Excel del dashboard",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @GetMapping("/ventas/excel")
    public ResponseEntity<byte[]> exportarVentasExcel(
            @RequestParam String desde,
            @RequestParam String hasta) {
        DateRange range = parseDateRange(desde, hasta);
        ReporteVentasResponse data = Objects.requireNonNull(
                ventas(desde, hasta).getBody()
        ).data();

        try (ExcelReportBuilder builder = new ExcelReportBuilder()) {
            Map<String, Object> metadata = rangeMetadata(range);
            builder.createSummarySheet(
                    businessName(),
                    "REPORTE DE VENTAS",
                    metadata,
                    linkedMap(
                            "Total de ventas", data.totalVentas(),
                            "Comprobantes", data.cantidadComprobantes(),
                            "Ticket promedio", data.promedioVenta()
                    )
            );
            builder.createTableSheet(
                    "Ventas por día",
                    "VENTAS POR DÍA",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("Fecha", ExcelReportBuilder.ValueType.TEXT, 16),
                            ExcelReportBuilder.column("Comprobantes", ExcelReportBuilder.ValueType.INTEGER, 16),
                            ExcelReportBuilder.column("Total", ExcelReportBuilder.ValueType.MONEY, 18)
                    ),
                    data.ventasPorDia().stream()
                            .map(row -> new Object[]{row.fecha(), row.cantidad(), row.total()})
                            .toList(),
                    chart(ChartTypes.BAR, "Ventas por día", "Total vendido (S/)", 0, 2)
            );
            builder.createTableSheet(
                    "Métodos de pago",
                    "MÉTODOS DE PAGO",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("Método", ExcelReportBuilder.ValueType.TEXT, 20),
                            ExcelReportBuilder.column("Transacciones", ExcelReportBuilder.ValueType.INTEGER, 16),
                            ExcelReportBuilder.column("Total", ExcelReportBuilder.ValueType.MONEY, 18)
                    ),
                    data.ventasPorMetodo().stream()
                            .map(row -> new Object[]{row.metodo(), row.cantidad(), row.total()})
                            .toList(),
                    chart(ChartTypes.PIE, "Distribución de pagos", "Total por método", 0, 2)
            );
            return excelResponse(
                    builder.toBytes(),
                    "ventas-" + range.desde() + "-" + range.hasta() + ".xlsx"
            );
        } catch (IOException error) {
            throw new BusinessException(
                    "No se pudo generar el Excel de ventas",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @GetMapping("/pagos/excel")
    public ResponseEntity<byte[]> exportarPagosExcel(
            @RequestParam String desde,
            @RequestParam String hasta) {
        DateRange range = parseDateRange(desde, hasta);
        ReporteVentasResponse data = Objects.requireNonNull(
                ventas(desde, hasta).getBody()).data();
        BigDecimal total = data.ventasPorMetodo().stream()
                .map(VentaPorMetodo::total)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long transacciones = data.ventasPorMetodo().stream()
                .mapToLong(VentaPorMetodo::cantidad)
                .sum();
        String principal = data.ventasPorMetodo().stream()
                .max(Comparator.comparing(VentaPorMetodo::total))
                .map(VentaPorMetodo::metodo)
                .orElse("Sin datos");

        try (ExcelReportBuilder builder = new ExcelReportBuilder()) {
            Map<String, Object> metadata = rangeMetadata(range);
            builder.createSummarySheet(
                    businessName(),
                    "REPORTE DE PAGOS",
                    metadata,
                    linkedMap(
                            "Venta total", total,
                            "Transacciones", transacciones,
                            "Método con mayor importe", principal
                    )
            );
            builder.createTableSheet(
                    "Métodos de pago",
                    "MÉTODOS DE PAGO",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("Método", ExcelReportBuilder.ValueType.TEXT, 20),
                            ExcelReportBuilder.column("Transacciones", ExcelReportBuilder.ValueType.INTEGER, 16),
                            ExcelReportBuilder.column("Total", ExcelReportBuilder.ValueType.MONEY, 18),
                            ExcelReportBuilder.column("Participación", ExcelReportBuilder.ValueType.PERCENT, 16)
                    ),
                    data.ventasPorMetodo().stream()
                            .map(row -> new Object[]{
                                    row.metodo(),
                                    row.cantidad(),
                                    row.total(),
                                    total.signum() == 0
                                            ? 0d
                                            : row.total().divide(total, 6, RoundingMode.HALF_UP).doubleValue()
                            })
                            .toList(),
                    chart(ChartTypes.PIE, "Distribución de pagos", "Total por método", 0, 2)
            );
            return excelResponse(
                    builder.toBytes(),
                    "pagos-" + range.desde() + "-" + range.hasta() + ".xlsx"
            );
        } catch (IOException error) {
            throw new BusinessException(
                    "No se pudo generar el Excel de pagos",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @GetMapping("/arqueos/excel")
    public ResponseEntity<byte[]> exportarArqueosExcel() {
        List<ArqueoEntity> arqueos = arqueoRepo.findAll().stream()
                .sorted(Comparator.comparing(
                        ArqueoEntity::getAperturaEn,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .toList();
        long abiertos = countArqueos(arqueos, ArqueoEntity.EstadoArqueo.ABIERTO);
        long precierres = countArqueos(arqueos, ArqueoEntity.EstadoArqueo.PRECIERRE);
        long cerrados = countArqueos(arqueos, ArqueoEntity.EstadoArqueo.CERRADO);
        BigDecimal totalVentas = sumArqueos(arqueos, ArqueoEntity::getTotalVentas);
        BigDecimal diferencia = sumArqueos(arqueos, ArqueoEntity::getDiferencia);
        Map<String, Object> metadata = generatedMetadata();

        try (ExcelReportBuilder builder = new ExcelReportBuilder()) {
            builder.createSummarySheet(
                    businessName(),
                    "REPORTE DE ARQUEOS",
                    metadata,
                    linkedMap(
                            "Cantidad total", arqueos.size(),
                            "Abiertos", abiertos,
                            "Pre-cierres", precierres,
                            "Cerrados", cerrados,
                            "Total de ventas", totalVentas,
                            "Diferencia acumulada", diferencia
                    )
            );
            builder.createTableSheet(
                    "Arqueos",
                    "DETALLE DE ARQUEOS",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("ID", ExcelReportBuilder.ValueType.INTEGER, 10),
                            ExcelReportBuilder.column("Cajero", ExcelReportBuilder.ValueType.TEXT, 28),
                            ExcelReportBuilder.column("Apertura", ExcelReportBuilder.ValueType.DATE_TIME, 20),
                            ExcelReportBuilder.column("Cierre", ExcelReportBuilder.ValueType.DATE_TIME, 20),
                            ExcelReportBuilder.column("Monto apertura", ExcelReportBuilder.ValueType.MONEY, 18),
                            ExcelReportBuilder.column("Monto cierre", ExcelReportBuilder.ValueType.MONEY, 18),
                            ExcelReportBuilder.column("Total ventas", ExcelReportBuilder.ValueType.MONEY, 18),
                            ExcelReportBuilder.column("Efectivo", ExcelReportBuilder.ValueType.MONEY, 18),
                            ExcelReportBuilder.column("Digital", ExcelReportBuilder.ValueType.MONEY, 18),
                            ExcelReportBuilder.column("Redondeo", ExcelReportBuilder.ValueType.MONEY, 16),
                            ExcelReportBuilder.column("Esperado", ExcelReportBuilder.ValueType.MONEY, 18),
                            ExcelReportBuilder.column("Diferencia", ExcelReportBuilder.ValueType.MONEY, 18),
                            ExcelReportBuilder.column("Estado", ExcelReportBuilder.ValueType.TEXT, 15),
                            ExcelReportBuilder.column("Notas", ExcelReportBuilder.ValueType.TEXT, 32)
                    ),
                    arqueos.stream().map(item -> new Object[]{
                            item.getId(),
                            item.getNombreCajero(),
                            item.getAperturaEn(),
                            item.getCierreEn(),
                            item.getMontoApertura(),
                            item.getMontoCierre(),
                            item.getTotalVentas(),
                            item.getTotalEfectivo(),
                            item.getTotalDigital(),
                            item.getTotalRedondeo(),
                            item.getMontoEsperado(),
                            item.getDiferencia(),
                            item.getEstado() == null ? null : item.getEstado().name(),
                            item.getNotas()
                    }).toList(),
                    chart(ChartTypes.BAR, "Ventas por cajero", "Total de ventas (S/)", 1, 6)
            );
            builder.createTableSheet(
                    "Estados",
                    "ARQUEOS POR ESTADO",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("Estado", ExcelReportBuilder.ValueType.TEXT, 20),
                            ExcelReportBuilder.column("Cantidad", ExcelReportBuilder.ValueType.INTEGER, 16)
                    ),
                    List.of(
                            new Object[]{"ABIERTO", abiertos},
                            new Object[]{"PRECIERRE", precierres},
                            new Object[]{"CERRADO", cerrados}
                    ),
                    chart(ChartTypes.PIE, "Distribución por estado", "Cantidad de arqueos", 0, 1)
            );
            return excelResponse(
                    builder.toBytes(),
                    "arqueos-" + LocalDate.now() + ".xlsx"
            );
        } catch (IOException error) {
            throw new BusinessException(
                    "No se pudo generar el Excel de arqueos",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @GetMapping("/historial/excel")
    public ResponseEntity<byte[]> exportarHistorialExcel(
            @RequestParam(required = false) String estado) {
        ComprobanteEntity.EstadoComprobante filtro = parseEstado(estado);
        List<ComprobanteEntity> comprobantes = comprobanteRepo.findAll().stream()
                .filter(item -> filtro == null || item.getEstado() == filtro)
                .sorted(Comparator.comparing(
                        ComprobanteEntity::getCreadoEn,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .toList();

        BigDecimal total = comprobantes.stream()
                .map(ComprobanteEntity::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long completados = countComprobantes(comprobantes, ComprobanteEntity.EstadoComprobante.COMPLETADO);
        long anulados = countComprobantes(comprobantes, ComprobanteEntity.EstadoComprobante.ANULADO);
        long pendientes = countComprobantes(comprobantes, ComprobanteEntity.EstadoComprobante.PENDIENTE);
        Map<String, Object> metadata = generatedMetadata();
        metadata.put("Filtro", filtro == null ? "TODOS" : filtro.name());

        Map<String, Aggregate> byMethod = aggregate(
                comprobantes,
                item -> item.getMetodoPago() == null ? "SIN MÉTODO" : item.getMetodoPago().name());
        Map<String, Aggregate> byState = aggregate(
                comprobantes,
                item -> item.getEstado() == null ? "SIN ESTADO" : item.getEstado().name());
        Map<String, Aggregate> byType = aggregate(
                comprobantes,
                item -> nombreTipoComprobante(item.getTipoComprobanteId()));

        try (ExcelReportBuilder builder = new ExcelReportBuilder()) {
            builder.createSummarySheet(
                    businessName(),
                    "HISTORIAL DE COMPROBANTES",
                    metadata,
                    linkedMap(
                            "Cantidad de comprobantes", comprobantes.size(),
                            "Total monetario", total,
                            "Completados", completados,
                            "Anulados", anulados,
                            "Pendientes", pendientes
                    )
            );
            builder.createTableSheet(
                    "Comprobantes",
                    "DETALLE DE COMPROBANTES",
                    metadata,
                    List.of(
                            ExcelReportBuilder.column("ID", ExcelReportBuilder.ValueType.INTEGER, 10),
                            ExcelReportBuilder.column("Pedido", ExcelReportBuilder.ValueType.INTEGER, 12),
                            ExcelReportBuilder.column("Tipo", ExcelReportBuilder.ValueType.TEXT, 16),
                            ExcelReportBuilder.column("Serie", ExcelReportBuilder.ValueType.TEXT, 12),
                            ExcelReportBuilder.column("Número", ExcelReportBuilder.ValueType.INTEGER, 12),
                            ExcelReportBuilder.column("Método", ExcelReportBuilder.ValueType.TEXT, 18),
                            ExcelReportBuilder.column("Total", ExcelReportBuilder.ValueType.MONEY, 18),
                            ExcelReportBuilder.column("Estado", ExcelReportBuilder.ValueType.TEXT, 16),
                            ExcelReportBuilder.column("Pagado", ExcelReportBuilder.ValueType.DATE_TIME, 20),
                            ExcelReportBuilder.column("Creado", ExcelReportBuilder.ValueType.DATE_TIME, 20)
                    ),
                    comprobantes.stream().map(item -> new Object[]{
                            item.getId(),
                            item.getPedidoId(),
                            nombreTipoComprobante(item.getTipoComprobanteId()),
                            item.getSerie(),
                            item.getNumero(),
                            item.getMetodoPago() == null ? null : item.getMetodoPago().name(),
                            item.getTotal(),
                            item.getEstado() == null ? null : item.getEstado().name(),
                            item.getPagadoEn(),
                            item.getCreadoEn()
                    }).toList(),
                    null
            );
            addAggregateSheet(builder, "Métodos de pago", "MÉTODOS DE PAGO",
                    metadata, byMethod, ChartTypes.PIE, "Comprobantes por método");
            addAggregateSheet(builder, "Estados", "COMPROBANTES POR ESTADO",
                    metadata, byState, ChartTypes.PIE, "Comprobantes por estado");
            addAggregateSheet(builder, "Tipos", "COMPROBANTES POR TIPO",
                    metadata, byType, ChartTypes.BAR, "Comprobantes por tipo");
            String suffix = filtro == null ? "todos" : filtro.name().toLowerCase(Locale.ROOT);
            return excelResponse(
                    builder.toBytes(),
                    "historial-comprobantes-" + suffix + "-" + LocalDate.now() + ".xlsx"
            );
        } catch (IOException error) {
            throw new BusinessException(
                    "No se pudo generar el Excel del historial",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private DateRange parseDateRange(String desde, String hasta) {
        try {
            LocalDate start = desde == null || desde.isBlank()
                    ? LocalDate.now()
                    : LocalDate.parse(desde.substring(0, 10));
            LocalDate end = hasta == null || hasta.isBlank()
                    ? LocalDate.now()
                    : LocalDate.parse(hasta.substring(0, 10));
            LocalDate today = LocalDate.now();
            if (start.isAfter(end)) {
                throw new BusinessException(
                        "La fecha desde no puede ser mayor que la fecha hasta",
                        HttpStatus.BAD_REQUEST
                );
            }
            if (start.isAfter(today) || end.isAfter(today)) {
                throw new BusinessException(
                        "La fecha de consulta no puede ser posterior a hoy",
                        HttpStatus.BAD_REQUEST
                );
            }
            return new DateRange(start, end);
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new BusinessException(
                    "El rango de fechas no tiene un formato válido",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private Map<String, Object> rangeMetadata(DateRange range) {
        Map<String, Object> metadata = generatedMetadata();
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("Desde", range.desde().toString());
        ordered.put("Hasta", range.hasta().toString());
        ordered.putAll(metadata);
        return ordered;
    }

    private Map<String, Object> generatedMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("Generado", LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        metadata.put("Moneda", "Soles (PEN)");
        return metadata;
    }

    private String businessName() {
        return negocioConfigRepo.findById(1L)
                .map(NegocioConfigEntity::getNombreComercial)
                .filter(value -> !value.isBlank())
                .orElse("LA FLOR DEL TUMBO");
    }

    private ExcelReportBuilder.ChartSpec chart(
            ChartTypes type,
            String title,
            String series,
            int categoryColumn,
            int valueColumn
    ) {
        return new ExcelReportBuilder.ChartSpec(
                type, title, series, categoryColumn, valueColumn);
    }

    private Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index].toString(), values[index + 1]);
        }
        return result;
    }

    private long countArqueos(
            List<ArqueoEntity> arqueos,
            ArqueoEntity.EstadoArqueo estado
    ) {
        return arqueos.stream().filter(item -> item.getEstado() == estado).count();
    }

    private BigDecimal sumArqueos(
            List<ArqueoEntity> arqueos,
            Function<ArqueoEntity, BigDecimal> getter
    ) {
        return arqueos.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ComprobanteEntity.EstadoComprobante parseEstado(String estado) {
        if (estado == null || estado.isBlank()) return null;
        try {
            return ComprobanteEntity.EstadoComprobante.valueOf(
                    estado.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new BusinessException(
                    "El estado de comprobante no es válido",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private long countComprobantes(
            List<ComprobanteEntity> comprobantes,
            ComprobanteEntity.EstadoComprobante estado
    ) {
        return comprobantes.stream().filter(item -> item.getEstado() == estado).count();
    }

    private Map<String, Aggregate> aggregate(
            List<ComprobanteEntity> comprobantes,
            Function<ComprobanteEntity, String> classifier
    ) {
        Map<String, Aggregate> result = new LinkedHashMap<>();
        for (ComprobanteEntity item : comprobantes) {
            String key = classifier.apply(item);
            Aggregate current = result.getOrDefault(
                    key, new Aggregate(0, BigDecimal.ZERO));
            result.put(key, new Aggregate(
                    current.count() + 1,
                    current.total().add(
                            item.getTotal() == null ? BigDecimal.ZERO : item.getTotal())
            ));
        }
        return result;
    }

    private void addAggregateSheet(
            ExcelReportBuilder builder,
            String sheetName,
            String title,
            Map<String, Object> metadata,
            Map<String, Aggregate> aggregates,
            ChartTypes chartType,
            String chartTitle
    ) {
        builder.createTableSheet(
                sheetName,
                title,
                metadata,
                List.of(
                        ExcelReportBuilder.column("Categoría", ExcelReportBuilder.ValueType.TEXT, 22),
                        ExcelReportBuilder.column("Cantidad", ExcelReportBuilder.ValueType.INTEGER, 16),
                        ExcelReportBuilder.column("Total", ExcelReportBuilder.ValueType.MONEY, 18)
                ),
                aggregates.entrySet().stream()
                        .map(entry -> new Object[]{
                                entry.getKey(),
                                entry.getValue().count(),
                                entry.getValue().total()
                        })
                        .toList(),
                chart(chartType, chartTitle, "Cantidad de comprobantes", 0, 1)
        );
    }

    private ResponseEntity<byte[]> excelResponse(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .body(bytes);
    }

    private record Aggregate(long count, BigDecimal total) {}
}
