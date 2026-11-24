# ============================================================================
#  find-jdk.ps1 - locate installed JDKs on this Windows machine
# ----------------------------------------------------------------------------
#  Why: the launchers used to hard-code JAVA_HOME. On another PC the JDK may
#       live anywhere (Program Files, IntelliJ .jdks, D:\devtools, ...), so we
#       search first and validate by actually running "java -version".
#
#  Usage:
#     powershell -NoProfile -ExecutionPolicy Bypass -File tools\find-jdk.ps1
#         -> human readable table of every JDK found + a "BEST" line
#     ... -Quiet
#         -> prints only the best JDK home, nothing else (for .bat scripts)
#     ... -Json
#         -> JSON array [{ path, version, major, source }]
#     ... -PreferMajor 17
#         -> which major version wins (default 17, the version this project needs)
#
#  Exit code: 0 = at least one usable JDK found, 1 = none found.
#  ASCII only on purpose: Windows PowerShell 5.1 reads BOM-less UTF-8 as ANSI,
#  so non-ASCII text here would come out garbled.
# ============================================================================

[CmdletBinding()]
param(
    [int]$PreferMajor = 17,
    [int]$MaxDepth = 3,
    [switch]$Quiet,
    [switch]$Json
)

$ErrorActionPreference = 'SilentlyContinue'

# ---------------------------------------------------------------- helpers ---
function Write-Log([string]$msg) {
    if (-not $Quiet) { Write-Host $msg }
}

# A real JDK home always has bin\java.exe.
function Test-JdkHome([string]$path) {
    if ([string]::IsNullOrWhiteSpace($path)) { return $false }
    return (Test-Path -LiteralPath (Join-Path $path 'bin\java.exe'))
}

function ConvertTo-Major([string]$version) {
    if ($version -match '^(\d+)') { return [int]$Matches[1] }
    return $null
}

# Read <home>\release first: it is instant and avoids touching stderr.
# Only if that file is missing (very old JRE) we actually spawn java.exe.
function Get-JavaVersion([string]$jdkHome) {
    $rel = Join-Path $jdkHome 'release'
    if (Test-Path -LiteralPath $rel) {
        $hit = Select-String -LiteralPath $rel -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
        if ($hit) {
            $v = $hit.Matches[0].Groups[1].Value
            $m = ConvertTo-Major $v
            if ($m) { return [pscustomobject]@{ Version = $v; Major = $m } }
        }
    }

    # Fallback: run "java -version" with redirected streams so PowerShell never
    # turns the stderr banner into a NativeCommandError record.
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = (Join-Path $jdkHome 'bin\java.exe')
        $psi.Arguments = '-version'
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $psi.RedirectStandardError = $true
        $psi.RedirectStandardOutput = $true
        $proc = [System.Diagnostics.Process]::Start($psi)
        $text = $proc.StandardError.ReadToEnd() + "`n" + $proc.StandardOutput.ReadToEnd()
        $proc.WaitForExit(10000) | Out-Null
        if ($text -match 'version\s+"([^"]+)"') {
            $v = $Matches[1]
            $m = ConvertTo-Major $v
            if ($m) { return [pscustomobject]@{ Version = $v; Major = $m } }
        }
    } catch {
        return $null
    }
    return $null
}

$found = [ordered]@{}   # path -> source tag (dedupe, keep first source)

function Add-Candidate([string]$path, [string]$source) {
    if (-not (Test-JdkHome $path)) { return }
    $full = (Resolve-Path -LiteralPath $path).Path.TrimEnd('\')
    if (-not $found.Contains($full)) { $found[$full] = $source }
}

# ------------------------------------------------------- 1) JAVA_HOME env ---
Add-Candidate $env:JAVA_HOME 'env:JAVA_HOME'

# ------------------------------------------------------------- 2) registry --
$javaSoft = @(
    'HKLM:\SOFTWARE\JavaSoft\JDK',
    'HKLM:\SOFTWARE\JavaSoft\Java Development Kit',
    'HKLM:\SOFTWARE\WOW6432Node\JavaSoft\JDK',
    'HKLM:\SOFTWARE\WOW6432Node\JavaSoft\Java Development Kit',
    'HKCU:\SOFTWARE\JavaSoft\JDK',
    'HKCU:\SOFTWARE\JavaSoft\Java Development Kit'
)
foreach ($base in $javaSoft) {
    if (-not (Test-Path $base)) { continue }
    foreach ($key in (Get-ChildItem -Path $base)) {
        $props = Get-ItemProperty -Path $key.PSPath
        if ($props.JavaHome) { Add-Candidate $props.JavaHome "registry:$($key.PSChildName)" }
    }
}

# Vendor installers (Adoptium/Temurin, Microsoft, Corretto, Zulu, BellSoft...)
$vendorBases = @(
    'HKLM:\SOFTWARE\Eclipse Adoptium\JDK',
    'HKLM:\SOFTWARE\Eclipse Foundation\JDK',
    'HKLM:\SOFTWARE\Microsoft\JDK',
    'HKLM:\SOFTWARE\Amazon Corretto',
    'HKLM:\SOFTWARE\Azul Systems\Zulu',
    'HKLM:\SOFTWARE\BellSoft\LibericaJDK',
    'HKLM:\SOFTWARE\Semeru'
)
foreach ($base in $vendorBases) {
    if (-not (Test-Path $base)) { continue }
    foreach ($key in (Get-ChildItem -Path $base -Recurse -Depth 1)) {
        $props = Get-ItemProperty -Path $key.PSPath
        foreach ($name in 'Path', 'InstallationPath', 'JavaHome') {
            if ($props.$name) { Add-Candidate $props.$name "registry:$($key.PSChildName)" }
        }
    }
}

# ---------------------------------------------------------- 3) PATH lookup --
$javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
if ($javaCmd) {
    Add-Candidate (Split-Path (Split-Path $javaCmd.Source -Parent) -Parent) 'PATH'
}

# ------------------------------------------------- 4) well-known directories --
$knownRoots = @(
    (Join-Path $env:ProgramFiles 'Java'),
    (Join-Path ${env:ProgramFiles(x86)} 'Java'),
    (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
    (Join-Path $env:ProgramFiles 'Microsoft'),
    (Join-Path $env:ProgramFiles 'Amazon Corretto'),
    (Join-Path $env:ProgramFiles 'Zulu'),
    (Join-Path $env:ProgramFiles 'BellSoft'),
    (Join-Path $env:ProgramFiles 'Semeru'),
    (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium'),
    (Join-Path $env:USERPROFILE '.jdks'),          # IntelliJ "Download JDK"
    (Join-Path $env:USERPROFILE 'scoop\apps'),
    'C:\devtools',
    'D:\devtools'
)
# JetBrains ships a full JBR inside every IDE install (works for -jar too).
foreach ($jb in @((Join-Path $env:ProgramFiles 'JetBrains'), (Join-Path $env:LOCALAPPDATA 'Programs'))) {
    if (-not (Test-Path $jb)) { continue }
    foreach ($ide in (Get-ChildItem -Path $jb -Directory -ErrorAction SilentlyContinue)) {
        Add-Candidate (Join-Path $ide.FullName 'jbr') "JetBrains:$($ide.Name)"
    }
}

foreach ($root in $knownRoots) {
    if (-not (Test-Path $root)) { continue }
    # the root itself, its direct children, and one level deeper (devtools\jdk17\jdk-17.0.2)
    Add-Candidate $root "known:$root"
    foreach ($d1 in (Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue)) {
        Add-Candidate $d1.FullName "known:$root"
        foreach ($d2 in (Get-ChildItem -Path $d1.FullName -Directory -ErrorAction SilentlyContinue)) {
            Add-Candidate $d2.FullName "known:$root"
        }
    }
}

# -------------------------------------- 5) broader scan only if still empty --
if ($found.Count -eq 0) {
    Write-Log 'No JDK in registry / PATH / known folders, scanning drives (this can take a while) ...'
    $nameFilter = '*jdk*', '*jre*', '*java*', '*temurin*', '*adoptium*', '*corretto*',
                  '*zulu*', '*openjdk*', '*graal*', '*semeru*', '*liberica*'
    $drives = Get-PSDrive -PSProvider FileSystem | Where-Object { $_.Root -match '^[A-Za-z]:\\$' }
    foreach ($drv in $drives) {
        $root = $drv.Root
        if (-not (Test-Path $root)) { continue }
        Write-Log ("  scanning {0} (depth {1}) ..." -f $root, $MaxDepth)
        foreach ($dir in (Get-ChildItem -Path $root -Directory -Recurse -Depth $MaxDepth -ErrorAction SilentlyContinue)) {
            $name = $dir.Name.ToLowerInvariant()
            if ($nameFilter | Where-Object { $name -like $_ }) {
                Add-Candidate $dir.FullName "scan:$root"
            }
        }
        if ($found.Count -gt 0) { break }   # first drive with hits is enough
    }
}

# ------------------------------------------------------------- validate ----
$results = @()
foreach ($path in $found.Keys) {
    $info = Get-JavaVersion $path
    if (-not $info) { continue }
    # Maven needs javac, so a full JDK beats a bare JRE when both exist.
    $hasJavac = Test-Path -LiteralPath (Join-Path $path 'bin\javac.exe')
    $results += [pscustomobject]@{
        Path    = $path
        Version = ($info.Version -replace '^.*"|".*$', '')
        Major   = $info.Major
        Kind    = $(if ($hasJavac) { 'JDK' } else { 'JRE' })
        Javac   = $hasJavac
        Source  = $found[$path]
    }
}

if ($results.Count -eq 0) {
    if ($Quiet) {
        [Console]::Error.WriteLine('NO_JDK_FOUND')
    } else {
        Write-Host ''
        Write-Host 'No working JDK found on this machine.' -ForegroundColor Yellow
        Write-Host 'Install JDK 17 (pick one):'
        Write-Host '  winget install --id EclipseAdoptium.Temurin.17.JDK -e'
        Write-Host '  or download: https://adoptium.net/temurin/releases/?version=17'
        Write-Host 'After installing, open a NEW terminal so PATH is refreshed, then run this script again.'
    }
    exit 1
}

# Best pick: preferred major first, then a full JDK over a JRE, then newest major.
$sorted = $results | Sort-Object `
    @{ Expression = { if ($_.Major -eq $PreferMajor) { 0 } else { 1 } } }, `
    @{ Expression = { if ($_.Javac) { 0 } else { 1 } } }, `
    @{ Expression = { $_.Major }; Descending = $true }, `
    @{ Expression = { $_.Path.Length } }

$best = $sorted | Select-Object -First 1

if ($Json) {
    # PowerShell 5.1 emits a bare object for a single item (and a PSCustomObject
    # has no .Count), so normalise to an array first
    $arr = @($sorted)
    if ($arr.Count -eq 1) {
        "[{0}]" -f ($arr[0] | ConvertTo-Json -Depth 3)
    } else {
        $arr | ConvertTo-Json -Depth 3
    }
    exit 0
}

if ($Quiet) {
    # exact path on stdout, warnings to stderr, so .bat can capture it safely
    if ($best.Major -ne $PreferMajor) {
        [Console]::Error.WriteLine("WARN: best JDK is $($best.Major), project needs $PreferMajor")
    }
    Write-Output $best.Path
    exit 0
}

Write-Host ''
Write-Host 'JDKs found on this machine:' -ForegroundColor Cyan
$sorted | Format-Table -AutoSize Path, Version, Major, Kind, Source | Out-String -Width 200 | Write-Host
Write-Host ("BEST: {0}" -f $best.Path) -ForegroundColor Green
Write-Host ("      version {0} (major {1}), {2}, source: {3}" -f $best.Version, $best.Major, $best.Kind, $best.Source)
if (-not $best.Javac) {
    Write-Host 'WARNING: this is a JRE without javac - it can run the jar but cannot compile.' -ForegroundColor Yellow
}
if ($best.Major -ne $PreferMajor) {
    Write-Host ("WARNING: project requires JDK {0}, the best match is {1}." -f $PreferMajor, $best.Major) -ForegroundColor Yellow
}
exit 0
