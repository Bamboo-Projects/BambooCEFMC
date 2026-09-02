# Baut upstream java-cef auf dem Pin aus pin.properties.
#
# Aufruf:
#   pwsh -File tools/runtime/build-jcef.ps1                  gepatcht bauen
#   pwsh -File tools/runtime/build-jcef.ps1 -SkipPatches     nackt bauen
#   pwsh -File tools/runtime/build-jcef.ps1 -Clean           Quellen neu holen
#   pwsh -File tools/runtime/build-jcef.ps1 -Purge           auch den Cache leeren
#
# Warum außerhalb des Repositorys gebaut wird: Die CEF-Distribution entpackt
# Pfade von rund 240 Zeichen. Unter D:\Projekte\FactoryNetwork\tools\runtime\...
# reißt das Windows' Grenze von 260, das Entpacken bricht ohne Fehlermeldung
# ab, einzelne Kopfdateien fehlen, und der Bau scheitert Minuten später an
# etwas, das mit der Ursache nichts zu tun hat.

[CmdletBinding()]
param(
    [string]$WorkDir,
    [switch]$SkipPatches,
    [switch]$Clean,
    [switch]$Purge
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$runtimeDir = Split-Path -Parent $PSCommandPath
$outDir     = Join-Path $runtimeDir 'build\out\windows-x86_64'
$logDir     = Join-Path $runtimeDir 'build\log'

# ---------------------------------------------------------------- Hilfsmittel

function Write-Step([string]$text) {
    Write-Host ''
    Write-Host "== $text" -ForegroundColor Cyan
}

function Assert-ExitCode([string]$what) {
    if ($LASTEXITCODE -ne 0) {
        throw "$what fehlgeschlagen (Rückgabewert $LASTEXITCODE)"
    }
}

function Read-Pin([string]$path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $path -Encoding utf8) {
        if ($line -match '^\s*(#|$)') { continue }
        $parts = $line -split '=', 2
        if ($parts.Count -ne 2) { throw "pin.properties: Zeile ohne '=': $line" }
        $values[$parts[0].Trim()] = $parts[1].Trim()
    }
    foreach ($key in 'jcef.repo', 'jcef.commit', 'cef.version') {
        if (-not $values.ContainsKey($key)) { throw "pin.properties: '$key' fehlt" }
    }
    if ($values['jcef.commit'] -notmatch '^[0-9a-f]{40}$') {
        throw "pin.properties: jcef.commit ist kein voller SHA — ein Branch driftet zwischen zwei Bauten"
    }
    return $values
}

# ----------------------------------------------------------- Falle 1: Pfade

if (-not $WorkDir) {
    $WorkDir = if ($env:FN_JCEF_BUILD) { $env:FN_JCEF_BUILD } else { 'C:\fnjcef' }
}
$sourceDir = Join-Path $WorkDir 'src'
$cacheDir  = Join-Path $WorkDir 'cache'

# Gemessen an der CEF-Distribution: Ihr tiefster Pfad kam im Zwischenordner auf
# 264 Zeichen, vier über der Grenze. Der Ordnername der Distribution allein
# frisst 63 davon. Bleiben für die Wurzel 20.
if ($sourceDir.Length -gt 20) {
    throw ("Quellpfad zu lang: '$sourceDir' hat $($sourceDir.Length) Zeichen. " +
           "Die CEF-Distribution braucht rund 240 für ihre tiefsten Dateien. " +
           "Kürzeren Pfad über -WorkDir oder FN_JCEF_BUILD wählen, z.B. C:\fnjcef.")
}

# ---------------------------------------------------------- Falle 2: Python

# Die Fassung wird festgenagelt, weil ein Bauwerkzeug, das die Python-Fassung
# des Rechners nimmt, auf zwei Rechnern zwei verschiedene Dinge tut.
#
# PYTHON_EXECUTABLE zu setzen hat einen zweiten Zweck: Es umgeht
# find_package(PythonInterp) im CMakeLists — ein Modul, das neuere
# CMake-Fassungen nicht mehr mitbringen.
#
# Was 3.12 *nicht* rettet: gsutil, das clang-format lädt, scheitert auch hier
# (six.moves fehlt); unter 3.13 und neuer an einer Enum-Prüfung. Dagegen hilft
# nur Patch 0003.
$python = $env:FN_PYTHON
if (-not $python) {
    $candidates = @(
        'C:\Python312\python.exe'
        (Join-Path $env:ProgramFiles 'Python312\python.exe')
        (Join-Path $env:LOCALAPPDATA 'Programs\Python\Python312\python.exe')
    )
    $python = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $python) {
    throw ("Python 3.12 fehlt. Installieren mit: winget install Python.Python.3.12 " +
           "— oder den Pfad über FN_PYTHON setzen.")
}
$pythonVersion = & $python -c 'import sys; print("%d.%d" % sys.version_info[:2])'
Assert-ExitCode 'Python-Fassung abfragen'
if ($pythonVersion -ne '3.12') {
    throw "Python 3.12 erwartet, gefunden $pythonVersion unter $python"
}
$env:PYTHON_EXECUTABLE = $python

# -------------------------------------------------------- Werkzeuge suchen

$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path -LiteralPath $vswhere)) { throw "vswhere.exe fehlt — Visual Studio ist nicht installiert" }
$vsVersion = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationVersion
Assert-ExitCode 'vswhere'
if (-not $vsVersion) { throw "Kein Visual Studio mit C++-Werkzeugen gefunden" }
$generator = switch ([int]($vsVersion -split '\.')[0]) {
    18      { 'Visual Studio 18 2026' }
    17      { 'Visual Studio 17 2022' }
    16      { 'Visual Studio 16 2019' }
    default { throw "Visual Studio $vsVersion wird nicht unterstützt" }
}

foreach ($tool in 'git', 'cmake', 'javac', 'jar') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool fehlt im PATH" }
}

$pin = Read-Pin (Join-Path $runtimeDir 'pin.properties')

# Nur ein Bau zur Zeit. Zwei Läufe teilen sich Quellbaum, Bauverzeichnis und
# Protokolle; der zweite reißt dem ersten die CEF-Distribution unter den
# Füßen weg, und beide scheitern an etwas, das wie ein Werkzeugfehler
# aussieht. Einmal erlebt, deshalb die Sperre.
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$lockPath = Join-Path $logDir 'build.lock'
try {
    $lock = [System.IO.File]::Open($lockPath, 'Create', 'Write', 'None')
}
catch {
    throw "Es läuft bereits ein Bau (Sperre: $lockPath). Erst den beenden."
}

Write-Step 'Umgebung'
Write-Host "  Arbeitsverzeichnis  $WorkDir"
Write-Host "  Python              $python ($pythonVersion)"
Write-Host "  Generator           $generator (VS $vsVersion)"
Write-Host "  java-cef            $($pin['jcef.commit'])"
Write-Host "  CEF                 $($pin['cef.version'])"
Write-Host "  Patches             $(if ($SkipPatches) { 'übersprungen' } else { 'werden angewendet' })"

# --------------------------------------------------------------- Quellbaum

if ($Purge -and (Test-Path -LiteralPath $WorkDir)) {
    Write-Step 'Arbeitsverzeichnis samt Cache löschen'
    Remove-Item -LiteralPath $WorkDir -Recurse -Force
}
elseif ($Clean -and (Test-Path -LiteralPath $sourceDir)) {
    Write-Step 'Quellbaum löschen (Cache bleibt)'
    # Die CEF-Distribution wird beim Bau über ihre SHA-1 geprüft. Sie aus dem
    # Cache zu nehmen ist deshalb derselbe Eingang, nicht eine Abkürzung.
    $archive = Join-Path $sourceDir "third_party\cef\cef_binary_$($pin['cef.version'])_windows64.tar.bz2"
    if (Test-Path -LiteralPath $archive) {
        New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
        Move-Item -LiteralPath $archive -Destination $cacheDir -Force
    }
    Remove-Item -LiteralPath $sourceDir -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $sourceDir, $cacheDir, $logDir | Out-Null

Write-Step 'java-cef auf den Pin holen'
if (-not (Test-Path -LiteralPath (Join-Path $sourceDir '.git'))) {
    git init -q $sourceDir
    Assert-ExitCode 'git init'
    git -C $sourceDir remote add origin $pin['jcef.repo']
    Assert-ExitCode 'git remote add'
}
# Den SHA direkt holen, nicht clone --depth 1 + checkout: Letzteres geht nur so
# lange gut, wie der Pin zufällig HEAD ist — genau die stille Drift, gegen die
# pin.properties da ist.
git -C $sourceDir fetch --depth 1 origin $pin['jcef.commit']
Assert-ExitCode 'git fetch'
git -C $sourceDir checkout -q --force --detach FETCH_HEAD
Assert-ExitCode 'git checkout'
git -C $sourceDir reset -q --hard FETCH_HEAD
Assert-ExitCode 'git reset'

$cefInSource = (Select-String -LiteralPath (Join-Path $sourceDir 'CMakeLists.txt') `
                              -Pattern 'set\(CEF_VERSION "([^"]+)"\)').Matches[0].Groups[1].Value
if ($cefInSource -ne $pin['cef.version']) {
    throw ("Der Pin traegt CEF $cefInSource, pin.properties nennt $($pin['cef.version']). " +
           "Einer von beiden ist angehoben worden.")
}

# ----------------------------------------------------------------- Patches

if (-not $SkipPatches) {
    $patches = @(Get-ChildItem -LiteralPath (Join-Path $runtimeDir 'patches') -Filter '0*.patch' |
                 Sort-Object Name)
    if (-not $patches) { throw "Keine Patches unter patches/ gefunden. Nackt bauen: -SkipPatches" }
    Write-Step "Patches anwenden ($($patches.Count))"
    foreach ($patch in $patches) {
        git -C $sourceDir apply --check --whitespace=nowarn $patch.FullName
        if ($LASTEXITCODE -ne 0) {
            throw ("$($patch.Name) passt nicht auf den Pin. Entweder ist der Pin " +
                   "angehoben worden oder der Patch ist veraltet — beides gehört " +
                   "geklärt, bevor halb gepatchter Code übersetzt wird.")
        }
        git -C $sourceDir apply --whitespace=nowarn $patch.FullName
        Assert-ExitCode "git apply $($patch.Name)"
        Write-Host "  $($patch.Name)" -ForegroundColor Green
    }
}

# ------------------------------------------------- CEF-Distribution aus dem Cache

$cefArchiveName = "cef_binary_$($pin['cef.version'])_windows64.tar.bz2"
$cached = Join-Path $cacheDir $cefArchiveName
$target = Join-Path $sourceDir "third_party\cef\$cefArchiveName"
if ((Test-Path -LiteralPath $cached) -and -not (Test-Path -LiteralPath $target)) {
    Write-Step 'CEF-Distribution aus dem Cache'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $cached -Destination $target
}

# ------------------------------------------------------------- nativer Bau

$buildDir = Join-Path $sourceDir 'jcef_build'
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null

Write-Step 'CMake konfigurieren'
$configureLog = Join-Path $logDir 'cmake-configure.log'
cmake -S $sourceDir -B $buildDir -G $generator -A x64 -DPROJECT_ARCH=x86_64 2>&1 |
    Tee-Object -FilePath $configureLog
Assert-ExitCode 'cmake konfigurieren'

# Die Dateiliste ist nicht geraten: CMake gibt sie beim Konfigurieren aus.
Copy-Item -LiteralPath $configureLog -Destination (Join-Path $logDir 'cef-dateiliste.log') -Force

# Das heruntergeladene Archiv für den nächsten Lauf sichern.
if ((Test-Path -LiteralPath $target) -and -not (Test-Path -LiteralPath $cached)) {
    Copy-Item -LiteralPath $target -Destination $cached
}

Write-Step 'jcef bauen'
cmake --build $buildDir --config Release --target jcef 2>&1 |
    Tee-Object -FilePath (Join-Path $logDir 'cmake-build.log')
Assert-ExitCode 'cmake bauen'

# ---------------------------------------------------------------- Java-Teil

Write-Step 'Java übersetzen'
$classesDir = Join-Path $sourceDir 'out\win64'
if (Test-Path -LiteralPath $classesDir) { Remove-Item -LiteralPath $classesDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null

# Dieselbe Auswahl wie tools/compile.bat von upstream: die sechs Pakete, jedes
# ohne Unterordner. Das ist keine Bequemlichkeit — org/cef/browser/mac greift
# auf sun.lwawt zu und übersetzt auf Windows nicht.
$packages = 'org\cef', 'org\cef\browser', 'org\cef\callback', 'org\cef\handler',
            'org\cef\misc', 'org\cef\network'
$javaSources = foreach ($package in $packages) {
    Get-ChildItem -LiteralPath (Join-Path $sourceDir "java\$package") -Filter '*.java' |
        ForEach-Object { $_.FullName }
}
$sourceList = Join-Path $logDir 'javac-quellen.txt'
Set-Content -LiteralPath $sourceList -Value $javaSources -Encoding utf8

# jogamp steht auf dem Klassenpfad, weil CefBrowserOsr und CefRenderer von
# upstream gegen JOGL übersetzen. Die Jars gehören nicht in die Auslieferung —
# gebraucht werden sie nur, wer CefBrowserOsr auch benutzt.
$classpath = (Join-Path $sourceDir 'third_party\jogamp\jar\*')

& javac -nowarn -encoding UTF-8 -cp $classpath -d $classesDir "@$sourceList" 2>&1 |
    Tee-Object -FilePath (Join-Path $logDir 'javac.log')
Assert-ExitCode 'javac'

Write-Step 'jcef.jar packen'
$manifest = Join-Path $sourceDir 'java\manifest\MANIFEST.MF'
Push-Location $classesDir
try {
    & jar -c -m $manifest -f 'jcef.jar' 'org'
    Assert-ExitCode 'jar'
}
finally { Pop-Location }

# ------------------------------------------------------------- einsammeln

Write-Step 'Artefakte einsammeln'
if (Test-Path -LiteralPath $outDir) { Remove-Item -LiteralPath $outDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$cefRoot    = Join-Path $sourceDir "third_party\cef\cef_binary_$($pin['cef.version'])_windows64"
$nativeOut  = Join-Path $buildDir 'native\Release'
$cefRelease = Join-Path $cefRoot 'Release'
$cefRes     = Join-Path $cefRoot 'Resources'

$expected = Get-Content -LiteralPath (Join-Path $runtimeDir 'files-windows.txt') -Encoding utf8 |
            Where-Object { $_ -notmatch '^\s*(#|$)' } | ForEach-Object { $_.Trim() }

$sources = @{}
foreach ($dir in $nativeOut, $cefRelease, $cefRes) {
    if (-not (Test-Path -LiteralPath $dir)) { continue }
    foreach ($file in Get-ChildItem -LiteralPath $dir -File) {
        if (-not $sources.ContainsKey($file.Name)) { $sources[$file.Name] = $file.FullName }
    }
}
$sources['jcef.jar'] = Join-Path $classesDir 'jcef.jar'

$missing = @()
foreach ($name in $expected) {
    if ($name -eq 'locales/') { continue }
    if ($sources.ContainsKey($name)) {
        Copy-Item -LiteralPath $sources[$name] -Destination (Join-Path $outDir $name)
    } else {
        $missing += $name
    }
}
$localesSource = @((Join-Path $nativeOut 'locales'), (Join-Path $cefRes 'locales')) |
                 Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if ($localesSource) {
    Copy-Item -LiteralPath $localesSource -Destination $outDir -Recurse
} else {
    $missing += 'locales/'
}

if ($missing) {
    throw "Im Ergebnis fehlen: $($missing -join ', ')"
}

Write-Step 'Fertig'
Get-ChildItem -LiteralPath $outDir -Recurse -File |
    Sort-Object FullName |
    ForEach-Object { '{0,12:N0}  {1}' -f $_.Length, $_.FullName.Substring($outDir.Length + 1) }
Write-Host ''
Write-Host "Ergebnis unter $outDir" -ForegroundColor Green

$lock.Close()
