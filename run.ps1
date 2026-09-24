param(
    [switch]$Console,
    [switch]$Test,
    [switch]$GuiTest,
    [switch]$BuildOnly
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$jdkBin = $null
$candidates = @()
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

$classes = Join-Path $PSScriptRoot 'build\classes'
New-Item -ItemType Directory -Force $classes | Out-Null
Get-ChildItem -LiteralPath $classes -Recurse -Filter '*.class' | Remove-Item -Force
$sources = @(Get-ChildItem 'fightinggame\src' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& (Join-Path $jdkBin 'javac.exe') --release 17 -encoding UTF-8 -Xlint:all -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = Join-Path $PSScriptRoot 'build\fightinggame.jar'
$jarStream = [System.IO.File]::Open($jarPath, [System.IO.FileMode]::Create)
$archive = New-Object System.IO.Compression.ZipArchive($jarStream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $manifest = $archive.CreateEntry('META-INF/MANIFEST.MF')
    $writer = New-Object System.IO.StreamWriter($manifest.Open(), [System.Text.Encoding]::ASCII)
    $writer.Write("Manifest-Version: 1.0`r`nMain-Class: com.itheima.App`r`n`r`n")
    $writer.Dispose()
    foreach ($classFile in (Get-ChildItem -LiteralPath $classes -Recurse -File)) {
        $entryName = $classFile.FullName.Substring($classes.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $classFile.FullName, $entryName) | Out-Null
    }
} finally { $archive.Dispose(); $jarStream.Dispose() }

if ($Test -or $GuiTest) {
    $testClasses = Join-Path $PSScriptRoot 'build\test-classes'
    New-Item -ItemType Directory -Force $testClasses | Out-Null
    $testSources = @(Get-ChildItem 'fightinggame\test' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    & (Join-Path $jdkBin 'javac.exe') --release 17 -encoding UTF-8 -cp $classes -d $testClasses @testSources
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    $testMain = if ($GuiTest) { 'com.itheima.GuiSmokeTest' } else { 'com.itheima.GameTests' }
    & (Join-Path $jdkBin 'java.exe') '-Dfile.encoding=UTF-8' -ea -cp "$classes;$testClasses" $testMain
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed.' }
    return
}
if ($BuildOnly) { Write-Host 'Built build/fightinggame.jar'; return }
if ($Console) {
    & (Join-Path $jdkBin 'java.exe') '-Dfile.encoding=UTF-8' -jar 'build\fightinggame.jar' --console
    if ($LASTEXITCODE -ne 0) { throw 'Game exited with an error.' }
} else {
    Start-Process -FilePath (Join-Path $jdkBin 'javaw.exe') -ArgumentList '-Dfile.encoding=UTF-8', '-jar', 'build\fightinggame.jar' -WorkingDirectory $PSScriptRoot
}
