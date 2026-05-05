package com.restaurante.modules.pedidos.application;

import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.ws.PedidoEventPublisher;
import com.restaurante.modules.pedidos.infrastructure.web.dto.AgregarItemRequest;
import com.restaurante.modules.pedidos.infrastructure.web.dto.CrearPedidoRequest;
import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemCocinaDTO;
import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemPedidoDTO;
import com.restaurante.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PedidoService {

    private final PedidoJpaRepo pedidoRepo;
    private final DetallePedidoJpaRepo detalleRepo;
    private final SesionMesaJpaRepo sesionRepo;
    private final MesaJpaRepo mesaRepo;
    private final ProductoJpaRepo productoRepo;
    private final PedidoEventPublisher eventPublisher;

    public PedidoService(PedidoJpaRepo pedidoRepo, DetallePedidoJpaRepo detalleRepo,
                         SesionMesaJpaRepo sesionRepo, MesaJpaRepo mesaRepo,
                         ProductoJpaRepo productoRepo, PedidoEventPublisher eventPublisher) {
        this.pedidoRepo = pedidoRepo;
        this.detalleRepo = detalleRepo;
        this.sesionRepo = sesionRepo;
        this.mesaRepo = mesaRepo;
        this.productoRepo = productoRepo;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Long crearPedido(CrearPedidoRequest request) {
        sesionRepo.findById(request.sesionMesaId())
                .orElseThrow(() -> new BusinessException("Sesión de mesa no encontrada", HttpStatus.NOT_FOUND));

        // Retorna pedido activo existente para esta sesión, o crea uno nuevo
        return pedidoRepo
                .findTopBySesionMesaIdAndEstadoNotOrderByCreadoEnAsc(
                        request.sesionMesaId(), PedidoEntity.EstadoPedido.CANCELADO)
                .filter(p -> p.getEstado() != PedidoEntity.EstadoPedido.COBRADO)
                .map(PedidoEntity::getId)
                .orElseGet(() -> {
                    PedidoEntity pedido = new PedidoEntity();
                    pedido.setSesionMesaId(request.sesionMesaId());
                    return pedidoRepo.save(pedido).getId();
                });
    }

    @Transactional
    public ItemPedidoDTO agregarItem(Long pedidoId, AgregarItemRequest request) {
        PedidoEntity pedido = pedidoRepo.findById(pedidoId)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado", HttpStatus.NOT_FOUND));

        if (pedido.getEstado() == PedidoEntity.EstadoPedido.COBRADO
                || pedido.getEstado() == PedidoEntity.EstadoPedido.CANCELADO) {
            throw new BusinessException("No se puede agregar ítems a un pedido " + pedido.getEstado());
        }

        var producto = productoRepo.findById(request.productoId())
                .orElseThrow(() -> new BusinessException("Producto no encontrado", HttpStatus.NOT_FOUND));

        DetallePedidoEntity detalle = new DetallePedidoEntity();
        detalle.setPedidoId(pedidoId);
        detalle.setProductoId(request.productoId());
        detalle.setCantidad(request.cantidad());
        detalle.setPrecioUnitario(producto.getPrecio());
        detalle.setObservaciones(request.observaciones());
        DetallePedidoEntity saved = detalleRepo.save(detalle);

        String numeroMesa = sesionRepo.findById(pedido.getSesionMesaId())
                .flatMap(s -> mesaRepo.findById(s.getMesaId()))
                .map(m -> m.getNumero())
                .orElse("?");

        eventPublisher.publicarNuevoItem(new ItemCocinaDTO(
                saved.getId(), pedidoId, numeroMesa,
                producto.getNombre(), request.cantidad(),
                request.observaciones(), saved.getCreadoEn()
        ));

        BigDecimal subtotal = producto.getPrecio().multiply(BigDecimal.valueOf(request.cantidad()));
        return new ItemPedidoDTO(
                saved.getId(), pedidoId, producto.getId(),
                producto.getNombre(), request.cantidad(), producto.getPrecio(),
                subtotal, saved.getEstado().name(), request.observaciones(), saved.getCreadoEn()
        );
    }

    public List<ItemPedidoDTO> getDetalle(Long pedidoId) {
        return detalleRepo.findByPedidoId(pedidoId).stream()
                .map(d -> {
                    String nombre = productoRepo.findById(d.getProductoId())
                            .map(p -> p.getNombre()).orElse("Desconocido");
                    BigDecimal subtotal = d.getPrecioUnitario()
                            .multiply(BigDecimal.valueOf(d.getCantidad()));
                    return new ItemPedidoDTO(
                            d.getId(), d.getPedidoId(), d.getProductoId(),
                            nombre, d.getCantidad(), d.getPrecioUnitario(),
                            subtotal, d.getEstado().name(), d.getObservaciones(), d.getCreadoEn()
                    );
                }).toList();
    }
}
