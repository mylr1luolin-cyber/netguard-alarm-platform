#requires -version 5
<#
.SYNOPSIS
    用 GitHub REST API 把当前 git 仓库的 HEAD 提交整份上传到远端仓库。

.DESCRIPTION
    适用的场景：本机到 github.com 的 git 协议（443 上的 smart HTTP）被重置，
    但 api.github.com 可以访问。此时 git push 走不通，可以改用 Git Data API：
    逐个文件上传 blob -> 建 tree -> 建 commit -> 移动分支引用。

    需要一枚 fine-grained PAT，权限：Contents = Read and write（仅该仓库）。

.PARAMETER Token
    GitHub Personal Access Token。

.PARAMETER Owner
    仓库所属账号，默认 mylr1luolin-cyber。

.PARAMETER Repo
    仓库名，默认 netguard-alarm-platform。

.PARAMETER Branch
    目标分支，默认 main。

.PARAMETER Message
    提交说明；默认取本地 HEAD 的提交说明。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\push-to-github.ps1 -Token ghp_xxx
#>
param(
    [Parameter(Mandatory = $true)][string]$Token,
    [string]$Owner = 'mylr1luolin-cyber',
    [string]$Repo = 'netguard-alarm-platform',
    [string]$Branch = 'main',
    [string]$Message = ''
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$api = "https://api.github.com/repos/$Owner/$Repo"
$headers = @{
    Authorization          = "Bearer $Token"
    Accept                 = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
    'User-Agent'           = 'NetGuard-Uploader'
}

$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    # ---- 0. 本地文件清单（只提交 git 跟踪的文件，二进制也能传）----
    $raw = git ls-files -z
    $files = ($raw -split "`0") | Where-Object { $_ -ne '' }
    if (-not $files) { throw '当前目录不是 git 仓库，或者没有跟踪的文件。' }

    if (-not $Message) {
        $Message = (git log -1 --pretty=%B).Trim()
        if (-not $Message) { $Message = 'chore: sync files' }
    }

    Write-Host ("待上传 {0} 个文件，目标 {1}/{2} 分支 {3}" -f $files.Count, $Owner, $Repo, $Branch)

    # ---- 1. 远端当前 HEAD（用于 parents 与 force 更新）----
    $ref = $null
    try {
        $ref = Invoke-RestMethod -Uri "$api/git/ref/heads/$Branch" -Headers $headers -Method Get
    } catch {
        Write-Host "分支 $Branch 还不存在，将新建。"
    }

    # ---- 2. 逐个上传 blob ----
    $tree = New-Object System.Collections.Generic.List[object]
    $i = 0
    foreach ($f in $files) {
        $i++
        $full = Join-Path $root $f
        $bytes = [IO.File]::ReadAllBytes($full)
        $b64 = [Convert]::ToBase64String($bytes)
        $body = @{ content = $b64; encoding = 'base64' } | ConvertTo-Json -Compress
        $blob = Invoke-RestMethod -Uri "$api/git/blobs" -Headers $headers -Method Post -Body $body -ContentType 'application/json; charset=utf-8'
        $tree.Add(@{
            path = ($f -replace '\\', '/')
            mode = '100644'
            type = 'blob'
            sha  = $blob.sha
        })
        if ($i % 20 -eq 0 -or $i -eq $files.Count) {
            Write-Host ("  已上传 {0}/{1}" -f $i, $files.Count)
        }
    }

    # ---- 3. 建 tree（不带 base_tree，整棵树替换）----
    Write-Host '创建 tree ...'
    $treeBody = @{ tree = $tree } | ConvertTo-Json -Depth 6 -Compress
    $newTree = Invoke-RestMethod -Uri "$api/git/trees" -Headers $headers -Method Post -Body $treeBody -ContentType 'application/json; charset=utf-8'

    # ---- 4. 建 commit ----
    Write-Host '创建 commit ...'
    $parents = @()
    if ($ref -and $ref.object -and $ref.object.sha) { $parents = @($ref.object.sha) }
    $commitBody = @{
        message = $Message
        tree    = $newTree.sha
        parents = $parents
    } | ConvertTo-Json -Compress
    $commit = Invoke-RestMethod -Uri "$api/git/commits" -Headers $headers -Method Post -Body $commitBody -ContentType 'application/json; charset=utf-8'

    # ---- 5. 移动分支（不存在则创建）----
    Write-Host "更新分支 $Branch -> $($commit.sha.Substring(0,7)) ..."
    if ($ref) {
        $patch = @{ sha = $commit.sha; force = $true } | ConvertTo-Json -Compress
        $null = Invoke-RestMethod -Uri "$api/git/refs/heads/$Branch" -Headers $headers -Method Patch -Body $patch -ContentType 'application/json; charset=utf-8'
    } else {
        $create = @{ ref = "refs/heads/$Branch"; sha = $commit.sha } | ConvertTo-Json -Compress
        $null = Invoke-RestMethod -Uri "$api/git/refs" -Headers $headers -Method Post -Body $create -ContentType 'application/json; charset=utf-8'
    }

    Write-Host ''
    Write-Host ("完成：{0} 个文件 -> https://github.com/{1}/{2}/tree/{3}" -f $files.Count, $Owner, $Repo, $Branch)
    Write-Host ("commit: {0}" -f $commit.sha)
    exit 0
} catch {
    Write-Host ''
    Write-Host ("失败：{0}" -f $_.Exception.Message)
    if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
        Write-Host $_.ErrorDetails.Message
    }
    exit 1
} finally {
    Pop-Location
}
