param(
    [switch]$SkipBuild
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

Write-Host 'Starting CodeReboot Arena...' -ForegroundColor Green
& $mvn spring-boot:run
