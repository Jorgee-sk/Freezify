# Estado del proyecto

Última actualización: 2026-10-02

| Fase | Estado |
|---|---|
| 0 — Product Definition | ✅ Completada |
| 1 — Foundation | ✅ Completada (CI en verde en el pull request #1) |
| 2 — Inventory | 🟡 Implementada, incluido el tiempo real; falta que la CI ejecute los tests nuevos del móvil |

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

**Mobile**
- Pantalla de inventario como vista principal del hogar; miembros y ajustes pasan a un icono de la barra superior.
- Lista con cantidad, ubicación y caducidad (las fechas estimadas, etiquetadas), búsqueda, filtro por
  ubicación, categoría y estado, y paginación.
- Alta y edición a pantalla completa con sugerencias del catálogo, recientes, coma decimal y selector de fecha.
- Menú por alimento: consumir, tirar (con motivo), marcar como abierto, editar y eliminar con confirmación.

**Tiempo real**
- `GET /households/{id}/events`: flujo de eventos (SSE) solo para miembros. Cada cambio del inventario,
  una vez confirmado en base de datos, envía `inventory-changed` a todos los miembros conectados.
- Los eventos no llevan datos: el cliente vuelve a pedir el inventario por la API normal, con su autorización.
- Quien sale del hogar, o es expulsado, deja de recibir eventos en el acto; al eliminar el hogar se cierran
  todas las conexiones. Máximo de 5 conexiones por miembro y hogar; latido cada 25 s; cada conexión dura
  como mucho 30 minutos y el cliente vuelve a autenticarse al reconectar.
- Web y móvil escuchan mientras el inventario está en pantalla, reconectan con espera creciente, detectan
  una conexión muerta por silencio y, tras reconectar, vuelven a pedir el inventario por si se perdió algo.

### Tests

| Qué | Resultado |
|---|---|
| Tiempo real, cliente Dart real de la app contra el backend real (en la VM de Dart, con el mismo adaptador de red que Android) | ✅ recibe los cambios (26 ms) y la conexión sobrevive a 32 s de silencio |
| Tiempo real, web en navegador contra el backend real | ✅ un alimento añadido desde fuera del navegador aparece sin recargar |
| Mobile `flutter test` | 🟡 50 tests pasaron en CI (pull request #2). Los **9 nuevos de tiempo real no se han ejecutado todavía**: no pueden correr en la máquina de desarrollo y lo harán en la CI del próximo pull request |
| Mobile `flutter analyze` y `flutter build apk --debug` | ✅ sin avisos / APK generado |
| Código de la app móvil contra el backend real (compilado para web en una copia temporal) | ✅ lista compartida con la web, alta con sugerencia del catálogo (1 l de leche), consumo parcial (de 300 g a 200 g) y acceso a miembros y ajustes |
| Backend `./mvnw verify` | ✅ 83 tests: tiempo real (9), inventario (19), catálogo (6), cantidades (8), más los 41 de la Fase 1 |
| Comprobación del test de aislamiento | ✅ Al quitar a propósito la comprobación de hogar, el test falla (200 en lugar de 404) |
| Web lint / test / build | ✅ sin avisos / 59 tests / correcto |
| CI de los pull requests #1 y #2 | ✅ `backend`, `web`, `mobile` y `docker` |
| Extremo a extremo en navegador contra el backend real | ✅ alta con autocompletado (500 g de pechuga de pollo) y consumo parcial (quedan 300 g) |
| Migración `V2` sobre una base con datos de `V1` | ✅ aplicada al arrancar sobre la base local existente |

### Pendiente en esta fase

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

- **Tiempo real a través de nginx sin probar**: se ha probado contra el backend directamente y a través del
  proxy de desarrollo de Vite. El backend envía `X-Accel-Buffering: no` para que nginx no retenga los
  eventos, pero esa ruta no se ha ejecutado (Docker no está instalado aquí).
- **Tiempo real en un móvil de verdad sin probar**: el cliente se ha ejecutado en la VM de Dart, no en un
  dispositivo; no se ha comprobado qué ocurre al pasar la app a segundo plano o cambiar de red.
- **Conexiones en memoria**: con más de una instancia del backend, un cambio solo llegaría a los miembros
  conectados a la misma instancia.
- **Un token caducado sigue escuchando** hasta que la conexión termina (30 minutos como máximo); los eventos
  no contienen datos.
- **Selector de fecha del móvil sin probar**: ni los tests ni la prueba manual abren el calendario; solo se
  ha comprobado que el formulario guarda sin fecha y que conserva la fecha existente al editar.
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
Tras fusionarlo, `main` también pasa.

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

1. Abrir el pull request del tiempo real y confirmar en CI los tests nuevos del móvil; con eso se cierra la Fase 2.
2. Fase 3 — motor de caducidad.
