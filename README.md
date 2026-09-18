# Heat Routing Service

Prototype for the LCT 2026 case: heat network routing for new connections.

## Mandatory stack

- Java 11
- Spring Boot 2.6.3
- Spring Data
- PostgreSQL + PostGIS
- springdoc-openapi-ui 1.7.0
- Docker Compose 1.29.2 compatible configuration

## Run locally

```bash
docker-compose up --build
```

Application:
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

Database:
- PostgreSQL/PostGIS on localhost:5432
- DB: heat_routing
- User: heat
- Password: heat

## First milestone

1. Accept input GeoJSON.
2. Validate geometry.
3. Transform EPSG:4326 -> EPSG:32637 for calculations.
4. Build routing model.
5. Return output GeoJSON.
