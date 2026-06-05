package com.restaurante.modules.pedidos.application;

import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoEntity;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.web.dto.AgregarItemRequest;
import com.restaurante.modules.pedidos.infrastructure.ws.PedidoEventPublisher;
import com.restaurante.shared.audit.AuditoriaContexto;
import com.restaurante.shared.audit.AuditoriaGlobalService;
import com.restaurante.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock private PedidoJpaRepo pedidoRepo;
    @Mock private DetallePedidoJpaRepo detalleRepo;
    @Mock private SesionMesaJpaRepo sesionRepo;
    @Mock private MesaJpaRepo mesaRepo;
    @Mock private ProductoJpaRepo productoRepo;
    @Mock private PedidoEventPublisher eventPublisher;
    @Mock private AuditoriaGlobalService auditoriaGlobalService;

    private PedidoService service;
    private AuditoriaContexto contexto;

    @BeforeEach
    void setUp() {
        service = new PedidoService(
                pedidoRepo, detalleRepo, sesionRepo, mesaRepo, productoRepo,
                eventPublisher, auditoriaGlobalService
        );
        contexto = new AuditoriaContexto(1L, "test", "MS", "127.0.0.1", "/test");
    }

    @Test
    void agregarPrimerItemMuevePedidoAEnCocina() {
        PedidoEntity pedido = new PedidoEntity();
        pedido.setSesionMesaId(7L);
        SesionMesaEntity sesion = new SesionMesaEntity();
        sesion.setMeseroId(1L);
        ProductoEntity producto = new ProductoEntity();
        producto.setNombre("Ceviche");
        producto.setPrecio(new BigDecimal("25.00"));

        when(pedidoRepo.findById(3L)).thenReturn(Optional.of(pedido));
        when(sesionRepo.findById(7L)).thenReturn(Optional.of(sesion));
        when(productoRepo.findById(11L)).thenReturn(Optional.of(producto));
        when(detalleRepo.save(any(DetallePedidoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.agregarItem(3L, new AgregarItemRequest(11L, 2, "sin cebolla"), 1L, true, contexto);

        assertEquals(PedidoEntity.EstadoPedido.EN_COCINA, pedido.getEstado());
        verify(pedidoRepo).save(pedido);
    }

    @Test
    void agregarItemRechazaProductoNoDisponible() {
        PedidoEntity pedido = new PedidoEntity();
        pedido.setSesionMesaId(7L);
        SesionMesaEntity sesion = new SesionMesaEntity();
        sesion.setMeseroId(1L);
        ProductoEntity producto = new ProductoEntity();
        producto.setDisponible(false);

        when(pedidoRepo.findById(3L)).thenReturn(Optional.of(pedido));
        when(sesionRepo.findById(7L)).thenReturn(Optional.of(sesion));
        when(productoRepo.findById(11L)).thenReturn(Optional.of(producto));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.agregarItem(3L, new AgregarItemRequest(11L, 1, null), 1L, true, contexto));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Producto no disponible", exception.getMessage());
        verify(detalleRepo, never()).save(any());
    }
}
