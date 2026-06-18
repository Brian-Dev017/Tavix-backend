package com.restaurante.modules.auth.infrastructure.web;

import com.restaurante.modules.auth.domain.port.in.AuthUseCase;
import com.restaurante.modules.auth.domain.port.in.RefreshTokenUseCase;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginRequest;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginResponse;
import com.restaurante.shared.exception.BusinessException;
import com.restaurante.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthUseCase authUseCase;

    @Mock
    private RefreshTokenUseCase refreshTokenUseCase;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authUseCase, refreshTokenUseCase);
    }

    @Test
    void loginReturnsBothTokensWithoutRequiringSharedCookieState() {
        LoginRequest request = new LoginRequest("admin", "secreto");
        LoginResponse tokens = new LoginResponse(
                "access-token", "refresh-tab-a", "AD", "Ana", "Perez");
        when(authUseCase.login(request)).thenReturn(tokens);

        ResponseEntity<ApiResponse<LoginResponse>> result = controller.login(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(tokens, result.getBody().data());
    }

    @Test
    void refreshRejectsRequestWithoutTabToken() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.refresh(null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("Refresh token no enviado", exception.getMessage());
        verifyNoInteractions(refreshTokenUseCase);
    }

    @Test
    void refreshUsesOnlyExplicitTabToken() {
        when(refreshTokenUseCase.refresh("refresh-tab-b")).thenReturn("new-access-token");

        ResponseEntity<ApiResponse<String>> result = controller.refresh("refresh-tab-b");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("new-access-token", result.getBody().data());
        verify(refreshTokenUseCase).refresh("refresh-tab-b");
    }

    @Test
    void logoutRevokesOnlyExplicitTabToken() {
        controller.logout("refresh-tab-c");

        verify(refreshTokenUseCase).logout("refresh-tab-c");
    }

    @Test
    void logoutWithoutTabTokenIsIdempotent() {
        controller.logout(" ");

        verify(refreshTokenUseCase, never()).logout(" ");
    }
}
