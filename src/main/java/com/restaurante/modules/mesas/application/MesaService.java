package com.restaurante.modules.mesas.application;

import com.restaurante.modules.mesas.infrastructure.persistence.MesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaJpaRepo;
import com.restaurante.modules.mesas.infrastructure.web.dto.MesaDTO;
import com.restaurante.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MesaService {

    private final MesaJpaRepo mesaRepo;
    private final SesionMesaJpaRepo sesionRepo;

    public MesaService(MesaJpaRepo mesaRepo, SesionMesaJpaRepo sesionRepo) {
        this.mesaRepo = mesaRepo;
        this.sesionRepo = sesionRepo;
    }

    public List<MesaDTO> listarMesas() {
        return mesaRepo.findAllByOrderByNumeroAsc().stream()
                .map(m -> {
                    Long sesionId = sesionRepo.findByMesaIdAndCerradaEnIsNull(m.getId())
                            .map(SesionMesaEntity::getId)
                            .orElse(null);
                    return new MesaDTO(m.getId(), m.getNumero(), m.getCapacidad(), m.getEstado().name(), sesionId);
                })
                .toList();
    }

    @Transactional
    public Long abrirSesion(Long mesaId, Long meseroId) {
        MesaEntity mesa = mesaRepo.findById(mesaId)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada", HttpStatus.NOT_FOUND));

        if (mesa.getEstado() != MesaEntity.EstadoMesa.DISPONIBLE) {
            throw new BusinessException("La mesa no está disponible", HttpStatus.CONFLICT);
        }

        sesionRepo.findByMesaIdAndCerradaEnIsNull(mesaId).ifPresent(s -> {
            throw new BusinessException("Ya existe una sesión activa para esta mesa", HttpStatus.CONFLICT);
        });

        SesionMesaEntity sesion = new SesionMesaEntity();
        sesion.setMesaId(mesaId);
        sesion.setMeseroId(meseroId);
        sesionRepo.save(sesion);

        mesa.setEstado(MesaEntity.EstadoMesa.OCUPADA);
        mesaRepo.save(mesa);

        return sesion.getId();
    }

    @Transactional
    public void cerrarSesion(Long sesionId) {
        SesionMesaEntity sesion = sesionRepo.findById(sesionId)
                .orElseThrow(() -> new BusinessException("Sesión no encontrada", HttpStatus.NOT_FOUND));

        if (sesion.getCerradaEn() != null) {
            throw new BusinessException("La sesión ya está cerrada", HttpStatus.CONFLICT);
        }

        sesion.setCerradaEn(java.time.LocalDateTime.now());
        sesionRepo.save(sesion);

        mesaRepo.findById(sesion.getMesaId()).ifPresent(mesa -> {
            mesa.setEstado(MesaEntity.EstadoMesa.DISPONIBLE);
            mesaRepo.save(mesa);
        });
    }
}
