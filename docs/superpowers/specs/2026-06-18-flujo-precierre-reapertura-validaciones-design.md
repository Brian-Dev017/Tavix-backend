# Flujo de pre-cierre, reapertura y validaciones de comprobantes

## Objetivo

Corregir el ciclo operativo de caja para que el pre-cierre termine el turno activo del cajero, quede pendiente de revisión administrativa y permita una nueva apertura. La primera apertura del día se autoriza con las credenciales del cajero; las siguientes requieren credenciales de un administrador activo. También se impedirán caracteres inválidos en DNI, RUC, nombre y apellido de boletas y facturas.

## Estado de caja

`ArqueoEntity.EstadoArqueo` tendrá tres estados:

- `ABIERTO`: el cajero puede cobrar y todavía puede realizar el pre-cierre.
- `PRECIERRE`: el cajero terminó su turno; ya no puede cobrar con ese arqueo ni repetir el pre-cierre. El administrador debe revisarlo y cerrarlo.
- `CERRADO`: el administrador completó el cierre.

El endpoint de arqueo activo seguirá devolviendo únicamente un arqueo `ABIERTO`. Un arqueo en `PRECIERRE` libera al cajero para realizar otra apertura.

## Aperturas

La petición de apertura incluirá `usuario`, `contrasena`, `montoApertura` y `notas`.

- Si el cajero no tiene aperturas registradas durante el día actual, las credenciales deben pertenecer al cajero autenticado y activo.
- Si ya existe al menos una apertura del cajero durante el día actual, las credenciales deben pertenecer a cualquier usuario activo con rol `AD`.
- Nunca se permitirá abrir si el cajero ya tiene un arqueo `ABIERTO`.
- La autorización se comprobará en backend. El frontend solo adaptará el formulario y los mensajes según la información de elegibilidad devuelta por el backend.

Se expondrá un endpoint de estado de apertura que indique si existe caja abierta, cuántas aperturas realizó el cajero hoy y qué tipo de autorización requiere la próxima apertura.

## Pre-cierre y cierre administrativo

El pre-cierre:

- exige las credenciales del cajero autenticado;
- se bloquea si existen pagos pendientes;
- calcula y persiste ventas, efectivo, pagos digitales, redondeo, monto esperado y diferencia;
- cambia el estado de `ABIERTO` a `PRECIERRE`;
- no establece `cierreEn`.

El cierre administrativo solo podrá ejecutarlo un usuario con rol `AD` y aceptará arqueos en `PRECIERRE`. Al cerrar, establecerá `cierreEn` y cambiará el estado a `CERRADO`. No se cerrará directamente un arqueo `ABIERTO`, para preservar la obligación del pre-cierre del cajero.

## Panel administrativo

El panel mostrará de forma explícita:

- arqueos `ABIERTO`;
- arqueos `PRECIERRE`, identificados como pendientes de cierre;
- arqueos `CERRADO`.

Los arqueos pendientes tendrán una acción de cierre administrativo individual. El historial y su exportación CSV incluirán el estado `PRECIERRE`.

## Validaciones de boleta y factura

En frontend:

- DNI: solo dígitos, máximo 8.
- RUC: solo dígitos, máximo 11.
- Nombre y apellido de boleta: solo letras, espacios y caracteres españoles.
- Se filtrarán escritura, pegado y cualquier actualización del modelo.
- Las funciones `dni` y `ruc` validarán el valor original, no una versión silenciosamente saneada.

En backend:

- DNI debe coincidir con exactamente 8 dígitos.
- RUC debe coincidir con exactamente 11 dígitos.
- Nombre y apellido combinados de boleta solo admitirán letras y espacios.

## Persistencia

Una migración Flyway ampliará el `ENUM` de `arqueo_caja.estado` para aceptar `PRECIERRE` sin modificar registros existentes.

## Pruebas

Se cubrirán:

- transición `ABIERTO -> PRECIERRE`;
- rechazo de pre-cierre repetido;
- primera apertura con credenciales del cajero;
- segunda apertura con credenciales administrativas;
- rechazo de segunda apertura con credenciales del cajero;
- cierre administrativo únicamente desde `PRECIERRE`;
- validación estricta de DNI, RUC y nombres;
- filtrado de entrada en frontend;
- compilación y suites existentes de ambos proyectos.
