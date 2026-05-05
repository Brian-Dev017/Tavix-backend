package com.restaurante.modules.configuracion.infrastructure.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "serie_comprobante")
public class SerieComprobanteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** B = Boleta, F = Factura, T = Ticket */
    @Column(nullable = false, length = 1)
    private String tipo;

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(name = "correlativo_actual", nullable = false)
    private int correlativoActual = 1;

    private boolean activo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getSerie() { return serie; }
    public void setSerie(String serie) { this.serie = serie; }

    public int getCorrelativoActual() { return correlativoActual; }
    public void setCorrelativoActual(int correlativoActual) { this.correlativoActual = correlativoActual; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
