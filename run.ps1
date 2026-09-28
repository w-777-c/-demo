param(
    [switch]$Console,
    [switch]$Test,
    [switch]$GuiTest,
    [switch]$NetworkTest,
    [switch]$BuildOnly,
    [switch]$ReuseServer,
    [string]$JdkPath
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$jdkBin = $null
$candidates = @()
if ($JdkPath) { $candidates += (Join-Path $JdkPath 'bin') }
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin') }
$candidates += (Join-Path $PSScriptRoot '.tools\jdk\bin')
$installedCompiler = Get-Command javac -ErrorAction SilentlyContinue
if ($installedCompiler) { $candidates += (Split-Path $installedCompiler.Source) }
foreach ($candidate in $candidates) {
    if ((Test-Path (Join-Path $candidate 'javac.exe')) -and (Test-Path (Join-Path $candidate 'java.exe'))) {
        $jdkBin = $candidate
        break
    }
}
if (-not $jdkBin) { throw 'JDK 17+ is required. Set JAVA_HOME to your JDK installation.' }
& (Join-Path $PSScriptRoot 'packaging\prepare-network.ps1') -JdkPath (Split-Path $jdkBin) -ReuseServer:$ReuseServer
$libraries = Join-Path $PSScriptRoot 'build\lib\*'

$classes = Join-Path $PSScriptRoot 'build\classes'
if (Test-Path -LiteralPath $classes) {
    $resolvedClasses = (Resolve-Path -LiteralPath $classes).Path
    $resolvedBuild = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build'))
    if (-not $resolvedClasses.StartsWith($resolvedBuild + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Classes directory is outside build.' }
    Remove-Item -LiteralPath $resolvedClasses -Recurse -Force
}
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = @(Get-ChildItem 'fightinggame\src' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& (Join-Path $jdkBin 'javac.exe') --release 17 -encoding UTF-8 -Xlint:all -cp $libraries -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
$resources = Join-Path $PSScriptRoot 'fightinggame\resources'
if (Test-Path -LiteralPath $resources) {
    Copy-Item -Path (Join-Path $resources '*') -Destination $classes -Recurse -Force
}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = Join-Path $PSScriptRoot 'build\fightinggame.jar'
$jarStream = [System.IO.File]::Open($jarPath, [System.IO.FileMode]::Create)
$archive = New-Object System.IO.Compression.ZipArchive($jarStream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $manifest = $archive.CreateEntry('META-INF/MANIFEST.MF')
    $writer = New-Object System.IO.StreamWriter($manifest.Open(), [System.Text.Encoding]::ASCII)
    $writer.Write("Manifest-Version: 1.0`r`nMain-Class: com.itheima.App`r`nClass-Path: lib/jackson-core.jar lib/jackson-annotations.jar `r`n lib/jackson-databind.jar`r`n`r`n")
    $writer.Dispose()
    foreach ($classFile in (Get-ChildItem -LiteralPath $classes -Recurse -File)) {
        $entryName = $classFile.FullName.Substring($classes.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $classFile.FullName, $entryName) | Out-Null
    }
} finally { $archive.Dispose(); $jarStream.Dispose() }

if ($Test -or $GuiTest -or $NetworkTest) {
    $testClasses = Join-Path $PSScriptRoot 'build\test-classes'
    New-Item -ItemType Directory -Force $testClasses | Out-Null
    $testSources = @(Get-ChildItem 'fightinggame\test' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    & (Join-Path $jdkBin 'javac.exe') --release 17 -encoding UTF-8 -cp "$classes;$libraries" -d $testClasses @testSources
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    $testMain = if ($NetworkTest) { 'com.itheima.NetworkSmokeTest' } elseif ($GuiTest) { 'com.itheima.GuiSmokeTest' } else { 'com.itheima.GameTests' }
    & (Join-Path $jdkBin 'java.exe') '-Dfile.encoding=UTF-8' -ea -cp "$classes;$testClasses;$libraries" $testMain
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed.' }
    if ($Test) {
        & (Join-Path $jdkBin 'java.exe') '-Dfile.encoding=UTF-8' -ea -cp "$classes;$testClasses;$libraries" com.itheima.ui.UiResourceTests
        if ($LASTEXITCODE -ne 0) { throw 'Presentation tests failed.' }
    }
    return
}
if ($BuildOnly) { Write-Host 'Built build/fightinggame.jar'; return }
if ($Console) {
    & (Join-Path $jdkBin 'java.exe') '-Dfile.encoding=UTF-8' -jar 'build\fightinggame.jar' --console
    if ($LASTEXITCODE -ne 0) { throw 'Game exited with an error.' }
} else {
    Start-Process -FilePath (Join-Path $jdkBin 'javaw.exe') -ArgumentList '-Dfile.encoding=UTF-8', '-jar', 'build\fightinggame.jar' -WorkingDirectory $PSScriptRoot -WindowStyle Hidden
}
