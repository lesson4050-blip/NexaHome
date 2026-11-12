@echo off
title Orbit TV Launcher
echo ==============================================
echo   Starting Orbit TV Box (Windowed Mode)
echo ==============================================

set "ANDROID_SDK_ROOT=D:\Android\Sdk"
set "ANDROID_HOME=D:\Android\Sdk"
set "ANDROID_AVD_HOME=D:\Android\avd"
set "TEMP=D:\Android\temp"
set "TMP=D:\Android\temp"
set "ADB=D:\Android\Sdk\platform-tools\adb.exe"

start "Orbit TV Box" "D:\Android\Sdk\emulator\emulator.exe" -avd Orbit_TV_Box -gpu host

echo Waiting for device...
"%ADB%" wait-for-device

echo Waiting for Android TV to finish boot...
:wait_boot
timeout /t 2 >nul
"%ADB%" shell getprop sys.boot_completed | findstr "1" >nul
if errorlevel 1 goto wait_boot

echo Android TV is ready!
echo Installing latest Orbit Launcher build...
"%ADB%" install -r "d:\TV\app\build\outputs\apk\debug\app-debug.apk"

echo Disabling Google stock launcher so Orbit is the ONLY launcher...
"%ADB%" shell pm disable-user --user 0 com.google.android.apps.tv.launcherx >nul 2>&1
"%ADB%" shell pm disable-user --user 0 com.google.android.tungsten.setupwraith >nul 2>&1

echo Launching Orbit TV Launcher...
"%ADB%" shell am start -n com.orbit.tv.launcher.debug/com.orbit.tv.launcher.ui.screens.HomeActivity

echo ==============================================
echo   Orbit TV Launcher is running!
echo ==============================================
timeout /t 3 >nul
exit
