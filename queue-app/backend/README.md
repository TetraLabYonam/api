# Queue backend

Spring Boot API with PostgreSQL and Flyway. Configuration is supplied through `QUEUE_*` environment variables.

## Docker Compose (recommended)

From the repository root:

```shell
cp .env.example .env
docker compose up --build
docker compose logs -f api
docker compose stop
docker compose down
docker compose down -v  # reset local database data
```

The API is internal to the Compose network and is reached through the web container at `http://localhost:8080` by default. Set `WEB_PORT` in `.env` when port 8080 is occupied. Readiness is available at `/actuator/health/readiness`; admin requests use the local `QUEUE_ADMIN_TOKEN` as a Bearer token. Use synthetic local values only.
