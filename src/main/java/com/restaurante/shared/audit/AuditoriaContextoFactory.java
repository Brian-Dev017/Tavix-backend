package com.restaurante.shared.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuditoriaContextoFactory {

    public AuditoriaContexto from(HttpServletRequest request, Authentication authentication) {
        return new AuditoriaContexto(
                resolveUsuarioId(authentication),
                resolveUsuarioLogin(authentication),
                resolveRol(authentication),
                resolveIp(request),
                request != null ? request.getRequestURI() : null
        );
    }

    private Long resolveUsuarioId(Authentication authentication) {
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            return null;
        }

        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveUsuarioLogin(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof String details
                && StringUtils.hasText(details)) {
            return details;
        }
        return null;
    }

    private String resolveRol(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority == null || !StringUtils.hasText(authority.getAuthority())) {
                continue;
            }

            String rol = authority.getAuthority();
            if (rol.startsWith("ROLE_")) {
                return rol.substring(5);
            }
            return rol;
        }

        return null;
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
