package com.restaurante.modules.cocina.application;

import com.restaurante.modules.cocina.infrastructure.web.dto.ColaItemDTO;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
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

    public CocinaService(DetallePedidoJpaRepo detalleRepo) {
        this.detalleRepo = detalleRepo;
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
                .orElseThrow(() -> new BusinessException("Ítem no encontrado", HttpStatus.NOT_FOUND));

        DetallePedidoEntity.EstadoDetalle estado;
        try {
            estado = DetallePedidoEntity.EstadoDetalle.valueOf(nuevoEstado);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Estado inválido: " + nuevoEstado, HttpStatus.BAD_REQUEST);
        }

        detalle.setEstado(estado);
        detalleRepo.save(detalle);
    }
}
