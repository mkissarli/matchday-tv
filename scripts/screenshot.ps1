param(
    [string]$Out = "captures\screen.png"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = "C:\Users\Thief3\AppData\Local\Android\Sdk"
}
$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$path = if ([System.IO.Path]::IsPathRooted($Out)) { $Out } else { Join-Path $root $Out }
$dir = Split-Path -Parent $path
if ($dir) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

# cmd.exe preserves the PNG bytes; PowerShell redirection can change them on Windows PowerShell 5.
cmd.exe /c "`"$adb`" exec-out screencap -p > `"$path`""
if ($LASTEXITCODE -ne 0) { throw "screencap failed" }
Write-Host "saved $path"
