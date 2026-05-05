package com.restaurante.modules.configuracion.infrastructure.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "impresora")
public class ImpresoraEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    /** COCINA, CAJA, BARRA */
    @Column(nullable = false, length = 10)
    private String tipo;

    private String host;

    private int puerto;

    private boolean activo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPuerto() { return puerto; }
    public void setPuerto(int puerto) { this.puerto = puerto; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
