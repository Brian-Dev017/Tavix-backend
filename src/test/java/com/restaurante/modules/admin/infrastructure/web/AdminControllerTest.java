package com.restaurante.modules.admin.infrastructure.web;

import com.restaurante.modules.admin.infrastructure.web.dto.GuardarProductoRequest;
import com.restaurante.modules.auth.domain.port.out.RefreshTokenRepositoryPort;
import com.restaurante.modules.auth.infrastructure.persistence.UsuarioJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.CategoriaEntity;
import com.restaurante.modules.catalogo.infrastructure.persistence.CategoriaJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoEntity;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.mesas.application.MesaService;
import com.restaurante.modules.admin.infrastructure.web.dto.GuardarMesaRequest;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.DetallePedidoJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.shared.audit.AuditoriaContextoFactory;
import com.restaurante.shared.audit.AuditoriaGlobalService;
import com.restaurante.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    private ProductoJpaRepo productoRepo;
    private CategoriaJpaRepo categoriaRepo;
    private DetallePedidoJpaRepo detalleRepo;
    private MesaJpaRepo mesaRepo;
    private AdminController controller;
    private Authentication auth;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        productoRepo = mock(ProductoJpaRepo.class);
        categoriaRepo = mock(CategoriaJpaRepo.class);
        detalleRepo = mock(DetallePedidoJpaRepo.class);
        mesaRepo = mock(MesaJpaRepo.class);
        controller = new AdminController(
                mock(UsuarioJpaRepo.class),
                productoRepo,
                categoriaRepo,
                mesaRepo,
                mock(PedidoJpaRepo.class),
                mock(ComprobanteJpaRepo.class),
                mock(MesaService.class),
                detalleRepo,
                mock(RefreshTokenRepositoryPort.class),
                mock(PasswordEncoder.class),
                mock(AuditoriaGlobalService.class),
                mock(AuditoriaContextoFactory.class)
        );
        auth = mock(Authentication.class);
        request = mock(HttpServletRequest.class);
    }

    @Test
    void crearBebidaFriaNoRequiereCocina() {
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setNombre("Bebidas frías");
        ProductoEntity guardado = new ProductoEntity();
        ReflectionTestUtils.setField(guardado, "id", 21L);
        when(categoriaRepo.findById(3L)).thenReturn(Optional.of(categoria));
        when(productoRepo.save(any(ProductoEntity.class))).thenAnswer(invocation -> {
            ProductoEntity producto = invocation.getArgument(0);
            guardado.setCategoriaId(producto.getCategoriaId());
            guardado.setNombre(producto.getNombre());
            guardado.setPrecio(producto.getPrecio());
            guardado.setRequiereCocina(producto.isRequiereCocina());
            return guardado;
        });

        controller.crearProducto(
                new GuardarProductoRequest(
                        3L, "Limonada", "", new BigDecimal("8.50"), "", true
                ),
                auth,
                request
        );

        assertFalse(guardado.isRequiereCocina());
    }

    @Test
    void eliminarProductoBorraRealmenteCuandoNoTienePedidos() {
        ProductoEntity producto = new ProductoEntity();
        ReflectionTestUtils.setField(producto, "id", 9L);
        when(productoRepo.findById(9L)).thenReturn(Optional.of(producto));
        when(detalleRepo.existsByProductoId(9L)).thenReturn(false);

        controller.eliminarProducto(9L, auth, request);

        verify(productoRepo).delete(producto);
    }

    @Test
    void eliminarProductoExplicaCuandoTienePedidos() {
        ProductoEntity producto = new ProductoEntity();
        ReflectionTestUtils.setField(producto, "id", 9L);
        when(productoRepo.findById(9L)).thenReturn(Optional.of(producto));
        when(detalleRepo.existsByProductoId(9L)).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> controller.eliminarProducto(9L, auth, request)
        );
        verify(productoRepo, never()).delete(any());
    }

    @Test
    void actualizarMesaParaLlevarCambiaCapacidadYConservaNombre() {
        MesaEntity mesa = mesaParaLlevar(MesaEntity.EstadoMesa.DISPONIBLE);
        when(mesaRepo.findById(15L)).thenReturn(Optional.of(mesa));
        when(mesaRepo.save(mesa)).thenReturn(mesa);

        controller.actualizarMesa(
                15L,
                new GuardarMesaRequest("99", 6),
                auth,
                request
        );

        assertEquals("Para llevar", mesa.getNumero());
        assertEquals(6, mesa.getCapacidad());
    }

    @Test
    void toggleMesaParaLlevarAlternaDisponibleEInactiva() {
        MesaEntity mesa = mesaParaLlevar(MesaEntity.EstadoMesa.DISPONIBLE);
        when(mesaRepo.findById(15L)).thenReturn(Optional.of(mesa));
        when(mesaRepo.save(mesa)).thenReturn(mesa);

        controller.toggleEstadoMesa(15L, auth, request);
        assertEquals(MesaEntity.EstadoMesa.INACTIVA, mesa.getEstado());

        controller.toggleEstadoMesa(15L, auth, request);
        assertEquals(MesaEntity.EstadoMesa.DISPONIBLE, mesa.getEstado());
    }

    @Test
    void mesaParaLlevarOcupadaNoPuedeEditarseNiCambiarEstado() {
        MesaEntity mesa = mesaParaLlevar(MesaEntity.EstadoMesa.OCUPADA);
        when(mesaRepo.findById(15L)).thenReturn(Optional.of(mesa));

        assertThrows(
                BusinessException.class,
                () -> controller.actualizarMesa(
                        15L,
                        new GuardarMesaRequest("Para llevar", 6),
                        auth,
                        request
                )
        );
        assertThrows(
                BusinessException.class,
                () -> controller.toggleEstadoMesa(15L, auth, request)
        );
    }

    @Test
    void mesaParaLlevarNuncaPuedeEliminarse() {
        MesaEntity mesa = mesaParaLlevar(MesaEntity.EstadoMesa.DISPONIBLE);
        when(mesaRepo.findById(15L)).thenReturn(Optional.of(mesa));

        assertThrows(
                BusinessException.class,
                () -> controller.eliminarMesa(15L, auth, request)
        );
    }

    private MesaEntity mesaParaLlevar(MesaEntity.EstadoMesa estado) {
        MesaEntity mesa = new MesaEntity();
        ReflectionTestUtils.setField(mesa, "id", 15L);
        mesa.setNumero("Para llevar");
        mesa.setCapacidad(1);
        mesa.setTipo(MesaEntity.TipoMesa.PARA_LLEVAR);
        mesa.setEstado(estado);
        return mesa;
    }
}
