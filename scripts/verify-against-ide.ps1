<#
.SYNOPSIS
    用本机已安装的 IntelliJ IDEA 作为编译目标，验证插件源码与目标平台 API 兼容。

.DESCRIPTION
    插件平时用 Gradle 针对某个可下载的 IDE 版本编译；但 JetBrains 平台从 2026.x 起
    部分模块是 Java 25 字节码，且有网络拉不到 IDE 分发包的场景（内网/离线）。
    本脚本直接以本地 IDE 安装目录为 classpath 调用 JDK 25 的 javac，
    用来确认「源码能否通过目标 IDE 的 API 编译」，编译产物只用于验证，不会被打包。

    退出码 0 表示编译通过。

.PARAMETER IdePath
    IDEA 安装目录，例如 "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2"。

.PARAMETER JdkHome
    JDK 25 及以上的安装目录。省略时依次尝试：JAVA_HOME、PATH 里的 java、
    以及 IDE 自带的 jbr 目录。

.PARAMETER OutDir
    存放验证用 class 文件的目录，默认在临时目录下。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$IdePath,

    [string]$JdkHome = '',

    [string]$OutDir = (Join-Path ([System.IO.Path]::GetTempPath()) 'webviewer-verify')
)

$ErrorActionPreference = 'Stop'

function Get-JavaMajor {
    param([string]$JdkDir)

    $javac = Join-Path $JdkDir 'bin\javac.exe'
    if (-not (Test-Path $javac)) {
        return 0
    }
    # 老版本 JDK 的 javac 把版本号写到 stderr，这里要允许它输出而不是当作终止错误
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $text = (& $javac -version 2>&1 | Out-String)
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($text -match 'javac\s+(\d+)') {
        return [int]$Matches[1]
    }
    if ($text -match 'javac\s+1\.(\d+)') {
        return [int]$Matches[1]
    }
    return 0
}

function Resolve-JdkHome {
    param([string]$Explicit)

    if ($Explicit) {
        $major = Get-JavaMajor -JdkDir $Explicit
        if ($major -lt 25) {
            throw "指定的 JdkHome 需要 JDK 25 及以上（当前 javac 主版本：$major）：$Explicit"
        }
        return $Explicit
    }

    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += (Join-Path $IdePath 'jbr')
    $candidates += (Get-Command java -ErrorAction SilentlyContinue | ForEach-Object { Split-Path (Split-Path $_.Source -Parent) -Parent })

    # 平台从 2026.x 起有 Java 25 字节码，javac 必须 >= 25 才能读取
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        if ((Get-JavaMajor -JdkDir $candidate) -ge 25) {
            return $candidate
        }
    }
    throw '找不到 JDK 25 及以上的 javac，请用 -JdkHome 指定（例如 IDE 自带的 jbr 目录）'
}

function Get-ClassPathJars {
    param([string]$Root)

    if (-not (Test-Path $Root)) {
        throw "IDE 安装目录不存在：$Root"
    }

    $jars = @()
    $jars += Get-ChildItem (Join-Path $Root 'lib') -Filter *.jar -ErrorAction SilentlyContinue

    # JCEF 的类在 plugins/jcef-plugin 下，且从 2026.x 起拆进了 lib/modules
    $jcef = Join-Path $Root 'plugins\jcef-plugin\lib'
    if (Test-Path $jcef) {
        $jars += Get-ChildItem $jcef -Filter *.jar -ErrorAction SilentlyContinue
        $modules = Join-Path $jcef 'modules'
        if (Test-Path $modules) {
            $jars += Get-ChildItem $modules -Filter *.jar -ErrorAction SilentlyContinue
        }
    }

    if ($jars.Count -eq 0) {
        throw "在 $Root 下没有找到任何 jar，确认这是 IntelliJ IDEA 的安装目录"
    }
    return $jars | ForEach-Object { $_.FullName }
}

$jdk = Resolve-JdkHome -Explicit $JdkHome
$javac = Join-Path $jdk 'bin\javac.exe'
Write-Host "JDK    : $jdk"

$jars = Get-ClassPathJars -Root $IdePath
Write-Host "IDE    : $IdePath ($($jars.Count) jars)"

$buildFile = Join-Path $IdePath 'build.txt'
if (Test-Path $buildFile) {
    Write-Host "Build  : $((Get-Content $buildFile -Raw).Trim())"
}

$sourceRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'src\main\java'
$sources = Get-ChildItem $sourceRoot -Recurse -Filter *.java
if ($sources.Count -eq 0) {
    throw "没有找到源码：$sourceRoot"
}

if (Test-Path $OutDir) {
    Remove-Item $OutDir -Recurse -Force
}
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

# javac 的参数文件里反斜杠是转义字符，统一换成正斜杠
$toArgPath = { param($p) $p -replace '\\', '/' }
$classpath = ($jars | ForEach-Object { & $toArgPath $_ }) -join ';'

$argFile = Join-Path ([System.IO.Path]::GetTempPath()) 'webviewer-javac-args.txt'
$builder = New-Object System.Text.StringBuilder
[void]$builder.AppendLine('--release 17')
[void]$builder.AppendLine('-Xlint:deprecation')
[void]$builder.AppendLine("-d `"$(& $toArgPath $OutDir)`"")
[void]$builder.AppendLine("-cp `"$classpath`"")
foreach ($source in $sources) {
    [void]$builder.AppendLine("`"$(& $toArgPath $source.FullName)`"")
}
[System.IO.File]::WriteAllText($argFile, $builder.ToString(), (New-Object System.Text.UTF8Encoding($false)))

# javac 的告警写在 stderr，不能让 $ErrorActionPreference 把它当成终止错误
$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $javacOutput = (& $javac "@$argFile" 2>&1 | Out-String)
    $javacExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previous
}
if ($javacExit -ne 0) {
    Write-Host $javacOutput
    throw "针对 $IdePath 的编译验证失败（javac 退出码 $javacExit）"
}
if ($javacOutput -match 'deprecation') {
    Write-Host '提示   : 存在过时 API 使用（不影响编译，详见下方）' -ForegroundColor Yellow
    Write-Host $javacOutput
}

$classCount = (Get-ChildItem $OutDir -Recurse -Filter *.class).Count
Write-Host "OK     : 编译通过，生成 $classCount 个 class" -ForegroundColor Green
