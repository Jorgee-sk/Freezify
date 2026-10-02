# Freezify

> Sé qué tengo, sé qué está a punto de caducar y sé qué puedo cocinar con ello.

Aplicación para controlar los alimentos de casa y reducir el desperdicio: inventario compartido por hogar,
prioridad por caducidad, recetas y plan semanal que aprovechan lo que caduca antes, y lista de la compra
generada a partir del plan.

**Estado:** Fases 0 a 2 completadas; Fase 3 (motor de caducidad) en curso. Hoy funciona de extremo a extremo: registro, sesión, hogares
compartidos e invitaciones, y el inventario del hogar con consumo, descarte y actualización en tiempo real, en backend, web y móvil. Detalle en [docs/STATUS.md](docs/STATUS.md).

## Documentación

| Documento | Contenido |
|---|---|
| [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) | Alcance del MVP, casos de uso, reglas de producto |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Módulos, decisiones técnicas, seguridad, modelo de datos, riesgos |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Fases y criterios de salida |
| [docs/STATUS.md](docs/STATUS.md) | Qué está hecho, qué se ha probado y qué no |
| [docs/HOW_TO_USE.md](docs/HOW_TO_USE.md) | Arrancar todo en local, ver la base de datos, Docker y plan de producción |
| [masterPrompt.md](masterPrompt.md) | Encargo original |

## Estructura

```text
apps/backend    Java 21 · Spring Boot 4 · PostgreSQL · Flyway   (monolito modular)
apps/web        React 19 · TypeScript · Vite
apps/mobile     Flutter (Android / iOS)
infrastructure  Dockerfiles y nginx
docs            Especificación, arquitectura, roadmap, estado
```

## Desarrollo local

Requisitos: JDK 21, Node 24, Flutter 3.44. **No hace falta Docker ni instalar PostgreSQL.**

### Backend

```bash
cd apps/backend
./mvnw spring-boot:test-run
```

Arranca en `http://localhost:8080` con un PostgreSQL embebido cuyos datos se guardan en
`apps/backend/.local/postgres` (bórralo para empezar de cero). Usa un secreto JWT solo válido para
desarrollo.

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>

Tests (arrancan su propio PostgreSQL embebido):

```bash
cd apps/backend
./mvnw verify
```

### Web

```bash
cd apps/web
npm install
npm run dev
```

Abre <http://localhost:5173>. Las llamadas a `/api` se redirigen al backend en `localhost:8080`.

```bash
npm run lint
npm test
npm run build
```

### Mobile

```bash
cd apps/mobile
flutter pub get
flutter run
```

Por defecto apunta a `http://10.0.2.2:8080/api/v1` (el backend de tu máquina visto desde el emulador de
Android). Para otro destino:

```bash
flutter run --dart-define=FREEZIFY_API_URL=http://192.168.1.20:8080/api/v1
```

```bash
flutter analyze
flutter test
```

## Con Docker

```bash
cp .env.example .env   # y rellena los valores
docker compose up --build
```

Web en <http://localhost:3000>, API en <http://localhost:8080>.

## Configuración del backend

| Variable | Obligatoria | Descripción |
|---|---|---|
| `FREEZIFY_JWT_SECRET` | Sí | Firma de los access tokens. Mínimo 32 bytes; la aplicación no arranca sin ella |
| `FREEZIFY_DB_URL` | No | JDBC URL. Por defecto `jdbc:postgresql://localhost:5432/freezify` |
| `FREEZIFY_DB_USER` / `FREEZIFY_DB_PASSWORD` | No | Credenciales de PostgreSQL |
| `FREEZIFY_CORS_ALLOWED_ORIGINS` | No | Orígenes de navegador permitidos, separados por comas |
| `FREEZIFY_PORT` | No | Puerto HTTP (8080) |
| `FREEZIFY_TIME_ZONE` | No | Zona horaria para calcular el día de hoy (`Europe/Madrid`) |
| `FREEZIFY_FCM_CREDENTIALS_FILE` | No | Ruta de la clave de la cuenta de servicio de Firebase. Sin ella no se envían notificaciones push |

Ningún secreto se guarda en el repositorio.
