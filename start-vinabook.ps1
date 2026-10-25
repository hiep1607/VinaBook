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
        $accept = if ($Json) { 'application/json' } else { 'text/html, */*' }
        $probeUrl = $Url -replace '^http://localhost:', 'http://127.0.0.1:'
        $r = Invoke-WebRequest $probeUrl -UseBasicParsing -TimeoutSec 10 -Headers @{ Accept = $accept }
        if ($r.StatusCode -ne 200) { return $false }
        if ($Json) { return ($r.Headers['Content-Type'] -match 'application/json') }
        return $true
    } catch { return $false }
}

function Wait-Service([string]$Url, [System.Diagnostics.Process]$Process, [string]$Log, [switch]$Json, [int]$TimeoutSeconds = 300) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Http $Url -Json:$Json) { return }
        if ($null -ne $Process) {
            $Process.Refresh()
            if ($Process.HasExited) { throw "Tien trinh dung. Xem log: $Log" }
        }
        Start-Sleep -Seconds 2
    }
    throw "Qua $TimeoutSeconds giay van chua san sang: $Url. Xem log: $Log"
}

function Get-DependencyHash([string]$Path) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    $stream = [IO.File]::OpenRead($Path)
    try { return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '') }
    finally { $stream.Dispose(); $algorithm.Dispose() }
}
function Save-DependencyDirectory([string]$Directory, [string]$Name) {
    $workspacePrefix = [IO.Path]::GetFullPath($root).TrimEnd('\') + '\'
    $modules = [IO.Path]::GetFullPath((Join-Path $Directory 'node_modules'))
    $backup = [IO.Path]::GetFullPath((Join-Path $logs ($Name + '-dependencies-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6))))
    foreach ($targetPath in @($modules, $backup)) {
        if (!$targetPath.StartsWith($workspacePrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Duong dan dependency nam ngoai thu muc du an; dung thao tac.'
        }
    }
    if (Test-Path -LiteralPath $modules) {
        # npm ci deletes node_modules in place. Windows may keep individual files
        # open, so preserve the directory before installing into a fresh location.
        try {
            [IO.Directory]::Move($modules, $backup)
        } catch {
            throw "Windows dang giu thu vien $Name. Thu muc cu van duoc giu nguyen. Dong trinh build/kiem thu cua du an roi chay lai. Chi tiet: $($_.Exception.Message)"
        }
        return $backup
    }
    return $null
}

function Sync-Dependencies([string]$Directory, [string]$Name, [switch]$LegacyPeerDeps) {
    $lock = Join-Path $Directory 'package-lock.json'
    $hasLock = (Test-Path -LiteralPath $lock) -and ((Get-Item -LiteralPath $lock).Length -gt 2)
    $baseHash = if ($hasLock) { (Get-DependencyHash $lock) } else { 'no-lockfile' }
    $baseHash += '|' + (Get-DependencyHash (Join-Path $Directory 'package.json'))
    $hash = $baseHash + '|legacy=' + $LegacyPeerDeps.IsPresent
    $stamp = Join-Path $Directory 'node_modules/.vinabook-dependencies'
    $cli = Join-Path $Directory 'node_modules/.bin/ng.cmd'
    $savedHash = if (Test-Path -LiteralPath $stamp) { (Get-Content -LiteralPath $stamp -Raw).Trim() } else { '' }
    if ((Test-Path -LiteralPath $cli) -and ($savedHash -eq $hash -or $savedHash -eq $baseHash)) {
        # Upgrade the previous launcher marker without reinstalling unchanged packages.
        if ($savedHash -ne $hash) { Set-Content -LiteralPath $stamp -Value $hash -Encoding ASCII }
        return
    }
    Write-Host "Dang dong bo dependency $Name..."
    $backup = Save-DependencyDirectory $Directory $Name
    $command = if ($hasLock) { 'npm ci --no-audit --no-fund' } else { 'npm install --no-audit --no-fund' }
    if ($LegacyPeerDeps) { $command += ' --legacy-peer-deps' }
    $install = Start-Logged $Directory $command ($Name + '-install')
    $install.WaitForExit()
    if ($install.ExitCode -ne 0) {
        if ($backup) {
            $failed = Save-DependencyDirectory $Directory ($Name + '-failed')
            [IO.Directory]::Move($backup, (Join-Path $Directory 'node_modules'))
        }
        throw "Cai dependency $Name loi (exit $($install.ExitCode)). Thu vien cu duoc giu lai. Xem: $(Join-Path $logs ($Name + '-install.log'))"
    }
    Set-Content -LiteralPath $stamp -Value $hash -Encoding ASCII
}

function Start-Logged([string]$Directory, [string]$Command, [string]$Name) {
    $log = Join-Path $logs "$Name.log"
    $process = Start-Process $env:ComSpec -WorkingDirectory $Directory -WindowStyle Hidden -PassThru `
        -ArgumentList ('/d /s /c "' + $Command + ' > "' + $log + '" 2>&1"')
    return $process
}

$launcherLock = $null
try {
    try {
        $launcherLock = [IO.File]::Open((Join-Path $logs 'launcher.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch [IO.IOException] {
        throw 'Mot lan khoi dong VinaBook khac dang chay. Cho lan do hoan tat; khong can bam lai.'
    }
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
        $javaPath = if ($java -is [System.IO.FileInfo]) { $java.FullName } else { $java.Source }
        $check = Start-Process $javaPath -ArgumentList '-XshowSettings:properties -version' -WindowStyle Hidden -Wait -PassThru -RedirectStandardError $javaLog
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
        $build = (Join-Path $root 'VinaBookStore/target-runtime').Replace('\', '/')
        $backend = Start-Logged (Join-Path $root 'VinaBookStore') ('mvnw.cmd "-Dvinabook.build.directory=' + $build + '" clean spring-boot:run') 'backend'
        Wait-Service 'http://localhost:8080/api/products' $backend (Join-Path $logs 'backend.log') -Json
    }
    $chatStatus = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/chat/status' -TimeoutSec 10
    $expectedProvider = if ($env:AI_PROVIDER) { $env:AI_PROVIDER.Trim().ToLowerInvariant() } else { 'local' }
    if ($chatStatus.provider -ne $expectedProvider -or ($expectedProvider -ne 'local' -and -not $chatStatus.aiEnabled)) {
        throw "Backend chatbot dang chay sai cau hinh (hien tai: $($chatStatus.provider), yeu cau: $expectedProvider). Khoi dong lai backend voi VinaBookStore/.env.local; script khong tu tat tien trinh dang chay."
    }
    Write-Host "Chatbot: $($chatStatus.provider) / $($chatStatus.architecture)"
    $apps = @(
        @{ Name = 'storefront'; Folder = 'bookstore-ui'; Port = 4200; LegacyPeerDeps = $false; Route = '/' },
        @{ Name = 'admin'; Folder = 'angular-admin'; Port = 4300; LegacyPeerDeps = $true; Route = '/login' }
    )
    $started = @()
    foreach ($app in $apps) {
        $bundle = "http://localhost:$($app.Port)/main.js"
        if (Test-Port $app.Port) {
            if (!(Test-Http $bundle)) { throw "Cong $($app.Port) dang bi chiem hoac Angular $($app.Name) chua build thanh cong." }
            Write-Host "$($app.Name) da chay."
        } else {
            $directory = Join-Path $root $app.Folder
            Sync-Dependencies $directory $app.Name -LegacyPeerDeps:$app.LegacyPeerDeps
            Write-Host "Dang khoi dong $($app.Name) tren cong $($app.Port)..."
            $process = Start-Logged $directory ("npm start -- --host 127.0.0.1 --port " + $app.Port) $app.Name
            $started += @{ Url = $bundle; Process = $process; Log = (Join-Path $logs ($app.Name + '.log')) }
        }
    }
    foreach ($app in $started) { Wait-Service $app.Url $app.Process $app.Log }
    Wait-Service 'http://localhost:4200/api/products' $null (Join-Path $logs 'storefront.log') -Json -TimeoutSeconds 45
    Wait-Service 'http://localhost:4300/login' $null (Join-Path $logs 'admin.log') -TimeoutSeconds 45
    Write-Host 'Cua hang san sang: http://localhost:4200/' -ForegroundColor Green
    Write-Host 'Quan tri san sang: http://localhost:4300/login' -ForegroundColor Green
    Write-Host "Log: $logs"
    Write-Host 'Backend, cua hang va quan tri dang chay nen. Chay lai lenh nay se dung lai cac dich vu da san sang.'
    if (!$NoBrowser) {
        foreach ($app in $apps) { Start-Process ("http://localhost:" + $app.Port + $app.Route) }
    }
    exit 0
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    if ($null -ne $launcherLock) { $launcherLock.Dispose() }
}
