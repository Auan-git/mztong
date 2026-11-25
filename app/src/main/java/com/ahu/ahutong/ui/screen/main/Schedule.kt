package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ahu.ahutong.data.schedule.CustomCourseSupport
import com.ahu.ahutong.data.schedule.TeachingWeekPolicy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.screen.main.schedule.CourseCard
import com.ahu.ahutong.ui.screen.main.schedule.CourseCardSpec
import com.ahu.ahutong.ui.screen.main.schedule.AddCourseDialog
import com.ahu.ahutong.ui.screen.main.schedule.CourseDetailDialog
import com.ahu.ahutong.ui.screen.main.schedule.shortScheduleLocation
import com.ahu.ahutong.ui.screen.main.schedule.weekRangeText
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.Hct.Companion.toHct
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.PaletteStyle
import com.kyant.monet.TonalPalettes.Companion.toTonalPalettes
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.n2
import com.kyant.monet.toColor
import com.kyant.monet.toSrgb
import com.ahu.ahutong.ui.theme.withNight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import android.widget.Toast
import com.ahu.ahutong.ui.screen.main.schedule.CourseCardSpec.cellSpacing
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain

@Composable
fun Schedule(
    scheduleViewModel: ScheduleViewModel = hiltViewModel(),
    behaviorRuntime: BehaviorPredictionRuntime
) {
    val behaviorReporter = rememberBehaviorActionReporter()
    val scope = rememberCoroutineScope()
    val scheduleConfig by scheduleViewModel.scheduleConfig.observeAsState()
    // 用系统日期计算今天，避免 App 跨天/跨周后仍用旧的 config.weekDay 高亮
    val currentWeekday = DebugClock.nowLocalDate().dayOfWeek.value
    var currentWeek by rememberSaveable { mutableStateOf(scheduleConfig?.week ?: 1) }
    val pagerState = rememberPagerState(
        // 周数固定为 20（超星同样为 20 周），但 config.week 异常时不能把初始页设到范围外
        initialPage = (currentWeek - 1).coerceIn(0, 19),
        pageCount = { 20 }
    )

    val scheduleResult = scheduleViewModel.schedule.observeAsState().value
    val nextScheduleResult = scheduleViewModel.nextSchedule.observeAsState().value
    var isPreviewNextSemester by rememberSaveable { mutableStateOf(false) }
    var isOverviewSchedule by rememberSaveable { mutableStateOf(false) }
    var isSettingsVisible by rememberSaveable { mutableStateOf(false) }
    val activeScheduleResult = if (isPreviewNextSemester) nextScheduleResult else scheduleResult
    val schedule = activeScheduleResult?.getOrNull() ?: emptyList()
    val context = LocalContext.current


    LaunchedEffect(scheduleConfig?.week, isPreviewNextSemester) {
        if (!isPreviewNextSemester) {
            scheduleConfig?.week?.let { resolvedWeek ->
                currentWeek = resolvedWeek
                val targetPage = (resolvedWeek - 1).coerceIn(0, 19)
                if (pagerState.currentPage != targetPage) {
                    pagerState.scrollToPage(targetPage)
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        currentWeek = pagerState.currentPage + 1
    }

    LaunchedEffect(scheduleResult) {
        scheduleResult?.exceptionOrNull()?.let {
            Toast.makeText(context, "加载课表失败: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(nextScheduleResult) {
        nextScheduleResult?.exceptionOrNull()?.let {
            Toast.makeText(context, "加载下学期课表失败: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(activeScheduleResult, isPreviewNextSemester) {
        when {
            activeScheduleResult == null -> Unit
            activeScheduleResult.isFailure -> behaviorRuntime.onContentStateChanged(
                SemanticDomain.SCHEDULE,
                ContentStateBucket.ERROR,
                freshnessBucket = 7,
                resultCount = ResultCountBucket.ZERO,
                errorType = ErrorTypeBucket.NETWORK
            )
            else -> behaviorRuntime.onContentStateChanged(
                SemanticDomain.SCHEDULE,
                if (schedule.isEmpty()) ContentStateBucket.EMPTY else ContentStateBucket.READY,
                freshnessBucket = 1,
                resultCount = scheduleResultBucket(schedule.size)
            )
        }
    }

    LaunchedEffect(isPreviewNextSemester) {
        if (isPreviewNextSemester && nextScheduleResult == null) {
            scheduleViewModel.refreshNextSchedule()
        }
        val targetWeek = if (isPreviewNextSemester) 1 else scheduleConfig?.week ?: 1
        currentWeek = targetWeek
        pagerState.animateScrollToPage((targetWeek - 1).coerceIn(0, 19))
    }

    val baseColor = 50.a1.toSrgb().toHct()
    val courseColors by remember(schedule) {
        mutableStateOf(
            schedule.map { it.name }.distinct()
                .mapIndexed { index, name ->
                    name to baseColor.copy(
                        h = 360.0 * index / schedule.map { it.name }
                            .distinct().size.coerceAtLeast(1)
                    ).toSrgb()
                        .toColor()
                }.toMap()
        )
    }

    val timetable = ScheduleViewModel.timetable
    val schoolCurrentWeek = scheduleConfig?.week ?: currentWeek
    val isViewingCurrentWeek = !isPreviewNextSemester &&
        scheduleConfig?.isInSemester == true &&
        currentWeek == schoolCurrentWeek
    val hasSemesterData = scheduleConfig?.hasSemesterData() ?: false
    val weekSubtitle = remember(currentWeek, schoolCurrentWeek, scheduleConfig?.isInSemester, isPreviewNextSemester, scheduleConfig?.startTime, hasSemesterData) {
        buildWeekSubtitleText(
            week = currentWeek,
            schoolWeek = schoolCurrentWeek,
            isInSemester = scheduleConfig?.isInSemester == true,
            hasSemesterData = hasSemesterData,
            isPreviewNextSemester = isPreviewNextSemester,
            startTime = scheduleConfig?.startTime,
        )
    }

    var detailedCourse by rememberSaveable { mutableStateOf<Course?>(null) }
    var isWeekPickerVisible by rememberSaveable { mutableStateOf(false) }
    // 长按空白格：待添加课程的（星期, 起始节次）
    var addCourseSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // 正在二次编辑的自定义课程
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    val haptic = LocalHapticFeedback.current
    val settingsCardColor = 100.n1 withNight 20.n1

    // 每 30 秒刷新一次「当前时间」指示线；跨天时顺便重算周次/日期
    var nowMinuteOfDay by remember { mutableStateOf(currentMinuteOfDay()) }
    var configDate by remember { mutableStateOf(DebugClock.nowLocalDate()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMinuteOfDay = currentMinuteOfDay()
            val today = DebugClock.nowLocalDate()
            if (today != configDate) {
                configDate = today
                scheduleViewModel.reloadScheduleConfig()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScheduleWeekHeader(
            currentWeek = currentWeek,
            subtitle = weekSubtitle,
            isPreviewNextSemester = isPreviewNextSemester,
            onOpenWeekPicker = { isWeekPickerVisible = true },
            onPrevWeek = {
                if (currentWeek > 1) {
                    behaviorRuntime.recordCommittedMutationAsync(
                        MutationId.SCHEDULE_WEEK_CHANGED,
                        currentWeek,
                        currentWeek - 1,
                        coarseValueBucket = if (currentWeek - 1 == schoolCurrentWeek) "CURRENT_WEEK" else "OTHER_WEEK"
                    )
                    scope.launch { pagerState.animateScrollToPage((currentWeek - 2).coerceIn(0, 19)) }
                }
            },
            onNextWeek = {
                if (currentWeek < 20) {
                    behaviorRuntime.recordCommittedMutationAsync(
                        MutationId.SCHEDULE_WEEK_CHANGED,
                        currentWeek,
                        currentWeek + 1,
                        coarseValueBucket = if (currentWeek + 1 == schoolCurrentWeek) "CURRENT_WEEK" else "OTHER_WEEK"
                    )
                    scope.launch { pagerState.animateScrollToPage(currentWeek.coerceIn(0, 19)) }
                }
            },
            onBackToCurrentWeek = {
                val target = (schoolCurrentWeek - 1).coerceIn(0, 19)
                scope.launch { pagerState.animateScrollToPage(target) }
            },
            onRefresh = {
                if (isPreviewNextSemester) {
                    scheduleViewModel.refreshNextSchedule(true)
                } else {
                    behaviorReporter.organic(AppActionId.MANUAL_REFRESH_SCHEDULE)
                    scheduleViewModel.refreshSchedule(true)
                }
            },
            onOpenSettings = { isSettingsVisible = true },
            showBackToCurrentWeek = !isViewingCurrentWeek && !isPreviewNextSemester,
        )

        val cellWidth = (
                LocalConfiguration.current.screenWidthDp.dp -
                        CourseCardSpec.mainColumnWidth -
                        CourseCardSpec.cellSpacing * 9
                ) / 7
        val cellHeight = CourseCardSpec.rowHeight

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val pageWeek = page + 1
            val isTodayView = !isPreviewNextSemester &&
                scheduleConfig?.isInSemester == true &&
                pageWeek == schoolCurrentWeek
            val weekCourses = remember(schedule, pageWeek) {
                schedule.filter { course ->
                    val inWeek = course.weekIndexes?.contains(pageWeek)
                        ?: (pageWeek >= course.startWeek && pageWeek <= course.endWeek)
                    course.startTime in 1..timetable.size && inWeek
                }
            }

            Box(
                modifier = with(CourseCardSpec) {
                    Modifier
                        .fillMaxWidth()
                        .height(
                            mainRowHeight +
                                    (cellHeight + cellSpacing) * timetable.size +
                                    24.dp
                        )
                        .clip(SmoothRoundedCornerShape(28.dp))
                        .background(99.n1 withNight 20.n1)
                        .padding(top = 8.dp)
                        .padding(cellSpacing)
                        .pointerInput(isOverviewSchedule, isPreviewNextSemester, timetable) {
                            detectTapGestures(
                                onLongPress = { position ->
                                    if (!isOverviewSchedule && !isPreviewNextSemester) {
                                        val mainColumnPx = mainColumnWidth.toPx()
                                        val mainRowPx = mainRowHeight.toPx()
                                        val spacingPx = cellSpacing.toPx()
                                        val stepX = cellWidth.toPx() + spacingPx
                                        val stepY = cellHeight.toPx() + spacingPx
                                        val column = ((position.x - mainColumnPx - spacingPx) / stepX).toInt()
                                        val row = ((position.y - mainRowPx - spacingPx) / stepY).toInt()
                                        val periods = timetable.keys.sorted()
                                        if (position.x >= mainColumnPx && position.y >= mainRowPx &&
                                            column in 0..6 && row in periods.indices
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            addCourseSlot = (column + 1) to periods[row]
                                        }
                                    }
                                },
                            )
                        }
                }
            ) {
                // 左上角「节」表头
                Text(
                    text = "节",
                    modifier = with(CourseCardSpec) {
                        Modifier
                            .size(mainColumnWidth, mainRowHeight)
                            .offset(x = cellSpacing, y = cellSpacing)
                    },
                    textAlign = TextAlign.Center,
                    color = 50.n1 withNight 80.n1,
                    style = MaterialTheme.typography.labelSmall
                )

                // 星期表头（今天高亮）
                val weekDates by remember(pageWeek, scheduleConfig?.startTime) {
                    mutableStateOf(
                        List(7) { index ->
                            Calendar.getInstance().apply {
                                time = scheduleConfig?.startTime
                                    ?: SimpleDateFormat("MM-dd", Locale.CHINA).parse("09-01")
                                add(Calendar.DATE, ((pageWeek - 1) * 7) + index)
                            }
                        }
                    )
                }
                weekDates.forEachIndexed { index, date ->
                    val isToday = isTodayView && index + 1 == currentWeekday
                    Column(
                        modifier = with(CourseCardSpec) {
                            Modifier
                                .size(cellWidth, mainRowHeight)
                                .offset(
                                    x = mainColumnWidth + (cellWidth + cellSpacing) * index + cellSpacing,
                                    y = cellSpacing
                                )
                                .clip(SmoothRoundedCornerShape(12.dp))
                                .background(
                                    if (isToday) 40.a1 withNight 90.a1 else Color.Transparent
                                )
                        },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[index],
                            color = if (isToday) 100.n1 withNight 0.n1 else Color.Unspecified,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = SimpleDateFormat("MM/dd", Locale.CHINA).format(date.time),
                            color = if (isToday) {
                                100.n1 withNight 0.n1
                            } else {
                                50.n1 withNight 80.n1
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // 左侧节次列：节次 + 起止时间
                timetable.entries.sortedBy { it.key }.forEach { (period, time) ->
                    val start = time.substringBefore("-").trim()
                    val end = time.substringAfter("-", "").trim()
                    val isCurrentPeriod = isTodayView && nowMinuteOfDay in minuteRangeOf(time)
                    Column(
                        modifier = with(CourseCardSpec) {
                            Modifier
                                .size(mainColumnWidth, cellHeight)
                                .offset(
                                    y = mainRowHeight + (cellHeight + cellSpacing) * (period - 1) + cellSpacing
                                )
                                .clip(SmoothRoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrentPeriod) 90.a1 withNight 40.a1 else Color.Transparent
                                )
                        },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = ScheduleViewModel.periodLabel(period),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = start,
                            color = 50.n1 withNight 80.n1,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = end,
                            color = 50.n1 withNight 80.n1,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // 节次之间的细分隔线（参考官方课表的浅色横线，提升可读性）
                val separatorColor = 0.n1.copy(alpha = 0.05f) withNight 100.n1.copy(alpha = 0.07f)
                Box(
                    modifier = Modifier
                        .offset(y = CourseCardSpec.mainRowHeight)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(separatorColor)
                )
                timetable.entries.sortedBy { it.key }.forEach { (period, _) ->
                    Box(
                        modifier = Modifier
                            .offset(
                                x = CourseCardSpec.mainColumnWidth,
                                y = CourseCardSpec.mainRowHeight +
                                    (cellHeight + CourseCardSpec.cellSpacing) * (period - 1)
                            )
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(separatorColor)
                    )
                }

                // 当前时间指示线（仅本周、教学周内显示）
                if (isTodayView) {
                    nowLineOffset(nowMinuteOfDay, timetable, cellHeight)?.let { lineY ->
                        Row(
                            modifier = Modifier
                                .offset(y = lineY)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(40.a1 withNight 90.a1)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(40.a1 withNight 90.a1)
                            )
                        }
                    }
                }

                // 课程卡片
                if (isOverviewSchedule) {
                    weekCourses
                        .groupBy { "${it.weekday}-${it.startTime}-${it.length}" }
                        .values
                        .forEach { sameTimeCourses ->
                            key(sameTimeCourses.joinToString("-") { it.hashCode().toString() }) {
                                OverviewCourseGroupCard(
                                    courses = sameTimeCourses,
                                    colors = courseColors,
                                    cellWidth = cellWidth,
                                    cellHeight = cellHeight,
                                    currentWeek = pageWeek,
                                    onClick = {
                                        behaviorReporter.organic(AppActionId.OPEN_COURSE_DETAIL)
                                        detailedCourse = it
                                    }
                                )
                            }
                        }
                } else {
                    weekCourses.forEach { course ->
                        key(course.hashCode()) {
                            CourseCard(
                                course = course,
                                color = courseColors.getOrElse(course.name) { 50.a1 },
                                cellWidth = cellWidth,
                                cellHeight = cellHeight,
                                isCurrentWeek = true,
                                onClick = {
                                    behaviorReporter.organic(AppActionId.OPEN_COURSE_DETAIL)
                                    detailedCourse = it
                                }
                            )
                        }
                    }
                }

                if (weekCourses.isEmpty()) {
                    Text(
                        text = if (isPreviewNextSemester) "下学期暂无课程" else "本周暂无课程\n长按空白格可添加课程",
                        modifier = Modifier.align(Alignment.Center),
                        color = 50.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        if (isWeekPickerVisible) {
            WeekPickerDialog(
                selectedWeek = currentWeek,
                schoolCurrentWeek = schoolCurrentWeek,
                isInSemester = scheduleConfig?.isInSemester == true,
                onSelect = { week ->
                    isWeekPickerVisible = false
                    if (week != currentWeek) {
                        behaviorRuntime.recordCommittedMutationAsync(
                            MutationId.SCHEDULE_WEEK_CHANGED,
                            currentWeek,
                            week,
                            coarseValueBucket = if (week == schoolCurrentWeek) "CURRENT_WEEK" else "OTHER_WEEK"
                        )
                        scope.launch { pagerState.animateScrollToPage((week - 1).coerceIn(0, 19)) }
                    }
                },
                onDismiss = { isWeekPickerVisible = false },
            )
        }
        if (isSettingsVisible) {
            ScheduleSettingsDialog(
                isOverviewSchedule = isOverviewSchedule,
                isPreviewNextSemester = isPreviewNextSemester,
                backdropColor = settingsCardColor,
                onOverviewChange = { enabled ->
                    val oldValue = isOverviewSchedule
                    isOverviewSchedule = enabled
                    behaviorRuntime.recordCommittedMutationAsync(
                        MutationId.SCHEDULE_OVERVIEW_CHANGED,
                        oldValue,
                        enabled
                    )
                },
                onPreviewNextSemesterChange = { enabled ->
                    val oldValue = isPreviewNextSemester
                    isPreviewNextSemester = enabled
                    behaviorRuntime.recordCommittedMutationAsync(
                        MutationId.SCHEDULE_SEMESTER_PREVIEW_CHANGED,
                        oldValue,
                        enabled,
                        coarseValueBucket = if (enabled) "NEXT_SEMESTER" else "CURRENT_SEMESTER"
                    )
                },
                onDismiss = { isSettingsVisible = false }
            )
        }
        // 长按空白格 → 添加自定义课程
        addCourseSlot?.let { (weekday, startPeriod) ->
            AddCourseDialog(
                weekday = weekday,
                startPeriod = startPeriod,
                weekCount = TeachingWeekPolicy.SCHEDULE_WEEK_COUNT,
                onDismiss = { addCourseSlot = null },
                onConfirm = { name, teacher, location, length, startWeek, endWeek ->
                    addCourseSlot = null
                    scheduleViewModel.addCustomCourse(
                        name = name,
                        teacher = teacher,
                        location = location,
                        weekday = weekday,
                        startPeriod = startPeriod,
                        length = length,
                        startWeek = startWeek,
                        endWeek = endWeek,
                    )
                    Toast.makeText(context, "已添加到课表", Toast.LENGTH_SHORT).show()
                },
            )
        }

        // course dialog
        detailedCourse?.let { course ->
            val isCustom = CustomCourseSupport.isCustom(course)
            CourseDetailDialog(
                course = course,
                onDismiss = { detailedCourse = null },
                onEdit = if (isCustom) {
                    {
                        editingCourse = course
                        detailedCourse = null
                    }
                } else {
                    null
                },
                onDelete = if (isCustom) {
                    {
                        scheduleViewModel.deleteCustomCourse(course)
                        Toast.makeText(context, "已从课表删除", Toast.LENGTH_SHORT).show()
                        detailedCourse = null
                    }
                } else {
                    null
                },
            )
        }

        // 二次编辑自定义课程
        editingCourse?.let { course ->
            AddCourseDialog(
                weekday = course.weekday.takeIf { it > 0 } ?: 1,
                startPeriod = course.startTime.takeIf { it > 0 } ?: 1,
                weekCount = TeachingWeekPolicy.SCHEDULE_WEEK_COUNT,
                initialName = course.name.orEmpty(),
                initialTeacher = course.teacher.orEmpty(),
                initialLocation = course.location.orEmpty(),
                initialLength = course.length.takeIf { it > 0 } ?: 2,
                initialStartWeek = course.startWeek.takeIf { it > 0 } ?: 1,
                initialEndWeek = course.endWeek.takeIf { it > 0 } ?: TeachingWeekPolicy.SCHEDULE_WEEK_COUNT,
                isEdit = true,
                onDismiss = { editingCourse = null },
                onConfirm = { name, teacher, location, length, startWeek, endWeek ->
                    editingCourse = null
                    scheduleViewModel.updateCustomCourse(
                        course = course,
                        name = name,
                        teacher = teacher,
                        location = location,
                        length = length,
                        startWeek = startWeek,
                        endWeek = endWeek,
                    )
                    Toast.makeText(context, "已更新课程", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun ScheduleWeekHeader(
    currentWeek: Int,
    subtitle: String,
    isPreviewNextSemester: Boolean,
    onOpenWeekPicker: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onBackToCurrentWeek: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    showBackToCurrentWeek: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(SmoothRoundedCornerShape(12.dp))
                .clickable(onClick = onOpenWeekPicker)
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isPreviewNextSemester) {
                        "下学期 · 第 $currentWeek 周"
                    } else {
                        "第 $currentWeek 周"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "选择周次",
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = 50.n1 withNight 80.n1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .clip(ContinuousCapsule)
                .background(100.n1 withNight 30.n1)
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            if (showBackToCurrentWeek) {
                ScheduleActionButton(
                    icon = Icons.Default.MyLocation,
                    contentDescription = "回到本周",
                    enabled = true,
                    onClick = onBackToCurrentWeek
                )
            }
            ScheduleActionButton(
                icon = Icons.Default.KeyboardArrowLeft,
                contentDescription = "上一周",
                enabled = currentWeek > 1,
                onClick = onPrevWeek
            )
            ScheduleActionButton(
                icon = Icons.Default.KeyboardArrowRight,
                contentDescription = "下一周",
                enabled = currentWeek < 20,
                onClick = onNextWeek
            )
            ScheduleActionButton(
                icon = Icons.Default.Refresh,
                contentDescription = "刷新课表",
                enabled = true,
                onClick = onRefresh
            )
            ScheduleActionButton(
                icon = Icons.Default.Settings,
                contentDescription = "课表设置",
                enabled = true,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun ScheduleActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier.size(36.dp),
        enabled = enabled,
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun WeekPickerDialog(
    selectedWeek: Int,
    schoolCurrentWeek: Int,
    isInSemester: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = 100.n1 withNight 20.n1,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择周次",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = 0.n1 withNight 100.n1
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (column in 0 until 5) {
                            val week = row * 5 + column + 1
                            val selected = week == selectedWeek
                            val isSchoolWeek = isInSemester && week == schoolCurrentWeek
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(ContinuousCapsule)
                                    .background(
                                        when {
                                            selected -> 40.a1 withNight 90.a1
                                            isSchoolWeek -> 90.a1 withNight 40.a1
                                            else -> 95.n1 withNight 25.n1
                                        }
                                    )
                                    .clickable { onSelect(week) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = week.toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = when {
                                        selected || isSchoolWeek -> 100.n1 withNight 0.n1
                                        else -> 20.n1 withNight 90.n1
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "完成", color = 40.a1 withNight 80.a1)
            }
        }
    )
}

/** 当前时刻（一天中的分钟数），用于当前时间指示线。 */
private fun currentMinuteOfDay(): Int = Calendar.getInstance().let {
    it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
}

/** "08:10-08:55" -> 490..535；格式异常返回空区间。 */
private fun minuteRangeOf(range: String): IntRange {
    val startText = range.substringBefore("-").trim()
    val endText = range.substringAfter("-", "").trim()
    val start = parseMinuteOfDay(startText) ?: return IntRange.EMPTY
    val end = parseMinuteOfDay(endText) ?: return IntRange.EMPTY
    if (end < start) return IntRange.EMPTY
    return start..end
}

private fun parseMinuteOfDay(text: String): Int? {
    val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(text) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour > 23 || minute > 59) return null
    return hour * 60 + minute
}

/** 当前时间线在课表格子里的 y 偏移；不在任何一节课内时返回 null。 */
private fun nowLineOffset(
    nowMinute: Int,
    timetable: Map<Int, String>,
    cellHeight: Dp,
): Dp? {
    for ((period, range) in timetable.entries.sortedBy { it.key }) {
        val minutes = minuteRangeOf(range)
        if (minutes.isEmpty()) continue
        if (nowMinute in minutes) {
            val fraction = if (minutes.last > minutes.first) {
                (nowMinute - minutes.first).toFloat() / (minutes.last - minutes.first)
            } else {
                0f
            }
            return CourseCardSpec.mainRowHeight +
                (cellHeight + CourseCardSpec.cellSpacing) * (period - 1) +
                CourseCardSpec.cellSpacing +
                cellHeight * fraction
        }
    }
    return null
}

private fun buildWeekSubtitleText(
    week: Int,
    schoolWeek: Int,
    isInSemester: Boolean,
    hasSemesterData: Boolean,
    isPreviewNextSemester: Boolean,
    startTime: Date?,
): String {
    if (isPreviewNextSemester) return "下学期课表预览"
    if (!hasSemesterData || startTime == null) return "尚未获取开学时间，请下拉刷新"
    if (!isInSemester) return "当前不在学期内"
    val start = Calendar.getInstance().apply {
        time = startTime
        add(Calendar.DATE, (week - 1) * 7)
    }
    val end = Calendar.getInstance().apply {
        time = start.time
        add(Calendar.DATE, 6)
    }
    val format = SimpleDateFormat("M月d日", Locale.CHINA)
    val suffix = if (week == schoolWeek) " · 本周" else ""
    return "${format.format(start.time)} - ${format.format(end.time)}$suffix"
}
private fun scheduleResultBucket(count: Int): ResultCountBucket = when (count) {
    0 -> ResultCountBucket.ZERO
    in 1..5 -> ResultCountBucket.ONE_TO_FIVE
    in 6..20 -> ResultCountBucket.SIX_TO_TWENTY
    else -> ResultCountBucket.TWENTY_ONE_PLUS
}

@Composable
private fun ScheduleSettingsDialog(
        isOverviewSchedule: Boolean,
        isPreviewNextSemester: Boolean,
        backdropColor: Color,
        onOverviewChange: (Boolean) -> Unit,
        onPreviewNextSemesterChange: (Boolean) -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            containerColor = backdropColor,
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "课表设置",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = 0.n1 withNight 100.n1
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScheduleSettingRow(
                        title = "总览课表",
                        description = "显示全部周次的课程，重叠课程会平分同一块时间区域",
                        selected = isOverviewSchedule,
                        onSelect = onOverviewChange
                    )
                    ScheduleSettingRow(
                        title = "预览下学期课表",
                        description = "切换到教务系统中的下学期课表",
                        selected = isPreviewNextSemester,
                        onSelect = onPreviewNextSemesterChange
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "完成",
                        color = 40.a1 withNight 80.a1
                    )
                }
            }
        )
    }


    @Composable
    private fun ScheduleSettingRow(
        title: String,
        description: String,
        selected: Boolean,
        onSelect: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(12.dp))
                .clickable { onSelect(!selected) }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = 0.n1 withNight 100.n1
                )
                Text(
                    text = description,
                    color = 50.n1 withNight 80.n1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = selected,
                onCheckedChange = onSelect,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }

@Composable
private fun OverviewCourseGroupCard(
    courses: List<Course>,
    colors: Map<String, Color>,
    cellWidth: Dp,
    cellHeight: Dp,
    currentWeek: Int,
    onClick: (Course) -> Unit
) {
    val course = courses.firstOrNull() ?: return
    val fullHeight = cellHeight * course.length + cellSpacing * (course.length - 1)
    val sortedCourses = remember(courses) {
        courses.sortedWith(
            compareBy<Course> { it.startWeek }
                .thenBy { it.endWeek }
                .thenBy { it.name ?: "" }
        )
    }
    Box(
        modifier = with(CourseCardSpec) {
            Modifier
                .size(
                    cellWidth,
                    fullHeight
                )
                .offset(
                    x = mainColumnWidth +
                                (cellWidth + cellSpacing) * (course.weekday - 1) +
                                cellSpacing,
                    y = mainRowHeight +
                            (cellHeight + cellSpacing) * (course.startTime - 1) +
                                cellSpacing
                )
                .clip(SmoothRoundedCornerShape(8.dp))
                .background(95.a1 withNight 30.n1)
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            sortedCourses.forEachIndexed { index, item ->
                val isCurrentWeek = currentWeek in item.weekIndexes
                val color = colors.getOrElse(item.name) { 50.a1 }
                CompositionLocalProvider(
                    LocalTonalPalettes provides color.toTonalPalettes(
                        style = PaletteStyle.Vibrant,
                        tonalValues = doubleArrayOf()
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(if (isCurrentWeek) color else color.copy(alpha = 0.45f))
                            .clickable { onClick(item) }
                            .padding(4.dp)
                    ) {
                        OverviewCourseContent(
                            course = item,
                            stackedCount = sortedCourses.size
                        )
                    }
                }
                if (index != sortedCourses.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(100.n1.copy(alpha = 0.65f) withNight 0.n1.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.OverviewCourseContent(
    course: Course,
    stackedCount: Int
) {
    Text(
        text = course.name ?: "",
        modifier = Modifier.padding(bottom = 38.dp),
        color = 100.n1,
        fontWeight = FontWeight.Bold,
        maxLines = if (stackedCount <= 1) 3 else 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = course.weekRangeText(),
            color = 100.n1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
        OverviewLocationPill(
            text = course.location.shortScheduleLocation(),
            maxLines = if (stackedCount <= 1) 2 else 1
        )
    }
}

@Composable
private fun OverviewLocationPill(
    text: String,
    maxLines: Int
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(6.dp))
            .background(95.a1 withNight 30.n2)
            .padding(2.dp),
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        style = TextStyle(
            fontSize = 11.sp,
            color = 10.n1 withNight 90.n1,
            fontWeight = FontWeight.Bold
        )
    )
}
