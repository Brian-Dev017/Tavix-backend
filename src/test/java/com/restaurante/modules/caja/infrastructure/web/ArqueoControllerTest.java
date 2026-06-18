package com.restaurante.modules.caja.infrastructure.web;

import com.restaurante.modules.auth.infrastructure.persistence.UsuarioJpaRepo;
import com.restaurante.modules.auth.infrastructure.persistence.UsuarioEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ArqueoJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArqueoControllerTest {

    @Test
    void primeraAperturaValidaCredencialesDelCajeroActual() {
        ArqueoJpaRepo arqueoRepo = mock(ArqueoJpaRepo.class);
        UsuarioJpaRepo usuarioRepo = mock(UsuarioJpaRepo.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UsuarioEntity cajero = usuario(7L, "cajero", "CA");
        when(arqueoRepo.findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(
                7L, ArqueoEntity.EstadoArqueo.ABIERTO)).thenReturn(Optional.empty());
        when(arqueoRepo.countByCajeroIdAndAperturaEnBetween(any(), any(), any())).thenReturn(0L);
        when(usuarioRepo.findByUsuario("cajero")).thenReturn(Optional.of(cajero));
        when(usuarioRepo.findById(7L)).thenReturn(Optional.of(cajero));
        when(passwordEncoder.matches("secreto", cajero.getContrasenaHash())).thenReturn(true);
        when(arqueoRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArqueoController controller = controller(arqueoRepo, usuarioRepo, passwordEncoder, mock(PedidoJpaRepo.class));
        ArqueoEntity creado = controller.abrir(
                new ArqueoController.AbrirArqueoRequest(
                        "cajero", "secreto", new BigDecimal("100.00"), null),
                auth(7L, "ROLE_CA")
        ).getBody().data();

        assertEquals(ArqueoEntity.EstadoArqueo.ABIERTO, creado.getEstado());
        assertEquals(7L, creado.getCajeroId());
    }

    @Test
    void segundaAperturaRequiereCredencialesDeAdministrador() {
        ArqueoJpaRepo arqueoRepo = mock(ArqueoJpaRepo.class);
        UsuarioJpaRepo usuarioRepo = mock(UsuarioJpaRepo.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UsuarioEntity cajero = usuario(7L, "cajero", "CA");
        when(arqueoRepo.findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(
                7L, ArqueoEntity.EstadoArqueo.ABIERTO)).thenReturn(Optional.empty());
        when(arqueoRepo.countByCajeroIdAndAperturaEnBetween(any(), any(), any())).thenReturn(1L);
        when(usuarioRepo.findByUsuario("cajero")).thenReturn(Optional.of(cajero));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        ArqueoController controller = controller(arqueoRepo, usuarioRepo, passwordEncoder, mock(PedidoJpaRepo.class));

        BusinessException error = assertThrows(BusinessException.class, () -> controller.abrir(
                new ArqueoController.AbrirArqueoRequest(
                        "cajero", "secreto", new BigDecimal("100.00"), null),
                auth(7L, "ROLE_CA")
        ));

        assertEquals("La reapertura requiere credenciales de un administrador", error.getMessage());
    }

    @Test
    void segundaAperturaAceptaCredencialesDeAdministradorActivo() {
        ArqueoJpaRepo arqueoRepo = mock(ArqueoJpaRepo.class);
        UsuarioJpaRepo usuarioRepo = mock(UsuarioJpaRepo.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UsuarioEntity cajero = usuario(7L, "cajero", "CA");
        UsuarioEntity admin = usuario(1L, "admin", "AD");
        when(arqueoRepo.findTopByCajeroIdAndEstadoOrderByAperturaEnDesc(
                7L, ArqueoEntity.EstadoArqueo.ABIERTO)).thenReturn(Optional.empty());
        when(arqueoRepo.countByCajeroIdAndAperturaEnBetween(any(), any(), any())).thenReturn(1L);
        when(usuarioRepo.findByUsuario("admin")).thenReturn(Optional.of(admin));
        when(usuarioRepo.findById(7L)).thenReturn(Optional.of(cajero));
        when(passwordEncoder.matches("admin-secret", admin.getContrasenaHash())).thenReturn(true);
        when(arqueoRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArqueoController controller = controller(arqueoRepo, usuarioRepo, passwordEncoder, mock(PedidoJpaRepo.class));
        ArqueoEntity creado = controller.abrir(
                new ArqueoController.AbrirArqueoRequest(
                        "admin", "admin-secret", new BigDecimal("100.00"), null),
                auth(7L, "ROLE_CA")
        ).getBody().data();

        assertEquals(ArqueoEntity.EstadoArqueo.ABIERTO, creado.getEstado());
    }

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

    @Test
    void precierreCambiaEstadoYLiberaLaCajaDelCajero() {
        ArqueoJpaRepo arqueoRepo = mock(ArqueoJpaRepo.class);
        UsuarioJpaRepo usuarioRepo = mock(UsuarioJpaRepo.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        PedidoJpaRepo pedidoRepo = mock(PedidoJpaRepo.class);
        UsuarioEntity cajero = usuario(7L, "cajero", "CA");
        ArqueoEntity arqueo = new ArqueoEntity();
        arqueo.setCajeroId(7L);
        arqueo.setMontoApertura(new BigDecimal("50.00"));
        arqueo.setEstado(ArqueoEntity.EstadoArqueo.ABIERTO);
        when(arqueoRepo.findById(4L)).thenReturn(Optional.of(arqueo));
        when(pedidoRepo.countByEstadoIn(any(Collection.class))).thenReturn(0L);
        when(usuarioRepo.findByUsuario("cajero")).thenReturn(Optional.of(cajero));
        when(passwordEncoder.matches("secreto", cajero.getContrasenaHash())).thenReturn(true);
        when(arqueoRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArqueoController controller = controller(arqueoRepo, usuarioRepo, passwordEncoder, pedidoRepo);
        ArqueoEntity actualizado = controller.registrarPrecierre(
                4L,
                new ArqueoController.PrecierreArqueoRequest(
                        "cajero", "secreto", new BigDecimal("50.00"), null),
                auth(7L, "ROLE_CA")
        ).getBody().data();

        assertEquals(ArqueoEntity.EstadoArqueo.PRECIERRE, actualizado.getEstado());
        verify(arqueoRepo).save(arqueo);
    }

    @Test
    void soloAdminPuedeCerrarUnArqueoEnPrecierre() {
        ArqueoJpaRepo arqueoRepo = mock(ArqueoJpaRepo.class);
        ArqueoEntity arqueo = new ArqueoEntity();
        arqueo.setCajeroId(7L);
        arqueo.setEstado(ArqueoEntity.EstadoArqueo.PRECIERRE);
        when(arqueoRepo.findById(4L)).thenReturn(Optional.of(arqueo));

        ArqueoController controller = controller(
                arqueoRepo, mock(UsuarioJpaRepo.class), mock(PasswordEncoder.class), mock(PedidoJpaRepo.class));

        BusinessException error = assertThrows(BusinessException.class, () -> controller.cerrar(
                4L,
                new ArqueoController.CerrarArqueoRequest(new BigDecimal("50.00"), null),
                auth(7L, "ROLE_CA")
        ));

        assertEquals("Solo un administrador puede cerrar la caja", error.getMessage());
    }

    private static ArqueoController controller(ArqueoJpaRepo arqueoRepo,
                                               UsuarioJpaRepo usuarioRepo,
                                               PasswordEncoder passwordEncoder,
                                               PedidoJpaRepo pedidoRepo) {
        return new ArqueoController(
                arqueoRepo,
                mock(ComprobanteJpaRepo.class),
                usuarioRepo,
                passwordEncoder,
                pedidoRepo
        );
    }

    private static Authentication auth(Long userId, String authority) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(userId.toString());
        doReturn(List.of(new SimpleGrantedAuthority(authority)))
                .when(auth).getAuthorities();
        return auth;
    }

    private static UsuarioEntity usuario(Long id, String username, String rol) {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(id);
        usuario.setNombre(username);
        usuario.setApellido("Prueba");
        usuario.setUsuario(username);
        usuario.setContrasenaHash("hash-" + username);
        usuario.setRolId(rol);
        usuario.setActivo(true);
        return usuario;
    }
}
