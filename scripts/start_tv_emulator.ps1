$env:TEMP = "D:\Android\temp"
$env:TMP = "D:\Android\temp"
$env:ANDROID_AVD_HOME = "D:\Android\avd"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"

$emulatorBin = "D:\Android\Sdk\emulator\emulator.exe"
$adbBin = "D:\Android\Sdk\platform-tools\adb.exe"

Write-Host "Starting Android TV Emulator (Orbit_TV_Box)..."
# Start emulator as detached process so it doesn't block PowerShell
$proc = Start-Process -FilePath $emulatorBin -ArgumentList "-avd", "Orbit_TV_Box", "-gpu", "auto", "-skin", "1920x1080" -PassThru

Write-Host "Waiting for device to appear in ADB..."
& $adbBin wait-for-device

Write-Host "Waiting for Android TV to boot completely..."
$booted = $false
for ($i = 0; $i -lt 60; $i++) {
    $res = & $adbBin shell getprop sys.boot_completed
    if ($res -match "1") {
        $booted = $true
        break
    }
    Start-Sleep -Seconds 2
}

if ($booted) {
    Write-Host "Orbit_TV_Box booted successfully!"
    & $adbBin devices
} else {
    Write-Host "Timed out waiting for boot, checking state:"
    & $adbBin devices
}
