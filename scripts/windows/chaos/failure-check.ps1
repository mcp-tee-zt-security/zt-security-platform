$ErrorActionPreference='Stop'
$base='http://localhost:8091'
Write-Host '1) baseline' -ForegroundColor Cyan
Invoke-RestMethod "$base/health" | ConvertTo-Json
Write-Host '2) stop worker 1' -ForegroundColor Cyan
docker compose -f docker/docker-compose.yml stop policy-data-plane-1 | Out-Null
Start-Sleep 3
Invoke-RestMethod "$base/health" | ConvertTo-Json
Write-Host '3) restore worker 1' -ForegroundColor Cyan
docker compose -f docker/docker-compose.yml start policy-data-plane-1 | Out-Null
Start-Sleep 3
Write-Host '4) stop control plane (last-known-good test)' -ForegroundColor Cyan
docker compose -f docker/docker-compose.yml stop authorization-api | Out-Null
Start-Sleep 8
Invoke-RestMethod "$base/health" | ConvertTo-Json
Write-Host 'Restore control plane' -ForegroundColor Cyan
docker compose -f docker/docker-compose.yml start authorization-api | Out-Null
