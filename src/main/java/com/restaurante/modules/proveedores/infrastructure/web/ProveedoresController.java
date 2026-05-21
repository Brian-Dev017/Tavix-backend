package com.restaurante.modules.proveedores.infrastructure.web;

import com.restaurante.modules.proveedores.infrastructure.persistence.ProveedorEntity;
import com.restaurante.modules.proveedores.infrastructure.persistence.ProveedorJpaRepo;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedoresController {

    private final ProveedorJpaRepo proveedorRepo;

    public ProveedoresController(ProveedorJpaRepo proveedorRepo) {
        this.proveedorRepo = proveedorRepo;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProveedorEntity>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(proveedorRepo.findAllByOrderByRazonSocialAsc()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProveedorEntity>> crear(@RequestBody ProveedorEntity body) {
        validarProveedor(body);
        body.setId(null);
        body.setActivo(true);
        ProveedorEntity saved = proveedorRepo.save(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Proveedor creado", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProveedorEntity>> actualizar(
            @PathVariable Long id,
            @RequestBody ProveedorEntity body) {
        ProveedorEntity entity = proveedorRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Proveedor no encontrado", HttpStatus.NOT_FOUND));
        validarProveedor(body);
        copiar(body, entity);
        return ResponseEntity.ok(ApiResponse.ok("Proveedor actualizado", proveedorRepo.save(entity)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        ProveedorEntity entity = proveedorRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Proveedor no encontrado", HttpStatus.NOT_FOUND));
        entity.setActivo(false);
        proveedorRepo.save(entity);
        return ResponseEntity.ok(ApiResponse.ok("Proveedor desactivado", null));
    }

    private void validarProveedor(ProveedorEntity body) {
        if (body.getRuc() == null || !body.getRuc().matches("\\d{11}")) {
            throw new BusinessException("El RUC debe tener 11 digitos", HttpStatus.BAD_REQUEST);
        }
        if (body.getRazonSocial() == null || body.getRazonSocial().isBlank()) {
            throw new BusinessException("La razon social es obligatoria", HttpStatus.BAD_REQUEST);
        }
        if (body.getTipoContribuyente() == null || body.getTipoContribuyente().isBlank()) {
            throw new BusinessException("El tipo de contribuyente es obligatorio", HttpStatus.BAD_REQUEST);
        }
        if (body.getEstadoRuc() == null || body.getEstadoRuc().isBlank()) {
            throw new BusinessException("El estado del RUC es obligatorio", HttpStatus.BAD_REQUEST);
        }
        if (body.getCondicionRuc() == null || body.getCondicionRuc().isBlank()) {
            throw new BusinessException("La condicion del RUC es obligatoria", HttpStatus.BAD_REQUEST);
        }
        if (body.getCci() != null && !body.getCci().isBlank() && !body.getCci().matches("\\d{20}")) {
            throw new BusinessException("El CCI debe tener 20 digitos", HttpStatus.BAD_REQUEST);
        }
        if (body.isSujetoDetraccion()
                && (body.getPorcentajeDetraccion() == null || body.getPorcentajeDetraccion().signum() <= 0)) {
            throw new BusinessException("Indica el porcentaje de detraccion", HttpStatus.BAD_REQUEST);
        }
    }

    private void copiar(ProveedorEntity source, ProveedorEntity target) {
        target.setRuc(source.getRuc());
        target.setRazonSocial(source.getRazonSocial());
        target.setNombreComercial(source.getNombreComercial());
        target.setTipoContribuyente(source.getTipoContribuyente());
        target.setEstadoRuc(source.getEstadoRuc());
        target.setCondicionRuc(source.getCondicionRuc());
        target.setDepartamento(source.getDepartamento());
        target.setProvincia(source.getProvincia());
        target.setDistrito(source.getDistrito());
        target.setDireccionFiscal(source.getDireccionFiscal());
        target.setRegimenTributario(source.getRegimenTributario());
        target.setAgenteRetencionPercepcion(source.isAgenteRetencionPercepcion());
        target.setSujetoDetraccion(source.isSujetoDetraccion());
        target.setPorcentajeDetraccion(source.getPorcentajeDetraccion());
        target.setCuentaDetracciones(source.getCuentaDetracciones());
        target.setBancoPrincipal(source.getBancoPrincipal());
        target.setTipoCuenta(source.getTipoCuenta());
        target.setMoneda(source.getMoneda());
        target.setNumeroCuentaBancaria(source.getNumeroCuentaBancaria());
        target.setCci(source.getCci());
        target.setContactoComercialNombre(source.getContactoComercialNombre());
        target.setContactoComercialTelefono(source.getContactoComercialTelefono());
        target.setContactoComercialCorreo(source.getContactoComercialCorreo());
        target.setPlazoPago(source.getPlazoPago());
        target.setLeadTime(source.getLeadTime());
    }
}
