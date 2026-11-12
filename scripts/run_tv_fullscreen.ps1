$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:ANDROID_AVD_HOME = "D:\Android\avd"
$env:TEMP = "D:\Android\temp"
$env:TMP = "D:\Android\temp"

$emulatorBin = "D:\Android\Sdk\emulator\emulator.exe"
$adbBin = "D:\Android\Sdk\platform-tools\adb.exe"

Write-Host "Killing any old emulator instances..."
Get-Process *qemu*, *emulator* -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 1

Write-Host "Launching Orbit TV Box (Cold Boot, 1080p)..."
Start-Process -FilePath $emulatorBin -ArgumentList "-avd", "Orbit_TV_Box", "-no-snapshot-load", "-gpu", "host"

Write-Host "Waiting for emulator window to appear..."
$code = @"
using System;
using System.Text;
using System.Runtime.InteropServices;

public class WinHelper {
    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    public static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern int SetWindowLong(IntPtr hWnd, int nIndex, int dwNewLong);

    [DllImport("user32.dll")]
    public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

    public const int GWL_STYLE = -16;
    public const int WS_VISIBLE = 0x10000000;
    public const int WS_POPUP = unchecked((int)0x80000000);
    public const uint SWP_FRAMECHANGED = 0x0020;
    public const uint SWP_SHOWWINDOW = 0x0040;

    public static bool MakeFullscreen() {
        bool found = false;
        EnumWindows((hWnd, lParam) => {
            if (IsWindowVisible(hWnd)) {
                StringBuilder sb = new StringBuilder(256);
                GetWindowText(hWnd, sb, 256);
                string title = sb.ToString();
                if (title.Contains("Orbit") || title.Contains("Android Emulator") || title.Contains("qemu")) {
                    SetWindowLong(hWnd, GWL_STYLE, WS_POPUP | WS_VISIBLE);
                    SetWindowPos(hWnd, (IntPtr)(-1), 0, 0, 1920, 1080, SWP_FRAMECHANGED | SWP_SHOWWINDOW);
                    found = true;
                }
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }
}
"@

Add-Type -TypeDefinition $code -Language CSharp

for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    if ([WinHelper]::MakeFullscreen()) {
        Write-Host "Window locked to Borderless Fullscreen (1920x1080)!"
        break
    }
}

Write-Host "Waiting for Android boot..."
& $adbBin wait-for-device
for ($i = 0; $i -lt 30; $i++) {
    $res = & $adbBin shell getprop sys.boot_completed
    if ($res -match "1") { break }
    Start-Sleep -Seconds 1
}

Write-Host "Launching Orbit Launcher..."
& $adbBin shell am start -n com.orbit.tv.launcher.debug/com.orbit.tv.launcher.ui.screens.HomeActivity

# Apply fullscreen again after boot to ensure it stays on top
Start-Sleep -Seconds 2
[WinHelper]::MakeFullscreen()
Write-Host "DONE! Fullscreen 1:1 TV display active."
