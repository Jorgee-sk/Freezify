# Estado del proyecto

Última actualización: 2026-10-02

| Fase | Estado |
|---|---|
| 0 — Product Definition | ✅ Completada |
| 1 — Foundation | ✅ Completada (CI en verde en el pull request #1) |
| 2 — Inventory | ✅ Completada (CI en verde en el pull request #3) |
| 3 — Expiration Engine | 🟡 En curso: prioridad, "consume primero", estimación de fechas y scheduler hechos; faltan las notificaciones |

## Fase 3 — Expiration Engine 🟡

### Completed

- **Niveles de prioridad** como función pura de dominio (`ExpirationPriority`): caducado, vence hoy, urgente
  (1–2 días), consumir pronto (3–5), próximo (6–10) y OK (más de 10). Es el orden que usarán el recomendador
  de recetas y el planificador.
- Cada alimento del inventario devuelve `priority` y `daysUntilExpiration`, calculados contra el día de hoy.
- `GET /households/{id}/inventory/consume-first`: cuántos alimentos hay en cada nivel y cuáles hay que comer
  primero (caducados o con 5 días o menos), los más urgentes delante.
- **Web y móvil**: panel "Consume primero" al principio del inventario y etiqueta de prioridad con color en
  cada alimento. No hay etiqueta cuando queda mucho tiempo o no hay fecha, para que siga significando algo.
  El panel se actualiza con cada cambio, también con los que llegan en tiempo real.

- **Estimación de fechas por reglas**:
  - 57 reglas de vida útil (20 por categoría y 37 de alimentos concretos) según dónde se guarda el alimento:
    días desde la compra y, cuando abrirlo cambia las cosas, días desde que se abre.
  - La regla del alimento gana a la de su categoría. Si no hay regla, **no se inventa fecha**: el alimento
    queda sin fecha.
  - Un alimento sin fecha del usuario recibe una estimada. Abrir un alimento acorta su fecha si la regla lo
    dice (leche abierta: 3 días), aunque el envase marque una fecha posterior; abrirlo nunca la alarga.
  - La fecha que dio el usuario **se conserva siempre** (`userExpirationDate`) aparte de la que se aplica
    (`expirationDate` + `expirationSource`); el formulario edita la del usuario y muestra la estimada como
    indicación. Una estimación nunca vuelve al servidor como si la hubiera escrito el usuario.
  - La estimación se recalcula al crear, editar (cambio de ubicación, de categoría…) y abrir.

- **Scheduler de caducidad**:
  - Un barrido diario, poco después de medianoche (`Europe/Madrid`), marca como caducado (`EXPIRED`) todo
    alimento que sigue en casa y ha pasado de fecha. También se ejecuta al arrancar, por si la aplicación
    estaba parada a medianoche. Ejecutarlo dos veces no cambia nada.
  - Los miembros con el inventario abierto reciben el aviso en tiempo real.
  - El estado también se ajusta en cada escritura: un alimento añadido ya caducado nace como caducado, y
    corregir la fecha de uno caducado lo devuelve a disponible o abierto.
  - Un alimento caducado sigue en casa: se puede consumir, tirar o editar. Web y móvil lo muestran con una
    sola etiqueta "Caducado".

### Tests

| Qué | Resultado |
|---|---|
| Barrido contra la base local real | ✅ al arrancar marcó como caducado el único alimento pasado de fecha y no tocó los demás |
| Backend `./mvnw verify` | ✅ 122 tests (5 del barrido, 7 de la función de estimación, 8 de estimación por la API, 14 de niveles y 5 de "consume primero") |
| Web lint / test / build | ✅ sin avisos / 68 tests / correcto |
| Mobile `flutter analyze` y APK | ✅ sin avisos / generado |
| Mobile `flutter test` | 🟡 67 pasaron en CI (pull request #5); **1 nuevo sin ejecutar** (68 en total): correrá en la CI del próximo pull request |
| Migración `V3` sobre la base local con datos | ✅ aplicada; las fechas existentes pasan a ser "del usuario" sin cambios |
| Web contra el backend real | ✅ un pollo sin fecha aparece con "Caduca hacia el… (fecha estimada)" y su prioridad |
| Web y móvil contra el backend real | ✅ con alimentos caducados, que vencen hoy, urgentes y próximos: el panel y las etiquetas muestran el nivel y los días correctos |

### Pendiente en esta fase

- **Notificaciones** in-app y push, con preferencias y sin spam.

### Known issues

- **Los días de las reglas no salen de una fuente oficial**: son valores conservadores de conocimiento
  general, escritos a mano. Hay que contrastarlos con una fuente autorizada (AESAN, FoodKeeper) antes de
  abrir a usuarios reales; es el riesgo R3 de `ARCHITECTURE.md`.
- **Los alimentos sin fecha añadidos antes de esta versión no reciben estimación** hasta que se editan o se
  abren.
- **No se puede pedir "sin fecha"** para un alimento que tiene regla: si el usuario no da fecha, se estima.
- **El formulario no muestra la estimación antes de guardar**; se ve después, en la lista.
- **El barrido es global y de una sola instancia**: usa una única zona horaria para todos los hogares y no
  tiene bloqueo entre instancias. Con varias instancias se ejecutaría en todas; es inofensivo, pero cada una
  enviaría su aviso en tiempo real.
- **Si el barrido falla, solo queda constancia en el log**; no hay alerta ni reintento hasta el día siguiente
  o el próximo arranque.
- **"Hoy" es el día en `Europe/Madrid`** para todos los hogares.

## Fase 2 — Inventory ✅

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
| Mobile `flutter test` | ✅ en CI (pull request #3): 59 tests (27 de inventario, 17 de la aplicación, 8 del cliente HTTP y 7 de tiempo real). No pueden ejecutarse en la máquina de desarrollo. En la primera ejecución falló uno por un error del propio test (buscaba el botón de volver por su texto en inglés); corregido |
| Mobile `flutter analyze` y `flutter build apk --debug` | ✅ sin avisos / APK generado |
| Código de la app móvil contra el backend real (compilado para web en una copia temporal) | ✅ lista compartida con la web, alta con sugerencia del catálogo (1 l de leche), consumo parcial (de 300 g a 200 g) y acceso a miembros y ajustes |
| Backend `./mvnw verify` | ✅ 83 tests: tiempo real (9), inventario (19), catálogo (6), cantidades (8), más los 41 de la Fase 1 |
| Comprobación del test de aislamiento | ✅ Al quitar a propósito la comprobación de hogar, el test falla (200 en lugar de 404) |
| Web lint / test / build | ✅ sin avisos / 59 tests / correcto |
| CI de los pull requests #1, #2 y #3 | ✅ `backend`, `web`, `mobile` y `docker` |
| Extremo a extremo en navegador contra el backend real | ✅ alta con autocompletado (500 g de pechuga de pollo) y consumo parcial (quedan 300 g) |
| Migración `V2` sobre una base con datos de `V1` | ✅ aplicada al arrancar sobre la base local existente |

### Fuera de esta fase

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

- **Orden de "añadidos recientemente"**: un test falló una vez porque dos alimentos creados en el mismo
  milisegundo podían salir en cualquier orden. Ahora el desempate es por id; no ha vuelto a fallar en tres
  ejecuciones completas, pero al ser intermitente no hay prueba definitiva.
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

1. Abrir el pull request del scheduler y confirmar la CI.
2. Notificaciones in-app y push, con preferencias y sin spam: es lo último de la Fase 3.
