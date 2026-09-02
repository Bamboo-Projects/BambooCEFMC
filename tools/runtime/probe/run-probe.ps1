# Übersetzt und startet die Standalone-Proben.
#
# Aufruf:
#   pwsh -File tools/runtime/probe/run-probe.ps1                    A4a, Takt
#   pwsh -File tools/runtime/probe/run-probe.ps1 -Seconds 30
#   pwsh -File tools/runtime/probe/run-probe.ps1 -Main KeyProbe     A4b, Tastatur
#   pwsh -File tools/runtime/probe/run-probe.ps1 -CompileOnly
#
# Die Probe läuft gegen die gebaute Laufzeitumgebung unter build/out und
# braucht kein Minecraft. Nach dem Lauf zählt das Skript die verbliebenen
# jcef_helper-Prozesse — der eine Messwert, der im Proof-of-Concept nicht
# stimmte (acht statt null).

[CmdletBinding()]
param(
    [string]$Main = 'Probe',
    [string]$Url,
    [int]$Seconds = 20,
    [int]$Width = 1920,
    [int]$Height = 1080,
    [switch]$CompileOnly,
    [ValidateSet('worker', 'main', 'awt', 'edt')]
    [string]$ThreadMode = 'worker',
    [string]$CefLog = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$probeDir   = Split-Path -Parent $PSCommandPath
$runtimeDir = Split-Path -Parent $probeDir
$outDir     = Join-Path $runtimeDir 'build\out\windows-x86_64'
$classesDir = Join-Path $runtimeDir 'build\probe-classes'
$jar        = Join-Path $outDir 'jcef.jar'

if (-not (Test-Path -LiteralPath $jar)) {
    throw "Keine Laufzeitumgebung unter $outDir. Erst build-jcef.ps1 laufen lassen."
}

if (-not $Url) {
    # Zur Takt-Probe eine Seite, die sich dauernd ändert: Ohne Änderung malt
    # Chromium nicht neu, und der gemessene Abstand wäre nicht der der Bildrate,
    # sondern der der Langeweile. Zum Prüfstand die Seite, die mitschreibt.
    $page = if ($Main -eq 'KeyProbe') { 'probe-keys.html' } else { 'probe-takt.html' }
    $Url = 'file:///' + ((Join-Path $probeDir $page) -replace '\\', '/')
}

Write-Host ''
Write-Host "== Übersetzen" -ForegroundColor Cyan
if (Test-Path -LiteralPath $classesDir) { Remove-Item -LiteralPath $classesDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null

$sources = @(Get-ChildItem -LiteralPath $probeDir -Filter '*.java' |
             Where-Object { $_.Name -ne 'ScanProbe.java' } |   # braucht LWJGL
             ForEach-Object { $_.FullName })
& javac -nowarn -encoding UTF-8 -cp $jar -d $classesDir @sources
if ($LASTEXITCODE -ne 0) { throw "javac fehlgeschlagen (Rückgabewert $LASTEXITCODE)" }
Write-Host "  $($sources.Count) Dateien übersetzt" -ForegroundColor Green

if ($CompileOnly) { return }

$before = @(Get-Process jcef_helper -ErrorAction SilentlyContinue)
if ($before) {
    throw "Es laufen schon $($before.Count) jcef_helper-Prozesse. Erst aufräumen, sonst zählt die Probe fremde mit."
}

Write-Host ''
Write-Host "== $Main" -ForegroundColor Cyan
$arguments = @(
    "-Djava.library.path=$outDir"
    "-Dprobe.thread=$ThreadMode"
    $(if ($CefLog) { "-Dprobe.log=$CefLog" })
    '-cp', "$jar;$classesDir"
    $Main
    $Url
    $Seconds
    $Width
    $Height
)
& java @arguments
$exit = $LASTEXITCODE

Write-Host ''
Write-Host '== Waisen' -ForegroundColor Cyan
# Chromium räumt seine Helfer asynchron ab, teils Sekunden nach dem Schließen.
# Wer im selben Takt zählt, in dem er schließt, sieht einen Zwischenzustand und
# nennt ihn ein Leck.
Start-Sleep -Seconds 3
$after = @(Get-Process jcef_helper -ErrorAction SilentlyContinue)
if ($after.Count -eq 0) {
    Write-Host '  0 jcef_helper — sauber' -ForegroundColor Green
} else {
    Write-Host "  $($after.Count) jcef_helper übrig" -ForegroundColor Red
    $after | Select-Object Id, ProcessName, StartTime | Format-Table | Out-Host
}

exit $exit
