[CmdletBinding()]
param(
    [string]$ApiBaseUrl = "http://localhost:8080",
    [string]$AgentJar,
    [string]$AgentConfig
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $AgentJar) {
    $AgentJar = Join-Path $repoRoot "smartcard-agent\target\smartcard-agent-0.1.0-SNAPSHOT-exec.jar"
}
if (-not $AgentConfig) {
    $AgentConfig = Join-Path $repoRoot "agent-local.yml"
}
$apiUri = [Uri]$ApiBaseUrl
if ($apiUri.Host -notin @("localhost", "127.0.0.1", "::1")) {
    throw "Bu yardımcı yalnız localhost API ile local test için kullanılabilir."
}
if (-not (Test-Path -LiteralPath $AgentJar)) {
    throw "Agent JAR bulunamadı: $AgentJar. Önce Maven package komutunu çalıştırın."
}
if (-not (Test-Path -LiteralPath $AgentConfig)) {
    Copy-Item -LiteralPath (Join-Path $repoRoot "agent-local.example.yml") -Destination $AgentConfig
}

$health = Invoke-RestMethod -Uri "$ApiBaseUrl/actuator/health" -TimeoutSec 10
if ($health.status -ne "UP") {
    throw "Signature API hazır değil: $ApiBaseUrl"
}
$manifestKey = Invoke-RestMethod `
    -Uri "$ApiBaseUrl/api/v1/signing-configuration/manifest-key" `
    -TimeoutSec 10
if ($manifestKey.algorithm -ne "Ed25519" -or [string]::IsNullOrWhiteSpace($manifestKey.publicKey)) {
    throw "API geçerli Ed25519 manifest public key döndürmedi."
}

$previousManifestKey = $env:EIMZA_AGENT_MANIFEST_PUBLIC_KEY
$previousOrigin = $env:EIMZA_AGENT_ALLOWED_ORIGIN
$previousAdditionalLocation = $env:SPRING_CONFIG_ADDITIONAL_LOCATION
try {
    $env:EIMZA_AGENT_MANIFEST_PUBLIC_KEY = $manifestKey.publicKey
    $env:EIMZA_AGENT_ALLOWED_ORIGIN = $apiUri.GetLeftPart([UriPartial]::Authority)
    $normalizedConfig = ([IO.Path]::GetFullPath($AgentConfig)).Replace("\", "/")
    $env:SPRING_CONFIG_ADDITIONAL_LOCATION = "file:$normalizedConfig"
    Push-Location $repoRoot
    try {
        & java -jar $AgentJar
        if ($LASTEXITCODE -ne 0) {
            throw "Smart Card Agent $LASTEXITCODE çıkış koduyla kapandı."
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:EIMZA_AGENT_MANIFEST_PUBLIC_KEY = $previousManifestKey
    $env:EIMZA_AGENT_ALLOWED_ORIGIN = $previousOrigin
    $env:SPRING_CONFIG_ADDITIONAL_LOCATION = $previousAdditionalLocation
}