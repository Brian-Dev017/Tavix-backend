package com.restaurante.modules.reportes.infrastructure.web;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportesControllerExcelTest {

    @Test
    void ventasExcelContieneHojasTablasYGraficos() throws Exception {
        ComprobanteJpaRepo comprobanteRepo = mock(ComprobanteJpaRepo.class);
        ComprobanteEntity comprobante = new ComprobanteEntity();
        comprobante.setEstado(ComprobanteEntity.EstadoComprobante.COMPLETADO);
        comprobante.setMetodoPago(ComprobanteEntity.MetodoPago.EFECTIVO);
        comprobante.setTotal(new BigDecimal("25.50"));
        comprobante.setPagadoEn(LocalDateTime.now());
        when(comprobanteRepo.findAll()).thenReturn(List.of(comprobante));

        ReportesController controller = new ReportesController(
                comprobanteRepo,
                mock(PedidoJpaRepo.class),
                mock(DetallePedidoJpaRepo.class),
                mock(ProductoJpaRepo.class),
                mock(DatosComprobanteJpaRepo.class),
                mock(NegocioConfigJpaRepo.class)
        );
        String today = LocalDate.now().toString();

        ResponseEntity<byte[]> response =
                controller.exportarVentasExcel(today, today);

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(response.getBody())
        )) {
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Ventas por día"));
            assertNotNull(workbook.getSheet("Métodos de pago"));
            assertFalse(
                    workbook.getSheet("Ventas por día")
                            .getDrawingPatriarch()
                            .getCharts()
                            .isEmpty()
            );
            assertFalse(
                    workbook.getSheet("Métodos de pago")
                            .getDrawingPatriarch()
                            .getCharts()
                            .isEmpty()
            );
        }
    }

    @Test
    void dashboardExcelContieneTodasLasSeccionesYGraficos() throws Exception {
        ComprobanteJpaRepo comprobanteRepo = mock(ComprobanteJpaRepo.class);
        PedidoJpaRepo pedidoRepo = mock(PedidoJpaRepo.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(comprobanteRepo.findAll()).thenReturn(List.of());
        when(pedidoRepo.findAll()).thenReturn(List.of());
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(query);
        when(query.setParameter(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        ReportesController controller = new ReportesController(
                comprobanteRepo,
                pedidoRepo,
                mock(DetallePedidoJpaRepo.class),
                mock(ProductoJpaRepo.class),
                mock(DatosComprobanteJpaRepo.class),
                mock(NegocioConfigJpaRepo.class)
        );
        ReflectionTestUtils.setField(controller, "em", entityManager);
        String today = LocalDate.now().toString();

        ResponseEntity<byte[]> response =
                controller.exportarDashboardExcel(today, today);

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(response.getBody())
        )) {
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Ventas por día"));
            assertNotNull(workbook.getSheet("Métodos de pago"));
            assertNotNull(workbook.getSheet("Categorías"));
            assertNotNull(workbook.getSheet("Platos"));
            assertFalse(
                    workbook.getSheet("Ventas por día")
                            .getDrawingPatriarch()
                            .getCharts()
                            .isEmpty()
            );
            assertFalse(
                    workbook.getSheet("Métodos de pago")
                            .getDrawingPatriarch()
                            .getCharts()
                            .isEmpty()
            );
            assertFalse(
                    workbook.getSheet("Categorías")
                            .getDrawingPatriarch()
                            .getCharts()
                            .isEmpty()
            );
            assertFalse(
                    workbook.getSheet("Platos")
                            .getDrawingPatriarch()
                            .getCharts()
                            .isEmpty()
            );
        }
    }
}
