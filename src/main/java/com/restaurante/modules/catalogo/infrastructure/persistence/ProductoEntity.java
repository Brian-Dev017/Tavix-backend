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

    @Column(name = "imagen_url", length = 255)
    private String imagenUrl;

    private boolean disponible = true;

    public Long getId() { return id; }
    public Long getCategoriaId() { return categoriaId; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getPrecio() { return precio; }
    public String getImagenUrl() { return imagenUrl; }
    public boolean isDisponible() { return disponible; }

    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
}
