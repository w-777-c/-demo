param(
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version = '1.2.0',
    [string]$JdkPath
)

$ErrorActionPreference = 'Stop'
$candidates = @()
if ($JdkPath) { $candidates += $JdkPath }
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$localJdks = Join-Path $PSScriptRoot '.tools\packaging-jdk'
if (Test-Path $localJdks) {
    $candidates += @(Get-ChildItem -LiteralPath $localJdks -Directory | Select-Object -ExpandProperty FullName)
}
$candidates += (Join-Path $PSScriptRoot '.tools\jdk')
$installedPackager = Get-Command jpackage -ErrorAction SilentlyContinue
if ($installedPackager) { $candidates += (Split-Path (Split-Path $installedPackager.Source)) }
$packagingJdk = $null
foreach ($candidate in $candidates) {
    $complete = $true
    foreach ($tool in @('java.exe', 'javac.exe', 'jpackage.exe', 'jlink.exe')) {
        if (-not (Test-Path (Join-Path $candidate "bin\$tool"))) { $complete = $false }
    }
    if ($complete -and (Test-Path (Join-Path $candidate 'jmods'))) {
        $packagingJdk = (Resolve-Path -LiteralPath $candidate).Path
        break
    }
}
if (-not $packagingJdk) { throw 'A full Windows x64 JDK 21+ with jpackage and jlink is required. Pass -JdkPath or set JAVA_HOME.' }
$releaseFile = Get-Content -LiteralPath (Join-Path $packagingJdk 'release')
if (-not ($releaseFile -match '^OS_ARCH="(amd64|x86_64)"$')) { throw 'This package targets Windows x64; select an x64 JDK.' }
$versionLine = ($releaseFile | Where-Object { $_ -match '^JAVA_VERSION=' })
if ($versionLine -notmatch '^JAVA_VERSION="(?<major>\d+)' -or [int]$Matches.major -lt 21) { throw 'Packaging requires JDK 21 or newer.' }

$releaseName = "IronArena-$Version-windows-x64"
$dist = Join-Path $PSScriptRoot 'dist'
$application = Join-Path $dist $releaseName
$zip = "$application.zip"
if ((Test-Path $application) -or (Test-Path $zip)) {
    throw "Release $Version already exists. Choose a new -Version to preserve existing releases and player saves."
}

Push-Location $PSScriptRoot
try {
    & (Join-Path $PSScriptRoot 'run.ps1') -BuildOnly -JdkPath $packagingJdk
    $staging = Join-Path $PSScriptRoot ('build\portable-' + [guid]::NewGuid().ToString('N'))
    $inputDirectory = Join-Path $staging 'input'
    $runtime = Join-Path $staging 'runtime'
    $imageDirectory = Join-Path $staging 'image'
    New-Item -ItemType Directory -Force $inputDirectory, $dist | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'build\fightinggame.jar') -Destination $inputDirectory
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'build\lib') -Destination $inputDirectory -Recurse
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'build\server') -Destination $inputDirectory -Recurse

    & (Join-Path $packagingJdk 'bin\jlink.exe') --add-modules ALL-MODULE-PATH --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output $runtime
    if ($LASTEXITCODE -ne 0) { throw 'Runtime image creation failed.' }
    & (Join-Path $packagingJdk 'bin\jpackage.exe') --type app-image --name IronArena --app-version $Version --vendor IronArena --description 'Iron Arena - Crimson Theatre' --icon (Join-Path $PSScriptRoot 'packaging\IronArena.ico') --input $inputDirectory --main-jar fightinggame.jar --main-class com.itheima.App --runtime-image $runtime --dest $imageDirectory --java-options '-Dfile.encoding=UTF-8' --java-options '-Dfightinggame.dataDir=$APPDIR/../data'
    if ($LASTEXITCODE -ne 0) { throw 'EXE packaging failed.' }

    $image = Join-Path $imageDirectory 'IronArena'
    New-Item -ItemType Directory -Path (Join-Path $image 'data') | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'packaging\README.txt') -Destination $image
    $licenses = Join-Path $image 'licenses'
    New-Item -ItemType Directory -Path $licenses | Out-Null
    Copy-Item -Path (Join-Path $PSScriptRoot 'fightinggame\resources\fonts\*-OFL.txt') -Destination $licenses
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fightinggame\resources\icons\LICENSE.txt') -Destination (Join-Path $licenses 'Lucide.txt')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fightinggame\resources\CREDITS.md') -Destination $licenses
    # Keep the runtime's legal directory and record the exact source JDK in the release.
    Copy-Item -LiteralPath (Join-Path $packagingJdk 'release') -Destination (Join-Path $image 'JAVA-RUNTIME.txt')
    $resolvedImage = (Resolve-Path -LiteralPath $image).Path
    $resolvedBuild = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'build')).Path
    $resolvedDist = (Resolve-Path -LiteralPath $dist).Path
    $resolvedApplication = [System.IO.Path]::GetFullPath($application)
    if (-not $resolvedImage.StartsWith($resolvedBuild + '\', [StringComparison]::OrdinalIgnoreCase) -or
        -not $resolvedApplication.StartsWith($resolvedDist + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Packaging output paths are outside the intended build and dist directories.'
    }
    Move-Item -LiteralPath $resolvedImage -Destination $resolvedApplication
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::CreateFromDirectory($application, $zip, [System.IO.Compression.CompressionLevel]::Optimal, $true)
    $checksum = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText("$zip.sha256", "$checksum  $releaseName.zip`n", [System.Text.Encoding]::ASCII)
    Write-Host "Portable application: $application\IronArena.exe"
    Write-Host "Distribution archive: $zip"
    Write-Host "SHA256: $checksum"
} finally { Pop-Location }
