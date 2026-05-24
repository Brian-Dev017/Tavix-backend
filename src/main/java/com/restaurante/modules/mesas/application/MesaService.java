package com.restaurante.modules.mesas.application;

import com.restaurante.modules.mesas.infrastructure.persistence.MesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaJpaRepo;
import com.restaurante.modules.mesas.infrastructure.web.dto.MesaDTO;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.modules.auth.infrastructure.persistence.UsuarioJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MesaService {

    private final MesaJpaRepo mesaRepo;
    private final SesionMesaJpaRepo sesionRepo;
    private final PedidoJpaRepo pedidoRepo;
    private final UsuarioJpaRepo usuarioRepo;

    public MesaService(MesaJpaRepo mesaRepo, SesionMesaJpaRepo sesionRepo,
                       PedidoJpaRepo pedidoRepo, UsuarioJpaRepo usuarioRepo) {
        this.mesaRepo = mesaRepo;
        this.sesionRepo = sesionRepo;
        this.pedidoRepo = pedidoRepo;
        this.usuarioRepo = usuarioRepo;
    }

    public List<MesaDTO> listarMesas() {
        return mesaRepo.findAllByOrderByNumeroAsc().stream()
                .map(m -> {
                    Optional<SesionMesaEntity> sesionActiva =
                            sesionRepo.findByMesaIdAndCerradaEnIsNull(m.getId());
                    Long sesionId = sesionActiva.map(SesionMesaEntity::getId).orElse(null);
                    Long meseroId = sesionActiva.map(SesionMesaEntity::getMeseroId).orElse(null);
                    String meseroNombre = meseroId == null ? null : usuarioRepo.findById(meseroId)
                            .map(u -> (u.getNombre() + " " + u.getApellido()).trim())
                            .orElse("Mesero #" + meseroId);
                    return new MesaDTO(m.getId(), m.getNumero(), m.getCapacidad(), m.getEstado().name(),
                            sesionId, meseroId, meseroNombre);
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    @Transactional
    public void cerrarSesionSiVacia(Long sesionId, Long usuarioId, boolean admin) {
        SesionMesaEntity sesion = sesionRepo.findById(sesionId)
                .orElseThrow(() -> new BusinessException("Sesion no encontrada", HttpStatus.NOT_FOUND));
        if (sesion.getCerradaEn() != null) {
            return;
        }
        if (!admin && !sesion.getMeseroId().equals(usuarioId)) {
            throw new BusinessException("No puedes liberar una mesa tomada por otro mesero", HttpStatus.FORBIDDEN);
        }

        List<PedidoEntity> pedidos = pedidoRepo.findBySesionMesaId(sesionId);
        boolean tienePedidoEnProceso = pedidos.stream()
                .anyMatch(p -> p.getEstado() == PedidoEntity.EstadoPedido.EN_COCINA
                        || p.getEstado() == PedidoEntity.EstadoPedido.LISTO);
        if (tienePedidoEnProceso) {
            throw new BusinessException("No se puede liberar una mesa con pedido enviado", HttpStatus.CONFLICT);
        }
        pedidos.stream()
                .filter(p -> p.getEstado() == PedidoEntity.EstadoPedido.ABIERTO)
                .forEach(p -> {
                    p.setEstado(PedidoEntity.EstadoPedido.CANCELADO);
                    p.setMotivoCancelacion("Sesion liberada sin pedido confirmado");
                    p.setCanceladoEn(java.time.LocalDateTime.now());
                    pedidoRepo.save(p);
                });
        cerrarSesion(sesionId);
    }
}
