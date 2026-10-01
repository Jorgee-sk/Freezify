# Freezify backend

Monolito modular: Java 21, Spring Boot 4, PostgreSQL, Flyway. Arquitectura y decisiones en
[../../docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md).

## Comandos

```bash
./mvnw spring-boot:test-run
```

Arranca la aplicación con el perfil `local` y un PostgreSQL embebido en el puerto 54329
(`src/test/java/com/freezify/LocalFreezifyApplication.java`). Los datos se guardan en `.local/postgres`.
Detén la aplicación con Ctrl+C para que PostgreSQL se cierre limpiamente.

```bash
./mvnw verify
```

Ejecuta todos los tests contra un PostgreSQL embebido efímero.

## Módulos

Cada paquete de primer nivel bajo `com.freezify` es un módulo. El paquete raíz del módulo es su API pública;
`internal` y `web` son privados. `ModularityTests` falla si un módulo usa el interior de otro o si aparece un
ciclo.

| Módulo | Contenido |
|---|---|
| `common` | `ApiException`, errores `problem+json`, correlation ID, rate limiting |
| `users` | Cuenta y perfil. API: `Users`, `UserAccount` |
| `auth` | Registro, login, tokens, configuración de Spring Security |
| `households` | Hogares, miembros, invitaciones. API: `HouseholdAccess`, `HouseholdRole` |
| `food` | Catálogo de alimentos y cantidades. API: `FoodCatalog`, `Food`, `Quantity`, `Unit`, `FoodCategory`, `StorageLocation` |
| `inventory` | Alimentos del hogar, consumo y descarte. API: `ItemStatus`, `ExpirationSource`, `InventoryEvents` |
| `analytics` | Registra eventos de producto a partir de los eventos de los demás módulos |

## Añadir un módulo que maneje datos de un hogar

1. Recibe `householdId` de la ruta y el usuario de `CurrentUser.id(jwt)`.
2. Llama a `HouseholdAccess.requireMember(householdId, userId)` **antes** de leer o escribir nada.
3. Añade un test que compruebe que un usuario de otro hogar recibe 404.

## Esquema

Solo mediante migraciones Flyway en `src/main/resources/db/migration`. Hibernate valida el esquema al
arrancar (`ddl-auto=validate`) y nunca lo modifica.
