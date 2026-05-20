package com.restaurante.modules.caja.application;

import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.DatosComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.web.dto.EmitirComprobanteRequest;
import com.restaurante.modules.configuracion.infrastructure.persistence.SerieComprobanteJpaRepo;
import com.restaurante.modules.mesas.application.MesaService;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CajaServiceTest {

    @Mock private ComprobanteJpaRepo comprobanteRepo;
    @Mock private DatosComprobanteJpaRepo datosRepo;
    @Mock private PedidoJpaRepo pedidoRepo;
    @Mock private SerieComprobanteJpaRepo serieRepo;
    @Mock private MesaService mesaService;

    private CajaService service;

    @BeforeEach
    void setUp() {
        service = new CajaService(comprobanteRepo, datosRepo, pedidoRepo, serieRepo, mesaService);
    }

    @Test
    void emitirComprobanteRechazaPedidoQueNoEstaListo() {
        PedidoEntity pedido = new PedidoEntity();
        pedido.setEstado(PedidoEntity.EstadoPedido.EN_COCINA);

        when(comprobanteRepo.findByPedidoId(8L)).thenReturn(Optional.empty());
        when(pedidoRepo.findById(8L)).thenReturn(Optional.of(pedido));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.emitirComprobante(2L,
                        new EmitirComprobanteRequest(8L, "T", "EFECTIVO", null, null, null)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Solo se puede cobrar un pedido LISTO", exception.getMessage());
    }
}
