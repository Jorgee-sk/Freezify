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
| `food` | Catálogo canónico de alimentos, categorías, unidades y cantidades | common | 2 ✔ |
| `inventory` | Alimentos del hogar, consumo y descarte | households, food | 2 ✔ |
| `expiration` | Niveles de prioridad y estimación de fechas por reglas de vida útil | food | 3 ✔ |
| `notifications` | Avisos de caducidad dentro de la app, preferencias, dispositivos y envío push por FCM | households, inventory, expiration, food, users | 3 ✔ |
| `recipes` | Catálogo de recetas, recomendador y registro de lo cocinado | food, inventory, expiration, households | 4 ✔ |
| `mealplanning` | Plan semanal, simulación de la despensa y generador | recipes, inventory, food, expiration, households | 5 ✔ |
| `shopping` | Lista de la compra del hogar, llenada a partir del plan; lo comprado pasa al inventario | mealplanning, inventory, food, households | 6 ✔ |
| `ai` | `AIProvider`, `AiService`, casos de uso de IA | common | 7 |
| `integrations` | `ProductCatalogProvider` y fuentes externas | food | 7+ |
| `analytics` | Eventos de producto (hecho) y estadísticas (Fase 8) | escucha eventos de `users`, `inventory`, `notifications`, `recipes`, `mealplanning` y `shopping` | 2 ✔ / 8 |
| `realtime` | Flujos de eventos (SSE) por hogar | households; escucha eventos de `inventory`, `mealplanning`, `recipes`, `shopping` y `households` | 2 ✔ |

Los módulos marcados con ✔, además de los cuatro de la Fase 1, existen en el código. Los de fases futuras **no existen todavía**: se crean cuando tienen contenido real.

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
| D16 | `FoodCategory` es un **enum**, no una entidad | No hay categorías de usuario en el MVP; los clientes traducen el código |
| D17 | Consumo y desperdicio en **una tabla** `food_outcomes` con `type` | Mismas columnas y se consultan juntas; sustituye a `FoodConsumption` + `FoodWaste` |
| D18 | Los recursos de un hogar cuelgan de su ruta: `/households/{id}/inventory` | El hogar es explícito y toda petición pasa por `HouseholdAccess` |
| D19 | El catálogo de alimentos se carga y se **busca en memoria** | Pequeño y estático entre despliegues; ignora acentos sin extensiones de PostgreSQL |
| D20 | Los eventos entre módulos se escuchan **tras el commit**, en transacción propia | Un fallo al registrar una métrica nunca deshace la operación del usuario |
| D21 | Los eventos SSE **no llevan datos**: solo avisan de que algo cambió | Los datos siempre salen de la API autorizada; un evento filtrado no revela nada y no hay que versionar su contenido |
| D23 | La fecha del usuario y la fecha que se aplica se guardan **por separado** | Estimar o acortar por apertura nunca pisa lo que el usuario escribió; el origen (`USER` / `ESTIMATED`) describe la fecha que se aplica |
| D24 | Las reglas de vida útil son **datos** (tabla sembrada por migración), no código | Se corrigen sin tocar la lógica; la regla del alimento gana a la de su categoría y, sin regla, no hay fecha |
| D25 | Un aviso lleva **datos, no frases** (alimentos, fechas, si la fecha es estimada) | Cada cliente lo redacta en el idioma del usuario y una estimación nunca se presenta como un hecho |
| D26 | Anti-spam por **novedad**: se guarda el nivel más urgente del que ya se avisó por usuario y alimento | Un aviso al día por hogar como mucho, y solo si algo es nuevo o más urgente; nada se repite |
| D27 | Los trabajos programados leen otros módulos por interfaces propias (`ExpiringFood`, `HouseholdDirectory`), no por `HouseholdAccess` | No actúan en nombre de un usuario; lo que devuelven nunca llega a un cliente sin comprobar la pertenencia |
| D28 | La API de avisos no lleva identificador de usuario en la ruta (`/notifications`) | El usuario sale siempre del token; no hay ningún id que manipular |
| D29 | Push por la **API HTTP v1 de FCM** con un cliente propio (firma con Nimbus, `HttpClient` del JDK), sin el SDK de Firebase Admin | Una sola llamada HTTP; evita arrastrar decenas de dependencias de Google |
| D30 | El texto del push lo redacta **el servidor**, en el idioma guardado del usuario | Con la app cerrada no hay cliente que lo redacte. Dentro de la app siguen redactando los clientes (D25) |
| D31 | El push se envía **tras el commit** del aviso y nunca lo deshace | El aviso es la fuente de verdad; el push es solo un medio de entrega |
| D32 | La clave de Firebase se indica por **ruta de fichero** (`FREEZIFY_FCM_CREDENTIALS_FILE`); sin ella el push queda desactivado | El secreto no vive en el repositorio ni en la imagen; desarrollo y CI funcionan sin Firebase |
| D33 | Al cerrar sesión la app **invalida su identificador de push** además de avisar al backend | Funciona también cuando la sesión caduca sola y ya no se puede llamar al backend; el servidor olvida el dispositivo cuando Firebase lo da por desaparecido |
| D34 | El plugin de Google Services se aplica **solo si existe** `google-services.json` | El fichero no está en el repositorio (público); quien lo clone puede compilar la app, sin push |
| D35 | Las recetas son **datos sembrados por migración** y se leen una vez en memoria, como el catálogo de alimentos | Solo cambian con un despliegue; puntuar necesita tenerlas todas a mano en cada petición |
| D36 | El recomendador es una **función pura** (`RecipeScorer`): receta + existencias + historial + día → puntuación y factores | Determinista, testeable sin base de datos y explicable: cada número sale de sus entradas |
| D37 | La puntuación es la **media ponderada** de los factores; solo cuentan las proporciones de los pesos | Añadir un factor (preferencias) es añadir un peso, sin recalibrar los demás |
| D38 | La urgencia de varios alimentos se combina como **probabilidades independientes** (1 − Π(1 − u)) | Dos alimentos con prisa cuentan más que uno, sin pasar nunca de 1 |
| D39 | Un ingrediente puede ser **básico** (`staple`): se lista pero no se busca en el inventario | Casi nadie apunta la sal o el aceite; sin esto toda receta tendría "faltas" falsas |
| D40 | Lo pasado de fecha **no cuenta como disponible** para cocinar | La aplicación no debe proponer comer algo caducado |
| D41 | "La he cocinado" **no descuenta** del inventario | Restar cantidades a ciegas puede perder datos; consumir sigue siendo una acción explícita por alimento |
| D42 | Los motivos de una recomendación los **redacta el cliente** a partir de los datos (como los avisos, D25), con frases que no dependen del género ni del número del alimento | El servidor no inventa texto y cada frase se puede rastrear hasta un dato del inventario |
| D43 | Las restricciones alimentarias son **del hogar**, no de cada usuario | Se cocina para todos en la misma cocina; y así no existe un dato de salud personal que otros miembros puedan deducir |
| D44 | Lo que contiene una receta se **deriva** de sus ingredientes (`food_traits`), no se etiqueta a mano por receta | Una sola fuente de verdad: corregir un alimento corrige todas sus recetas, y ninguna receta puede quedar sin etiquetar |
| D45 | Las restricciones son un **filtro previo** a la puntuación, no un factor | Una receta que el hogar no puede comer no debe aparecer nunca, por bien que encaje |
| D46 | `MealPlan` no es una tabla: un plan es la semana de un hogar y solo se guardan sus comidas (`meal_plan_entries`) | La semana no tiene datos propios; una tabla más solo añadiría una creación concurrente que resolver |
| D47 | El plan **simula** la despensa (qué queda cada día) y nunca escribe en el inventario | Planificar no es consumir: descontar a ciegas perdería datos (como D41) |
| D48 | Lo que habrá de cada ingrediente se **recalcula al leer** el plan, no se guarda al generarlo | El inventario cambia cada día; una explicación guardada quedaría obsoleta y sería falsa |
| D49 | El generador es **voraz y determinista**: comida a comida, en orden cronológico | Explicable y testeable (D15); un optimizador global no se justifica con 21 platos principales |
| D50 | La variedad es una **regla** (qué recetas pueden entrar) antes que un factor, con una excepción: aprovechar algo a punto de caducar | Con mucha cantidad de un alimento, ningún peso evita repetirlo cada día; pero evitar el desperdicio es el objetivo del producto |
| D51 | Generar **no toca lo que eligió una persona**, y solo reemplaza lo generado si se pide | Una operación automática no debe perder decisiones del usuario |
| D52 | Un alimento medido de forma no comparable con la receta **no se descuenta** | No se sabe cuánto se usa; afirmar que se acaba o que sobra sería inventar un dato |
| D53 | Elegir la receta de una comida es **una sola instrucción** (`insert … on conflict`) | Sin leer y luego escribir no hay carrera entre dos miembros; el generador usa `do nothing` para no pisar lo elegido |
| D54 | Los avisos se redactan como **"Nombre: frase sobre su fecha"** | Los nombres del catálogo pueden ser plurales; ninguna frase hace concordar un verbo con ellos |
| D55 | Un flujo de tiempo real **dura como mucho lo que su token** | Quien ya no podría pedir los datos no debe seguir oyendo que cambian |
| D56 | La descripción de la API (Swagger) **no se publica por defecto** | Una instancia desplegada no debe describir su API a cualquiera; se activa por variable de entorno |
| D57 | **Una lista de la compra por hogar**, sin tabla `shopping_lists`: solo se guardan sus líneas | Como el plan (D46): la lista no tiene datos propios y se comparte entre los miembros |
| D58 | Lo que falta se calcula en el módulo del plan (`PlanNeeds`) con la misma despensa simulada | Una sola forma de saber qué hay cada día: la lista y el plan nunca se contradicen |
| D59 | Las líneas del plan se **recalculan** al volver a llenar la lista; las que toca una persona pasan a ser suyas | La lista sigue al plan sin duplicar, y nada de lo que alguien escribió se pierde |
| D60 | Llenar la lista toma un **bloqueo de PostgreSQL por hogar** (`pg_advisory_xact_lock`) | Dos miembros a la vez no duplican líneas, sin una tabla más que bloquear |
| D22 | El token viaja en la cabecera `Authorization`, también en SSE | Nunca en la URL; por eso la web usa `fetch` con lectura en streaming en lugar de `EventSource` |

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

Implementado (`V1__foundation.sql`, `V2__inventory.sql`, `V3__shelf_life.sql`, `V4__notifications.sql`,
`V5__device_tokens.sql`, `V6__recipes.sql`, `V7__dietary_restrictions.sql`, `V8__meal_plan.sql`,
`V9__shopping_list.sql`):

```text
users ──< refresh_tokens
users ──< household_members >── households ──< household_invitations
foods ──< food_items >── households
food_items ──< food_outcomes >── households
foods ──< shelf_life_rules
product_events
users ──< notifications >── households      notifications ──< notification_items
users ──  notification_preferences, notification_checks
users ──< notification_item_alerts >── food_items
users ──< device_tokens
recipes ──< recipe_ingredients >── foods
recipes ──< cooked_recipes >── households
foods ──< food_traits
households ── household_diets
households ──< meal_plan_entries >── recipes
households ──< shopping_list_items >── foods (opcional)
```

Entidades previstas por fase:

| Fase | Entidades |
|---|---|
| 2 ✔ | `Food` (catálogo), `FoodItem`, `FoodOutcome` (consumo y desperdicio), `ProductEvent`; `FoodCategory` es un enum |
| 3 ✔ | `ShelfLifeRule`, `Notification`, `NotificationPreference`, `DeviceToken` |
| 4 ✔ | `Recipe`, `RecipeIngredient`, `CookedRecipe`, `FoodTrait`, `HouseholdDiet`; `UserPreference` (gustos personales) pendiente |
| 5 | `MealPlanEntry` (hecha); `MealPlan` es la semana de un hogar, sin tabla (D46) |
| 6 ✔ | `ShoppingListItem`; la lista es la del hogar, sin tabla (D57) |
| 7 | `Scan`, `ScanResult`, `Product` |

`Food` (alimento canónico, p. ej. "tomate") es la pieza que une todo: `FoodItem`, `RecipeIngredient` y
`ShoppingListItem` apuntan a él. Sin esa normalización no hay matching fiable entre inventario y recetas.

## 5. API

REST versionada bajo `/api/v1`, documentada con OpenAPI (`/v3/api-docs`, Swagger UI en `/swagger-ui.html`; desactivados salvo con `FREEZIFY_API_DOCS=true` o en local, D56).

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

Fase 2 (todo bajo `/households/{id}/inventory` salvo el catálogo):

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/foods?q=&lang=&limit=` | Autocompletado sobre el catálogo |
| GET | `/inventory?state=&location=&category=&q=&sort=&page=&size=` | Listado paginado |
| GET | `/inventory/recent` | Alimentos añadidos recientemente |
| POST | `/inventory` | Añadir |
| GET / PUT / DELETE | `/inventory/{itemId}` | Ver / reemplazar / eliminar (no cuenta como desperdicio) |
| POST | `/inventory/{itemId}/open` | Marcar como abierto |
| POST | `/inventory/{itemId}/consume` | Consumir todo o una cantidad |
| POST | `/inventory/{itemId}/discard` | Tirar todo o una cantidad, con motivo |
| GET | `/inventory/consume-first` | Cuántos alimentos hay en cada nivel de prioridad y cuáles comer primero |
| GET | `/households/{id}/events` | Flujo SSE: `inventory-changed`, `meal-plan-changed` y `shopping-list-changed` cuando cambian el inventario, el plan o la lista (sin datos) |

Fase 3 (avisos del usuario que hace la petición):

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/notifications?page=&size=` | Sus avisos, del más reciente al más antiguo |
| GET | `/notifications/unread-count` | Cuántos tiene sin leer |
| POST | `/notifications/{id}/read` | Marcar uno como leído |
| POST | `/notifications/read-all` | Marcar todos como leídos |
| GET / PUT | `/notifications/preferences` | Ver / reemplazar sus preferencias |
| PUT | `/notifications/devices` | La app registra dónde recibe push este usuario (repetible) |
| POST | `/notifications/devices/unregister` | La app deja de recibir push de este usuario (cierre de sesión) |

Fase 4:

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/recipes?household=&q=&maxMinutes=&difficulty=&course=&lang=&page=&size=` | Catálogo de recetas; con `household`, sin lo que ese hogar no come |
| GET | `/recipes/{id}?lang=` | Receta con ingredientes y pasos |
| GET | `/households/{id}/recipes/recommendations?lang=&limit=` | Qué cocinar con lo que hay, con los datos que lo explican |
| POST | `/households/{id}/recipes/{recipeId}/cooked` | El hogar ha cocinado la receta hoy |
| GET / PUT | `/households/{id}/diet` | Ver / reemplazar lo que no se come en el hogar (cualquier miembro) |
| GET | `/households/{id}/meal-plan?week=&lang=` | La semana (lunes a domingo) que contiene esa fecha: comidas, lo que habrá de cada ingrediente y lo que el plan deja caducar |
| PUT / DELETE | `/households/{id}/meal-plan/{date}/{slot}` | Elegir o sustituir / quitar la receta de una comida (`LUNCH`, `DINNER`) |
| POST | `/households/{id}/meal-plan/{date}/{slot}/move` | Mover la comida a otro día o comida; si está ocupada, se intercambian |
| POST | `/households/{id}/meal-plan/generate` | Rellenar las comidas vacías de una semana, de hoy en adelante |
| GET | `/households/{id}/shopping-list?lang=` | La lista del hogar, por pasillos |
| POST | `/households/{id}/shopping-list/items` | Añadir un alimento del catálogo o texto libre, con o sin cantidad |
| PUT / DELETE | `/households/{id}/shopping-list/items/{itemId}` | Cambiar / quitar una línea |
| PUT | `/households/{id}/shopping-list/items/{itemId}/checked` | Marcar como comprado o desmarcar |
| DELETE | `/households/{id}/shopping-list/items/checked` | Quitar todo lo comprado |
| POST | `/households/{id}/shopping-list/items/checked/to-inventory?lang=` | Pasar lo comprado al inventario (lo que no tiene cantidad se queda) |
| POST | `/households/{id}/shopping-list/from-plan` | Añadir lo que les falta a las comidas de una semana, de hoy en adelante |

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
| R13 | **Datos de alérgenos** escritos a mano: un error puede ocultar un alérgeno a una persona alérgica | Alto | Marcado prudente, aviso visible de que no es una garantía y de comprobar la etiqueta. **Pendiente:** contrastar con una fuente autorizada antes del lanzamiento |
| R3 | **Fechas estimadas** erróneas (seguridad alimentaria, responsabilidad) | Alto | Siempre etiquetadas; reglas conservadoras; nunca pisan la fecha del usuario. **Pendiente:** contrastar los días con una fuente autorizada antes del lanzamiento |
| R4 | **Conversión de unidades** (recuento ↔ masa: "2 tomates" vs "300 g") | Medio | Peso medio por unidad en el catálogo; cuando no exista, no se convierte y se avisa |
| R5 | **Contenido de recetas**: licencias y normalización de ingredientes | Medio | Conjunto inicial propio y pequeño; no importar datasets sin revisar licencia |
| R6 | **Coste y latencia de IA** | Medio | `AIProvider` intercambiable, caché, límites por usuario, determinista por defecto |
| R7 | **Push en iOS**: requiere macOS, cuenta de Apple Developer y APNs | Medio | El entorno de desarrollo actual es Windows: iOS no se puede compilar ni probar aquí |
| R8 | Refresh token de la web en `localStorage` (expuesto a XSS) | Medio | Aceptado en Fase 1; migrar a cookie `HttpOnly` + `SameSite` antes de abrir al público |
| R9 | Sincronización offline y conflictos | Medio | Acotar a lectura en caché en el MVP |
| R10 | **RGPD**: imágenes de tickets, borrado de cuenta | Medio | Retención configurable, borrado en cascada, minimización desde el diseño |
| R11 | Versiones muy recientes (Spring Boot 4.1, TypeScript 7, Vite 8) | Bajo | Versiones fijadas; CI en cada cambio |
| R12 | Rate limiting y SSE en memoria no escalan horizontalmente | Bajo | Una instancia en el MVP; documentado el punto de sustitución |
