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
| `food` | Catálogo canónico de alimentos, categorías, unidades y cantidades; alias (abreviaturas y nombres de cada hogar) | common | 2 ✔ |
| `inventory` | Alimentos del hogar, consumo y descarte | households, food | 2 ✔ |
| `expiration` | Niveles de prioridad y estimación de fechas por reglas de vida útil | food | 3 ✔ |
| `notifications` | Avisos de caducidad dentro de la app, preferencias, dispositivos y envío push por FCM | households, inventory, expiration, food, users | 3 ✔ |
| `recipes` | Catálogo de recetas, recomendador, registro de lo cocinado y receta escrita por IA con lo que hay en casa | food, inventory, expiration, households, ai | 4 ✔ / 7 ✔ |
| `mealplanning` | Plan semanal, simulación de la despensa y generador | recipes, inventory, food, expiration, households | 5 ✔ |
| `shopping` | Lista de la compra del hogar, llenada a partir del plan; lo comprado pasa al inventario | mealplanning, inventory, food, households | 6 ✔ |
| `ai` | `AiService` (casos de uso de IA: leer un ticket, escribir una receta), `AiProvider` (sin modelo o API compatible con OpenAI) y límite diario por persona | food, common | 7 ✔ |
| `scanning` | Escaneo de tickets: lectura por reglas o con IA, asociación al catálogo, alias aprendidos por hogar, confirmación al inventario | ai, food, inventory, households | 7 ✔ |
| `integrations` | `ProductCatalogProvider` y fuentes externas | food | 7+ |
| `analytics` | Eventos de producto (hecho) y estadísticas (Fase 8) | escucha eventos de `users`, `inventory`, `notifications`, `recipes`, `mealplanning`, `shopping` y `scanning` | 2 ✔ / 8 |
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
| D61 | La web lleva su refresh token en una **cookie `HttpOnly`** pedida con la cabecera `X-Freezify-Session: cookie`; el móvil sigue con el token en el cuerpo | Un XSS no puede llevarse el token; la cabecera, que otro sitio no puede enviar, protege la cookie de peticiones cruzadas sin un token CSRF aparte |
| D62 | Las invitaciones se **revocan borrándolas**; las puede revocar quien las generó o el propietario | Quien ya se unió no depende del código; sin una columna de estado que mantener |
| D63 | **Transferir** el hogar intercambia los papeles en una transacción | Siempre hay exactamente un propietario, y así el anterior puede abandonarlo |
| D64 | El **OCR del ticket corre en el móvil** (ML Kit) y solo viaja el texto; la copia de la foto se borra | Gratis y sin conexión; las fotos no salen del teléfono ni hay que almacenarlas |
| D65 | **Leer un ticket no guarda nada**; confirmar envía las líneas ya revisadas | Sin tablas de borradores que caduquen ni datos que retener; nada entra al inventario sin revisión (P2) |
| D66 | Las **reglas** leen siempre el ticket; el modelo es opcional y su respuesta se valida y se **descarta entera** si falla | La app funciona sin IA y sin coste; un modelo que se equivoca o inventa no llega a la revisión |
| D67 | Un único proveedor **compatible con la API de OpenAI** (Gemini, Ollama, OpenAI) sin SDK, elegido por variables de entorno | Cambiar de proveedor, o pasar de la capa gratuita a uno local, es configuración y no código |
| D68 | **Alias por hogar** aprendidos al confirmar, sobre alias compartidos escritos a mano (`receipt_aliases`) | Cada hogar compra en sus tiendas: lo que corrige una vez se reconoce después (R1) |
| D69 | La receta con IA **solo recibe lo que hay en casa y el hogar come**, y su respuesta se valida dos veces: contra lo enviado (módulo `ai`) y contra el catálogo (módulo `recipes`) | Lo que el hogar no come nunca llega al modelo; una receta que usa o nombra lo que no hay no llega a nadie (P5) |
| D70 | La receta generada **no se guarda** y su porqué se calcula con las fechas del inventario | Sin recetas de calidad desconocida en el catálogo; la explicación no la inventa el modelo (P3) |
| D71 | El **límite diario de IA se cuenta en la base de datos** con un `insert … on conflict … returning` | Sobrevive a reinicios, vale con varias instancias y dos llamadas a la vez no se cuelan |
| D72 | Los **alias de alimentos son del módulo `food`** (`FoodAliases`), aunque los aprenda el escaneo | El inventario los usa para reconocer nombres escritos a mano sin depender del escaneo (que depende del inventario) |
| D73 | Las recetas generadas se comprueban también contra una **lista escrita a mano de alérgenos y carnes fuera del catálogo** | Lo que más daño haría que el modelo añadiera por su cuenta es lo que alguien no puede comer |
| D74 | La **foto de un alimento** va al modelo en la petición y **no se guarda**; su tipo se comprueba por el contenido | Sin almacenamiento de imágenes, URLs firmadas ni retención que gestionar; nada que no sea una foto sale del servidor |
| D75 | El modelo **elige entre los alimentos del catálogo** (o "otro") y devuelve **varios candidatos con su confianza** | Lo elegido se añade como un alimento del catálogo, y la duda se ve en lugar de esconderse tras una única respuesta (P7) |
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
`V9__shopping_list.sql`, `V10__receipt_aliases.sql`, `V11__ai_usage.sql`):

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
households (opcional) ──< receipt_aliases >── foods
users ──< ai_usage
```

Entidades previstas por fase:

| Fase | Entidades |
|---|---|
| 2 ✔ | `Food` (catálogo), `FoodItem`, `FoodOutcome` (consumo y desperdicio), `ProductEvent`; `FoodCategory` es un enum |
| 3 ✔ | `ShelfLifeRule`, `Notification`, `NotificationPreference`, `DeviceToken` |
| 4 ✔ | `Recipe`, `RecipeIngredient`, `CookedRecipe`, `FoodTrait`, `HouseholdDiet`; `UserPreference` (gustos personales) pendiente |
| 5 | `MealPlanEntry` (hecha); `MealPlan` es la semana de un hogar, sin tabla (D46) |
| 6 ✔ | `ShoppingListItem`; la lista es la del hogar, sin tabla (D57) |
| 7 | `ReceiptAlias` (hecha); el escaneo no guarda borradores (D65); `Product` (códigos de barras) sin hacer |

`Food` (alimento canónico, p. ej. "tomate") es la pieza que une todo: `FoodItem`, `RecipeIngredient` y
`ShoppingListItem` apuntan a él. Sin esa normalización no hay matching fiable entre inventario y recetas.

## 5. API

REST versionada bajo `/api/v1`, documentada con OpenAPI (`/v3/api-docs`, Swagger UI en `/swagger-ui.html`; desactivados salvo con `FREEZIFY_API_DOCS=true` o en local, D56).

Fase 1:

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/register` | Alta; devuelve tokens y usuario |
| POST | `/auth/login` | Login |
| POST | `/auth/refresh` | Rota el refresh token (del cuerpo, o de la cookie con `X-Freezify-Session: cookie`) |
| POST | `/auth/logout` | Revoca el refresh token y, en la web, borra la cookie |
| GET / PATCH | `/users/me` | Perfil |
| POST / GET | `/households` | Crear / listar los míos |
| GET / PATCH / DELETE | `/households/{id}` | Detalle / renombrar (owner) / eliminar (owner) |
| GET | `/households/{id}/members` | Miembros |
| DELETE | `/households/{id}/members/{userId}` | Expulsar (owner) o abandonar (uno mismo) |
| POST | `/households/{id}/invitations` | Genera un código de invitación |
| GET | `/households/{id}/invitations` | Códigos que aún permiten unirse |
| DELETE | `/households/{id}/invitations/{code}` | Revoca un código (quien lo generó o el propietario) |
| POST | `/households/{id}/owner` | El propietario hace propietario a otro miembro |
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
| POST | `/households/{id}/scans/receipt?lang=` | Leer el texto de un ticket en un borrador para revisar; no guarda nada |
| POST | `/households/{id}/scans/receipt/confirm` | Poner en el inventario las líneas revisadas y recordar el alimento elegido para cada texto |
| POST | `/households/{id}/scans/food?lang=` | Foto de un alimento (multipart `image`, JPEG/PNG/WebP, ≤ 5 MB): candidatos del catálogo con su confianza; no se guarda |
| GET | `/ai` | Si el servidor tiene un modelo de lenguaje |
| GET | `/households/{id}/recipes/generated/ingredients?lang=` | Lo que una receta generada puede usar: lo que se le daría al modelo, sin los básicos |
| POST | `/households/{id}/recipes/generated?lang=` | Receta escrita por IA con lo que hay en casa y el hogar come (1–8 raciones; `use`: hasta 3 alimentos que tiene que usar); no se guarda |

Convenciones: DTOs como `record`, Bean Validation, fechas ISO-8601 en UTC, paginación `page`/`size`/`sort`
en las colecciones que puedan crecer (a partir de Fase 2).

## 6. Clientes

| | Web | Mobile |
|---|---|---|
| Framework | React 19 + TypeScript + Vite | Flutter (Dart 3) |
| Estado servidor | TanStack Query | Riverpod |
| Navegación | React Router | go_router |
| HTTP | `fetch` con wrapper tipado y refresh automático | dio con interceptor de refresh |
| Tokens | Access en memoria; refresh en una cookie `HttpOnly` que ningún script lee (D61) | `flutter_secure_storage` (Keystore / Keychain) |
| i18n | i18next (es, en) | `flutter_localizations` + ARB (es, en) |
| Tests | Vitest + Testing Library | `flutter_test` |

Offline (móvil): lectura desde caché a partir de Fase 2; escritura offline con cola solo para acciones
simples y solo si los datos de uso lo justifican (Fase 9).

## 7. IA (Fase 7)

```text
ScanController → ReceiptScanService ─┬─ ReceiptParser (reglas, siempre)
                                     ├─ AiService.readReceipt → AiProvider ─┬─ NoAiProvider (por defecto)
                                     │                                       └─ OpenAiCompatibleProvider
                                     │                                           (Gemini, Ollama, OpenAI…)
                                     └─ FoodMatcher (catálogo + receipt_aliases)

RecipeController → RecipeGenerator ─┬─ inventario filtrado por la dieta del hogar
                                    ├─ AiService.writeRecipe → AiProvider (la misma cadena)
                                    └─ FoodMentions (la receta no nombra alimentos que no usa)

ScanController → FoodPhotoService ── tipo por contenido → AiService.identifyFood → AiProvider
                                                          (imagen como data URL, catálogo como opciones)
```

- Los controladores nunca llaman a un modelo; `AiService` expone casos de uso concretos, nunca un "chat".
- Toda respuesta se valida en el backend contra el esquema pedido; si una parte no valida, o cita texto que
  no está en la entrada, se descarta entera y se usa la vía sin IA. En los tests el proveedor se sustituye.
- El proveedor se elige por variables de entorno (`FREEZIFY_AI_*`); sin ellas no hay modelo y todo funciona.
- Al modelo solo le llega lo necesario: el texto del ticket con los números largos tapados.
- Límite diario de llamadas por persona (en la tabla `ai_usage`) y tiempo máximo por llamada, para no agotar
  cuotas gratuitas.
- No hay caché por hash: el mismo ticket rara vez se lee dos veces. Se añadirá si los datos de uso lo piden.
- OCR: ML Kit en el dispositivo; el modelo solo estructura texto (D64).

## 8. Riesgos técnicos

| # | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| R1 | **Normalización de alimentos**: los tickets españoles abrevian ("TOM PERA 1K"); sin mapear a `Food` no hay recetas ni lista de la compra fiables | Alto | Catálogo canónico con alias desde Fase 2; revisión humana obligatoria; aprender alias de las correcciones del usuario |
| R2 | **Calidad del OCR** en tickets arrugados o térmicos | Alto | OCR en dispositivo + estructuración posterior; la UI de revisión es el camino principal, no la excepción. **Pendiente:** probar ML Kit con tickets reales en un móvil |
| R14 | **Datos enviados a un modelo externo**: en la capa gratuita de Gemini, Google puede usarlos para mejorar sus productos | Medio | Del ticket solo se envía el texto, con los números largos tapados; la foto de un alimento sí sale, reducida y con aviso en pantalla, y no se guarda en Freezify; el modelo es opcional. Con usuarios reales: capa de pago u Ollama, e informarlo en la política de privacidad |
| R13 | **Datos de alérgenos** escritos a mano: un error puede ocultar un alérgeno a una persona alérgica | Alto | Marcado prudente, aviso visible de que no es una garantía y de comprobar la etiqueta. **Pendiente:** contrastar con una fuente autorizada antes del lanzamiento |
| R3 | **Fechas estimadas** erróneas (seguridad alimentaria, responsabilidad) | Alto | Siempre etiquetadas; reglas conservadoras; nunca pisan la fecha del usuario. **Pendiente:** contrastar los días con una fuente autorizada antes del lanzamiento |
| R4 | **Conversión de unidades** (recuento ↔ masa: "2 tomates" vs "300 g") | Medio | Peso medio por unidad en el catálogo; cuando no exista, no se convierte y se avisa |
| R5 | **Contenido de recetas**: licencias y normalización de ingredientes | Medio | Conjunto inicial propio y pequeño; no importar datasets sin revisar licencia |
| R6 | **Coste y latencia de IA** | Medio | `AIProvider` intercambiable, caché, límites por usuario, determinista por defecto |
| R7 | **Push en iOS**: requiere macOS, cuenta de Apple Developer y APNs | Medio | El entorno de desarrollo actual es Windows: iOS no se puede compilar ni probar aquí |
| R8 | Refresh token de la web robado por XSS | Medio | ✅ Mitigado: cookie `HttpOnly`, `Secure`, `SameSite=Strict`, limitada a `/api/v1/auth` y que solo se usa con la cabecera `X-Freezify-Session` (D61). Un XSS aún podría usar la sesión mientras la página está abierta, pero no llevársela |
| R9 | Sincronización offline y conflictos | Medio | Acotar a lectura en caché en el MVP |
| R10 | **RGPD**: imágenes de tickets, borrado de cuenta | Medio | Retención configurable, borrado en cascada, minimización desde el diseño |
| R11 | Versiones muy recientes (Spring Boot 4.1, TypeScript 7, Vite 8) | Bajo | Versiones fijadas; CI en cada cambio |
| R12 | Rate limiting y SSE en memoria no escalan horizontalmente | Bajo | Una instancia en el MVP; documentado el punto de sustitución |
