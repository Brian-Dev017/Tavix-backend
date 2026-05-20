# Flujo Restaurante Design

## Objetivo

Hacer que el sistema cumpla el flujo operativo de La Flor del Tumbo: mesa disponible, sesion activa, pedido con items, cocina prepara, caja cobra, comprobante fiscal se emite y la mesa queda liberada.

## Modelo De Negocio

La mesa solo puede atenderse cuando existe una `sesion_mesa` abierta. Esa sesion pertenece a un mesero y agrupa exactamente el pedido activo de la atencion. Un pedido empieza `ABIERTO`, pasa a `EN_COCINA` al recibir su primer item, pasa a `LISTO` cuando todos sus items no cancelados estan `LISTO`, y termina `COBRADO` cuando caja emite un comprobante completado. Al cobrarse, la sesion se cierra y la mesa vuelve a `DISPONIBLE`.

Los items de cocina avanzan en orden: `PENDIENTE -> EN_PREPARACION -> LISTO`. Un item tambien puede pasar a `CANCELADO` con motivo registrado. Los items cancelados no bloquean que el pedido pase a `LISTO`, pero si todos los items estan cancelados el pedido no se puede cobrar.

El comprobante se emite solo para pedidos `LISTO`. El tipo puede ser `T`, `B` o `F`. Factura exige RUC de 11 digitos, razon social y direccion. Boleta permite DNI opcional, pero si se envia debe tener 8 digitos. El comprobante toma la serie activa por tipo, consume el correlativo, guarda `serie`, `numero`, `subtotal`, `igv`, `descuento`, `total`, `metodo_pago`, `pagado_en`, y cierra el pedido.

## Arquitectura

La base de datos tendra el contrato reproducible en migraciones SQL versionadas. Las vistas `v_cola_cocina` y `v_consumo_por_pedido` existiran en el repo. Los triggers automatizaran cambios exigidos por requisitos, pero los servicios tambien mantendran invariantes de negocio para que la API sea clara y testeable.

Los servicios de dominio quedaran como punto de control de reglas:

- `MesaService`: abrir y cerrar sesiones validando estado y sesion activa.
- `PedidoService`: crear pedido solo sobre sesion abierta, agregar items solo de productos disponibles, calcular snapshots y descuentos.
- `CocinaService`: validar transiciones de estado y cancelacion con motivo.
- `CajaService`: validar comprobantes, consumir serie/correlativo, marcar cobro y liberar mesa.

## Frontend

Mesero podra seleccionar cantidad y observacion antes de agregar. El resumen mostrara items, cantidades, estados y total. Cocina mostrara cola por WebSocket con refresco de respaldo cada 10 segundos y permitira cancelar con motivo. Caja refrescara automaticamente cada 10 segundos, mostrara solo pedidos cobrables como tales, permitira descuento con motivo y validara datos del comprobante antes de enviar.

## Pruebas

Se agregaran pruebas unitarias de servicios para las reglas criticas de pedido, cocina y caja. El frontend se verificara con `npm run build`. El backend se verificara con Maven; si Maven no existe localmente, se agregara wrapper o se documentara el bloqueo exacto.
