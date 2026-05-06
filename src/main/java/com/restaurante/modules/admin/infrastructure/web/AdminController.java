package com.restaurante.modules.admin.infrastructure.web;

import com.restaurante.modules.admin.infrastructure.web.dto.*;
import com.restaurante.modules.auth.domain.port.out.RefreshTokenRepositoryPort;
import com.restaurante.modules.auth.infrastructure.persistence.UsuarioEntity;
import com.restaurante.modules.auth.infrastructure.persistence.UsuarioJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.CategoriaJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoEntity;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaEntity;
import com.restaurante.modules.mesas.infrastructure.persistence.MesaJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UsuarioJpaRepo usuarioRepo;
    private final ProductoJpaRepo productoRepo;
    private final CategoriaJpaRepo categoriaRepo;
    private final MesaJpaRepo mesaRepo;
    private final RefreshTokenRepositoryPort refreshTokenRepo;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UsuarioJpaRepo usuarioRepo, ProductoJpaRepo productoRepo,
                           CategoriaJpaRepo categoriaRepo, MesaJpaRepo mesaRepo,
                           RefreshTokenRepositoryPort refreshTokenRepo,
                           PasswordEncoder passwordEncoder) {
        this.usuarioRepo = usuarioRepo;
        this.productoRepo = productoRepo;
        this.categoriaRepo = categoriaRepo;
        this.mesaRepo = mesaRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ─── USUARIOS ───────────────────────────────────────────────────────────

    @GetMapping("/usuarios")
    public ResponseEntity<ApiResponse<List<UsuarioAdminDTO>>> listarUsuarios() {
        List<UsuarioAdminDTO> usuarios = usuarioRepo.findAll().stream()
                .map(u -> new UsuarioAdminDTO(u.getId(), u.getNombre(), u.getApellido(),
                        u.getUsuario(), u.getRolId(), u.isActivo()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(usuarios));
    }

    private static final Set<String> ROLES_VALIDOS = Set.of("AD", "ME", "CO", "CA");

    @PostMapping("/usuarios")
    public ResponseEntity<ApiResponse<UsuarioAdminDTO>> crearUsuario(@RequestBody CrearUsuarioRequest req) {
        if (req.nombre() == null || req.nombre().isBlank() ||
            req.apellido() == null || req.apellido().isBlank() ||
            req.usuario() == null || req.usuario().isBlank() ||
            req.contrasena() == null || req.contrasena().length() < 6) {
            throw new BusinessException("Datos inválidos: nombre, apellido, usuario y contraseña (mín. 6 chars) son requeridos", HttpStatus.BAD_REQUEST);
        }
        if (!ROLES_VALIDOS.contains(req.rolId())) {
            throw new BusinessException("Rol inválido: " + req.rolId(), HttpStatus.BAD_REQUEST);
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
        return ResponseEntity.ok(ApiResponse.ok(new UsuarioAdminDTO(
                saved.getId(), saved.getNombre(), saved.getApellido(),
                saved.getUsuario(), saved.getRolId(), saved.isActivo())));
    }

    @PutMapping("/usuarios/{id}")
    public ResponseEntity<ApiResponse<UsuarioAdminDTO>> actualizarUsuario(
            @PathVariable Long id, @RequestBody ActualizarUsuarioRequest req,
            Authentication auth) {
        UsuarioEntity u = usuarioRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (req.rolId() != null && !ROLES_VALIDOS.contains(req.rolId())) {
            throw new BusinessException("Rol inválido: " + req.rolId(), HttpStatus.BAD_REQUEST);
        }
        // Evitar que el admin se desactive a sí mismo
        Long selfId = Long.parseLong(auth.getName());
        if (id.equals(selfId) && Boolean.FALSE.equals(req.activo())) {
            throw new BusinessException("No puedes desactivar tu propia cuenta", HttpStatus.BAD_REQUEST);
        }
        if (req.nombre() != null) u.setNombre(req.nombre());
        if (req.apellido() != null) u.setApellido(req.apellido());
        if (req.rolId() != null) u.setRolId(req.rolId());
        if (req.activo() != null) u.setActivo(req.activo());
        UsuarioEntity saved = usuarioRepo.save(u);
        if (Boolean.FALSE.equals(req.activo())) {
            refreshTokenRepo.revokarPorUsuario(id);
        }
        return ResponseEntity.ok(ApiResponse.ok(new UsuarioAdminDTO(
                saved.getId(), saved.getNombre(), saved.getApellido(),
                saved.getUsuario(), saved.getRolId(), saved.isActivo())));
    }

    @PatchMapping("/usuarios/{id}/contrasena")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id, @RequestBody ResetPasswordRequest req) {
        UsuarioEntity u = usuarioRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        u.setContrasenaHash(passwordEncoder.encode(req.nuevaContrasena()));
        usuarioRepo.save(u);
        refreshTokenRepo.revokarPorUsuario(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/usuarios/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarUsuario(@PathVariable Long id,
            Authentication auth) {
        Long selfId = Long.parseLong(auth.getName());
        if (id.equals(selfId)) {
            throw new BusinessException("No puedes desactivar tu propia cuenta", HttpStatus.BAD_REQUEST);
        }
        UsuarioEntity u = usuarioRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        u.setActivo(false);
        usuarioRepo.save(u);
        refreshTokenRepo.revokarPorUsuario(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ─── PRODUCTOS ──────────────────────────────────────────────────────────

    @GetMapping("/productos")
    public ResponseEntity<ApiResponse<List<ProductoAdminDTO>>> listarProductos() {
        Map<Long, String> catNombres = categoriaRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(c -> c.getId(), c -> c.getNombre()));
        List<ProductoAdminDTO> productos = productoRepo.findAll().stream()
                .map(p -> new ProductoAdminDTO(p.getId(), p.getCategoriaId(),
                        catNombres.getOrDefault(p.getCategoriaId(), "?"),
                        p.getNombre(), p.getDescripcion(), p.getPrecio(),
                        p.getImagenUrl(), p.isDisponible()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(productos));
    }

    @PostMapping("/productos")
    public ResponseEntity<ApiResponse<ProductoAdminDTO>> crearProducto(@RequestBody GuardarProductoRequest req) {
        String catNombre = categoriaRepo.findById(req.categoriaId())
                .map(c -> c.getNombre())
                .orElseThrow(() -> new BusinessException("Categoría no encontrada", HttpStatus.NOT_FOUND));
        ProductoEntity p = new ProductoEntity();
        p.setCategoriaId(req.categoriaId());
        p.setNombre(req.nombre());
        p.setDescripcion(req.descripcion());
        p.setPrecio(req.precio());
        p.setImagenUrl(req.imagenUrl());
        p.setDisponible(req.disponible());
        ProductoEntity saved = productoRepo.save(p);
        return ResponseEntity.ok(ApiResponse.ok(new ProductoAdminDTO(saved.getId(), saved.getCategoriaId(),
                catNombre, saved.getNombre(), saved.getDescripcion(), saved.getPrecio(),
                saved.getImagenUrl(), saved.isDisponible())));
    }

    @PutMapping("/productos/{id}")
    public ResponseEntity<ApiResponse<ProductoAdminDTO>> actualizarProducto(
            @PathVariable Long id, @RequestBody GuardarProductoRequest req) {
        ProductoEntity p = productoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Producto no encontrado", HttpStatus.NOT_FOUND));
        String catNombre = categoriaRepo.findById(req.categoriaId())
                .map(c -> c.getNombre())
                .orElseThrow(() -> new BusinessException("Categoría no encontrada", HttpStatus.NOT_FOUND));
        p.setCategoriaId(req.categoriaId());
        p.setNombre(req.nombre());
        p.setDescripcion(req.descripcion());
        p.setPrecio(req.precio());
        p.setImagenUrl(req.imagenUrl());
        p.setDisponible(req.disponible());
        ProductoEntity saved = productoRepo.save(p);
        return ResponseEntity.ok(ApiResponse.ok(new ProductoAdminDTO(saved.getId(), saved.getCategoriaId(),
                catNombre, saved.getNombre(), saved.getDescripcion(), saved.getPrecio(),
                saved.getImagenUrl(), saved.isDisponible())));
    }

    @PatchMapping("/productos/{id}/disponibilidad")
    public ResponseEntity<ApiResponse<Void>> toggleDisponibilidad(@PathVariable Long id) {
        ProductoEntity p = productoRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Producto no encontrado", HttpStatus.NOT_FOUND));
        p.setDisponible(!p.isDisponible());
        productoRepo.save(p);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/productos/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarProducto(@PathVariable Long id) {
        if (!productoRepo.existsById(id)) {
            throw new BusinessException("Producto no encontrado", HttpStatus.NOT_FOUND);
        }
        productoRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ─── CATEGORÍAS ─────────────────────────────────────────────────────────

    @GetMapping("/categorias")
    public ResponseEntity<ApiResponse<List<CategoriaAdminDTO>>> listarCategorias() {
        List<CategoriaAdminDTO> cats = categoriaRepo.findAll().stream()
                .map(c -> new CategoriaAdminDTO(c.getId(), c.getNombre(), c.getDescripcion(), c.isActivo()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(cats));
    }

    @PostMapping("/categorias")
    public ResponseEntity<ApiResponse<CategoriaAdminDTO>> crearCategoria(
            @RequestBody GuardarCategoriaRequest req) {
        if (req.nombre() == null || req.nombre().isBlank()) {
            throw new BusinessException("El nombre es requerido", HttpStatus.BAD_REQUEST);
        }
        com.restaurante.modules.catalogo.infrastructure.persistence.CategoriaEntity c =
                new com.restaurante.modules.catalogo.infrastructure.persistence.CategoriaEntity();
        c.setNombre(req.nombre());
        c.setDescripcion(req.descripcion());
        c.setActivo(true);
        var saved = categoriaRepo.save(c);
        return ResponseEntity.ok(ApiResponse.ok(
                new CategoriaAdminDTO(saved.getId(), saved.getNombre(), saved.getDescripcion(), saved.isActivo())));
    }

    @PutMapping("/categorias/{id}")
    public ResponseEntity<ApiResponse<CategoriaAdminDTO>> actualizarCategoria(
            @PathVariable Long id, @RequestBody GuardarCategoriaRequest req) {
        var c = categoriaRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Categoría no encontrada", HttpStatus.NOT_FOUND));
        if (req.nombre() != null && !req.nombre().isBlank()) c.setNombre(req.nombre());
        if (req.descripcion() != null) c.setDescripcion(req.descripcion());
        var saved = categoriaRepo.save(c);
        return ResponseEntity.ok(ApiResponse.ok(
                new CategoriaAdminDTO(saved.getId(), saved.getNombre(), saved.getDescripcion(), saved.isActivo())));
    }

    @DeleteMapping("/categorias/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarCategoria(@PathVariable Long id) {
        if (!categoriaRepo.existsById(id)) {
            throw new BusinessException("Categoría no encontrada", HttpStatus.NOT_FOUND);
        }
        categoriaRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ─── MESAS ──────────────────────────────────────────────────────────────

    @GetMapping("/mesas")
    public ResponseEntity<ApiResponse<List<MesaAdminDTO>>> listarMesasAdmin() {
        List<MesaAdminDTO> mesas = mesaRepo.findAllByOrderByNumeroAsc().stream()
                .map(m -> new MesaAdminDTO(m.getId(), m.getNumero(), m.getCapacidad(), m.getEstado().name()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(mesas));
    }

    @PostMapping("/mesas")
    public ResponseEntity<ApiResponse<MesaAdminDTO>> crearMesa(@RequestBody GuardarMesaRequest req) {
        if (req.numero() == null || req.numero().isBlank() || req.numero().length() > 5) {
            throw new BusinessException("Número de mesa requerido (máx. 5 caracteres)", HttpStatus.BAD_REQUEST);
        }
        if (req.capacidad() < 1) {
            throw new BusinessException("La capacidad debe ser al menos 1", HttpStatus.BAD_REQUEST);
        }
        if (mesaRepo.findAll().stream().anyMatch(m -> m.getNumero().equalsIgnoreCase(req.numero()))) {
            throw new BusinessException("Ya existe una mesa con ese número", HttpStatus.CONFLICT);
        }
        MesaEntity mesa = new MesaEntity();
        mesa.setNumero(req.numero().toUpperCase());
        mesa.setCapacidad(req.capacidad());
        MesaEntity saved = mesaRepo.save(mesa);
        return ResponseEntity.ok(ApiResponse.ok(
                new MesaAdminDTO(saved.getId(), saved.getNumero(), saved.getCapacidad(), saved.getEstado().name())));
    }

    @PutMapping("/mesas/{id}")
    public ResponseEntity<ApiResponse<MesaAdminDTO>> actualizarMesa(
            @PathVariable Long id, @RequestBody GuardarMesaRequest req) {
        MesaEntity mesa = mesaRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada", HttpStatus.NOT_FOUND));
        if (mesa.getEstado() == MesaEntity.EstadoMesa.OCUPADA) {
            throw new BusinessException("No se puede editar una mesa ocupada", HttpStatus.CONFLICT);
        }
        if (req.numero() != null && !req.numero().isBlank()) {
            String nuevoNum = req.numero().toUpperCase();
            boolean duplicado = mesaRepo.findAll().stream()
                    .anyMatch(m -> !m.getId().equals(id) && m.getNumero().equalsIgnoreCase(nuevoNum));
            if (duplicado) throw new BusinessException("Ya existe una mesa con ese número", HttpStatus.CONFLICT);
            mesa.setNumero(nuevoNum);
        }
        if (req.capacidad() >= 1) mesa.setCapacidad(req.capacidad());
        MesaEntity saved = mesaRepo.save(mesa);
        return ResponseEntity.ok(ApiResponse.ok(
                new MesaAdminDTO(saved.getId(), saved.getNumero(), saved.getCapacidad(), saved.getEstado().name())));
    }

    @PatchMapping("/mesas/{id}/estado")
    public ResponseEntity<ApiResponse<MesaAdminDTO>> toggleEstadoMesa(@PathVariable Long id) {
        MesaEntity mesa = mesaRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada", HttpStatus.NOT_FOUND));
        if (mesa.getEstado() == MesaEntity.EstadoMesa.OCUPADA ||
            mesa.getEstado() == MesaEntity.EstadoMesa.RESERVADA) {
            throw new BusinessException("No se puede cambiar el estado de una mesa ocupada o reservada", HttpStatus.CONFLICT);
        }
        MesaEntity.EstadoMesa nuevoEstado = mesa.getEstado() == MesaEntity.EstadoMesa.DISPONIBLE
                ? MesaEntity.EstadoMesa.INACTIVA
                : MesaEntity.EstadoMesa.DISPONIBLE;
        mesa.setEstado(nuevoEstado);
        MesaEntity saved = mesaRepo.save(mesa);
        return ResponseEntity.ok(ApiResponse.ok(
                new MesaAdminDTO(saved.getId(), saved.getNumero(), saved.getCapacidad(), saved.getEstado().name())));
    }

    @DeleteMapping("/mesas/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarMesa(@PathVariable Long id) {
        MesaEntity mesa = mesaRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada", HttpStatus.NOT_FOUND));
        if (mesa.getEstado() == MesaEntity.EstadoMesa.OCUPADA) {
            throw new BusinessException("No se puede eliminar una mesa ocupada", HttpStatus.CONFLICT);
        }
        mesaRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
