package com.restaurante.modules.caja.application;

import com.restaurante.modules.caja.infrastructure.persistence.*;
import com.restaurante.modules.caja.infrastructure.web.dto.*;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CajaService {

    @PersistenceContext
    private EntityManager em;

    private final ComprobanteJpaRepo comprobanteRepo;
    private final DatosComprobanteJpaRepo datosRepo;
    private final PedidoJpaRepo pedidoRepo;

    public CajaService(ComprobanteJpaRepo comprobanteRepo,
                       DatosComprobanteJpaRepo datosRepo,
                       PedidoJpaRepo pedidoRepo) {
        this.comprobanteRepo = comprobanteRepo;
        this.datosRepo = datosRepo;
        this.pedidoRepo = pedidoRepo;
    }

    @SuppressWarnings("unchecked")
    public List<PedidoResumenDTO> getPedidosActivos() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT pedido_id, mesa, mesero, total_items, subtotal, igv, total_con_igv, estado_pedido " +
                "FROM v_consumo_por_pedido " +
                "WHERE estado_pedido IN ('ABIERTO','EN_COCINA','LISTO') " +
                "ORDER BY pedido_id ASC"
        ).getResultList();

        return rows.stream().map(r -> new PedidoResumenDTO(
                ((Number) r[0]).longValue(),
                (String) r[1],
                (String) r[2],
                ((Number) r[3]).intValue(),
                (BigDecimal) r[4],
                (BigDecimal) r[5],
                (BigDecimal) r[6],
                (String) r[7]
        )).toList();
    }

    @Transactional
    public ComprobanteResponseDTO emitirComprobante(Long cajeroId, EmitirComprobanteRequest request) {
        if (comprobanteRepo.findByPedidoId(request.pedidoId()).isPresent()) {
            throw new BusinessException("Ya existe un comprobante para este pedido", HttpStatus.CONFLICT);
        }

        PedidoEntity pedido = pedidoRepo.findById(request.pedidoId())
                .orElseThrow(() -> new BusinessException("Pedido no encontrado", HttpStatus.NOT_FOUND));

        if (pedido.getEstado() == PedidoEntity.EstadoPedido.COBRADO
                || pedido.getEstado() == PedidoEntity.EstadoPedido.CANCELADO) {
            throw new BusinessException("El pedido ya fue cerrado", HttpStatus.BAD_REQUEST);
        }

        // Calcular totales desde v_consumo_por_pedido
        List<Object[]> totales = em.createNativeQuery(
                "SELECT subtotal, igv, total_con_igv FROM v_consumo_por_pedido WHERE pedido_id = ?1"
        ).setParameter(1, request.pedidoId()).getResultList();

        if (totales.isEmpty()) {
            throw new BusinessException("El pedido no tiene ítems", HttpStatus.BAD_REQUEST);
        }

        Object[] t = totales.get(0);
        BigDecimal subtotal = (BigDecimal) t[0];
        BigDecimal igv = (BigDecimal) t[1];
        BigDecimal total = (BigDecimal) t[2];

        // Guardar datos_comprobante si corresponde (Boleta/Factura)
        Long datosId = null;
        if (request.datosComprobante() != null &&
                ("B".equals(request.tipoComprobanteId()) || "F".equals(request.tipoComprobanteId()))) {
            DatosComprobanteEntity datos = new DatosComprobanteEntity();
            datos.setTipoComprobanteId(request.tipoComprobanteId());
            datos.setRucDni(request.datosComprobante().rucDni());
            datos.setRazonSocial(request.datosComprobante().razonSocial());
            datos.setDireccion(request.datosComprobante().direccion());
            datosId = datosRepo.save(datos).getId();
        }

        // Crear comprobante en PENDIENTE primero
        ComprobanteEntity comp = new ComprobanteEntity();
        comp.setPedidoId(request.pedidoId());
        comp.setCajeroId(cajeroId);
        comp.setTipoComprobanteId(request.tipoComprobanteId() != null ? request.tipoComprobanteId() : "T");
        comp.setDatosComprobanteId(datosId);
        comp.setSubtotal(subtotal);
        comp.setIgv(igv);
        comp.setTotal(total);
        comp.setMetodoPago(ComprobanteEntity.MetodoPago.valueOf(request.metodoPago()));
        ComprobanteEntity saved = comprobanteRepo.save(comp);

        // Actualizar a COMPLETADO — dispara el trigger tr_comprobante_liberar_mesa
        saved.setEstado(ComprobanteEntity.EstadoComprobante.COMPLETADO);
        saved.setPagadoEn(LocalDateTime.now());
        saved.setActualizadoEn(LocalDateTime.now());
        comprobanteRepo.saveAndFlush(saved);

        return new ComprobanteResponseDTO(
                saved.getId(), saved.getPedidoId(),
                saved.getTipoComprobanteId(), saved.getMetodoPago().name(),
                subtotal, igv, total,
                saved.getEstado().name(), saved.getPagadoEn()
        );
    }
}
