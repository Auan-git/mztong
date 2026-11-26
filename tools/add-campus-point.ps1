<#
.SYNOPSIS
    民职通校园地图点位生成器：录入地点名称 + 高德坐标，自动生成/追加 campus_points.json。

.DESCRIPTION
    支持交互式录入或命令行批量录入；坐标为高德 GCJ-02（与 campus_map.html 一致）。
    数据默认写入 app/src/main/assets/campus_points.json，供「校园地图」固定点位使用。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1 `
      -Campus "大兴校区" -Name "第一食堂" -Lng 116.349512 -Lat 39.754678 `
      -Category "餐饮" -Note "东门入口" -Aliases "一食堂,食堂"

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1 -List
    powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1 -Remove "dx-001"
    powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1 -Validate
    powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1 -Csv tools\points.csv
#>
param(
    [string]$Path,
    [string]$Campus,
    [string]$Name,
    [double]$Lng = 0,
    [double]$Lat = 0,
    [string]$Category = "其他",
    [string]$Note = "",
    [string[]]$Aliases = @(),
    [string]$Id,
    [switch]$List,
    [string]$Remove,
    [string]$Csv,
    [switch]$Validate,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($Path)) {
    $Path = Join-Path $repoRoot "app\src\main\assets\campus_points.json"
}

# 脚本自身用 UTF-8 BOM 保存（兼容 Windows PowerShell 5.1 中文）；
# 生成的 JSON 用 UTF-8 无 BOM（Gson / Android assets 读取更稳妥）。
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$categoryMap = [ordered]@{
    "1" = "餐饮"
    "2" = "学习"
    "3" = "运动"
    "4" = "宿舍"
    "5" = "校门"
    "6" = "实训"
    "7" = "行政"
    "8" = "其他"
}

function Read-Points {
    param([string]$FilePath)
    if (-not (Test-Path $FilePath)) { return @() }
    $raw = Get-Content -Path $FilePath -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($raw)) { return @() }
    try {
        $data = $raw | ConvertFrom-Json
    } catch {
        throw "JSON 解析失败（$FilePath）：$($_.Exception.Message)。请先修复或备份后重试。"
    }
    if ($null -eq $data) { return @() }
    # ConvertFrom-Json 对单元素数组会返回单个对象，这里统一包成数组。
    return @($data)
}

function ConvertTo-JsonArray {
    param([object[]]$Points)
    if ($null -eq $Points -or $Points.Count -eq 0) { return "[]" }
    $parts = foreach ($p in $Points) {
        $json = $p | ConvertTo-Json -Depth 10
        # 不同 PowerShell 版本的缩进/空格不一致：统一成 2 空格缩进后的常见格式，便于阅读。
        $json = $json -replace '"([A-Za-z0-9_]+)":\s+', '"$1": '
        $json = $json -replace '\[\s*\]', '[]'
        $json
    }
    return "[" + ($parts -join ",`n") + "]"
}

function Write-Points {
    param([string]$FilePath, [object[]]$Points)
    $dir = Split-Path -Parent $FilePath
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $json = ConvertTo-JsonArray -Points $Points
    [System.IO.File]::WriteAllText($FilePath, $json + "`n", $utf8NoBom)
}

function Normalize-Campus {
    param([string]$Value)
    $v = $Value.Trim()
    if ($v -eq "" -or $v -eq "1") { return "大兴校区" }
    if ($v -eq "2") { return "燕郊校区" }
    if ($v -match "大兴") { return "大兴校区" }
    if ($v -match "燕郊") { return "燕郊校区" }
    return $v
}

function Get-CampusCode {
    param([string]$CampusName)
    if ($CampusName -match "大兴") { return "dx" }
    if ($CampusName -match "燕郊") { return "yj" }
    return "xy"
}

function Get-NextId {
    param([object[]]$Points, [string]$CampusName)
    $prefix = Get-CampusCode -CampusName $CampusName
    $max = 0
    foreach ($p in $Points) {
        $id = [string]$p.id
        if ($id -match "^$prefix-(\d+)$") {
            $n = [int]$Matches[1]
            if ($n -gt $max) { $max = $n }
        }
    }
    return ("{0}-{1:000}" -f $prefix, ($max + 1))
}

function Test-Coordinate {
    param([double]$Longitude, [double]$Latitude, [string]$Name)
    if ($Longitude -eq 0 -or $Latitude -eq 0) { return "缺少经度或纬度" }
    if ($Longitude -lt 73 -or $Longitude -gt 136) { return "经度 $Longitude 超出中国范围（73~136）" }
    if ($Latitude -lt 3 -or $Latitude -gt 54) { return "纬度 $Latitude 超出中国范围（3~54）" }
    if (($Longitude.ToString("F6") -match "\.0{6}$") -and ($Latitude.ToString("F6") -match "\.0{6}$")) {
        return "$Name 坐标看起来像默认值，请用高德坐标拾取器重新取点"
    }
    return $null
}

function New-Point {
    param(
        [string]$CampusName,
        [string]$PlaceName,
        [double]$Longitude,
        [double]$Latitude,
        [string]$CategoryName,
        [string]$Remark,
        [string[]]$AliasList,
        [string]$ExplicitId
    )
    $cleanAliases = @()
    foreach ($a in $AliasList) {
        if ($null -eq $a) { continue }
        foreach ($piece in ([string]$a -split "[,，、;；]")) {
            $t = $piece.Trim()
            if ($t) { $cleanAliases += $t }
        }
    }
    $point = [ordered]@{
        id       = $ExplicitId
        campus   = $CampusName
        name     = $PlaceName.Trim()
        category = $CategoryName.Trim()
        lng      = [math]::Round($Longitude, 6)
        lat      = [math]::Round($Latitude, 6)
        note     = $Remark.Trim()
        aliases  = @($cleanAliases | Select-Object -Unique)
    }
    return [pscustomobject]$point
}

function Add-Point {
    param([object]$Point, [switch]$SkipConfirm)
    $points = @(Read-Points -FilePath $Path)

    $dupName = @($points | Where-Object { $_.campus -eq $Point.campus -and $_.name -eq $Point.name })
    if ($dupName.Count -gt 0) {
        if (-not $Force) {
            throw "【$($Point.campus)】已存在同名点位「$($Point.name)」（id=$($dupName[0].id)）；如需覆盖请加 -Force，或先 -Remove。"
        }
        # -Force 的含义是覆盖同名点位：保留原 id，替换其他字段。
        if (-not $Point.id) { $Point.id = $dupName[0].id }
        $points = @($points | Where-Object { $_.id -ne $dupName[0].id })
        Write-Host "已存在同名点位，按 -Force 覆盖：$($dupName[0].id)" -ForegroundColor Yellow
    }
    $near = $points | Where-Object {
        $_.campus -eq $Point.campus -and
        [math]::Abs([double]$_.lng - [double]$Point.lng) -lt 0.00003 -and
        [math]::Abs([double]$_.lat - [double]$Point.lat) -lt 0.00003
    }
    if ($near -and -not $Force) {
        Write-Host "⚠ 这个位置附近已有点位：$((@($near))[0].name)（$((@($near))[0].id)），如果不是同一地点可继续。" -ForegroundColor Yellow
    }

    if (-not $Point.id) {
        $Point.id = Get-NextId -Points $points -CampusName $Point.campus
    }
    if ($points | Where-Object { $_.id -eq $Point.id }) {
        throw "点位 id 已存在：$($Point.id)"
    }

    if (-not $SkipConfirm) {
        Write-Host ""
        Write-Host "即将写入："
        Write-Host ("  校区：{0}" -f $Point.campus)
        Write-Host ("  名称：{0}（{1}）" -f $Point.name, $Point.category)
        Write-Host ("  坐标：{0}, {1}" -f $Point.lng, $Point.lat)
        if ($Point.note)  { Write-Host ("  备注：{0}" -f $Point.note) }
        if ($Point.aliases.Count -gt 0) { Write-Host ("  别名：{0}" -f ($Point.aliases -join "、")) }
        $confirm = Read-Host "确认写入？(Y/n)"
        if ($confirm -and $confirm -notmatch "^(y|Y|是|确认)$") { Write-Host "已取消。"; return }
    }

    $points += $Point
    Write-Points -FilePath $Path -Points $points
    Write-Host "✅ 已写入 $Path（共 $($points.Count) 个点位，本次 id=$($Point.id)）" -ForegroundColor Green
}

function Show-Points {
    $points = @(Read-Points -FilePath $Path)
    if ($points.Count -eq 0) { Write-Host "暂无点位。"; return }
    Write-Host ("共 {0} 个点位：{1}" -f $points.Count, $Path)
    $format = "{0,-8}  {1,-10}  {2,-14}  {3,-6}  {4,12}  {5,12}  {6}"
    Write-Host ($format -f "id", "校区", "名称", "分类", "经度", "纬度", "备注")
    Write-Host ("-" * 100)
    foreach ($p in $points) {
        Write-Host ($format -f $p.id, $p.campus, $p.name, $p.category, ([double]$p.lng).ToString("F6"), ([double]$p.lat).ToString("F6"), $p.note)
    }
}

function Remove-Point {
    param([string]$PointId)
    $points = @(Read-Points -FilePath $Path)
    $target = $points | Where-Object { $_.id -eq $PointId }
    if (-not $target) { throw "找不到 id=$PointId" }
    $remaining = @($points | Where-Object { $_.id -ne $PointId })
    Write-Points -FilePath $Path -Points $remaining
    Write-Host "✅ 已删除「$($target[0].campus) / $($target[0].name)」（id=$PointId），剩余 $($remaining.Count) 个" -ForegroundColor Green
}

function Test-Points {
    $points = @(Read-Points -FilePath $Path)
    $errors = @()
    $ids = @{}
    foreach ($p in $points) {
        $label = "id=$($p.id) name=$($p.name)"
        foreach ($field in @("id", "campus", "name", "category", "lng", "lat")) {
            if ($null -eq $p.$field -or [string]::IsNullOrWhiteSpace([string]$p.$field)) {
                $errors += "$label 缺少字段 $field"
            }
        }
        if ($ids.ContainsKey([string]$p.id)) { $errors += "$label id 重复" } else { $ids[[string]$p.id] = $true }
        $coordError = Test-Coordinate -Longitude ([double]$p.lng) -Latitude ([double]$p.lat) -Name ([string]$p.name)
        if ($coordError) { $errors += "$label $coordError" }
    }
    if ($errors.Count -eq 0) {
        Write-Host "✅ 校验通过：$($points.Count) 个点位" -ForegroundColor Green
    } else {
        Write-Host "❌ 发现 $($errors.Count) 个问题：" -ForegroundColor Red
        $errors | ForEach-Object { Write-Host "  - $_" }
        exit 1
    }
}

function Import-CsvPoints {
    param([string]$CsvPath)
    if (-not (Test-Path $CsvPath)) { throw "找不到 CSV：$CsvPath" }
    $rows = Import-Csv -Path $CsvPath -Encoding UTF8
    if (-not $rows -or $rows.Count -eq 0) { throw "CSV 为空：$CsvPath" }
    $imported = 0
    foreach ($row in $rows) {
        $campusName = Normalize-Campus ([string]$row.campus)
        $placeName = ([string]$row.name).Trim()
        if (-not $placeName) { throw "第 $($imported + 2) 行缺少 name" }
        $longitude = [double]::Parse(([string]$row.lng).Trim(), [System.Globalization.CultureInfo]::InvariantCulture)
        $latitude = [double]::Parse(([string]$row.lat).Trim(), [System.Globalization.CultureInfo]::InvariantCulture)
        $err = Test-Coordinate -Longitude $longitude -Latitude $latitude -Name $placeName
        if ($err) { throw "第 $($imported + 2) 行：$err" }
        $categoryName = ([string]$row.category).Trim(); if (-not $categoryName) { $categoryName = "其他" }
        $remark = ([string]$row.note).Trim()
        $aliasText = [string]$row.aliases
        $aliasList = @()
        if ($aliasText) { $aliasList = @($aliasText -split "[,，、;；]") }
        $point = New-Point -CampusName $campusName -PlaceName $placeName -Longitude $longitude -Latitude $latitude -CategoryName $categoryName -Remark $remark -AliasList $aliasList
        Add-Point -Point $point -SkipConfirm
        $imported++
    }
    Write-Host "✅ CSV 导入完成：$imported 个点位" -ForegroundColor Green
}

function Read-Category {
    Write-Host "分类：1 餐饮  2 学习  3 运动  4 宿舍  5 校门  6 实训  7 行政  8 其他"
    $inputValue = Read-Host "选择分类（回车=其他，可直接输入自定义分类）"
    if ([string]::IsNullOrWhiteSpace($inputValue)) { return "其他" }
    if ($categoryMap.Contains($inputValue.Trim())) { return $categoryMap[$inputValue.Trim()] }
    return $inputValue.Trim()
}

# ------------------------------ 入口 ------------------------------

if ($List) { Show-Points; return }
if ($Remove) { Remove-Point -PointId $Remove; return }
if ($Validate) { Test-Points; return }
if ($Csv) { Import-CsvPoints -CsvPath $Csv; return }

# 命令行直录模式
if (-not [string]::IsNullOrWhiteSpace($Name)) {
    $campusName = Normalize-Campus ([string]$Campus)
    $err = Test-Coordinate -Longitude $Lng -Latitude $Lat -Name $Name
    if ($err) { throw $err }
    $point = New-Point -CampusName $campusName -PlaceName $Name -Longitude $Lng -Latitude $Lat -CategoryName $Category -Remark $Note -AliasList $Aliases -ExplicitId $Id
    Add-Point -Point $point
    return
}

# 交互式录入
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  民职通 · 校园点位生成器" -ForegroundColor Cyan
Write-Host "  坐标请用高德坐标拾取器（GCJ-02）" -ForegroundColor Cyan
Write-Host "  https://lbs.amap.com/tools/picker" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "数据文件：$Path"

$existing = @(Read-Points -FilePath $Path)
if ($existing.Count -eq 0) {
    Write-Points -FilePath $Path -Points @()
    Write-Host "已创建空数据文件（0 个点位）。"
} else {
    Write-Host "当前已有 $($existing.Count) 个点位。"
}

while ($true) {
    Write-Host ""
    Write-Host "—— 新增点位（输入 Q 退出）——"
    $campusInput = Read-Host "校区：1=大兴校区  2=燕郊校区（回车=大兴，也可直接输入名称）"
    if ($campusInput -match "^\s*[Qq]\s*$") { break }
    $campusName = Normalize-Campus ([string]$campusInput)

    $placeName = Read-Host "地点名称（必填）"
    if ([string]::IsNullOrWhiteSpace($placeName)) { Write-Host "⚠ 名称不能为空"; continue }

    $lngText = Read-Host "经度（如 116.349512）"
    $lngValue = 0.0
    if (-not [double]::TryParse($lngText.Trim(), [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$lngValue)) {
        Write-Host "⚠ 经度格式不对"; continue
    }

    $latText = Read-Host "纬度（如 39.754678）"
    $latValue = 0.0
    if (-not [double]::TryParse($latText.Trim(), [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$latValue)) {
        Write-Host "⚠ 纬度格式不对"; continue
    }

    $err = Test-Coordinate -Longitude $lngValue -Latitude $latValue -Name $placeName
    if ($err) { Write-Host "⚠ $err"; continue }

    $categoryName = Read-Category
    $remark = Read-Host "备注/入口说明（可空）"
    $aliasText = Read-Host "别名，逗号分隔（可空，如：一食堂,食堂）"
    $aliasList = @()
    if (-not [string]::IsNullOrWhiteSpace($aliasText)) { $aliasList = @($aliasText -split "[,，、;；]") }

    $point = New-Point -CampusName $campusName -PlaceName $placeName -Longitude $lngValue -Latitude $latValue -CategoryName $categoryName -Remark $remark -AliasList $aliasList -ExplicitId $Id
    try {
        Add-Point -Point $point
    } catch {
        Write-Host "⚠ $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host "已退出。当前点位文件：$Path" -ForegroundColor Cyan