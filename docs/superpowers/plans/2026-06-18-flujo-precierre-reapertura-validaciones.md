# Flujo de Pre-cierre, Reapertura y Validaciones Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar el estado `PRECIERRE`, reaperturas autorizadas por administrador y entradas estrictas para boletas y facturas.

**Architecture:** El backend será la fuente de verdad para estados y credenciales. El frontend consultará la elegibilidad de apertura, representará los tres estados y filtrará entradas sin sustituir las validaciones del servidor.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Flyway/MySQL, Vue 3, TypeScript, PrimeVue, Vitest.

---

## Estructura de archivos

- `src/main/java/com/restaurante/modules/caja/infrastructure/persistence/ArqueoEntity.java`: incorporar `PRECIERRE`.
- `src/main/java/com/restaurante/modules/caja/infrastructure/persistence/ArqueoJpaRepo.java`: contar aperturas del día.
- `src/main/java/com/restaurante/modules/caja/infrastructure/web/ArqueoController.java`: reglas de apertura, pre-cierre, elegibilidad y cierre.
- `src/main/resources/db/migration/V15__estado_precierre_arqueo.sql`: ampliar el enum de base de datos.
- `src/test/java/com/restaurante/modules/caja/infrastructure/web/ArqueoControllerTest.java`: pruebas del flujo y credenciales.
- `src/test/java/com/restaurante/modules/caja/application/CajaServiceTest.java`: pruebas estrictas de comprobantes.
- `../frontend/src/modules/admin/api/reportesApi.ts`: contratos de estado y apertura.
- `../frontend/src/modules/caja/views/CajaView.vue`: UI de reapertura y filtrado de campos.
- `../frontend/src/modules/admin/views/ArqueoView.vue`: visualización y cierre de pre-cierres.
- `../frontend/src/shared/validation/inputValidation.ts`: validación estricta de documentos.
- `../frontend/src/shared/validation/inputValidation.test.ts`: pruebas de documentos y nombres.

### Task 1: Persistencia y reglas backend de arqueo

- [ ] Escribir pruebas que demuestren la transición a `PRECIERRE`, la autorización del cajero en primera apertura, la autorización administrativa en reaperturas y el cierre exclusivo de pre-cierres.
- [ ] Ejecutar `mvn -Dtest=ArqueoControllerTest test` y verificar que fallen por las reglas ausentes.
- [ ] Añadir `PRECIERRE`, el conteo diario, el endpoint de elegibilidad y validación de credenciales en `ArqueoController`.
- [ ] Añadir `V15__estado_precierre_arqueo.sql`.
- [ ] Ejecutar `mvn -Dtest=ArqueoControllerTest test` y verificar que pase.

### Task 2: Validaciones backend de comprobantes

- [ ] Escribir pruebas para rechazar DNI/RUC con letras y nombre de boleta con números.
- [ ] Ejecutar `mvn -Dtest=CajaServiceTest test` y confirmar el fallo inicial.
- [ ] Mantener las expresiones regulares estrictas en `CajaService` y ajustar cualquier validación ambigua detectada por las pruebas.
- [ ] Ejecutar `mvn -Dtest=CajaServiceTest test`.

### Task 3: Contratos y flujo de cajero en frontend

- [ ] Añadir al API el contrato de elegibilidad y las credenciales de apertura.
- [ ] Actualizar `CajaView.vue` para ocultar pre-cierre después de la transición, habilitar la nueva apertura y solicitar credenciales administrativas desde la segunda apertura.
- [ ] Usar propiedades nativas (`inputmode`) y saneamiento reactivo para DNI/RUC y nombres.
- [ ] Añadir pruebas unitarias para saneamiento y validación estricta.
- [ ] Ejecutar `npm test`.

### Task 4: Panel administrativo

- [ ] Mostrar `PRECIERRE` como pendiente en la tarjeta y tabla.
- [ ] Permitir cerrar el arqueo `PRECIERRE` seleccionado.
- [ ] Impedir que el panel intente cerrar un arqueo todavía `ABIERTO`.
- [ ] Ejecutar `npm run build`.

### Task 5: Verificación integral

- [ ] Ejecutar `mvn test`.
- [ ] Ejecutar `npm test` y `npm run build`.
- [ ] Revisar los diffs de backend y frontend para detectar regresiones o cambios no relacionados.
