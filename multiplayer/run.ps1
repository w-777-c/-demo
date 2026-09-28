param(
    [ValidateRange(1024, 65535)][int]$Port = 8080,
    [string]$BindAddress = '127.0.0.1',
    [switch]$Test,
    [switch]$BuildOnly,
    [string]$JdkPath,
    [string]$BuildDirectory,
    [string]$MavenPath
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot
$jdks = @()
if ($JdkPath) { $jdks += $JdkPath }
if ($env:JAVA_HOME) { $jdks += $env:JAVA_HOME }
$localJdks = Join-Path $repository '.tools\packaging-jdk'
if (Test-Path $localJdks) { $jdks += @(Get-ChildItem $localJdks -Directory | Select-Object -ExpandProperty FullName) }
$jdks += (Join-Path $repository '.tools\jdk')
$systemJava = Get-Command javac -ErrorAction SilentlyContinue
if ($systemJava) { $jdks += (Split-Path (Split-Path $systemJava.Source)) }
$selectedJdk = $jdks | Where-Object { Test-Path (Join-Path $_ 'bin\javac.exe') } | Select-Object -First 1
if (-not $selectedJdk) { throw 'Install JDK 17+ or pass -JdkPath.' }

$mavens = @()
if ($MavenPath) { $mavens += (Join-Path $MavenPath 'bin\mvn.cmd') }
$systemMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($systemMaven) { $mavens += $systemMaven.Source }
$localMaven = Join-Path $repository '.tools\maven'
if (Test-Path $localMaven) {
    $mavens += @(Get-ChildItem $localMaven -Directory | ForEach-Object { Join-Path $_.FullName 'bin\mvn.cmd' })
}
$maven = $mavens | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $maven) { throw 'Install Maven 3.9+ or pass -MavenPath.' }

$previousJavaHome = $env:JAVA_HOME
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = $selectedJdk
    $goal = if ($Test) { 'test' } else { 'package' }
    $buildOptions = @()
    if ($BuildDirectory) { $buildOptions += "-Darena.buildDirectory=$([System.IO.Path]::GetFullPath($BuildDirectory))" }
    & $maven --batch-mode --no-transfer-progress --settings (Join-Path $PSScriptRoot 'maven-settings.xml') @buildOptions $goal
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
    if ($Test -or $BuildOnly) { return }
    $outputDirectory = if ($BuildDirectory) { $BuildDirectory } else { Join-Path $PSScriptRoot 'target' }
    & (Join-Path $selectedJdk 'bin\java.exe') '-Dfile.encoding=UTF-8' -jar (Join-Path $outputDirectory 'arena-multiplayer-0.2.0.jar') "--server.port=$Port" "--server.address=$BindAddress"
    if ($LASTEXITCODE -ne 0) { throw 'Server exited with an error.' }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
