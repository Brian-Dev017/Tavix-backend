package com.restaurante.shared.config;

import com.restaurante.modules.auth.infrastructure.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Set<String> ALLOWED_ROLES = Set.of("AD", "CO");

    private final JwtTokenProvider jwtTokenProvider;

    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                throw new AccessDeniedException("Token WebSocket inválido");
            }

            Claims claims = jwtTokenProvider.parseToken(token);
            String rol = claims.get("rol", String.class);
            if (rol == null || !ALLOWED_ROLES.contains(rol)) {
                throw new AccessDeniedException("Rol sin acceso a cocina");
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + rol))
            );
            accessor.setUser(authentication);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                && "/topic/cocina".equals(accessor.getDestination())
                && accessor.getUser() == null) {
            throw new AccessDeniedException("Suscripción no autenticada");
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        String token = accessor.getFirstNativeHeader("token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        return null;
    }
}