package com.restaurante.modules.caja.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "datos_comprobante")
public class DatosComprobanteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_comprobante_id", length = 1)
    private String tipoComprobanteId;

    @Column(name = "ruc_dni", length = 11)
    private String rucDni;

    @Column(name = "razon_social")
    private String razonSocial;

    @Column
    private String direccion;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();

    public Long getId() { return id; }
    public String getTipoComprobanteId() { return tipoComprobanteId; }
    public String getRucDni() { return rucDni; }
    public String getRazonSocial() { return razonSocial; }
    public String getDireccion() { return direccion; }

    public void setTipoComprobanteId(String tipoComprobanteId) { this.tipoComprobanteId = tipoComprobanteId; }
    public void setRucDni(String rucDni) { this.rucDni = rucDni; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
}
