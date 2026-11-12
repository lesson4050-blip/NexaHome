$env:TEMP = "D:\Android\temp"
$env:TMP = "D:\Android\temp"
$env:ANDROID_AVD_HOME = "D:\Android\avd"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"

Write-Host "Launching Orbit_TV_Box with visible GUI window..."
Start-Process -FilePath "D:\Android\Sdk\emulator\emulator.exe" -ArgumentList "-avd", "Orbit_TV_Box", "-gpu", "host"

Write-Host "Waiting for device to connect to ADB..."
& "D:\Android\Sdk\platform-tools\adb.exe" wait-for-device

Write-Host "Waiting for boot completion..."
for ($i = 0; $i -lt 30; $i++) {
    $res = & "D:\Android\Sdk\platform-tools\adb.exe" shell getprop sys.boot_completed
    if ($res -match "1") { break }
    Start-Sleep -Seconds 1
}

Write-Host "Launching Orbit Launcher on screen..."
& "D:\Android\Sdk\platform-tools\adb.exe" shell am start -n com.orbit.tv.launcher.debug/com.orbit.tv.launcher.ui.screens.HomeActivity
Write-Host "Done! The emulator window is visible on your monitor."
