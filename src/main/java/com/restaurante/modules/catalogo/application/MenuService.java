package com.restaurante.modules.catalogo.application;

import com.restaurante.modules.catalogo.infrastructure.persistence.CategoriaJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.persistence.ProductoJpaRepo;
import com.restaurante.modules.catalogo.infrastructure.web.dto.CategoriaConProductosDTO;
import com.restaurante.modules.catalogo.infrastructure.web.dto.ProductoDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MenuService {

    private final CategoriaJpaRepo categoriaRepo;
    private final ProductoJpaRepo productoRepo;

    public MenuService(CategoriaJpaRepo categoriaRepo, ProductoJpaRepo productoRepo) {
        this.categoriaRepo = categoriaRepo;
        this.productoRepo = productoRepo;
    }

    public List<CategoriaConProductosDTO> getMenuDigital() {
        return categoriaRepo.findByActivoTrue().stream()
                .map(cat -> {
                    List<ProductoDTO> productos = productoRepo
                            .findByCategoriaIdAndDisponibleTrue(cat.getId())
                            .stream()
                            .map(p -> new ProductoDTO(p.getId(), p.getNombre(),
                                    p.getDescripcion(), p.getPrecio(), p.getImagenUrl()))
                            .toList();
                    return new CategoriaConProductosDTO(cat.getId(), cat.getNombre(), productos);
                })
                .filter(c -> !c.productos().isEmpty())
                .toList();
    }
}
