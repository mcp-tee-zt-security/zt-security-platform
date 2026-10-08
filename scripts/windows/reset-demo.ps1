$ErrorActionPreference = 'Stop'
Set-Location (Join-Path (Split-Path -Parent $PSScriptRoot) "..")
Write-Host "This deletes the local demo PostgreSQL volume." -ForegroundColor Yellow
$ok = Read-Host "Type RESET to continue"
if ($ok -ne 'RESET') { exit 1 }
docker compose -f docker/docker-compose.yml down -v
docker compose -f docker/docker-compose.yml up --build -d
