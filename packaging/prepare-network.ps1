param([string]$JdkPath, [switch]$ReuseServer)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot
$serverDirectory = Join-Path $repository 'build\server'
$serverJar = Join-Path $serverDirectory 'arena-server.jar'
New-Item -ItemType Directory -Force $serverDirectory | Out-Null
if (-not $ReuseServer -or -not (Test-Path -LiteralPath $serverJar)) {
    $isolatedBuild = Join-Path $repository ('build\server-build-' + [guid]::NewGuid().ToString('N'))
    & (Join-Path $repository 'multiplayer\run.ps1') -BuildOnly -JdkPath $JdkPath -BuildDirectory $isolatedBuild
    Copy-Item -LiteralPath (Join-Path $isolatedBuild 'arena-multiplayer-0.2.0.jar') -Destination $serverJar -Force
}
$libraryDirectory = Join-Path $repository 'build\lib'
New-Item -ItemType Directory -Force $libraryDirectory | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($serverJar)
try {
    foreach ($name in @('jackson-core', 'jackson-annotations', 'jackson-databind')) {
        $entries = @($archive.Entries | Where-Object { $_.FullName -match "^BOOT-INF/lib/$name-[0-9].*\.jar$" })
        if ($entries.Count -ne 1) { throw "Expected one $name dependency in server archive." }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entries[0], (Join-Path $libraryDirectory "$name.jar"), $true)
    }
} finally { $archive.Dispose() }
