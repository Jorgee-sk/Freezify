# Estado del proyecto

Última actualización: 2026-10-02

| Fase | Estado |
|---|---|
| 0 — Product Definition | ✅ Completada |
| 1 — Foundation | ✅ Completada (CI en verde en el pull request #1) |
| 2 — Inventory | ✅ Completada (CI en verde en el pull request #3) |
| 3 — Expiration Engine | 🟡 Código completo; **falta comprobar en un móvil Android que el push llega**, y hasta entonces no se da por cerrada |
| 4 — Recipes | 🟡 Funcionalidad completa (CI en verde en el pull request #12). Antes de usuarios reales hay que revisar los datos de alérgenos |
| 5 — Smart Planning | 🟡 En curso: el backend del plan semanal y su generador están hechos; faltan las pantallas de web y móvil |

## Fase 5 — Smart Planning 🟡

### Completed

- **Plan semanal del hogar** (solo backend por ahora): una receta por día y comida (comida y cena), de lunes
  a domingo.
  - `GET /households/{id}/meal-plan?week=` devuelve la semana que contiene esa fecha (la actual si no se
    indica).
  - `PUT /households/{id}/meal-plan/{fecha}/{LUNCH|DINNER}` elige o sustituye la receta de una comida;
    `DELETE` la quita; `POST …/move` la mueve a otro día o comida y, si allí ya hay algo, las intercambia.
  - Cualquier miembro ve y cambia el plan; nadie de fuera del hogar puede verlo ni tocarlo.
- **Lo que habrá en casa ese día**: cada comida, de hoy en adelante, dice por ingrediente si lo habrá
  (suficiente, parte, cantidad no comparable) o si falta, contando con que las comidas planificadas antes ya
  se han llevado lo suyo y con que un alimento deja de contar el día después de su fecha. Si la fecha de un
  alimento es estimada, se indica como estimada. Es una simulación: **el inventario no se toca**.
- **Lo que el plan deja caducar**: la semana incluye la lista de alimentos de casa que caducan antes de que
  acabe y que las comidas planificadas hasta su fecha no usan o no terminan, con la cantidad que sobraría.
- **Generación automática** (`POST /households/{id}/meal-plan/generate`): rellena las comidas vacías de la
  semana, de hoy en adelante.
  - Determinista: con la misma casa, los mismos alimentos y el mismo día sale el mismo plan.
  - Las comidas se rellenan en el orden en que se van a comer. Para cada una se puntúan las recetas contra lo
    que quedaría en casa ese día y se elige la mejor; sus ingredientes salen de la despensa simulada antes de
    mirar la comida siguiente. Así se usa primero lo que caduca primero y nunca se cuenta con un alimento
    pasado de fecha.
  - Factores, cada uno entre 0 y 1, con pesos configurables (`freezify.meal-planning.weights`): aprovechar
    lo que va a caducar (0,35), comprar poco que no se vaya a comprar ya para otra comida del plan (0,25),
    variedad (0,20), novedad (0,10) y poco esfuerzo (0,10).
  - **Variedad como regla**: no se repite receta mientras queden otras; no se pone el mismo tipo de plato dos
    veces el mismo día, ni en días seguidos salvo que así se aproveche un alimento al que le quedan dos días
    o menos. Dos platos son del mismo tipo si comparten su ingrediente base o su carne, pescado o huevo.
  - Solo propone platos principales y **nunca** nada que el hogar no coma (las restricciones de la Fase 4).
  - **Lo que eligió una persona no se toca**. Lo generado antes solo se sustituye si se pide expresamente.
  - Si no quedan recetas sin repetir demasiado (dietas muy restrictivas), deja comidas vacías y dice cuántas.
- Cada cambio del plan avisa en tiempo real a los miembros conectados (`meal-plan-changed`) y la primera
  comida planificada de una semana registra el evento de producto `meal_plan_created`.
- Para que el planificador pueda usarlas, las recetas y el cálculo de disponibilidad pasan a ser la interfaz
  pública del módulo de recetas (`Recipe`, `RecipeScorer`, `RecipeCatalog`); su comportamiento no cambia.

### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 259 tests (50 nuevos: 8 de la despensa simulada, 17 del generador y 25 de la API del plan); 1 omitido, el que habla con Firebase real |
| Comprobación del test de aislamiento del plan | ✅ al quitar a propósito la comprobación de pertenencia al hogar, el test falla (204 en lugar de 404) |
| Límites entre módulos (`ModularityTests`) | ✅ el módulo nuevo solo usa las interfaces públicas de recetas, inventario, alimentos y hogares |
| Migración `V8` sobre la base local con datos | ✅ aplicada al arrancar |
| Generador contra el backend y la base locales reales | ✅ con pollo (600 g, 2 días), tomates (3 días), lechuga (4), calabacín (5), arroz, huevos, champiñones sin fecha y yogur (1 día): planificó las seis comidas que quedaban de la semana, usó todo el pollo antes de su fecha en dos platos distintos, marcó la fecha de los champiñones como estimada y señaló el yogur como lo único que el plan deja caducar. Para la semana siguiente rellenó las 14 comidas con 14 platos distintos |
| Web y móvil | ⏳ sin pantallas todavía |

### Pendiente en esta fase

- **Pantallas del plan semanal en web y móvil**: ver la semana, elegir, mover, sustituir y quitar comidas,
  generar el plan y ver qué falta y qué se deja caducar.
- **Preferencias del usuario** como factor: no existen todavía (viene de la Fase 4).

### Known issues

- **El generador es voraz**: elige comida a comida, sin volver atrás. Da un buen plan, no el mejor posible, y
  puede dejar sin usar algo que otro orden habría aprovechado; en ese caso lo señala.
- **El coste no se calcula con precios**: no los hay para las recetas. "Comprar poco" hace de coste y de
  reutilización de ingredientes a la vez.
- **Cantidades que no se pueden comparar** (la receta pide "2 tomates" y hay "500 g"): se cuenta con que el
  alimento está, pero no se descuenta ni se afirma cuánto queda; es el riesgo R4.
- **Las raciones no se ajustan** al número de personas del hogar.
- **"Mismo tipo de plato" es una regla sencilla** (ingrediente base o carne, pescado o huevo en común); el
  ingrediente base es el primero de la receta.
- **Solo comida y cena**: no se planifican desayunos ni postres, aunque se pueden poner a mano.
- **Marcar una comida del plan como cocinada no existe**: "La he cocinado" sigue estando en la receta y el
  plan no lo refleja.
- **No hay "vaciar la semana"** ni copia de una semana a otra.
- **Dos miembros que eligen a la vez la misma comida vacía**: la segunda petición falla con un error genérico
  en lugar de un mensaje claro. Sin test.
- **Los alimentos fuera del catálogo** no encajan en ninguna receta: aparecen siempre como "el plan no los
  usa" si caducan en la semana.
- **El día es el de `Europe/Madrid`** para todos los hogares, como en el resto de la aplicación.

## Fase 4 — Recipes 🟡

### Completed

- **Catálogo de recetas**: 25 recetas propias de cocina casera, en español e inglés, con raciones, tiempos,
  dificultad, tipo (desayuno, principal, postre) y pasos. Cada ingrediente es un alimento del catálogo con su
  cantidad y unidad; nada depende de texto libre.
  - `GET /recipes` lista y filtra (texto sin acentos, tiempo máximo, dificultad, tipo), paginado.
  - `GET /recipes/{id}` devuelve la receta con ingredientes y pasos.
- **Recomendador determinista** (`GET /households/{id}/recipes/recommendations`): puntúa cada receta contra
  lo que hay hoy en el hogar y devuelve las mejores primero.
  - Factores, cada uno entre 0 y 1: ingredientes que ya hay en casa, urgencia de caducidad de los que usa,
    comodidad (tiempo y dificultad) y novedad (cuánto hace que se cocinó). Pesos configurables
    (`freezify.recipes.weights`); solo cuentan sus proporciones.
  - Solo se recomiendan recetas que usan al menos un alimento que hay en casa.
  - **Un alimento pasado de fecha nunca se cuenta como disponible** ni se propone para cocinar.
  - Lo básico de cocina (sal, aceite, agua, ajo) aparece en la receta pero no se busca en el inventario ni
    cuenta como "falta".
  - Las cantidades se comparan en la misma magnitud (gramos con kilos). Si no se pueden comparar (la receta
    pide "2 tomates" y hay "500 g"), se dice que hay, sin afirmar que sea suficiente.
  - Un alimento escrito a mano se reconoce si su nombre coincide con uno del catálogo.
- **Explicación con datos reales**: cada recomendación devuelve sus factores y, por ingrediente, si hay
  suficiente, parte, cantidad no comparable o falta, con la fecha de caducidad más cercana de lo que hay en
  casa y si esa fecha es estimada. El servidor no escribe frases: las redactarán los clientes.
- **"La he cocinado"** (`POST /households/{id}/recipes/{recipeId}/cooked`): se anota una vez por hogar,
  receta y día, y baja esa receta en las recomendaciones durante dos semanas. No toca el inventario.
- Eventos de producto `recipe_viewed` y `recipe_cooked`.
- **Pantallas de recetas en web y móvil**, con acceso desde el inventario del hogar:
  - "Qué cocinar con lo que tienes": las cinco mejores recomendaciones, cada una con su encaje y sus motivos
    en frases ("Tienes calabacín con caducidad en 2 días", "Solo te falta: mozzarella", "Tiempo aproximado:
    25 min"). Las frases se construyen en el cliente con los datos de la recomendación; una fecha estimada
    se dice siempre como estimada.
  - Catálogo con búsqueda y filtros por tiempo y tipo.
  - Detalle de receta: ingredientes con cantidad y, para este hogar, si lo tienes, tienes menos, hay que
    comprobar la cantidad o falta, más cuándo caduca lo que está a punto; pasos; y "La he cocinado".

- **Restricciones alimentarias del hogar**, como filtro duro:
  - Cada alimento del catálogo indica lo que contiene entre 11 rasgos (carne, cerdo, pescado, marisco,
    lácteos, huevo, gluten, frutos secos, soja, sésamo, alcohol). Una receta contiene lo que contengan sus
    ingredientes, incluidos los básicos.
  - El hogar elige una dieta (ninguna, vegetariana, vegana) y, además, qué evitar. Una receta que contenga
    algo de eso **no aparece** ni en las recomendaciones ni en el catálogo del hogar; el filtro se aplica en
    el servidor, antes de puntuar.
  - Las restricciones son **del hogar, no de una persona**: cualquier miembro las ve y las cambia. Así no
    hay datos de salud personales que otros miembros descubran de rebote.
  - Web y móvil: pantalla "Qué no se come en casa", línea que dice con qué se han filtrado las recetas,
    "Contiene: …" en cada receta y un aviso si se abre una receta con algo que el hogar no come.
  - La pantalla avisa de que es una ayuda y no una garantía, y de que hay que comprobar la etiqueta.

### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 209 tests (10 de restricciones, 16 del cálculo de puntuación y 17 de la API de recetas); 1 omitido, el que habla con Firebase real |
| Web lint / test / build | ✅ sin avisos / 116 tests (11 de restricciones y 20 de recetas) / correcto |
| Restricciones, web contra el backend real | ✅ al guardar "vegetariana, sin huevo" las recomendaciones pasaron de cinco a una (crema de calabacín) y el catálogo de 25 a 6 recetas |
| Restricciones, código de la app móvil contra el backend real (compilado para web en modo de depuración) | ✅ mostró lo guardado desde la web, guardó "sin gluten" y, al volver, la lista ya no tenía recetas con pasta |
| Migración `V7` sobre la base local con datos | ✅ aplicada; comprueba ella misma que entran los 69 rasgos |
| Mobile `flutter analyze` y APK debug | ✅ sin avisos / generado |
| Mobile `flutter test` | ✅ en CI (pull request #12), con los 11 nuevos de restricciones |
| Recetas, web contra el backend real | ✅ recomendaciones con sus motivos, detalle con lo que hay de cada ingrediente y "La he cocinado", que bajó la receta del 71 % al 59 % y añadió "La has cocinado hoy" |
| Recetas, código de la app móvil contra el backend real (compilado para web en modo de depuración) | ✅ recomendaciones con sus motivos y detalle de receta. No se pulsó "La he cocinado" ni se probaron los filtros en esta versión |
| Recomendador contra el backend y la base locales reales | ✅ con calabacín (2 días), tomate, pasta, huevos, champiñones sin fecha y leche caducada: propone primero el revuelto de champiñones y la pasta con calabacín, marca la fecha de los champiñones como estimada y no usa la leche caducada |
| Migración `V6` sobre la base local con datos | ✅ aplicada; comprueba ella misma que entran las 25 recetas y sus 153 ingredientes |

### Pendiente en esta fase

- **Preferencias personales** (gustos, alimentos que no apetecen), que son el factor de la puntuación que
  falta. No bloquean nada: hoy la puntuación reparte los otros cuatro pesos.
- **Avisos de recetas** ("hay recetas que aprovechan lo que va a caducar").

### Known issues

- **La app móvil pide el catálogo entero de una vez** (hasta 100 recetas) en lugar de paginar; la web pagina.
- **La web no actualiza las recomendaciones en tiempo real** si otro miembro cambia el inventario mientras
  la página está abierta; se piden de nuevo cada vez que se entra.
- **Los avisos de caducidad se redactan mal con nombres en plural** ("Huevos caduca hoy"); en las recetas ya
  está resuelto con frases que no dependen del número.
- **Lo que contiene cada alimento está escrito a mano, no sale de etiquetas ni de una fuente oficial.** Es
  deliberadamente prudente (la pasta cuenta como "con huevo", el jamón cocido como "con lácteos, gluten y
  soja"), pero puede haber errores en los dos sentidos y no cubre trazas. Hay que revisarlo con una fuente
  autorizada antes de abrir a usuarios reales; es el mismo tipo de riesgo que las fechas estimadas (R3).
- **Por esa prudencia quedan pocas recetas con algunas restricciones**: 2 veganas, 12 vegetarianas y 15 sin
  gluten de las 25.
- **Solo 11 rasgos**: no hay apio, mostaza, sulfitos, altramuces, moluscos ni cacahuetes como categoría
  propia (los 14 alérgenos de declaración obligatoria en la UE no están todos).
- **Los alimentos escritos a mano no tienen rasgos**: no afectan al filtro porque las recetas solo usan
  alimentos del catálogo, pero el inventario no avisa de nada sobre ellos.
- **Las restricciones son por hogar**: no se puede decir "solo yo soy vegetariano".
- **No queda registro de quién cambió las restricciones** más allá del último cambio, ni se avisa a los
  demás miembros.
- **Las recetas no llevan información nutricional ni imagen**: no hay una fuente fiable de la que sacarlas y
  no se inventan.
- **Recetas escritas a mano y no probadas en cocina** una por una: cantidades y tiempos son orientativos.
- **"La he cocinado" no descuenta ingredientes del inventario**; hay que consumirlos a mano.
- **Las cantidades no se ajustan a las raciones** que quiera el usuario.
- **No se convierte entre unidades y peso** ("2 tomates" frente a "500 g"); es el riesgo R4.
- **El ajo cuenta como básico**: nunca aparece como ingrediente que falte.
- **Los pesos del recomendador son los de la especificación menos el de preferencias** (0,15), que entrará
  con las preferencias del usuario.

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
| Mobile `flutter test` | ✅ 96 en CI (pull request #9), a la tercera ejecución. Primera: 5 fallos de 96; cerrar sesión lanzaba un error de dependencia circular entre el controlador de sesión y el de push, que solo salta en modo de depuración (corregido y comprobado en una compilación de depuración en el navegador). Segunda: 3 fallos, los tres tests en los que la app descarta el identificador de push; el código esperaba a cancelar unas suscripciones y esa espera no termina bajo el reloj simulado de los tests (reproducido en la máquina de Dart y corregido; en un dispositivo no afectaba). Los tests sustituyen a Firebase por un doble: no prueban la integración real |
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

1. Fase 5: pantallas del plan semanal en web y móvil.
2. Fase 6 — Shopping: lista de la compra a partir de lo que falta en el plan.
3. Pendientes: probar el push en un móvil Android (Fase 3) y revisar los datos de alérgenos y de vida útil
   con una fuente autorizada antes de abrir a usuarios reales.
