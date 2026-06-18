# Diseño: aislamiento de sesión por pestaña

## Objetivo

Cada pestaña del navegador debe administrar una sesión independiente. Iniciar o cerrar sesión en una pestaña no debe autenticar, reemplazar ni cerrar la sesión de otras pestañas.

## Comportamiento requerido

- Una pestaña abierta en `/login` sin haber iniciado sesión permanece en el login, aunque otra pestaña del mismo navegador se autentique.
- Cada inicio de sesión genera un refresh token distinto.
- La pestaña autenticada conserva su sesión al refrescar la página.
- Navegar o pegar una URL protegida dentro de una pestaña autenticada conserva la identidad de esa pestaña.
- Pegar una URL protegida en una pestaña sin sesión redirige al login.
- Cerrar sesión revoca y elimina únicamente la sesión de la pestaña actual.
- Las demás pestañas autenticadas continúan funcionando con sus propios usuarios y tokens.

## Causa raíz

El backend escribe el refresh token en una cookie `HttpOnly`. Las cookies pertenecen al perfil y origen del navegador, no a una pestaña. Por ello, todas las pestañas pueden enviar la misma cookie a `/api/auth/refresh`.

El aislamiento debe apoyarse en almacenamiento propio de la pestaña. `sessionStorage` persiste durante recargas y navegación en esa pestaña, pero no sincroniza cambios entre pestañas.

## Diseño elegido

### Frontend

- Mantener el access token solamente en memoria.
- Guardar el refresh token en `sessionStorage` bajo una clave exclusiva del contexto de la pestaña.
- En el arranque del router:
  - si la pestaña tiene refresh token, solicitar un access token y reconstruir el usuario desde el JWT;
  - si no lo tiene, marcar el arranque como terminado sin llamar a `/refresh`;
  - si la renovación falla, limpiar solamente el estado de esa pestaña.
- En peticiones protegidas, renovar únicamente usando el refresh token almacenado en la pestaña.
- En logout, enviar explícitamente ese refresh token al backend y luego limpiar el estado local aunque la llamada falle.
- No utilizar cookies como fuente de autenticación ni `localStorage` para tokens.

### Backend

- Entregar el access token y refresh token en la respuesta de login.
- No crear una cookie de refresh token.
- Exigir `X-Refresh-Token` en `/api/auth/refresh`.
- En `/api/auth/logout`, revocar únicamente el token recibido en `X-Refresh-Token`.
- No usar una cookie como fallback, porque volvería a introducir estado compartido entre pestañas.
- Mantener múltiples refresh tokens válidos por usuario para permitir sesiones simultáneas.

## Flujo de datos

### Inicio de sesión

1. La pestaña envía credenciales a `/api/auth/login`.
2. El backend valida al usuario, crea un access token y un refresh token independientes.
3. La pestaña guarda el refresh token en su `sessionStorage`.
4. El access token queda en memoria y se usa en `Authorization: Bearer`.

### Recarga o navegación directa

1. El frontend pierde el access token en memoria debido a la recarga.
2. Lee el refresh token de su propio `sessionStorage`.
3. Solicita un access token nuevo mediante `X-Refresh-Token`.
4. Reconstruye usuario y rol desde el JWT y permite la ruta protegida.

### Pestaña sin sesión

1. No existe refresh token en su `sessionStorage`.
2. El router no intenta renovar mediante cookies.
3. Cualquier ruta protegida redirige a `/login`.

### Cierre de sesión

1. La pestaña envía su refresh token a `/api/auth/logout`.
2. El backend revoca únicamente ese registro.
3. La pestaña elimina sus tokens y usuario.
4. Los tokens de otras pestañas no se modifican.

## Errores y seguridad

- Un refresh sin cabecera debe devolver `401 Unauthorized`.
- Un refresh token inválido, expirado o revocado debe devolver `401 Unauthorized`.
- El logout sin token será idempotente: limpia el estado local sin afectar otras sesiones.
- El frontend nunca debe recurrir a una cookie cuando no encuentre el token de pestaña.
- Los tokens no se registrarán en logs ni mensajes de error.

## Pruebas

### Backend

- Login devuelve ambos tokens y no emite cookie de refresh.
- Refresh rechaza solicitudes sin `X-Refresh-Token`, aunque exista una cookie antigua.
- Logout revoca solo el token recibido por cabecera.
- Dos refresh tokens del mismo usuario permanecen independientes al revocar uno.

### Frontend

- Una pestaña sin refresh token no ejecuta bootstrap de una sesión ajena.
- Una pestaña con refresh token restaura su access token al recargar.
- Logout elimina únicamente el token almacenado en esa pestaña.
- Una ruta protegida sin sesión redirige a `/login`.
- Una sesión válida mantiene usuario y rol al navegar directamente a una URL protegida.

## Compatibilidad

Las cookies antiguas de refresh token pueden seguir presentes temporalmente en algunos navegadores. El backend no las aceptará para renovar o cerrar sesiones. En login y logout se puede emitir una cookie expirada únicamente como limpieza de migración, sin usarla como credencial.

## Fuera de alcance

- Sincronizar logout entre pestañas.
- Limitar la cantidad de sesiones simultáneas por usuario.
- Implementar rotación automática de refresh tokens.
- Compartir una sesión al duplicar una pestaña; el navegador puede copiar inicialmente `sessionStorage`, por lo que este caso requiere una identidad adicional de pestaña y no forma parte de este cambio.
