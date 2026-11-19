<#
.SYNOPSIS
    一条命令完成发版：打 tag → 打包 → 创建 Gitee Release → 上传安装包附件。

.DESCRIPTION
    版本号默认取自 build.gradle.kts 的 version，Release 正文默认取自仓库根目录的
    release-notes-<版本>.md；没有该文件时回退到 plugin.xml 里对应版本的 change-notes。

    令牌按以下顺序获取：-Token 参数 → 环境变量 GITEE_TOKEN → -TokenFile 指向的文件。
    Gitee API v5 只认私人令牌（账号密码会返回 401），权限至少需要 projects。

.EXAMPLE
    # 预演：只检查版本、构建产物、tag 状态和将调用的接口，不发任何请求
    ./scripts/publish-release.ps1 -DryRun

.EXAMPLE
    # 正式发版，令牌从环境变量或 %TEMP%\gitee_token.txt 读取
    ./scripts/publish-release.ps1

.EXAMPLE
    # 指定版本与说明文件，并跳过重新打包
    ./scripts/publish-release.ps1 -Version 1.0.9 -NotesFile docs/1.0.9.md -SkipBuild
#>
[CmdletBinding()]
param(
    # 版本号，默认读取 build.gradle.kts
    [string]$Version,

    # Gitee 私人令牌；不传则依次看环境变量与 TokenFile
    [string]$Token,

    # 存放令牌的文件（内容为一行令牌）
    [string]$TokenFile = (Join-Path ([System.IO.Path]::GetTempPath()) 'gitee_token.txt'),

    # 仓库全名 owner/repo，默认从 git remote origin 解析
    [string]$Repo,

    # Release 正文来源，默认 release-notes-<版本>.md
    [string]$NotesFile,

    # 使用的 Gradle 命令，默认优先 PATH 上的 gradle，其次仓库内的 gradlew.bat
    [string]$GradleExe,

    [switch]$SkipBuild,
    [switch]$SkipTag,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent

function Write-Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }
function Write-Info($text) { Write-Host "   $text" }
function Fail($text) { throw $text }

function Get-ChangeNotesFromPluginXml {
    param([string]$TargetVersion)

    $pluginXml = Join-Path $repoRoot 'src\main\resources\META-INF\plugin.xml'
    if (-not (Test-Path $pluginXml)) { return '' }

    $text = Get-Content $pluginXml -Raw
    $block = [regex]::Match($text, '(?s)<change-notes><!\[CDATA\[(.*?)\]\]></change-notes>')
    if (-not $block.Success) { return '' }

    $lines = $block.Groups[1].Value -split "`r?`n"
    $collected = @()
    $inSection = $false
    $versionPattern = '^\s*' + [regex]::Escape($TargetVersion) + '\s*-'
    foreach ($line in $lines) {
        if ($line -match $versionPattern) {
            $inSection = $true
            $collected += $line.Trim()
            continue
        }
        if ($inSection) {
            if ($line -match '^\s*[0-9]+\.[0-9]+\.[0-9]+\s*-') { break }
            $collected += $line.TrimEnd()
        }
    }
    return ($collected -join "`n").Trim()
}

Push-Location $repoRoot
try {
    # ---------- 1. 版本号 ----------
    Write-Step '解析版本号'
    if (-not $Version) {
        $gradleFile = Join-Path $repoRoot 'build.gradle.kts'
        if (-not (Test-Path $gradleFile)) { Fail "找不到 build.gradle.kts：$gradleFile" }
        $versionMatch = [regex]::Match((Get-Content $gradleFile -Raw), 'version\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"')
        if (-not $versionMatch.Success) { Fail 'build.gradle.kts 里没有 version = "x.y.z" 形式的版本号' }
        $Version = $versionMatch.Groups[1].Value
    }
    $tagName = "v$Version"
    $zipName = "idea-web-viewer-$Version.zip"
    Write-Info "版本 $Version（tag: $tagName）"

    # ---------- 2. 工作区 ----------
    Write-Step '检查工作区'
    $dirty = git status --porcelain
    if ($dirty) {
        Write-Warning '工作区有未提交的改动，发版前建议先提交：'
        $dirty | ForEach-Object { Write-Host "   $_" }
    } else {
        Write-Info '工作区干净'
    }
    $branch = (git rev-parse --abbrev-ref HEAD).Trim()
    Write-Info "当前分支 $branch"

    # ---------- 3. 构建 ----------
    $zipPath = Join-Path $repoRoot "build\distributions\$zipName"
    if ($SkipBuild) {
        Write-Step '跳过构建'
        if (-not (Test-Path $zipPath)) { Fail "没有找到构建产物：$zipPath" }
    } else {
        Write-Step '打包 buildPlugin'
        if (-not $GradleExe) {
            if (Get-Command gradle -ErrorAction SilentlyContinue) {
                $GradleExe = 'gradle'
            } elseif (Test-Path (Join-Path $repoRoot 'gradle\wrapper\gradle-wrapper.jar')) {
                $GradleExe = Join-Path $repoRoot 'gradlew.bat'
            } else {
                Fail '既没有 PATH 上的 gradle，也没有可用的 Gradle Wrapper'
            }
        }
        Write-Info "使用 $GradleExe"
        & $GradleExe buildPlugin --console=plain
        if ($LASTEXITCODE -ne 0) { Fail "构建失败（退出码 $LASTEXITCODE）" }
        if (-not (Test-Path $zipPath)) { Fail "构建完成但没有产物：$zipPath" }
    }
    $zipSize = [math]::Round((Get-Item $zipPath).Length / 1KB, 1)
    Write-Info "产物 $zipName（$zipSize KB）"

    # ---------- 4. tag ----------
    Write-Step "处理 tag $tagName"
    $head = (git rev-parse HEAD).Trim()
    $existingTag = git tag -l $tagName
    if ($existingTag) {
        $tagCommit = (git rev-list -n 1 $tagName).Trim()
        if ($tagCommit -ne $head) {
            # tag 指向的是历史提交且仍是 HEAD 的祖先（例如打完 tag 后只补了文档提交）时允许继续，
            # 否则说明提交已经分叉，发布的内容可能不是预期的那个
            git merge-base --is-ancestor $tagCommit HEAD 2>$null
            if ($LASTEXITCODE -ne 0) {
                Fail "tag $tagName 指向 $($tagCommit.Substring(0,7))，与当前 HEAD $($head.Substring(0,7)) 不在同一条历史线上；请先确认要发布哪个提交"
            }
            Write-Warning "tag $tagName 指向 $($tagCommit.Substring(0,7))，当前 HEAD 是 $($head.Substring(0,7))（tag 之后有新提交），将沿用已有 tag"
        } else {
            Write-Info 'tag 已存在且指向当前提交，跳过创建'
        }
    } elseif ($SkipTag) {
        Write-Info '已指定 -SkipTag，跳过创建（注意：Release 需要远端已存在该 tag）'
    } elseif ($DryRun) {
        Write-Info "[dry-run] 将创建 tag $tagName 并推送"
    } else {
        git tag -a $tagName -m "Web Viewer $Version"
        if ($LASTEXITCODE -ne 0) { Fail '创建 tag 失败' }
        $env:GIT_TERMINAL_PROMPT = '0'
        git push origin $tagName
        if ($LASTEXITCODE -ne 0) { Fail '推送 tag 失败' }
        Write-Info 'tag 已创建并推送'
    }

    # ---------- 5. Release 正文 ----------
    Write-Step '准备 Release 正文'
    if (-not $NotesFile) {
        $candidate = Join-Path $repoRoot "release-notes-$Version.md"
        if (Test-Path $candidate) { $NotesFile = $candidate }
    }
    if ($NotesFile -and (Test-Path $NotesFile)) {
        $body = [System.IO.File]::ReadAllText($NotesFile, [System.Text.Encoding]::UTF8)
        Write-Info "正文来源：$([System.IO.Path]::GetFileName($NotesFile))"
    } else {
        $body = Get-ChangeNotesFromPluginXml -TargetVersion $Version
        Write-Info '正文来源：plugin.xml 的 change-notes'
    }
    if (-not $body -or $body.Trim().Length -eq 0) {
        $body = "Web Viewer $Version"
        Write-Info '未找到说明内容，使用默认标题作为正文'
    }
    Write-Info "正文长度 $($body.Length) 字符"

    # ---------- 6. 仓库与令牌 ----------
    Write-Step '解析仓库与令牌'
    if (-not $Repo) {
        $remoteUrl = (git remote get-url origin).Trim()
        $remoteMatch = [regex]::Match($remoteUrl, 'gitee\.com[:/](?<owner>[^/]+)/(?<repo>[^/]+?)(\.git)?$')
        if (-not $remoteMatch.Success) {
            Fail "无法从 origin（$remoteUrl）解析 Gitee 仓库，请用 -Repo 指定"
        }
        $Repo = "$($remoteMatch.Groups['owner'].Value)/$($remoteMatch.Groups['repo'].Value)"
    }
    Write-Info "仓库 $Repo"

    $tokenValue = $Token
    if (-not $tokenValue -and $env:GITEE_TOKEN) { $tokenValue = $env:GITEE_TOKEN }
    if (-not $tokenValue -and (Test-Path $TokenFile)) {
        $tokenValue = (Get-Content $TokenFile -Raw).Trim()
    }
    if (-not $tokenValue) {
        if ($DryRun) {
            Write-Info '未提供令牌（dry-run 不需要）'
        } else {
            Fail "没有找到 Gitee 令牌：设置环境变量 GITEE_TOKEN，或用 -TokenFile 指定文件（当前期望 $TokenFile）"
        }
    } else {
        Write-Info "令牌已读取（长度 $($tokenValue.Length)，内容不输出）"
    }

    $apiBase = "https://gitee.com/api/v5/repos/$Repo"
    $headers = @{ Authorization = "token $tokenValue" }

    # ---------- 7. 创建或更新 Release ----------
    Write-Step "发布 Release $tagName"
    if ($DryRun) {
        Write-Info "[dry-run] POST $apiBase/releases（tag=$tagName, name=Web Viewer $Version）"
        Write-Info "[dry-run] 若已存在则 PATCH $apiBase/releases/{id} 更新正文"
        Write-Info "[dry-run] POST $apiBase/releases/{id}/attach_files 上传 $zipName"
        Write-Info '[dry-run] 未发出任何请求'
        return
    }

    $release = $null
    try {
        $release = Invoke-RestMethod -Uri "$apiBase/releases/tags/$tagName" -Headers $headers -TimeoutSec 30
    } catch {
        $release = $null
    }

    if ($release -and $release.id) {
        Write-Info "Release 已存在（id=$($release.id)），更新标题与正文"
        $payload = @{ name = "Web Viewer $Version"; body = $body; tag_name = $tagName } | ConvertTo-Json
        $release = Invoke-RestMethod -Method Patch -Uri "$apiBase/releases/$($release.id)" `
            -Headers $headers -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($payload)) -TimeoutSec 60
    } else {
        Write-Info 'Release 不存在，开始创建'
        $payload = @{
            tag_name         = $tagName
            name             = "Web Viewer $Version"
            body             = $body
            target_commitish = $branch
            prerelease       = $false
        } | ConvertTo-Json
        $release = Invoke-RestMethod -Method Post -Uri "$apiBase/releases" `
            -Headers $headers -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($payload)) -TimeoutSec 60
    }
    if (-not $release.id) { Fail '创建/更新 Release 失败：响应里没有 id' }
    Write-Info "Release id=$($release.id)"

    # ---------- 8. 上传附件 ----------
    Write-Step '上传安装包附件'
    $downloadUrl = $null
    $attached = @()
    try {
        $attached = @(Invoke-RestMethod -Uri "$apiBase/releases/$($release.id)/attach_files" -Headers $headers -TimeoutSec 30)
    } catch {
        $attached = @()
    }
    $already = $attached | Where-Object { $_.name -eq $zipName }
    if ($already) {
        $downloadUrl = $already[0].browser_download_url
        Write-Info "附件已存在，跳过上传：$downloadUrl"
    } else {
        if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
            Fail '需要 curl.exe 来上传 multipart 附件（Windows 10+ 自带）'
        }
        $raw = curl.exe -s -X POST "$apiBase/releases/$($release.id)/attach_files" `
            -H "Authorization: token $tokenValue" -F "file=@$zipPath" --max-time 180
        if ($LASTEXITCODE -ne 0) { Fail "curl 上传失败（退出码 $LASTEXITCODE）" }
        try {
            $uploaded = $raw | ConvertFrom-Json
        } catch {
            Fail "上传响应无法解析：$raw"
        }
        if (-not $uploaded.name) { Fail "上传失败：$raw" }
        $downloadUrl = $uploaded.browser_download_url
        Write-Info "已上传 $($uploaded.name)（$([math]::Round($uploaded.size / 1KB, 1)) KB）"
    }

    Write-Host "`n发布完成" -ForegroundColor Green
    Write-Host "  版本    : $Version"
    Write-Host "  仓库    : https://gitee.com/$Repo"
    Write-Host "  Release : https://gitee.com/$Repo/releases/tag/$tagName"
    Write-Host "  下载    : $downloadUrl"
} finally {
    Pop-Location
}
