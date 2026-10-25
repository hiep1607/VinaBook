$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($PSScriptRoot).TrimEnd('\')
$logs = Join-Path $root '.run-logs'

function Test-VinaBookProcess($Process) {
    $command = ([string]$Process.CommandLine).Replace('/', '\')
    $name = ([string]$Process.Name).ToLowerInvariant()
    $isBackend = $command.IndexOf(($root + '\VinaBookStore\'), [StringComparison]::OrdinalIgnoreCase) -ge 0
    $isFrontend = $command.IndexOf(($root + '\bookstore-ui\'), [StringComparison]::OrdinalIgnoreCase) -ge 0 -or
                  $command.IndexOf(($root + '\angular-admin\'), [StringComparison]::OrdinalIgnoreCase) -ge 0
    if ($name -eq 'node.exe') {
        return ($isFrontend -and $command -match '@angular\\cli\\bin\\ng(?:\.js)?["\s]' -and $command -match '\bserve\b')
    }
    if ($name -in @('java.exe', 'javaw.exe')) {
        return ($isBackend -and $command -match 'vn\.fs\.VinaBookApplication|VinaBook[^"\s]*\.jar|spring-boot:run')
    }
    if ($name -in @('powershell.exe', 'pwsh.exe')) {
        $startFile = [Regex]::Escape((Join-Path $root 'start-vinabook.ps1'))
        return ($command -notmatch '\s-Command\s' -and $command -match ('(?i)\s-File\s+"?' + $startFile + '(?:"|\s|$)'))
    }
    if ($name -eq 'cmd.exe') {
        foreach ($service in @('backend', 'storefront', 'admin')) {
            if ($command.IndexOf((Join-Path $logs ($service + '.log')), [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
                $command -match 'npm start|mvnw\.cmd.+spring-boot:run') { return $true }
        }
    }
    return $false
}

try {
    $processes = @(Get-CimInstance Win32_Process)
    $owned = New-Object 'System.Collections.Generic.HashSet[int]'
    foreach ($process in $processes) {
        if ($process.ProcessId -ne $PID -and (Test-VinaBookProcess $process)) { [void]$owned.Add([int]$process.ProcessId) }
    }
    # Include build workers and npm/Maven children of verified project processes.
    # Browsers, MySQL and unrelated application processes are never selected.
    do {
        $added = $false
        foreach ($process in $processes) {
            if ($process.ProcessId -ne $PID -and $owned.Contains([int]$process.ParentProcessId) -and
                $process.Name -in @('node.exe', 'java.exe', 'javaw.exe', 'cmd.exe', 'powershell.exe', 'pwsh.exe')) {
                if ($owned.Add([int]$process.ProcessId)) { $added = $true }
            }
        }
    } while ($added)
    # Stop launchers first, so an in-progress startup cannot create new servers.
    $selected = @($processes | Where-Object { $owned.Contains([int]$_.ProcessId) } |
        Sort-Object @{ Expression = { if ($_.Name -in @('powershell.exe', 'pwsh.exe')) { 0 } elseif ($_.Name -eq 'cmd.exe') { 1 } else { 2 } } })
    foreach ($process in $selected) {
        $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.ProcessId)"
        if ($current -and $current.CreationDate -eq $process.CreationDate) {
            try {
                Stop-Process -Id $process.ProcessId -ErrorAction Stop
                Write-Host "Da tat $($process.Name) (PID $($process.ProcessId))."
            } catch {
                if (Get-Process -Id $process.ProcessId -ErrorAction SilentlyContinue) { throw }
            }
        }
    }
    $remaining = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in 4200, 4300, 8080 })
    foreach ($connection in $remaining) {
        $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($connection.OwningProcess)"
        if ($owner -and (Test-VinaBookProcess $owner)) { throw "VinaBook van dang chay tren cong $($connection.LocalPort). Hay thu lai." }
        Write-Host "Cong $($connection.LocalPort) thuoc ung dung khac; giu nguyen."
    }
    Write-Host 'VinaBook local da tat (backend, cua hang va quan tri).' -ForegroundColor Green
    Write-Host 'MySQL va web Azure van duoc giu nguyen. Mo lai bang start-vinabook.cmd.'
    exit 0
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
