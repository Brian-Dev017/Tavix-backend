package com.restaurante.modules.caja.application;

import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.DatosComprobanteJpaRepo;
import com.restaurante.modules.caja.infrastructure.web.dto.EmitirComprobanteRequest;
import com.restaurante.modules.configuracion.infrastructure.persistence.SerieComprobanteEntity;
import com.restaurante.modules.configuracion.infrastructure.persistence.SerieComprobanteJpaRepo;
import com.restaurante.modules.configuracion.infrastructure.persistence.NegocioConfigJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoEntity;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.mesas.application.MesaService;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.ws.PedidoEventPublisher;
import com.restaurante.shared.audit.AuditoriaGlobalService;
import com.restaurante.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CajaServiceTest {

    @Mock private ComprobanteJpaRepo comprobanteRepo;
    @Mock private ArqueoJpaRepo arqueoRepo;
    @Mock private DatosComprobanteJpaRepo datosRepo;
    @Mock private PedidoJpaRepo pedidoRepo;
    @Mock private SerieComprobanteJpaRepo serieRepo;
    @Mock private DetallePedidoJpaRepo detalleRepo;
    @Mock private ProductoJpaRepo productoRepo;
    @Mock private MesaService mesaService;
    @Mock private SesionMesaJpaRepo sesionRepo;
    @Mock private MesaJpaRepo mesaRepo;
    @Mock private PedidoEventPublisher eventPublisher;
    @Mock private NegocioConfigJpaRepo negocioRepo;
    @Mock private AuditoriaGlobalService auditoriaGlobalService;
    @Mock private EntityManager entityManager;
    @Mock private Query query;

    private CajaService service;

    @BeforeEach
    void setUp() {
        service = new CajaService(comprobanteRepo, arqueoRepo, datosRepo, pedidoRepo, serieRepo,
                detalleRepo, productoRepo, mesaService, sesionRepo, mesaRepo, eventPublisher,
                negocioRepo, auditoriaGlobalService);
        ReflectionTestUtils.setField(service, "em", entityManager);
    }

    @Test
    void emitirComprobanteRechazaPedidoQueNoEstaListo() {
        PedidoEntity pedido = new PedidoEntity();
        pedido.setEstado(PedidoEntity.EstadoPedido.EN_COCINA);

        when(comprobanteRepo.findByPedidoId(8L)).thenReturn(Optional.empty());
        when(pedidoRepo.findById(8L)).thenReturn(Optional.of(pedido));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.emitirComprobante(2L,
                        false, new EmitirComprobanteRequest(8L, "T", "EFECTIVO", null, null, null,
                                new BigDecimal("10.00")), null));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Solo se puede cobrar un pedido LISTO", exception.getMessage());
    }

    @Test
    void emitirComprobanteCompletaSinCerrarSesionManualmente() {
        PedidoEntity pedido = new PedidoEntity();
        pedido.setSesionMesaId(22L);
        pedido.setEstado(PedidoEntity.EstadoPedido.LISTO);

        SerieComprobanteEntity serie = new SerieComprobanteEntity();
        serie.setTipo("T");
        serie.setSerie("T001");
        serie.setCorrelativoActual(7);

        when(comprobanteRepo.findByPedidoId(8L)).thenReturn(Optional.empty());
        when(pedidoRepo.findById(8L)).thenReturn(Optional.of(pedido));
        ArqueoEntity arqueo = new ArqueoEntity();
        arqueo.setCajeroId(2L);
        when(arqueoRepo.findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(2L, ArqueoEntity.EstadoArqueo.ABIERTO))
                .thenReturn(Optional.of(arqueo));
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(1, 8L)).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(new Object[] {
                new BigDecimal("20.00"), new BigDecimal("3.60"), new BigDecimal("20.00")
        }));
        when(serieRepo.findTopByTipoAndActivoTrueOrderByIdAsc("T")).thenReturn(Optional.of(serie));
        when(comprobanteRepo.save(any(ComprobanteEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(comprobanteRepo.saveAndFlush(any(ComprobanteEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.emitirComprobante(2L, false,
                new EmitirComprobanteRequest(8L, "T", "EFECTIVO", null, null, null,
                        new BigDecimal("30.00")), null);

        assertEquals(PedidoEntity.EstadoPedido.COBRADO, pedido.getEstado());
    }

    @Test
    void emitirComprobanteParaLlevarPermitePedidoAbiertoYEnviaACocina() {
        PedidoEntity pedido = new PedidoEntity();
        pedido.setSesionMesaId(22L);
        pedido.setEstado(PedidoEntity.EstadoPedido.ABIERTO);
        SesionMesaEntity sesion = new SesionMesaEntity();
        sesion.setMesaId(5L);
        MesaEntity mesa = new MesaEntity();
        mesa.setNumero("Para llevar");
        mesa.setTipo(MesaEntity.TipoMesa.PARA_LLEVAR);

        SerieComprobanteEntity serie = new SerieComprobanteEntity();
        serie.setTipo("T");
        serie.setSerie("T001");
        serie.setCorrelativoActual(7);

        DetallePedidoEntity detalle = new DetallePedidoEntity();
        detalle.setPedidoId(8L);
        detalle.setProductoId(11L);
        detalle.setCantidad(1);
        ProductoEntity producto = new ProductoEntity();
        producto.setNombre("Hamburguesa");

        when(comprobanteRepo.findByPedidoId(8L)).thenReturn(Optional.empty());
        when(pedidoRepo.findById(8L)).thenReturn(Optional.of(pedido));
        when(sesionRepo.findById(22L)).thenReturn(Optional.of(sesion));
        when(mesaRepo.findById(5L)).thenReturn(Optional.of(mesa));
        ArqueoEntity arqueo = new ArqueoEntity();
        arqueo.setCajeroId(2L);
        when(arqueoRepo.findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(2L, ArqueoEntity.EstadoArqueo.ABIERTO))
                .thenReturn(Optional.of(arqueo));
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(1, 8L)).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(new Object[] {
                new BigDecimal("18.00"), new BigDecimal("3.24"), new BigDecimal("18.00")
        }));
        when(serieRepo.findTopByTipoAndActivoTrueOrderByIdAsc("T")).thenReturn(Optional.of(serie));
        when(comprobanteRepo.save(any(ComprobanteEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(comprobanteRepo.saveAndFlush(any(ComprobanteEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(detalleRepo.findByPedidoId(8L)).thenReturn(List.of(detalle));
        when(productoRepo.findById(11L)).thenReturn(Optional.of(producto));

        service.emitirComprobante(2L, false,
                new EmitirComprobanteRequest(8L, "T", "EFECTIVO", null, null, null,
                        new BigDecimal("20.00")), null);

        assertEquals(PedidoEntity.EstadoPedido.COBRADO, pedido.getEstado());
        verify(eventPublisher).publicarNuevoItem(any());
        verify(mesaService).cerrarSesion(22L);
    }

    @Test
    void emitirComprobanteRechazaDescuentoSinPermisoAdmin() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.emitirComprobante(2L, false,
                        new EmitirComprobanteRequest(8L, "T", "EFECTIVO", null,
                                new BigDecimal("1.00"), "Cortesia", new BigDecimal("30.00")), null));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Solo un administrador puede aplicar descuentos", exception.getMessage());
    }
}
