# 校园地图点位维护（固定点位）

`tools/add-campus-point.ps1` 用于录入「校园地图」的固定点位（食堂、图书馆、体育馆、教学楼、校门等），
自动生成/追加 `app/src/main/assets/campus_points.json`，格式与 App 读取的一致。

## 坐标怎么取

坐标必须使用**高德坐标系（GCJ-02）**，和 `campus_map.html` 里的坐标一致：

1. 打开高德开放平台「坐标拾取器」：<https://lbs.amap.com/tools/picker>
2. 搜索「民政职业大学」并放大到 18~19 级
3. 点击目标建筑，复制显示的 `经度,纬度`
4. 建议标「入口」而不是建筑中心（导航更准），并在备注里写清楚

> 百度地图的 BD-09、手机 GPS 的 WGS-84 都与高德不一致，直接用会偏移几百米，不要混用。

## 可视化前端（推荐，可地图取点）

双击 `tools\open-campus-points-editor.bat`，或直接用浏览器打开 `tools\campus-points-editor.html`：

- 左侧填写名称/分类/备注/别名，右侧高德地图**点击取点**自动填经纬度；右上角可切换**标准图 / 卫星图**（校园区域标准图没有建筑轮廓，用卫星图对着楼取点更准）；
- 下方列表可编辑、删除，支持按校区筛选；
- 右下角实时显示 `campus_points.json`，可复制或下载；
- 数据自动保存在浏览器本地（localStorage），刷新不丢；也可导入已有 JSON。

> 直接双击打开时，如果高德 JS API 因域名限制不显示地图，手填坐标仍然可用；也可以先用 App 里的「校园地图」取点，再填进编辑器。下载的 `campus_points.json` 覆盖到 `app/src/main/assets/campus_points.json` 即可。

## 交互式录入

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1
```

按提示依次输入：

- 校区：`1` 大兴校区 / `2` 燕郊校区（直接回车默认大兴，也可输入名称）
- 地点名称（必填）
- 经度、纬度（如 `116.349512`、`39.754678`）
- 分类（1 餐饮 / 2 学习 / 3 运动 / 4 宿舍 / 5 校门 / 6 实训 / 7 行政 / 8 其他）
- 备注/入口说明（可空）
- 别名，逗号分隔（可空，如「一食堂,食堂」）

确认后即写入 JSON；输入 `Q` 退出。也可以双击 `tools\add-campus-point.bat` 直接运行。

## 命令行录入

```powershell
powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1 `
  -Campus "大兴校区" `
  -Name "第一食堂" `
  -Lng 116.349512 `
  -Lat 39.754678 `
  -Category "餐饮" `
  -Note "东门入口" `
  -Aliases "一食堂,食堂"
```

同名点位默认会报错；确认要覆盖时加 `-Force`（保留原 id，更新坐标/备注）。

## 批量导入 CSV

CSV 表头：`campus,name,lng,lat,category,note,aliases`（aliases 用逗号/分号分隔）：

```csv
campus,name,lng,lat,category,note,aliases
大兴校区,教学楼A,116.3499,39.7544,学习,主入口,教A;A楼
燕郊校区,操场,116.7964,39.9547,运动,,操场
```

执行：

```powershell
powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1 -Csv tools\campus_points_template.csv
```

模板见 `tools/campus_points_template.csv`。

## 查看 / 删除 / 校验

```powershell
# 查看全部点位
powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1 -List

# 按 id 删除
powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1 -Remove "dx-001"

# 校验 JSON（字段、坐标范围、id 是否重复）
powershell -ExecutionPolicy Bypass -File tools\add-campus-point.ps1 -Validate
```

## 数据格式

```json
[
  {
    "id": "dx-001",
    "campus": "大兴校区",
    "name": "第一食堂",
    "category": "餐饮",
    "lng": 116.349512,
    "lat": 39.754678,
    "note": "东门入口",
    "aliases": ["一食堂", "食堂"]
  }
]
```

| 字段 | 说明 |
|---|---|
| `id` | 自动生成：大兴 `dx-001`、燕郊 `yj-001`；用于点击回传/编辑/删除 |
| `campus` | 校区名称，与地图上的校区切换一致 |
| `name` | 显示名称 |
| `category` | 分类，用于图标/筛选 |
| `lng` / `lat` | 高德 GCJ-02 坐标（6 位小数） |
| `note` | 入口/楼层等补充说明 |
| `aliases` | 别名数组，便于以后做搜索 |

## 注意

- 文件默认写到 `app/src/main/assets/campus_points.json`，可用 `-Path` 指定其他位置。
- 生成的是 UTF-8（无 BOM）JSON，可直接作为 Android assets 读取。
- 用户自己在地图上标注的点位由 App 保存在本机（MMKV），不写入这个文件、不上传、不分享。