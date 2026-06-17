package com.restaurante.modules.pedidos.application;

import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.mesas.application.MesaService;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.web.dto.AgregarItemRequest;
import com.restaurante.modules.pedidos.infrastructure.web.dto.CrearPedidoRequest;
import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemCocinaDTO;
import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemPedidoDTO;
import com.restaurante.modules.pedidos.infrastructure.ws.PedidoEventPublisher;
import com.restaurante.shared.audit.AuditoriaContexto;
import com.restaurante.shared.audit.AuditoriaGlobalService;
import com.restaurante.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class PedidoService {

    private final PedidoJpaRepo pedidoRepo;
    private final DetallePedidoJpaRepo detalleRepo;
    private final SesionMesaJpaRepo sesionRepo;
    private final MesaJpaRepo mesaRepo;
    private final MesaService mesaService;
    private final ProductoJpaRepo productoRepo;
    private final PedidoEventPublisher eventPublisher;
    private final AuditoriaGlobalService auditoriaGlobalService;

    public PedidoService(PedidoJpaRepo pedidoRepo, DetallePedidoJpaRepo detalleRepo,
                         SesionMesaJpaRepo sesionRepo, MesaJpaRepo mesaRepo,
                         MesaService mesaService, ProductoJpaRepo productoRepo, PedidoEventPublisher eventPublisher,
                         AuditoriaGlobalService auditoriaGlobalService) {
        this.pedidoRepo = pedidoRepo;
        this.detalleRepo = detalleRepo;
        this.sesionRepo = sesionRepo;
        this.mesaRepo = mesaRepo;
        this.mesaService = mesaService;
        this.productoRepo = productoRepo;
        this.eventPublisher = eventPublisher;
        this.auditoriaGlobalService = auditoriaGlobalService;
    }

    @Transactional
    public Long crearPedido(CrearPedidoRequest request, Long usuarioId, boolean admin, AuditoriaContexto contexto) {
        var sesion = sesionRepo.findById(request.sesionMesaId())
                .orElseThrow(() -> new BusinessException("Sesion de mesa no encontrada", HttpStatus.NOT_FOUND));
        if (sesion.getCerradaEn() != null) {
            throw new BusinessException("La sesion de mesa esta cerrada", HttpStatus.CONFLICT);
        }
        if (!admin && !sesion.getMeseroId().equals(usuarioId)) {
            throw new BusinessException("Esta mesa esta siendo atendida por otro mesero", HttpStatus.FORBIDDEN);
        }

        return pedidoRepo
                .findTopBySesionMesaIdAndEstadoNotOrderByCreadoEnAsc(
                        request.sesionMesaId(), PedidoEntity.EstadoPedido.CANCELADO)
                .filter(p -> p.getEstado() != PedidoEntity.EstadoPedido.COBRADO)
                .map(PedidoEntity::getId)
                .orElseGet(() -> {
                    PedidoEntity pedido = new PedidoEntity();
                    pedido.setSesionMesaId(request.sesionMesaId());
                    PedidoEntity saved = pedidoRepo.save(pedido);
                    auditoriaGlobalService.registrar(
                            "pedidos",
                            "pedido",
                            saved.getId().toString(),
                            "CREAR",
                            "Creacion de pedido",
                            null,
                            saved,
                            contexto
                    );
                    return saved.getId();
                });
    }

    @Transactional
    public Long crearPedidoParaLlevar(Long cajeroId, AuditoriaContexto contexto) {
        Long sesionMesaId = mesaService.abrirSesionParaLlevar(cajeroId);
        PedidoEntity pedido = new PedidoEntity();
        pedido.setSesionMesaId(sesionMesaId);
        pedido.setObservaciones("Pedido para llevar");
        PedidoEntity saved = pedidoRepo.save(pedido);
        auditoriaGlobalService.registrar(
                "pedidos",
                "pedido",
                saved.getId().toString(),
                "CREAR",
                "Creacion de pedido para llevar",
                null,
                saved,
                contexto
        );
        return saved.getId();
    }

    @Transactional
    public ItemPedidoDTO agregarItem(Long pedidoId, AgregarItemRequest request, Long usuarioId, boolean admin,
                                     boolean cajero, AuditoriaContexto contexto) {
        PedidoEntity pedido = pedidoRepo.findById(pedidoId)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado", HttpStatus.NOT_FOUND));
        var sesion = sesionRepo.findById(pedido.getSesionMesaId())
                .orElseThrow(() -> new BusinessException("Sesion de mesa no encontrada", HttpStatus.NOT_FOUND));
        if (sesion.getCerradaEn() != null) {
            throw new BusinessException("La sesion de mesa esta cerrada", HttpStatus.CONFLICT);
        }
        boolean paraLlevar = esSesionParaLlevar(sesion.getMesaId());
        if (!admin && !sesion.getMeseroId().equals(usuarioId) && !(cajero && paraLlevar)) {
            throw new BusinessException("No puedes agregar items a una mesa tomada por otro mesero",
                    HttpStatus.FORBIDDEN);
        }

        if (pedido.getEstado() == PedidoEntity.EstadoPedido.COBRADO
                || pedido.getEstado() == PedidoEntity.EstadoPedido.CANCELADO) {
            throw new BusinessException("No se puede agregar items a un pedido " + pedido.getEstado(),
                    HttpStatus.CONFLICT);
        }

        var producto = productoRepo.findById(request.productoId())
                .orElseThrow(() -> new BusinessException("Producto no encontrado", HttpStatus.NOT_FOUND));
        if (!producto.isDisponible()) {
            throw new BusinessException("Producto no disponible", HttpStatus.CONFLICT);
        }

        boolean requiereCocina = producto.isRequiereCocina();

        DetallePedidoEntity detalle = new DetallePedidoEntity();
        detalle.setPedidoId(pedidoId);
        detalle.setProductoId(request.productoId());
        detalle.setCantidad(request.cantidad());
        detalle.setPrecioUnitario(producto.getPrecio());
        detalle.setObservaciones(request.observaciones());
        // Las bebidas/productos que no pasan por cocina quedan LISTO de inmediato
        if (!requiereCocina) {
            detalle.setEstado(DetallePedidoEntity.EstadoDetalle.LISTO);
        }
        DetallePedidoEntity saved = detalleRepo.save(detalle);
        auditoriaGlobalService.registrar(
                "pedidos",
                "detalle_pedido",
                saved.getId().toString(),
                "CREAR",
                "Creacion de item en pedido",
                null,
                saved,
                contexto
        );

        if (!paraLlevar) {
            PedidoEntity.EstadoPedido estadoAnterior = pedido.getEstado();
            PedidoEntity.EstadoPedido nuevoEstado = calcularEstadoPedido(pedidoId, estadoAnterior);
            if (nuevoEstado != estadoAnterior) {
                pedido.setEstado(nuevoEstado);
                PedidoEntity updated = pedidoRepo.save(pedido);
                auditoriaGlobalService.registrar(
                        "pedidos",
                        "pedido",
                        updated.getId().toString(),
                        "CAMBIO_ESTADO",
                        "Cambio de estado de pedido",
                        Map.of("estado", estadoAnterior.name()),
                        Map.of("estado", updated.getEstado().name()),
                        contexto
                );
            }
        }

        String numeroMesa = sesionRepo.findById(pedido.getSesionMesaId())
                .flatMap(s -> mesaRepo.findById(s.getMesaId()))
                .map(m -> m.getNumero())
                .orElse("?");

        // Solo se envía a la cola de cocina si el producto pasa por cocina
        if (!paraLlevar && requiereCocina) {
            eventPublisher.publicarNuevoItem(new ItemCocinaDTO(
                    saved.getId(), pedidoId, numeroMesa,
                    producto.getNombre(), request.cantidad(),
                    request.observaciones(), saved.getCreadoEn()
            ));
        }

        // Notificar cambio de pedido para refresco en tiempo real (caja/mesas)
        eventPublisher.publicarPedidoEvento(Map.of(
                "evento", "ITEM_AGREGADO",
                "pedidoId", pedidoId,
                "estado", pedido.getEstado().name(),
                "mesa", numeroMesa
        ));

        BigDecimal subtotal = producto.getPrecio().multiply(BigDecimal.valueOf(request.cantidad()));
        return new ItemPedidoDTO(
                saved.getId(), pedidoId, producto.getId(),
                producto.getNombre(), request.cantidad(), producto.getPrecio(),
                subtotal, saved.getEstado().name(), request.observaciones(), saved.getCreadoEn()
        );
    }

    public List<ItemPedidoDTO> getDetalle(Long pedidoId, Long usuarioId, boolean admin, boolean cajero) {
        PedidoEntity pedido = pedidoRepo.findById(pedidoId)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado", HttpStatus.NOT_FOUND));
        var sesion = sesionRepo.findById(pedido.getSesionMesaId())
                .orElseThrow(() -> new BusinessException("Sesion de mesa no encontrada", HttpStatus.NOT_FOUND));
        boolean paraLlevar = esSesionParaLlevar(sesion.getMesaId());
        if (!admin && !sesion.getMeseroId().equals(usuarioId) && !(cajero && paraLlevar)) {
            throw new BusinessException("No puedes ver un pedido de otro mesero", HttpStatus.FORBIDDEN);
        }
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

    /**
     * Recalcula el estado del pedido a partir de sus ítems activos.
     * Si todos están LISTO/ENTREGADO (p.ej. solo bebidas) → LISTO; si hay
     * pendientes que pasan por cocina → EN_COCINA. No toca COBRADO/CANCELADO.
     */
    private PedidoEntity.EstadoPedido calcularEstadoPedido(Long pedidoId, PedidoEntity.EstadoPedido actual) {
        if (actual == PedidoEntity.EstadoPedido.COBRADO
                || actual == PedidoEntity.EstadoPedido.CANCELADO) {
            return actual;
        }
        List<DetallePedidoEntity> activos = detalleRepo.findByPedidoId(pedidoId).stream()
                .filter(d -> d.getEstado() != DetallePedidoEntity.EstadoDetalle.CANCELADO)
                .toList();
        if (activos.isEmpty()) {
            return actual;
        }
        boolean todosListos = activos.stream().allMatch(d ->
                d.getEstado() == DetallePedidoEntity.EstadoDetalle.LISTO
                        || d.getEstado() == DetallePedidoEntity.EstadoDetalle.ENTREGADO);
        return todosListos
                ? PedidoEntity.EstadoPedido.LISTO
                : PedidoEntity.EstadoPedido.EN_COCINA;
    }

    private boolean esSesionParaLlevar(Long mesaId) {
        if (mesaId == null) return false;
        return mesaRepo.findById(mesaId)
                .map(MesaEntity::isParaLlevar)
                .orElse(false);
    }
}
