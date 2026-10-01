# Estado del proyecto

Última actualización: 2026-10-01

| Fase | Estado |
|---|---|
| 0 — Product Definition | ✅ Completada |
| 1 — Foundation | ✅ Completada (CI en verde en el pull request #1) |
| 2 — Inventory | 🟡 En curso: backend y web hechos; faltan móvil y tiempo real |

## Fase 2 — Inventory 🟡

### Completed

**Backend**
- Módulo `food`: catálogo canónico de 78 alimentos (es/en) con categoría, unidad y ubicación habituales;
  búsqueda para autocompletado sin distinguir mayúsculas ni acentos (`GET /foods`).
- Modelo de cantidades: `Quantity` (`BigDecimal` + unidad) con dimensiones masa / volumen / recuento,
  conversión y aritmética solo dentro de la misma dimensión.
- Módulo `inventory`: alimentos del hogar con cantidad, ubicación, estado, fechas, marca, precio y notas.
  - Crear, ver, editar, eliminar.
  - Listado paginado con filtros (estado, ubicación, categoría, texto) y orden (caducidad, nombre, recientes).
  - Abrir, consumir y tirar, total o parcialmente, con conversión de unidades.
  - Cada consumo o descarte queda registrado (`food_outcomes`) con su valor estimado proporcional y, si se
    tira, el motivo. Es la base de las estadísticas de la Fase 8.
  - Alimentos añadidos recientemente, para repetirlos con un clic.
  - `expirationSource` (`USER` / `ESTIMATED`) presente en modelo, API y UI; una restricción de la base de
    datos impide una fecha sin origen.
- Módulo `analytics`: registra los eventos de producto `user_registered`, `food_added`, `food_consumed` y
  `food_discarded` después de confirmarse la transacción que los origina.
- Los parámetros de consulta inválidos responden `VALIDATION_ERROR`, igual que un cuerpo inválido.

**Web**
- Pantalla de inventario como vista principal del hogar (`/households/:id`); miembros y ajustes pasan a
  `/households/:id/settings`.
- Lista con cantidad, ubicación y caducidad; las fechas estimadas se muestran etiquetadas como tales.
- Filtros por ubicación, categoría, estado y texto; paginación.
- Alta y edición con autocompletado del catálogo, recientes y coma decimal.
- Consumir / tirar (cantidad parcial en unidades compatibles, motivo), abrir y eliminar con confirmación.

### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 74 tests: inventario (19), catálogo (6), cantidades (8), más los 41 de la Fase 1 |
| Comprobación del test de aislamiento | ✅ Al quitar a propósito la comprobación de hogar, el test falla (200 en lugar de 404) |
| Web lint / test / build | ✅ sin avisos / 48 tests / correcto |
| Mobile `flutter analyze` | ✅ sin avisos (solo cambió un test) |
| CI del pull request #1 | ✅ `backend`, `web`, `mobile` y `docker` |
| Extremo a extremo en navegador contra el backend real | ✅ alta con autocompletado (500 g de pechuga de pollo) y consumo parcial (quedan 300 g) |
| Migración `V2` sobre una base con datos de `V1` | ✅ aplicada al arrancar sobre la base local existente |

### Pendiente en esta fase

- **Móvil**: pantallas de inventario en Flutter (hoy la app móvil solo tiene sesión y hogares).
- **Tiempo real (SSE)**: hoy un miembro ve los cambios de otro al recargar o volver a la pantalla.
- **Eventos de producto** `food_scanned`, `receipt_scanned` y el resto llegan con sus funcionalidades.

### Decisiones tomadas en esta fase

| Decisión | Motivo |
|---|---|
| `FoodCategory` es un enum fijo, no una tabla | No hay categorías definidas por el usuario en el MVP; los clientes traducen el código |
| `FoodConsumption` y `FoodWaste` son una sola tabla `food_outcomes` con `type` | Tienen las mismas columnas y las estadísticas las consultan juntas |
| La API de inventario cuelga del hogar: `/households/{id}/inventory` | El hogar es explícito en cada petición y pasa siempre por `HouseholdAccess` |
| El catálogo se busca en memoria | Es pequeño y solo cambia con un despliegue; evita depender de extensiones de PostgreSQL para ignorar acentos |
| El día "de hoy" se calcula en `Europe/Madrid` (`FREEZIFY_TIME_ZONE`) | Las fechas de compra y caducidad son días, no instantes; zona por hogar más adelante |
| Editar un alimento reemplaza todos sus campos (`PUT`) | Evita la ambigüedad entre "campo ausente" y "campo vacío" |

### Known issues

- **Ediciones simultáneas**: si dos miembros editan el mismo alimento uno tras otro, gana el último. Si dos
  peticiones se solapan de verdad sobre el mismo alimento, la segunda debería recibir 409
  `CONCURRENT_MODIFICATION` (columna `version`); ese caso **no tiene test** y la web lo muestra con el
  mensaje de error genérico.
- **El estado `EXPIRED` aún no se asigna**: lo hará el motor de caducidad (Fase 3).
- **El catálogo no tiene alias ni sinónimos** ("jitomate", abreviaturas de ticket); previsto para la Fase 7.
- **Los eventos de producto no se borran con la cuenta** (no hay borrado de cuenta todavía).

## Fase 1 — Foundation ✅

Criterio de salida cumplido y probado: un usuario se registra, crea un hogar, invita a otro y ambos ven el
mismo hogar; un tercero no puede verlo.

### CI

| Ejecución | Resultado |
|---|---|
| Primera, sobre `main` (`8094093`) | ❌ `backend` (`mvnw` sin permiso de ejecución) y `mobile` (8 de 24 tests) |
| Pull request #1 (`568a593`) | ✅ `backend`, `web`, `mobile` y `docker` |

Con la segunda ejecución quedan confirmados los dos arreglos y, por primera vez, ejecutados en CI:
los tests del backend en Linux, los 24 tests de Flutter y la construcción de las dos imágenes Docker.
`main` seguirá en rojo hasta que se fusione el pull request.

### Sin verificar

| Qué | Motivo | Qué hace falta |
|---|---|---|
| `docker compose up` (los contenedores en marcha, no solo su construcción) | Docker no está instalado aquí; la CI solo construye las imágenes | Ejecutarlo en una máquina con Docker |
| App en un dispositivo o emulador Android | No hay ningún AVD configurado | `flutter run` en un emulador |
| iOS | Requiere macOS | Compilar en un Mac |

### Known issues

- **Refresh token de la web en `localStorage`** (riesgo R8): expuesto a XSS. Migrar a cookie `HttpOnly` antes de abrir al público.
- **Rate limiting por IP y cabeceras `X-Forwarded-*`**: el backend confía en esas cabeceras, así que debe ser accesible solo a través del proxy. El contador vive en memoria (una sola instancia).
- **El registro revela si un correo ya existe** (409). Mitigado por el rate limiting.
- **No hay verificación de correo, recuperación de contraseña ni eliminación de cuenta.**
- **El propietario no puede abandonar ni transferir un hogar**; solo eliminarlo.
- **Las invitaciones son reutilizables hasta que caducan (7 días)** y no se pueden revocar.
- **Sin tests E2E automatizados**; las pruebas de extremo a extremo han sido manuales.
- **Swagger UI y `/v3/api-docs` son públicos**; desactivarlos o protegerlos en producción.
- **Detener el backend local**: si el proceso se mata en vez de cerrarse con Ctrl+C, PostgreSQL embebido puede quedar vivo en el puerto 54329.

## Next

1. Fusionar el pull request #1.
2. Inventario en la app móvil.
3. SSE para que los cambios de inventario lleguen a los demás miembros sin recargar.
4. Fase 3 — motor de caducidad.
