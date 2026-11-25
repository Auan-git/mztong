package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.DisposableEffect
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.utils.FileUtils
import com.ahu.ahutong.R
import com.ahu.ahutong.appwidget.ScheduleAdaptiveWidgetProvider
import com.ahu.ahutong.appwidget.ScheduleAppWidget
import com.ahu.ahutong.appwidget.ScheduleAppWidgetReceiver
import com.ahu.ahutong.appwidget.WidgetUpdateScheduler
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.ahu.ahutong.ui.theme.withNight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 「小工具」页不展示的首页格子 id（电话本、失物招领、天气）。 */
private val TOOLS_HIDDEN_WIDGET_IDS = setOf("phone_book", "lost_found", "weather")

/** 当前桌面已添加的课表组件数量（Glance + 自适应两种实现都算）。 */
private fun currentWidgetCount(context: Context): Int {
    val manager = AppWidgetManager.getInstance(context) ?: return 0
    fun count(providerClass: Class<*>) = runCatching {
        manager.getAppWidgetIds(ComponentName(context, providerClass)).size
    }.getOrDefault(0)
    return count(ScheduleAppWidgetReceiver::class.java) +
        count(ScheduleAdaptiveWidgetProvider::class.java)
}

/** 手动刷新桌面组件（两种实现都刷新），并重新排好下一次定时更新。 */
private fun refreshDesktopWidgets(
    context: Context,
    scope: CoroutineScope,
    onFinished: () -> Unit,
) {
    scope.launch {
        runCatching { ScheduleAppWidget().updateAll(context) }
        val manager = AppWidgetManager.getInstance(context)
        val adaptiveIds = runCatching {
            manager.getAppWidgetIds(ComponentName(context, ScheduleAdaptiveWidgetProvider::class.java))
        }.getOrDefault(IntArray(0))
        if (adaptiveIds.isNotEmpty()) {
            context.sendBroadcast(
                Intent(context, ScheduleAdaptiveWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, adaptiveIds)
                }
            )
        }
        runCatching { WidgetUpdateScheduler.scheduleNext(context) }
        withContext(Dispatchers.Main) { onFinished() }
    }
}

/** 各桌面添加小组件的操作步骤（无法一键添加时展示）。 */
private fun widgetGuideText(): String = buildString {
    appendLine("1. 回到桌面，长按空白处（部分桌面需双指捏合）")
    appendLine("2. 选择「小组件 / 添加工具 / 微件」")
    appendLine("3. 找到「民职通」→「民职通课表」，拖到桌面")
    appendLine("4. 长按组件边缘可调整大小，内容会随课表自动更新")
    appendLine()
    append("找不到「民职通」时：等几秒让桌面加载组件列表，或重启桌面 / 手机后再试。MIUI、HyperOS、ColorOS 等桌面请在「添加工具」里查找。")
}

@Composable
fun Tools(
    navController: NavHostController,
    homeEditEnabled: Boolean = false,
    onEditHome: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var homeWidgetIds by remember {
        mutableStateOf(AHUCache.getHomeWidgetSlots().filterNotNull().toSet())
    }
    var installedWidgetCount by remember { mutableStateOf(currentWidgetCount(context)) }
    var pinRequesting by remember { mutableStateOf(false) }
    var guideVisible by remember { mutableStateOf(false) }

    fun refreshHomeWidgetIds() {
        homeWidgetIds = AHUCache.getHomeWidgetSlots().filterNotNull().toSet()
    }

    fun refreshWidgetInstances() {
        installedWidgetCount = currentWidgetCount(context)
    }

    fun requestPinWidget() {
        if (pinRequesting) return
        pinRequesting = true
        scope.launch {
            var accepted = false
            try {
                // Glance 1.1.x 的 successCallback 是 PendingIntent，无法直接回调 Compose；
                // 这里只用返回值判断桌面是否受理，结果由「回到 App 时刷新已添加数量」补上。
                accepted = GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                    receiver = ScheduleAppWidgetReceiver::class.java,
                )
            } catch (t: Throwable) {
                Log.w("Tools", "requestPinGlanceAppWidget failed", t)
            }
            if (!accepted) {
                // 部分桌面不支持 Glance 的一键添加，退回系统 RemoteViews 组件请求。
                val fallbackAccepted = runCatching {
                    AppWidgetManager.getInstance(context).requestPinAppWidget(
                        ComponentName(context, ScheduleAdaptiveWidgetProvider::class.java),
                        null,
                        null,
                    )
                }.getOrDefault(false)
                if (fallbackAccepted) {
                    Toast.makeText(context, "请在桌面按提示完成添加", Toast.LENGTH_SHORT).show()
                } else {
                    guideVisible = true
                }
            } else {
                Toast.makeText(context, "请按桌面提示确认添加", Toast.LENGTH_SHORT).show()
            }
            pinRequesting = false
            refreshWidgetInstances()
        }
    }

    LaunchedEffect(navController) {
        refreshHomeWidgetIds()
        refreshWidgetInstances()
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            if (backStackEntry.destination.route == "tools") {
                refreshHomeWidgetIds()
                refreshWidgetInstances()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // 从桌面返回 App 时刷新「已添加 N 个」状态；新增组件后自动刷新内容。
            if (event == Lifecycle.Event.ON_RESUME) {
                val latest = currentWidgetCount(context)
                if (latest > installedWidgetCount) {
                    installedWidgetCount = latest
                    Toast.makeText(context, "已添加课表组件", Toast.LENGTH_SHORT).show()
                    refreshDesktopWidgets(context, scope) { refreshWidgetInstances() }
                } else {
                    installedWidgetCount = latest
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 32.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "小工具",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium
            )
            if (homeEditEnabled) {
                IconButton(
                    onClick = {
                        onEditHome()
                        navController.navigate("home") {
                            popUpTo("home") {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "编辑首页"
                    )
                }
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeWidgetRegistry.widgets
                .filter { it.id !in homeWidgetIds }
                // 电话本、失物招领、天气不再出现在「小工具」页；
                // 功能页面与首页自定义格子仍保留，需要时可在首页编辑里添加。
                .filter { it.id !in TOOLS_HIDDEN_WIDGET_IDS }
                .forEach { widget ->
                    ToolItem(
                        title = widget.title,
                        iconId = widget.iconId,
                        tint = widget.tint,
                        onClick = { navController.navigate(widget.route) }
                    )
                }

        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(SmoothRoundedCornerShape(32.dp))
                .background(100.n1 withNight 30.n1)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "桌面课表组件",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "把当天课程放到桌面，随课表自动更新",
                        style = MaterialTheme.typography.bodyMedium,
                        color = 50.n1 withNight 80.n1
                    )
                }
                if (installedWidgetCount > 0) {
                    Text(
                        text = "已添加 $installedWidgetCount 个",
                        style = MaterialTheme.typography.labelLarge,
                        color = 40.a1 withNight 80.a1
                    )
                }
            }
            Image(
                painter = painterResource(id = R.mipmap.schedule_widget_prev),
                contentDescription = "桌面课表组件预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(SmoothRoundedCornerShape(20.dp))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { requestPinWidget() },
                    enabled = !pinRequesting,
                ) {
                    Text(text = if (pinRequesting) "正在添加…" else "一键添加到桌面")
                }
                OutlinedButton(onClick = { guideVisible = true }) {
                    Text(text = "添加方法")
                }
            }
            if (installedWidgetCount > 0) {
                TextButton(
                    onClick = {
                        refreshDesktopWidgets(context, scope) {
                            refreshWidgetInstances()
                            Toast.makeText(context, "已刷新桌面组件", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(text = "立即刷新组件内容")
                }
            }
        }
    }

    if (guideVisible) {
        AlertDialog(
            onDismissRequest = { guideVisible = false },
            title = { Text(text = "添加桌面组件") },
            text = { Text(text = widgetGuideText()) },
            confirmButton = {
                TextButton(onClick = { guideVisible = false }) {
                    Text(text = "知道了")
                }
            },
        )
    }
}

@Composable
private fun ToolItem(
    title: String,
    iconId: Int,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(SmoothRoundedCornerShape(16.dp))
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 16.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = iconId),
            modifier = Modifier.size(40.dp),
            contentDescription = null,
            tint = tint
        )
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
