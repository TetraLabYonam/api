# Manual testing

Use synthetic values only. Start the backend using the root-independent Docker bootstrap in `backend/README.md`; its `QUEUE_*` variables are required. Confirm `GET /actuator/health/readiness` is HTTP 200 and inspect `GET /actuator/prometheus` for `queue_ticket_requests_total`.

## API checks

1. Without a bearer token, `POST /api/v1/admin/jobs` and `GET /api/v1/admin/jobs` return HTTP 401. With `Authorization: Bearer local-admin-token`, the calls succeed.
2. Create an admin job, open a session, and confirm public `GET /api/v1/jobs` exposes only the open session. A closed or absent session is not publicly discoverable.
3. Issue a ticket with a synthetic phone value and retry it in different formatting. The retry is marked duplicate and reuses the number. Responses contain no phone HMAC, last-four, or key-version fields.
4. Capture Prometheus before and after one issue. Parse the `outcome` label and verify only the `issued` counter increases by one. Inspect label keys and values; no job ID, session UUID, phone, title, unit, or secret appears in labels. Do not search raw metric text for numeric job IDs.

## Android checks

Run `flutter devices`, select the intended physical device with `flutter run -d <device-id>`, and pass `--dart-define=API_BASE_URL=http://<host-lan-ip>:8080`. Use `10.0.2.2` only for an Android emulator. Verify member and admin flows separately. Cleartext HTTP is debug-only; build release and inspect the merged manifest as documented in `mobile/README.md`, confirming cleartext is absent or false.

## Teardown

```shell
docker rm -f queue-postgres
docker network rm queue-local
```

Terraform verification is validation only: run `terraform init`, `terraform fmt -check`, and `terraform validate`; do not apply infrastructure.
