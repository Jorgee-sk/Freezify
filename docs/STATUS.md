# Estado del proyecto

Última actualización: 2026-10-02

| Fase | Estado |
|---|---|
| 0 — Product Definition | ✅ Completada |
| 1 — Foundation | ✅ Completada (CI en verde en el pull request #1) |
| 2 — Inventory | ✅ Completada (CI en verde en el pull request #3) |
| 3 — Expiration Engine | 🟡 Código completo; **falta comprobar en un móvil Android que el push llega**, y hasta entonces no se da por cerrada |

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

- **Avisos dentro de la app**:
  - Cada miembro recibe, una vez al día y no antes de la hora que elija, un aviso por hogar con los alimentos
    que están a punto de caducar o ya han caducado: nombra el alimento si es uno solo y los cuenta si son
    varios (lista los 5 más urgentes).
  - **Sin spam**: como mucho un aviso por hogar y día, y solo cuando hay algo nuevo que contar, es decir, un
    alimento del que aún no se ha avisado o que se ha vuelto más urgente (quedan 2 días → vence hoy →
    caducado). Un alimento que sigue caducado en la nevera no vuelve a provocar un aviso por sí solo.
  - Preferencias por usuario: activar o desactivar, hora, frecuencia máxima (diaria, cada 3 días, semanal),
    antelación (vence hoy, 2 días o menos, 5 días o menos) y categorías de las que no quiere avisos. Con una
    frecuencia menor las novedades no se pierden: llegan juntas cuando toca.
  - Si la aplicación estaba parada a la hora elegida, el aviso sale en la siguiente comprobación (cada hora
    y al arrancar).
  - Una fecha estimada **siempre** se redacta como estimación ("caduca en aproximadamente 1 día (fecha
    estimada)"). El servidor envía datos, no frases: web y móvil las redactan en el idioma del usuario.
  - Cada usuario solo ve y marca sus propios avisos; al salir de un hogar, o al eliminarlo, sus avisos
    desaparecen.
  - **Web y móvil**: campana con el número de avisos sin leer, lista de avisos (abrir uno lleva al inventario
    de ese hogar y lo marca como leído), "marcar todo como leído" y pantalla de preferencias.
  - Abrir un aviso por primera vez registra el evento de producto `notification_opened`.

- **Envío push desde el servidor** (Firebase Cloud Messaging):
  - Proyecto de Firebase `freezify-c50ea` creado, con la app Android `com.freezify.app` registrada. Google
    Analytics y Gemini quedaron desactivados.
  - La app Android pasa a identificarse como `com.freezify.app` (antes `com.freezify.freezify`).
  - `PUT /notifications/devices` registra dónde recibe avisos un usuario y
    `POST /notifications/devices/unregister` lo retira al cerrar sesión. Un teléfono pertenece a quien inició
    sesión en él por última vez; solo su dueño puede retirarlo.
  - Cada aviso nuevo se envía, una vez confirmado en base de datos, a todos los dispositivos de su usuario,
    redactado en su idioma y con las fechas estimadas dichas como estimaciones. Un envío fallido no afecta al
    aviso, que sigue en la app; un dispositivo que Firebase da por desaparecido se olvida.
  - Sin la clave configurada (`FREEZIFY_FCM_CREDENTIALS_FILE`) no se envía nada y todo lo demás funciona.
    Una clave configurada pero ilegible impide arrancar.

- **Recepción de push en la app móvil** (Android):
  - Al iniciar sesión la app pide permiso de notificaciones, obtiene su identificador en Firebase y registra
    el teléfono en el backend; vuelve a registrarlo si Firebase cambia el identificador.
  - Tocar una notificación abre la pantalla de avisos. Si llega con la app en pantalla, se actualiza la
    campana (Android no muestra la notificación en ese caso).
  - Al cerrar sesión la app retira el teléfono del backend y además invalida su identificador. Si la sesión
    caduca sola, lo invalida igualmente: un teléfono no sigue recibiendo los avisos de quien lo usó antes.
  - Si el teléfono no puede recibir push (sin permiso, sin configuración de Firebase, otra plataforma), la
    app funciona igual, sin ellos.
  - El proyecto Android aplica la configuración de Firebase solo si `google-services.json` está presente; sin
    el fichero la app compila y funciona sin push.

### Tests

| Qué | Resultado |
|---|---|
| APK debug con Firebase Messaging y la configuración real | ✅ generado; la configuración de Firebase llega a la compilación |
| APK debug **sin** `google-services.json` | ✅ generado |
| Código de la app móvil contra el backend real (compilado para web, donde no hay push) | ✅ arranca con sesión, cierra sesión e inicia sesión de nuevo con el código nuevo |
| **Push en un dispositivo Android** | ❌ **sin probar**: no hay dispositivo ni emulador. No se ha comprobado el permiso, el registro del teléfono, la llegada de la notificación ni la apertura al tocarla |
| Envío push contra Firebase real (`FcmLiveTests`, solo se ejecuta con la clave configurada) | ✅ Google emite el token de acceso y Firebase acepta la petición para el proyecto; solo rechaza el dispositivo inventado (`400 INVALID_ARGUMENT`). No se ha entregado ningún mensaje |
| Backend arrancado con la clave real | ✅ carga la clave ("sent through Firebase project freezify-c50ea") y aplica la migración `V5` sobre la base local |
| APK debug con el identificador `com.freezify.app` | ✅ generado |
| CI del pull request #7 (avisos dentro de la app) | ✅ los cuatro jobs; los 17 tests nuevos de Flutter pasan (85 en total) |
| Avisos, web contra el backend real | ✅ con 5 alimentos en casa, al arrancar se creó un aviso con los 4 que tocaba (el de fecha estimada, redactado como tal); la campana marcó 1 sin leer, abrirlo llevó al inventario y lo marcó como leído, y las preferencias se guardaron |
| Avisos, código de la app móvil contra el backend real (compilado para web en una copia temporal) | ✅ lo mismo: campana con 1 sin leer, lista, preferencias guardadas (hora y una categoría) y apertura del aviso hacia el inventario |
| Comprobación del test de aislamiento de avisos | ✅ al quitar a propósito la comprobación de propietario, el test falla (204 en lugar de 404) |
| Migración `V4` sobre la base local con datos | ✅ aplicada al arrancar |
| Barrido contra la base local real | ✅ al arrancar marcó como caducado el único alimento pasado de fecha y no tocó los demás |
| Backend `./mvnw verify` | ✅ 165 tests (23 de push: 9 de registro y despacho, 9 del cliente de Firebase contra un servidor local, 5 de redacción; 20 de avisos, 5 del barrido, 7 de la función de estimación, 8 de estimación por la API, 14 de niveles y 5 de "consume primero") |
| Web lint / test / build | ✅ sin avisos / 85 tests (17 de avisos) / correcto |
| Mobile `flutter analyze` y APK | ✅ sin avisos / generado |
| Mobile `flutter test` | ❌ primera ejecución en CI (pull request #9): 91 pasan y **5 fallan**. Causa encontrada para al menos 4: cerrar sesión lanzaba un error de dependencia circular entre el controlador de sesión y el de push, que solo salta en modo de depuración; corregido y comprobado en una compilación de depuración en el navegador. El quinto fallo está sin identificar: el log del job no es legible sin iniciar sesión en GitHub, así que la CI publica ahora el nombre y el error de cada test fallido. Pendiente de volver a ejecutar |
| Migración `V3` sobre la base local con datos | ✅ aplicada; las fechas existentes pasan a ser "del usuario" sin cambios |
| Web contra el backend real | ✅ un pollo sin fecha aparece con "Caduca hacia el… (fecha estimada)" y su prioridad |
| Web y móvil contra el backend real | ✅ con alimentos caducados, que vencen hoy, urgentes y próximos: el panel y las etiquetas muestran el nivel y los días correctos |

### Pendiente en esta fase

- **Comprobar el push de extremo a extremo en un móvil Android** (o un emulador con servicios de Google):
  instalar la app, iniciar sesión, aceptar el permiso y ver llegar una notificación. Es lo único que falta
  para cerrar la fase.
- **Push en iOS**: necesita un Mac y una cuenta de Apple Developer.
- **Avisos de recetas y de compras**: llegarán con sus fases (4 y 6); hoy no hay nada de lo que avisar.

### Known issues

- **Los días de las reglas no salen de una fuente oficial**: son valores conservadores de conocimiento
  general, escritos a mano. Hay que contrastarlos con una fuente autorizada (AESAN, FoodKeeper) antes de
  abrir a usuarios reales; es el riesgo R3 de `ARCHITECTURE.md`.
- **Los alimentos sin fecha añadidos antes de esta versión no reciben estimación** hasta que se editan o se
  abren.
- **No se puede pedir "sin fecha"** para un alimento que tiene regla: si el usuario no da fecha, se estima.
- **El formulario no muestra la estimación antes de guardar**; se ve después, en la lista.
- **Ningún push se ha entregado todavía a un dispositivo**: lo comprobado es que Firebase acepta las
  credenciales y el formato de la petición, y que la app compila con Firebase. El código que habla con
  Firebase en el teléfono (`FirebasePushMessaging`) no se ha ejecutado nunca.
- **El permiso de notificaciones se pide nada más iniciar sesión**, sin explicación previa.
- **La web no recibe push**: solo avisos dentro de la aplicación.
- **El icono de la notificación es el de la app por defecto**; no hay icono ni canal de notificación propios.
- **Al cerrar sesión la app hace dos peticiones que acaban en 401** (hogares y avisos sin leer se vuelven a
  pedir ya sin sesión). No tiene efecto visible; el de hogares ya ocurría antes.
- **La clave de la cuenta de servicio de Firebase da acceso de administrador a todo el proyecto de Firebase**,
  no solo a enviar mensajes. Está fuera del repositorio; antes de producción conviene una cuenta de servicio
  con el permiso mínimo.
- **El envío es síncrono**: los push de cada aviso se envían uno a uno dentro de la comprobación horaria. Con
  muchos usuarios habrá que sacarlo a una cola.
- **No hay reintentos**: un push que falla (red, Firebase caído) no se vuelve a intentar.
- **El identificador de la app iOS sigue siendo `com.freezify.freezify`**; solo se ha cambiado Android.
- **Con Docker la clave no llega al contenedor**: `docker-compose.yml` aún no la monta.
- **La hora de los avisos es la de `Europe/Madrid`** para todos los usuarios, y así se indica en la pantalla
  de preferencias.
- **La comprobación de avisos lee de una vez todos los alimentos próximos a caducar de todos los hogares**,
  cada hora. Es suficiente para el MVP; con muchos hogares habrá que hacerlo por lotes.
- **Con varias instancias del backend** dos comprobaciones simultáneas del mismo usuario chocarían en la base
  de datos: una se descarta y queda un error en el log, sin avisos duplicados. Sin probar.
- **La app móvil muestra solo los 50 avisos más recientes**; la web pagina.
- **La campana no se actualiza en tiempo real**: la web la refresca cada 5 minutos y al volver a la pestaña;
  el móvil, al volver a la app y al tirar para refrescar.
- **No hay limpieza de avisos antiguos**: se acumulan (uno por hogar y día como mucho).
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

1. Subir la corrección al pull request #9 y dejar en verde los tests del móvil.
2. Probar el push en un móvil Android; con eso se cierra la Fase 3.
3. Fase 4 — Recipes.
