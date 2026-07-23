# Queue mobile app

Flutter member and admin clients share one app. For local development, run from `mobile/`:

```shell
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
flutter run --dart-define=DOMAIN_TOKEN=member --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

## Docker Compose web deployment

From the repository root, `cp .env.example .env && docker compose up --build` compiles the Flutter web app and serves it through Nginx at `http://localhost:8080` by default. Set `WEB_PORT` in `.env` when port 8080 is occupied. The build uses an empty `API_BASE_URL`, so browser requests are same-origin; Nginx proxies `/api/` and `/actuator/` to the API service. Stop with `docker compose stop`, or reset all local data with `docker compose down -v`.

Use synthetic local values only; do not place tokens or real phone numbers in source or command history.
