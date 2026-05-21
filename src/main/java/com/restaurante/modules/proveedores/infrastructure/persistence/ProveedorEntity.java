package com.restaurante.modules.proveedores.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "proveedor")
public class ProveedorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    @Column(name = "nombre_comercial")
    private String nombreComercial;

    @Column(length = 11)
    private String ruc;

    @Column(name = "tipo_contribuyente")
    private String tipoContribuyente;

    @Column(name = "estado_ruc")
    private String estadoRuc;

    @Column(name = "condicion_ruc")
    private String condicionRuc;

    private String departamento;
    private String provincia;
    private String distrito;

    @Column(name = "direccion_fiscal")
    private String direccionFiscal;

    @Column(name = "regimen_tributario")
    private String regimenTributario;

    @Column(name = "agente_retencion_percepcion")
    private boolean agenteRetencionPercepcion;

    @Column(name = "sujeto_detraccion")
    private boolean sujetoDetraccion;

    @Column(name = "porcentaje_detraccion", precision = 5, scale = 2)
    private BigDecimal porcentajeDetraccion;

    @Column(name = "cuenta_detracciones")
    private String cuentaDetracciones;

    @Column(name = "banco_principal")
    private String bancoPrincipal;

    @Column(name = "tipo_cuenta")
    private String tipoCuenta;

    private String moneda;

    @Column(name = "numero_cuenta_bancaria")
    private String numeroCuentaBancaria;

    @Column(length = 20)
    private String cci;

    @Column(name = "contacto_comercial_nombre")
    private String contactoComercialNombre;

    @Column(name = "contacto_comercial_telefono")
    private String contactoComercialTelefono;

    @Column(name = "contacto_comercial_correo")
    private String contactoComercialCorreo;

    @Column(name = "plazo_pago")
    private String plazoPago;

    @Column(name = "lead_time")
    private String leadTime;

    private boolean activo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getNombre() { return razonSocial; }
    public void setNombre(String nombre) { this.razonSocial = nombre; }

    public String getNombreComercial() { return nombreComercial; }
    public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }

    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }

    public String getTipoContribuyente() { return tipoContribuyente; }
    public void setTipoContribuyente(String tipoContribuyente) { this.tipoContribuyente = tipoContribuyente; }

    public String getEstadoRuc() { return estadoRuc; }
    public void setEstadoRuc(String estadoRuc) { this.estadoRuc = estadoRuc; }

    public String getCondicionRuc() { return condicionRuc; }
    public void setCondicionRuc(String condicionRuc) { this.condicionRuc = condicionRuc; }

    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }

    public String getProvincia() { return provincia; }
    public void setProvincia(String provincia) { this.provincia = provincia; }

    public String getDistrito() { return distrito; }
    public void setDistrito(String distrito) { this.distrito = distrito; }

    public String getDireccionFiscal() { return direccionFiscal; }
    public void setDireccionFiscal(String direccionFiscal) { this.direccionFiscal = direccionFiscal; }

    public String getRegimenTributario() { return regimenTributario; }
    public void setRegimenTributario(String regimenTributario) { this.regimenTributario = regimenTributario; }

    public boolean isAgenteRetencionPercepcion() { return agenteRetencionPercepcion; }
    public void setAgenteRetencionPercepcion(boolean agenteRetencionPercepcion) {
        this.agenteRetencionPercepcion = agenteRetencionPercepcion;
    }

    public boolean isSujetoDetraccion() { return sujetoDetraccion; }
    public void setSujetoDetraccion(boolean sujetoDetraccion) { this.sujetoDetraccion = sujetoDetraccion; }

    public BigDecimal getPorcentajeDetraccion() { return porcentajeDetraccion; }
    public void setPorcentajeDetraccion(BigDecimal porcentajeDetraccion) {
        this.porcentajeDetraccion = porcentajeDetraccion;
    }

    public String getCuentaDetracciones() { return cuentaDetracciones; }
    public void setCuentaDetracciones(String cuentaDetracciones) { this.cuentaDetracciones = cuentaDetracciones; }

    public String getBancoPrincipal() { return bancoPrincipal; }
    public void setBancoPrincipal(String bancoPrincipal) { this.bancoPrincipal = bancoPrincipal; }

    public String getTipoCuenta() { return tipoCuenta; }
    public void setTipoCuenta(String tipoCuenta) { this.tipoCuenta = tipoCuenta; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }

    public String getNumeroCuentaBancaria() { return numeroCuentaBancaria; }
    public void setNumeroCuentaBancaria(String numeroCuentaBancaria) {
        this.numeroCuentaBancaria = numeroCuentaBancaria;
    }

    public String getCci() { return cci; }
    public void setCci(String cci) { this.cci = cci; }

    public String getContactoComercialNombre() { return contactoComercialNombre; }
    public void setContactoComercialNombre(String contactoComercialNombre) {
        this.contactoComercialNombre = contactoComercialNombre;
    }

    public String getContactoComercialTelefono() { return contactoComercialTelefono; }
    public void setContactoComercialTelefono(String contactoComercialTelefono) {
        this.contactoComercialTelefono = contactoComercialTelefono;
    }

    public String getContactoComercialCorreo() { return contactoComercialCorreo; }
    public void setContactoComercialCorreo(String contactoComercialCorreo) {
        this.contactoComercialCorreo = contactoComercialCorreo;
    }

    public String getPlazoPago() { return plazoPago; }
    public void setPlazoPago(String plazoPago) { this.plazoPago = plazoPago; }

    public String getLeadTime() { return leadTime; }
    public void setLeadTime(String leadTime) { this.leadTime = leadTime; }

    public String getTelefono() { return contactoComercialTelefono; }
    public void setTelefono(String telefono) { this.contactoComercialTelefono = telefono; }

    public String getCorreo() { return contactoComercialCorreo; }
    public void setCorreo(String correo) { this.contactoComercialCorreo = correo; }

    public String getContacto() { return contactoComercialNombre; }
    public void setContacto(String contacto) { this.contactoComercialNombre = contacto; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
