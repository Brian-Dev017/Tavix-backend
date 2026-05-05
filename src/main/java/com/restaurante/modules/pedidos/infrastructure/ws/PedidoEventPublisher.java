package com.restaurante.modules.pedidos.infrastructure.ws;

import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemCocinaDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class PedidoEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public PedidoEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publicarNuevoItem(ItemCocinaDTO item) {
        messagingTemplate.convertAndSend("/topic/cocina", item);
    }
}
