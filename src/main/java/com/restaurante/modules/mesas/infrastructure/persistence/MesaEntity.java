package com.restaurante.modules.mesas.infrastructure.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "mesa")
public class MesaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 5, unique = true)
    private String numero;

    private int capacidad;

    @Enumerated(EnumType.STRING)
    private EstadoMesa estado = EstadoMesa.DISPONIBLE;

    public enum EstadoMesa { DISPONIBLE, OCUPADA, RESERVADA, INACTIVA }

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public int getCapacidad() { return capacidad; }
    public EstadoMesa getEstado() { return estado; }
    public void setNumero(String numero) { this.numero = numero; }
    public void setCapacidad(int capacidad) { this.capacidad = capacidad; }
    public void setEstado(EstadoMesa estado) { this.estado = estado; }
}
