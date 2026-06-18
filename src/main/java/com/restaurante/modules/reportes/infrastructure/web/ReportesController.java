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
import com.restaurante.shared.exception.BusinessException;
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

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle title = titleStyle(workbook);
            CellStyle header = headerStyle(workbook);
            CellStyle money = moneyStyle(workbook);
            createSummarySheet(
                    workbook,
                    title,
                    header,
                    money,
                    "REPORTE DE DASHBOARD",
                    range,
                    List.of(
                            new Object[]{"Ventas", data.ventasHoy()},
                            new Object[]{"Pedidos", data.pedidosHoy()}
                    )
            );
            createTableSheet(
                    workbook, title, header, money, "Ventas por día", range,
                    new String[]{"Fecha", "Comprobantes", "Total"},
                    data.ventasPorDia().stream()
                            .map(row -> new Object[]{row.fecha(), row.cantidad(), row.total()})
                            .toList(),
                    2, ChartTypes.BAR, "Ventas por día"
            );
            createTableSheet(
                    workbook, title, header, money, "Métodos de pago", range,
                    new String[]{"Método", "Transacciones", "Total"},
                    data.ventasPorMetodo().stream()
                            .map(row -> new Object[]{row.metodo(), row.cantidad(), row.total()})
                            .toList(),
                    2, ChartTypes.PIE, "Distribución de pagos"
            );
            createTableSheet(
                    workbook, title, header, money, "Categorías", range,
                    new String[]{"Categoría", "Cantidad", "Total"},
                    data.ventasPorCategoria().stream()
                            .map(row -> new Object[]{row.categoria(), row.cantidad(), row.total()})
                            .toList(),
                    2, ChartTypes.PIE, "Ventas por categoría"
            );
            createTableSheet(
                    workbook, title, header, money, "Platos", range,
                    new String[]{"Producto", "Cantidad"},
                    data.platosVendidos().stream()
                            .map(row -> new Object[]{row.nombre(), row.cantidad()})
                            .toList(),
                    1, ChartTypes.BAR, "Platos más vendidos"
            );
            return excelResponse(
                    workbook,
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

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle title = titleStyle(workbook);
            CellStyle header = headerStyle(workbook);
            CellStyle money = moneyStyle(workbook);
            createSummarySheet(
                    workbook,
                    title,
                    header,
                    money,
                    "REPORTE DE VENTAS",
                    range,
                    List.of(
                            new Object[]{"Total de ventas", data.totalVentas()},
                            new Object[]{"Comprobantes", data.cantidadComprobantes()},
                            new Object[]{"Ticket promedio", data.promedioVenta()}
                    )
            );
            createTableSheet(
                    workbook, title, header, money, "Ventas por día", range,
                    new String[]{"Fecha", "Comprobantes", "Total"},
                    data.ventasPorDia().stream()
                            .map(row -> new Object[]{row.fecha(), row.cantidad(), row.total()})
                            .toList(),
                    2, ChartTypes.BAR, "Ventas por día"
            );
            createTableSheet(
                    workbook, title, header, money, "Métodos de pago", range,
                    new String[]{"Método", "Transacciones", "Total"},
                    data.ventasPorMetodo().stream()
                            .map(row -> new Object[]{row.metodo(), row.cantidad(), row.total()})
                            .toList(),
                    2, ChartTypes.PIE, "Distribución de pagos"
            );
            return excelResponse(
                    workbook,
                    "ventas-" + range.desde() + "-" + range.hasta() + ".xlsx"
            );
        } catch (IOException error) {
            throw new BusinessException(
                    "No se pudo generar el Excel de ventas",
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

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("S/ #,##0.00"));
        return style;
    }

    private void createSummarySheet(
            XSSFWorkbook workbook,
            CellStyle title,
            CellStyle header,
            CellStyle money,
            String reportTitle,
            DateRange range,
            List<Object[]> values) {
        XSSFSheet sheet = workbook.createSheet("Resumen");
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(reportTitle);
        titleCell.setCellStyle(title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));
        Row rangeRow = sheet.createRow(1);
        rangeRow.createCell(0).setCellValue("Desde");
        rangeRow.createCell(1).setCellValue(range.desde().toString());
        rangeRow.createCell(2).setCellValue("Hasta: " + range.hasta());
        Row headers = sheet.createRow(3);
        headers.createCell(0).setCellValue("Indicador");
        headers.createCell(1).setCellValue("Valor");
        headers.getCell(0).setCellStyle(header);
        headers.getCell(1).setCellStyle(header);
        int rowIndex = 4;
        for (Object[] value : values) {
            Row row = sheet.createRow(rowIndex++);
            writeCell(row.createCell(0), value[0], money);
            writeCell(row.createCell(1), value[1], money);
        }
        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.createFreezePane(0, 4);
    }

    private void createTableSheet(
            XSSFWorkbook workbook,
            CellStyle title,
            CellStyle header,
            CellStyle money,
            String name,
            DateRange range,
            String[] headers,
            List<Object[]> rows,
            int valueColumn,
            ChartTypes chartType,
            String chartTitle) {
        XSSFSheet sheet = workbook.createSheet(name);
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(name.toUpperCase(Locale.ROOT));
        titleCell.setCellStyle(title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));
        Row rangeRow = sheet.createRow(1);
        rangeRow.createCell(0).setCellValue(
                "Desde " + range.desde() + " hasta " + range.hasta()
        );
        Row headerRow = sheet.createRow(3);
        for (int column = 0; column < headers.length; column++) {
            Cell cell = headerRow.createCell(column);
            cell.setCellValue(headers[column]);
            cell.setCellStyle(header);
        }
        int rowIndex = 4;
        for (Object[] values : rows) {
            Row row = sheet.createRow(rowIndex++);
            for (int column = 0; column < values.length; column++) {
                writeCell(row.createCell(column), values[column], money);
            }
        }
        if (rows.isEmpty()) {
            Row emptyRow = sheet.createRow(4);
            emptyRow.createCell(0).setCellValue("Sin datos");
            emptyRow.createCell(valueColumn).setCellValue(0);
            rowIndex = 5;
        }
        addChart(
                sheet,
                chartType,
                chartTitle,
                4,
                rowIndex - 1,
                0,
                valueColumn,
                headers.length + 1
        );
        for (int column = 0; column < headers.length; column++) {
            sheet.setColumnWidth(column, Math.max(16, headers[column].length() + 4) * 256);
        }
        sheet.createFreezePane(0, 4);
        sheet.setAutoFilter(new CellRangeAddress(3, Math.max(3, rowIndex - 1), 0, headers.length - 1));
    }

    private void writeCell(Cell cell, Object value, CellStyle money) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof BigDecimal number) {
            cell.setCellValue(number.doubleValue());
            cell.setCellStyle(money);
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private void addChart(
            XSSFSheet sheet,
            ChartTypes type,
            String title,
            int firstRow,
            int lastRow,
            int categoryColumn,
            int valueColumn,
            int anchorColumn) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFChart chart = drawing.createChart(
                drawing.createAnchor(
                        0, 0, 0, 0,
                        anchorColumn, 3,
                        anchorColumn + 9, 20
                )
        );
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet,
                new CellRangeAddress(firstRow, lastRow, categoryColumn, categoryColumn)
        );
        XDDFNumericalDataSource<Double> values =
                XDDFDataSourcesFactory.fromNumericCellRange(
                        sheet,
                        new CellRangeAddress(firstRow, lastRow, valueColumn, valueColumn)
                );
        if (type == ChartTypes.PIE) {
            XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
            data.addSeries(categories, values);
            chart.plot(data);
            return;
        }
        XDDFCategoryAxis categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis valueAxis = chart.createValueAxis(AxisPosition.LEFT);
        valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        XDDFBarChartData data = (XDDFBarChartData) chart.createData(
                ChartTypes.BAR,
                categoryAxis,
                valueAxis
        );
        data.setBarDirection(BarDirection.COL);
        data.addSeries(categories, values);
        chart.plot(data);
    }

    private ResponseEntity<byte[]> excelResponse(
            XSSFWorkbook workbook,
            String filename) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(XLSX_MIME))
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\""
                    )
                    .body(output.toByteArray());
        }
    }
}
