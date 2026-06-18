package com.restaurante.modules.caja.infrastructure.web;

import com.restaurante.modules.auth.infrastructure.persistence.UsuarioJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArqueoControllerTest {

    @Test
    void precierreSeBloqueaCuandoExistenPagosPendientes() {
        ArqueoJpaRepo arqueoRepo = mock(ArqueoJpaRepo.class);
        PedidoJpaRepo pedidoRepo = mock(PedidoJpaRepo.class);
        ArqueoEntity arqueo = new ArqueoEntity();
        arqueo.setCajeroId(7L);
        arqueo.setEstado(ArqueoEntity.EstadoArqueo.ABIERTO);
        when(arqueoRepo.findById(4L)).thenReturn(Optional.of(arqueo));
        when(pedidoRepo.countByEstadoIn(any(Collection.class))).thenReturn(2L);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("7");

        ArqueoController controller = new ArqueoController(
                arqueoRepo,
                mock(ComprobanteJpaRepo.class),
                mock(UsuarioJpaRepo.class),
                mock(PasswordEncoder.class),
                pedidoRepo
        );

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> controller.registrarPrecierre(
                        4L,
                        new ArqueoController.PrecierreArqueoRequest(
                                "cajero",
                                "secreto",
                                new BigDecimal("100.00"),
                                null
                        ),
                        auth
                )
        );

        assertEquals(
                "No se puede registrar el pre-cierre porque existen 2 pagos pendientes",
                error.getMessage()
        );
    }
}
