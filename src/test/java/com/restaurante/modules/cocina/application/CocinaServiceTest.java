package com.restaurante.modules.cocina.application;

import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.ws.PedidoEventPublisher;
import com.restaurante.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CocinaServiceTest {

    @Mock private DetallePedidoJpaRepo detalleRepo;
    @Mock private PedidoJpaRepo pedidoRepo;
    @Mock private PedidoEventPublisher eventPublisher;

    private CocinaService service;

    @BeforeEach
    void setUp() {
        service = new CocinaService(detalleRepo, pedidoRepo, eventPublisher);
    }

    @Test
    void actualizarEstadoRechazaSaltarPendienteAListo() {
        DetallePedidoEntity detalle = new DetallePedidoEntity();
        when(detalleRepo.findById(4L)).thenReturn(Optional.of(detalle));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.actualizarEstado(4L, "LISTO"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Transicion de estado invalida", exception.getMessage());
        verify(detalleRepo, never()).save(any());
    }

    @Test
    void cancelarItemExigeMotivo() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.cancelarItem(4L, " "));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("El motivo de cancelacion es obligatorio", exception.getMessage());
    }

    @Test
    void actualizarEstadoPublicaCambioParaLasDemasPantallas() {
        DetallePedidoEntity detalle = new DetallePedidoEntity();
        ReflectionTestUtils.setField(detalle, "id", 4L);
        detalle.setPedidoId(9L);
        detalle.setEstado(DetallePedidoEntity.EstadoDetalle.PENDIENTE);
        when(detalleRepo.findById(4L)).thenReturn(Optional.of(detalle));
        when(detalleRepo.findByPedidoId(9L)).thenReturn(List.of(detalle));

        service.actualizarEstado(4L, "EN_PREPARACION");

        verify(eventPublisher).publicarPedidoEvento(any());
    }
}
