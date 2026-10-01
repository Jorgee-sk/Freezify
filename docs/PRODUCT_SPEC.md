# Freezify — Especificación de producto

> Nombre provisional. El prompt maestro (`masterPrompt.md`) usa "FoodWise" en el título y "Freezify" como
> nombre de producto; se adopta **Freezify** en código, paquetes y documentación.

## 1. Propuesta de valor

> "Sé qué tengo, sé qué está a punto de caducar y sé qué puedo cocinar con ello."

Freezify no es una lista de alimentos. Su función diferencial es **decidir qué debería consumirse primero**
y adaptar recetas, plan semanal y lista de la compra para que nada caduque.

Cadena de valor que guía todas las decisiones:

```text
Inventory → Expiry → Prioritization → Recipes → Meal Plan → Shopping List → Waste Reduction
```

## 2. Usuarios

| Tipo | Perfil |
|---|---|
| Principal | Personas que viven solas, en pareja o comparten piso, cocinan con regularidad, compran en supermercado y tiran comida por olvido. |
| Secundario | Familias pequeñas. |
| Fuera de alcance | Restaurantes, supermercados, empresas. El producto es B2C. |

## 3. Alcance del MVP

El MVP es el recorrido completo de la sección 56 del prompt maestro, de extremo a extremo:

1. Crear cuenta y hogar (e invitar a otra persona).
2. Añadir alimentos: manualmente y **escaneando un ticket con revisión humana obligatoria**.
3. Ver el inventario compartido del hogar.
4. Ver qué caduca pronto, ordenado por prioridad.
5. Recibir recetas puntuadas según inventario y caducidad, con explicación basada en datos reales.
6. Crear un plan semanal (manual y generado).
7. Generar la lista de la compra a partir del plan, descontando el inventario.
8. Recibir notificaciones de caducidad.
9. Consultar consumo, desperdicio y ahorro estimado.

### Dentro del MVP

| Área | Incluido |
|---|---|
| Cuenta | Registro, login, refresh, logout, perfil, idioma (es/en) |
| Hogares | Crear, renombrar, miembros, invitación por código, abandonar |
| Inventario | CRUD, cantidades con unidad (g, kg, ml, l, unidad), ubicación, estado, filtros, búsqueda |
| Caducidad | Fecha real o estimada (marcada como tal), niveles de prioridad, panel "consume primero" |
| Recetas | Catálogo con ingredientes normalizados, filtros, recomendador determinista explicable |
| Plan semanal | Comida/cena por día, generación automática con variedad |
| Compra | Lista generada, agrupada por categoría, checklist compartido |
| Escaneo | Ticket → OCR → líneas → revisión → inventario |
| Notificaciones | Caducidad próxima, resumen diario, configurables |
| Estadísticas | Consumido, desperdiciado, ahorro estimado (siempre etiquetado como estimación) |

### Fuera del MVP (espacio arquitectónico reservado, sin implementar)

Códigos de barras, foto de alimento, receta generada por IA, asistente conversacional, gamificación,
integraciones con supermercados, tickets digitales, voz, smart fridge, calorías, comparación de precios,
premium y billing.

## 4. Reglas de producto no negociables

| # | Regla | Consecuencia técnica |
|---|---|---|
| P1 | Una fecha estimada nunca se presenta como oficial | `expirationSource ∈ {USER, ESTIMATED}` en modelo, API y UI |
| P2 | Nada detectado por OCR/IA entra al inventario sin revisión | El escaneo produce un borrador (`ScanResult`); solo la confirmación escribe en inventario |
| P3 | Las explicaciones de recetas usan datos reales | La explicación se construye a partir de los factores del scoring, no de texto libre de un LLM |
| P4 | Las cifras de ahorro son estimaciones | Se devuelven con indicador `estimated` y la UI lo muestra |
| P5 | No se inventa inventario | La IA solo recibe ingredientes existentes; su salida se valida contra un esquema |
| P6 | Aislamiento por hogar | Toda consulta se filtra por pertenencia al hogar; ningún ID de la petición es de fiar |
| P7 | Baja confianza ⇒ varias opciones | El reconocimiento devuelve candidatos con confianza, nunca una única respuesta rotunda |

## 5. Casos de uso principales

### UC-1 Alta y hogar
Registro → se crea el usuario → crea un hogar (pasa a ser `OWNER`) → genera un código de invitación →
la otra persona se une con el código y pasa a ser `MEMBER`.

### UC-2 Añadir alimento manualmente
Nombre (autocompletado sobre catálogo y recientes) → cantidad + unidad → ubicación → caducidad opcional.
Si no hay fecha, el sistema propone una **estimada** que el usuario puede aceptar o cambiar.

### UC-3 Escanear ticket
Foto → OCR → detección de líneas → normalización a alimentos del catálogo → pantalla de revisión
(cada campo inferido está marcado) → confirmación → alta en inventario.

### UC-4 "Consume primero"
Lista del hogar ordenada por nivel de prioridad:

| Días hasta caducar | Nivel | Etiqueta |
|---|---|---|
| < 0 | `EXPIRED` | Caducado |
| 0 | `TODAY` | Vence hoy |
| 1–2 | `URGENT` | Urgente |
| 3–5 | `SOON` | Consumir pronto |
| 6–10 | `UPCOMING` | Próximo |
| > 10 | `OK` | OK |

El nivel no es decorativo: es la entrada `expiryUrgency` del recomendador.

### UC-5 Recomendación de recetas
```text
score = ingredientMatch·0.35 + expiryUrgency·0.30 + preferenceMatch·0.15 + convenience·0.10 + novelty·0.10
```
Pesos configurables. Las restricciones alimentarias (alergias, dietas) son **filtros duros**, no factores
de puntuación. Cada resultado devuelve los factores y los ingredientes del inventario que los originan.

### UC-6 Plan semanal
Generación voraz con penalización por repetición: prioriza ingredientes urgentes, reutiliza ingredientes
ya abiertos y penaliza repetir proteína principal o receta en días consecutivos.

### UC-7 Lista de la compra
`necesario(plan) − disponible(inventario)`, sumando en unidad canónica por alimento.
Ejemplo de aceptación: 500 g + 300 g de pollo con 200 g en casa ⇒ 600 g; 2 + 1 tomates con 2 en casa ⇒ 1.

### UC-8 Cierre del ciclo
Marcar un alimento como consumido o descartado alimenta `FoodConsumption` / `FoodWaste`, que son la base
de las estadísticas y del ahorro estimado.

## 6. Modelo freemium (solo diseño)

| Free | Premium (posible) |
|---|---|
| Inventario, recetas, plan básico, lista de la compra, notificaciones | Escaneo ilimitado, foto de alimento, recetas con IA, plan inteligente, estadísticas avanzadas, varios hogares, historial completo |

No se implementa billing hasta validar uso real (Fase 10). El único preparativo es que los límites se
puedan expresar como política por hogar/usuario sin tocar los casos de uso.

## 7. Métricas

Eventos a registrar: `user_registered`, `food_added`, `food_scanned`, `receipt_scanned`, `recipe_viewed`,
`recipe_generated`, `meal_plan_created`, `shopping_list_created`, `food_consumed`, `food_discarded`,
`notification_opened`.

Métrica norte: **porcentaje de alimentos próximos a caducar que acaban consumidos**.
Secundarias: activación (hogar creado + ≥5 alimentos en 24 h), WAU/MAU, retención, alimentos por usuario.

## 8. Idioma y formatos

Español e inglés desde el inicio. Fechas `DD/MM/YYYY`, euros, sistema métrico. Ninguna cadena de UI
se escribe directamente en el código.
