param(
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$backendRoot = Join-Path $projectRoot 'VinaBookStore'
$frontendRoot = Join-Path $projectRoot 'bookstore-ui'

if (-not (Test-Path $backendRoot) -or -not (Test-Path $frontendRoot)) {
    throw "Không tìm thấy VinaBookStore hoặc bookstore-ui trong $projectRoot"
}

function Import-EnvFile([string]$path) {
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^\s*([^#][^=]*)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

$envCandidates = @(
    (Join-Path $backendRoot '.env.local'),
    $env:VINABOOK_ENV_FILE,
    'D:\book\book\VinaBookStore\.env.local'
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

if (-not $envCandidates) {
    throw "Chưa tìm thấy VinaBookStore/.env.local. Hãy tạo file từ .env.local.example và thêm API key."
}
$envFile = $envCandidates | Select-Object -First 1
Import-EnvFile -path $envFile

if (-not $env:JAVA_HOME -and (Test-Path 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot')) {
    $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
}

$maven = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $maven) {
    $maven = Join-Path $backendRoot 'mvnw.cmd'
}
$settings = Join-Path $projectRoot '..\..\maven-settings-local.xml'
$mavenArgs = @()
if (Test-Path $settings) {
    $mavenArgs += @('-s', (Resolve-Path $settings).Path)
}
$mavenArgs += 'spring-boot:run'

function Test-Endpoint([string]$url) {
    try {
        Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 | Out-Null
        return $true
    } catch {
        return $false
    }
}

if (-not (Test-Endpoint 'http://localhost:8080/api/chat/status')) {
    $backendCommand = "Set-Location -LiteralPath '$backendRoot'; & '$maven' $($mavenArgs -join ' ')"
    Start-Process powershell.exe -WorkingDirectory $backendRoot -ArgumentList @('-NoProfile', '-NoExit', '-Command', $backendCommand) | Out-Null
    Write-Host 'Đang khởi động backend...' -ForegroundColor Cyan
    $backendReady = $false
    1..60 | ForEach-Object {
        Start-Sleep -Seconds 1
        if (Test-Endpoint 'http://localhost:8080/api/chat/status') { $backendReady = $true; break }
    }
    if (-not $backendReady) { throw 'Backend không khởi động trong 60 giây.' }
} else {
    Write-Host 'Backend đã chạy sẵn.' -ForegroundColor DarkGray
}

if (-not (Test-Endpoint 'http://localhost:4200')) {
    $frontendCommand = "Set-Location -LiteralPath '$frontendRoot'; `$env:NODE_OPTIONS='--openssl-legacy-provider'; npm start"
    Start-Process powershell.exe -WorkingDirectory $frontendRoot -ArgumentList @('-NoProfile', '-NoExit', '-Command', $frontendCommand) | Out-Null
    Write-Host 'Đang khởi động frontend...' -ForegroundColor Cyan
    $frontendReady = $false
    1..60 | ForEach-Object {
        Start-Sleep -Seconds 1
        if (Test-Endpoint 'http://localhost:4200') { $frontendReady = $true; break }
    }
    if (-not $frontendReady) { throw 'Frontend không khởi động trong 60 giây.' }
} else {
    Write-Host 'Frontend đã chạy sẵn.' -ForegroundColor DarkGray
}

$status = Invoke-RestMethod 'http://localhost:8080/api/chat/status'
Write-Host "VinaBook đã sẵn sàng: http://localhost:4200 (AI: $($status.provider)/$($status.model))" -ForegroundColor Green
if (-not $NoBrowser) { Start-Process 'http://localhost:4200' }
