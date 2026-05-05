package com.restaurante.modules.auth.domain.port.in;

import com.restaurante.modules.auth.infrastructure.web.dto.LoginRequest;
import com.restaurante.modules.auth.infrastructure.web.dto.LoginResponse;

public interface AuthUseCase {
    LoginResponse login(LoginRequest request);
}
