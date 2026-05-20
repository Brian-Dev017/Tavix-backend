# Flujo Restaurante Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the La Flor del Tumbo order flow from table session to paid receipt and released table.

**Architecture:** Keep Spring services as the business authority and make the database contract reproducible with migrations, views, and triggers. Frontend changes consume the corrected API without inventing business state.

**Tech Stack:** Spring Boot 3.3.5, Java 21, MySQL 8, Vue 3, TypeScript, Pinia, PrimeVue.

---

### Task 1: Database Contract

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/db/migration/V2__flujo_pedidos_completo.sql`
- Modify: `src/main/java/com/restaurante/modules/caja/infrastructure/persistence/ComprobanteEntity.java`
- Modify: `src/main/java/com/restaurante/modules/pedidos/infrastructure/persistence/DetallePedidoEntity.java`

- [ ] Add Flyway dependencies for MySQL migrations.
- [ ] Add columns for comprobante `serie`, `numero`, `descuento`, `motivo_descuento`.
- [ ] Add columns for detalle cancelacion `motivo_cancelacion`, `cancelado_en`.
- [ ] Create `v_cola_cocina` and `v_consumo_por_pedido`.
- [ ] Add triggers for item insert/status and comprobante completion.

### Task 2: Backend Business Rules

**Files:**
- Modify: `MesaService.java`
- Modify: `PedidoService.java`
- Modify: `CocinaService.java`
- Modify: `CajaService.java`
- Modify DTOs under `modules/*/infrastructure/web/dto`
- Modify controllers under `modules/*/infrastructure/web`

- [ ] Validate active sessions and product availability.
- [ ] Move order state to `EN_COCINA` on first item.
- [ ] Validate kitchen transitions and cancellation reason.
- [ ] Mark order ready when all non-cancelled items are ready.
- [ ] Validate receipt type, payment method, fiscal data, and discount.
- [ ] Consume receipt series/correlative and mark order paid.
- [ ] Close table session when payment completes.

### Task 3: Frontend Flow

**Files:**
- Modify: `Tavix-Frontend/src/modules/pedidos/views/PedidoView.vue`
- Modify: `Tavix-Frontend/src/modules/pedidos/api/pedidosApi.ts`
- Modify: `Tavix-Frontend/src/modules/cocina/views/CocinaView.vue`
- Modify: `Tavix-Frontend/src/modules/cocina/api/cocinaApi.ts`
- Modify: `Tavix-Frontend/src/modules/caja/views/CajaView.vue`
- Modify: `Tavix-Frontend/src/modules/caja/api/cajaApi.ts`

- [ ] Add quantity input and summary-first behavior for waiter.
- [ ] Add kitchen cancel action with reason.
- [ ] Add 10-second fallback polling for kitchen and caja.
- [ ] Add discount amount/reason to caja.
- [ ] Display receipt series-number after payment.

### Task 4: Verification

**Files:**
- Create or modify backend tests under `src/test/java`.

- [ ] Add service tests for order item state transitions.
- [ ] Add service tests for receipt validation and payment closing.
- [ ] Run backend tests with Maven or wrapper.
- [ ] Run frontend build.
- [ ] Check git diff and status.
