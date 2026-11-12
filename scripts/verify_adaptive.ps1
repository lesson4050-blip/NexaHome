$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:ANDROID_AVD_HOME = "D:\Android\avd"
$env:TEMP = "D:\Android\temp"
$env:TMP = "D:\Android\temp"

$emulatorBin = "D:\Android\Sdk\emulator\emulator.exe"
$adbBin = "D:\Android\Sdk\platform-tools\adb.exe"

# Check if emulator already running
$running = & $adbBin devices | Select-String "device$"
if (-not $running) {
    Write-Host "Starting emulator..."
    Start-Process -FilePath $emulatorBin -ArgumentList "-avd", "Orbit_TV_Box", "-no-snapshot-load", "-gpu", "host"
    & $adbBin wait-for-device
    for ($i = 0; $i -lt 30; $i++) {
        $res = & $adbBin shell getprop sys.boot_completed
        if ($res -match "1") { break }
        Start-Sleep -Seconds 1
    }
}

Write-Host "Installing updated adaptive APK..."
& $adbBin install -r "d:\TV\app\build\outputs\apk\debug\app-debug.apk"

Write-Host "Launching HomeActivity..."
& $adbBin shell am start -n com.orbit.tv.launcher.debug/com.orbit.tv.launcher.ui.screens.HomeActivity

Start-Sleep -Seconds 3

Write-Host "Capturing screenshot..."
cmd.exe /c "$adbBin exec-out screencap -p > d:\TV\emulator_adaptive_verified.png"

Copy-Item "d:\TV\emulator_adaptive_verified.png" "C:\Users\Kpf Kes\Desktop\OrbitTV_Adaptive_Preview.png" -Force
Write-Host "Screenshot saved to Desktop: OrbitTV_Adaptive_Preview.png"
