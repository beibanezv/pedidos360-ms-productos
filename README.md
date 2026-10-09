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
- `AZURE_TENANT_ID` — Directory (tenant) ID de Entra ID. Con ese valor el servicio
  arma el issuer `https://sts.windows.net/<tenant-id>/` y va a buscar las llaves
  a `https://login.microsoftonline.com/<tenant-id>/discovery/keys`.
  Los tokens de este tenant son **v1.0**: si se apunta a la ruta `/v2.0` no valida
  ninguno y todas las peticiones protegidas dan 401.
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
AZURE_TENANT_ID=<Directory (tenant) ID> \
java -jar target/ms-productos-*.jar
```

## Tests
```bash
mvn test
```
