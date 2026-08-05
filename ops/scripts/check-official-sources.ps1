[CmdletBinding()]
param(
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$sources = @(
    @{
        id = "BTK-EIMZA-MEVZUAT"
        uri = "https://www.btk.gov.tr/elektronik-imza-mevzuati"
        expected = "Elektronik"
        expectedSha256 = ""
    },
    @{
        id = "ETSI-TS-119-312"
        uri = "https://www.etsi.org/deliver/etsi_ts/119300_119399/119312/02.01.01_60/ts_119312v020101p.pdf"
        expected = ""
        expectedSha256 = "7D8428F6433221FAE87525507EC204F39EF31A1D5547338444708A801ED18F72"
    },
    @{
        id = "OWASP-ASVS"
        uri = "https://owasp.org/www-project-application-security-verification-standard/"
        expected = "5.0.0"
        expectedSha256 = ""
    },
    @{
        id = "NIST-SP-800-61R3"
        uri = "https://csrc.nist.gov/pubs/sp/800/61/r3/final"
        expected = "800-61"
        expectedSha256 = ""
    }
)

$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(30)
$results = @()
$failed = $false
try {
    foreach ($source in $sources) {
        try {
            $bytes = $client.GetByteArrayAsync($source.uri).GetAwaiter().GetResult()
            $sha = [System.Security.Cryptography.SHA256]::Create()
            try {
                $hash = [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace("-", "")
            } finally {
                $sha.Dispose()
            }
            $text = [System.Text.Encoding]::UTF8.GetString($bytes)
            $expectedFound = [string]::IsNullOrWhiteSpace($source.expected) -or
                $text.Contains($source.expected)
            $hashMatches = $null
            if (-not [string]::IsNullOrWhiteSpace($source.expectedSha256)) {
                $hashMatches = $hash -eq $source.expectedSha256
            }
            if (-not $expectedFound -or $hashMatches -eq $false) {
                $failed = $true
            }
            $results += [ordered]@{
                id = $source.id
                uri = $source.uri
                expected = $source.expected
                expectedFound = $expectedFound
                expectedSha256 = $source.expectedSha256
                hashMatches = $hashMatches
                sha256 = $hash
                bytes = $bytes.Length
                checkedAt = (Get-Date).ToUniversalTime().ToString("o")
            }
        } catch {
            $failed = $true
            $results += [ordered]@{
                id = $source.id
                uri = $source.uri
                expected = $source.expected
                expectedFound = $false
                error = $_.Exception.Message
                checkedAt = (Get-Date).ToUniversalTime().ToString("o")
            }
        }
    }
} finally {
    $client.Dispose()
}

$json = $results | ConvertTo-Json -Depth 5
if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
    $json | Set-Content -Encoding utf8 -LiteralPath $OutputPath
}
$json
if ($failed) {
    throw "En az bir resmî kaynak erişilemedi veya beklenen sürüm işareti bulunamadı."
}
