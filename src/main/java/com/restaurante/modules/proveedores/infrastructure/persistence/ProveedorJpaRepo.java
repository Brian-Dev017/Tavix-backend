package com.restaurante.modules.proveedores.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProveedorJpaRepo extends JpaRepository<ProveedorEntity, Long> {
    List<ProveedorEntity> findAllByOrderByNombreAsc();
    List<ProveedorEntity> findByActivoTrue();
}
