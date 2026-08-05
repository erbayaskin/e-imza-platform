[CmdletBinding()]
param(
    [string]$ArtifactVersion = "0.1.0-SNAPSHOT",
    [string]$PackageVersion = "0.1.0",
    [Parameter(Mandatory = $true)]
    [string]$SigningThumbprint,
    [string]$TimestampUrl = "http://timestamp.digicert.com",
    [string]$OutputRoot = ""
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $workspace "dist/windows-agent"
}
$destination = Join-Path $OutputRoot $PackageVersion
if (Test-Path -LiteralPath $destination) {
    throw "Çıktı dizini zaten var; mevcut paket üzerine yazılmayacak: $destination"
}

$jarName = "smartcard-agent-$ArtifactVersion.jar"
$jarPath = Join-Path $workspace "smartcard-agent/target/$jarName"

Push-Location $workspace
try {
    & mvn --batch-mode --no-transfer-progress -pl smartcard-agent -am clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven paketi üretilemedi."
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Ajan JAR dosyası bulunamadı: $jarPath"
}
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "Java 21 jpackage PATH üzerinde bulunamadı."
}
if (-not (Get-Command signtool -ErrorAction SilentlyContinue)) {
    throw "Windows SDK signtool PATH üzerinde bulunamadı."
}

New-Item -ItemType Directory -Path $destination | Out-Null
& jpackage `
    --type msi `
    --name "EImzaAgent" `
    --description "E-İmza akıllı kart yerel aracısı" `
    --vendor "KURUM_ADI_GUNCELLENMELI" `
    --app-version $PackageVersion `
    --input (Split-Path $jarPath -Parent) `
    --main-jar $jarName `
    --dest $destination `
    --win-menu `
    --win-shortcut `
    --win-per-user-install `
    --win-upgrade-uuid "19e7b4ef-a877-4c23-a2a8-065a70d303d2" `
    --java-options "-Dspring.config.additional-location=file:C:/ProgramData/EImza/agent/"
if ($LASTEXITCODE -ne 0) {
    throw "jpackage MSI üretimi başarısız."
}

$msiFiles = @(Get-ChildItem -LiteralPath $destination -Filter "*.msi")
if ($msiFiles.Count -ne 1) {
    throw "Tam olarak bir MSI bekleniyordu; bulunan: $($msiFiles.Count)"
}
$msi = $msiFiles[0]
& signtool sign /sha1 $SigningThumbprint /fd SHA256 /tr $TimestampUrl /td SHA256 $msi.FullName
if ($LASTEXITCODE -ne 0) {
    throw "MSI kod imzası başarısız."
}
& signtool verify /pa /all $msi.FullName
if ($LASTEXITCODE -ne 0) {
    throw "MSI kod imzası doğrulanamadı."
}

$manifest = [ordered]@{
    file = $msi.Name
    version = $PackageVersion
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $msi.FullName).Hash
    signedAt = (Get-Date).ToUniversalTime().ToString("o")
    upgradeUuid = "19e7b4ef-a877-4c23-a2a8-065a70d303d2"
}
$manifestPath = Join-Path $destination "release-manifest.json"
$manifest | ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $manifestPath
Write-Output "İmzalı MSI ve manifest üretildi: $destination"
