# Backend consolidation

The independent reference application has been retired. The single executable
backend is `apps/authorization-api`, built by the root Maven project and run by
`docker/docker-compose.yml` on port 8080.

SDK lifecycle endpoints are implemented in `com.zt.security.integration`.
They use the existing authorization pipeline, approval table, event outbox,
authentication, and PostgreSQL tenant/workspace isolation.

See `docs/api/4.75_BACKEND_API.md` for compatibility and connector limitations.
Do not run `mvn spring-boot:run` from this directory.
