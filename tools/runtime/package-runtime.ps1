# Packt die gebauten Artefakte zu einem Laufzeit-Archiv.
#
# Aufruf:
#   pwsh -File tools/runtime/package-runtime.ps1
#   pwsh -File tools/runtime/package-runtime.ps1 -Platform windows-x86_64
#
# Eingang  build/out/<plattform>/
# Ausgang  build/dist/fn-runtime-<cef>-<plattform>.tar.gz
#
# Geprüft wird zweimal: einmal vor dem Packen gegen files-<plattform>.txt, und
# einmal danach am ausgepackten Archiv. Der zweite Durchgang ist der wichtige —
# ein Archiv, das sich nicht auspacken lässt, sieht bis dahin gesund aus.

[CmdletBinding()]
param(
    [string]$Platform = 'windows-x86_64'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$runtimeDir = Split-Path -Parent $PSCommandPath
$outDir     = Join-Path $runtimeDir "build\out\$Platform"
$distDir    = Join-Path $runtimeDir 'build\dist'

function Write-Step([string]$text) {
    Write-Host ''
    Write-Host "== $text" -ForegroundColor Cyan
}

function Read-Pin([string]$path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $path -Encoding utf8) {
        if ($line -match '^\s*(#|$)') { continue }
        $parts = $line -split '=', 2
        $values[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $values
}

# Liest eine Dateiliste: Kommentare und Leerzeilen weg, Rest getrimmt.
function Read-FileList([string]$path) {
    return @(Get-Content -LiteralPath $path -Encoding utf8 |
             Where-Object { $_ -notmatch '^\s*(#|$)' } |
             ForEach-Object { $_.Trim() })
}

# Vergleicht, was da sein soll, mit dem, was da ist. Ein Ordnereintrag endet
# auf '/' und wird als vorhanden gewertet, wenn der Ordner Dateien enthält.
function Assert-Complete([string]$root, [string[]]$expected, [string]$what) {
    $missing = @()
    foreach ($entry in $expected) {
        $path = Join-Path $root $entry.TrimEnd('/')
        if ($entry.EndsWith('/')) {
            if (-not (Test-Path -LiteralPath $path -PathType Container) -or
                -not (Get-ChildItem -LiteralPath $path -File)) {
                $missing += $entry
            }
        }
        elseif (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            $missing += $entry
        }
    }
    if ($missing) { throw "$what unvollständig, es fehlen: $($missing -join ', ')" }
}

if (-not (Test-Path -LiteralPath $outDir)) {
    throw "Keine Artefakte unter $outDir. Erst build-jcef.ps1 laufen lassen."
}
if (-not (Get-Command 'tar' -ErrorAction SilentlyContinue)) {
    throw 'tar fehlt im PATH (seit Windows 10 mitgeliefert)'
}

$pin      = Read-Pin (Join-Path $runtimeDir 'pin.properties')
$expected = Read-FileList (Join-Path $runtimeDir "files-$($Platform -replace '-x86_64$','').txt")

# Die CEF-Fassung trägt drei Teile; für einen Dateinamen taugt der erste.
$cefShort = ($pin['cef.version'] -split '\+')[0]
$archive  = Join-Path $distDir "fn-runtime-$cefShort-$Platform.tar.gz"

Write-Step 'Vollständigkeit vor dem Packen'
Assert-Complete $outDir $expected 'Das Bauergebnis'
Write-Host "  $($expected.Count) Einträge vorhanden" -ForegroundColor Green

Write-Step 'Packen'
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
# -C, damit im Archiv keine Wirtspfade stehen: Wer es auspackt, bekommt die
# Dateien flach und nicht build/out/windows-x86_64/ darum herum.
#
# <b>Und ohne Zeitstempel im gzip-Kopf.</b> Ohne diese Option trägt jedes
# Archiv die Uhrzeit seiner Entstehung, und zweimal Packen ergibt zweimal eine
# andere Prüfsumme. Dann sagt eine geänderte Summe nichts mehr darüber aus, ob
# sich der Inhalt geändert hat — und genau das soll sie sagen.
& tar -czf $archive --options 'gzip:!timestamp' -C $outDir .
if ($LASTEXITCODE -ne 0) { throw "tar fehlgeschlagen (Rückgabewert $LASTEXITCODE)" }
$size = (Get-Item -LiteralPath $archive).Length
Write-Host ("  {0}  {1:N0} Bytes" -f (Split-Path -Leaf $archive), $size) -ForegroundColor Green

Write-Step 'Probeweise auspacken'
$check = Join-Path ([System.IO.Path]::GetTempPath()) ("fn-runtime-probe-" + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $check | Out-Null
try {
    & tar -xzf $archive -C $check
    if ($LASTEXITCODE -ne 0) { throw "Das Archiv lässt sich nicht auspacken (Rückgabewert $LASTEXITCODE)" }
    Assert-Complete $check $expected 'Das ausgepackte Archiv'

    $before = (Get-ChildItem -LiteralPath $outDir -Recurse -File).Count
    $after  = (Get-ChildItem -LiteralPath $check  -Recurse -File).Count
    if ($before -ne $after) {
        throw "Im Archiv liegen $after Dateien, im Bauergebnis $before"
    }
    Write-Host "  $after Dateien, unverändert wieder da" -ForegroundColor Green
}
finally {
    Remove-Item -LiteralPath $check -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Step 'Prüfsumme'
# <b>Die Prüfsumme entsteht hier und nicht beim Hochladen.</b> Sie soll das
# Archiv beschreiben, das gerade geprüft wurde — wer sie später über eine
# hochgeladene Datei bildet, prüft nur, dass die Übertragung heil war, und
# nicht, dass die Datei die richtige ist.
$hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
$hashFile = "$archive.sha256"
# Format wie sha256sum: Prüfsumme, zwei Leerzeichen, Dateiname. Damit lässt
# sich die Datei auch von Hand prüfen, ohne dieses Skript.
Set-Content -LiteralPath $hashFile -Encoding ascii -NoNewline `
    -Value "$hash  $(Split-Path -Leaf $archive)`n"
Write-Host "  $hash" -ForegroundColor Green

Write-Step 'dist.properties nachziehen'
# <b>Nur den eigenen Plattform-Schlüssel ersetzen.</b> Die Datei trägt je
# Plattform eigene Zeilen; wer sie neu schreibt, löscht mit dem Windows-Lauf
# den Eintrag, den ein Linux-Lauf hinterlassen hat.
$distProps = Join-Path $runtimeDir 'dist.properties'
$lines = [System.Collections.Generic.List[string]](Get-Content -LiteralPath $distProps -Encoding utf8)
$vorher = ($lines | Where-Object { $_ -like "runtime.$Platform.sha256=*" }) -replace '.*=', ''

function Set-Key([System.Collections.Generic.List[string]]$lines, [string]$key, [string]$value) {
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -like "$key=*") { $lines[$i] = "$key=$value"; return }
    }
    $lines.Add("$key=$value")
}

Set-Key $lines 'runtime.version' $cefShort
Set-Key $lines "runtime.$Platform.archive" (Split-Path -Leaf $archive)
Set-Key $lines "runtime.$Platform.sha256" $hash
Set-Content -LiteralPath $distProps -Encoding utf8 -Value $lines

if ($vorher -and $vorher -ne $hash) {
    Write-Host '  Die Prüfsumme hat sich geändert — dist.properties committen!' -ForegroundColor Yellow
    Write-Host "  vorher  $vorher"
    Write-Host "  jetzt   $hash"
} else {
    Write-Host '  unverändert' -ForegroundColor Green
}

Write-Step 'Was hochzuladen ist'
Write-Host "  $(Split-Path -Leaf $archive)"
Write-Host "  $(Split-Path -Leaf $hashFile)"
Write-Host ''
Write-Host '  Ziel: R2-Bucket factorynetwork, flach im Wurzelverzeichnis.'
Write-Host '  Der Client holt beide unter derselben Adresse und prüft die'
Write-Host '  Summe, bevor er auspackt.'

Write-Host ''
Write-Host "Archiv unter $archive" -ForegroundColor Green
