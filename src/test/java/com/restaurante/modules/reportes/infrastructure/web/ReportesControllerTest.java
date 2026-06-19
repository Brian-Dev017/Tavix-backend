package com.restaurante.modules.reportes.infrastructure.web;

import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.DatosComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigEntity;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportesControllerTest {

    @Test
    void detalleIncluyePorcentajeIgvConfiguradoDelNegocio() {
        ComprobanteJpaRepo comprobanteRepo = mock(ComprobanteJpaRepo.class);
        DetallePedidoJpaRepo detalleRepo = mock(DetallePedidoJpaRepo.class);
        NegocioConfigJpaRepo negocioRepo = mock(NegocioConfigJpaRepo.class);
        ComprobanteEntity comprobante = mock(ComprobanteEntity.class);
        NegocioConfigEntity negocio = new NegocioConfigEntity();
        negocio.setId(1L);
        negocio.setIgvPorcentaje(new BigDecimal("18.00"));

        when(comprobante.getId()).thenReturn(9L);
        when(comprobante.getPedidoId()).thenReturn(4L);
        when(comprobante.getTipoComprobanteId()).thenReturn("B");
        when(comprobante.getSerie()).thenReturn("B001");
        when(comprobante.getNumero()).thenReturn(42);
        when(comprobante.getMetodoPago()).thenReturn(ComprobanteEntity.MetodoPago.EFECTIVO);
        when(comprobante.getSubtotal()).thenReturn(new BigDecimal("18.22"));
        when(comprobante.getIgv()).thenReturn(new BigDecimal("3.28"));
        when(comprobante.getDescuento()).thenReturn(BigDecimal.ZERO);
        when(comprobante.getTotal()).thenReturn(new BigDecimal("21.50"));
        when(comprobante.getEfectivoRecibido()).thenReturn(new BigDecimal("30.00"));
        when(comprobante.getVuelto()).thenReturn(new BigDecimal("8.50"));
        when(comprobante.getPagadoEn()).thenReturn(LocalDateTime.of(2026, 6, 18, 11, 33));
        when(comprobanteRepo.findById(9L)).thenReturn(Optional.of(comprobante));
        when(detalleRepo.findByPedidoId(4L)).thenReturn(List.of());
        when(negocioRepo.findById(1L)).thenReturn(Optional.of(negocio));

        ReportesController controller = new ReportesController(
                comprobanteRepo,
                mock(PedidoJpaRepo.class),
                detalleRepo,
                mock(ProductoJpaRepo.class),
                mock(DatosComprobanteJpaRepo.class),
                negocioRepo,
                mock(ArqueoJpaRepo.class)
        );

        ReportesController.PedidoDetalleReporte detalle =
                controller.detalle(9L).getBody().data();

        assertEquals(new BigDecimal("18.00"), detalle.negocioIgvPorcentaje());
    }
}
