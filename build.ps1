$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
& npm.cmd --prefix frontend ci
if ($LASTEXITCODE -ne 0) { throw '프런트엔드 의존성 설치 실패' }
& npm.cmd --prefix frontend test
if ($LASTEXITCODE -ne 0) { throw '프런트엔드 테스트 실패' }
& npm.cmd --prefix frontend run build
if ($LASTEXITCODE -ne 0) { throw '프런트엔드 빌드 실패' }
& "$PSScriptRoot/backend/mvnw.cmd" -f "$PSScriptRoot/backend/pom.xml" clean verify
if ($LASTEXITCODE -ne 0) { throw '서버 빌드 또는 테스트 실패' }
Write-Host '빌드 완료: backend/target/dashboard-1.0.0.jar'
