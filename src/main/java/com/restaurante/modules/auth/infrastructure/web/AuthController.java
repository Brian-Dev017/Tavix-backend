package com.restaurante.modules.auth.infrastructure.web;

import com.restaurante.modules.auth.domain.port.in.AuthUseCase;
import com.restaurante.modules.auth.domain.port.in.RefreshTokenUseCase;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginRequest;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginResponse;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
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

        writeRefreshTokenCookie(response, loginResponse.refreshToken(), Duration.ofDays(7));

        LoginResponse safeResponse = new LoginResponse(
                loginResponse.accessToken(), null,
                loginResponse.rol(), loginResponse.nombre(), loginResponse.apellido()
        );
        return ResponseEntity.ok(ApiResponse.ok(safeResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(HttpServletRequest request) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("Refresh token no enviado", HttpStatus.UNAUTHORIZED);
        }
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
        clearRefreshTokenCookie(response);
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

    private void writeRefreshTokenCookie(HttpServletResponse response,
                                         String refreshToken,
                                         Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
