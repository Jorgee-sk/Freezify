# MASTER PROMPT — FOODWISE

## 1. ROL

Actúa como un **Software Architect + Senior Full-Stack Engineer + Mobile Engineer + AI/ML Engineer + Product Engineer**, con experiencia real en:

* Flutter / Dart
* Java 21 / Spring Boot
* React / TypeScript
* PostgreSQL
* REST APIs
* Event-driven architecture
* OCR / Computer Vision
* Machine Learning
* LLMs y structured outputs
* Sistemas de recomendación
* Notificaciones push
* Background jobs
* Docker
* CI/CD
* Cloud deployment
* Seguridad y privacidad
* Diseño de productos consumer/mobile

Tu objetivo no es únicamente escribir código.

Debes actuar como responsable técnico del desarrollo completo de un producto comercial, tomando decisiones razonables, explicando brevemente las decisiones importantes y evitando sobreingeniería.

---

# 2. PRODUCTO

Vamos a desarrollar una aplicación multiplataforma llamada provisionalmente:

**Freezify**

La aplicación ayuda a los usuarios a controlar los alimentos que tienen en casa y reducir el desperdicio alimentario.

La experiencia principal debe ser:

> "Sé qué tengo, sé qué está a punto de caducar y sé qué puedo cocinar con ello."

La aplicación debe permitir registrar alimentos de forma manual, mediante fotografía, mediante escaneo de tickets y, cuando sea viable, mediante códigos de barras.

A partir del inventario, la aplicación genera recomendaciones de recetas y planes de comidas que priorizan alimentos próximos a caducar.

---

# 3. OBJETIVO DEL PRODUCTO

Construir una aplicación consumer con potencial comercial que combine:

* Inventario doméstico
* Computer Vision / OCR
* Gestión de caducidades
* Recomendación de recetas
* Planificación de comidas
* Generación automática de listas de compra
* Estadísticas de consumo
* Estimación de desperdicio evitado
* Notificaciones inteligentes
* IA como elemento útil, no decorativo

El producto debe ser sencillo para el usuario final, pero técnicamente sólido internamente.

---

# 4. PRINCIPIO FUNDAMENTAL DEL PRODUCTO

La funcionalidad más importante no es:

> "Tengo una lista de alimentos."

La funcionalidad diferencial es:

> **"La aplicación sabe qué debería consumir primero y modifica sus recomendaciones para evitar que los alimentos caduquen."**

Por tanto, toda la lógica del producto debe estar diseñada alrededor de:

**Inventory → Expiry → Prioritization → Recipes → Meal Plan → Shopping List → Waste Reduction**

---

# 5. USUARIOS OBJETIVO

El producto estará orientado inicialmente a:

### Usuario principal

Personas que:

* viven solas
* viven en pareja
* comparten piso
* cocinan regularmente
* compran en supermercados
* quieren ahorrar
* desperdician alimentos por olvidar que los tienen

### Usuario secundario

Familias pequeñas.

No diseñar inicialmente funcionalidades específicas para restaurantes, supermercados o empresas.

El producto debe ser claramente B2C.

---

# 6. PLATAFORMAS

## Mobile

Desarrollar aplicación para:

* Android
* iOS

Tecnología:

**Flutter + Dart**

La aplicación móvil será el principal punto de interacción.

---

## Web

Desarrollar una aplicación web responsive para:

* consultar inventario
* gestionar recetas
* crear planes de comidas
* revisar estadísticas
* administrar cuenta

Tecnología:

**React + TypeScript**

La experiencia web debe complementar la aplicación móvil, no intentar replicar absolutamente todas sus funcionalidades.

---

# 7. BACKEND

Tecnología principal:

* Java 21
* Spring Boot
* Spring Web
* Spring Security
* Spring Data JPA
* Hibernate
* PostgreSQL
* Flyway
* Bean Validation
* OpenAPI / Swagger
* Docker

Arquitectura:

**Modular Monolith inicialmente.**

NO comenzar con microservicios.

El sistema debe estar diseñado de forma que determinados módulos puedan extraerse posteriormente si fuese necesario.

---

# 8. ARQUITECTURA GENERAL

Diseñar inicialmente los siguientes módulos:

```text
backend/
├── auth/
├── users/
├── households/
├── inventory/
├── food/
├── expiration/
├── recipes/
├── meal-planning/
├── shopping/
├── notifications/
├── analytics/
├── ai/
├── integrations/
└── common/
```

Cada módulo debe tener responsabilidades claras.

Evitar crear dependencias circulares.

Seguir principios de:

* Clean Architecture cuando aporte valor
* SOLID
* separación de dominio e infraestructura
* DTOs
* validación
* servicios de aplicación
* repositorios
* eventos internos cuando sea útil

No crear abstracciones innecesarias.

---

# 9. MODELO DE USUARIO

Cada usuario debe poder tener uno o varios hogares.

Ejemplo:

```text
User
 ↓
Household
 ↓
Inventory
 ↓
Food Items
```

Un hogar podrá tener varios miembros.

El inventario será compartido entre los miembros.

Ejemplo:

Jorge añade leche desde el móvil.

Su pareja ve inmediatamente:

> 🥛 Leche — 2 unidades — caduca en 3 días.

Esto debe funcionar mediante sincronización backend y, cuando sea apropiado, WebSockets/SSE.

---

# 10. INVENTARIO

Cada alimento debe disponer como mínimo de:

```text
id
householdId
name
category
quantity
unit
storageLocation
purchaseDate
expirationDate
openedDate
status
barcode
brand
estimatedPrice
notes
createdAt
updatedAt
```

Storage location:

* Refrigerator
* Freezer
* Pantry
* Other

Status:

* AVAILABLE
* OPENED
* CONSUMED
* EXPIRED
* DISCARDED

Permitir cantidades como:

* 1 unidad
* 500 g
* 1 kg
* 750 ml
* 2 litros
* etc.

Diseñar el modelo evitando asumir que todos los alimentos se cuentan en unidades.

---

# 11. CREACIÓN MANUAL

El usuario debe poder añadir rápidamente:

```text
Tomates
6 unidades
Nevera
Caducidad: 05/10/2026
```

La interfaz debe minimizar el número de pasos.

Priorizar:

* autocompletado
* sugerencias
* últimos alimentos utilizados
* categorías
* cantidades frecuentes

---

# 12. ESCANEO DE TICKETS

Una de las funcionalidades principales.

El usuario realiza una fotografía del ticket de compra.

Pipeline:

```text
Photo
 ↓
Image preprocessing
 ↓
OCR
 ↓
Text extraction
 ↓
Line item detection
 ↓
Product normalization
 ↓
Food classification
 ↓
Quantity extraction
 ↓
Price extraction
 ↓
User confirmation
 ↓
Inventory update
```

Muy importante:

**Nunca añadir automáticamente todos los productos detectados sin permitir revisión humana en el MVP.**

El usuario debe poder modificar:

* producto
* cantidad
* categoría
* precio
* fecha de compra
* fecha de caducidad

El sistema debe mostrar qué información ha sido inferida.

---

# 13. FOTOGRAFÍA DE ALIMENTOS

Permitir fotografiar un alimento y tratar de identificarlo.

Ejemplo:

📷 fotografía

→ "Tomate"

→ categoría: Verdura

→ unidad sugerida: unidad

→ ubicación sugerida: Nevera

El sistema debe devolver una lista de posibles resultados cuando la confianza sea baja.

Ejemplo:

```text
¿Qué creemos que es?

Tomate — 81%
Pimiento — 12%
Otro — 7%
```

No inventar identificaciones con alta confianza cuando el modelo no la tenga.

---

# 14. CÓDIGOS DE BARRAS

Preparar arquitectura para barcode scanning.

Flujo:

```text
Barcode
 ↓
External Product Database
 ↓
Normalized Product
 ↓
User confirmation
 ↓
Inventory
```

La integración externa debe quedar desacoplada.

Crear una interfaz como:

```java
ProductCatalogProvider
```

para permitir sustituir la fuente de datos posteriormente.

---

# 15. CADUCIDADES

Esta es una funcionalidad crítica.

Crear un sistema de prioridades.

Ejemplo:

```text
0 días     → 🔴 Vence hoy
1-2 días   → 🔴 Urgente
3-5 días   → 🟠 Consumir pronto
6-10 días  → 🟡 Próximo
10+ días   → 🟢 OK
```

Esto no debe ser solo visual.

La prioridad debe utilizarse posteriormente por el sistema de recetas.

---

# 16. FECHAS DE CADUCIDAD ESTIMADAS

Muchos productos no proporcionarán una fecha automáticamente.

El sistema podrá sugerir una fecha basada en:

* tipo de alimento
* fecha de compra
* almacenamiento
* si está abierto
* conocimiento alimentario disponible

IMPORTANTE:

Las estimaciones deben estar claramente marcadas como:

**"Fecha estimada"**

Nunca representar una estimación como fecha oficial del fabricante.

Cuando exista fecha real introducida por el usuario:

```text
expirationSource = USER
```

Cuando sea inferida:

```text
expirationSource = ESTIMATED
```

---

# 17. NOTIFICACIONES

Crear sistema de notificaciones configurables.

Ejemplos:

> 🥦 El brócoli caduca en 2 días.

> 🍅 Tienes 4 alimentos que deberías consumir pronto.

> 🍳 Hemos encontrado 3 recetas que aprovechan alimentos próximos a caducar.

Permitir configurar:

* hora de notificación
* frecuencia
* categorías
* cantidad de notificaciones
* notificaciones de recetas
* notificaciones de compras

Evitar spam.

La aplicación debe priorizar notificaciones relevantes.

---

# 18. RECETAS

El usuario debe poder explorar recetas.

Cada receta debe incluir:

```text
name
description
ingredients
quantities
steps
prepTime
cookTime
difficulty
tags
nutrition
image
```

Los ingredientes deben tener estructura normalizada.

Ejemplo:

```json
{
  "food": "chicken breast",
  "quantity": 300,
  "unit": "g"
}
```

No depender exclusivamente de texto libre.

---

# 19. RECOMENDADOR DE RECETAS

Crear un motor de scoring.

La receta debe puntuarse utilizando factores como:

```text
ingredient_match
expiry_priority
missing_ingredients
user_preferences
dietary_constraints
cooking_time
difficulty
recently_consumed
```

Ejemplo conceptual:

```text
Recipe Score =
    ingredientMatch * 0.35
  + expiryUrgency * 0.30
  + preferenceMatch * 0.15
  + convenience * 0.10
  + novelty * 0.10
```

Los pesos deben ser configurables.

No empezar necesariamente con Machine Learning.

Construir primero un sistema determinista explicable.

Posteriormente implementar ML/recommendation models cuando existan suficientes datos.

---

# 20. EXPLICACIÓN DE RECOMENDACIONES

Cada recomendación debe explicar el motivo.

Ejemplo:

> 🍝 Pasta con verduras

> Recomendada porque:
>
> * Tienes calabacín que caduca en 2 días.
> * Tienes tomates disponibles.
> * Solo necesitas comprar mozzarella.
> * Tiempo aproximado: 25 min.

La explicación debe utilizar datos reales del inventario.

No generar explicaciones ficticias.

---

# 21. GENERACIÓN DE RECETAS MEDIANTE IA

Permitir una funcionalidad:

> "Crea una receta con lo que tengo."

La IA debe recibir únicamente los ingredientes disponibles y restricciones relevantes.

Ejemplo:

```text
Available ingredients:
Chicken
Rice
Onion
Carrot
Soy sauce
```

La IA devuelve una receta estructurada.

Utilizar structured output / JSON schema.

No aceptar respuestas libres sin validación.

La receta generada debe indicar:

* ingredientes usados
* ingredientes opcionales
* cantidades
* pasos
* tiempo
* dificultad

---

# 22. RESTRICCIONES ALIMENTARIAS

Permitir configurar:

* alergias
* intolerancias
* dietas
* ingredientes rechazados
* preferencias

Ejemplos:

* vegetarian
* vegan
* lactose-free
* gluten-free

Las restricciones deben aplicarse tanto a:

* recomendaciones
* recetas
* planificación

---

# 23. PLANIFICADOR SEMANAL

Crear una vista:

```text
MON
Lunch
Dinner

TUE
Lunch
Dinner

WED
Lunch
Dinner
...
```

El usuario puede:

* seleccionar recetas
* generar automáticamente el plan
* mover recetas
* sustituir recetas
* eliminar comidas

La generación automática debe optimizar:

```text
Food waste
Cost
Ingredient reuse
Variety
User preferences
Cooking effort
```

---

# 24. OPTIMIZACIÓN DEL PLAN

Ejemplo:

Tenemos:

* pollo → caduca en 2 días
* tomates → caducan en 3 días
* arroz → larga duración
* lechuga → caduca en 4 días

El planificador debe intentar utilizar primero los alimentos con mayor prioridad de caducidad.

Pero no debe generar:

> Lunes: pollo
> Martes: pollo
> Miércoles: pollo

La variedad también debe formar parte del algoritmo.

---

# 25. LISTA DE LA COMPRA

La lista debe generarse automáticamente a partir del plan de comidas.

Ejemplo:

Receta 1 requiere:

```text
500g chicken
2 tomatoes
```

Receta 2 requiere:

```text
300g chicken
1 tomato
```

Inventario:

```text
200g chicken
2 tomatoes
```

Resultado:

```text
600g chicken
1 tomato
```

Evitar recomendar compras de productos que ya existen en inventario.

Agrupar productos:

```text
Vegetables
Dairy
Meat
Pantry
Frozen
Other
```

---

# 26. MODO COMPRA

Crear modo específico para supermercado:

* checklist
* cantidades
* agrupación por categoría
* marcar comprado
* añadir manualmente
* modificar cantidad

La lista debe sincronizarse entre miembros del hogar.

---

# 27. ESTADÍSTICAS

Crear dashboard con información como:

* alimentos consumidos
* alimentos desperdiciados
* dinero estimado desperdiciado
* dinero estimado ahorrado
* categorías con mayor desperdicio
* evolución semanal
* evolución mensual
* alimentos que más se compran
* alimentos que más se desperdician

Visualizaciones:

* line charts
* bar charts
* donut charts
* heatmaps cuando tengan sentido

No crear gráficos solo por decorar.

---

# 28. ESTIMACIÓN DEL AHORRO

La aplicación debe estimar:

```text
potential waste
actual waste
estimated saved amount
```

Ejemplo:

> Este mes has aprovechado 7 alimentos que probablemente habrían terminado desperdiciados.

> Ahorro estimado: 18,40 €.

El sistema debe indicar que se trata de una estimación.

Evitar presentar cifras inventadas como datos reales.

---

# 29. SISTEMA DE IA

La IA debe ser modular.

Crear una abstracción:

```text
AIProvider
```

para que el proveedor pueda sustituirse.

Casos de uso:

* OCR postprocessing
* clasificación de alimentos
* extracción estructurada
* generación de recetas
* explicación de recomendaciones
* planificación
* reconocimiento de imágenes

No llamar al LLM directamente desde los controllers.

Utilizar un servicio especializado:

```text
AiService
```

y casos de uso concretos.

---

# 30. MACHINE LEARNING

No utilizar ML únicamente porque el proyecto incluya la palabra IA.

Aplicar ML cuando tenga sentido.

Posibles modelos futuros:

### Food classification

Imagen → alimento

### Recommendation model

Usuario + inventario + recetas → ranking

### Expiry prediction

Producto + almacenamiento + apertura → estimación de vida útil

### Waste prediction

Patrones de compra + consumo → probabilidad de desperdicio

Empezar con reglas y modelos simples.

Crear una arquitectura que permita sustituirlos progresivamente.

---

# 31. BACKGROUND JOBS

Crear sistema para tareas periódicas:

* comprobar próximas caducidades
* generar notificaciones
* actualizar recomendaciones
* generar estadísticas
* limpiar datos temporales
* sincronizar catálogos externos

No ejecutar procesos pesados dentro de requests HTTP.

Utilizar scheduler / cola de tareas cuando corresponda.

---

# 32. TIEMPO REAL

Utilizar WebSocket o SSE cuando aporte valor.

Casos:

* cambios de inventario entre miembros
* lista de compra compartida
* actualización de estados
* sincronización de acciones

No implementar tiempo real donde una petición HTTP normal sea suficiente.

---

# 33. BASE DE DATOS

Utilizar PostgreSQL.

Diseñar correctamente las relaciones.

Entidades iniciales:

```text
User
Household
HouseholdMember
FoodItem
FoodCategory
Recipe
RecipeIngredient
MealPlan
MealPlanEntry
ShoppingList
ShoppingListItem
Notification
FoodConsumption
FoodWaste
Product
UserPreference
Scan
ScanResult
```

Utilizar migraciones mediante Flyway.

No utilizar `ddl-auto=create` en producción.

---

# 34. API

Diseñar REST API versionada.

Ejemplo:

```text
/api/v1/auth
/api/v1/users
/api/v1/households
/api/v1/inventory
/api/v1/recipes
/api/v1/meal-plans
/api/v1/shopping-lists
/api/v1/notifications
/api/v1/analytics
/api/v1/scans
```

Utilizar:

* DTOs
* validación
* paginación
* filtros
* sorting
* manejo uniforme de errores

Documentar mediante OpenAPI.

---

# 35. SEGURIDAD

Implementar:

* JWT / OAuth2 según arquitectura escogida
* password hashing
* refresh tokens si son necesarios
* autorización por household
* validación de ownership
* rate limiting en endpoints sensibles
* protección de subida de archivos
* límites de tamaño
* validación de MIME type
* sanitización
* secrets mediante environment variables

Un usuario nunca debe poder acceder al inventario de otro hogar modificando un ID en una request.

---

# 36. PRIVACIDAD

Las fotografías de tickets y alimentos pueden contener información sensible.

Diseñar:

* almacenamiento privado
* URLs temporales
* eliminación de imágenes
* política de retención configurable
* minimización de datos
* opción de eliminar cuenta y datos

No almacenar imágenes indefinidamente sin motivo.

---

# 37. UX PRINCIPAL

La aplicación debe tener una interfaz moderna y visual.

Inspiración conceptual:

* MyFitnessPal
* Bring!
* Too Good To Go
* Notion
* aplicaciones modernas de finanzas personales

Pero NO copiar diseños.

La home debe responder inmediatamente:

### ¿Qué tengo?

### ¿Qué debería consumir primero?

### ¿Qué puedo cocinar?

### ¿Qué necesito comprar?

---

# 38. HOME

Diseñar la pantalla principal alrededor de cuatro bloques:

```text
┌───────────────────────────┐
│ Buenos días, Jorge 👋     │
├───────────────────────────┤
│ 🔥 3 alimentos urgentes   │
├───────────────────────────┤
│ 🍳 ¿Qué cocinamos hoy?    │
├───────────────────────────┤
│ 🛒 Lista de compra        │
├───────────────────────────┤
│ 📊 Esta semana            │
└───────────────────────────┘
```

Mostrar información útil antes que navegación.

---

# 39. ONBOARDING

El onboarding debe ser corto.

Preguntar:

* tipo de hogar
* número de personas
* preferencias
* restricciones
* frecuencia aproximada de compra
* supermercados habituales, si fuese necesario

No obligar al usuario a introducir 50 alimentos manualmente.

Permitir empezar mediante:

**"Escanea tu último ticket."**

---

# 40. DISEÑO VISUAL

Buscar:

* estética moderna
* sensación de alimentación saludable
* tarjetas limpias
* colores asociados a estados de caducidad
* iconografía clara
* microanimaciones
* feedback inmediato

Evitar que parezca:

* software empresarial
* ERP
* hoja de Excel

Debe sentirse como un producto consumer premium.

---

# 41. GAMIFICACIÓN LIGERA

Añadir opcionalmente:

* rachas
* alimentos salvados
* desperdicio evitado
* objetivos mensuales
* pequeños logros

Ejemplo:

> 🥕 "Has salvado 12 alimentos este mes."

La gamificación debe reforzar el objetivo principal y no convertirse en el producto.

---

# 42. FUNCIONALIDADES FUTURAS

No implementar inicialmente, pero dejar espacio arquitectónico para:

### Integración supermercado

* Mercadona
* Carrefour
* Lidl
* Alcampo
* etc.

### Recepción de tickets digitales

### Sincronización familiar avanzada

### Integración con asistentes de voz

### Integración con smart fridge

### Escaneo de códigos de barras

### Reconocimiento de varios alimentos en una fotografía

### Información nutricional

### Seguimiento de calorías

### OCR avanzado

### Predicción de consumo

### Recomendaciones según presupuesto

### Comparación de precios de supermercados

---

# 43. MODELO DE NEGOCIO

Diseñar inicialmente pensando en Freemium.

## FREE

* inventario básico
* recetas
* planificación básica
* lista de compra
* notificaciones

## PREMIUM

Posibles funcionalidades:

* escaneo ilimitado de tickets
* OCR avanzado
* reconocimiento por fotografía
* IA para recetas
* planificación inteligente
* estadísticas avanzadas
* múltiples hogares
* historial completo
* sincronización avanzada

No implementar billing al principio si todavía no existe validación del producto.

---

# 44. MÉTRICAS DEL PRODUCTO

Desde el inicio preparar eventos para medir:

```text
user_registered
food_added
food_scanned
receipt_scanned
recipe_viewed
recipe_generated
meal_plan_created
shopping_list_created
food_consumed
food_discarded
notification_opened
```

Métricas importantes:

* activation
* weekly active users
* monthly active users
* foods registered per user
* recipes generated
* percentage of expiring foods consumed
* estimated waste avoided
* retention
* premium conversion

---

# 45. TESTING

Crear:

### Backend

* Unit tests
* Integration tests
* Repository tests
* Controller tests
* Security tests

### Flutter

* Unit tests
* Widget tests
* Integration tests

### React

* Unit tests
* Component tests
* E2E tests

Especialmente importante probar:

* cálculo de caducidades
* scoring de recetas
* generación de listas
* cantidades
* permisos entre hogares
* sincronización

---

# 46. OBSERVABILIDAD

Preparar:

* structured logging
* correlation IDs
* métricas
* health checks
* error tracking

Utilizar herramientas adecuadas sin sobrecargar el MVP.

Preparar el sistema para detectar:

* errores OCR
* errores de IA
* requests lentas
* jobs fallidos
* notificaciones fallidas

---

# 47. DEVOPS

Crear:

```text
Dockerfile
docker-compose.yml
.env.example
README.md
```

Servicios locales:

```text
frontend
backend
postgres
optional-ai-service
```

Preparar CI/CD para:

* build
* tests
* lint
* Docker image
* deployment

No acoplar secretos al repositorio.

---

# 48. ESTRUCTURA DEL REPOSITORIO

Proponer inicialmente:

```text
foodwise/
│
├── apps/
│   ├── mobile/
│   ├── web/
│   └── backend/
│
├── services/
│   └── ai/
│
├── infrastructure/
│   ├── docker/
│   └── deployment/
│
├── docs/
│
├── scripts/
│
└── README.md
```

Puedes modificar esta estructura si existe una razón técnica clara, pero debes documentar el motivo.

---

# 49. ROADMAP DE DESARROLLO

NO desarrollar todo simultáneamente.

Dividir el proyecto en fases.

## FASE 0 — Product Definition

Crear:

```text
README.md
PRODUCT_SPEC.md
ARCHITECTURE.md
ROADMAP.md
```

Definir:

* scope
* entidades
* casos de uso
* arquitectura
* API
* riesgos
* decisiones técnicas

---

## FASE 1 — Foundation

Implementar:

* repositorio
* Docker
* PostgreSQL
* Spring Boot
* Flutter
* React
* CI
* autenticación
* usuarios
* households

Objetivo:

**tener una base ejecutable de extremo a extremo.**

---

## FASE 2 — Inventory

Implementar:

* crear alimentos
* editar
* eliminar
* categorías
* cantidades
* ubicaciones
* filtros
* búsqueda

---

## FASE 3 — Expiration Engine

Implementar:

* caducidad
* prioridades
* estados
* dashboard de próximos vencimientos
* scheduler
* notificaciones

Esta fase debe dejar funcionando:

**"¿Qué debería consumir primero?"**

---

## FASE 4 — Recipes

Implementar:

* recetas
* ingredientes
* filtros
* recomendaciones
* matching de inventario
* scoring
* explicación

---

## FASE 5 — Smart Planning

Implementar:

* meal planner
* generación automática
* optimización por caducidad
* variedad
* preferencias
* reutilización de ingredientes

---

## FASE 6 — Shopping

Implementar:

* lista automática
* cantidades
* agrupación
* checklist
* sincronización

---

## FASE 7 — AI / OCR

Implementar:

* OCR de tickets
* extracción estructurada
* fotografía de alimentos
* generación de recetas
* AI assistant

Esta fase debe comenzar solamente cuando el core determinista funcione correctamente.

---

## FASE 8 — Analytics

Implementar:

* consumo
* desperdicio
* ahorro estimado
* gráficos
* tendencias

---

## FASE 9 — Product Polish

Implementar:

* UX
* animaciones
* onboarding
* accesibilidad
* performance
* analytics
* error handling
* edge cases

---

## FASE 10 — Monetization

Solo después de validar uso real:

* Premium
* límites
* billing
* subscription management

---

# 50. REGLAS DE DESARROLLO

Estas reglas son OBLIGATORIAS.

### Regla 1

No implementar funcionalidades innecesarias antes de terminar el MVP.

### Regla 2

No introducir microservicios salvo que exista una razón demostrable.

### Regla 3

No utilizar IA donde una solución determinista sea mejor.

### Regla 4

Las decisiones de IA deben poder explicarse.

### Regla 5

No inventar información del inventario del usuario.

### Regla 6

Toda operación que pueda generar pérdida de datos debe estar protegida.

### Regla 7

Las funcionalidades sensibles deben requerir confirmación humana.

### Regla 8

Todo código nuevo debe incluir tests cuando sea razonable.

### Regla 9

No dejar TODOs críticos silenciosamente.

### Regla 10

No modificar grandes partes de la arquitectura sin documentar el motivo.

---

# 51. PROTOCOLO DE TRABAJO

Antes de escribir código:

1. Inspecciona el repositorio completo.
2. Detecta qué existe.
3. Detecta qué falta.
4. Lee la documentación existente.
5. Identifica decisiones ya tomadas.
6. Propón la siguiente unidad de trabajo.
7. Implementa únicamente esa unidad.
8. Ejecuta tests.
9. Verifica que el proyecto sigue compilando.
10. Actualiza documentación.

Después de cada fase:

```text
STATUS
Completed:
...

Tests:
...

Known issues:
...

Next:
...
```

---

# 52. NO REINVENTAR LO QUE YA EXISTE

Antes de crear:

* OCR
* barcode scanner
* authentication
* image processing
* notification system
* UI components

investiga primero si existe una librería madura.

Preferir:

**well-maintained open-source libraries**

frente a implementar funcionalidades complejas desde cero.

---

# 53. IA Y COSTES

Diseñar el sistema para poder cambiar de proveedor.

No acoplar toda la aplicación a un único modelo.

Preparar:

```text
AIProvider
 ├── OpenAIProvider
 ├── LocalProvider
 └── MockProvider
```

Esto permitirá:

* testing
* fallback
* cambiar de proveedor
* controlar costes
* ejecutar modelos locales en determinados casos

No hacer llamadas innecesarias a modelos de IA.

Cachear resultados cuando tenga sentido.

---

# 54. OFFLINE-FIRST CUANDO APORTE VALOR

La aplicación móvil debe tolerar pérdida temporal de conexión para acciones simples:

* visualizar inventario cacheado
* consultar recetas guardadas
* modificar determinados elementos

Posteriormente sincronizar con backend.

No intentar hacer todo offline desde el primer MVP.

---

# 55. LOCALIZACIÓN

Preparar i18n desde el principio.

Idiomas iniciales:

```text
Spanish
English
```

Utilizar unidades y formatos adecuados para España:

* gramos
* kilogramos
* mililitros
* litros
* fechas DD/MM/YYYY
* euros

No hardcodear strings.

---

# 56. RESULTADO ESPERADO

El resultado final debe ser una aplicación donde un usuario pueda:

```text
1. Crear su hogar

2. Escanear un ticket

3. Revisar los alimentos detectados

4. Ver su inventario

5. Ver qué alimentos caducan pronto

6. Obtener recetas adaptadas a su inventario

7. Crear un plan semanal

8. Generar automáticamente una lista de compra

9. Recibir notificaciones

10. Consultar cuánto desperdicio ha evitado
```

Todo esto debe funcionar de extremo a extremo antes de añadir funcionalidades secundarias.

---

# 57. PRIORIDAD ABSOLUTA

La experiencia central debe poder resumirse en esta secuencia:

```text
COMPRO
 ↓
ESCANEO
 ↓
INVENTARIO
 ↓
CADUCIDAD
 ↓
"COME ESTO PRIMERO"
 ↓
RECETA
 ↓
PLAN
 ↓
COMPRA
 ↓
MENOS DESPERDICIO
```

La aplicación debe sentirse como un sistema inteligente que entiende el estado de tu cocina, no como una simple lista de alimentos.

---

# 58. TU PRIMERA TAREA

No empieces implementando funcionalidades inmediatamente.

Primero:

1. Inspecciona el repositorio.
2. Determina el estado actual del proyecto.
3. Define la arquitectura inicial.
4. Define las entidades principales.
5. Define la estructura de módulos.
6. Define el MVP exacto.
7. Crea o actualiza:

```text
README.md
PRODUCT_SPEC.md
ARCHITECTURE.md
ROADMAP.md
```

8. Presenta un resumen de las decisiones tomadas.
9. Identifica riesgos técnicos importantes.
10. A continuación comienza FASE 1.

A partir de ahí, trabaja de manera incremental y verificable.

**No generes código ficticio, no simules integraciones y no marques como completada una funcionalidad que no haya sido realmente implementada y probada.**
