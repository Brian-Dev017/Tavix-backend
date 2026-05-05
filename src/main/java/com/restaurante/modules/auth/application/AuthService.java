package com.restaurante.modules.auth.application;

import com.restaurante.modules.auth.domain.model.Usuario;
import com.restaurante.modules.auth.domain.port.in.AuthUseCase;
import com.restaurante.modules.auth.domain.port.in.RefreshTokenUseCase;
import com.restaurante.modules.auth.domain.port.out.RefreshTokenRepositoryPort;
import com.restaurante.modules.auth.domain.port.out.UsuarioRepositoryPort;
import com.restaurante.modules.auth.infrastructure.security.JwtTokenProvider;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginRequest;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginResponse;
import com.restaurante.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService implements AuthUseCase, RefreshTokenUseCase {

    private final UsuarioRepositoryPort usuarioRepo;
    private final RefreshTokenRepositoryPort refreshTokenRepo;
    private final JwtTokenProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UsuarioRepositoryPort usuarioRepo,
                       RefreshTokenRepositoryPort refreshTokenRepo,
                       JwtTokenProvider jwtProvider,
                       PasswordEncoder passwordEncoder) {
        this.usuarioRepo = usuarioRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepo.findByUsuario(request.usuario())
                .orElseThrow(() -> new BusinessException("Credenciales inválidas", HttpStatus.UNAUTHORIZED));

        if (!usuario.isActivo()) {
            throw new BusinessException("Usuario inactivo", HttpStatus.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(request.contrasena(), usuario.getContrasenaHash())) {
            throw new BusinessException("Credenciales inválidas", HttpStatus.UNAUTHORIZED);
        }

        String accessToken = jwtProvider.generateAccessToken(usuario);
        String refreshToken = UUID.randomUUID().toString();
        LocalDateTime expiraEn = LocalDateTime.now().plusDays(7);
        refreshTokenRepo.save(usuario.getId(), refreshToken, expiraEn);

        return new LoginResponse(
                accessToken,
                refreshToken,
                usuario.getRol().name(),
                usuario.getNombre(),
                usuario.getApellido()
        );
    }

    @Override
    public String refresh(String refreshToken) {
        if (!refreshTokenRepo.isTokenValido(refreshToken)) {
            throw new BusinessException("Refresh token inválido o expirado", HttpStatus.UNAUTHORIZED);
        }
        Long usuarioId = refreshTokenRepo.findUsuarioIdByToken(refreshToken)
                .orElseThrow(() -> new BusinessException("Token no encontrado", HttpStatus.UNAUTHORIZED));
        Usuario usuario = usuarioRepo.findById(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado", HttpStatus.UNAUTHORIZED));
        return jwtProvider.generateAccessToken(usuario);
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenRepo.revokar(refreshToken);
    }
}
