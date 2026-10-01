# Estado del proyecto

Última actualización: 2026-10-01

## Fase 0 — Product Definition ✅

`README.md`, `docs/PRODUCT_SPEC.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`.

## Fase 1 — Foundation 🟡

Implementada. No se marca como completada porque quedan verificaciones que este entorno de desarrollo
no permite hacer (ver "Sin verificar").

### Completed

**Backend** (`apps/backend`)
- Spring Boot 4.1.1 / Java 21, Maven wrapper, monolito modular (`common`, `users`, `auth`, `households`).
- PostgreSQL + Flyway (`V1__foundation.sql`), `ddl-auto=validate`.
- Registro, login, refresh rotatorio con detección de reutilización, logout, limpieza diaria de tokens caducados.
- Perfil (`GET/PATCH /users/me`).
- Hogares: crear, listar, ver, renombrar, eliminar, miembros, expulsar/abandonar, invitación por código, unirse.
- Autorización por hogar (`HouseholdAccess`); acceso ajeno responde 404.
- Errores `application/problem+json` con `code` y `correlationId`; correlation ID en logs y respuestas.
- Rate limiting en `/auth/**` y `/households/join`; CORS restringido; health check; OpenAPI + Swagger UI.
- Arranque local sin Docker (`./mvnw spring-boot:test-run`) con PostgreSQL embebido y datos persistentes.

**Web** (`apps/web`)
- React 19 + TypeScript + Vite, TanStack Query, React Router, i18next (es/en).
- Login, registro, restauración de sesión, logout, cambio de idioma.
- Hogares: lista, crear, unirse, detalle, miembros, invitar, renombrar, expulsar, abandonar, eliminar (con confirmación).
- Cliente HTTP con renovación de sesión compartida entre peticiones concurrentes y entre pestañas.

**Mobile** (`apps/mobile`)
- Flutter 3.44, Riverpod, go_router, dio, flutter_secure_storage, i18n con ARB (es/en).
- Mismas pantallas y flujos que la web; refresh token en Keystore/Keychain; pantalla de reintento si se abre sin conexión.

**Infraestructura**
- `docker-compose.yml`, Dockerfiles de backend y web, nginx, `.env.example`, `.gitignore`, CI en GitHub Actions.

### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 41 tests contra PostgreSQL 18 real embebido: auth (19), hogares (15), rate limit (4), propiedades (2), límites entre módulos (1) |
| Web `npm run lint` | ✅ sin avisos |
| Web `npm test` | ✅ 22 tests (cliente HTTP 8, flujos de la aplicación 14) |
| Web `npm run build` | ✅ |
| Mobile `flutter analyze` | ✅ sin avisos |
| Mobile `flutter build apk --debug` | ✅ genera el APK |
| Mobile `flutter test` | ❌ **no ejecutados** — ver "Sin verificar" |
| Extremo a extremo, web → backend real | ✅ en navegador: registro, crear hogar, generar invitación, recargar y restaurar sesión |
| Extremo a extremo, API real con 3 usuarios | ✅ invitación y unión, miembros compartidos, un tercero recibe 404, anónimo 401, origen CORS no permitido 403 |
| Extremo a extremo, código de la app móvil → backend real | ✅ compilado para web en una copia temporal y probado en navegador: login, lista, detalle, invitación, restaurar sesión |
| Persistencia local | ✅ los datos sobreviven a reiniciar el backend, incluso tras matar el proceso |

### Sin verificar

| Qué | Motivo | Qué hace falta |
|---|---|---|
| Los 24 tests de Flutter (`test/api_client_test.dart`, `test/app_test.dart`) | Windows bloquea `flutter_tester.exe` con una directiva de Control de aplicaciones ("Una directiva de Control de aplicaciones bloqueó este archivo"). El intento alternativo con `--platform chrome` se queda colgado al cargar la suite. Los tests compilan y pasan el analizador, pero **nunca se han ejecutado** | Ejecutarlos en CI, o permitir ese ejecutable en la directiva de Windows |
| Imágenes Docker y `docker compose up` | Docker no está instalado en esta máquina. Solo se ha comprobado que el JAR se empaqueta y se extrae en capas con los nombres que usa el Dockerfile | Ejecutar `docker compose up --build` |
| Workflow de CI | El repositorio no tiene remoto todavía | Subirlo a GitHub y revisar la primera ejecución |
| App en un dispositivo o emulador Android | No hay ningún AVD configurado | `flutter run` en un emulador |
| iOS | Requiere macOS | Compilar en un Mac |

### Known issues

- **Refresh token de la web en `localStorage`** (riesgo R8): expuesto a XSS. Migrar a cookie `HttpOnly` antes de abrir al público.
- **Rate limiting por IP y cabeceras `X-Forwarded-*`**: el backend confía en esas cabeceras, así que debe ser accesible solo a través del proxy; expuesto directamente, un cliente podría falsear su IP y esquivar el límite. Además el contador vive en memoria (una sola instancia).
- **El registro revela si un correo ya existe** (409). Aceptado por ahora a cambio de una UX clara; mitigado por el rate limiting.
- **No hay verificación de correo ni recuperación de contraseña.** Necesario antes de usuarios reales.
- **No hay eliminación de cuenta** (requisito de privacidad). Previsto junto con el borrado de datos del hogar.
- **El propietario no puede abandonar ni transferir un hogar**; solo eliminarlo.
- **Las invitaciones son reutilizables hasta que caducan (7 días)** y no se pueden revocar manualmente.
- **Eventos de producto** (`user_registered`, …) aún no se registran; se añaden en Fase 2 junto con la tabla de eventos.
- **Sin tests E2E automatizados** (Playwright / `integration_test`); las pruebas de extremo a extremo de esta fase han sido manuales.
- **Swagger UI y `/v3/api-docs` son públicos**; desactivarlos o protegerlos en producción.
- **Detener el backend local**: si el proceso se mata en vez de cerrarse con Ctrl+C, PostgreSQL embebido puede quedar vivo en el puerto 54329 y hay que terminarlo a mano.

### Next

Fase 2 — Inventory. Primera unidad de trabajo: catálogo `Food` + `FoodCategory` con semilla es/en y el
modelo de cantidades (`BigDecimal` + unidad con dimensión), porque todo lo demás (alimentos del hogar,
recetas, lista de la compra) apunta a ese catálogo.
