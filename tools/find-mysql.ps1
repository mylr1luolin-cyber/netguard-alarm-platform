#requires -version 5
<#
.SYNOPSIS
    在本机查找 MySQL 安装目录（需要 bin\mysql.exe 与 bin\mysqld.exe）。

.DESCRIPTION
    查找顺序：环境变量 NG_MYSQL_HOME / MYSQL_HOME → 注册表 → MySQL 服务对应的可执行文件
    → 环境变量 PATH 中的 mysql.exe → 常见安装目录 → 限定深度的盘符扫描。
    找到后输出目录；找不到时输出可操作的提示并以 exit 1 结束。

.PARAMETER Quiet
    只输出最佳目录（供 bat 的 for /f 捕获），不带表格与说明。

.PARAMETER Json
    输出 JSON 数组，便于其它脚本消费。
#>
param(
    [switch]$Quiet,
    [switch]$Json
)

$ErrorActionPreference = 'SilentlyContinue'

function Test-MySqlHome {
    param([string]$dir)
    if ([string]::IsNullOrWhiteSpace($dir)) { return $false }
    return (Test-Path (Join-Path $dir 'bin\mysql.exe')) -and (Test-Path (Join-Path $dir 'bin\mysqld.exe'))
}

function Get-MySqlVersion {
    param([string]$dir)
    $exe = Join-Path $dir 'bin\mysql.exe'
    if (-not (Test-Path $exe)) { return '' }
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $exe
        $psi.Arguments = '--version'
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $p = [System.Diagnostics.Process]::Start($psi)
        $out = $p.StandardOutput.ReadToEnd() + $p.StandardError.ReadToEnd()
        $null = $p.WaitForExit(8000)
        if ($out -match '(\d+\.\d+\.\d+)') { return $Matches[1] }
    } catch { }
    return ''
}

$candidates = New-Object System.Collections.Generic.List[object]

function Add-Candidate {
    param([string]$dir, [string]$source)
    if ([string]::IsNullOrWhiteSpace($dir)) { return }
    $dir = $dir.TrimEnd('\').Trim()
    if (-not (Test-MySqlHome $dir)) { return }
    foreach ($c in $candidates) { if ($c.Path -eq $dir) { return } }
    $candidates.Add([pscustomobject]@{ Path = $dir; Source = $source })
}

# 1) 显式环境变量
Add-Candidate $env:NG_MYSQL_HOME 'env:NG_MYSQL_HOME'
Add-Candidate $env:MYSQL_HOME 'env:MYSQL_HOME'

# 2) 注册表（MySQL Installer 会写这里）
foreach ($key in @('HKLM:\SOFTWARE\MySQL AB', 'HKLM:\SOFTWARE\WOW6432Node\MySQL AB')) {
    Get-ChildItem $key | ForEach-Object {
        $loc = (Get-ItemProperty $_.PSPath).Location
        Add-Candidate $loc "registry:$($_.PSChildName)"
    }
}

# 3) Windows 服务：从 mysqld.exe 的命令行反推安装目录
Get-CimInstance Win32_Service -Filter "Name LIKE '%mysql%'" | ForEach-Object {
    if ($_.PathName -match '"?([A-Za-z]:\\[^"]*?)\\bin\\mysqld\.exe') {
        Add-Candidate $Matches[1] "service:$($_.Name)"
    }
}

# 4) PATH 里的 mysql.exe
$onPath = Get-Command mysql.exe -ErrorAction SilentlyContinue
if ($onPath) {
    Add-Candidate (Split-Path (Split-Path $onPath.Source -Parent) -Parent) 'PATH'
}

# 5) 常见安装目录
$roots = @(
    'C:\Program Files\MySQL',
    'C:\Program Files (x86)\MySQL',
    'D:\Program Files\MySQL',
    'C:\ProgramData\MySQL',
    'C:\xampp\mysql',
    'D:\xampp\mysql',
    'C:\wamp64\bin\mysql',
    'D:\wamp64\bin\mysql',
    'C:\phpstudy_pro\Extensions',
    'D:\phpstudy_pro\Extensions',
    'D:\devtools\mysql',
    'C:\devtools\mysql',
    'D:\devtools',
    'C:\devtools',
    'D:\mysql',
    'C:\mysql',
    'D:\environment\mysql',
    'C:\environment\mysql'
)
foreach ($root in $roots) {
    Add-Candidate $root 'known folder'
    Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        Add-Candidate $_.FullName "known folder:$root"
    }
}

# 6) 盘符扫描（深度 3，只扫看起来像 MySQL 的目录名）
if ($candidates.Count -eq 0) {
    foreach ($drive in (Get-PSDrive -PSProvider FileSystem | Where-Object { $_.Name -match '^[A-Za-z]$' })) {
        $root = "$($drive.Name):\"
        $hits = Get-ChildItem -Path $root -Directory -Depth 3 -Recurse -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '^(mysql|mariadb)[-_\.\d]*$' }
        foreach ($h in $hits) {
            Add-Candidate $h.FullName "scan:$root"
            $bin = Join-Path $h.FullName 'bin'
            if (Test-MySqlHome $h.FullName) { break }
        }
        if ($candidates.Count -gt 0) { break }
    }
}

if ($candidates.Count -eq 0) {
    if (-not $Quiet) {
        [Console]::Error.WriteLine('No MySQL found on this machine.')
        [Console]::Error.WriteLine('  - 装一个 MySQL 8：https://dev.mysql.com/downloads/installer/  （或 winget install Oracle.MySQL）')
        [Console]::Error.WriteLine('  - 或者把便携版解压到 D:\devtools\mysql 后重试')
        [Console]::Error.WriteLine('  - 已经装好的话，设一个环境变量 NG_MYSQL_HOME 指向 MySQL 根目录（含 bin\mysql.exe）')
    }
    exit 1
}

$list = @($candidates | ForEach-Object {
    [pscustomobject]@{
        Path    = $_.Path
        Version = Get-MySqlVersion $_.Path
        Source  = $_.Source
        Mysqld  = (Test-Path (Join-Path $_.Path 'bin\mysqld.exe'))
    }
})

if ($Json) {
    ConvertTo-Json -InputObject @($list) -Depth 3
    exit 0
}

$best = $list[0]
if ($Quiet) {
    Write-Output $best.Path
    exit 0
}

Write-Host ''
Write-Host 'MySQL found on this machine:'
Write-Host ''
$list | Format-Table Path, Version, Mysqld, Source -AutoSize | Out-String -Width 200 | Write-Host
Write-Host ('BEST: ' + $best.Path)
Write-Host ''
