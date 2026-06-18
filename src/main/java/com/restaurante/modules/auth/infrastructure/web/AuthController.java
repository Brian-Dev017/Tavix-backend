package com.restaurante.modules.auth.infrastructure.web;

import com.restaurante.modules.auth.domain.port.in.AuthUseCase;
import com.restaurante.modules.auth.domain.port.in.RefreshTokenUseCase;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginRequest;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginResponse;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";

    private final AuthUseCase authUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;

    public AuthController(AuthUseCase authUseCase, RefreshTokenUseCase refreshTokenUseCase) {
        this.authUseCase = authUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authUseCase.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(
            @RequestHeader(value = REFRESH_TOKEN_HEADER, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("Refresh token no enviado", HttpStatus.UNAUTHORIZED);
        }
        String newAccessToken = refreshTokenUseCase.refresh(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok(newAccessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = REFRESH_TOKEN_HEADER, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenUseCase.logout(refreshToken);
        }
        return ResponseEntity.ok(ApiResponse.ok("Sesión cerrada", null));
    }
}
