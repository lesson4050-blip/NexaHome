$avdDir = "D:\Android\avd\Orbit_TV_Box.avd"
$iniFile = "D:\Android\avd\Orbit_TV_Box.ini"

if (-not (Test-Path $avdDir)) {
    New-Item -ItemType Directory -Path $avdDir -Force | Out-Null
}

$iniContent = @"
avd.ini.encoding=UTF-8
path=D:\Android\avd\Orbit_TV_Box.avd
path.rel=Orbit_TV_Box.avd
target=android-34
"@
Set-Content -Path $iniFile -Value $iniContent -Encoding UTF8

$configContent = @"
AvdId=Orbit_TV_Box
PlayStore.enabled=false
abi.type=x86
avd.ini.displayname=Orbit TV Box
avd.ini.encoding=UTF-8
disk.dataPartition.size=6G
fastboot.chosenSnapshotFile=
fastboot.forceChosenSnapshotBoot=no
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
hw.accelerometer=no
hw.arc=false
hw.audioInput=yes
hw.battery=no
hw.camera.back=none
hw.camera.front=none
hw.cpu.arch=x86
hw.cpu.ncore=4
hw.dPad=yes
hw.device.manufacturer=Generic
hw.device.name=tv_1080p
hw.gps=no
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.gyroscope=no
hw.initialOrientation=landscape
hw.keyboard=yes
hw.lcd.density=320
hw.lcd.height=1080
hw.lcd.width=1920
hw.mainKeys=yes
hw.ramSize=2048
hw.sdCard=yes
hw.sensors.orientation=no
hw.sensors.proximity=no
hw.trackBall=no
image.sysdir.1=system-images\android-34\android-tv\x86\
tag.display=Android TV
tag.id=android-tv
vm.heapSize=256
"@
Set-Content -Path "$avdDir\config.ini" -Value $configContent -Encoding UTF8

$env:ANDROID_AVD_HOME = "D:\Android\avd"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"

# Set permanently for user so Android Studio and CLI always know
[Environment]::SetEnvironmentVariable("ANDROID_AVD_HOME", "D:\Android\avd", "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "D:\Android\Sdk", "User")

Write-Host "Listing AVDs in D:\Android\avd:"
& "D:\Android\Sdk\emulator\emulator.exe" -list-avds
