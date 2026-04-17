param(
    [int]$Port = 8080,
    [string]$CloudflaredPath = "C:\Program Files (x86)\cloudflared\cloudflared.exe",
    [ValidateSet('http2', 'quic', 'auto')]
    [string]$Protocol = 'auto',
    [switch]$ShowUrlHint
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$cloudflared = $null
$cloudflaredCommand = Get-Command cloudflared -ErrorAction SilentlyContinue
if ($cloudflaredCommand) {
    $cloudflared = $cloudflaredCommand.Source
} elseif (Test-Path $CloudflaredPath) {
    $cloudflared = $CloudflaredPath
} else {
    throw "cloudflared was not found. Install cloudflared CLI or provide -CloudflaredPath."
}

Write-Host "Starting Cloudflare Tunnel for http://localhost:$Port ..." -ForegroundColor Green
if ($ShowUrlHint) {
    Write-Host 'Share the generated https://...trycloudflare.com/login.html URL with your friend.' -ForegroundColor Yellow
}

$tunnelArgs = @('tunnel')
if ($Protocol -ne 'auto') {
    $tunnelArgs += @('--protocol', $Protocol)
}
$tunnelArgs += @('--url', "http://localhost:$Port")

& $cloudflared @tunnelArgs
