package com.restaurante.modules.caja.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "arqueo_caja")
public class ArqueoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cajero_id", nullable = false)
    private Long cajeroId;

    @Column(name = "nombre_cajero")
    private String nombreCajero;

    @Column(name = "apertura_en")
    private LocalDateTime aperturaEn = LocalDateTime.now();

    @Column(name = "cierre_en")
    private LocalDateTime cierreEn;

    @Column(name = "monto_apertura", precision = 10, scale = 2)
    private BigDecimal montoApertura;

    @Column(name = "monto_cierre", precision = 10, scale = 2)
    private BigDecimal montoCierre;

    @Column(name = "total_ventas", precision = 10, scale = 2)
    private BigDecimal totalVentas;

    @Column(name = "total_efectivo", precision = 10, scale = 2)
    private BigDecimal totalEfectivo = BigDecimal.ZERO;

    @Column(name = "total_digital", precision = 10, scale = 2)
    private BigDecimal totalDigital = BigDecimal.ZERO;

    @Column(name = "monto_esperado", precision = 10, scale = 2)
    private BigDecimal montoEsperado = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal diferencia = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private EstadoArqueo estado = EstadoArqueo.ABIERTO;

    @Column
    private String notas;

    @Transient
    private BigDecimal totalRedondeo = BigDecimal.ZERO;

    public enum EstadoArqueo { ABIERTO, CERRADO }

    public Long getId() { return id; }
    public Long getCajeroId() { return cajeroId; }
    public String getNombreCajero() { return nombreCajero; }
    public LocalDateTime getAperturaEn() { return aperturaEn; }
    public LocalDateTime getCierreEn() { return cierreEn; }
    public BigDecimal getMontoApertura() { return montoApertura; }
    public BigDecimal getMontoCierre() { return montoCierre; }
    public BigDecimal getTotalVentas() { return totalVentas; }
    public BigDecimal getTotalEfectivo() { return totalEfectivo; }
    public BigDecimal getTotalDigital() { return totalDigital; }
    public BigDecimal getMontoEsperado() { return montoEsperado; }
    public BigDecimal getDiferencia() { return diferencia; }
    public EstadoArqueo getEstado() { return estado; }
    public String getNotas() { return notas; }
    public BigDecimal getTotalRedondeo() { return totalRedondeo; }

    public void setCajeroId(Long cajeroId) { this.cajeroId = cajeroId; }
    public void setNombreCajero(String nombreCajero) { this.nombreCajero = nombreCajero; }
    public void setAperturaEn(LocalDateTime aperturaEn) { this.aperturaEn = aperturaEn; }
    public void setCierreEn(LocalDateTime cierreEn) { this.cierreEn = cierreEn; }
    public void setMontoApertura(BigDecimal montoApertura) { this.montoApertura = montoApertura; }
    public void setMontoCierre(BigDecimal montoCierre) { this.montoCierre = montoCierre; }
    public void setTotalVentas(BigDecimal totalVentas) { this.totalVentas = totalVentas; }
    public void setTotalEfectivo(BigDecimal totalEfectivo) { this.totalEfectivo = totalEfectivo; }
    public void setTotalDigital(BigDecimal totalDigital) { this.totalDigital = totalDigital; }
    public void setMontoEsperado(BigDecimal montoEsperado) { this.montoEsperado = montoEsperado; }
    public void setDiferencia(BigDecimal diferencia) { this.diferencia = diferencia; }
    public void setEstado(EstadoArqueo estado) { this.estado = estado; }
    public void setNotas(String notas) { this.notas = notas; }
    public void setTotalRedondeo(BigDecimal totalRedondeo) { this.totalRedondeo = totalRedondeo; }
}
