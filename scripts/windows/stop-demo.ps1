$ErrorActionPreference = 'Stop'
Set-Location (Join-Path (Split-Path -Parent $PSScriptRoot) "..")
docker compose -f docker/docker-compose.yml down
