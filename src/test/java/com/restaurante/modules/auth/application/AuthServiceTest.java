package com.restaurante.modules.auth.application;

import com.restaurante.modules.auth.domain.model.Rol;
import com.restaurante.modules.auth.domain.model.Usuario;
import com.restaurante.modules.auth.domain.port.out.RefreshTokenRepositoryPort;
import com.restaurante.modules.auth.domain.port.out.UsuarioRepositoryPort;
import com.restaurante.modules.auth.infrastructure.security.JwtTokenProvider;
import com.restaurante.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepositoryPort usuarioRepo;

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepo;

    @Mock
    private JwtTokenProvider jwtProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepo, refreshTokenRepo, jwtProvider, passwordEncoder);
    }

    @Test
    void refreshRejectsMissingToken() {
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refresh(null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("Refresh token no enviado", exception.getMessage());
        verify(refreshTokenRepo, never()).isTokenValido(null);
    }

    @Test
    void refreshRejectsInactiveUserAndRevokesToken() {
        String refreshToken = "refresh-token";
        Usuario usuarioInactivo = new Usuario(5L, "Ana", "Perez", "ana", "hash", Rol.CO, false);

        when(refreshTokenRepo.isTokenValido(refreshToken)).thenReturn(true);
        when(refreshTokenRepo.findUsuarioIdByToken(refreshToken)).thenReturn(Optional.of(5L));
        when(usuarioRepo.findById(5L)).thenReturn(Optional.of(usuarioInactivo));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refresh(refreshToken));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("Usuario inactivo", exception.getMessage());
        verify(refreshTokenRepo).revokar(refreshToken);
        verify(jwtProvider, never()).generateAccessToken(usuarioInactivo);
    }
}