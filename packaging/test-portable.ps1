param(
    [string]$Archive = (Join-Path $PSScriptRoot '..\dist\IronArena-1.2.0-windows-x64.zip')
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$archivePath = (Resolve-Path -LiteralPath $Archive).Path
$buildRoot = Join-Path $projectRoot 'build'
New-Item -ItemType Directory -Force $buildRoot | Out-Null
$testRoot = Join-Path $buildRoot ('portable-check-' + [guid]::NewGuid().ToString('N'))
$unicodeFolder = ([string][char]0x6E38) + [char]0x620F + ' portable test'
$extracted = Join-Path $testRoot $unicodeFolder
New-Item -ItemType Directory -Force $extracted | Out-Null
Expand-Archive -LiteralPath $archivePath -DestinationPath $extracted
$applications = @(Get-ChildItem -LiteralPath $extracted -Directory)
if ($applications.Count -ne 1) { throw 'Expected one application directory in the ZIP.' }
$application = $applications[0].FullName
$executable = Join-Path $application 'IronArena.exe'
if (-not (Test-Path $executable)) { throw 'Missing IronArena.exe.' }
$accounts = Join-Path $application 'data\accounts.properties'
if (Test-Path $accounts) { throw 'The distribution archive contains player accounts.' }

function New-PortableProcess([string]$Arguments) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $executable
    $info.Arguments = $Arguments
    $info.WorkingDirectory = $testRoot
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $info.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    $info.EnvironmentVariables['PATH'] = Join-Path $env:SystemRoot 'System32'
    foreach ($key in @('JAVA_HOME', 'JDK_HOME', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')) {
        $info.EnvironmentVariables.Remove($key)
    }
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    return $process
}

function Invoke-Console([string]$InputText, [int]$ExpectedExitCode = 0) {
    $process = New-PortableProcess '--console'
    try {
        if (-not $process.Start()) { throw 'Could not start portable EXE.' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.Write($InputText)
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(15000)) { throw 'Portable console flow timed out.' }
        if ($process.ExitCode -ne $ExpectedExitCode) { throw "Unexpected exit code $($process.ExitCode): $($stderr.Result)" }
        return $stdout.Result
    } finally {
        if ($process.Id -and -not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
        $process.Dispose()
    }
}

$gui = $null
$windowProcess = $null
$completed = $false
try {
    Invoke-Console "2`nPortableTest`nabc123`nabc123`n0`n" | Out-Null
    if (-not (Test-Path $accounts)) { throw 'The launcher did not save beside the EXE.' }
    if (Test-Path (Join-Path $testRoot 'data')) { throw 'Data leaked into the working directory.' }
    Invoke-Console "1`nPortableTest`nabc123`n1`n1`n0`ny`n0`n" | Out-Null
    $lines = [System.IO.File]::ReadAllLines($accounts)
    if (-not ($lines -contains 'user.0.games=1')) { throw 'Login, game settlement or persistence failed.' }
    if ([System.IO.File]::ReadAllText($accounts).Contains('abc123')) { throw 'Plaintext test password was saved.' }
    Write-Host 'PASS: ZIP extraction, Unicode/space path, no system Java, registration, login and saved game.'

    $gui = New-PortableProcess ''
    if (-not $gui.Start()) { throw 'Could not launch portable GUI.' }
    $gui.StandardInput.Close()
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        if ($gui.HasExited) { throw "Portable GUI exited early: $($gui.StandardError.ReadToEnd())" }
        # The Windows jpackage launcher can host the JVM in a child process.
        $candidates = @($gui)
        $children = @(Get-CimInstance Win32_Process -Filter "Name='IronArena.exe'" |
                Where-Object { $_.ParentProcessId -eq $gui.Id -and $_.ExecutablePath -eq $executable })
        foreach ($child in $children) { $candidates += Get-Process -Id $child.ProcessId -ErrorAction SilentlyContinue }
        foreach ($candidate in $candidates) {
            $candidate.Refresh()
            if ($candidate.MainWindowHandle -ne [IntPtr]::Zero -and $candidate.MainWindowTitle -like '*IRON ARENA*') {
                $windowProcess = $candidate
                break
            }
        }
        if ($windowProcess) { break }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    if (-not $windowProcess) { throw 'Game window did not appear.' }
    Invoke-Console "0`n" 1 | Out-Null
    Write-Host 'PASS: portable GUI opened and a second process respected the save-directory lock.'

    Add-Type -AssemblyName System.Drawing
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class PortableWindowCapture {
    [StructLayout(LayoutKind.Sequential)]
    public struct Rect { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr window, out Rect rect);
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr window);
}
'@
    [PortableWindowCapture]::SetForegroundWindow($windowProcess.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 500
    $bounds = New-Object PortableWindowCapture+Rect
    if (-not [PortableWindowCapture]::GetWindowRect($windowProcess.MainWindowHandle, [ref]$bounds)) { throw 'Could not read game-window bounds.' }
    $bitmap = New-Object System.Drawing.Bitmap(($bounds.Right - $bounds.Left), ($bounds.Bottom - $bounds.Top))
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CopyFromScreen($bounds.Left, $bounds.Top, 0, 0, $bitmap.Size)
        $bitmap.Save((Join-Path $buildRoot 'portable-exe.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose() }
    if (-not $windowProcess.CloseMainWindow() -or -not $gui.WaitForExit(10000)) { throw 'Game window did not close normally.' }
    if ($gui.ExitCode -ne 0) { throw "Portable GUI exited with code $($gui.ExitCode)." }
    Write-Host 'PASS: clean GUI shutdown; screenshot saved as build/portable-exe.png.'
    $network = New-PortableProcess ''
    try {
        $network.StartInfo.FileName = Join-Path $application 'runtime\bin\java.exe'
        $source = Join-Path $projectRoot 'fightinggame\test\com\itheima\NetworkSmokeTest.java'
        $classpath = (Join-Path $application 'app\fightinggame.jar') + ';' + (Join-Path $application 'app\lib\*')
        $screenshots = Join-Path $buildRoot 'packaged-screenshots'
        $network.StartInfo.Arguments = "-Dfile.encoding=UTF-8 `"-Darena.testOutput=$screenshots`" -ea -cp `"$classpath`" `"$source`""
        if (-not $network.Start()) { throw 'Could not start bundled-runtime network checks.' }
        $stdout = $network.StandardOutput.ReadToEndAsync()
        $stderr = $network.StandardError.ReadToEndAsync()
        $network.StandardInput.Close()
        if (-not $network.WaitForExit(90000)) { throw 'Bundled network checks timed out.' }
        if ($network.ExitCode -ne 0) { throw "Bundled network checks failed: $($stderr.Result) $($stdout.Result)" }
        Write-Host $stdout.Result
    } finally {
        if ($network.Id -and -not $network.HasExited) { $network.Kill(); $network.WaitForExit() }
        $network.Dispose()
    }
    $completed = $true
} finally {
    if ($gui) {
        if ($gui.Id -and -not $gui.HasExited) { $gui.Kill(); $gui.WaitForExit() }
        $gui.Dispose()
    }
    if ($completed) {
        $resolvedTestRoot = (Resolve-Path -LiteralPath $testRoot).Path
        $resolvedBuildRoot = (Resolve-Path -LiteralPath $buildRoot).Path
        if (-not $resolvedTestRoot.StartsWith($resolvedBuildRoot + '\portable-check-', [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to remove a test directory outside the build directory.'
        }
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
    } else { Write-Host "Failed-test files retained: $testRoot" }
}
