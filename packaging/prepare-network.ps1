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
        $destination = Join-Path $libraryDirectory "$name.jar"
        $temporary = Join-Path $libraryDirectory "$name-$([guid]::NewGuid().ToString('N')).tmp"
        $backup = Join-Path $libraryDirectory "$name-$([guid]::NewGuid().ToString('N')).bak"
        try {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entries[0], $temporary)
            $sameContent = (Test-Path -LiteralPath $destination) -and
                (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -eq
                (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
            if ($sameContent) {
                Remove-Item -LiteralPath $temporary -Force
            } elseif (Test-Path -LiteralPath $destination) {
                [System.IO.File]::Replace($temporary, $destination, $backup)
            } else {
                [System.IO.File]::Move($temporary, $destination)
            }
        } catch [System.IO.IOException] {
            throw "Cannot update $destination while the running game or server is using it. Close that process and retry. $($_.Exception.Message)"
        } finally {
            if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
            if (Test-Path -LiteralPath $backup) { Remove-Item -LiteralPath $backup -Force }
        }
    }
} finally { $archive.Dispose() }
