param(
    [string]$Device = "",
    [switch]$Release
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

# Override these two if your SDK/JDK live elsewhere.
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $env:JAVA_HOME = "C:\Users\Thief3\AppData\Local\Android\JDK\jdk-21.0.12.1+1"
}
if (-not $env:ANDROID_HOME -or -not (Test-Path (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"))) {
    $env:ANDROID_HOME = "C:\Users\Thief3\AppData\Local\Android\Sdk"
}

$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"

if ($Device) {
    & $adb connect $Device
}

Push-Location $root
try {
    if ($Release) {
        & .\gradlew.bat :app:assembleDebug
        & $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
    } else {
        & .\gradlew.bat :app:installDebug
    }
    & $adb shell am force-stop dev.matchday.tv.debug
    & $adb shell am start -n dev.matchday.tv.debug/dev.matchday.tv.MainActivity
} finally {
    Pop-Location
}
