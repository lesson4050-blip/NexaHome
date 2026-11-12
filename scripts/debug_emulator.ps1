$env:TEMP = "D:\Android\temp"
$env:TMP = "D:\Android\temp"
$env:ANDROID_AVD_HOME = "D:\Android\avd"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"

& "D:\Android\Sdk\emulator\emulator.exe" -avd Orbit_TV_Box -no-window -no-audio -verbose
