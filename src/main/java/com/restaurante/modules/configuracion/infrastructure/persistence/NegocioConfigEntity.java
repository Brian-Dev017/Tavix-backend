package com.restaurante.modules.configuracion.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "negocio_config")
public class NegocioConfigEntity {

    @Id
    private Long id;

    @Column(name = "ruc_negocio", length = 11)
    private String rucNegocio;

    @Column(name = "nombre_comercial")
    private String nombreComercial;

    private String direccion;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "igv_porcentaje", precision = 5, scale = 2)
    private BigDecimal igvPorcentaje = new BigDecimal("18.00");

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRucNegocio() { return rucNegocio; }
    public void setRucNegocio(String rucNegocio) { this.rucNegocio = rucNegocio; }

    public String getNombreComercial() { return nombreComercial; }
    public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public BigDecimal getIgvPorcentaje() { return igvPorcentaje; }
    public void setIgvPorcentaje(BigDecimal igvPorcentaje) { this.igvPorcentaje = igvPorcentaje; }
}
