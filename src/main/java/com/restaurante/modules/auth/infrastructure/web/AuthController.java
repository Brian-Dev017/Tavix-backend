package com.restaurante.modules.auth.infrastructure.web;

import com.restaurante.modules.auth.domain.port.in.AuthUseCase;
import com.restaurante.modules.auth.domain.port.in.RefreshTokenUseCase;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginRequest;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginResponse;
import com.restaurante.shared.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthUseCase authUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;

    public AuthController(AuthUseCase authUseCase, RefreshTokenUseCase refreshTokenUseCase) {
        this.authUseCase = authUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        LoginResponse loginResponse = authUseCase.login(request);

        Cookie cookie = new Cookie("refreshToken", loginResponse.refreshToken());
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(cookie);

        LoginResponse safeResponse = new LoginResponse(
                loginResponse.accessToken(), null,
                loginResponse.rol(), loginResponse.nombre(), loginResponse.apellido()
        );
        return ResponseEntity.ok(ApiResponse.ok(safeResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(HttpServletRequest request) {
        String refreshToken = extractRefreshCookie(request);
        String newAccessToken = refreshTokenUseCase.refresh(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok(newAccessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request,
                                                     HttpServletResponse response) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken != null) {
            refreshTokenUseCase.logout(refreshToken);
        }
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setMaxAge(0);
        cookie.setPath("/api/auth/refresh");
        response.addCookie(cookie);
        return ResponseEntity.ok(ApiResponse.ok("Sesión cerrada", null));
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
