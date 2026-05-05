package com.restaurante.modules.mesas.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sesion_mesa")
public class SesionMesaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mesa_id")
    private Long mesaId;

    @Column(name = "mesero_id")
    private Long meseroId;

    @Column(name = "abierta_en")
    private LocalDateTime abiertaEn = LocalDateTime.now();

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    public Long getId() { return id; }
    public Long getMesaId() { return mesaId; }
    public Long getMeseroId() { return meseroId; }
    public LocalDateTime getAbiertaEn() { return abiertaEn; }
    public LocalDateTime getCerradaEn() { return cerradaEn; }

    public void setMesaId(Long mesaId) { this.mesaId = mesaId; }
    public void setMeseroId(Long meseroId) { this.meseroId = meseroId; }
    public void setCerradaEn(java.time.LocalDateTime cerradaEn) { this.cerradaEn = cerradaEn; }
}
