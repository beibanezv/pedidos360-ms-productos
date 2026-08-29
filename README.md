# ms-productos — Pedidos360

Microservicio de catálogo (vinilos, tornamesas, audífonos, amplificadores).

## Requisitos
- Java 17, Maven 3.9+
- Docker (para Postgres local)

## Desarrollo local

```bash
docker compose up -d          # Postgres en localhost:5432
mvn spring-boot:run           # app en http://localhost:8081
```

Variables opcionales:
- `AZURE_TENANT_JWKS_URI` — issuer de Azure AD. Ej: `https://login.microsoftonline.com/<tenant-id>/v2.0`
  Si no se define, GET públicos funcionan igual; POST/PUT/DELETE requieren JWT y fallarán con 401 hasta configurar el tenant.

## Endpoints
- `GET /productos?categoria=vinilo` — público, filtro opcional
- `GET /productos/{id}` — público
- `POST /productos` — requiere JWT
- `PUT /productos/{id}` — requiere JWT
- `DELETE /productos/{id}` — requiere JWT

## Producción (RDS)
Activa perfil `prod`:
```bash
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/<db> \
SPRING_DATASOURCE_USERNAME=<user> \
SPRING_DATASOURCE_PASSWORD=<pass> \
AZURE_TENANT_JWKS_URI=https://login.microsoftonline.com/<tenant-id>/v2.0 \
java -jar target/ms-productos-*.jar
```

## Tests
```bash
mvn test
```
