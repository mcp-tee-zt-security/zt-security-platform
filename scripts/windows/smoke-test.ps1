$ErrorActionPreference = 'Stop'
Set-Location (Join-Path (Split-Path -Parent $PSScriptRoot) "..")
$base='http://localhost:8080'
$tenant='11111111-1111-1111-1111-111111111111'
$h=@{'X-API-Key'='dev-master-key';'X-Tenant-Id'=$tenant;'Content-Type'='application/json'}
Write-Host 'Health:'; Invoke-RestMethod "$base/v1/health"
Write-Host "`nPolicies:"; (Invoke-RestMethod "$base/v1/policies/all" -Headers $h).Count
Write-Host "`nDecisions:"; (Invoke-RestMethod "$base/v1/security/decisions" -Headers $h).Count
Write-Host "`nIncidents:"; (Invoke-RestMethod "$base/v1/security/incidents" -Headers $h).Count
Write-Host "`nGraph metrics:"; (Invoke-RestMethod "$base/v1/security-graph?windowMinutes=120" -Headers $h).metrics
Write-Host "`nSmoke test passed." -ForegroundColor Green
