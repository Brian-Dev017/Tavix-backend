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

    /** Notifica una venta cobrada (refresco automático de dashboard/caja). */
    public void publicarVenta(Object evento) {
        messagingTemplate.convertAndSend("/topic/ventas", evento);
    }

    /** Notifica cambios en pedidos (creación, items, estado) para refresco de caja/mesas. */
    public void publicarPedidoEvento(Object evento) {
        messagingTemplate.convertAndSend("/topic/pedidos", evento);
    }
}
