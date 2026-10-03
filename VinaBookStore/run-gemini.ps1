param(
	[switch]$Check
)

$ErrorActionPreference = 'Stop'
$backendDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $backendDir '.env.local'
$exampleFile = Join-Path $backendDir '.env.local.example'

if (-not (Test-Path -LiteralPath $envFile)) {
	Copy-Item -LiteralPath $exampleFile -Destination $envFile
	Write-Host "Da tao $envFile"
	Write-Host 'Hay dan khoa Gemini vao GEMINI_API_KEY_1, _2, _3 roi chay lai run-gemini.cmd.'
	exit 1
}

foreach ($rawLine in Get-Content -LiteralPath $envFile -Encoding utf8) {
	$line = $rawLine.Trim()
	if (-not $line -or $line.StartsWith('#')) {
		continue
	}
	$separator = $line.IndexOf('=')
	if ($separator -le 0) {
		continue
	}
	$name = $line.Substring(0, $separator).Trim()
	$value = $line.Substring($separator + 1).Trim()
	if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
		($value.StartsWith("'") -and $value.EndsWith("'"))) {
		$value = $value.Substring(1, $value.Length - 2)
	}
	[Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

$geminiKeys = @()
foreach ($candidate in @($env:GEMINI_API_KEY_1, $env:GEMINI_API_KEY_2, $env:GEMINI_API_KEY_3)) {
	if ($null -eq $candidate) { continue }
	$key = $candidate.Trim()
	if ($key -and $key -ne 'PASTE_GEMINI_API_KEY_HERE' -and $geminiKeys -cnotcontains $key) {
		$geminiKeys += $key
	}
}
if ($geminiKeys.Count -eq 0) {
	$key = if ($env:AI_API_KEY) { $env:AI_API_KEY.Trim() } else { '' }
	if ($key -and $key -ne 'PASTE_GEMINI_API_KEY_HERE') { $geminiKeys += $key }
}
if ($geminiKeys.Count -eq 0) {
	Write-Host "Chua co khoa Gemini trong $envFile" -ForegroundColor Yellow
	Write-Host 'Mo file, dien it nhat mot trong ba dong GEMINI_API_KEY_1, _2, _3 roi chay lai.'
	exit 1
}

if (-not $env:AI_PROVIDER) { $env:AI_PROVIDER = 'gemini' }
if (-not $env:AI_MODEL) { $env:AI_MODEL = 'gemini-3.1-flash-lite' }

$modelId = [Uri]::EscapeDataString($env:AI_MODEL)
$generation = @{ maxOutputTokens = 512 }
if ($env:AI_MODEL.StartsWith('gemma-4-')) {
	$generation.thinkingConfig = @{ thinkingLevel = 'minimal' }
}
$validationBody = @{
	contents = @(@{
		role = 'user'
		parts = @(@{ text = 'Reply with OK.' })
	})
	generationConfig = $generation
} | ConvertTo-Json -Depth 8
$validated = $false
$temporaryFailure = 0

$invalidKeys = 0
if ($Check) {
for ($keyIndex = 0; $keyIndex -lt $geminiKeys.Count -and -not $validated; $keyIndex++) {
	for ($attempt = 0; $attempt -lt 3; $attempt++) {
		try {
			$response = Invoke-RestMethod `
				-Method Post `
				-Uri "https://generativelanguage.googleapis.com/v1beta/models/$modelId`:generateContent" `
				-Headers @{ 'x-goog-api-key' = $geminiKeys[$keyIndex] } `
				-ContentType 'application/json; charset=utf-8' `
				-Body $validationBody `
				-TimeoutSec 15
			$textParts = @($response.candidates[0].content.parts | Where-Object {
				$_.text -and -not $_.thought
			} | ForEach-Object { $_.text })
			if (-not ($textParts -join '').Trim()) {
				$temporaryFailure = 'empty response'
				break
			}
			$validated = $true
			break
		} catch {
			$status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
			if ($status -in @(408, 500, 502, 503, 504) -and $attempt -lt 2) {
				Start-Sleep -Seconds (1 -shl $attempt)
				continue
			}
			if ($status -in @(0, 408, 429, 500, 502, 503, 504)) {
				$temporaryFailure = $status
				break
			}
			$invalidKey = $status -eq 400 -and $_.ErrorDetails.Message -match '"reason"\s*:\s*"API_KEY_INVALID"'
			if ($invalidKey -or $status -in @(401, 402, 403)) {
				$invalidKeys++
				break
			}
			throw "Gemini model $($env:AI_MODEL) khong tao duoc noi dung (HTTP $status). Hay kiem tra model va cau hinh."
		}
	}
}

if ($validated) {
	Write-Host "Da xac thuc Gemini: model=$($env:AI_MODEL), key thu $($keyIndex)/$($geminiKeys.Count) hop le." -ForegroundColor Green
} else {
	if ($invalidKeys -eq $geminiKeys.Count) { throw 'Tat ca khoa Gemini deu bi tu choi. Hay kiem tra lai.' }
	Write-Warning "Gemini model $($env:AI_MODEL) tam thoi khong tra loi hop le ($temporaryFailure). Backend van khoi dong; chatbot se dung du lieu cua hang de tra loi khi AI gap loi."
}
if ($validated) { exit 0 } else { exit 2 }
}

Write-Host "Da nap $($geminiKeys.Count) khoa Gemini; chatbot se tu chuyen khoa khi can." -ForegroundColor Green

$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
	Select-Object -First 1
if ($listener) {
	$running = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)"
	if (-not $running.CommandLine -or $running.CommandLine -notlike "*$backendDir*") {
		throw "Cong 8080 dang duoc process khac su dung (PID $($listener.OwningProcess))."
	}
	Write-Host 'Dang dung backend VinaBook cu de nap cau hinh Gemini moi...'
	Stop-Process -Id $listener.OwningProcess -Force
	Start-Sleep -Seconds 1
}

$preferredJava = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath $preferredJava)) {
	$env:JAVA_HOME = $preferredJava
}

Write-Host "Dang chay VinaBook backend voi $($env:AI_MODEL)..." -ForegroundColor Green
Set-Location -LiteralPath $backendDir
$installedMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($installedMaven -and $env:USERPROFILE) {
	$repository = Join-Path $env:USERPROFILE '.m2\repository'
	& $installedMaven.Source "-Dmaven.repo.local=$repository" "-Dvinabook.build.directory=$backendDir\target-runtime" spring-boot:run
} else {
	& .\mvnw.cmd spring-boot:run
}
exit $LASTEXITCODE
