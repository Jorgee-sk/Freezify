# Freezify — Cómo usarlo, desplegarlo y llevarlo a producción

Esta guía tiene dos partes:

1. **Lo que existe hoy**: cómo arrancar todo en tu máquina, ver la base de datos y usar Docker.
2. **Producción**: qué falta y cómo propongo hacerlo. Esa parte es un **plan**; todavía no hay nada desplegado.

A lo largo de la guía se marca qué se ha probado y qué no. Lo que no está probado puede necesitar ajustes.

---

## 1. Qué hay y cómo encaja

```text
Navegador ──► Web (React)  ──┐
                             ├──► Backend (Spring Boot, puerto 8080) ──► PostgreSQL
Móvil (Flutter) ─────────────┘
```

| Pieza | Carpeta | En local |
|---|---|---|
| Backend | `apps/backend` | `http://localhost:8080` |
| Web | `apps/web` | `http://localhost:5173` (desarrollo) |
| Móvil | `apps/mobile` | Emulador o dispositivo |
| Base de datos | — | PostgreSQL embebido (puerto 54329) o contenedor (puerto 5432) |

Hay **dos formas** de tenerlo en marcha en local:

| | Sin Docker (desarrollo) | Con Docker |
|---|---|---|
| Para qué | Programar: arranque rápido, recarga en caliente de la web | Ejecutar todo como se ejecutaría en un servidor |
| Base de datos | PostgreSQL embebido, se arranca solo | Contenedor `postgres:18` |
| Estado | ✅ Probado, es como se ha desarrollado todo | 🟡 Las imágenes se construyen en CI; `docker compose up` **no se ha ejecutado nunca** |

---

## 2. Arrancar en local sin Docker

Requisitos: JDK 21, Node 24, Flutter 3.44. No hace falta instalar PostgreSQL.

### 2.1 Backend

```bash
cd apps/backend
./mvnw spring-boot:test-run
```

- Queda escuchando en `http://localhost:8080`.
- Arranca un PostgreSQL embebido en el puerto **54329** y aplica las migraciones pendientes.
- Los datos se guardan en `apps/backend/.local/postgres` y sobreviven a los reinicios.
- Usa un secreto JWT que solo sirve para desarrollo.

Comprobar que está vivo:

```bash
curl http://localhost:8080/actuator/health
```

Para pararlo: **Ctrl+C** en esa terminal. Si cierras la ventana o matas el proceso, PostgreSQL puede quedar
vivo ocupando el puerto 54329 y el siguiente arranque fallará; en ese caso busca el proceso y termínalo:

```powershell
netstat -ano | findstr :54329
```

```powershell
taskkill /PID <el PID de la última columna> /T /F
```

Para empezar con la base de datos vacía, para el backend y borra la carpeta `apps/backend/.local`.

### 2.2 Web

En otra terminal:

```bash
cd apps/web
npm install
npm run dev
```

Abre <http://localhost:5173>. La web llama a `/api`, y el servidor de desarrollo lo redirige al backend en
`localhost:8080`, así que el backend tiene que estar arrancado.

### 2.3 Móvil

Con un emulador de Android abierto (o un dispositivo conectado):

```bash
cd apps/mobile
flutter pub get
flutter run
```

- Desde el **emulador de Android**, la app apunta por defecto a `http://10.0.2.2:8080/api/v1`, que es el
  backend de tu máquina visto desde el emulador.
- Desde un **móvil físico** en la misma wifi, indícale la IP de tu ordenador:

```bash
flutter run --dart-define=FREEZIFY_API_URL=http://192.168.1.20:8080/api/v1
```

  Puede que el cortafuegos de Windows te pida permitir conexiones entrantes al puerto 8080.
- El tráfico sin cifrar (`http://`) solo está permitido en compilaciones de **debug**. Una compilación de
  release exige `https://`.

> 🟡 La app no se ha ejecutado todavía en un emulador ni en un dispositivo (no hay ninguno configurado en la
> máquina de desarrollo). Se ha comprobado que compila el APK, sus 59 tests pasan en CI y su código funciona
> contra el backend real ejecutado en navegador y en la VM de Dart.

### 2.4 Notificaciones push (Firebase)

Los avisos dentro de la app funcionan sin configurar nada. Para que el servidor además envíe notificaciones
push hace falta la clave de la cuenta de servicio de Firebase:

| Qué | Dónde |
|---|---|
| Proyecto de Firebase | `freezify-c50ea`, en la cuenta de Google del propietario: <https://console.firebase.google.com/project/freezify-c50ea> |
| App Android registrada | `com.freezify.app` |
| Clave de la cuenta de servicio | `.freezify\firebase-service-account.json` dentro de tu carpeta de usuario (fuera del repositorio) |
| Configuración de la app Android | `apps/mobile/android/app/google-services.json` (ignorado por git) |

Arrancar el backend con push, en PowerShell:

```powershell
$env:FREEZIFY_FCM_CREDENTIALS_FILE = "$env:USERPROFILE\.freezify\firebase-service-account.json"
./mvnw spring-boot:test-run
```

Al arrancar debe aparecer `Push notifications are sent through Firebase project freezify-c50ea`. Con la
misma variable definida, `./mvnw test -Dtest=FcmLiveTests` comprueba contra Firebase real que la clave sirve,
sin entregar ningún mensaje.

**La clave es un secreto**: da acceso de administrador al proyecto de Firebase. No la copies al repositorio
ni la envíes por correo o chat. Si se filtra, bórrala en la consola de Firebase (Configuración del proyecto →
Cuentas de servicio → Administrar permisos de cuentas de servicio) y genera otra.

Para probarlo en un móvil Android (todavía sin hacer):

1. Comprueba que `apps/mobile/android/app/google-services.json` existe; sin él la app compila sin push.
2. Arranca el backend con la clave, como arriba, accesible desde el móvil (misma red wifi).
3. Instala la app apuntando a ese backend:
   `flutter run --dart-define=FREEZIFY_API_URL=http://<IP-de-tu-ordenador>:8080/api/v1`
4. Inicia sesión y acepta el permiso de notificaciones. En la tabla `device_tokens` debe aparecer una fila.
5. Añade un alimento que caduque mañana y reinicia el backend: al arrancar crea el aviso y envía el push.
   Solo hay un aviso por hogar y día, así que para repetir la prueba usa otro hogar u otro usuario.

La web no recibe push.

### 2.5 Probar la API a mano

- **Swagger UI**: <http://localhost:8080/swagger-ui.html>. Regístrate con `POST /auth/register`, copia el
  `accessToken` de la respuesta, pulsa **Authorize** y pégalo. El token dura 15 minutos.
- **Health**: <http://localhost:8080/actuator/health>.

### 2.6 Tests

```bash
cd apps/backend
./mvnw verify
```

```bash
cd apps/web
npm run lint
npm test
```

```bash
cd apps/mobile
flutter analyze
flutter test
```

En la máquina de desarrollo actual `flutter test` no funciona (una directiva de Windows bloquea el ejecutable
de tests de Flutter). Esos tests se ejecutan en la CI de GitHub en cada pull request.

---

## 3. Ver la base de datos

### 3.1 Datos de conexión

| | Local sin Docker | Con Docker |
|---|---|---|
| Host | `localhost` | `localhost` |
| Puerto | `54329` | `5432` |
| Base de datos | `postgres` | `freezify` |
| Usuario | `postgres` | `freezify` |
| Contraseña | *(vacía)* | la de `FREEZIFY_DB_PASSWORD` en tu `.env` |
| Disponible | Solo mientras el backend está arrancado | Mientras el contenedor `postgres` está arrancado |

En los dos casos el puerto solo es accesible desde tu propia máquina.

### 3.2 Con una herramienta gráfica

Cualquier cliente de PostgreSQL sirve: [DBeaver](https://dbeaver.io) (gratuito), pgAdmin, TablePlus o el panel
de base de datos de IntelliJ. Crea una conexión PostgreSQL con los datos de la tabla anterior.

URL JDBC para el modo sin Docker:

```text
jdbc:postgresql://localhost:54329/postgres
```

### 3.3 Por línea de comandos

Con Docker, sin instalar nada más:

```bash
docker compose exec postgres psql -U freezify freezify
```

Sin Docker necesitas tener `psql` instalado:

```bash
psql -h localhost -p 54329 -U postgres postgres
```

### 3.4 Qué tablas hay

| Tabla | Contenido |
|---|---|
| `users` | Cuentas (la contraseña está cifrada con bcrypt, no se puede leer) |
| `refresh_tokens` | Sesiones abiertas (solo se guarda el hash del token) |
| `households`, `household_members`, `household_invitations` | Hogares, quién pertenece a cada uno y códigos de invitación |
| `foods` | Catálogo de alimentos (78 filas, lo crean las migraciones) |
| `food_items` | El inventario de cada hogar |
| `food_outcomes` | Cada consumo o descarte, con su valor estimado |
| `product_events` | Eventos de producto para métricas |
| `flyway_schema_history` | Qué migraciones se han aplicado |

Consultas útiles:

```sql
-- Qué hay en cada hogar, lo que antes caduca primero
select h.name as hogar, i.name, i.quantity, i.unit, i.storage_location, i.expiration_date, i.status
from food_items i join households h on h.id = i.household_id
order by h.name, i.expiration_date nulls last;

-- Consumido frente a tirado
select type, count(*), sum(estimated_value) as valor_estimado
from food_outcomes group by type;

-- Migraciones aplicadas
select version, description, installed_on, success from flyway_schema_history order by installed_rank;
```

> ⚠️ Mira, pero no modifiques el esquema a mano. El esquema solo cambia mediante migraciones Flyway
> (`apps/backend/src/main/resources/db/migration`); si lo cambias por fuera, el backend se negará a arrancar
> porque valida el esquema al inicio.

---

## 4. Docker

> 🟡 **Estado:** las dos imágenes se construyen correctamente en la CI de GitHub en cada pull request. Lo que
> **nunca se ha ejecutado** es `docker compose up`: Docker no está instalado en la máquina de desarrollo.
> La primera vez que lo ejecutes puede aparecer algún ajuste pendiente.

### 4.1 Preparación (una vez)

1. Instala [Docker Desktop](https://www.docker.com/products/docker-desktop/) y ábrelo.
2. Crea el fichero de configuración a partir del ejemplo:

```bash
cp .env.example .env
```

3. Rellena `.env`. Necesita dos valores que no deben estar vacíos:
   - `FREEZIFY_DB_PASSWORD`: una contraseña cualquiera para la base de datos.
   - `FREEZIFY_JWT_SECRET`: al menos 32 caracteres aleatorios. Para generar uno:

```bash
openssl rand -base64 48
```

`.env` no se sube al repositorio (está en `.gitignore`).

4. Opcional, para enviar notificaciones push: deja la clave de Firebase en una carpeta **fuera del
   repositorio** y añade a `.env`:

```bash
FREEZIFY_SECRETS_DIR=/ruta/a/la/carpeta/con/la/clave
FREEZIFY_FCM_CREDENTIALS_FILE_IN_CONTAINER=/run/secrets/freezify/firebase-service-account.json
```

   La carpeta se monta en el contenedor del backend en `/run/secrets/freezify`, solo lectura. Sin esas
   variables no se envían push y todo lo demás funciona. Con Docker no se ha probado nunca.

5. Opcional: `FREEZIFY_API_DOCS=true` publica la descripción de la API (Swagger UI). Por defecto está
   desactivada; en local sin Docker (`spring-boot:test-run`) siempre está activa.

### 4.2 Arrancar

```bash
docker compose up --build -d
```

| Servicio | URL | Qué es |
|---|---|---|
| `web` | <http://localhost:3000> | nginx sirviendo la web y redirigiendo `/api` al backend |
| `backend` | <http://localhost:8080> | La API |
| `postgres` | `localhost:5432` | La base de datos, con los datos en el volumen `postgres-data` |

La primera vez tarda varios minutos porque descarga dependencias y compila.

### 4.3 Operaciones habituales

Estado de los contenedores:

```bash
docker compose ps
```

Logs del backend en vivo:

```bash
docker compose logs -f backend
```

Parar sin perder datos:

```bash
docker compose down
```

Parar **y borrar la base de datos** (irreversible):

```bash
docker compose down -v
```

Reconstruir tras cambiar código:

```bash
docker compose up --build -d
```

### 4.4 No mezcles los dos modos

El modo sin Docker y el modo Docker usan el puerto 8080 para el backend: no pueden estar arrancados a la vez.
Además son **bases de datos distintas**: lo que registres en uno no aparece en el otro.

---

## 5. Cómo se despliega hoy

**Hoy no hay ningún despliegue.** Todo lo anterior ocurre en tu máquina. Lo que sí existe:

- **CI en GitHub Actions** (`.github/workflows/ci.yml`): en cada pull request y en cada push a `main` ejecuta
  los tests de backend, web y móvil, y construye las dos imágenes Docker. No publica ni despliega nada.
- **Dockerfiles** de backend y web, y un `docker-compose.yml` pensado para una sola máquina.

---

## 6. Llevarlo a producción

### 6.1 Propuesta

Para el tamaño actual del producto propongo lo más simple que funcione: **un servidor pequeño con Docker
Compose**, que es exactamente lo que ya describe el repositorio.

```text
Internet ──HTTPS──► Proxy con TLS ──► web (nginx) ──► backend ──► PostgreSQL
                    (certificado            │
                     automático)            └── /api y eventos en tiempo real
```

| Decisión | Propuesta | Por qué |
|---|---|---|
| Dónde | Un VPS (2 vCPU, 4 GB) en la UE | Barato, suficiente para validar, y los datos se quedan en la UE (RGPD) |
| Cómo se ejecuta | Docker Compose | Ya existe; no hace falta Kubernetes para una instancia |
| HTTPS | Un proxy que gestione certificados solo (Caddy) delante de `web` | Sin HTTPS la app móvil de release no funciona y las contraseñas viajarían en claro |
| Base de datos | Empezar con el contenedor + copias automáticas; pasar a PostgreSQL gestionado cuando haya usuarios reales | Las copias de seguridad son lo que no puede fallar |
| Imágenes | La CI las publica en el registro de GitHub (GHCR) al fusionar en `main` | El servidor solo descarga imágenes ya probadas; no compila |
| Despliegue | `docker compose pull && docker compose up -d` en el servidor, lanzado desde la CI | Reproducible y con vuelta atrás: basta con volver a la etiqueta anterior |
| Secretos | Variables de entorno en el servidor, nunca en el repositorio | Ya está diseñado así |

Hay una restricción importante: **solo puede haber una instancia del backend**. El tiempo real y el límite de
peticiones viven en memoria (decisiones D12 y D14 de `ARCHITECTURE.md`). Para escalar a varias instancias
habría que moverlos antes a un almacén compartido.

La alternativa es una plataforma gestionada (Fly.io, Railway, Render) con PostgreSQL gestionado: menos
administración a cambio de más coste y de adaptar la configuración a cada plataforma. Tiene sentido si no
quieres mantener un servidor.

### 6.2 Qué falta por construir

Nada de esto existe todavía:

| Qué | Para qué |
|---|---|
| Publicación de imágenes desde la CI | Tener versiones etiquetadas que desplegar |
| `docker-compose.prod.yml` con proxy TLS y sin puertos internos expuestos | Que solo el proxy sea accesible desde internet |
| Job de despliegue (manual al principio) | Desplegar con un clic y poder volver atrás |
| Copias de seguridad de PostgreSQL, guardadas fuera del servidor, y **una restauración probada** | Una copia que nunca se ha restaurado no es una copia |
| Logs en JSON, alerta de caída y seguimiento de errores | Enterarse de los fallos antes que los usuarios |
| Entorno de pruebas (*staging*) | Probar cada versión con datos que no importan |

### 6.3 Qué hay que arreglar antes de abrir a usuarios reales

Estos puntos ya están identificados en `docs/STATUS.md` y bloquean un lanzamiento público:

| Bloqueo | Motivo |
|---|---|
| Verificación de correo y recuperación de contraseña | Hoy quien olvida la contraseña pierde la cuenta |
| Eliminación de cuenta y de sus datos | Obligación del RGPD |
| Refresh token de la web en una cookie `HttpOnly` | Hoy está en `localStorage`, expuesto a XSS |
| Swagger UI y `/v3/api-docs` cerrados en producción | Hoy son públicos |
| El backend solo accesible a través del proxy | Confía en las cabeceras `X-Forwarded-*` para el límite de peticiones |
| Tiempo real probado a través de nginx | Solo se ha probado contra el backend directamente |
| Política de privacidad y aviso legal | Se tratan datos personales |

### 6.4 La app móvil en producción

| Paso | Detalle |
|---|---|
| URL de la API | Se fija al compilar: `--dart-define=FREEZIFY_API_URL=https://api.tudominio.com/api/v1` |
| Android | Firmar la app con una clave propia (que **no** va al repositorio) y subirla a Google Play; empezar por la pista de pruebas internas |
| iOS | Requiere un Mac y una cuenta de Apple Developer; hoy no se puede ni compilar desde la máquina de desarrollo |
| Notificaciones push | Servidor y app Android listos (sección 2.4), sin probar aún en un dispositivo; para iOS falta un certificado de APNs. En producción la clave se monta como fichero y se indica con `FREEZIFY_FCM_CREDENTIALS_FILE` |

### 6.5 Orden propuesto

1. **Ahora**: seguir con las fases del producto en local. No hace falta producción para construir el MVP.
2. **Cuando el recorrido principal esté completo** (inventario → caducidad → recetas): montar *staging* con el
   esquema de 6.1 y probar ahí Docker, el proxy, las copias y el tiempo real a través de nginx.
3. **Antes de invitar a nadie**: resolver los bloqueos de 6.3.
4. **Beta cerrada**: unos pocos hogares de confianza, Android por pruebas internas.
5. **Lanzamiento**: solo después de validar uso real.

### 6.6 Decisiones que son tuyas

| Decisión | Opciones |
|---|---|
| Dónde alojarlo | VPS con Docker Compose (propuesto) o plataforma gestionada |
| Dominio | Hace falta uno para HTTPS y para la app móvil |
| Cuándo montar *staging* | Ya, para probar Docker cuanto antes, o al completar el recorrido principal (propuesto) |
| Cuentas de tiendas | Google Play (pago único) y Apple Developer (anual); solo cuando toque publicar |
