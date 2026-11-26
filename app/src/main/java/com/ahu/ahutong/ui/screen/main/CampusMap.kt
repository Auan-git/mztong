package com.ahu.ahutong.ui.screen.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ahu.ahutong.data.campus.CampusLocations
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.ahu.ahutong.ui.theme.withNight
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** 校园固定点位（来自 assets/campus_points.json）。 */
private data class CampusPoint(
    val id: String,
    val campus: String,
    val name: String,
    val category: String,
    val lng: Double,
    val lat: Double,
    val note: String,
)

private fun loadCampusPoints(context: Context): List<CampusPoint> {
    return runCatching {
        val raw = context.assets.open("campus_points.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim()
            val campus = item.optString("campus").trim()
            val name = item.optString("name").trim()
            val lng = item.optDouble("lng", Double.NaN)
            val lat = item.optDouble("lat", Double.NaN)
            if (id.isEmpty() || campus.isEmpty() || name.isEmpty() || lng.isNaN() || lat.isNaN()) {
                null
            } else {
                CampusPoint(
                    id = id,
                    campus = campus,
                    name = name,
                    category = item.optString("category", "其他"),
                    lng = lng,
                    lat = lat,
                    note = item.optString("note", ""),
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun campusPointsToJson(points: List<CampusPoint>): String {
    val array = JSONArray()
    points.forEach { point ->
        array.put(JSONObject().apply {
            put("id", point.id)
            put("campus", point.campus)
            put("name", point.name)
            put("category", point.category)
            put("lng", point.lng)
            put("lat", point.lat)
            put("note", point.note)
        })
    }
    return array.toString()
}

/**
 * 「小工具 → 校园地图」。
 *
 * 使用高德 Web（JS API 1.4）在地图 WebView 中展示大兴校区与燕郊校区；
 * 本机定位由 Android LocationManager 获取后通过 JS 注入显示。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CampusMap(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val campuses = CampusLocations.list
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("正在加载地图…") }
    var pageReady by remember { mutableStateOf(false) }
    var pageInitialized by remember { mutableStateOf(false) }
    var mapFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var locating by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var satelliteOn by remember { mutableStateOf(false) }
    var pointMenuExpanded by remember { mutableStateOf(false) }
    var pendingUserPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var newPointName by remember { mutableStateOf("") }
    var userPointToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    val selectedCampus = campuses.getOrElse(selectedIndex) { campuses.first() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val campusPoints = remember(context) { loadCampusPoints(context) }
    val campusPointsJson = remember(campusPoints) { campusPointsToJson(campusPoints) }

    fun showUserLocation(location: Location) {
        val lat = location.latitude
        val lng = location.longitude
        webView?.evaluateJavascript("showUser($lng, $lat)", null)
        status = "已定位：%.5f, %.5f".format(lat, lng)
        locating = false
    }

    fun locate() {
        if (locating) return
        locating = true
        status = "正在获取位置…"
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            locating = false
            status = "当前设备不支持定位"
            return
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val lastKnown = providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null) {
            showUserLocation(lastKnown)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        var settled = false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (settled) return
                settled = true
                runCatching { manager.removeUpdates(this) }
                handler.removeCallbacksAndMessages(null)
                showUserLocation(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }
        val requested = runCatching {
            manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
            true
        }.getOrDefault(false)
        if (!requested) {
            locating = false
            status = "系统定位服务未开启，请打开定位后重试"
            return
        }
        handler.postDelayed({
            if (!settled) {
                settled = true
                runCatching { manager.removeUpdates(listener) }
                locating = false
                status = "暂时获取不到位置，请到室外或打开系统定位后重试"
            }
        }, 8_000L)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            locate()
        } else {
            status = "未获得定位权限，可在系统设置中允许定位"
        }
    }

    fun ensurePermissionAndLocate() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            locate()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    val onJsMessage: (WebView, String) -> Unit = { source, message ->
        Log.i("CampusMapBridge", message)
        mainHandler.post {
            // 重试或离开页面后，忽略旧 WebView 的异步回调。
            if (webView !== source) return@post
            when {
                message == "initialized" -> pageInitialized = true
                message == "ready" -> {
                    pageReady = true
                    mapFailed = false
                    status = "地图已加载"
                }
                message.contains("失败") || message.contains("错误") -> {
                    mapFailed = true
                    pageReady = false
                    status = message
                }
                message.startsWith("longtap:") -> {
                    val parts = message.removePrefix("longtap:").split(",")
                    val lng = parts.getOrNull(0)?.toDoubleOrNull()
                    val lat = parts.getOrNull(1)?.toDoubleOrNull()
                    if (lng != null && lat != null) {
                        pendingUserPoint = lng to lat
                        newPointName = ""
                    }
                }
                message.startsWith("user-point-click:") -> {
                    runCatching {
                        val raw = message.removePrefix("user-point-click:")
                        val json = JSONObject(raw)
                        userPointToDelete = json.optString("id") to json.optString("name")
                    }
                }
                message.startsWith("focus-point:") -> {
                    status = "点位：${message.removePrefix("focus-point:")}"
                }
                message.startsWith("user-point-added:") -> {
                    status = "已添加标点：${message.removePrefix("user-point-added:")}"
                }
                message == "user-point-removed" -> {
                    status = "已删除标点"
                }
                message.startsWith("satellite:") -> {
                    status = if (message.endsWith("on")) "已切换到卫星图" else "已切换到标准图"
                }
                message.startsWith("point-click:") -> {
                    val pointId = message.removePrefix("point-click:").trim()
                    val point = campusPoints.firstOrNull { it.id == pointId }
                    if (point != null) {
                        status = "点位：${point.name}（${point.category}）"
                        if (point.note.isNotBlank()) status = "$status · ${point.note}"
                    }
                }
                !mapFailed -> status = message
            }
        }
    }

    LaunchedEffect(selectedIndex, pageInitialized) {
        if (pageInitialized) {
            status = "正在定位 ${selectedCampus.name}…"
            webView?.evaluateJavascript("setPoints($campusPointsJson)", null)
            webView?.evaluateJavascript(
                "setCampus(${JSONObject.quote(selectedCampus.name)}, " +
                    "${JSONObject.quote(selectedCampus.address)}, " +
                    "${selectedCampus.lng}, ${selectedCampus.lat}, ${selectedCampus.geocodeAddress})",
                null,
            )
        }
    }

    LaunchedEffect(loadAttempt) {
        delay(15_000L)
        if (!pageReady && !mapFailed) {
            mapFailed = true
            status = "地图加载超时，请检查网络后重试"
        }
    }

    fun retryMap() {
        pageReady = false
        pageInitialized = false
        mapFailed = false
        status = "正在加载地图…"
        loadAttempt++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "校园地图",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { ensurePermissionAndLocate() }, enabled = pageReady && !locating) {
                Icon(Icons.Default.MyLocation, contentDescription = "定位我的位置")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            campuses.forEachIndexed { index, campus ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { selectedIndex = index },
                    label = { Text(campus.name) },
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(99.n1 withNight 20.n1),
        ) {
            key(loadAttempt) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            // AndroidView 默认 WRAP_CONTENT 会让网页的百分比高度塌陷为 0。
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.loadsImagesAutomatically = true
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (request.isForMainFrame) {
                                        onJsMessage(view, "地图页面加载失败，请检查网络后重试")
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                    Log.i(
                                        "CampusMapWeb",
                                        "${message.message()} (${message.sourceId()}:${message.lineNumber()})",
                                    )
                                    return true
                                }
                            }
                            val mapView = this
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onJsMessage(message: String) = onJsMessage(mapView, message)
                                },
                                "AndroidBridge",
                            )
                            webView = this
                            loadUrl("file:///android_asset/campus_map.html")
                        }
                    },
                    onRelease = { view ->
                        if (webView === view) webView = null
                        view.stopLoading()
                        view.removeJavascriptInterface("AndroidBridge")
                        view.destroy()
                    },
                )
            }
            if (!pageReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(99.n1 withNight 20.n1),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = if (mapFailed) status else "地图加载中…",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (mapFailed) {
                            Button(onClick = { retryMap() }) { Text("重新加载") }
                        }
                    }
                }
            }
        }
        Surface(color = 100.n1 withNight 20.n1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${selectedCampus.name} · ${selectedCampus.address}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = 40.a1 withNight 80.a1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(enabled = pageReady, onClick = { pointMenuExpanded = true }) {
                            Text(text = "选择点位")
                        }
                        DropdownMenu(
                            expanded = pointMenuExpanded,
                            onDismissRequest = { pointMenuExpanded = false },
                        ) {
                            val pointsHere = campusPoints.filter { it.campus == selectedCampus.name }
                            if (pointsHere.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("本校区暂无固定点位") },
                                    onClick = { pointMenuExpanded = false },
                                    enabled = false,
                                )
                            }
                            pointsHere.forEach { point ->
                                DropdownMenuItem(
                                    text = { Text("${point.name} · ${point.category}") },
                                    onClick = {
                                        pointMenuExpanded = false
                                        webView?.evaluateJavascript(
                                            "focusPoint(${JSONObject.quote(point.id)})",
                                            null,
                                        )
                                        status = "点位：${point.name}（${point.category}）"
                                    },
                                )
                            }
                        }
                    }
                    TextButton(
                        enabled = pageReady,
                        onClick = {
                            satelliteOn = !satelliteOn
                            webView?.evaluateJavascript("setSatellite($satelliteOn)", null)
                        },
                    ) {
                        Text(text = if (satelliteOn) "标准图" else "卫星图")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { ensurePermissionAndLocate() },
                        enabled = pageReady && !locating,
                    ) {
                        Text(text = if (locating) "定位中…" else "定位我的位置")
                    }
                    TextButton(enabled = pageReady, onClick = {
                        webView?.evaluateJavascript("fitCampus()", null)
                    }) {
                        Text(text = "回到校区")
                    }
                }
            }
        }
    }
    pendingUserPoint?.let { (lng, lat) ->
        AlertDialog(
            onDismissRequest = { pendingUserPoint = null },
            title = { Text("添加标点") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "坐标：%.6f, %.6f".format(lat, lng),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = newPointName,
                        onValueChange = { newPointName = it },
                        singleLine = true,
                        label = { Text("名称") },
                        placeholder = { Text("例如：我的宿舍") },
                    )
                    Text(
                        text = "标点仅保存在本机，不会上传或分享。",
                        style = MaterialTheme.typography.bodySmall,
                        color = 40.a1 withNight 80.a1,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newPointName.trim().ifEmpty { "我的标点" }
                    val point = JSONObject().apply {
                        put("id", "user-" + System.currentTimeMillis())
                        put("campus", selectedCampus.name)
                        put("name", name)
                        put("category", "其他")
                        put("lng", lng)
                        put("lat", lat)
                        put("note", "")
                        put("aliases", JSONArray())
                    }
                    webView?.evaluateJavascript("addUserPoint($point)", null)
                    status = "已添加标点：$name"
                    pendingUserPoint = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUserPoint = null }) { Text("取消") }
            },
        )
    }

    userPointToDelete?.let { (pointId, pointName) ->
        AlertDialog(
            onDismissRequest = { userPointToDelete = null },
            title = { Text("我的标点") },
            text = { Text("「$pointName」仅保存在本机，删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    webView?.evaluateJavascript("removeUserPoint(${JSONObject.quote(pointId)})", null)
                    status = "已删除标点：$pointName"
                    userPointToDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { userPointToDelete = null }) { Text("取消") }
            },
        )
    }
}
