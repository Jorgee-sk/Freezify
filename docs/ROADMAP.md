# Freezify — Roadmap

Regla: una fase no se marca como completada hasta que su código está implementado **y probado**.
El estado detallado de cada entrega está en [STATUS.md](STATUS.md).

| Fase | Nombre | Estado |
|---|---|---|
| 0 | Product Definition | ✅ Completada |
| 1 | Foundation | 🟡 Implementada, CI sin confirmar — ver [STATUS.md](STATUS.md) |
| 2 | Inventory | 🟡 En curso: backend y web hechos; faltan móvil y SSE |
| 3 | Expiration Engine | ⏳ Pendiente |
| 4 | Recipes | ⏳ Pendiente |
| 5 | Smart Planning | ⏳ Pendiente |
| 6 | Shopping | ⏳ Pendiente |
| 7 | AI / OCR | ⏳ Pendiente |
| 8 | Analytics | ⏳ Pendiente |
| 9 | Product Polish | ⏳ Pendiente |
| 10 | Monetization | ⏳ Pendiente (solo tras validar uso real) |

## Fase 0 — Product Definition
`README.md`, `docs/PRODUCT_SPEC.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`.

## Fase 1 — Foundation
**Objetivo:** base ejecutable de extremo a extremo.

- Monorepo, `.gitignore`, `.env.example`
- Backend Spring Boot 4 / Java 21, PostgreSQL, Flyway
- Autenticación (registro, login, refresh rotatorio, logout), perfil de usuario
- Hogares: crear, listar, renombrar, eliminar, miembros, invitación por código
- Autorización por hogar con tests de aislamiento
- Errores uniformes (`application/problem+json`), correlation ID, health check, OpenAPI
- Web React: login, registro, hogares, miembros, invitación; es/en
- Mobile Flutter: login, registro, hogares, miembros, invitación; es/en
- Docker (backend, web, compose) y CI

**Criterio de salida:** un usuario se registra en web o móvil, crea un hogar, invita a otro usuario y ambos
ven el mismo hogar; un tercero no puede verlo.

## Fase 2 — Inventory
- Catálogo `Food` + `FoodCategory` (semilla es/en) — base del matching posterior
- `FoodItem` con cantidad (`BigDecimal` + unidad), ubicación, estado
- CRUD, filtros, búsqueda, paginación, autocompletado y recientes
- Consumir / descartar (genera `FoodConsumption` / `FoodWaste`)
- SSE para cambios de inventario entre miembros
- Tabla de eventos de producto (`food_added`, …)

## Fase 3 — Expiration Engine
- `expirationDate` + `expirationSource`; estimador por reglas (alimento × ubicación × abierto)
- Niveles de prioridad como función pura de dominio
- Panel "consume primero"
- Scheduler diario, notificaciones in-app y push (FCM), preferencias y anti-spam

## Fase 4 — Recipes
- `Recipe`, `RecipeIngredient` normalizado contra `Food`
- Conjunto inicial de recetas con licencia compatible
- Recomendador determinista con pesos configurables y explicación trazable
- Restricciones alimentarias como filtro duro

## Fase 5 — Smart Planning
- `MealPlan` / `MealPlanEntry`, vista semanal, mover/sustituir/eliminar
- Generador con prioridad de caducidad, variedad y reutilización

## Fase 6 — Shopping
- Lista generada desde el plan menos inventario, agrupada por categoría
- Modo compra, edición manual, sincronización por SSE

## Fase 7 — AI / OCR
Empieza solo cuando el núcleo determinista funciona.
- `AIProvider` (`Mock`, proveedor remoto, local) y `AiService`
- Ticket: OCR → extracción estructurada → revisión → inventario
- Foto de alimento con candidatos y confianza
- "Crea una receta con lo que tengo" con salida validada por esquema
- Almacenamiento privado de imágenes, URLs temporales, retención configurable

## Fase 8 — Analytics
Consumo, desperdicio, ahorro estimado, tendencias semanales y mensuales.

## Fase 9 — Product Polish
Onboarding, animaciones, accesibilidad, rendimiento, modo offline de lectura, casos límite.

## Fase 10 — Monetization
Premium, límites, billing. Solo con uso real validado.
