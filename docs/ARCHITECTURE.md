# Freezify — Arquitectura

## 1. Vista general

```text
┌──────────────┐   ┌──────────────┐
│ Flutter app  │   │  React web   │
│ Android/iOS  │   │  (Vite, TS)  │
└──────┬───────┘   └──────┬───────┘
       │  REST /api/v1 (JSON) + SSE (a partir de Fase 2)
       └─────────┬────────┘
          ┌──────▼───────────────────────────────┐
          │  Backend — monolito modular          │
          │  Java 21 · Spring Boot 4             │
          │  auth │ users │ households │ …       │
          └──────┬───────────────────────────────┘
                 │ JPA / Flyway
          ┌──────▼──────┐
          │ PostgreSQL  │
          └─────────────┘
```

Un único desplegable. Sin microservicios (regla 2). El módulo `ai` vivirá dentro del monolito detrás de
`AIProvider`; solo se extraerá a `services/ai` si aparece una razón demostrable (p. ej. un modelo local
en Python que no pueda ejecutarse en la JVM).

## 2. Repositorio

```text
Freezify/
├── apps/
│   ├── backend/        Spring Boot (Maven)
│   ├── web/            React + TypeScript (Vite)
│   └── mobile/         Flutter
├── infrastructure/
│   └── docker/         Dockerfiles y configuración de nginx
├── docs/               PRODUCT_SPEC, ARCHITECTURE, ROADMAP, STATUS
├── .github/workflows/  CI
├── docker-compose.yml
├── .env.example
└── README.md
```

Diferencias respecto a la estructura propuesta en el prompt maestro, y motivo:

| Cambio | Motivo |
|---|---|
| Raíz `Freezify/` en lugar de `foodwise/` | Nombre de producto adoptado |
| No existe `services/ai/` | Regla 2: no hay servicio separado hasta que haga falta. Se creará en Fase 7 solo si se justifica |
| No existen `scripts/` ni `infrastructure/deployment/` | No hay nada real que poner todavía; no se crean carpetas vacías |

## 3. Backend

### 3.1 Módulos

Cada módulo es un paquete de primer nivel bajo `com.freezify`. Lo que está en el paquete raíz del módulo
es su **API pública**; lo que está en subpaquetes (`internal`, `web`) es privado. Esto se verifica en un
test con **Spring Modulith** (`ApplicationModules.verify()`), que falla si hay ciclos o si un módulo usa
tipos internos de otro.

| Módulo | Responsabilidad | Depende de | Fase |
|---|---|---|---|
| `common` | Errores, correlation ID, tiempo, utilidades transversales | — | 1 |
| `users` | Cuenta de usuario y perfil | common | 1 |
| `auth` | Registro, login, tokens, configuración de seguridad | users, common | 1 |
| `households` | Hogares, miembros, invitaciones, **control de acceso por hogar** | users, common | 1 |
| `food` | Catálogo canónico de alimentos y categorías, unidades | common | 2 |
| `inventory` | Alimentos del hogar, consumo y descarte | households, food | 2 |
| `expiration` | Estimación de fechas y niveles de prioridad | food | 3 |
| `notifications` | Preferencias, generación y envío | households, inventory, expiration | 3 |
| `recipes` | Recetas y recomendador | food, inventory, expiration | 4 |
| `mealplanning` | Plan semanal y generador | recipes, inventory | 5 |
| `shopping` | Listas de la compra | mealplanning, inventory, food | 6 |
| `ai` | `AIProvider`, `AiService`, casos de uso de IA | common | 7 |
| `integrations` | `ProductCatalogProvider` y fuentes externas | food | 7+ |
| `analytics` | Eventos de producto y estadísticas | escucha eventos de los demás | 2 / 8 |

Los módulos de fases futuras **no existen todavía en el código**: se crean cuando tienen contenido real.

Comunicación entre módulos: llamada directa a la API pública cuando la dependencia va "hacia abajo" en la
tabla; eventos de aplicación de Spring cuando un módulo inferior debe informar a uno superior
(p. ej. `inventory` → `analytics`).

### 3.2 Capas dentro de un módulo

```text
households/
├── HouseholdAccess.java      API pública (lo que otros módulos pueden usar)
├── HouseholdRole.java
├── internal/                 entidades JPA, repositorios, servicios de aplicación
└── web/                      controladores REST y DTOs
```

Se aplica separación dominio/infraestructura donde aporta valor (reglas de caducidad, scoring, cálculo
de la lista de la compra serán funciones puras sin Spring ni JPA). Para CRUD sencillo no se añaden
puertos y adaptadores: entidad JPA + repositorio Spring Data + servicio.

### 3.3 Decisiones técnicas

| # | Decisión | Motivo |
|---|---|---|
| D1 | **Maven** con wrapper | Estándar en Spring, menos superficie que Gradle para un único módulo |
| D2 | **Spring Boot 4.1 / Java 21** | Línea con soporte activo a fecha de inicio (octubre 2026) |
| D3 | **JWT propio** firmado HS256 con las librerías de Spring Security (Nimbus) | Un único backend emite y valida; no hace falta un servidor OAuth2 externo. El código de la app depende de `Jwt` estándar, así que pasar a un IdP externo es un cambio de configuración |
| D4 | Access token de 15 min + **refresh token opaco rotatorio** guardado como hash SHA-256 | Permite revocar sesiones; si se reutiliza un refresh ya rotado se revoca toda la familia (detección de robo) |
| D5 | Contraseñas con `DelegatingPasswordEncoder` (bcrypt) | Permite migrar de algoritmo sin invalidar hashes |
| D6 | IDs **UUID v7** | No enumerables, generables en cliente (offline), ordenados en el tiempo |
| D7 | Acceso a hogar ajeno responde **404**, no 403 | No revela qué IDs existen |
| D8 | Errores en **RFC 9457** (`application/problem+json`) con campo `code` estable | Los clientes traducen por `code`, no por el texto |
| D9 | Tests contra **PostgreSQL real embebido** (zonky), nunca H2 | Las migraciones Flyway se prueban contra el motor de producción; no exige Docker |
| D10 | `spring.jpa.hibernate.ddl-auto=validate` siempre | El esquema solo cambia mediante Flyway |
| D11 | Cantidades como `BigDecimal` + unidad con dimensión (masa, volumen, recuento) | No asumir que todo se cuenta en unidades; sumas en unidad canónica (g, ml, ud) |
| D12 | **SSE** en lugar de WebSocket para tiempo real | Solo hace falta servidor → cliente; funciona sobre HTTP normal y es más simple de operar |
| D13 | Jobs con `@Scheduled`; ShedLock cuando haya más de una instancia | Suficiente para el MVP; sin broker |
| D14 | Rate limiting en memoria (Bucket4j) en `/auth/**` | Válido con una instancia; se moverá a almacenamiento compartido al escalar |
| D15 | Recomendador y planificador **deterministas** antes que ML | Explicables y testeables; ML solo cuando haya datos |

### 3.4 Seguridad

- API sin estado; `Authorization: Bearer <jwt>`. CSRF desactivado porque no hay cookies de sesión.
- Todo `/api/v1/**` requiere autenticación salvo `/api/v1/auth/**`.
- **Autorización por hogar:** todo acceso a datos de un hogar pasa por `HouseholdAccess.requireMember` /
  `requireOwner`. Ningún repositorio se consulta con un `householdId` sin esa comprobación previa.
- Secretos solo por variables de entorno. El arranque falla si `FREEZIFY_JWT_SECRET` tiene menos de 32 bytes.
- CORS restringido a orígenes configurados.
- Subida de ficheros (Fase 7): límite de tamaño, validación de tipo por contenido, almacenamiento privado,
  URLs firmadas temporales y retención configurable.

### 3.5 Observabilidad

- Filtro de **correlation ID** (`X-Correlation-Id`), propagado a MDC, a la respuesta y al `problem+json`.
- Actuator: `/actuator/health` (liveness/readiness). Métricas con Micrometer.
- Logs estructurados (JSON) activables por configuración en producción.
- Error tracking externo: pendiente de elegir (Fase 9).

## 4. Modelo de datos

Fase 1 (implementado en `V1__foundation.sql`):

```text
users ──< refresh_tokens
users ──< household_members >── households ──< household_invitations
```

Entidades previstas por fase:

| Fase | Entidades |
|---|---|
| 2 | `FoodCategory`, `Food` (catálogo), `FoodItem`, `FoodConsumption`, `FoodWaste`, `ProductEvent` |
| 3 | `Notification`, `NotificationPreference`, `DeviceToken`, `ShelfLifeRule` |
| 4 | `Recipe`, `RecipeIngredient`, `UserPreference` |
| 5 | `MealPlan`, `MealPlanEntry` |
| 6 | `ShoppingList`, `ShoppingListItem` |
| 7 | `Scan`, `ScanResult`, `Product` |

`Food` (alimento canónico, p. ej. "tomate") es la pieza que une todo: `FoodItem`, `RecipeIngredient` y
`ShoppingListItem` apuntan a él. Sin esa normalización no hay matching fiable entre inventario y recetas.

## 5. API

REST versionada bajo `/api/v1`, documentada con OpenAPI (`/v3/api-docs`, Swagger UI en `/swagger-ui.html`).

Fase 1:

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/register` | Alta; devuelve tokens y usuario |
| POST | `/auth/login` | Login |
| POST | `/auth/refresh` | Rota el refresh token |
| POST | `/auth/logout` | Revoca el refresh token |
| GET / PATCH | `/users/me` | Perfil |
| POST / GET | `/households` | Crear / listar los míos |
| GET / PATCH / DELETE | `/households/{id}` | Detalle / renombrar (owner) / eliminar (owner) |
| GET | `/households/{id}/members` | Miembros |
| DELETE | `/households/{id}/members/{userId}` | Expulsar (owner) o abandonar (uno mismo) |
| POST | `/households/{id}/invitations` | Genera un código de invitación |
| POST | `/households/join` | Unirse con un código |

Convenciones: DTOs como `record`, Bean Validation, fechas ISO-8601 en UTC, paginación `page`/`size`/`sort`
en las colecciones que puedan crecer (a partir de Fase 2).

## 6. Clientes

| | Web | Mobile |
|---|---|---|
| Framework | React 19 + TypeScript + Vite | Flutter (Dart 3) |
| Estado servidor | TanStack Query | Riverpod |
| Navegación | React Router | go_router |
| HTTP | `fetch` con wrapper tipado y refresh automático | dio con interceptor de refresh |
| Tokens | Access en memoria; refresh en `localStorage` (ver riesgo R8) | `flutter_secure_storage` (Keystore / Keychain) |
| i18n | i18next (es, en) | `flutter_localizations` + ARB (es, en) |
| Tests | Vitest + Testing Library | `flutter_test` |

Offline (móvil): lectura desde caché a partir de Fase 2; escritura offline con cola solo para acciones
simples y solo si los datos de uso lo justifican (Fase 9).

## 7. IA (Fase 7)

```text
Controller → caso de uso → AiService → AIProvider ─┬─ MockProvider   (tests, desarrollo)
                                                    ├─ proveedor remoto (LLM con salida estructurada)
                                                    └─ LocalProvider
```

- Los controladores nunca llaman a un LLM.
- Toda salida se valida contra un esquema JSON; si no valida, se descarta.
- El proveedor se elige por configuración; se cachea por hash de entrada cuando tenga sentido.
- OCR: primero una librería madura (ML Kit en el dispositivo); el LLM solo para estructurar el texto.

## 8. Riesgos técnicos

| # | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| R1 | **Normalización de alimentos**: los tickets españoles abrevian ("TOM PERA 1K"); sin mapear a `Food` no hay recetas ni lista de la compra fiables | Alto | Catálogo canónico con alias desde Fase 2; revisión humana obligatoria; aprender alias de las correcciones del usuario |
| R2 | **Calidad del OCR** en tickets arrugados o térmicos | Alto | OCR en dispositivo + estructuración posterior; la UI de revisión es el camino principal, no la excepción |
| R3 | **Fechas estimadas** erróneas (seguridad alimentaria, responsabilidad) | Alto | Siempre etiquetadas; reglas conservadoras; nunca sustituyen a la fecha del envase |
| R4 | **Conversión de unidades** (recuento ↔ masa: "2 tomates" vs "300 g") | Medio | Peso medio por unidad en el catálogo; cuando no exista, no se convierte y se avisa |
| R5 | **Contenido de recetas**: licencias y normalización de ingredientes | Medio | Conjunto inicial propio y pequeño; no importar datasets sin revisar licencia |
| R6 | **Coste y latencia de IA** | Medio | `AIProvider` intercambiable, caché, límites por usuario, determinista por defecto |
| R7 | **Push en iOS**: requiere macOS, cuenta de Apple Developer y APNs | Medio | El entorno de desarrollo actual es Windows: iOS no se puede compilar ni probar aquí |
| R8 | Refresh token de la web en `localStorage` (expuesto a XSS) | Medio | Aceptado en Fase 1; migrar a cookie `HttpOnly` + `SameSite` antes de abrir al público |
| R9 | Sincronización offline y conflictos | Medio | Acotar a lectura en caché en el MVP |
| R10 | **RGPD**: imágenes de tickets, borrado de cuenta | Medio | Retención configurable, borrado en cascada, minimización desde el diseño |
| R11 | Versiones muy recientes (Spring Boot 4.1, TypeScript 7, Vite 8) | Bajo | Versiones fijadas; CI en cada cambio |
| R12 | Rate limiting y SSE en memoria no escalan horizontalmente | Bajo | Una instancia en el MVP; documentado el punto de sustitución |
