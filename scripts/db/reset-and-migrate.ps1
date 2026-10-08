param(
  [string]$DbUrl = $env:DB_URL,
  [string]$DbUsername = $env:DB_USERNAME,
  [string]$DbPassword = $env:DB_PASSWORD,
  [string]$DatabaseName = "zt"
)
$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($DbUrl)) { $DbUrl = "jdbc:postgresql://localhost:5432/$DatabaseName" }
if ([string]::IsNullOrWhiteSpace($DbUsername)) { $DbUsername = "zt" }
if ([string]::IsNullOrWhiteSpace($DbPassword)) { $DbPassword = "zt" }
if ($DbUrl -notmatch '^jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)') { throw "Unsupported DB_URL. Expected jdbc:postgresql://host[:port]/database" }
$HostName = $Matches[1]; $Port = if ($Matches[2]) { $Matches[2] } else { "5432" }; $TargetDb = $Matches[3]
if (-not (Get-Command psql -ErrorAction SilentlyContinue)) { throw "psql was not found on PATH. Install PostgreSQL client tools first." }
$env:PGPASSWORD = $DbPassword
Write-Host "Resetting PostgreSQL database '$TargetDb' on $HostName`:$Port..."
& psql -h $HostName -p $Port -U $DbUsername -d postgres -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$TargetDb' AND pid <> pg_backend_pid();" | Out-Null
& psql -h $HostName -p $Port -U $DbUsername -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$TargetDb\";" | Out-Null
& psql -h $HostName -p $Port -U $DbUsername -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$TargetDb\";" | Out-Null
Write-Host "Database recreated. Start authorization-api; Flyway will apply V1__initial_schema.sql."
