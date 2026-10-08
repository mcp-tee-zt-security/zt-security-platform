$ErrorActionPreference = 'Stop'
Set-Location (Join-Path (Split-Path -Parent $PSScriptRoot) "..")
Write-Host "[ZT Security 1.3] Starting Docker stack..." -ForegroundColor Cyan
docker compose -f docker/docker-compose.yml up --build -d
Write-Host "Waiting for API..." -ForegroundColor Yellow
for ($i=0; $i -lt 30; $i++) {
  try { $r = Invoke-RestMethod http://localhost:8080/v1/health; if ($r.status -eq 'UP') { break } } catch {}
  Start-Sleep -Seconds 3
}
Write-Host "Dashboard: http://localhost:3000" -ForegroundColor Green
Write-Host "Swagger:   http://localhost:8080/swagger-ui.html" -ForegroundColor Green
Write-Host "Health:    http://localhost:8080/v1/health" -ForegroundColor Green
