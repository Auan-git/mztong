# 在电脑上预览：把 dist 目录里最新的 APK 安装到正在运行的 Android 模拟器并启动
#
# 用法（在项目根目录执行）：
#   powershell -ExecutionPolicy Bypass -File tools\preview_on_pc.ps1
# 或指定 APK：
#   powershell -ExecutionPolicy Bypass -File tools\preview_on_pc.ps1 -ApkPath "D:\some\app.apk"
param(
    [string]$ApkPath = ""
)

# 不启用 Stop：adb 会向 stderr 输出“daemon not running”等提示，PowerShell 5.1 会把它当成错误
$root = Split-Path -Parent $PSScriptRoot

if (-not $ApkPath) {
    $ApkPath = Get-ChildItem (Join-Path $root "dist") -Filter "*.apk" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $ApkPath -or -not (Test-Path $ApkPath)) {
    Write-Host "dist 目录里没有 APK。请先构建：gradlew.bat :app:assembleDebug"
    exit 1
}

$candidates = @("D:\apk\platform-tools\adb.exe")
if ($env:LOCALAPPDATA) {
    $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
}
if ($env:ANDROID_HOME) {
    $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe")
}
$adbCandidates = $candidates | Where-Object { $_ -and (Test-Path $_) }

foreach ($adb in $adbCandidates) {
    # ApkStart 默认用 5044 端口起 adb server；官方 SDK 默认 5037。
    foreach ($port in @(5044, 5037, 5555)) {
        $devices = & $adb -P $port devices 2>$null
        $line = $devices | Where-Object { $_ -match "^emulator-\d+\s+device" } | Select-Object -First 1
        if (-not $line) { continue }

        $serial = ($line -split "\s+")[0]
        Write-Host "找到模拟器 $serial（adb 端口 $port），正在安装：$ApkPath"
        & $adb -P $port -s $serial install -r $ApkPath | Out-Host
        & $adb -P $port -s $serial shell am start -n "com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity" | Out-Host
        Write-Host "已启动。去电脑上的模拟器窗口（标题会显示「民职通」）查看即可。"
        exit 0
    }
}

Write-Host "没有找到正在运行的 Android 模拟器。"
Write-Host "请先启动模拟器（例如打开 ApkStart，或执行 D:\apk\emulator\emulator.exe -avd apkstart），再运行本脚本。"
exit 1