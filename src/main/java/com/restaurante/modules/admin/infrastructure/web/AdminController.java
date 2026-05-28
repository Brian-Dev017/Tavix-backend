package com.restaurante.modules.admin.infrastructure.web;

import com.restaurante.modules.admin.infrastructure.web.dto.*;
import com.restaurante.modules.auth.domain.port.out.RefreshTokenRepositoryPort;
import com.restaurante.modules.auth.infrastructure.persistence.UsuarioEntity;
import com.restaurante.modules.auth.infrastructure.persistence.UsuarioJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.CategoriaEntity;
import com.restaurante.modules.catalogo.infrastructure.persistence.CategoriaJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoEntity;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteEntity;
import com.restaurante.modules.caja.infrastructure.persistence.ComprobanteJpaRepo;
import com.restaurante.modules.mesas.application.MesaService;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoEntity;
import com.restaurante.modules.pedidos.infrastructure.persistence.PedidoJpaRepo;
import com.restaurante.shared.audit.AuditoriaContexto;
import com.restaurante.shared.audit.AuditoriaContextoFactory;
import com.restaurante.shared.audit.AuditoriaGlobalService;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Set<String> ROLES_VALIDOS = Set.of("AD", "ME", "CO", "CA");

    private final UsuarioJpaRepo usuarioRepo;
    private final ProductoJpaRepo productoRepo;
    private final CategoriaJpaRepo categoriaRepo;
    private final MesaJpaRepo mesaRepo;
    private final PedidoJpaRepo pedidoRepo;
    private final ComprobanteJpaRepo comprobanteRepo;
    private final MesaService mesaService;
    private final RefreshTokenRepositoryPort refreshTokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaGlobalService auditoriaGlobalService;
    private final AuditoriaContextoFactory auditoriaContextoFactory;

    public AdminController(UsuarioJpaRepo usuarioRepo, ProductoJpaRepo productoRepo,
                           CategoriaJpaRepo categoriaRepo, MesaJpaRepo mesaRepo,
                           PedidoJpaRepo pedidoRepo, ComprobanteJpaRepo comprobanteRepo,
                           MesaService mesaService,
                           RefreshTokenRepositoryPort refreshTokenRepo,
                           PasswordEncoder passwordEncoder,
                           AuditoriaGlobalService auditoriaGlobalService,
                           AuditoriaContextoFactory auditoriaContextoFactory) {
        this.usuarioRepo = usuarioRepo;
        this.productoRepo = productoRepo;
        this.categoriaRepo = categoriaRepo;
        this.mesaRepo = mesaRepo;
        this.pedidoRepo = pedidoRepo;
        this.comprobanteRepo = comprobanteRepo;
        this.mesaService = mesaService;
        this.refreshTokenRepo = refreshTokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaGlobalService = auditoriaGlobalService;
        this.auditoriaContextoFactory = auditoriaContextoFactory;
    }

    @GetMapping("/usuarios")
    public ResponseEntity<ApiResponse<List<UsuarioAdminDTO>>> listarUsuarios() {
        List<UsuarioAdminDTO> usuarios = usuarioRepo.findAll().stream()
                .map(u -> new UsuarioAdminDTO(u.getId(), u.getNombre(), u.getApellido(),
                        u.getUsuario(), u.getRolId(), u.isActivo()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(usuarios));
    }

    @PatchMapping("/pedidos/{id}/cancelar")
    public ResponseEntity<ApiResponse<Void>> cancelarPedido(
            @PathVariable Long id,
            @RequestBody CancelarPedidoRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {
        if (request == null || request.motivo() == null || request.motivo().isBlank()) {
            throw new BusinessException("El motivo de anulacion es obligatorio", HttpStatus.BAD_REQUEST);
        }
        PedidoEntity pedido = pedidoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado", HttpStatus.NOT_FOUND));
        if (pedido.getEstado() == PedidoEntity.EstadoPedido.COBRADO) {
            throw new BusinessException("No se puede anular un pedido cobrado", HttpStatus.CONFLICT);
        }
        Map<String, Object> antes = snapshotPedido(pedido);
        pedido.setEstado(PedidoEntity.EstadoPedido.CANCELADO);
        pedido.setMotivoCancelacion(request.motivo().trim());
        pedido.setCanceladoEn(LocalDateTime.now());
        PedidoEntity saved = pedidoRepo.save(pedido);
        if (pedido.getSesionMesaId() != null) {
            mesaService.cerrarSesion(pedido.getSesionMesaId());
        }
        registrar(
                "pedido",
                saved.getId(),
                "CAMBIO_ESTADO",
                "Pedido anulado desde administracion",
                antes,
                snapshotPedido(saved),
                auth,
                httpRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Pedido anulado", null));
    }

    @PostMapping("/usuarios")
    public ResponseEntity<ApiResponse<UsuarioAdminDTO>> crearUsuario(@RequestBody CrearUsuarioRequest req,
                                                                     Authentication auth,
                                                                     HttpServletRequest httpRequest) {
        if (req.nombre() == null || req.nombre().isBlank()
                || req.apellido() == null || req.apellido().isBlank()
                || req.usuario() == null || req.usuario().isBlank()
                || req.contrasena() == null || req.contrasena().length() < 6) {
            throw new BusinessException("Datos invalidos: nombre, apellido, usuario y contrasena (min. 6 chars) son requeridos", HttpStatus.BAD_REQUEST);
        }
        if (!ROLES_VALIDOS.contains(req.rolId())) {
            throw new BusinessException("Rol invalido: " + req.rolId(), HttpStatus.BAD_REQUEST);
        }
        if (usuarioRepo.findByUsuario(req.usuario()).isPresent()) {
            throw new BusinessException("El nombre de usuario ya existe", HttpStatus.CONFLICT);
        }
        UsuarioEntity u = new UsuarioEntity();
        u.setNombre(req.nombre());
        u.setApellido(req.apellido());
        u.setUsuario(req.usuario());
        u.setContrasenaHash(passwordEncoder.encode(req.contrasena()));
        u.setRolId(req.rolId());
        u.setActivo(true);
        UsuarioEntity saved = usuarioRepo.save(u);
        registrar("usuario", saved.getId(), "CREAR", "Creacion de usuario", null, saved, auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(new UsuarioAdminDTO(
                saved.getId(), saved.getNombre(), saved.getApellido(),
                saved.getUsuario(), saved.getRolId(), saved.isActivo())));
    }

    @PutMapping("/usuarios/{id}")
    public ResponseEntity<ApiResponse<UsuarioAdminDTO>> actualizarUsuario(
            @PathVariable Long id, @RequestBody ActualizarUsuarioRequest req,
            Authentication auth, HttpServletRequest httpRequest) {
        UsuarioEntity u = usuarioRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (req.rolId() != null && !ROLES_VALIDOS.contains(req.rolId())) {
            throw new BusinessException("Rol invalido: " + req.rolId(), HttpStatus.BAD_REQUEST);
        }
        Long selfId = Long.parseLong(auth.getName());
        if (id.equals(selfId) && Boolean.FALSE.equals(req.activo())) {
            throw new BusinessException("No puedes desactivar tu propia cuenta", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> antes = snapshotUsuario(u);
        if (req.nombre() != null) u.setNombre(req.nombre());
        if (req.apellido() != null) u.setApellido(req.apellido());
        if (req.rolId() != null) u.setRolId(req.rolId());
        if (req.activo() != null) u.setActivo(req.activo());
        UsuarioEntity saved = usuarioRepo.save(u);
        if (Boolean.FALSE.equals(req.activo())) {
            refreshTokenRepo.revokarPorUsuario(id);
        }
        registrar("usuario", saved.getId(), "ACTUALIZAR", "Actualizacion de usuario",
                antes, snapshotUsuario(saved), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(new UsuarioAdminDTO(
                saved.getId(), saved.getNombre(), saved.getApellido(),
                saved.getUsuario(), saved.getRolId(), saved.isActivo())));
    }

    @PatchMapping("/usuarios/{id}/contrasena")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id, @RequestBody ResetPasswordRequest req,
            Authentication auth, HttpServletRequest httpRequest) {
        UsuarioEntity u = usuarioRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (req.claveAnterior() == null || !passwordEncoder.matches(req.claveAnterior(), u.getContrasenaHash())) {
            throw new BusinessException("La clave anterior no es correcta", HttpStatus.FORBIDDEN);
        }
        if (req.nuevaContrasena() == null || req.nuevaContrasena().length() < 6) {
            throw new BusinessException("La nueva contrasena debe tener al menos 6 caracteres", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> antes = Map.of("passwordActualizada", false);
        u.setContrasenaHash(passwordEncoder.encode(req.nuevaContrasena()));
        usuarioRepo.save(u);
        refreshTokenRepo.revokarPorUsuario(id);
        registrar("usuario", u.getId(), "ACTUALIZAR", "Restablecimiento de contrasena",
                antes, Map.of("passwordActualizada", true), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/usuarios/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarUsuario(@PathVariable Long id,
                                                             Authentication auth,
                                                             HttpServletRequest httpRequest) {
        Long selfId = Long.parseLong(auth.getName());
        if (id.equals(selfId)) {
            throw new BusinessException("No puedes desactivar tu propia cuenta", HttpStatus.BAD_REQUEST);
        }
        UsuarioEntity u = usuarioRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        Map<String, Object> antes = snapshotUsuario(u);
        u.setActivo(false);
        usuarioRepo.save(u);
        refreshTokenRepo.revokarPorUsuario(id);
        registrar("usuario", u.getId(), "ELIMINAR_LOGICO", "Desactivacion de usuario",
                antes, snapshotUsuario(u), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/productos")
    public ResponseEntity<ApiResponse<List<ProductoAdminDTO>>> listarProductos() {
        Map<Long, String> catNombres = categoriaRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(CategoriaEntity::getId, CategoriaEntity::getNombre));
        List<ProductoAdminDTO> productos = productoRepo.findAll().stream()
                .map(p -> new ProductoAdminDTO(p.getId(), p.getCategoriaId(),
                        catNombres.getOrDefault(p.getCategoriaId(), "?"),
                        p.getNombre(), p.getDescripcion(), p.getPrecio(),
                        p.getImagenUrl(), p.isDisponible()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(productos));
    }

    @PostMapping("/productos")
    public ResponseEntity<ApiResponse<ProductoAdminDTO>> crearProducto(@RequestBody GuardarProductoRequest req,
                                                                       Authentication auth,
                                                                       HttpServletRequest httpRequest) {
        String catNombre = categoriaRepo.findById(req.categoriaId())
                .map(CategoriaEntity::getNombre)
                .orElseThrow(() -> new BusinessException("Categoria no encontrada", HttpStatus.NOT_FOUND));
        ProductoEntity p = new ProductoEntity();
        p.setCategoriaId(req.categoriaId());
        p.setNombre(req.nombre());
        p.setDescripcion(req.descripcion());
        p.setPrecio(req.precio());
        p.setImagenUrl(req.imagenUrl());
        p.setDisponible(req.disponible());
        ProductoEntity saved = productoRepo.save(p);
        registrar("producto", saved.getId(), "CREAR", "Creacion de producto", null, saved, auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(new ProductoAdminDTO(saved.getId(), saved.getCategoriaId(),
                catNombre, saved.getNombre(), saved.getDescripcion(), saved.getPrecio(),
                saved.getImagenUrl(), saved.isDisponible())));
    }

    @PutMapping("/productos/{id}")
    public ResponseEntity<ApiResponse<ProductoAdminDTO>> actualizarProducto(
            @PathVariable Long id, @RequestBody GuardarProductoRequest req,
            Authentication auth, HttpServletRequest httpRequest) {
        ProductoEntity p = productoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Producto no encontrado", HttpStatus.NOT_FOUND));
        if (!p.isDisponible()) {
            throw new BusinessException("No se puede editar un producto no disponible", HttpStatus.CONFLICT);
        }
        String catNombre = categoriaRepo.findById(req.categoriaId())
                .map(CategoriaEntity::getNombre)
                .orElseThrow(() -> new BusinessException("Categoria no encontrada", HttpStatus.NOT_FOUND));
        Map<String, Object> antes = snapshotProducto(p);
        p.setCategoriaId(req.categoriaId());
        p.setNombre(req.nombre());
        p.setDescripcion(req.descripcion());
        p.setPrecio(req.precio());
        p.setImagenUrl(req.imagenUrl());
        p.setDisponible(req.disponible());
        ProductoEntity saved = productoRepo.save(p);
        registrar("producto", saved.getId(), "ACTUALIZAR", "Actualizacion de producto",
                antes, snapshotProducto(saved), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(new ProductoAdminDTO(saved.getId(), saved.getCategoriaId(),
                catNombre, saved.getNombre(), saved.getDescripcion(), saved.getPrecio(),
                saved.getImagenUrl(), saved.isDisponible())));
    }

    @PatchMapping("/productos/{id}/disponibilidad")
    public ResponseEntity<ApiResponse<Void>> toggleDisponibilidad(@PathVariable Long id,
                                                                  Authentication auth,
                                                                  HttpServletRequest httpRequest) {
        ProductoEntity p = productoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Producto no encontrado", HttpStatus.NOT_FOUND));
        Map<String, Object> antes = snapshotProducto(p);
        p.setDisponible(!p.isDisponible());
        productoRepo.save(p);
        registrar("producto", p.getId(), "CAMBIO_ESTADO", "Cambio de disponibilidad de producto",
                antes, snapshotProducto(p), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/productos/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarProducto(@PathVariable Long id,
                                                              Authentication auth,
                                                              HttpServletRequest httpRequest) {
        ProductoEntity producto = productoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Producto no encontrado", HttpStatus.NOT_FOUND));
        Map<String, Object> antes = snapshotProducto(producto);
        producto.setDisponible(false);
        productoRepo.save(producto);
        registrar("producto", producto.getId(), "ELIMINAR_LOGICO", "Desactivacion de producto",
                antes, snapshotProducto(producto), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Producto desactivado", null));
    }

    public record ComprobanteAnulacionDTO(
            Long id,
            Long pedidoId,
            String tipoComprobante,
            String serie,
            Integer numero,
            String metodoPago,
            java.math.BigDecimal total,
            String estado,
            LocalDateTime pagadoEn
    ) {}

    public record AnularComprobanteRequest(String motivo) {}

    @GetMapping("/comprobantes/emitidos")
    public ResponseEntity<ApiResponse<List<ComprobanteAnulacionDTO>>> listarComprobantesEmitidos() {
        List<ComprobanteAnulacionDTO> comprobantes = comprobanteRepo
                .findByTipoComprobanteIdInAndEstadoOrderByPagadoEnDesc(
                        List.of("B", "F"), ComprobanteEntity.EstadoComprobante.COMPLETADO)
                .stream()
                .map(c -> new ComprobanteAnulacionDTO(
                        c.getId(), c.getPedidoId(), nombreTipoComprobante(c.getTipoComprobanteId()),
                        c.getSerie(), c.getNumero(), c.getMetodoPago().name(), c.getTotal(),
                        c.getEstado().name(), c.getPagadoEn()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(comprobantes));
    }

    @GetMapping("/comprobantes/emitidos/buscar")
    public ResponseEntity<ApiResponse<List<ComprobanteAnulacionDTO>>> buscarComprobantesEmitidosPorNumero(
            @RequestParam Integer numero) {
        if (numero == null || numero <= 0) {
            throw new BusinessException("El numero de comprobante debe ser mayor a cero", HttpStatus.BAD_REQUEST);
        }
        List<ComprobanteAnulacionDTO> comprobantes = comprobanteRepo
                .findByTipoComprobanteIdInAndEstadoAndNumeroOrderByPagadoEnDesc(
                        List.of("B", "F"), ComprobanteEntity.EstadoComprobante.COMPLETADO, numero)
                .stream()
                .map(c -> new ComprobanteAnulacionDTO(
                        c.getId(), c.getPedidoId(), nombreTipoComprobante(c.getTipoComprobanteId()),
                        c.getSerie(), c.getNumero(), c.getMetodoPago().name(), c.getTotal(),
                        c.getEstado().name(), c.getPagadoEn()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(comprobantes));
    }

    @PatchMapping("/comprobantes/{id}/anular")
    public ResponseEntity<ApiResponse<Void>> anularComprobante(
            @PathVariable Long id,
            @RequestBody AnularComprobanteRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {
        if (request == null || request.motivo() == null || request.motivo().isBlank()) {
            throw new BusinessException("El motivo de anulacion es obligatorio", HttpStatus.BAD_REQUEST);
        }
        ComprobanteEntity comp = comprobanteRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Comprobante no encontrado", HttpStatus.NOT_FOUND));
        if (!"B".equals(comp.getTipoComprobanteId()) && !"F".equals(comp.getTipoComprobanteId())) {
            throw new BusinessException("Solo se anulan boletas o facturas desde este flujo", HttpStatus.BAD_REQUEST);
        }
        if (comp.getEstado() != ComprobanteEntity.EstadoComprobante.COMPLETADO) {
            throw new BusinessException("Solo se puede anular un comprobante completado", HttpStatus.CONFLICT);
        }
        Map<String, Object> antes = snapshotComprobante(comp);
        comp.setEstado(ComprobanteEntity.EstadoComprobante.ANULADO);
        comp.setMotivoAnulacion(request.motivo().trim());
        comp.setAnuladoEn(LocalDateTime.now());
        comprobanteRepo.save(comp);
        registrar("comprobante_venta", comp.getId(), "CAMBIO_ESTADO", "Anulacion de comprobante",
                antes, snapshotComprobante(comp), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Comprobante anulado", null));
    }

    private String nombreTipoComprobante(String tipo) {
        return switch (tipo) {
            case "B" -> "Boleta";
            case "F" -> "Factura";
            default -> "Ticket";
        };
    }

    @GetMapping("/categorias")
    public ResponseEntity<ApiResponse<List<CategoriaAdminDTO>>> listarCategorias() {
        List<CategoriaAdminDTO> cats = categoriaRepo.findAll().stream()
                .map(c -> new CategoriaAdminDTO(c.getId(), c.getNombre(), c.getDescripcion(), c.isActivo()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(cats));
    }

    @PostMapping("/categorias")
    public ResponseEntity<ApiResponse<CategoriaAdminDTO>> crearCategoria(
            @RequestBody GuardarCategoriaRequest req,
            Authentication auth,
            HttpServletRequest httpRequest) {
        if (req.nombre() == null || req.nombre().isBlank()) {
            throw new BusinessException("El nombre es requerido", HttpStatus.BAD_REQUEST);
        }
        CategoriaEntity c = new CategoriaEntity();
        c.setNombre(req.nombre());
        c.setDescripcion(req.descripcion());
        c.setActivo(true);
        CategoriaEntity saved = categoriaRepo.save(c);
        registrar("categoria", saved.getId(), "CREAR", "Creacion de categoria", null, saved, auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(
                new CategoriaAdminDTO(saved.getId(), saved.getNombre(), saved.getDescripcion(), saved.isActivo())));
    }

    @PutMapping("/categorias/{id}")
    public ResponseEntity<ApiResponse<CategoriaAdminDTO>> actualizarCategoria(
            @PathVariable Long id, @RequestBody GuardarCategoriaRequest req,
            Authentication auth, HttpServletRequest httpRequest) {
        CategoriaEntity c = categoriaRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Categoria no encontrada", HttpStatus.NOT_FOUND));
        Map<String, Object> antes = snapshotCategoria(c);
        if (req.nombre() != null && !req.nombre().isBlank()) c.setNombre(req.nombre());
        if (req.descripcion() != null) c.setDescripcion(req.descripcion());
        CategoriaEntity saved = categoriaRepo.save(c);
        registrar("categoria", saved.getId(), "ACTUALIZAR", "Actualizacion de categoria",
                antes, snapshotCategoria(saved), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(
                new CategoriaAdminDTO(saved.getId(), saved.getNombre(), saved.getDescripcion(), saved.isActivo())));
    }

    @DeleteMapping("/categorias/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarCategoria(@PathVariable Long id,
                                                               Authentication auth,
                                                               HttpServletRequest httpRequest) {
        CategoriaEntity categoria = categoriaRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Categoria no encontrada", HttpStatus.NOT_FOUND));
        Map<String, Object> antes = snapshotCategoria(categoria);
        categoria.setActivo(false);
        categoriaRepo.save(categoria);
        registrar("categoria", categoria.getId(), "ELIMINAR_LOGICO", "Desactivacion de categoria",
                antes, snapshotCategoria(categoria), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Categoria desactivada", null));
    }

    @GetMapping("/mesas")
    public ResponseEntity<ApiResponse<List<MesaAdminDTO>>> listarMesasAdmin() {
        List<MesaAdminDTO> mesas = mesaRepo.findAllByOrderByNumeroAsc().stream()
                .map(m -> new MesaAdminDTO(m.getId(), m.getNumero(), m.getCapacidad(), m.getEstado().name()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(mesas));
    }

    @PostMapping("/mesas")
    public ResponseEntity<ApiResponse<MesaAdminDTO>> crearMesa(@RequestBody GuardarMesaRequest req,
                                                               Authentication auth,
                                                               HttpServletRequest httpRequest) {
        if (req.numero() == null || req.numero().isBlank() || req.numero().length() > 5) {
            throw new BusinessException("Numero de mesa requerido (max. 5 caracteres)", HttpStatus.BAD_REQUEST);
        }
        if (req.capacidad() < 1) {
            throw new BusinessException("La capacidad debe ser al menos 1", HttpStatus.BAD_REQUEST);
        }
        if (mesaRepo.findAll().stream().anyMatch(m -> m.getNumero().equalsIgnoreCase(req.numero()))) {
            throw new BusinessException("Ya existe una mesa con ese numero", HttpStatus.CONFLICT);
        }
        MesaEntity mesa = new MesaEntity();
        mesa.setNumero(req.numero().toUpperCase());
        mesa.setCapacidad(req.capacidad());
        MesaEntity saved = mesaRepo.save(mesa);
        registrar("mesa", saved.getId(), "CREAR", "Creacion de mesa", null, saved, auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(
                new MesaAdminDTO(saved.getId(), saved.getNumero(), saved.getCapacidad(), saved.getEstado().name())));
    }

    @PutMapping("/mesas/{id}")
    public ResponseEntity<ApiResponse<MesaAdminDTO>> actualizarMesa(
            @PathVariable Long id, @RequestBody GuardarMesaRequest req,
            Authentication auth, HttpServletRequest httpRequest) {
        MesaEntity mesa = mesaRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada", HttpStatus.NOT_FOUND));
        if (mesa.getEstado() == MesaEntity.EstadoMesa.OCUPADA) {
            throw new BusinessException("No se puede editar una mesa ocupada", HttpStatus.CONFLICT);
        }
        Map<String, Object> antes = snapshotMesa(mesa);
        if (req.numero() != null && !req.numero().isBlank()) {
            String nuevoNum = req.numero().toUpperCase();
            boolean duplicado = mesaRepo.findAll().stream()
                    .anyMatch(m -> !m.getId().equals(id) && m.getNumero().equalsIgnoreCase(nuevoNum));
            if (duplicado) {
                throw new BusinessException("Ya existe una mesa con ese numero", HttpStatus.CONFLICT);
            }
            mesa.setNumero(nuevoNum);
        }
        if (req.capacidad() >= 1) mesa.setCapacidad(req.capacidad());
        MesaEntity saved = mesaRepo.save(mesa);
        registrar("mesa", saved.getId(), "ACTUALIZAR", "Actualizacion de mesa",
                antes, snapshotMesa(saved), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(
                new MesaAdminDTO(saved.getId(), saved.getNumero(), saved.getCapacidad(), saved.getEstado().name())));
    }

    @PatchMapping("/mesas/{id}/estado")
    public ResponseEntity<ApiResponse<MesaAdminDTO>> toggleEstadoMesa(@PathVariable Long id,
                                                                      Authentication auth,
                                                                      HttpServletRequest httpRequest) {
        MesaEntity mesa = mesaRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada", HttpStatus.NOT_FOUND));
        if (mesa.getEstado() == MesaEntity.EstadoMesa.OCUPADA
                || mesa.getEstado() == MesaEntity.EstadoMesa.RESERVADA) {
            throw new BusinessException("No se puede cambiar el estado de una mesa ocupada o reservada", HttpStatus.CONFLICT);
        }
        Map<String, Object> antes = snapshotMesa(mesa);
        MesaEntity.EstadoMesa nuevoEstado = mesa.getEstado() == MesaEntity.EstadoMesa.DISPONIBLE
                ? MesaEntity.EstadoMesa.INACTIVA
                : MesaEntity.EstadoMesa.DISPONIBLE;
        mesa.setEstado(nuevoEstado);
        MesaEntity saved = mesaRepo.save(mesa);
        registrar("mesa", saved.getId(), "CAMBIO_ESTADO", "Cambio de estado de mesa",
                antes, snapshotMesa(saved), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(
                new MesaAdminDTO(saved.getId(), saved.getNumero(), saved.getCapacidad(), saved.getEstado().name())));
    }

    @DeleteMapping("/mesas/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarMesa(@PathVariable Long id,
                                                          Authentication auth,
                                                          HttpServletRequest httpRequest) {
        MesaEntity mesa = mesaRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada", HttpStatus.NOT_FOUND));
        if (mesa.getEstado() == MesaEntity.EstadoMesa.OCUPADA) {
            throw new BusinessException("No se puede eliminar una mesa ocupada", HttpStatus.CONFLICT);
        }
        Map<String, Object> antes = snapshotMesa(mesa);
        mesa.setEstado(MesaEntity.EstadoMesa.INACTIVA);
        mesaRepo.save(mesa);
        registrar("mesa", mesa.getId(), "ELIMINAR_LOGICO", "Desactivacion de mesa",
                antes, snapshotMesa(mesa), auth, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Mesa desactivada", null));
    }

    private void registrar(String tabla, Long registroId, String accion, String descripcion,
                           Object valorAnterior, Object valorNuevo,
                           Authentication auth, HttpServletRequest httpRequest) {
        AuditoriaContexto contexto = auditoriaContextoFactory.from(httpRequest, auth);
        auditoriaGlobalService.registrar(
                "admin",
                tabla,
                String.valueOf(registroId),
                accion,
                descripcion,
                valorAnterior,
                valorNuevo,
                contexto
        );
    }

    private Map<String, Object> snapshotUsuario(UsuarioEntity usuario) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", usuario.getId());
        snapshot.put("nombre", usuario.getNombre());
        snapshot.put("apellido", usuario.getApellido());
        snapshot.put("usuario", usuario.getUsuario());
        snapshot.put("rolId", usuario.getRolId());
        snapshot.put("activo", usuario.isActivo());
        snapshot.put("contrasenaHash", usuario.getContrasenaHash());
        return snapshot;
    }

    private Map<String, Object> snapshotProducto(ProductoEntity producto) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", producto.getId());
        snapshot.put("categoriaId", producto.getCategoriaId());
        snapshot.put("nombre", producto.getNombre());
        snapshot.put("descripcion", producto.getDescripcion());
        snapshot.put("precio", producto.getPrecio());
        snapshot.put("imagenUrl", producto.getImagenUrl());
        snapshot.put("disponible", producto.isDisponible());
        return snapshot;
    }

    private Map<String, Object> snapshotCategoria(CategoriaEntity categoria) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", categoria.getId());
        snapshot.put("nombre", categoria.getNombre());
        snapshot.put("descripcion", categoria.getDescripcion());
        snapshot.put("activo", categoria.isActivo());
        return snapshot;
    }

    private Map<String, Object> snapshotMesa(MesaEntity mesa) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", mesa.getId());
        snapshot.put("numero", mesa.getNumero());
        snapshot.put("capacidad", mesa.getCapacidad());
        snapshot.put("estado", mesa.getEstado() != null ? mesa.getEstado().name() : null);
        return snapshot;
    }

    private Map<String, Object> snapshotPedido(PedidoEntity pedido) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", pedido.getId());
        snapshot.put("sesionMesaId", pedido.getSesionMesaId());
        snapshot.put("estado", pedido.getEstado() != null ? pedido.getEstado().name() : null);
        snapshot.put("motivoCancelacion", pedido.getMotivoCancelacion());
        snapshot.put("canceladoEn", pedido.getCanceladoEn());
        return snapshot;
    }

    private Map<String, Object> snapshotComprobante(ComprobanteEntity comprobante) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", comprobante.getId());
        snapshot.put("pedidoId", comprobante.getPedidoId());
        snapshot.put("tipoComprobanteId", comprobante.getTipoComprobanteId());
        snapshot.put("serie", comprobante.getSerie());
        snapshot.put("numero", comprobante.getNumero());
        snapshot.put("metodoPago", comprobante.getMetodoPago() != null ? comprobante.getMetodoPago().name() : null);
        snapshot.put("total", comprobante.getTotal());
        snapshot.put("estado", comprobante.getEstado() != null ? comprobante.getEstado().name() : null);
        snapshot.put("motivoAnulacion", comprobante.getMotivoAnulacion());
        snapshot.put("anuladoEn", comprobante.getAnuladoEn());
        snapshot.put("pagadoEn", comprobante.getPagadoEn());
        return snapshot;
    }
}
