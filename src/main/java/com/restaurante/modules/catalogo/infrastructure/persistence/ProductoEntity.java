package com.restaurante.modules.catalogo.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "producto")
public class ProductoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "categoria_id")
    private Long categoriaId;

    @Column(length = 80)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    private BigDecimal precio;

    @Column(name = "costo")
    private BigDecimal costo;

    @Column(name = "imagen_url", length = 255)
    private String imagenUrl;

    private boolean disponible = true;

    @Column(name = "requiere_cocina")
    private boolean requiereCocina = true;

    public Long getId() { return id; }
    public Long getCategoriaId() { return categoriaId; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getPrecio() { return precio; }
    public BigDecimal getCosto() { return costo; }
    public String getImagenUrl() { return imagenUrl; }
    public boolean isDisponible() { return disponible; }
    public boolean isRequiereCocina() { return requiereCocina; }

    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public void setCosto(BigDecimal costo) { this.costo = costo; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
    public void setRequiereCocina(boolean requiereCocina) { this.requiereCocina = requiereCocina; }
}
