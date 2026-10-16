param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$logs = Join-Path $root '.run-logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

# Load backend settings as literal values; explicit process overrides take priority.
# Never print private settings, including signing keys and AI credentials.
$settingsFile = Join-Path $root 'VinaBookStore/.env.local'
if (Test-Path -LiteralPath $settingsFile) {
    foreach ($settingsLine in Get-Content -LiteralPath $settingsFile -Encoding utf8) {
        if ($settingsLine -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $settingName = $Matches[1]
            $settingValue = $Matches[2].Trim()
            if ($settingValue.Length -ge 2 -and (
                ($settingValue.StartsWith('"') -and $settingValue.EndsWith('"')) -or
                ($settingValue.StartsWith("'") -and $settingValue.EndsWith("'")))) {
                $settingValue = $settingValue.Substring(1, $settingValue.Length - 2)
            }
            if ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($settingName, 'Process'))) {
                [Environment]::SetEnvironmentVariable($settingName, $settingValue, 'Process')
            }
        }
    }
}

function Test-Port([int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        return ($task.Wait(1000) -and $client.Connected)
    } catch { return $false } finally { $client.Dispose() }
}

function Test-Http([string]$Url, [switch]$Json) {
    try {
        $r = Invoke-WebRequest $Url -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -ne 200) { return $false }
        if ($Json) { return ($r.Headers['Content-Type'] -match 'application/json') }
        return $true
    } catch { return $false }
}

function Wait-Service([string]$Url, [System.Diagnostics.Process]$Process, [string]$Log, [switch]$Json) {
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline) {
        if (Test-Http $Url -Json:$Json) { return }
        $Process.Refresh()
        if ($Process.HasExited) { throw "Tien trinh dung. Xem log: $Log" }
        Start-Sleep -Seconds 2
    }
    throw "Qua 5 phut van chua san sang: $Url. Xem log: $Log"
}

function Start-Logged([string]$Directory, [string]$Command, [string]$Name) {
    $log = Join-Path $logs "$Name.log"
    $process = Start-Process $env:ComSpec -WorkingDirectory $Directory -WindowStyle Minimized -PassThru `
        -ArgumentList ('/d /s /c "' + $Command + ' > "' + $log + '" 2>&1"')
    return $process
}

try {
    if (!(Test-Port 3306)) {
        throw 'MySQL chua chay tren cong 3306. Mo Services, Start dich vu MySQL, roi chay lai. Database book can duoc import tu db/01-schema-seed.sql lan dau.'
    }
    if (!(Get-Command node.exe -ErrorAction SilentlyContinue) -or !(Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        throw 'Can cai Node.js/npm (du an da kiem tra voi Node 16).'
    }
    if (!(Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
        $java = Get-Command java.exe -ErrorAction SilentlyContinue
        if (!$java) {
            # Known-good JDK 17 install on this machine; used only if PATH has no java.
            $known = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe'
            if (Test-Path $known) { $java = Get-Item $known } else { throw 'Can cai JDK 17 va them java vao PATH hoac dat JAVA_HOME.' }
        }
        $javaLog = Join-Path $logs 'java-check.log'
        $check = Start-Process $java.Source -ArgumentList '-XshowSettings:properties -version' -NoNewWindow -Wait -PassThru -RedirectStandardError $javaLog
        $settings = Get-Content $javaLog -Raw
        if ($settings -match 'java.home\s*=\s*([^\r\n]+)') { $env:JAVA_HOME = $Matches[1].Trim() }
        if (!(Test-Path "$env:JAVA_HOME\bin\javac.exe")) { throw 'Khong tim thay JDK. Hay dat JAVA_HOME den JDK 17.' }
    }
    if (Test-Port 8080) {
        if (!(Test-Http 'http://localhost:8080/api/products' -Json)) { throw 'Cong 8080 dang bi chiem hoac backend loi. Khong tu dong tat tien trinh khac.' }
        Write-Host 'Backend da chay.'
    } else {
        if (-not $env:JWT_SECRET) { throw 'Hay dat JWT_SECRET trong VinaBookStore/.env.local hoac bien moi truong truoc khi chay backend.' }
        Write-Host 'Dang build sach va khoi dong backend (khong bo qua bien dich test)...'
        $build = (Join-Path $logs 'backend-build').Replace('\', '/')
        $backend = Start-Logged (Join-Path $root 'VinaBookStore') ('mvnw.cmd "-Dvinabook.build.directory=' + $build + '" clean spring-boot:run') 'backend'
        Wait-Service 'http://localhost:8080/api/products' $backend (Join-Path $logs 'backend.log') -Json
    }
    $chatStatus = Invoke-RestMethod -Uri 'http://localhost:8080/api/chat/status' -TimeoutSec 10
    $expectedProvider = if ($env:AI_PROVIDER) { $env:AI_PROVIDER.Trim().ToLowerInvariant() } else { 'local' }
    if ($chatStatus.provider -ne $expectedProvider -or ($expectedProvider -ne 'local' -and -not $chatStatus.aiEnabled)) {
        throw "Backend chatbot dang chay sai cau hinh (hien tai: $($chatStatus.provider), yeu cau: $expectedProvider). Khoi dong lai backend voi VinaBookStore/.env.local; script khong tu tat tien trinh dang chay."
    }
    Write-Host "Chatbot: $($chatStatus.provider) / $($chatStatus.architecture)"
    $ui = Join-Path $root 'bookstore-ui'
    if (Test-Port 4200) {
        if (!(Test-Http 'http://localhost:4200/main.js')) { throw 'Cong 4200 dang bi chiem hoac Angular chua build thanh cong.' }
        Write-Host 'Frontend da chay.'
    } else {
        Push-Location $ui
        try {
            $lock = 'package-lock.json'
            $hasLock = (Test-Path $lock) -and ((Get-Item $lock).Length -gt 2)
            $hash = if ($hasLock) { (Get-FileHash $lock).Hash } else { 'no-lockfile' }
            $hash = $hash + '|' + (Get-FileHash 'package.json').Hash
            $stamp = Join-Path $ui 'node_modules/.vinabook-dependencies'
            if (!(Test-Path $stamp) -or ((Get-Content $stamp -Raw).Trim() -ne $hash)) {
                Write-Host 'Dang dong bo dependency tu package.json...'
                $instLog = Join-Path $logs 'install.log'
                $cmd = 'npm install --no-audit --no-fund'
                $inst = Start-Process $env:ComSpec -WorkingDirectory $ui -WindowStyle Minimized -PassThru `
                    -ArgumentList ('/d /s /c "' + $cmd + ' > "' + $instLog + '" 2>&1"')
                $inst.WaitForExit()
                if ($inst.ExitCode -ne 0) { throw "Cai dependency loi (exit $($inst.ExitCode)). Xem: $instLog" }
                Set-Content $stamp $hash -Encoding ASCII
            }
        } finally { Pop-Location }
        Write-Host 'Dang khoi dong Angular...'
        $frontend = Start-Logged $ui 'npm start -- --host 127.0.0.1 --port 4200' 'storefront'
        Wait-Service 'http://localhost:4200/main.js' $frontend (Join-Path $logs 'storefront.log')
    }
    if (!(Test-Http 'http://localhost:4200/api/products' -Json)) { throw "Frontend khong ket noi duoc API. Xem log trong $logs" }
    Write-Host 'Web san sang: http://localhost:4200/' -ForegroundColor Green
    Write-Host "Log: $logs"
    Write-Host 'Cac dich vu dang chay trong cua so thu nho. Dong cua so dich vu de dung.'
    if (!$NoBrowser) { Start-Process 'http://localhost:4200/' }
    exit 0
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
