package com.restaurante.modules.caja.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "comprobante_venta")
public class ComprobanteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false, unique = true)
    private Long pedidoId;

    @Column(name = "cajero_id", nullable = false)
    private Long cajeroId;

    @Column(name = "arqueo_caja_id")
    private Long arqueoCajaId;

    @Column(name = "tipo_comprobante_id", length = 1)
    private String tipoComprobanteId = "T";

    @Column(length = 4)
    private String serie;

    private Integer numero;

    @Column(name = "datos_comprobante_id")
    private Long datosComprobanteId;

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal igv;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    @Column(precision = 10, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(name = "motivo_descuento")
    private String motivoDescuento;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false)
    private MetodoPago metodoPago;

    @Column(name = "efectivo_recibido", precision = 10, scale = 2)
    private BigDecimal efectivoRecibido;

    @Column(precision = 10, scale = 2)
    private BigDecimal vuelto = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private EstadoComprobante estado = EstadoComprobante.PENDIENTE;

    @Column(name = "pagado_en")
    private LocalDateTime pagadoEn;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    public enum MetodoPago { EFECTIVO, TARJETA, IZIPAY, YAPE, PLIN, TRANSFERENCIA }
    public enum EstadoComprobante { PENDIENTE, COMPLETADO, ANULADO }

    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public Long getCajeroId() { return cajeroId; }
    public Long getArqueoCajaId() { return arqueoCajaId; }
    public String getTipoComprobanteId() { return tipoComprobanteId; }
    public String getSerie() { return serie; }
    public Integer getNumero() { return numero; }
    public Long getDatosComprobanteId() { return datosComprobanteId; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getIgv() { return igv; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getDescuento() { return descuento; }
    public String getMotivoDescuento() { return motivoDescuento; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public BigDecimal getEfectivoRecibido() { return efectivoRecibido; }
    public BigDecimal getVuelto() { return vuelto; }
    public EstadoComprobante getEstado() { return estado; }
    public LocalDateTime getPagadoEn() { return pagadoEn; }
    public LocalDateTime getAnuladoEn() { return anuladoEn; }
    public LocalDateTime getCreadoEn() { return creadoEn; }

    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public void setCajeroId(Long cajeroId) { this.cajeroId = cajeroId; }
    public void setArqueoCajaId(Long arqueoCajaId) { this.arqueoCajaId = arqueoCajaId; }
    public void setTipoComprobanteId(String tipoComprobanteId) { this.tipoComprobanteId = tipoComprobanteId; }
    public void setSerie(String serie) { this.serie = serie; }
    public void setNumero(Integer numero) { this.numero = numero; }
    public void setDatosComprobanteId(Long datosComprobanteId) { this.datosComprobanteId = datosComprobanteId; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public void setIgv(BigDecimal igv) { this.igv = igv; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }
    public void setMotivoDescuento(String motivoDescuento) { this.motivoDescuento = motivoDescuento; }
    public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
    public void setEfectivoRecibido(BigDecimal efectivoRecibido) { this.efectivoRecibido = efectivoRecibido; }
    public void setVuelto(BigDecimal vuelto) { this.vuelto = vuelto; }
    public void setEstado(EstadoComprobante estado) { this.estado = estado; }
    public void setPagadoEn(LocalDateTime pagadoEn) { this.pagadoEn = pagadoEn; }
    public void setAnuladoEn(LocalDateTime anuladoEn) { this.anuladoEn = anuladoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
