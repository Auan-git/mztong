# 生成 app 内置的学习资料目录快照（Gitee API 被限流时 App 用它兜底）
# 用法：powershell -ExecutionPolicy Bypass -File tools\generate_materials_snapshot.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$materialsRoot = Join-Path $root "学习资料"
$out = Join-Path $root "app\src\main\assets\materials_tree_snapshot.json"
if (-not (Test-Path $materialsRoot)) { Write-Host "缺少 学习资料 目录"; exit 1 }
$entries = @()
Get-ChildItem $materialsRoot -Recurse -Force | Sort-Object FullName | ForEach-Object {
    $rel = ($_.FullName.Substring($materialsRoot.Length + 1) -replace '\\', '/')
    if ($_.PSIsContainer) {
        $entries += [pscustomobject]@{ path = "学习资料/$rel"; type = "tree"; size = 0 }
    } else {
        $entries += [pscustomobject]@{ path = "学习资料/$rel"; type = "blob"; size = [int64]$_.Length }
    }
}
$snapshot = [pscustomobject]@{ generatedAt = (Get-Date).ToString("o"); branch = "master"; entries = $entries }
$json = $snapshot | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText($out, $json + "`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Host "已生成 $out（$($entries.Count) 条）"