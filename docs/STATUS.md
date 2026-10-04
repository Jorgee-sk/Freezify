# Estado del proyecto

Última actualización: 2026-10-03

| Fase | Estado |
|---|---|
| 0 — Product Definition | ✅ Completada |
| 1 — Foundation | ✅ Completada (CI en verde en el pull request #1) |
| 2 — Inventory | ✅ Completada (CI en verde en el pull request #3) |
| 3 — Expiration Engine | 🟡 Código completo; **falta comprobar en un móvil Android que el push llega**, y hasta entonces no se da por cerrada |
| 4 — Recipes | 🟡 Funcionalidad completa (CI en verde en el pull request #12). Antes de usuarios reales hay que revisar los datos de alérgenos |
| 5 — Smart Planning | 🟡 Funcionalidad completa (CI en verde en el pull request #14). Faltan las preferencias del usuario como factor |
| 6 — Shopping | ✅ Completada (CI en verde en los pull requests #16 y #17) |
| 7 — AI / OCR | 🟡 En curso: escaneo de tickets y receta con IA (CI en verde en los pull requests #19 y #20) y foto de un alimento (tests nuevos del móvil pendientes de CI). Faltan escanear tickets desde la web y el asistente |

## Fase 7 — AI / OCR 🟡

### Tercera unidad: foto de un alimento (2026-10-03)

#### Completed

- **"Identificar por foto"** en el formulario de alta del inventario, en la web (subir o hacer una foto) y en el
  móvil (cámara o galería). Solo aparece si el servidor tiene un modelo de lenguaje.
- **El modelo elige entre los alimentos del catálogo** (o "otro", con un nombre) y da **varios candidatos con su
  confianza** (regla P7): "Tomate — 81 %", "Pimiento rojo — 12 %", "Caqui — 7 %". Se le pide expresamente que,
  si la foto no es clara, dé varios con poca confianza en vez de uno con mucha; cuando el primero no llega al
  60 % la app lo dice: "No estamos seguros. ¿Es alguno de estos?".
- **Nada se rellena hasta que la persona elige**: un alimento del catálogo trae su categoría, unidad y
  ubicación habituales; "otro" solo pone el nombre; si no es ninguno, se escribe a mano.
- **La foto se trata con cuidado**:
  - el backend mira su contenido y solo acepta JPEG, PNG o WebP, diga lo que diga el fichero (415
    `UNSUPPORTED_IMAGE`); como mucho 5 MB (413 `FILE_TOO_LARGE`), y nginx deja pasar hasta 6 MB;
  - web y móvil la reducen antes de enviarla (lado mayor de 1280 px, JPEG): una foto de 3000×2000 viajó
    en unos 16 kB;
  - **no se guarda** en ningún sitio ni se escribe en los logs; va al modelo configurado y se olvida. La
    pantalla avisa de que se envía al servicio de IA.
- La respuesta se comprueba y se descarta entera si no sigue el esquema, propone algo que no está en el
  catálogo ni es "otro", o da una confianza fuera de 0–1. El mismo alimento dos veces cuenta una.
- Errores con su motivo (sin IA, límite del día, respuesta inservible) y evento de producto `food_scanned`.

#### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 367 tests (8 nuevos: validación de los candidatos, envío de la imagen al proveedor y API de la foto); 1 omitido, el que habla con Firebase real |
| Web lint / test / build | ✅ sin avisos / 178 tests (4 nuevos) / correcto |
| Mobile `flutter analyze` | ✅ sin avisos |
| Mobile `flutter test` | 🟡 los **4 nuevos no se han ejecutado** (181 en total): correrán en la CI del pull request |
| API contra el backend real y un modelo simulado | ✅ un JPEG de 20 kB devolvió "Tomate 81 % (verduras, nevera)", "Pimiento rojo 12 %" y "Caqui 7 % (no es del catálogo)"; un texto con extensión .jpg dio 415 y un JPEG de 7 MB, 413 (el límite de Spring, que los tests no pueden ejercitar); solo el primero llegó al modelo, como imagen en una data URL |
| Web contra el backend real | ✅ una foto de 3000×2000 se redujo antes de enviarla, salieron los tres candidatos con su porcentaje y elegir "Tomate" rellenó el alimento y la ubicación |
| App móvil con la cámara | ⏳ **no probado**: no hay móvil ni emulador; la versión web no se ha probado con una foto |
| Con **Gemini real** u **Ollama real** | ⏳ **no probado**: la calidad real de la identificación es desconocida |

#### Known issues

- **Calidad desconocida**: no se ha probado con un modelo real ni con fotos reales de comida. El porcentaje es
  lo que dice el modelo, no una probabilidad calibrada.
- **La foto sale del dispositivo** hacia el servicio de IA (con la capa gratuita de Gemini, Google puede
  usarla; riesgo R14). Lo avisa la pantalla.
- **Solo un alimento por foto**: una foto de la compra entera no da una lista.

### Segunda unidad: "Crea una receta con lo que tengo" (2026-10-03)

#### Completed

- **Receta escrita por un modelo de lenguaje con lo que hay en casa** (`POST
  /households/{id}/recipes/generated`, con 1 a 8 raciones). Solo existe si el servidor tiene un modelo
  configurado: `GET /ai` lo dice y web y móvil solo ofrecen el botón entonces.
- **Al modelo solo le llega lo necesario** (regla P5): los alimentos del inventario que no han pasado su fecha y
  que el hogar come, con su cantidad y los días que les quedan, más sal, aceite de oliva y agua como básicos;
  las raciones, el idioma y, como recordatorio, lo que el hogar no come.
  - Lo que choca con la dieta del hogar **no se envía**. Lo que no es del catálogo (contenido desconocido) solo
    se envía si el hogar no evita nada.
  - Varios paquetes del mismo alimento van como uno, con la fecha del que caduca antes. Como mucho 40
    alimentos, los que caducan antes primero.
- **La respuesta se comprueba entera y se descarta entera si falla**:
  - en el módulo de IA: el esquema (título, resumen, minutos, dificultad, pasos), que cada ingrediente sea de
    la lista, sin repetir, **sin usar más de lo que hay** (con conversión g/kg y ml/l) y que use al menos un
    alimento de casa que no sea opcional;
  - en el módulo de recetas: que el título, el resumen y los pasos **no nombren ningún alimento del catálogo
    que la receta no use** ("añade la nata" sin nata la invalida); "tomate" dentro de "tomate triturado" no
    cuenta.
- **El porqué sale del inventario, no del modelo**: cada ingrediente lleva los días que le quedan a lo que hay
  en casa, y web y móvil marcan los que caducan en 5 días o menos ("Caduca en unos 3 días (estimada)").
- **Aviso visible** en la receta: escrita por IA; revisar cantidades, tiempos y que carne, pescado y huevos
  queden bien hechos. No se guarda ni entra en el catálogo.
- Errores con su motivo: sin IA configurada (503 `AI_NOT_CONFIGURED`), límite del día gastado (429
  `AI_LIMIT_REACHED`), respuesta inservible (502 `AI_UNAVAILABLE`), nada en casa (409 `NOTHING_TO_COOK_WITH`).
- Evento de producto `recipe_generated`.
- **Elegir qué usar**: la pantalla enseña lo que la IA puede usar (lo mismo que se le envía, con cantidades y
  caducidades; `GET …/recipes/generated/ingredients`) y deja marcar hasta 3 alimentos del catálogo que la
  receta tiene que usar sí o sí. La respuesta que no los use, o los use como opcionales, se descarta. Pedir uno
  que no está en casa, ha caducado o no se come en el hogar da 409 `FOOD_NOT_AVAILABLE`.
- **Alimentos fuera del catálogo**: la comprobación de nombres conoce además una lista escrita a mano de
  alimentos que no están en el catálogo: los alérgenos de declaración obligatoria en la UE (frutos secos,
  sésamo, soja, moluscos, crustáceos, pescados, mostaza, apio, altramuz, gluten como pan rallado o cuscús) y
  carnes ("pollo", "cerdo", "ternera"...). Una receta que los nombra sin usarlos se descarta ("espolvorea
  almendras" sin almendras). Si la receta usa algo cuyo nombre los contiene ("Pechuga de pollo"), sí puede
  nombrarlos.
- **Problema conocido resuelto: el límite diario de llamadas se guarda en la base de datos** (`V11__ai_usage.sql`)
  en lugar de en memoria: reiniciar el backend ya no lo reinicia y varias instancias comparten la cuenta. Se
  cuenta con una sola sentencia, así que dos llamadas a la vez no se cuelan; solo se guarda la última semana.

#### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 359 tests (19 nuevos: validación de la receta del modelo, alimentos que tiene que usar, menciones de alimentos y alérgenos, límite diario en la base de datos, API de la receta y nombres escritos a mano en el inventario); 1 omitido, el que habla con Firebase real |
| Web lint / test / build | ✅ sin avisos / 174 tests (5 nuevos) / correcto |
| Mobile `flutter analyze` | ✅ sin avisos |
| Mobile `flutter test` | 🟡 en la primera CI del pull request falló uno de los 3 nuevos: la fila "Sal" quedaba fuera de la pantalla de test, y una lista que se construye al hacer scroll no la había creado. Corregido el test (hace scroll antes de comprobarla); los **4 nuevos no se han vuelto a ejecutar** (177 en total) |
| Web contra el backend real y un modelo simulado | ✅ un hogar con calabacín (1 día), pollo (3 días), arroz y jamón, que no come cerdo: al modelo le llegaron calabacín, pollo, arroz y los básicos, sin el jamón y con "pork" como aviso; la receta mostró las cantidades usadas, "Caduca en 1 día" y "Caduca en 3 días", y la sal como opcional y básico; una segunda respuesta que añadía "nata para cocinar" se rechazó con "La IA no ha dado una receta válida" |
| App móvil (versión web) contra el backend real | ✅ el botón aparece en Recetas con IA configurada; la receta, el aviso, los ingredientes con su caducidad y los pasos se ven bien |
| Lista de lo que la IA puede usar (web, backend real) | ✅ en un hogar que no come cerdo salieron calabacín ("Caduca en 1 día"), pechuga de pollo y arroz, sin el jamón; marcar el calabacín lo dejó pulsado. La selección de pollo para la receta solo se ha probado en los tests (con IA simulada) |
| Con **Gemini real** u **Ollama real** | ⏳ **no probado** (no hay clave ni Ollama en esta máquina) |

- **Problema conocido resuelto: el inventario reconoce los nombres escritos a mano.** Al añadir o editar un
  alimento sin elegirlo del catálogo, si el nombre es exactamente el de un alimento del catálogo ("leche"),
  una abreviatura compartida ("AOVE") o un nombre que el hogar confirmó al revisar un ticket ("Queso mozz.
  rallado"), queda asociado a ese alimento (con su categoría si no se eligió otra), y cuenta para recetas,
  fechas estimadas y la lista de la compra. El nombre se guarda tal como se escribió; cualquier otro nombre
  sigue siendo texto libre. Los alias pasan a ser una API del módulo `food` (`FoodAliases`), que usan el
  escaneo y el inventario.

#### Known issues

- **La comprobación de nombres reconoce el catálogo y una lista de alérgenos y carnes, no todo**: un alimento
  que no está en ninguno de los dos ("trufa") no se detecta; por eso el aviso pide revisar la receta. Y en algún
  caso raro puede rechazar una receta buena ("nuez moscada" cuenta como nuez). La lista está escrita a mano.
- **Las instrucciones de cocinado las escribe el modelo**: tiempos y temperaturas no se comprueban, porque no
  hay una regla fiable que lo haga; lo cubre el aviso visible. No se puede resolver en código.
- **No se puede guardar ni marcar como cocinada** una receta generada. Es a propósito (D70): marcar como
  cocinada solo sirve para las recetas del catálogo (variedad del plan y recomendaciones), y guardar recetas
  de calidad desconocida sería una función nueva que el MVP no pide. (Resuelto: elegir qué alimentos usar.)

### Primera unidad: escanear el ticket de la compra

Primera unidad: **escanear el ticket de la compra**. Todo es gratis por defecto: el OCR corre en el móvil y
el backend lee el ticket con reglas; un modelo de lenguaje es opcional y puede ser gratuito (capa gratuita de
Gemini u Ollama en local).

### Completed

- **Escanear un ticket desde el móvil** (botón de ticket en el inventario):
  - Foto con la cámara o desde la galería. El texto se lee **en el teléfono** con Google ML Kit, sin conexión
    y sin coste; **la foto no se envía** y la copia que queda en la caché de la app se borra al leerla.
  - El OCR lee las columnas del ticket (productos y precios) por separado; la app vuelve a montar cada fila
    por su altura en la foto.
  - También se puede **pegar el texto** de un ticket digital; es la única vía donde no hay cámara (la versión
    web de la app).
- **Lectura del ticket en el backend** (`POST /households/{id}/scans/receipt`), que **no guarda nada**:
  - **Por reglas**, siempre disponibles: producto y precio al final de la línea, número de unidades delante,
    tamaños ("1L", "33CL", "6X125G", "12 UDS"), productos pesados con su línea "0,856 kg x 2,10 €/kg" y líneas
    "3 x 1,29" bajo un producto. Descarta cabecera, totales, IVA, pago y descuentos, y lee la fecha del ticket
    (solo si es de los últimos 30 días; si no, propone hoy).
  - **Con un modelo de lenguaje, si se configura**: recibe el texto con los números largos tapados (tarjeta,
    NIF, teléfono) y devuelve los productos en un esquema JSON. La respuesta se comprueba entera y **se
    descarta entera** si no sigue el esquema o si cita algo que no está en el ticket; entonces se usan las
    reglas. Cada persona tiene un límite diario de llamadas (30 por defecto) y la llamada no puede pasar de
    12 s.
  - **Cada línea se asocia a un alimento del catálogo por palabras**: plurales, tildes y palabras como "de" no
    estorban; gana el nombre más concreto ("tomate triturado" antes que "tomate") y, a igualdad, el producto
    que va primero ("CHOCOLATE CON LECHE" es chocolate). Si quedan dos alimentos empatados no se elige ninguno
    y se ofrecen como candidatos.
  - **28 abreviaturas de tickets** escritas a mano ("PECH POLLO", "AOVE", "ESPAGUETI"...), solo como
    sugerencia.
  - **El hogar enseña al sistema**: al confirmar, cada línea recuerda el alimento elegido para ese texto en ese
    hogar ("QUESO MOZZ RALLADO" → mozzarella) y la próxima vez sale "Como la última vez". Decir que una línea
    no es ningún alimento del catálogo olvida la elección. Otros hogares no ven lo que aprende uno.
- **Revisión antes de añadir nada** (regla P2): cada línea muestra el texto del ticket, el alimento propuesto
  y **qué se ha leído y qué se ha supuesto** ("1 l · del ticket", "1 ud · supuesto", "Reconocido en el
  catálogo", "Sin alimento del catálogo", "Revisado por ti"), con la fecha de compra "leída del ticket" o "no
  aparece en el ticket".
  - Solo se proponen marcados los alimentos del catálogo; el resto (bolsa, detergente) queda desmarcado.
  - Cada línea se puede corregir: alimento (candidatos o búsqueda en el catálogo), cantidad, unidad,
    categoría, ubicación, precio y fecha de caducidad si se ve en el envase.
  - Salir a mitad de la revisión pide confirmación.
- **Confirmar** (`POST …/scans/receipt/confirm`) pone en el inventario lo marcado, con su precio y la fecha de
  compra. Sin fecha de caducidad se estima, **marcada como estimación**; la que la persona lee en el envase es
  suya. Si una línea falla no entra ninguna. Evento de producto `receipt_scanned`.
- **Módulo `ai`** con `AiService` (casos de uso concretos) y `AiProvider`: sin modelo (por defecto) o
  cualquier servicio con la API de OpenAI y salida estructurada (Gemini, Ollama, OpenAI...), elegido por
  variables de entorno. Si se elige un proveedor y falta la URL o el modelo, el backend no arranca.

### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 340 tests (33 nuevos: lectura por reglas, asociación al catálogo, validación de la respuesta del modelo, cliente HTTP del proveedor, configuración y API de escaneo); 1 omitido, el que habla con Firebase real |
| Mobile `flutter analyze` y APK debug con ML Kit | ✅ sin avisos / generado |
| Mobile `flutter test` | ✅ 173 en CI (pull request #19), con los 5 nuevos |
| Ticket de ejemplo contra el backend real | ✅ un ticket de Mercadona escrito a mano, de 13 productos: los 11 alimentos asociados bien (también "PECH POLLO", "AOVE" y "ESPAGUETI 500G" → pasta 500 g), el tomate y el plátano con su peso, los yogures "2 x 4X125G" como 1000 g, la fecha leída y detergente y bolsa desmarcados |
| App móvil (versión web) contra el backend real | ✅ texto pegado → revisión → "QUESO MOZZ RALLADO" corregido a mozzarella buscándolo en el catálogo → 5 productos en el inventario con precio y fechas estimadas; un segundo ticket propuso mozzarella "Como la última vez"; el inventario se refrescó al volver; salir a mitad de revisión pidió confirmación y no añadió nada |
| Camino del modelo de lenguaje de punta a punta | ✅ con un servidor local que imita la API de OpenAI: el backend envió el esquema y la clave, el número de tarjeta llegó tapado, "PCHG PLL" se convirtió en pechuga de pollo 0,5 kg, y una respuesta con un producto inventado se descartó y se usaron las reglas |
| Con **Gemini real** u **Ollama real** | ⏳ **no probado**: no hay clave ni Ollama en esta máquina (ver HOW_TO_USE 2.5) |
| **Cámara y ML Kit en un móvil** | ⏳ **no probado**: no hay emulador ni dispositivo; solo se ha comprobado que el APK compila |

### Pendiente en esta fase

- Asistente.
- Escanear desde la web (hoy solo el móvil; la web podría pegar texto).

### Known issues

- **ML Kit sin probar en un teléfono**: la calidad real del OCR con tickets térmicos o arrugados es
  desconocida (riesgo R2). La revisión es el camino principal precisamente por eso.
- **Las reglas conocen los formatos habituales, no todos**: tickets con el precio en otra línea o con columnas
  raras pueden salir mal leídos o vacíos. Sin modelo configurado no hay otra lectura.
- **Capa gratuita de Gemini**: Google puede usar lo que se le envía para mejorar sus productos. Sirve para
  probar; con usuarios reales hace falta la capa de pago u Ollama (ver HOW_TO_USE 2.5).
- (Resuelto en la segunda unidad: el límite diario de llamadas se guarda en la base de datos.)
- **Las abreviaturas son pocas y escritas a mano**; se irán completando con lo que aprenden los hogares.
- (Resuelto: el alta y la edición a mano del inventario reconocen los nombres del catálogo y los alias; ver
  la segunda unidad.)
- **Cada línea confirmada se recuerda**, también las que ya se reconocían por su nombre: la tabla crece con los
  textos distintos que compra cada hogar (son pocos).

## Fase 6 — Shopping 🟡

### Completed

- **Lista de la compra compartida por hogar** (`/households/{id}/shopping-list`): cualquier miembro la ve y
  la cambia, y lo que hace uno lo ven los demás al momento (`shopping-list-changed` en tiempo real).
- **Lista automática a partir del plan** (`POST …/from-plan` con una semana): añade lo que les falta a las
  comidas planificadas de esa semana, de hoy en adelante.
  - Calcula lo que falta como el plan: las comidas se cocinan en orden, lo que caduca antes se usa antes y lo
    pasado de fecha no cuenta. Con el ejemplo de la especificación (300 g + 300 g de pollo y 200 g en casa)
    pide 400 g.
  - Suma lo que piden varias comidas (200 g + 200 g de pasta = 400 g) y escribe 1500 g como 1,5 kg.
  - No pide lo que ya hay en casa, ni lo que alguien ya puso en la lista, ni lo que ya se compró para esa
    semana. Los básicos de cocina (sal, aceite) nunca se piden.
  - Volver a pulsarlo recalcula las líneas del plan de esa semana: si el plan o el inventario cambian, la
    lista se ajusta sin duplicar nada. Las líneas de otras semanas no se tocan. Dos miembros que lo pulsan a
    la vez se ordenan en la base de datos y no duplican líneas.
  - Cada línea del plan dice para qué día hace falta ("Para el plan · lunes 5 oct").
- **Añadir a mano**: un alimento del catálogo (su nombre sale del catálogo en el idioma de cada miembro) o
  texto libre, con o sin cantidad. Si el mismo alimento ya está en la lista sin comprar, se suma a esa línea.
- **Cambiar la cantidad** de una línea: desde entonces es de las personas y el plan ya no la cambia.
- **Marcar como comprado** (y desmarcar), **quitar** una línea y **quitar lo comprado** de una vez, con
  confirmación.
- **Agrupada por pasillos** en el orden de la especificación: verduras, frutas, lácteos, carne, pescado,
  huevos, panadería, despensa, congelados, bebidas, preparados y otros.
- **Web y móvil**: pantalla "Lista de la compra" con acceso desde el inventario, y botón "Llevar a la lista lo
  que falta" en el plan semanal.
- Evento de producto `shopping_list_created` la primera vez que el plan llena una lista vacía.
- **Pasar lo comprado al inventario** (`POST …/items/checked/to-inventory`, web y móvil): cada línea comprada
  entra en el inventario con fecha de compra de hoy, la ubicación habitual del alimento (o "otro" si es texto
  libre) y, si hay regla, una fecha estimada etiquetada como tal; después sale de la lista. Una línea sin
  cantidad se queda en la lista y se dice cuáles: el inventario necesita saber cuánto hay y no se inventa.
- **El texto libre que nombra un alimento del catálogo es ese alimento**: "leche" escrita a mano es la Leche
  del catálogo, se suma a la que pida el plan y sale en el idioma de cada miembro.

### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 289 tests (20 nuevos: 16 de la API de la lista, 3 de la despensa simulada y 1 de unidades); 1 omitido, el que habla con Firebase real |
| Comprobación del test de aislamiento de la lista | ✅ al quitar a propósito la comprobación de pertenencia al hogar, el test falla (200 en lugar de 404) |
| Límites entre módulos (`ModularityTests`) | ✅ la lista solo usa las interfaces públicas del plan (`PlanNeeds`), alimentos y hogares |
| Migración `V9` sobre la base local con datos | ✅ aplicada al arrancar |
| Web lint / test / build | ✅ sin avisos / 160 tests (13 nuevos) / correcto |
| Web contra el backend real | ✅ con el plan generado de un hogar de prueba, la lista se llenó con 5 cosas agrupadas por pasillo (400 g de pasta sumando dos comidas); se marcó la pasta como comprada, se añadió "Pilas" como texto libre y, al volver a llenarla, la pasta comprada no se pidió de nuevo |
| Código de la app móvil contra el backend real (compilado para web en modo de depuración) | ✅ llenó la lista desde el plan de la semana (9 cosas, con "Patata · 1 kg" de 1000 g) y marcó una línea como comprada, sin errores en consola. No se probó añadir ni cambiar cantidades en esta versión |
| Mobile `flutter analyze` y APK debug | ✅ sin avisos / generado |
| Mobile `flutter test` | ✅ 164 en CI (pull request #17), con los 12 de la lista y el de "pasar al inventario" |
| Pasar lo comprado al inventario: backend `./mvnw verify` | ✅ 292 tests (3 nuevos) |
| Pasar lo comprado al inventario: web | ✅ 161 tests (1 nuevo); contra el backend real, con leche (escrita a mano como "leche"), pasta y pilas sin cantidad marcadas como compradas: la leche (1 l, nevera) y la pasta (400 g, despensa) pasaron al inventario sin fecha inventada y las pilas siguieron en la lista con el aviso |

### Known issues

- **Lo comprado cuenta como ya comprado hasta que se pasa al inventario o se quita de la lista**: mientras
  tanto, al llenar esa misma semana no se pide de nuevo.
- **Al pasar al inventario no se pide la fecha del envase**: entra con la estimada por las reglas, o sin fecha
  si no hay regla; se puede editar después.
- **Cantidades no comparables** (la receta pide "2 tomates" y en casa hay "500 g"): no se piden, porque no se
  sabe si hacen falta; es el riesgo R4.
- **Las cantidades son las de las recetas**: no se ajustan a las raciones ni al formato en que se vende
  (no dice "1 paquete").
- **No hay precios** ni coste estimado de la compra.
- **El texto libre solo se reconoce si coincide con el nombre del catálogo** (sin mayúsculas ni acentos);
  "leche entera" o "jitomate" siguen siendo texto libre. No hay sinónimos (previsto para la Fase 7).
- **El orden de los pasillos es fijo**, no el de una tienda concreta.

## Problemas conocidos resueltos — lote 2 (2026-10-03)

### Completed

- **El refresh token de la web ya no está en `localStorage`** (riesgo R8): el backend lo pone en una cookie
  `HttpOnly` (`Secure`, `SameSite=Strict`, solo para `/api/v1/auth`) que ningún script puede leer, así que un
  XSS no puede robarlo. La web solo guarda una marca de que hay sesión, sin secretos.
  - La web lo pide con la cabecera `X-Freezify-Session: cookie`; sin ella el backend no usa la cookie, de modo
    que un formulario o un enlace de otro sitio no pueden renovar ni cerrar la sesión.
  - Renovar rota la cookie como rotaba el token; una cookie usada dos veces cierra la sesión entera y el
    navegador recibe la orden de olvidarla. Cerrar sesión la revoca y la borra.
  - Quien tenía el token guardado por la versión anterior lo entrega una vez al recargar y pasa a la cookie,
    sin tener que volver a iniciar sesión.
  - La app móvil no cambia: sigue guardando su token en el almacenamiento seguro del teléfono.
  - En local sin HTTPS la cookie no lleva `Secure` (`FREEZIFY_REFRESH_COOKIE_SECURE=false`, ya puesto en el
    perfil local); en cualquier despliegue debe quedarse en `true`.
- **Invitaciones revocables**: el hogar muestra sus códigos activos y quien generó un código, o el
  propietario, puede revocarlo (con confirmación). Quien ya se unió se queda.
- **Transferir el hogar**: el propietario puede hacer propietario a otro miembro (con confirmación) y pasa a
  ser miembro; después puede abandonar el hogar. Siempre hay exactamente un propietario.

### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 307 tests (15 nuevos: 9 de la sesión del navegador y 6 de invitaciones y transferencia); 1 omitido, el que habla con Firebase real |
| Web lint / test / build | ✅ sin avisos / 169 tests (6 nuevos de ajustes del hogar y 2 de sesión; los de sesión existentes, adaptados a la cookie) / correcto |
| Mobile `flutter analyze` y APK debug | ✅ sin avisos / generado |
| Mobile `flutter test` | 🟡 los **4 nuevos no se han ejecutado** (168 en total): correrán en la CI del próximo pull request |
| Web contra el backend real | ✅ al iniciar sesión `localStorage` solo tenía `freezify.locale` y `freezify.session`, y `document.cookie` estaba vacío (la cookie no es visible para scripts); al recargar la página la sesión siguió; se revocó un código ajeno siendo propietario, se transfirió el hogar (los papeles se intercambiaron y apareció "Abandonar hogar"); al cerrar sesión la cookie dejó de renovar (401); y un token guardado en `localStorage` por la versión anterior pasó a la cookie al recargar y desapareció del almacenamiento |
| App móvil en el navegador | ⏳ no probado: la app móvil no cambia de sesión, y las pantallas nuevas de códigos y transferencia solo tienen sus tests de widget |

### Known issues

- **Una sesión de la web no se puede compartir con otra web en otro dominio**: la cookie es `SameSite=Strict`
  y solo vale para el mismo sitio; hoy la web y la API van siempre juntas detrás del mismo proxy.
- **La marca `freezify.session` puede quedar desfasada** (por ejemplo, si la cookie caduca): la web lo
  descubre al primer intento de renovar y vuelve a la pantalla de inicio de sesión.
- **Los códigos siguen siendo reutilizables** mientras son válidos (varias personas pueden unirse con el
  mismo); ahora se pueden revocar.

## Problemas conocidos resueltos — lote 1 (2026-10-03)

Problemas de la lista de "Known issues" de fases anteriores que se podían arreglar en código y probar aquí.

### Completed

- **Avisos de caducidad bien redactados con nombres en plural** (web, móvil y push): "Huevos: su fecha de
  caducidad es hoy" en lugar de "Huevos caduca hoy". Ninguna frase hace concordar un verbo con el nombre.
- **Plan de comidas**:
  - Dos miembros que eligen a la vez la misma comida ya no chocan: la elección es una sola instrucción en
    base de datos y gana la última. Lo que rellena el generador nunca sustituye a lo que un miembro eligió
    mientras tanto.
  - Cada comida dice si se cocinó ese día, y las de hoy tienen "La he cocinado". Marcar una receta como
    cocinada actualiza el plan en tiempo real.
  - Aviso en la comida si su receta contiene algo que el hogar no come (elegida a mano, o planificada antes
    de cambiar las restricciones).
  - **Añadir al plan desde la receta** (web y móvil): día de los próximos 14 y comida o cena, avisando antes
    de qué receta se sustituiría. Lleva al plan en esa semana.
  - El selector de recetas de la web muestra todas las que el hogar come, no solo 20.
- **Las recomendaciones de la web se actualizan solas** cuando otro miembro cambia el inventario.
- **Fechas estimadas para alimentos antiguos**: el barrido de cada noche (y al arrancar) estima la fecha de
  los alimentos en casa que no tienen ninguna pero podrían tenerla por sus reglas. Sin regla, sigue sin fecha.
- **Limpieza de avisos**: cada noche se borran los avisos de hace más de 90 días
  (`freezify.notifications.retention`).
- **Los flujos de tiempo real terminan cuando caduca el token** con el que se abrieron, no hasta 30 minutos
  después.
- **Swagger UI y `/v3/api-docs` desactivados por defecto**; se activan con `FREEZIFY_API_DOCS=true`. En local
  sin Docker siguen activos.
- **Docker**: la clave de Firebase llega al contenedor del backend desde una carpeta fuera del repositorio
  (`FREEZIFY_SECRETS_DIR`, montada solo lectura), y la CI comprueba que `docker-compose.yml` es válido.
- **La web explica el error de edición simultánea** (`CONCURRENT_MODIFICATION`) en lugar de dar un mensaje
  genérico.

### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 269 tests (10 nuevos); 1 omitido, el que habla con Firebase real |
| Test de elección simultánea | ✅ con el código anterior falla 3 de 3 veces (una de las dos peticiones recibe un error); con el nuevo pasa |
| Web lint / test / build | ✅ sin avisos / 147 tests (8 nuevos) / correcto |
| Mobile `flutter analyze` y APK debug | ✅ sin avisos / generado |
| Mobile `flutter test` | ✅ 151 en CI (pull request #15), con los 5 nuevos |
| Contra el backend y la base locales reales | ✅ al arrancar, el barrido estimó la fecha de 1 alimento antiguo sin fecha; con la API en modo local, `/v3/api-docs` y Swagger UI responden; el aviso real de un hogar de prueba se lee "Champiñones: quedan aproximadamente 4 días para su fecha de caducidad (fecha estimada)"; en la web, el plan avisó de la carne y el pescado de dos comidas en un hogar vegetariano, "La he cocinado" dejó la comida como "Cocinada", y "Añadir al plan" avisó de la receta que sustituiría, la añadió y llevó a esa semana |
| Código de la app móvil contra el backend real (compilado para web en modo de depuración) | ✅ "La he cocinado" en una comida de hoy, y "Añadir al plan" con el aviso de sustitución, el aviso de confirmación y el enlace al plan |
| `docker compose` con la clave | ⏳ sin probar: no hay Docker aquí; la CI solo valida el fichero |

### No resuelto en este lote

Siguen en la lista de cada fase, porque necesitan algo que aquí no hay (un dispositivo, un Mac, Docker, una
fuente de datos oficial, un servicio de correo) o una decisión de producto:

- Probar el push en un móvil Android; push en iOS.
- Revisar los datos de vida útil y de alérgenos con una fuente autorizada.
- Verificación de correo, recuperación de contraseña y borrado de cuenta (necesitan enviar correos).
- Preferencias personales y otras funciones nuevas.
- (Resueltos en el lote 2: refresh token de la web en cookie `HttpOnly`, revocar invitaciones y transferir un
  hogar.)

## Fase 5 — Smart Planning 🟡

### Completed

- **Plan semanal del hogar**: una receta por día y comida (comida y cena), de lunes a domingo.
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
- **Pantallas del plan en web y móvil**, con acceso desde el inventario del hogar:
  - La semana día a día, con comida y cena; se pasa a la semana anterior o siguiente y se vuelve a la actual.
  - En cada comida: la receta (abre su detalle), si es una propuesta automática, y frases con lo que aprovecha
    ("Aprovecha calabacín, con caducidad el 7 oct."), lo que falta por comprar y lo que habrá en menos
    cantidad. Las frases se construyen en el cliente con los datos del plan; una fecha estimada se dice
    siempre como estimada.
  - Elegir receta (buscando entre las que el hogar come), cambiarla, quitarla y moverla a otro día o comida;
    al mover se indica con qué receta se intercambiaría.
  - "Rellenar los huecos" genera el plan y dice cuántas comidas ha planificado y cuántas quedan vacías.
    "Rehacer la propuesta" solo aparece si hay propuestas automáticas por delante y **pide confirmación**.
    En una semana ya pasada no se ofrece generar.
  - Aviso "El plan deja caducar" con los alimentos, la cantidad que sobraría y su fecha.
  - El plan se actualiza solo cuando otro miembro lo cambia o cambia el inventario.

### Tests

| Qué | Resultado |
|---|---|
| Backend `./mvnw verify` | ✅ 259 tests (50 nuevos: 8 de la despensa simulada, 17 del generador y 25 de la API del plan); 1 omitido, el que habla con Firebase real |
| Comprobación del test de aislamiento del plan | ✅ al quitar a propósito la comprobación de pertenencia al hogar, el test falla (204 en lugar de 404) |
| Límites entre módulos (`ModularityTests`) | ✅ el módulo nuevo solo usa las interfaces públicas de recetas, inventario, alimentos y hogares |
| Migración `V8` sobre la base local con datos | ✅ aplicada al arrancar |
| Generador contra el backend y la base locales reales | ✅ con pollo (600 g, 2 días), tomates (3 días), lechuga (4), calabacín (5), arroz, huevos, champiñones sin fecha y yogur (1 día): planificó las seis comidas que quedaban de la semana, usó todo el pollo antes de su fecha en dos platos distintos, marcó la fecha de los champiñones como estimada y señaló el yogur como lo único que el plan deja caducar. Para la semana siguiente rellenó las 14 comidas con 14 platos distintos |
| Web lint / test / build | ✅ sin avisos / 139 tests (23 del plan: 13 de la página y 10 de las frases) / correcto |
| Plan, web contra el backend real | ✅ mostró la semana generada con sus frases y el aviso del yogur; en la semana siguiente generó 14 comidas, y se quitó una comida, se eligió otra receta y se movió sobre otra (se intercambiaron y cada una conservó su origen) |
| Mobile `flutter analyze` y APK debug | ✅ sin avisos / generado |
| Mobile `flutter test` | ✅ 146 en CI (pull request #14), con los 23 nuevos del plan |
| Plan, código de la app móvil contra el backend real (compilado para web en modo de depuración) | ✅ generó 6 comidas con sus frases (la fecha estimada de los champiñones, dicha como estimada), y se movió una comida sobre otra, se cambió su receta y se quitó, sin errores en consola. No se probó "Rehacer la propuesta" ni el cambio de semana en esta versión |

### Pendiente en esta fase

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
- **Solo se puede marcar como cocinada una comida de hoy**: el servidor anota lo cocinado con la fecha de hoy.
- **No hay "vaciar la semana"** ni copia de una semana a otra.
- **Mover una comida solo dentro de la semana en pantalla**, eligiendo el destino de una lista; no se
  arrastra. La API sí permite mover entre semanas.
- **Desde la receta solo se puede añadir a los próximos 14 días**; más allá, desde el plan.
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
- **La clave de Firebase con Docker no se ha probado**: `docker-compose.yml` la monta, pero nunca se ha ejecutado.
- **La hora de los avisos es la de `Europe/Madrid`** para todos los usuarios, y así se indica en la pantalla
  de preferencias.
- **La comprobación de avisos lee de una vez todos los alimentos próximos a caducar de todos los hogares**,
  cada hora. Es suficiente para el MVP; con muchos hogares habrá que hacerlo por lotes.
- **Con varias instancias del backend** dos comprobaciones simultáneas del mismo usuario chocarían en la base
  de datos: una se descarta y queda un error en el log, sin avisos duplicados. Sin probar.
- **La app móvil muestra solo los 50 avisos más recientes**; la web pagina.
- **La campana no se actualiza en tiempo real**: la web la refresca cada 5 minutos y al volver a la pestaña;
  el móvil, al volver a la app y al tirar para refrescar.
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
- **Selector de fecha del móvil sin probar**: ni los tests ni la prueba manual abren el calendario; solo se
  ha comprobado que el formulario guarda sin fecha y que conserva la fecha existente al editar.
- **Ediciones simultáneas**: si dos miembros editan el mismo alimento uno tras otro, gana el último. Si dos
  peticiones se solapan de verdad sobre el mismo alimento, la segunda debería recibir 409
  `CONCURRENT_MODIFICATION` (columna `version`), que web y móvil explican; ese caso **no tiene test**.
- **El catálogo no tiene sinónimos** ("jitomate"); desde la Fase 7 el escaneo de tickets entiende abreviaturas
  y aprende de cada hogar, y el alta a mano en el inventario reconoce esos mismos nombres.
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

- **Rate limiting por IP y cabeceras `X-Forwarded-*`**: el backend confía en esas cabeceras, así que debe ser accesible solo a través del proxy. El contador vive en memoria (una sola instancia).
- **El registro revela si un correo ya existe** (409). Mitigado por el rate limiting.
- **No hay verificación de correo, recuperación de contraseña ni eliminación de cuenta.**
- **Las invitaciones son reutilizables hasta que caducan (7 días)**; desde el lote 2 se pueden revocar.
- **Sin tests E2E automatizados**; las pruebas de extremo a extremo han sido manuales.
- **Detener el backend local**: si el proceso se mata en vez de cerrarse con Ctrl+C, PostgreSQL embebido puede quedar vivo en el puerto 54329.

## Next

1. Abrir el pull request de la foto de un alimento y confirmar en CI los 4 tests nuevos del móvil.
2. Probar con la cámara en un móvil Android (ticket y foto de alimento) y la IA con una clave gratuita de
   Gemini (HOW_TO_USE 2.5).
3. Fase 7, siguientes unidades: escanear tickets desde la web y el asistente.
4. Pendientes: probar el push en un móvil Android (Fase 3) y revisar los datos de alérgenos y de vida útil
   con una fuente autorizada antes de abrir a usuarios reales.
