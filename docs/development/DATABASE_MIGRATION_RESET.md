# Database Migration Policy — 4.75

4.75 uses Flyway, but the historical V1..V150 chain has been squashed into a single canonical fresh-install migration:

- `apps/authorization-api/src/main/resources/db/migration/V1__initial_schema.sql`

For a **new/development database**, recreate the PostgreSQL database and start the authorization API. Flyway will create `flyway_schema_history` and apply V1.

## Windows PowerShell

```powershell
.\scripts\db\reset-and-migrate.ps1
```

## Linux/macOS

```bash
./scripts/db/reset-and-migrate.sh
```

The reset scripts are intentionally destructive and are for development/test databases only.

## Production

Do **not** drop a production database. Production upgrades must use a normal forward-only Flyway migration strategy from the deployed schema version.
