package com.restaurante.modules.reportes.infrastructure.web;

import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Reportes avanzados (diario / semanal / mensual / especiales), historial
 * enriquecido y exportación a Excel (Apache POI). Todo bajo /api/reportes
 * (solo ROLE_AD). Consultas nativas para exactitud sobre "precio incluye IGV".
 */
@RestController
@RequestMapping("/api/reportes")
public class ReportesAvanzadosController {

    private static final Set<String> CATEGORIAS_GASTO =
            Set.of("INSUMOS", "SERVICIOS", "PERSONAL", "DELIVERY", "MANTENIMIENTO", "OTROS");
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @PersistenceContext
    private EntityManager em;

    // ════════════════════ Helpers de consulta ════════════════════

    private LocalDate parseFecha(String v, LocalDate def) {
        return (v == null || v.isBlank()) ? def : LocalDate.parse(v.substring(0, 10));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> filas(Query q, String... cols) {
        List<Object[]> res = q.getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : res) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < cols.length; i++) m.put(cols[i], r[i]);
            out.add(m);
        }
        return out;
    }

    private Query rango(String sql, LocalDate desde, LocalDate hasta) {
        return em.createNativeQuery(sql)
                .setParameter(1, desde.atStartOfDay())
                .setParameter(2, hasta.atTime(LocalTime.MAX));
    }

    private BigDecimal unBigDecimal(Query q) {
        Object v = q.getSingleResult();
        if (v == null) return BigDecimal.ZERO;
        return (v instanceof BigDecimal b) ? b : new BigDecimal(v.toString());
    }

    // ════════════════════ Métodos de datos (reutilizados por JSON y Excel) ════════════════════

    private List<Map<String, Object>> dataVentasPorFecha(LocalDate d, LocalDate h) {
        Query q = rango(
                "SELECT DATE(cv.pagado_en) fecha, COUNT(*) n_ventas, " +
                "ROUND(SUM(cv.subtotal),2) subtotal, ROUND(SUM(cv.descuento),2) descuentos, " +
                "ROUND(SUM(cv.igv),2) igv, ROUND(SUM(cv.total),2) total, " +
                "GROUP_CONCAT(DISTINCT cv.metodo_pago ORDER BY cv.metodo_pago SEPARATOR ', ') metodos " +
                "FROM comprobante_venta cv " +
                "WHERE cv.estado='COMPLETADO' AND cv.pagado_en BETWEEN ?1 AND ?2 " +
                "GROUP BY DATE(cv.pagado_en) ORDER BY fecha", d, h);
        return filas(q, "fecha", "nVentas", "subtotal", "descuentos", "igv", "total", "metodos");
    }

    private List<Map<String, Object>> dataProductosVendidos(LocalDate d, LocalDate h,
                                                            String orden, String dir, int limit) {
        String campo = switch (orden == null ? "" : orden) {
            case "ganancia" -> "ganancia_estimada";
            case "total" -> "total_generado";
            default -> "cantidad";
        };
        String direccion = "asc".equalsIgnoreCase(dir) ? "ASC" : "DESC";
        int top = Math.max(1, Math.min(limit, 1000));
        Query q = rango(
                "SELECT pr.id, pr.nombre, COALESCE(c.nombre,'Sin categoria') categoria, " +
                "SUM(dp.cantidad) cantidad, pr.precio precio_unitario, " +
                "ROUND(SUM(dp.cantidad*dp.precio_unitario),2) total_generado, " +
                "ROUND(SUM(dp.cantidad*(dp.precio_unitario - COALESCE(pr.costo,0))),2) ganancia_estimada " +
                "FROM detalle_pedido dp " +
                "JOIN comprobante_venta cv ON cv.pedido_id=dp.pedido_id AND cv.estado='COMPLETADO' " +
                "JOIN producto pr ON pr.id=dp.producto_id " +
                "LEFT JOIN categoria c ON c.id=pr.categoria_id " +
                "WHERE dp.estado<>'CANCELADO' AND cv.pagado_en BETWEEN ?1 AND ?2 " +
                "GROUP BY pr.id, pr.nombre, c.nombre, pr.precio " +
                "ORDER BY " + campo + " " + direccion + " LIMIT " + top, d, h);
        return filas(q, "productoId", "producto", "categoria", "cantidad", "precioUnitario",
                "totalGenerado", "gananciaEstimada");
    }

    private List<Map<String, Object>> dataVentasPorCategoria(LocalDate d, LocalDate h) {
        Query q = rango(
                "SELECT COALESCE(c.nombre,'Sin categoria') categoria, SUM(dp.cantidad) cantidad, " +
                "ROUND(SUM(dp.cantidad*dp.precio_unitario),2) total " +
                "FROM detalle_pedido dp " +
                "JOIN comprobante_venta cv ON cv.pedido_id=dp.pedido_id AND cv.estado='COMPLETADO' " +
                "JOIN producto pr ON pr.id=dp.producto_id " +
                "LEFT JOIN categoria c ON c.id=pr.categoria_id " +
                "WHERE dp.estado<>'CANCELADO' AND cv.pagado_en BETWEEN ?1 AND ?2 " +
                "GROUP BY c.nombre ORDER BY total DESC", d, h);
        return filas(q, "categoria", "cantidad", "total");
    }

    private List<Map<String, Object>> dataPorMetodo(LocalDate d, LocalDate h) {
        Query q = rango(
                "SELECT cv.metodo_pago metodo, COUNT(*) n, ROUND(SUM(cv.total),2) total " +
                "FROM comprobante_venta cv " +
                "WHERE cv.estado='COMPLETADO' AND cv.pagado_en BETWEEN ?1 AND ?2 " +
                "GROUP BY cv.metodo_pago ORDER BY total DESC", d, h);
        return filas(q, "metodo", "cantidad", "total");
    }

    private List<Map<String, Object>> dataPorTrabajador(LocalDate d, LocalDate h, String rol) {
        Query q;
        if ("CAJERO".equalsIgnoreCase(rol)) {
            q = rango(
                    "SELECT u.id, CONCAT(u.nombre,' ',u.apellido) trabajador, " +
                    "COUNT(*) pedidos, ROUND(SUM(cv.total),2) total " +
                    "FROM comprobante_venta cv JOIN usuario u ON u.id=cv.cajero_id " +
                    "WHERE cv.estado='COMPLETADO' AND cv.pagado_en BETWEEN ?1 AND ?2 " +
                    "GROUP BY u.id, u.nombre, u.apellido ORDER BY total DESC", d, h);
        } else {
            q = rango(
                    "SELECT u.id, CONCAT(u.nombre,' ',u.apellido) trabajador, " +
                    "COUNT(DISTINCT p.id) pedidos, ROUND(SUM(cv.total),2) total " +
                    "FROM comprobante_venta cv " +
                    "JOIN pedido p ON p.id=cv.pedido_id " +
                    "JOIN sesion_mesa sm ON sm.id=p.sesion_mesa_id " +
                    "JOIN usuario u ON u.id=sm.mesero_id " +
                    "WHERE cv.estado='COMPLETADO' AND cv.pagado_en BETWEEN ?1 AND ?2 " +
                    "GROUP BY u.id, u.nombre, u.apellido ORDER BY total DESC", d, h);
        }
        return filas(q, "usuarioId", "trabajador", "pedidos", "total");
    }

    private List<Map<String, Object>> dataAnulaciones(LocalDate d, LocalDate h) {
        Query q = rango(
                "SELECT CONCAT(cv.serie,'-',LPAD(cv.numero,8,'0')) comprobante, " +
                "cv.motivo_anulacion motivo, CONCAT(u.nombre,' ',u.apellido) trabajador, " +
                "cv.anulado_en fecha, cv.total monto " +
                "FROM comprobante_venta cv LEFT JOIN usuario u ON u.id=cv.cajero_id " +
                "WHERE cv.estado='ANULADO' AND COALESCE(cv.anulado_en,cv.actualizado_en) BETWEEN ?1 AND ?2 " +
                "ORDER BY fecha DESC", d, h);
        return filas(q, "comprobante", "motivo", "trabajador", "fecha", "monto");
    }

    private List<Map<String, Object>> dataDescuentos(LocalDate d, LocalDate h) {
        Query q = rango(
                "SELECT CONCAT(cv.serie,'-',LPAD(cv.numero,8,'0')) comprobante, " +
                "cv.motivo_descuento motivo, CONCAT(u.nombre,' ',u.apellido) trabajador, " +
                "cv.pagado_en fecha, cv.descuento monto, cv.total total " +
                "FROM comprobante_venta cv LEFT JOIN usuario u ON u.id=cv.cajero_id " +
                "WHERE cv.descuento>0 AND cv.estado='COMPLETADO' AND cv.pagado_en BETWEEN ?1 AND ?2 " +
                "ORDER BY fecha DESC", d, h);
        return filas(q, "comprobante", "motivo", "trabajador", "fecha", "monto", "total");
    }

    private Map<String, Object> dataResumen(LocalDate d, LocalDate h) {
        Query q = em.createNativeQuery(
                "SELECT " +
                "(SELECT COUNT(*) FROM comprobante_venta WHERE estado='COMPLETADO' AND pagado_en BETWEEN ?1 AND ?2) n_ventas, " +
                "(SELECT COALESCE(ROUND(SUM(total),2),0) FROM comprobante_venta WHERE estado='COMPLETADO' AND pagado_en BETWEEN ?1 AND ?2) ingresos, " +
                "(SELECT COALESCE(ROUND(SUM(igv),2),0) FROM comprobante_venta WHERE estado='COMPLETADO' AND pagado_en BETWEEN ?1 AND ?2) igv, " +
                "(SELECT COALESCE(ROUND(SUM(descuento),2),0) FROM comprobante_venta WHERE estado='COMPLETADO' AND pagado_en BETWEEN ?1 AND ?2) descuentos, " +
                "(SELECT COUNT(*) FROM comprobante_venta WHERE estado='ANULADO' AND COALESCE(anulado_en,actualizado_en) BETWEEN ?1 AND ?2) anuladas, " +
                "(SELECT COALESCE(ROUND(SUM(dp.cantidad*COALESCE(pr.costo,0)),2),0) FROM detalle_pedido dp " +
                "   JOIN comprobante_venta cv ON cv.pedido_id=dp.pedido_id AND cv.estado='COMPLETADO' " +
                "   JOIN producto pr ON pr.id=dp.producto_id " +
                "   WHERE dp.estado<>'CANCELADO' AND cv.pagado_en BETWEEN ?1 AND ?2) costo_productos, " +
                "(SELECT COALESCE(ROUND(SUM(monto),2),0) FROM gasto WHERE fecha BETWEEN ?3 AND ?4) gastos")
                .setParameter(1, d.atStartOfDay())
                .setParameter(2, h.atTime(LocalTime.MAX))
                .setParameter(3, d)
                .setParameter(4, h);
        Object[] r = (Object[]) q.getSingleResult();
        BigDecimal ingresos = (BigDecimal) r[1];
        BigDecimal costoProd = (BigDecimal) r[5];
        BigDecimal gastos = (BigDecimal) r[6];
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("desde", d.toString());
        out.put("hasta", h.toString());
        out.put("nVentas", ((Number) r[0]).longValue());
        out.put("ingresos", ingresos);
        out.put("igv", r[2]);
        out.put("descuentos", r[3]);
        out.put("anuladas", ((Number) r[4]).longValue());
        out.put("costoProductos", costoProd);
        out.put("gastos", gastos);
        out.put("utilidad", ingresos.subtract(costoProd).subtract(gastos));
        return out;
    }

    private BigDecimal totalPorMetodos(LocalDateTime ini, LocalDateTime fin, List<String> metodos) {
        return unBigDecimal(em.createNativeQuery(
                "SELECT COALESCE(ROUND(SUM(total),2),0) FROM comprobante_venta " +
                "WHERE estado='COMPLETADO' AND pagado_en BETWEEN ?1 AND ?2 AND metodo_pago IN (?3)")
                .setParameter(1, ini).setParameter(2, fin).setParameter(3, metodos));
    }

    private Map<String, Object> dataCajaDiaria(LocalDate f) {
        LocalDateTime ini = f.atStartOfDay();
        LocalDateTime fin = f.atTime(LocalTime.MAX);
        BigDecimal montoInicial = unBigDecimal(em.createNativeQuery(
                "SELECT COALESCE(SUM(monto_apertura),0) FROM arqueo_caja WHERE apertura_en BETWEEN ?1 AND ?2")
                .setParameter(1, ini).setParameter(2, fin));
        BigDecimal montoReal = unBigDecimal(em.createNativeQuery(
                "SELECT COALESCE(SUM(monto_cierre),0) FROM arqueo_caja WHERE estado='CERRADO' AND apertura_en BETWEEN ?1 AND ?2")
                .setParameter(1, ini).setParameter(2, fin));
        BigDecimal efectivo = totalPorMetodos(ini, fin, List.of("EFECTIVO"));
        BigDecimal tarjeta = totalPorMetodos(ini, fin, List.of("TARJETA"));
        BigDecimal digital = totalPorMetodos(ini, fin, List.of("YAPE", "PLIN", "IZIPAY", "TRANSFERENCIA"));
        BigDecimal gastos = unBigDecimal(em.createNativeQuery(
                "SELECT COALESCE(SUM(monto),0) FROM gasto WHERE fecha = ?1").setParameter(1, f));
        BigDecimal esperado = montoInicial.add(efectivo).subtract(gastos);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fecha", f.toString());
        out.put("montoInicial", montoInicial);
        out.put("totalEfectivo", efectivo);
        out.put("totalTarjeta", tarjeta);
        out.put("totalDigital", digital);
        out.put("gastos", gastos);
        out.put("montoEsperado", esperado);
        out.put("montoReal", montoReal);
        out.put("diferencia", montoReal.subtract(esperado));
        return out;
    }

    private List<Map<String, Object>> dataGastos(LocalDate d, LocalDate h) {
        Query q = em.createNativeQuery(
                "SELECT g.id, g.fecha, g.categoria, g.descripcion, g.monto, " +
                "CONCAT(u.nombre,' ',u.apellido) registrado_por " +
                "FROM gasto g LEFT JOIN usuario u ON u.id=g.registrado_por " +
                "WHERE g.fecha BETWEEN ?1 AND ?2 ORDER BY g.fecha DESC, g.id DESC")
                .setParameter(1, d).setParameter(2, h);
        return filas(q, "id", "fecha", "categoria", "descripcion", "monto", "registradoPor");
    }

    private List<Map<String, Object>> dataHistorial(int limit, int offset) {
        Query q = em.createNativeQuery(
                "SELECT cv.id, CONCAT(cv.serie,'-',LPAD(cv.numero,8,'0')) comprobante, " +
                "CASE cv.tipo_comprobante_id WHEN 'B' THEN 'Boleta' WHEN 'F' THEN 'Factura' ELSE 'Ticket' END tipo, " +
                "cv.pedido_id, COALESCE(dc.razon_social,'Mostrador') cliente, " +
                "CASE WHEN m.tipo='PARA_LLEVAR' THEN 'Para llevar' " +
                "     WHEN m.numero IS NOT NULL THEN CONCAT('Mesa ', m.numero) ELSE '-' END canal, " +
                "cv.metodo_pago metodo, cv.total, cv.efectivo_recibido, cv.vuelto, cv.estado, " +
                "CONCAT(u.nombre,' ',u.apellido) cajero, cv.creado_en, cv.pagado_en " +
                "FROM comprobante_venta cv " +
                "LEFT JOIN datos_comprobante dc ON dc.id=cv.datos_comprobante_id " +
                "LEFT JOIN usuario u ON u.id=cv.cajero_id " +
                "LEFT JOIN pedido pe ON pe.id=cv.pedido_id " +
                "LEFT JOIN sesion_mesa sm ON sm.id=pe.sesion_mesa_id " +
                "LEFT JOIN mesa m ON m.id=sm.mesa_id " +
                "ORDER BY cv.creado_en DESC LIMIT " + limit + " OFFSET " + offset);
        return filas(q, "id", "comprobante", "tipo", "pedidoId", "cliente", "canal", "metodo",
                "total", "efectivoRecibido", "vuelto", "estado", "cajero", "creadoEn", "pagadoEn");
    }

    // ════════════════════ Endpoints JSON ════════════════════

    @GetMapping("/ventas-por-fecha")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> ventasPorFecha(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        return ResponseEntity.ok(ApiResponse.ok(
                dataVentasPorFecha(parseFecha(desde, LocalDate.now()), parseFecha(hasta, LocalDate.now()))));
    }

    @GetMapping("/productos-vendidos")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> productosVendidos(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "cantidad") String orden,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(dataProductosVendidos(
                parseFecha(desde, LocalDate.now()), parseFecha(hasta, LocalDate.now()), orden, dir, limit)));
    }

    @GetMapping("/ventas-por-categoria")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> ventasPorCategoria(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        return ResponseEntity.ok(ApiResponse.ok(
                dataVentasPorCategoria(parseFecha(desde, LocalDate.now()), parseFecha(hasta, LocalDate.now()))));
    }

    @GetMapping("/por-metodo")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> porMetodo(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        return ResponseEntity.ok(ApiResponse.ok(
                dataPorMetodo(parseFecha(desde, LocalDate.now()), parseFecha(hasta, LocalDate.now()))));
    }

    @GetMapping("/por-trabajador")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> porTrabajador(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "MESERO") String rol) {
        return ResponseEntity.ok(ApiResponse.ok(
                dataPorTrabajador(parseFecha(desde, LocalDate.now()), parseFecha(hasta, LocalDate.now()), rol)));
    }

    @GetMapping("/anulaciones-descuentos")
    public ResponseEntity<ApiResponse<Map<String, Object>>> anulacionesDescuentos(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        LocalDate d = parseFecha(desde, LocalDate.now());
        LocalDate h = parseFecha(hasta, LocalDate.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("anulaciones", dataAnulaciones(d, h));
        out.put("descuentos", dataDescuentos(d, h));
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/resumen")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resumen(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        return ResponseEntity.ok(ApiResponse.ok(dataResumen(
                parseFecha(desde, LocalDate.now().withDayOfMonth(1)), parseFecha(hasta, LocalDate.now()))));
    }

    @GetMapping("/caja-diaria")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cajaDiaria(
            @RequestParam(required = false) String fecha) {
        return ResponseEntity.ok(ApiResponse.ok(dataCajaDiaria(parseFecha(fecha, LocalDate.now()))));
    }

    @GetMapping("/gastos")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listarGastos(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        return ResponseEntity.ok(ApiResponse.ok(dataGastos(
                parseFecha(desde, LocalDate.now().withDayOfMonth(1)), parseFecha(hasta, LocalDate.now()))));
    }

    public record GastoRequest(String fecha, String categoria, String descripcion, BigDecimal monto) {}

    @PostMapping("/gastos")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> registrarGasto(
            @RequestBody GastoRequest req, Authentication auth) {
        if (req == null || req.monto() == null || req.monto().signum() < 0) {
            throw new BusinessException("El monto del gasto debe ser mayor o igual a cero", HttpStatus.BAD_REQUEST);
        }
        String categoria = req.categoria() == null ? "" : req.categoria().trim().toUpperCase();
        if (!CATEGORIAS_GASTO.contains(categoria)) {
            throw new BusinessException("Categoría de gasto inválida. Use: " + CATEGORIAS_GASTO, HttpStatus.BAD_REQUEST);
        }
        LocalDate fecha = parseFecha(req.fecha(), LocalDate.now());
        Long usuarioId = Long.parseLong(auth.getName());
        em.createNativeQuery(
                "INSERT INTO gasto (fecha, categoria, descripcion, monto, registrado_por) VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, fecha).setParameter(2, categoria)
                .setParameter(3, req.descripcion() == null ? null : req.descripcion().trim())
                .setParameter(4, req.monto()).setParameter(5, usuarioId)
                .executeUpdate();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fecha", fecha.toString());
        out.put("categoria", categoria);
        out.put("monto", req.monto());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Gasto registrado", out));
    }

    @GetMapping("/historial-detallado")
    public ResponseEntity<ApiResponse<Map<String, Object>>> historialDetallado(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(size, 100));
        long total = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM comprobante_venta")
                .getSingleResult()).longValue();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", dataHistorial(s, p * s));
        out.put("page", p);
        out.put("size", s);
        out.put("total", total);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    // ════════════════════ Exportación a Excel (Apache POI) ════════════════════

    private CellStyle estiloCabecera(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    /** Hoja tabular: cabeceras visibles + filas mapeadas por claves. */
    private void hojaTabla(Workbook wb, CellStyle cab, String nombre,
                           String[] headers, String[] keys, List<Map<String, Object>> filas) {
        Sheet sh = wb.createSheet(nombreHoja(nombre));
        Row hr = sh.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(cab);
        }
        int r = 1;
        for (Map<String, Object> fila : filas) {
            Row dr = sh.createRow(r++);
            for (int i = 0; i < keys.length; i++) escribirCelda(dr.createCell(i), fila.get(keys[i]));
        }
        for (int i = 0; i < headers.length; i++) sh.autoSizeColumn(i);
    }

    /** Hoja clave/valor (resúmenes). */
    private void hojaClaveValor(Workbook wb, CellStyle cab, String nombre, Map<String, Object> datos) {
        Sheet sh = wb.createSheet(nombreHoja(nombre));
        Row hr = sh.createRow(0);
        Cell c0 = hr.createCell(0); c0.setCellValue("Campo"); c0.setCellStyle(cab);
        Cell c1 = hr.createCell(1); c1.setCellValue("Valor"); c1.setCellStyle(cab);
        int r = 1;
        for (Map.Entry<String, Object> e : datos.entrySet()) {
            Row dr = sh.createRow(r++);
            dr.createCell(0).setCellValue(e.getKey());
            escribirCelda(dr.createCell(1), e.getValue());
        }
        sh.autoSizeColumn(0); sh.autoSizeColumn(1);
    }

    private void escribirCelda(Cell cell, Object v) {
        if (v == null) { cell.setBlank(); return; }
        if (v instanceof Number n) { cell.setCellValue(n.doubleValue()); return; }
        cell.setCellValue(v.toString());
    }

    private String nombreHoja(String nombre) {
        String limpio = nombre.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return limpio.length() > 31 ? limpio.substring(0, 31) : limpio;
    }

    private byte[] aBytes(Workbook wb) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("No se pudo generar el Excel", HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            try { wb.close(); } catch (IOException ignored) {}
        }
    }

    private ResponseEntity<byte[]> descargaExcel(byte[] contenido, String nombreArchivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nombreArchivo)
                .body(contenido);
    }

    /** Reporte completo en un solo libro con todas las hojas. */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        LocalDate d = parseFecha(desde, LocalDate.now().withDayOfMonth(1));
        LocalDate h = parseFecha(hasta, LocalDate.now());
        Workbook wb = new XSSFWorkbook();
        CellStyle cab = estiloCabecera(wb);

        hojaClaveValor(wb, cab, "Resumen", dataResumen(d, h));
        hojaTabla(wb, cab, "Ventas por fecha",
                new String[]{"Fecha", "N° ventas", "Subtotal", "Descuentos", "IGV", "Total", "Métodos"},
                new String[]{"fecha", "nVentas", "subtotal", "descuentos", "igv", "total", "metodos"},
                dataVentasPorFecha(d, h));
        hojaTabla(wb, cab, "Productos vendidos",
                new String[]{"Producto", "Categoría", "Cantidad", "Precio unit.", "Total generado", "Ganancia est."},
                new String[]{"producto", "categoria", "cantidad", "precioUnitario", "totalGenerado", "gananciaEstimada"},
                dataProductosVendidos(d, h, "cantidad", "desc", 1000));
        hojaTabla(wb, cab, "Ventas por categoria",
                new String[]{"Categoría", "Cantidad", "Total"},
                new String[]{"categoria", "cantidad", "total"}, dataVentasPorCategoria(d, h));
        hojaTabla(wb, cab, "Por metodo de pago",
                new String[]{"Método", "N° ventas", "Total"},
                new String[]{"metodo", "cantidad", "total"}, dataPorMetodo(d, h));
        hojaTabla(wb, cab, "Rendimiento meseros",
                new String[]{"Trabajador", "Pedidos", "Total"},
                new String[]{"trabajador", "pedidos", "total"}, dataPorTrabajador(d, h, "MESERO"));
        hojaTabla(wb, cab, "Rendimiento cajeros",
                new String[]{"Trabajador", "Ventas", "Total"},
                new String[]{"trabajador", "pedidos", "total"}, dataPorTrabajador(d, h, "CAJERO"));
        hojaTabla(wb, cab, "Anulaciones",
                new String[]{"Comprobante", "Motivo", "Trabajador", "Fecha", "Monto"},
                new String[]{"comprobante", "motivo", "trabajador", "fecha", "monto"}, dataAnulaciones(d, h));
        hojaTabla(wb, cab, "Descuentos",
                new String[]{"Comprobante", "Motivo", "Trabajador", "Fecha", "Descuento", "Total"},
                new String[]{"comprobante", "motivo", "trabajador", "fecha", "monto", "total"}, dataDescuentos(d, h));
        hojaTabla(wb, cab, "Gastos",
                new String[]{"ID", "Fecha", "Categoría", "Descripción", "Monto", "Registrado por"},
                new String[]{"id", "fecha", "categoria", "descripcion", "monto", "registradoPor"}, dataGastos(d, h));
        hojaClaveValor(wb, cab, "Caja del dia", dataCajaDiaria(h));

        return descargaExcel(aBytes(wb), "reporte_" + d + "_a_" + h + ".xlsx");
    }

    /** Historial de comprobantes en Excel (todas las columnas del requerimiento). */
    @GetMapping("/historial-detallado/excel")
    public ResponseEntity<byte[]> historialExcel(@RequestParam(defaultValue = "5000") int limit) {
        int top = Math.max(1, Math.min(limit, 50000));
        Workbook wb = new XSSFWorkbook();
        CellStyle cab = estiloCabecera(wb);
        hojaTabla(wb, cab, "Historial comprobantes",
                new String[]{"#", "Comprobante", "Tipo", "Pedido", "Cliente", "Mesa/Canal", "Método",
                        "Total", "Recibido", "Vuelto", "Estado", "Cajero", "Creado", "Pagado"},
                new String[]{"id", "comprobante", "tipo", "pedidoId", "cliente", "canal", "metodo",
                        "total", "efectivoRecibido", "vuelto", "estado", "cajero", "creadoEn", "pagadoEn"},
                dataHistorial(top, 0));
        return descargaExcel(aBytes(wb), "historial_comprobantes.xlsx");
    }
}
