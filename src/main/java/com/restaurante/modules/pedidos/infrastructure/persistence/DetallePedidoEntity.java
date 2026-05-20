package com.restaurante.modules.pedidos.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "detalle_pedido")
public class DetallePedidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "producto_id")
    private Long productoId;

    private int cantidad;

    @Column(name = "precio_unitario")
    private BigDecimal precioUnitario = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private EstadoDetalle estado = EstadoDetalle.PENDIENTE;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    @Column(name = "cancelado_en")
    private LocalDateTime canceladoEn;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();

    public enum EstadoDetalle { PENDIENTE, EN_PREPARACION, LISTO, ENTREGADO, CANCELADO }

    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public Long getProductoId() { return productoId; }
    public int getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public EstadoDetalle getEstado() { return estado; }
    public String getObservaciones() { return observaciones; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public LocalDateTime getCanceladoEn() { return canceladoEn; }
    public LocalDateTime getCreadoEn() { return creadoEn; }

    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public void setEstado(EstadoDetalle estado) { this.estado = estado; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }
    public void setCanceladoEn(LocalDateTime canceladoEn) { this.canceladoEn = canceladoEn; }
}
