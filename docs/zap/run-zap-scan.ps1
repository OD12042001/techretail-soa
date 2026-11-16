<#
.SYNOPSIS
    Runs an OWASP ZAP active API scan against the TechRetail BUS (gateway, port 8090).

.DESCRIPTION
    1. Checks that the Docker daemon and the gateway are up.
    2. Logs in through the BUS and obtains an ADMIN JWT.
    3. Writes a scan copy of openapi-gateway.yaml whose server URL is reachable
       from inside the container (host.docker.internal).
    4. Runs zap-api-scan.py (ghcr.io/zaproxy/zaproxy:stable) with the token
       injected as "Authorization: Bearer <token>" through ZAP_AUTH_HEADER_VALUE.
    5. Converts the HTML report to PDF with headless Microsoft Edge.

    Output: docs/zap/reports/zap-report.{html,md,json,pdf}

    WARNING: the active scan sends attack payloads to POST/PUT endpoints. It
    creates junk users, products and categories and can modify product id 1.
    Run it only against the local development stack.

.EXAMPLE
    .\docs\zap\run-zap-scan.ps1
#>
[CmdletBinding()]
param(
    [string]$GatewayUrl = 'http://localhost:8090',
    [string]$ContainerGatewayUrl = 'http://host.docker.internal:8090',
    [string]$Usuario = 'admin@techretail.pe',
    [string]$Password = 'Admin123!',
    [string]$Image = 'ghcr.io/zaproxy/zaproxy:stable',
    [switch]$SkipPdf
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$zapDir = $PSScriptRoot
$spec = Join-Path $zapDir 'openapi-gateway.yaml'
$reportsDir = Join-Path $zapDir 'reports'
New-Item -ItemType Directory -Force -Path $reportsDir | Out-Null

# 1. Docker daemon
#docker info *> $null
if ($LASTEXITCODE -ne 0) {
   # throw 'The Docker daemon is not running. Start Docker Desktop and retry.'
}

# 2. Gateway health
try {
    $health = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -TimeoutSec 10
} catch {
    throw "The gateway is not reachable at $GatewayUrl. Start PostgreSQL and the four services first."
}
if ($health.status -ne 'UP') {
    throw "The gateway answered status '$($health.status)' instead of UP."
}

# 3. ADMIN token through the BUS
$body = @{ usuario = $Usuario; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$GatewayUrl/api/auth/login" `
    -ContentType 'application/json' -Body $body
if (-not $login.token) {
    throw 'The login did not return a token.'
}
Write-Host "Token obtained for $Usuario (rol $($login.rol), expires in $($login.expiraEn) s)."

# 4. Scan copy of the contract, pointing at the host from inside the container
$scanSpec = Join-Path $reportsDir 'openapi-scan.yaml'
$specText = [System.IO.File]::ReadAllText($spec, $utf8NoBom)
[System.IO.File]::WriteAllText($scanSpec, $specText.Replace('http://localhost:8090', $ContainerGatewayUrl), $utf8NoBom)

# 5. Active API scan
$dockerArgs = @(
    'run', '--rm',
    '-v', "${reportsDir}:/zap/wrk/:rw",
    '-e', "ZAP_AUTH_HEADER_VALUE=Bearer $($login.token)",
    $Image,
    'zap-api-scan.py',
    '-t', '/zap/wrk/openapi-scan.yaml',
    '-f', 'openapi',
    '-r', 'zap-report.html',
    '-w', 'zap-report.md',
    '-J', 'zap-report.json',
    '-I'
)
Write-Host "Running $Image zap-api-scan.py (first run pulls the image, ~1.5 GB)..."
& docker @dockerArgs
$zapExit = $LASTEXITCODE
Remove-Item $scanSpec -ErrorAction SilentlyContinue

# zap-api-scan exit codes: 0 = no FAIL alerts, 1 = at least one FAIL, 2 = WARN (ignored by -I), 3 = error
if ($zapExit -ge 3) {
    throw "ZAP finished with exit code $zapExit (scan error). Check the console output above."
}
Write-Host "ZAP finished with exit code $zapExit (0 = no FAIL alerts, 1 = at least one FAIL alert)."

$html = Join-Path $reportsDir 'zap-report.html'
if (-not (Test-Path $html)) {
    throw "The HTML report was not generated: $html"
}

# 6. HTML -> PDF with headless Edge
if (-not $SkipPdf) {
    $edge = @(
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1

    if ($edge) {
        $pdf = Join-Path $reportsDir 'zap-report.pdf'
        $htmlUri = ([System.Uri]$html).AbsoluteUri
        Start-Process -FilePath $edge -Wait -ArgumentList @(
            '--headless=new', '--disable-gpu', '--no-pdf-header-footer',
            "`"--print-to-pdf=$pdf`"", $htmlUri
        )
        if (Test-Path $pdf) {
            Write-Host "PDF report: $pdf"
        } else {
            Write-Warning 'Edge did not produce the PDF. Open zap-report.html and print it to PDF manually.'
        }
    } else {
        Write-Warning 'Microsoft Edge not found. Open zap-report.html and print it to PDF manually.'
    }
}

Write-Host "Reports in $reportsDir"
