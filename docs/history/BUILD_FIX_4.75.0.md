# 4.75.0 — data-plane healthcheck correction

## Latest Docker readiness correction
The database seeds contain two ACTIVE demo policies. With real PostgreSQL and Redis, the authorization API returned both through /v1/policy-bundles/fast, and both Rust nodes reached HTTP 200 /ready with policyCount=2 and signatureValid=true.

The Docker healthcheck used BusyBox wget against localhost. Rust binds to IPv4 0.0.0.0 by default, while localhost can resolve first to IPv6 ::1. BusyBox wget does not fall back to the IPv4 address after connection refusal. This failure was reproduced against both actual Rust nodes with BusyBox 1.36.1. The same request to 127.0.0.1 succeeded.

Changed all three port-8091 readiness probes (both Rust nodes and the nginx frontend) to http://127.0.0.1:8091/ready. Readiness requirements, signature verification, fail-closed behavior, and database data remain intact. Both Rust nodes also passed a subsequent refresh check. Docker itself is unavailable in the verification environment; the Alpine image was not executed.

Apply the updated docker/docker-compose.yml, then run from the project root:

```powershell
docker compose -f docker/docker-compose.yml up -d --force-recreate policy-data-plane-1 policy-data-plane-2 policy-data-plane
```

No image rebuild is required for this Compose-only change. Do not delete PostgreSQL volumes. If the deployed nodes remain unhealthy, obtain the actual readiness body and healthcheck error:

```powershell
docker compose -f docker/docker-compose.yml exec policy-data-plane-2 wget -S -O- http://127.0.0.1:8091/ready
docker compose -f docker/docker-compose.yml logs --tail=80 policy-data-plane-2
docker inspect --format '{{json .State.Health}}' docker-policy-data-plane-2-1
```

## Previous startup corrections and validation


## Latest validation
- Maven package: BUILD SUCCESS; 20 unit tests passed, zero failures/errors.
- Real PostgreSQL 16.15 and Redis 7.0.15 were used to start the complete AuthorizationApplication from its packaged JAR, including all business beans and JPA repositories.
- Cold startup applied the complete V1 baseline to zt_security_475 successfully.
- Subsequent startup validated the existing new-schema migration and completed normally.
- `/v1/health`: UP.
- Authenticated `/v1/policies`: 200, two demo policies; a second request also succeeded through Redis cache deserialization.
- Authenticated `/v1/tenants`: 200, one demo tenant.
- A legacy public schema with checksum -1331607090 and a sentinel data row was present during startup. Both the old checksum and row remained unchanged.
- Local verification used non-default ports and disabled outbound OTLP metrics export because there is no collector here.
- Actual Docker images and the full write/evaluation workflow were not tested. Prior dashboard production build, Rust release build, three Rust unit tests, and Rust runtime smoke checks remain valid.

## Reported failure and data handling
The reported exception is Flyway's V1 checksum mismatch in the existing public schema. The current baseline belongs to a new schema, `zt_security_475`. Flyway, Hibernate, and the JDBC search path now all use that schema by default.

Existing public-schema data is preserved. The new schema contains the current demo seed data; old records are not copied into it. No reset, drop, checksum rewrite, validation bypass, or Flyway repair is performed. `DB_SCHEMA` can explicitly select a compatible schema.

## Additional runtime corrections
- Fixed the baseline policies.created_by type to VARCHAR(255), matching the Java model and demo seed values.
- Restored SQL comment prefixes lost on wrapped comment lines.
- Corrected nine indexes that referenced nonexistent created_at fields to use their tables' actual timestamp columns.
- Registered RiskScoringProperties explicitly, because it lives outside the application's default component-scan package.
- Configured the S3 client region from AWS_REGION, defaulting to ap-northeast-2, so local startup does not require AWS region discovery. S3 archival remains controlled by the existing enable flag.
- Changed WorkspaceSession's set_config SELECT from jdbc.update to jdbc.queryForObject.
- Added JavaTimeModule to the shared Redis serializer and made CacheConfig use that same RedisCacheConfiguration. Regression tests cover Policy/Instant and RiskEngine.Result/evidence round trips.
- All previous dashboard, Java, OpenTelemetry dependency/runtime, and Rust corrections are included.

## IntelliJ
Extract this ZIP into a fresh directory. Open the root pom.xml, reload Maven, build, and run AuthorizationApplication. Existing DB_URL/DB_USERNAME/DB_PASSWORD and Redis connection settings can be used. The default schema changes automatically to zt_security_475.

## Docker
From the project root:

```powershell
docker compose -f docker/docker-compose.yml build --no-cache
docker compose -f docker/docker-compose.yml up -d
```

These commands preserve database volumes.

## Migration discipline
Treat the new schema's V1 baseline as immutable once installed. Future database changes must be added as V2/V3 migrations.

## Actual smoke results
API_GET_OK /v1/policies count=2
API_GET_OK /v1/policies count=2
API_GET_OK /v1/tenants count=1
FULL_STARTUP_OK: migrated new schema, full Spring application health UP, legacy data/checksum preserved

2026-10-08T19:47:53.730+11:00  INFO 23 --- [zt-authorization-api] [           main] [                                                 ] c.zt.security.AuthorizationApplication   : Started AuthorizationApplication in 12.293 seconds (process running for 12.884)

## Latest data-plane smoke evidence

```text
BUSYBOX_PROBE localhost:18091 exit=1 wget: can't connect to remote host: Connection refused
BUSYBOX_PROBE 127.0.0.1:18091 exit=0 
BUSYBOX_PROBE localhost:18092 exit=1 wget: can't connect to remote host: Connection refused
BUSYBOX_PROBE 127.0.0.1:18092 exit=0 
DATAPLANE_REFRESH_OK 18091
DATAPLANE_REFRESH_OK 18092
```

The reported user database independently shows both expected ACTIVE policies in zt_security_475. Its Docker readiness response has not yet been collected, so the locally reproduced localhost defect is not asserted as the only possible cause of that deployment failure.
