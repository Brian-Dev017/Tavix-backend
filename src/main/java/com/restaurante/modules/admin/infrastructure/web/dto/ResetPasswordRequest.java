package com.restaurante.modules.admin.infrastructure.web.dto;

public record ResetPasswordRequest(String claveAnterior, String nuevaContrasena) {}
