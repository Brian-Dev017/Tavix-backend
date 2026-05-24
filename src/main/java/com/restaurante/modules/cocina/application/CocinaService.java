package com.restaurante.modules.cocina.application;

import com.restaurante.modules.cocina.infrastructure.web.dto.ColaItemDTO;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CocinaService {

    @PersistenceContext
    private EntityManager em;

    private final DetallePedidoJpaRepo detalleRepo;
    private final PedidoJpaRepo pedidoRepo;

    public CocinaService(DetallePedidoJpaRepo detalleRepo, PedidoJpaRepo pedidoRepo) {
        this.detalleRepo = detalleRepo;
        this.pedidoRepo = pedidoRepo;
    }

    @SuppressWarnings("unchecked")
    public List<ColaItemDTO> getCola() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT detalle_id, pedido_id, mesa, producto, cantidad, observaciones, estado_item, solicitado_en " +
                "FROM v_cola_cocina ORDER BY solicitado_en ASC"
        ).getResultList();

        return rows.stream().map(r -> new ColaItemDTO(
                ((Number) r[0]).longValue(),
                ((Number) r[1]).longValue(),
                (String) r[2],
                (String) r[3],
                ((Number) r[4]).intValue(),
                (String) r[5],
                (String) r[6],
                r[7] instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) r[7]
        )).toList();
    }

    @Transactional
    public void actualizarEstado(Long detalleId, String nuevoEstado) {
        DetallePedidoEntity detalle = detalleRepo.findById(detalleId)
                .orElseThrow(() -> new BusinessException("Item no encontrado", HttpStatus.NOT_FOUND));

        DetallePedidoEntity.EstadoDetalle estado;
        try {
            estado = DetallePedidoEntity.EstadoDetalle.valueOf(nuevoEstado);
        } catch (RuntimeException e) {
            throw new BusinessException("Estado invalido: " + nuevoEstado, HttpStatus.BAD_REQUEST);
        }

        if (!esTransicionValida(detalle.getEstado(), estado)) {
            throw new BusinessException("Transicion de estado invalida", HttpStatus.CONFLICT);
        }

        detalle.setEstado(estado);
        detalleRepo.save(detalle);
        actualizarPedidoSiCorresponde(detalle.getPedidoId());
    }

    @Transactional
    public void cancelarItem(Long detalleId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("El motivo de cancelacion es obligatorio", HttpStatus.BAD_REQUEST);
        }

        DetallePedidoEntity detalle = detalleRepo.findById(detalleId)
                .orElseThrow(() -> new BusinessException("Item no encontrado", HttpStatus.NOT_FOUND));
        if (detalle.getEstado() == DetallePedidoEntity.EstadoDetalle.LISTO
                || detalle.getEstado() == DetallePedidoEntity.EstadoDetalle.ENTREGADO) {
            throw new BusinessException("No se puede cancelar un item ya listo o entregado", HttpStatus.CONFLICT);
        }

        detalle.setEstado(DetallePedidoEntity.EstadoDetalle.CANCELADO);
        detalle.setMotivoCancelacion(motivo.trim());
        detalle.setCanceladoEn(LocalDateTime.now());
        detalleRepo.save(detalle);
        actualizarPedidoSiCorresponde(detalle.getPedidoId());
    }

    private boolean esTransicionValida(DetallePedidoEntity.EstadoDetalle actual,
                                       DetallePedidoEntity.EstadoDetalle destino) {
        return (actual == DetallePedidoEntity.EstadoDetalle.PENDIENTE
                    && destino == DetallePedidoEntity.EstadoDetalle.EN_PREPARACION)
                || (actual == DetallePedidoEntity.EstadoDetalle.EN_PREPARACION
                    && destino == DetallePedidoEntity.EstadoDetalle.LISTO);
    }

    private void actualizarPedidoSiCorresponde(Long pedidoId) {
        if (pedidoRepo == null || pedidoId == null) return;

        List<DetallePedidoEntity> detalles = detalleRepo.findByPedidoId(pedidoId);
        List<DetallePedidoEntity> activos = detalles.stream()
                .filter(d -> d.getEstado() != DetallePedidoEntity.EstadoDetalle.CANCELADO)
                .toList();
        if (activos.isEmpty()) {
            pedidoRepo.findById(pedidoId).ifPresent(pedido -> {
                if (pedido.getEstado() != PedidoEntity.EstadoPedido.COBRADO
                        && pedido.getEstado() != PedidoEntity.EstadoPedido.CANCELADO) {
                    pedido.setEstado(PedidoEntity.EstadoPedido.CANCELADO);
                    pedido.setMotivoCancelacion("Todos los items del pedido fueron cancelados");
                    pedido.setCanceladoEn(LocalDateTime.now());
                    pedidoRepo.save(pedido);
                }
            });
            return;
        }

        boolean todosListos = activos.stream()
                .allMatch(d -> d.getEstado() == DetallePedidoEntity.EstadoDetalle.LISTO);
        if (!todosListos) return;

        pedidoRepo.findById(pedidoId).ifPresent(pedido -> {
            if (pedido.getEstado() == PedidoEntity.EstadoPedido.EN_COCINA
                    || pedido.getEstado() == PedidoEntity.EstadoPedido.ABIERTO) {
                pedido.setEstado(PedidoEntity.EstadoPedido.LISTO);
                pedidoRepo.save(pedido);
            }
        });
    }
}
