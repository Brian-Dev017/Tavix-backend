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

    @Enumerated(EnumType.STRING)
    private EstadoArqueo estado = EstadoArqueo.ABIERTO;

    @Column
    private String notas;

    public enum EstadoArqueo { ABIERTO, CERRADO }

    public Long getId() { return id; }
    public Long getCajeroId() { return cajeroId; }
    public String getNombreCajero() { return nombreCajero; }
    public LocalDateTime getAperturaEn() { return aperturaEn; }
    public LocalDateTime getCierreEn() { return cierreEn; }
    public BigDecimal getMontoApertura() { return montoApertura; }
    public BigDecimal getMontoCierre() { return montoCierre; }
    public BigDecimal getTotalVentas() { return totalVentas; }
    public EstadoArqueo getEstado() { return estado; }
    public String getNotas() { return notas; }

    public void setCajeroId(Long cajeroId) { this.cajeroId = cajeroId; }
    public void setNombreCajero(String nombreCajero) { this.nombreCajero = nombreCajero; }
    public void setAperturaEn(LocalDateTime aperturaEn) { this.aperturaEn = aperturaEn; }
    public void setCierreEn(LocalDateTime cierreEn) { this.cierreEn = cierreEn; }
    public void setMontoApertura(BigDecimal montoApertura) { this.montoApertura = montoApertura; }
    public void setMontoCierre(BigDecimal montoCierre) { this.montoCierre = montoCierre; }
    public void setTotalVentas(BigDecimal totalVentas) { this.totalVentas = totalVentas; }
    public void setEstado(EstadoArqueo estado) { this.estado = estado; }
    public void setNotas(String notas) { this.notas = notas; }
}
