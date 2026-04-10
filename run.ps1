param(
    [switch]$SkipBuild,
    [int]$Port = 8080,
    [switch]$EnableHitClaimDiagnostics
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$mavenCandidates = @(
    'mvn.cmd',
    'C:\Users\Nishant Pradhan\.maven\maven-3.9.14\bin\mvn.cmd'
)

$mvn = $null
foreach ($candidate in $mavenCandidates) {
    $command = Get-Command $candidate -ErrorAction SilentlyContinue
    if ($command) {
        $mvn = $command.Source
        break
    }

    if (Test-Path $candidate) {
        $mvn = $candidate
        break
    }
}

if (-not $mvn) {
    throw 'Maven was not found. Install Maven or add mvn.cmd to PATH.'
}

if (-not $SkipBuild) {
    Write-Host 'Building project...' -ForegroundColor Cyan
    & $mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

try {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
} catch {
    $listener = $null
}

if ($listener) {
    $pid = $listener.OwningProcess
    $processName = 'unknown'
    try {
        $process = Get-Process -Id $pid -ErrorAction Stop
        $processName = $process.ProcessName
    } catch {
    }

    Write-Host "Port $Port is already in use by PID $pid ($processName)." -ForegroundColor Yellow
    Write-Host "Stop the process or run with a different port, e.g. ./run.ps1 -Port 8081" -ForegroundColor Yellow
    exit 1
}

Write-Host 'Starting CodeReboot Arena...' -ForegroundColor Green
$mavenRunArgs = @(
    'spring-boot:run',
    "-Dspring-boot.run.arguments=--server.port=$Port"
)

if ($EnableHitClaimDiagnostics) {
    $mavenRunArgs += '-Dspring-boot.run.jvmArguments=-Dcodereboot.diagnostics.hitClaims=true'
    Write-Host 'Hit-claim diagnostics: enabled' -ForegroundColor DarkYellow
}

& $mvn @mavenRunArgs
