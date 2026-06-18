# Aislamiento de sesión por pestaña Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aislar autenticación, restauración y logout por pestaña sin compartir sesiones mediante cookies.

**Architecture:** El backend entregará ambos tokens en el login y aceptará el refresh token únicamente mediante `X-Refresh-Token`. El frontend conservará el access token en memoria y el refresh token en `sessionStorage`, restaurando solo la sesión perteneciente a la pestaña actual.

**Tech Stack:** Java 21, Spring Boot 3.3, JUnit 5, Mockito, Vue 3, Pinia, TypeScript, Axios, Vite.

---

## Estructura de archivos

- `src/main/java/com/restaurante/modules/auth/infrastructure/web/AuthController.java`: contrato HTTP de login, refresh y logout sin cookies como credenciales.
- `src/test/java/com/restaurante/modules/auth/infrastructure/web/AuthControllerTest.java`: pruebas unitarias del contrato por cabecera y ausencia de cookies.
- `../frontend/src/modules/auth/store/authStore.ts`: estado y restauración de la sesión propia de cada pestaña.
- `../frontend/src/modules/auth/api/authApi.ts`: envío explícito del refresh token.
- `../frontend/src/modules/auth/views/LoginView.vue`: almacenamiento del refresh token al autenticar.
- `../frontend/src/shared/api/axiosInstance.ts`: renovación únicamente cuando ya existe una sesión de pestaña.
- `../frontend/src/shared/auth/logout.ts`: revocación y limpieza de la pestaña actual.
- `../frontend/src/shared/router/index.ts`: espera del bootstrap antes de autorizar rutas.

### Task 1: Probar el contrato HTTP aislado

**Files:**
- Create: `src/test/java/com/restaurante/modules/auth/infrastructure/web/AuthControllerTest.java`
- Modify: `src/main/java/com/restaurante/modules/auth/infrastructure/web/AuthController.java`

- [ ] **Step 1: Escribir pruebas que fallen**

Crear pruebas con Mockito que verifiquen:

```java
@Test
void loginReturnsRefreshTokenWithoutWritingSharedCookie() {
    LoginResponse tokens = new LoginResponse("access", "refresh-a", "AD", "Ana", "Perez");
    when(authUseCase.login(any())).thenReturn(tokens);

    ResponseEntity<ApiResponse<LoginResponse>> result =
            controller.login(new LoginRequest("admin", "secreto"), response);

    assertSame(tokens, result.getBody().data());
    verify(response, never()).addHeader(eq("Set-Cookie"), anyString());
}

@Test
void refreshRejectsCookieWhenHeaderIsMissing() {
    when(request.getCookies()).thenReturn(new Cookie[] {
            new Cookie("refreshToken", "cookie-compartida")
    });

    assertThrows(BusinessException.class,
            () -> controller.refresh(null));
    verifyNoInteractions(refreshTokenUseCase);
}

@Test
void logoutRevokesOnlyHeaderToken() {
    controller.logout("refresh-b");
    verify(refreshTokenUseCase).logout("refresh-b");
}
```

- [ ] **Step 2: Ejecutar las pruebas y confirmar el fallo**

Run:

```powershell
.\mvnw.cmd -Dtest=AuthControllerTest test
```

Si no existe wrapper:

```powershell
mvn -Dtest=AuthControllerTest test
```

Expected: FAIL porque el controlador todavía escribe/lee cookies y sus firmas requieren request/response.

- [ ] **Step 3: Implementar el contrato por cabecera**

Dejar las firmas del controlador así:

```java
@PostMapping("/login")
public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(authUseCase.login(request)));
}

@PostMapping("/refresh")
public ResponseEntity<ApiResponse<String>> refresh(
        @RequestHeader(value = REFRESH_TOKEN_HEADER, required = false) String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
        throw new BusinessException("Refresh token no enviado", HttpStatus.UNAUTHORIZED);
    }
    return ResponseEntity.ok(ApiResponse.ok(refreshTokenUseCase.refresh(refreshToken)));
}

@PostMapping("/logout")
public ResponseEntity<ApiResponse<Void>> logout(
        @RequestHeader(value = REFRESH_TOKEN_HEADER, required = false) String refreshToken) {
    if (refreshToken != null && !refreshToken.isBlank()) {
        refreshTokenUseCase.logout(refreshToken);
    }
    return ResponseEntity.ok(ApiResponse.ok("Sesión cerrada", null));
}
```

Eliminar imports y métodos relacionados con `Cookie`, `HttpServletRequest`, `HttpServletResponse`, `ResponseCookie`, `Duration`, `Arrays`, escritura, lectura y limpieza de cookies.

- [ ] **Step 4: Ejecutar la prueba enfocada**

Run:

```powershell
mvn -Dtest=AuthControllerTest test
```

Expected: PASS.

### Task 2: Completar la sesión exclusiva de pestaña en frontend

**Files:**
- Modify: `../frontend/src/modules/auth/store/authStore.ts`
- Modify: `../frontend/src/modules/auth/api/authApi.ts`
- Modify: `../frontend/src/modules/auth/views/LoginView.vue`
- Modify: `../frontend/src/shared/api/axiosInstance.ts`
- Modify: `../frontend/src/shared/auth/logout.ts`
- Modify: `../frontend/src/shared/router/index.ts`

- [ ] **Step 1: Confirmar el flujo actual**

Verificar que:

```typescript
const refreshToken = ref<string | null>(
  sessionStorage.getItem('tab_refresh_token'),
)
```

que `refreshSession()` rechaza cuando no existe dicho token y que `bootstrapSession()` no llama al backend en ese caso.

- [ ] **Step 2: Eliminar cualquier dependencia de cookies**

Configurar los clientes Axios sin `withCredentials`:

```typescript
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL as string,
})
```

Y renovar exclusivamente con:

```typescript
axios.post<{ data: string }>(
  `${import.meta.env.VITE_API_URL}/api/auth/refresh`,
  {},
  { headers: { 'X-Refresh-Token': refreshToken.value } },
)
```

- [ ] **Step 3: Mantener login y logout por pestaña**

En login guardar ambos valores:

```typescript
auth.setAccessToken(accessToken)
auth.setRefreshToken(refreshToken)
```

En logout capturar el token antes de limpiar:

```typescript
const refreshToken = auth.refreshToken
try {
  await authApi.logout(refreshToken)
} finally {
  auth.logout()
}
```

La API debe enviar la cabecera solo si el token existe:

```typescript
logout: (refreshToken: string | null) =>
  api.post('/api/auth/logout', {}, {
    headers: refreshToken
      ? { 'X-Refresh-Token': refreshToken }
      : undefined,
  })
```

- [ ] **Step 4: Mantener el guard del router**

Antes de evaluar `requiresGuest` o `requiresAuth`:

```typescript
const auth = useAuthStore()
await auth.bootstrapSession()
```

Una pestaña sin refresh token debe terminar el bootstrap sin autenticarse y una ruta protegida debe redirigir a `/login`.

- [ ] **Step 5: Compilar frontend**

Run:

```powershell
npm run build
```

Working directory: `../frontend`

Expected: TypeScript y Vite terminan sin errores.

### Task 3: Verificación integral

**Files:**
- Test: todos los tests backend
- Test: build frontend

- [ ] **Step 1: Ejecutar toda la suite backend**

Run:

```powershell
mvn test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Ejecutar build frontend**

Run:

```powershell
npm run build
```

Working directory: `../frontend`

Expected: build exitoso.

- [ ] **Step 3: Verificar manualmente cuatro pestañas**

1. Abrir cuatro pestañas nuevas en `/login`.
2. Iniciar sesión como administrador únicamente en la primera.
3. Refrescar las otras tres: deben permanecer en `/login`.
4. Refrescar la primera: debe restaurar al administrador.
5. Iniciar distintos usuarios en otras pestañas.
6. Copiar una URL protegida y pegarla dentro de cada pestaña: debe usar la identidad propia de esa pestaña.
7. Cerrar sesión en una pestaña: las demás deben continuar operativas.

- [ ] **Step 4: Revisar el diff**

Run:

```powershell
git diff --check
git status --short
```

Expected: sin errores de whitespace y sin sobrescribir modificaciones ajenas al alcance.
