package com.restaurante.modules.catalogo.infrastructure.web;

import com.restaurante.modules.catalogo.application.MenuService;
import com.restaurante.modules.catalogo.infrastructure.web.dto.CategoriaConProductosDTO;
import com.restaurante.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoriaConProductosDTO>>> getMenu() {
        return ResponseEntity.ok(ApiResponse.ok(menuService.getMenuDigital()));
    }
}
