$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$dashboardJar = Join-Path $PSScriptRoot 'backend/target/dashboard-1.0.0.jar'
if (-not (Test-Path -LiteralPath $dashboardJar)) { & "$PSScriptRoot/build.ps1" }
& java --enable-native-access=ALL-UNNAMED -jar $dashboardJar
if ($LASTEXITCODE -ne 0) { throw '대시보드 실행 실패' }
