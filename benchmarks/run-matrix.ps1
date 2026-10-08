param(
  [int[]]$Rps = @(1000,10000,50000,100000),
  [string]$Duration = "30s"
)
$ErrorActionPreference = 'Stop'
Write-Host "ZT Security Data Plane benchmark matrix" -ForegroundColor Cyan
Write-Host "Target: P99 < 2ms at each sustained rate. Actual results are environment-dependent." -ForegroundColor Yellow
foreach ($r in $Rps) {
  Write-Host "`n=== ${r} RPS ===" -ForegroundColor Green
  $env:RPS = "$r"
  $env:DURATION = $Duration
  k6 run benchmarks/k6-fast-path.js
  if ($LASTEXITCODE -ne 0) { Write-Warning "Benchmark threshold failed at ${r} RPS" }
}
