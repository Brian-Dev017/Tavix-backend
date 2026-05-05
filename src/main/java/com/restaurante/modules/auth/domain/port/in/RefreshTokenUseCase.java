package com.restaurante.modules.auth.domain.port.in;

public interface RefreshTokenUseCase {
    String refresh(String refreshToken);
    void logout(String refreshToken);
}
