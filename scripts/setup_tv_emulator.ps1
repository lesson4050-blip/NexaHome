# Script to install Android TV image strictly on Drive D:
$env:TEMP = "D:\Android\temp"
$env:TMP = "D:\Android\temp"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_AVD_HOME = "D:\Android\avd"
$env:ANDROID_HOME = "D:\Android\Sdk"

Write-Host "Checking Drive C before operation:"
Get-PSDrive C, D | Select-Object Name, @{N='FreeGB';E={[math]::Round($_.Free/1GB, 2)}}

Write-Host "Installing system-images/android-34/android-tv/x86 into D:\Android\Sdk..."
& "C:\Users\Kpf Kes\.android\bin\android-cli.exe" --sdk="D:\Android\Sdk" sdk install "system-images/android-34/android-tv/x86"

Write-Host "Checking Drive C after operation:"
Get-PSDrive C, D | Select-Object Name, @{N='FreeGB';E={[math]::Round($_.Free/1GB, 2)}}
