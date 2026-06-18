package com.restaurante.modules.pedidos.infrastructure.ws;

import com.restaurante.modules.pedidos.infrastructure.web.dto.ItemCocinaDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PedidoEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public PedidoEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publicarNuevoItem(ItemCocinaDTO item) {
        publicarTrasCommit("/topic/cocina", item);
    }

    /** Notifica una venta cobrada (refresco automático de dashboard/caja). */
    public void publicarVenta(Object evento) {
        publicarTrasCommit("/topic/ventas", evento);
    }

    /** Notifica cambios en pedidos (creación, items, estado) para refresco de caja/mesas. */
    public void publicarPedidoEvento(Object evento) {
        publicarTrasCommit("/topic/pedidos", evento);
    }

    private void publicarTrasCommit(String destination, Object evento) {
        Runnable publicar = () -> messagingTemplate.convertAndSend(destination, evento);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publicar.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publicar.run();
            }
        });
    }
}
