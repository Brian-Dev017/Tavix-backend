package com.restaurante.shared.config;

import com.restaurante.modules.auth.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class StompAuthChannelInterceptorTest {

    private final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(mock(JwtTokenProvider.class));

    @Test
    void cajeroPuedeSuscribirseAVentas() {
        Message<byte[]> message = subscription("/topic/ventas", "CA");
        assertDoesNotThrow(() -> interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test
    void meseroNoPuedeSuscribirseAVentas() {
        Message<byte[]> message = subscription("/topic/ventas", "ME");
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test
    void cocineroPuedeSuscribirseAPedidos() {
        Message<byte[]> message = subscription("/topic/pedidos", "CO");
        assertDoesNotThrow(() -> interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class)));
    }

    private Message<byte[]> subscription(String destination, String role) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                "1",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        ));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
