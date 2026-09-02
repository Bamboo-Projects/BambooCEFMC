# Schreibt das Manifest der Laufzeitumgebung.
#
# Aufruf:
#   pwsh -File tools/runtime/manifest.ps1
#   pwsh -File tools/runtime/manifest.ps1 -BaseUrl https://…/v1/
#
# Eingang  build/dist/*.tar.gz, pin.properties, patches/
# Ausgang  build/dist/runtime-manifest.json
#
# Das Manifest wird **erzeugt, nicht gepflegt**. Jeder Wert stammt aus einer
# Datei oder aus pin.properties; keine Prüfsumme wird von Hand eingetragen.
# Von Hand gepflegte Prüfsummen sind der Weg, auf dem eine falsche Zahl in eine
# Auslieferung gerät — und geprüft wird sie erst dort, wo niemand mehr
# nachsehen kann.

[CmdletBinding()]
param(
    [string]$BaseUrl = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$runtimeDir = Split-Path -Parent $PSCommandPath
$distDir    = Join-Path $runtimeDir 'build\dist'
$manifest   = Join-Path $distDir 'runtime-manifest.json'

function Read-Pin([string]$path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $path -Encoding utf8) {
        if ($line -match '^\s*(#|$)') { continue }
        $parts = $line -split '=', 2
        $values[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $values
}

if (-not (Test-Path -LiteralPath $distDir)) {
    throw "Kein $distDir. Erst package-runtime.ps1 laufen lassen."
}
$archives = @(Get-ChildItem -LiteralPath $distDir -Filter '*.tar.gz' | Sort-Object Name)
if (-not $archives) { throw "Keine Archive unter $distDir" }

$pin = Read-Pin (Join-Path $runtimeDir 'pin.properties')

# 146.0.10+g8219561+chromium-146.0.7680.179 zerfällt in die CEF-Fassung und die
# Chromium-Fassung. Beide gehören ins Manifest: Wer einen Fehler sucht, sucht
# ihn mal in der einen und mal in der anderen.
if ($pin['cef.version'] -notmatch '^(?<cef>.+)\+chromium-(?<chromium>.+)$') {
    throw "cef.version hat nicht die erwartete Form: $($pin['cef.version'])"
}
$cef      = $Matches['cef']
$chromium = $Matches['chromium']

# Die Liste der Patches gehört hinein: Eine installierte Laufzeitumgebung soll
# sagen können, was in ihr steckt.
$patches = @(Get-ChildItem -LiteralPath (Join-Path $runtimeDir 'patches') -Filter '0*.patch' |
             Sort-Object Name |
             ForEach-Object { $_.BaseName })

$platforms = [ordered]@{}
foreach ($archive in $archives) {
    # Aus fn-runtime-146.0.10-windows-x86_64.tar.gz wird windows-x86_64.
    if ($archive.Name -notmatch '^fn-runtime-.+?-(?<platform>[a-z0-9]+-[a-z0-9_]+)\.tar\.gz$') {
        throw "Archivname passt nicht zum Schema: $($archive.Name)"
    }
    $platforms[$Matches['platform']] = [ordered]@{
        url    = "$BaseUrl$($archive.Name)"
        size   = $archive.Length
        sha256 = (Get-FileHash -LiteralPath $archive.FullName -Algorithm SHA256).Hash.ToLower()
    }
}

$content = [ordered]@{
    runtime   = 1
    cef       = $cef
    chromium  = $chromium
    jcef      = $pin['jcef.commit']
    patches   = $patches
    platforms = $platforms
}

$json = $content | ConvertTo-Json -Depth 5
Set-Content -LiteralPath $manifest -Value $json -Encoding utf8

Write-Host $json
Write-Host ''
Write-Host "Manifest unter $manifest" -ForegroundColor Green
if (-not $BaseUrl) {
    Write-Host 'Hinweis: ohne -BaseUrl steht in "url" nur der Dateiname.' -ForegroundColor Yellow
}
