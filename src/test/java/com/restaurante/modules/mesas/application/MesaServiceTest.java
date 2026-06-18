package com.restaurante.modules.mesas.application;

import com.restaurante.modules.auth.infrastructure.persistence.UsuarioJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.SesionMesaJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.ws.PedidoEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MesaServiceTest {

    @Test
    void listarMesasExcluyeLaMesaParaLlevar() {
        MesaJpaRepo mesaRepo = mock(MesaJpaRepo.class);
        SesionMesaJpaRepo sesionRepo = mock(SesionMesaJpaRepo.class);
        MesaEntity mesaUno = mesa(1L, "1", MesaEntity.TipoMesa.SALON);
        MesaEntity mesaDos = mesa(2L, "2", MesaEntity.TipoMesa.SALON);
        MesaEntity paraLlevar =
                mesa(3L, "Para llevar", MesaEntity.TipoMesa.PARA_LLEVAR);
        when(mesaRepo.findAllByOrderByNumeroAsc())
                .thenReturn(List.of(mesaUno, mesaDos, paraLlevar));
        when(sesionRepo.findByMesaIdAndCerradaEnIsNull(1L))
                .thenReturn(Optional.empty());
        when(sesionRepo.findByMesaIdAndCerradaEnIsNull(2L))
                .thenReturn(Optional.empty());

        MesaService service = new MesaService(
                mesaRepo,
                sesionRepo,
                mock(PedidoJpaRepo.class),
                mock(UsuarioJpaRepo.class),
                mock(PedidoEventPublisher.class)
        );

        var mesas = service.listarMesas();

        assertEquals(2, mesas.size());
        assertFalse(mesas.stream().anyMatch(m -> "Para llevar".equals(m.numero())));
    }

    private MesaEntity mesa(
            Long id,
            String numero,
            MesaEntity.TipoMesa tipo) {
        MesaEntity mesa = new MesaEntity();
        ReflectionTestUtils.setField(mesa, "id", id);
        mesa.setNumero(numero);
        mesa.setCapacidad(4);
        mesa.setTipo(tipo);
        return mesa;
    }
}
