package com.restaurante.modules.reportes.infrastructure.web;

import com.restaurante.modules.caja.infrastructure.persistence.ArqueoEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.DatosComprobanteJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportesControllerExcelTest {

    @Test
    void ventasExcelContieneHojasTablasYGraficos() throws Exception {
        ComprobanteJpaRepo comprobanteRepo = mock(ComprobanteJpaRepo.class);
        ComprobanteEntity comprobante = comprobante(1L, "B", "EFECTIVO", "25.50");
        when(comprobanteRepo.findAll()).thenReturn(List.of(comprobante));
        ReportesController controller = controller(
                comprobanteRepo, mock(PedidoJpaRepo.class), mock(ArqueoJpaRepo.class));
        String today = LocalDate.now().toString();

        ResponseEntity<byte[]> response = controller.exportarVentasExcel(today, today);

        try (XSSFWorkbook workbook = workbook(response)) {
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Ventas por día"));
            assertNotNull(workbook.getSheet("Métodos de pago"));
            assertFalse(workbook.getSheet("Ventas por día")
                    .getDrawingPatriarch().getCharts().isEmpty());
            assertFalse(workbook.getSheet("Métodos de pago")
                    .getDrawingPatriarch().getCharts().isEmpty());
        }
    }

    @Test
    void dashboardExcelVacioContieneSeccionesSinGraficosInvalidos() throws Exception {
        ComprobanteJpaRepo comprobanteRepo = mock(ComprobanteJpaRepo.class);
        PedidoJpaRepo pedidoRepo = mock(PedidoJpaRepo.class);
        when(comprobanteRepo.findAll()).thenReturn(List.of());
        when(pedidoRepo.findAll()).thenReturn(List.of());
        ReportesController controller = controller(
                comprobanteRepo, pedidoRepo, mock(ArqueoJpaRepo.class));
        String today = LocalDate.now().toString();

        ResponseEntity<byte[]> response = controller.exportarDashboardExcel(today, today);

        try (XSSFWorkbook workbook = workbook(response)) {
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Ventas por día"));
            assertNotNull(workbook.getSheet("Métodos de pago"));
            assertNotNull(workbook.getSheet("Categorías"));
            assertNotNull(workbook.getSheet("Platos"));
            assertNull(workbook.getSheet("Ventas por día").getDrawingPatriarch());
            assertNull(workbook.getSheet("Métodos de pago").getDrawingPatriarch());
            assertNull(workbook.getSheet("Categorías").getDrawingPatriarch());
            assertNull(workbook.getSheet("Platos").getDrawingPatriarch());
        }
    }

    @Test
    void pagosExcelIncluyeResumenTablaYGrafico() throws Exception {
        ComprobanteJpaRepo comprobanteRepo = mock(ComprobanteJpaRepo.class);
        when(comprobanteRepo.findAll()).thenReturn(List.of(
                comprobante(1L, "B", "EFECTIVO", "20.00"),
                comprobante(2L, "F", "YAPE", "30.00")
        ));
        ReportesController controller = controller(
                comprobanteRepo, mock(PedidoJpaRepo.class), mock(ArqueoJpaRepo.class));
        String today = LocalDate.now().toString();

        try (XSSFWorkbook workbook = workbook(
                controller.exportarPagosExcel(today, today))) {
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Métodos de pago"));
            assertFalse(workbook.getSheet("Métodos de pago")
                    .getDrawingPatriarch().getCharts().isEmpty());
        }
    }

    @Test
    void arqueosExcelIncluyeResumenDetalleYEstados() throws Exception {
        ArqueoJpaRepo arqueoRepo = mock(ArqueoJpaRepo.class);
        ArqueoEntity arqueo = new ArqueoEntity();
        arqueo.setNombreCajero("Cajero Prueba");
        arqueo.setAperturaEn(LocalDateTime.now());
        arqueo.setMontoApertura(new BigDecimal("100.00"));
        arqueo.setTotalVentas(new BigDecimal("80.00"));
        arqueo.setEstado(ArqueoEntity.EstadoArqueo.PRECIERRE);
        when(arqueoRepo.findAll()).thenReturn(List.of(arqueo));
        ReportesController controller = controller(
                mock(ComprobanteJpaRepo.class), mock(PedidoJpaRepo.class), arqueoRepo);

        try (XSSFWorkbook workbook = workbook(controller.exportarArqueosExcel())) {
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Arqueos"));
            assertNotNull(workbook.getSheet("Estados"));
            assertFalse(workbook.getSheet("Arqueos")
                    .getDrawingPatriarch().getCharts().isEmpty());
        }
    }

    @Test
    void historialExcelIncluyeTodosLosAgregados() throws Exception {
        ComprobanteJpaRepo comprobanteRepo = mock(ComprobanteJpaRepo.class);
        when(comprobanteRepo.findAll()).thenReturn(List.of(
                comprobante(1L, "B", "EFECTIVO", "20.00"),
                comprobante(2L, "F", "YAPE", "30.00")
        ));
        ReportesController controller = controller(
                comprobanteRepo, mock(PedidoJpaRepo.class), mock(ArqueoJpaRepo.class));

        try (XSSFWorkbook workbook = workbook(
                controller.exportarHistorialExcel("COMPLETADO"))) {
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Comprobantes"));
            assertNotNull(workbook.getSheet("Métodos de pago"));
            assertNotNull(workbook.getSheet("Estados"));
            assertNotNull(workbook.getSheet("Tipos"));
        }
    }

    private static ReportesController controller(
            ComprobanteJpaRepo comprobanteRepo,
            PedidoJpaRepo pedidoRepo,
            ArqueoJpaRepo arqueoRepo
    ) {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        ReportesController controller = new ReportesController(
                comprobanteRepo,
                pedidoRepo,
                mock(DetallePedidoJpaRepo.class),
                mock(ProductoJpaRepo.class),
                mock(DatosComprobanteJpaRepo.class),
                mock(NegocioConfigJpaRepo.class),
                arqueoRepo
        );
        ReflectionTestUtils.setField(controller, "em", entityManager);
        return controller;
    }

    private static ComprobanteEntity comprobante(
            Long pedidoId,
            String tipo,
            String metodo,
            String total
    ) {
        ComprobanteEntity comprobante = new ComprobanteEntity();
        comprobante.setPedidoId(pedidoId);
        comprobante.setTipoComprobanteId(tipo);
        comprobante.setSerie(tipo + "001");
        comprobante.setNumero(pedidoId.intValue());
        comprobante.setEstado(ComprobanteEntity.EstadoComprobante.COMPLETADO);
        comprobante.setMetodoPago(ComprobanteEntity.MetodoPago.valueOf(metodo));
        comprobante.setSubtotal(new BigDecimal(total));
        comprobante.setIgv(BigDecimal.ZERO);
        comprobante.setDescuento(BigDecimal.ZERO);
        comprobante.setTotal(new BigDecimal(total));
        comprobante.setPagadoEn(LocalDateTime.now());
        return comprobante;
    }

    private static XSSFWorkbook workbook(ResponseEntity<byte[]> response) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(response.getBody()));
    }
}
