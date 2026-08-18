package com.daejin.woojintoday.ui.screens.timetable

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.data.model.TimeRegion
import com.daejin.woojintoday.data.model.Weekday
import com.daejin.woojintoday.ui.components.ResponsiveContainer
import com.daejin.woojintoday.ui.icons.IconChevronLeft
import com.daejin.woojintoday.ui.icons.IconClose
import com.daejin.woojintoday.ui.icons.IconSparkle
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.SurfaceElevated
import com.daejin.woojintoday.ui.theme.TextDisabled
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import com.daejin.woojintoday.ui.theme.TimetablePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AiTimetable"
private const val STEP_COUNT = 5
private val WEEKDAYS_MON_TO_FRI = listOf(Weekday.MON, Weekday.TUE, Weekday.WED, Weekday.THU, Weekday.FRI)

private enum class DaysOffMode { SPECIFIC, ANY_N, ANY }
private enum class TimeOfDayPref { MORNING, AFTERNOON, MIXED, ANY }

/** 위저드에서 모은 답변 — "조건"이 정리되면 실제 생성 알고리즘이 이 값들을 그대로 입력으로 받게 될
 *  것이라, 지금은 UI 상태를 들고 있는 용도로만 쓰인다. */
private class AiTimetableAnswers {
    var credits by mutableIntStateOf(15)
    var daysOffMode by mutableStateOf(DaysOffMode.ANY)
    var specificDaysOff by mutableStateOf<Set<Weekday>>(emptySet())
    var anyDaysOffCount by mutableIntStateOf(1)
    var timeOfDayPref by mutableStateOf(TimeOfDayPref.ANY)
    var ratioDoesNotMatter by mutableStateOf(false)
    var majorPercent by mutableIntStateOf(50)
    var ownMajorWithinMajorPercent by mutableIntStateOf(100)
    var preferEasyLectures by mutableStateOf(true)
}

/**
 * "AI로 시간표 짜기" — 학점/공강/오전오후/전공교양비율/기타옵션을 카드 한 장씩 넘겨가며 물어본 뒤,
 * 제안 시간표 3개를 시간표 그리드 그대로 보여준다(상세보기만 가능, 수정 불가). 하나를 골라 완료를
 * 누르면 [onApply]로 그 과목 목록을 넘긴다.
 *
 * [excludedCourseNames]는 이미 이수한 과목(이수구분표에서 조회, 과목명 기준)이라 후보에서 제외되고,
 * [studentGrade]는 전공 과목을 내 학년 이상으로만 채우는 데 쓰인다. 실제 생성 로직은
 * [generateTimetables] 참고.
 */
@Composable
fun AiTimetableDialog(
    courses: List<Course>,
    year: Int,
    semester: Int,
    excludedCourseNames: Set<String> = emptySet(),
    studentGrade: String? = null,
    studentDepartment: String? = null,
    courseDepartments: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onApply: (List<Course>) -> Unit
) {
    val answers = remember { AiTimetableAnswers() }
    var showResults by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<List<Course>>>(emptyList()) }
    var showInfeasibleConfirm by remember { mutableStateOf(false) }
    // 페이지(0~2)별로 독립적으로 "잠근" 과목 — 다시 뽑아도 이 과목들은 그대로 유지된다.
    val lockedKeysByPage = remember { androidx.compose.runtime.mutableStateMapOf<Int, Set<String>>() }
    val regeneratingPages = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
    val inputPagerState = rememberPagerState(pageCount = { STEP_COUNT })
    val resultPagerState = rememberPagerState(pageCount = { results.size })
    val scope = rememberCoroutineScope()

    fun generate(relaxed: Boolean): List<List<Course>> = generateTimetables(
        courses = courses,
        excludedCourseNames = excludedCourseNames,
        studentGrade = studentGrade,
        studentDepartment = studentDepartment,
        courseDepartments = courseDepartments,
        answers = answers,
        relaxed = relaxed,
        lockedKeysByPage = lockedKeysByPage
    )

    fun toggleLock(page: Int, course: Course) {
        val current = lockedKeysByPage[page].orEmpty()
        lockedKeysByPage[page] = if (course.courseKey in current) current - course.courseKey else current + course.courseKey
    }

    fun regeneratePage(page: Int) {
        if (regeneratingPages[page] == true) return
        scope.launch {
            regeneratingPages[page] = true
            val currentCombo = results.getOrNull(page)?.map { it.courseKey }?.toSet().orEmpty()
            val newCombo = withContext(Dispatchers.Default) {
                generateTimetables(
                    courses = courses,
                    excludedCourseNames = excludedCourseNames,
                    studentGrade = studentGrade,
                    studentDepartment = studentDepartment,
                    courseDepartments = courseDepartments,
                    answers = answers,
                    relaxed = false,
                    lockedKeysByPage = mapOf(0 to lockedKeysByPage[page].orEmpty()),
                    count = 1,
                    seedOffset = kotlin.random.Random.nextInt(),
                    initialExcludeCombos = if (currentCombo.isNotEmpty()) listOf(currentCombo) else emptyList()
                ).firstOrNull()
            }
            if (!newCombo.isNullOrEmpty() && page < results.size) {
                results = results.toMutableList().also { it[page] = newCombo }
            }
            regeneratingPages[page] = false
        }
    }

    if (showInfeasibleConfirm) {
        AlertDialog(
            onDismissRequest = { showInfeasibleConfirm = false },
            title = { Text("조건에 맞는 과목이 없어요") },
            text = { Text("설정한 조건을 모두 만족하는 시간표를 찾지 못했어요. 조건을 최대한 반영해서 그래도 만들어드릴까요?") },
            confirmButton = {
                TextButton(onClick = {
                    showInfeasibleConfirm = false
                    results = generate(relaxed = true)
                    showResults = true
                }) { Text("그래도 만들어줘") }
            },
            dismissButton = {
                TextButton(onClick = { showInfeasibleConfirm = false }) { Text("다시 설정할게요") }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Background)) {
        ResponsiveContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            AiDialogHeader(
                showResults = showResults,
                resultPagerState = resultPagerState,
                onDismiss = onDismiss
            )

            if (!showResults) {
                val onBack: () -> Unit = { scope.launch { inputPagerState.animateScrollToPage(inputPagerState.currentPage - 1) } }
                val onNext: () -> Unit = {
                    if (inputPagerState.currentPage == STEP_COUNT - 1) {
                        val strict = generate(relaxed = false)
                        if (strict.isEmpty()) {
                            showInfeasibleConfirm = true
                        } else {
                            results = strict
                            showResults = true
                        }
                    } else {
                        scope.launch { inputPagerState.animateScrollToPage(inputPagerState.currentPage + 1) }
                    }
                }
                HorizontalPager(
                    state = inputPagerState,
                    userScrollEnabled = false,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> CreditsStep(answers, page, onBack, onNext)
                        1 -> DaysOffStep(answers, page, onBack, onNext)
                        2 -> TimeOfDayStep(answers, page, onBack, onNext)
                        3 -> RatioStep(answers, page, onBack, onNext)
                        else -> ExtraOptionsStep(answers, page, onBack, onNext)
                    }
                }
            } else {
                HorizontalPager(
                    state = resultPagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { page ->
                    AiResultPage(
                        courses = results.getOrElse(page) { emptyList() },
                        year = year,
                        semester = semester,
                        lockedCourseKeys = lockedKeysByPage[page].orEmpty(),
                        onToggleLock = { course -> toggleLock(page, course) },
                        isRegenerating = regeneratingPages[page] == true,
                        onRegenerate = { regeneratePage(page) }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .padding(bottom = 56.dp) // 화면 맨 아래에 딱 붙지 않도록 이 요소 높이만큼 위로 띄운다
                        .offset(y = (-16).dp), // 위저드 이전/다음 버튼과 같은 정도로 살짝 더 위로
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showResults = false },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("이전")
                    }
                    Button(
                        onClick = {
                            onApply(results.getOrElse(resultPagerState.currentPage) { emptyList() })
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                        enabled = results.getOrElse(resultPagerState.currentPage) { emptyList() }.isNotEmpty()
                    ) {
                        Text("이 시간표로 할래요")
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun AiDialogHeader(
    showResults: Boolean,
    resultPagerState: PagerState,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            IconClose(tint = TextPrimary, size = 16.dp)
        }
        Spacer(modifier = Modifier.weight(1f))
        if (showResults) {
            Text(
                text = "${resultPagerState.currentPage + 1} / ${resultPagerState.pageCount}",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(40.dp)) // 좌측 닫기 버튼과 시각적으로 균형 맞추는 자리
    }
}

@Composable
private fun WizardNavRow(
    showBack: Boolean,
    isLast: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showBack) {
            OutlinedButton(
                onClick = onBack,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                IconChevronLeft(tint = TextPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("이전")
            }
        }
        Button(
            onClick = onNext,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
        ) {
            Text(if (isLast) "시간표 만들기" else "다음")
        }
    }
}

/** 카드 한 장의 공통 뼈대 — 맨 위에 회색 "N/5" 진행도, 그 아래 질문, 그 아래 콘텐츠, 그리고 그
 *  콘텐츠 바로 아래(화면 맨 밑이 아니라)에 이전/다음 버튼이 붙는다. 콘텐츠가 길어지는 카드를
 *  대비해 전체를 스크롤 가능하게 둔다. */
@Composable
private fun StepScaffold(
    question: String,
    stepIndex: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // 이전/다음 버튼까지 포함한 덩어리 전체를 그대로 유지한 채, 그 덩어리를 통째로 살짝
        // 위로 옮긴다 — 안에서 뭘 따로 떼어내지 않는다.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = (-24).dp)
        ) {
            Text(
                text = "${stepIndex + 1}/$STEP_COUNT",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = question,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(28.dp))
            content()
            Spacer(modifier = Modifier.height(28.dp))
            WizardNavRow(showBack = stepIndex > 0, isLast = stepIndex == STEP_COUNT - 1, onBack = onBack, onNext = onNext)
        }
    }
}

/** Material3의 기본 Slider 트랙은 양 끝에 작은 "정지 표시" 점을 그리는데, 이 마법사에선 그냥 매끈한
 *  드래그바로만 보이길 원해서 트랙을 직접 지정해 그 점을 뺀다. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlainSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = SliderDefaults.colors(
        thumbColor = Primary,
        activeTrackColor = Primary,
        inactiveTrackColor = Border,
        disabledThumbColor = Primary,
        disabledActiveTrackColor = Primary,
        disabledInactiveTrackColor = Border
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier,
        colors = colors,
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                enabled = enabled,
                drawStopIndicator = null
            )
        }
    )
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Primary else Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OnPrimary else TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// 카드 1 — 학점
@Composable
private fun CreditsStep(answers: AiTimetableAnswers, stepIndex: Int, onBack: () -> Unit, onNext: () -> Unit) {
    StepScaffold(question = "이번학기 몇학점\n들으실건가요?", stepIndex = stepIndex, onBack = onBack, onNext = onNext) {
        Text(
            text = "${answers.credits}학점",
            style = MaterialTheme.typography.titleLarge,
            color = Primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        PlainSlider(
            value = answers.credits.toFloat(),
            onValueChange = { answers.credits = it.toInt() },
            valueRange = 5f..22f
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("5학점", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("22학점", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

// 카드 2 — 공강
@Composable
private fun DaysOffStep(answers: AiTimetableAnswers, stepIndex: Int, onBack: () -> Unit, onNext: () -> Unit) {
    StepScaffold(question = "공강은 언제로\n할까요?", stepIndex = stepIndex, onBack = onBack, onNext = onNext) {
        val specificMode = answers.daysOffMode == DaysOffMode.SPECIFIC
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().alpha(if (specificMode) 1f else 0.35f)
        ) {
            WEEKDAYS_MON_TO_FRI.forEach { day ->
                val selected = day in answers.specificDaysOff
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected && specificMode) Primary else Surface)
                        // 항상 눌릴 수 있어야 한다 — 다른 모드(아무 요일이나/상관없어요)에 있다가도
                        // 여기를 누르면 그 즉시 "특정 요일" 모드로 돌아와야 하므로, 여기서 막으면
                        // 영영 못 돌아온다. 흐려 보이는 건 순전히 "지금 이 모드가 아님" 표시일 뿐.
                        .clickable {
                            if (specificMode) {
                                answers.specificDaysOff = if (selected) {
                                    answers.specificDaysOff - day
                                } else if (answers.specificDaysOff.size < WEEKDAYS_MON_TO_FRI.size - 1) {
                                    answers.specificDaysOff + day
                                } else {
                                    answers.specificDaysOff // 5일 다 비울 순 없음 — 마지막 한 요일은 항상 남겨둠
                                }
                            } else {
                                // 다른 모드에서 넘어오는 첫 탭 — 그 모드에 있는 동안 쌓여있던 이전
                                // 선택은 버리고, 지금 누른 요일 하나로 새로 시작한다(안 그러면 이전에
                                // 골라뒀던 요일들과 합쳐져서 여러 개가 한꺼번에 선택된 것처럼 보인다).
                                answers.daysOffMode = DaysOffMode.SPECIFIC
                                answers.specificDaysOff = setOf(day)
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.label.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected && specificMode) OnPrimary else TextPrimary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (answers.daysOffMode == DaysOffMode.ANY_N) Primary else Surface)
                .clickable { answers.daysOffMode = DaysOffMode.ANY_N }
                .padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val onAnyN = answers.daysOffMode == DaysOffMode.ANY_N
            Text(
                text = "아무 요일이나",
                style = MaterialTheme.typography.labelLarge,
                color = if (onAnyN) OnPrimary else TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..4).forEach { n ->
                    val active = onAnyN && answers.anyDaysOffCount == n
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (active) OnPrimary else SurfaceElevated)
                            .clickable {
                                answers.daysOffMode = DaysOffMode.ANY_N
                                answers.anyDaysOffCount = n
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = n.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) Primary else TextSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "일이면 돼요",
                style = MaterialTheme.typography.labelLarge,
                color = if (onAnyN) OnPrimary else TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ChoiceButton(
            label = "상관없어요",
            selected = answers.daysOffMode == DaysOffMode.ANY,
            onClick = { answers.daysOffMode = DaysOffMode.ANY }
        )
    }
}

// 카드 3 — 오전/오후
@Composable
private fun TimeOfDayStep(answers: AiTimetableAnswers, stepIndex: Int, onBack: () -> Unit, onNext: () -> Unit) {
    StepScaffold(question = "오전 수업, 오후 수업 중\n언제가 좋으세요?", stepIndex = stepIndex, onBack = onBack, onNext = onNext) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceButton("오전 수업이 좋아요", answers.timeOfDayPref == TimeOfDayPref.MORNING) {
                answers.timeOfDayPref = TimeOfDayPref.MORNING
            }
            ChoiceButton("오후 수업이 좋아요", answers.timeOfDayPref == TimeOfDayPref.AFTERNOON) {
                answers.timeOfDayPref = TimeOfDayPref.AFTERNOON
            }
            ChoiceButton("적당히 섞어주세요", answers.timeOfDayPref == TimeOfDayPref.MIXED) {
                answers.timeOfDayPref = TimeOfDayPref.MIXED
            }
            ChoiceButton("상관없어요", answers.timeOfDayPref == TimeOfDayPref.ANY) {
                answers.timeOfDayPref = TimeOfDayPref.ANY
            }
        }
    }
}

// 카드 4 — 전공/교양 비율
@Composable
private fun RatioStep(answers: AiTimetableAnswers, stepIndex: Int, onBack: () -> Unit, onNext: () -> Unit) {
    StepScaffold(question = "전공과 교양 비율을\n정해주세요", stepIndex = stepIndex, onBack = onBack, onNext = onNext) {
        Column(modifier = Modifier.alpha(if (answers.ratioDoesNotMatter) 0.35f else 1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "전공(주전공+타전공) ${answers.majorPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Text(
                    text = "교양 ${100 - answers.majorPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }
            PlainSlider(
                value = answers.majorPercent.toFloat(),
                // "상관없어요" 상태에서도 슬라이더를 만지면(탭/드래그) 그 자체로 다시 비율 모드로
                // 돌아온다 — 공강 스텝의 요일 칩과 같은 패턴("반대편을 누르면 그게 활성화").
                onValueChange = {
                    answers.ratioDoesNotMatter = false
                    answers.majorPercent = (it / 5).toInt() * 5
                },
                valueRange = 0f..100f,
                enabled = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface)
                    .padding(16.dp)
            ) {
                Text(
                    text = "전공 비중 중에서, 주전공과 타전공 비율",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "주전공 ${answers.ownMajorWithinMajorPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "타전공 ${100 - answers.ownMajorWithinMajorPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                PlainSlider(
                    value = answers.ownMajorWithinMajorPercent.toFloat(),
                    onValueChange = {
                        answers.ratioDoesNotMatter = false
                        answers.ownMajorWithinMajorPercent = (it / 5).toInt() * 5
                    },
                    valueRange = 0f..100f,
                    enabled = true
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        ChoiceButton(
            label = "상관없어요",
            selected = answers.ratioDoesNotMatter,
            onClick = { answers.ratioDoesNotMatter = true }
        )
    }
}

// 카드 5 — 추가 옵션
@Composable
private fun ExtraOptionsStep(answers: AiTimetableAnswers, stepIndex: Int, onBack: () -> Unit, onNext: () -> Unit) {
    StepScaffold(question = "추가로 원하는\n옵션이 있나요?", stepIndex = stepIndex, onBack = onBack, onNext = onNext) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceButton("꿀강의 위주로 담아주세요!", answers.preferEasyLectures) {
                answers.preferEasyLectures = true
            }
            ChoiceButton("상관없어요", !answers.preferEasyLectures) {
                answers.preferEasyLectures = false
            }
        }
    }
}

// 결과 카드 — 시간표 그리드를 읽기 전용으로 그대로 보여준다(상세보기만 가능, 수정 불가).
@Composable
private fun AiResultPage(
    courses: List<Course>,
    year: Int,
    semester: Int,
    lockedCourseKeys: Set<String>,
    onToggleLock: (Course) -> Unit,
    isRegenerating: Boolean,
    onRegenerate: () -> Unit
) {
    var detailCourse by remember { mutableStateOf<Course?>(null) }
    val colorFor: (Course) -> androidx.compose.ui.graphics.Color = { course ->
        val index = courses.indexOf(course)
        TimetablePalette[if (index >= 0) index % TimetablePalette.size else 0]
    }
    val totalCredits = courses.sumOf { it.credit }

    detailCourse?.let { course ->
        CourseDetailReadOnlyDialog(course = course, year = year, semester = semester, onDismiss = { detailCourse = null })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "총 ${if (totalCredits == totalCredits.toInt().toDouble()) totalCredits.toInt().toString() else totalCredits.toString()}학점 · ${courses.size}과목",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            RegenerateButton(isLoading = isRegenerating, onClick = onRegenerate)
        }
        WeeklyTimetable(
            courses = courses,
            colorFor = colorFor,
            onCourseClick = { course -> detailCourse = course },
            onCourseRemove = {},
            onGridTap = {},
            previewCourse = null,
            previewColor = Primary,
            regions = emptyList<TimeRegion>(),
            onRegionUpdate = { _, _ -> },
            onRegionCreate = { 0 },
            onRegionFinalize = {},
            showRemoveButton = false,
            lockedCourseKeys = lockedCourseKeys,
            onToggleLock = onToggleLock,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

private val REGENERATE_LOADING_MESSAGES = listOf("시간표를 만들고 있어요!", "잠시만 기다려주세요!")

/** "다시 주세요!" 알약(pill) 버튼 — 위저드 카드들의 라운드 버튼과 같은 느낌으로 배경을 채운 작은
 *  버튼이되, Material Button의 기본 최소 높이를 쓰지 않고 직접 패딩만 줘서 이 줄의 세로폭이
 *  늘어나지 않게 한다. 로딩 중엔 두 문구를 번갈아 보여주며 재클릭을 막는다. */
@Composable
private fun RegenerateButton(isLoading: Boolean, onClick: () -> Unit) {
    var messageIndex by remember { mutableStateOf(0) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            messageIndex = 0
            while (true) {
                delay(900)
                messageIndex = (messageIndex + 1) % REGENERATE_LOADING_MESSAGES.size
            }
        }
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isLoading) Surface else Primary)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = if (isLoading) REGENERATE_LOADING_MESSAGES[messageIndex] else "다시 주세요!",
            style = MaterialTheme.typography.labelMedium,
            color = if (isLoading) TextSecondary else OnPrimary
        )
    }
}

/** [CourseDetailDialog]는 강의계획서 보기로 더 들어갈 수 있는데, 그 흐름은 실제 시간표 화면과
 *  똑같이 그대로 재사용한다(읽기 전용 제약은 애초에 이 다이얼로그들 자체가 "삭제" 버튼 같은 편집
 *  기능이 없어서 별도 처리 없이도 자연히 지켜진다). */
@Composable
private fun CourseDetailReadOnlyDialog(course: Course, year: Int, semester: Int, onDismiss: () -> Unit) {
    var showSyllabus by remember { mutableStateOf(false) }
    if (showSyllabus) {
        SyllabusDialog(
            course = course,
            year = year,
            semester = semester,
            onDismiss = { showSyllabus = false }
        )
    } else {
        CourseDetailDialog(
            course = course,
            year = year,
            semester = semester,
            onDismiss = onDismiss,
            onShowSyllabus = { showSyllabus = true }
        )
    }
}

private val MAJOR_CATEGORIES = setOf("전필", "전선", "전기")

/** 12시가 아니라 11시 30분부터를 "오후"로 본다. */
private const val AFTERNOON_CUTOFF_MINUTES = 11 * 60 + 30

/** course.grade가 "3" 또는 "3학년"처럼 표기가 다를 수 있어 숫자만 뽑아 비교한다. */
private fun gradeDigits(text: String): String = text.filter { it.isDigit() }

/** course.ratio(경쟁률)가 "1.5"/"1.5:1" 등 표기가 섞여 있을 수 있어 첫 숫자만 뽑는다. */
private fun parseRatio(text: String): Double =
    Regex("""[0-9]+(\.[0-9]+)?""").find(text)?.value?.toDoubleOrNull() ?: 0.0

private fun hasConflict(picked: List<Course>, candidate: Course): Boolean =
    picked.any { existing ->
        existing.sessions.any { a ->
            candidate.sessions.any { b -> a.day == b.day && a.startMinutes < b.endMinutes && b.startMinutes < a.endMinutes }
        }
    }

private fun daysOff(picked: List<Course>): Set<Weekday> {
    val used = picked.flatMap { course -> course.sessions.map { it.day } }.toSet()
    return WEEKDAYS_MON_TO_FRI.toSet() - used
}

/** 오전/오후를 콕 집어 골랐을 때, 이 과목이 그 선호를 벗어나는 세션을 하나라도 갖고 있는지
 *  (한 과목 전체가 벗어나는 셈 쳐서 "과목 단위"로 위반을 센다 — "섞어주세요/상관없어요"는 위반
 *  개념 자체가 없다). */
private fun courseViolatesTimePref(course: Course, pref: TimeOfDayPref): Boolean = when (pref) {
    TimeOfDayPref.MORNING -> course.sessions.any { it.startMinutes >= AFTERNOON_CUTOFF_MINUTES }
    TimeOfDayPref.AFTERNOON -> course.sessions.any { it.startMinutes < AFTERNOON_CUTOFF_MINUTES }
    TimeOfDayPref.MIXED, TimeOfDayPref.ANY -> false
}

private fun isOwnMajor(course: Course, studentDepartment: String?, courseDepartments: Map<String, String>): Boolean =
    course.category in MAJOR_CATEGORIES && courseDepartments[course.courseKey] == studentDepartment

private fun isOtherMajor(course: Course, studentDepartment: String?, courseDepartments: Map<String, String>): Boolean =
    course.category in MAJOR_CATEGORIES && !isOwnMajor(course, studentDepartment, courseDepartments)

/** 전공(전필/전선/전기) 과목의 "같은 과목" 기준은 이름만으로는 부족하고 같은 학과여야 한다 —
 *  다른 학과의 동명 전공과목(예: 학과별 캡스톤디자인)은 다른 과목이다. 교양 등 나머지 카테고리는
 *  학과 구분이 없어 이름만 같으면 같은 과목으로 본다. */
private fun isSameCourseAsCompleted(
    course: Course,
    excludedCourseNames: Set<String>,
    studentDepartment: String?,
    courseDepartments: Map<String, String>
): Boolean {
    if (course.name !in excludedCourseNames) return false
    if (course.category !in MAJOR_CATEGORIES) return true
    return courseDepartments[course.courseKey] == studentDepartment
}

/** [relaxed]가 false면 하드 제약을 그대로 적용한 후보 목록을, true면(조건을 다 만족하는 조합이
 *  하나도 없을 때 사용자가 "그래도 만들어줘"를 눌렀을 때) 이미 이수한 과목 제외만 남기고 나머지는
 *  전부 점수(soft) 쪽으로 넘긴다 — 그래야 뭐라도 결과가 나온다. */
private fun buildPool(
    courses: List<Course>,
    excludedCourseNames: Set<String>,
    studentDepartment: String?,
    courseDepartments: Map<String, String>,
    ownGrade: Int?,
    answers: AiTimetableAnswers,
    relaxed: Boolean
): List<Course> {
    val base = courses.filter { course ->
        course.sessions.isNotEmpty() &&
            !isSameCourseAsCompleted(course, excludedCourseNames, studentDepartment, courseDepartments) &&
            // 타전공 과목 중 비고란에 "제한"이 박혀있으면(수강 제한 대상이라는 뜻) 아예 후보에서 뺀다.
            !(isOtherMajor(course, studentDepartment, courseDepartments) && course.note.contains("제한"))
    }
    if (relaxed) return base

    var pool = base.filter { course ->
        if (!course.name.contains("캡스톤")) return@filter true
        courseDepartments[course.courseKey] == studentDepartment
    }

    if (ownGrade != null) {
        pool = pool.filter { course ->
            if (course.category !in MAJOR_CATEGORIES) return@filter true
            val courseGrade = gradeDigits(course.grade).toIntOrNull() ?: return@filter true
            courseGrade >= ownGrade
        }
    }

    if (!answers.ratioDoesNotMatter) {
        when (answers.majorPercent) {
            0 -> pool = pool.filter { it.category !in MAJOR_CATEGORIES }
            100 -> pool = pool.filter { it.category in MAJOR_CATEGORIES }
        }
        when (answers.ownMajorWithinMajorPercent) {
            0 -> pool = pool.filter { it.category !in MAJOR_CATEGORIES || courseDepartments[it.courseKey] != studentDepartment }
            100 -> pool = pool.filter { it.category !in MAJOR_CATEGORIES || courseDepartments[it.courseKey] == studentDepartment }
        }
    }

    val specificDaysOff = if (answers.daysOffMode == DaysOffMode.SPECIFIC) answers.specificDaysOff else emptySet()
    if (specificDaysOff.isNotEmpty()) {
        pool = pool.filter { course -> course.sessions.none { it.day in specificDaysOff } }
    }

    return pool
}

/** 그리디 배치를 오전/오후 선호를 지키는 과목부터 먼저 채우고, 그걸로 목표 학점을 못 채웠을 때만
 *  선호를 벗어나는 과목을 딱 1개까지 끼워 넣는다 — 순서를 안 나누고 그냥 섞어서 채우면 어길 필요가
 *  없는데도 운 나쁘게 먼저 뽑혀서 선호가 안 지켜지는 문제가 있었다. */
/** 오전/오후 선호를 지키는 과목을 먼저, 어길 수밖에 없는 과목은 뒤로 미룬다(호출부에서 최대 1개로
 *  캡을 건다) — 이미 정해둔 [list] 순서(우선순위/셔플)는 그대로 유지한 채 시간 위반 여부로만
 *  다시 나눈다. */
private fun applyTimePrefOrder(list: List<Course>, enforceTimeCap: Boolean, timeOfDayPref: TimeOfDayPref): List<Course> =
    if (!enforceTimeCap) list else {
        val (compliant, violating) = list.partition { !courseViolatesTimePref(it, timeOfDayPref) }
        compliant + violating
    }

/**
 * 그리디 배치.
 *
 * 전공/교양 비율을 신경 쓰는 경우([tieredByMajor])엔 무작정 섞어서 채우지 않고 "주전공 먼저 최대한
 * 채우고, 그다음 타전공, 그다음 교양"의 우선순위 계층으로 채운다 — 순수 무작위 셔플로 채우면
 * 표본 안에 타전공/교양이 압도적으로 많을 때(수백~수천 과목 중 내 학과 전공은 몇 개 안 됨) 주전공이
 * 운 나쁘게 밀려나는 문제가 있었다. 각 계층에는 각자의 학점 예산이 있고, 어느 한 계층의 공급이
 * 모자라면 남은 학점은 다음 계층이 이어받는다(우선순위는 지키되 목표 학점은 최대한 채움).
 * 타전공을 고를 땐 비고란에 텍스트가 없는(제한 조건이 없어 보이는) 과목을 먼저 본다.
 *
 * 오전/오후 선호(전역 최대 1개 위반 허용)와 "같은 과목명 두 번 금지"는 계층과 무관하게 항상 지킨다.
 */
private fun greedyPick(
    pool: List<Course>,
    seed: Int,
    targetCredits: Int,
    enforceTimeCap: Boolean,
    timeOfDayPref: TimeOfDayPref,
    tieredByMajor: Boolean,
    ownMajorCreditBudget: Double,
    otherMajorCreditBudget: Double,
    studentDepartment: String?,
    courseDepartments: Map<String, String>,
    lockedCourses: List<Course> = emptyList()
): List<Course> {
    val random = kotlin.random.Random(seed * 7919 + 17)

    val picked = mutableListOf<Course>()
    var creditSum = 0.0
    var violatingCount = 0
    // "다시 주세요"로 일부 과목을 고정("잠금")한 채 나머지만 다시 뽑을 때 — 잠긴 과목을 먼저
    // picked에 넣어두면 이후 tryAdd의 이름중복/시간충돌/학점상한 체크가 자연히 이를 존중한다.
    lockedCourses.forEach { course ->
        picked += course
        creditSum += course.credit
        if (enforceTimeCap && courseViolatesTimePref(course, timeOfDayPref)) violatingCount += 1
    }
    fun tryAdd(course: Course): Boolean {
        if (creditSum + course.credit > targetCredits + 3) return false
        if (picked.any { it.name == course.name }) return false // 같은 과목명(다른 분반)이 한 시간표에 두 번 들어가면 안 됨
        if (hasConflict(picked, course)) return false
        val violates = enforceTimeCap && courseViolatesTimePref(course, timeOfDayPref)
        if (violates && violatingCount >= 1) return false // 선호를 벗어나는 과목은 전체 시간표에 최대 1개까지만
        picked += course
        creditSum += course.credit
        if (violates) violatingCount += 1
        return true
    }

    fun fillFrom(list: List<Course>, budget: Double? = null) {
        var budgetUsed = 0.0
        for (course in list) {
            if (creditSum >= targetCredits) return
            if (budget != null && budgetUsed >= budget) return
            if (tryAdd(course)) budgetUsed += course.credit
        }
    }

    if (tieredByMajor) {
        val ownMajor = applyTimePrefOrder(pool.filter { isOwnMajor(it, studentDepartment, courseDepartments) }.shuffled(random), enforceTimeCap, timeOfDayPref)
        val otherMajor = applyTimePrefOrder(
            pool.filter { it.category in MAJOR_CATEGORIES && !isOwnMajor(it, studentDepartment, courseDepartments) }
                .shuffled(random)
                .sortedBy { if (it.note.isBlank()) 0 else 1 }, // 비고란에 제한 조건 텍스트가 없는 과목을 우선
            enforceTimeCap,
            timeOfDayPref
        )
        val general = applyTimePrefOrder(pool.filter { it.category !in MAJOR_CATEGORIES }.shuffled(random), enforceTimeCap, timeOfDayPref)
        val generalCreditBudget = targetCredits - ownMajorCreditBudget - otherMajorCreditBudget

        // 잠긴 과목이 이미 어느 계층의 예산을 얼마나 써뒀는지 빼줘야, 그 계층에 더 채울 수 있는
        // "남은" 예산이 정확해진다(안 그러면 잠금 과목 학점만큼 매번 그 계층이 초과 배정됨).
        val lockedOwnMajorCredits = lockedCourses.filter { isOwnMajor(it, studentDepartment, courseDepartments) }.sumOf { it.credit }
        val lockedOtherMajorCredits = lockedCourses.filter { it.category in MAJOR_CATEGORIES && !isOwnMajor(it, studentDepartment, courseDepartments) }.sumOf { it.credit }
        val lockedGeneralCredits = lockedCourses.filter { it.category !in MAJOR_CATEGORIES }.sumOf { it.credit }

        fillFrom(ownMajor, (ownMajorCreditBudget - lockedOwnMajorCredits).coerceAtLeast(0.0))
        fillFrom(otherMajor, (otherMajorCreditBudget - lockedOtherMajorCredits).coerceAtLeast(0.0))
        fillFrom(general, (generalCreditBudget - lockedGeneralCredits).coerceAtLeast(0.0)) // 교양도 자기 몫의 예산 안에서 먼저 채운다 — 안 그러면 뒤의 무예산 폴백에서 주전공한테 밀려 사라짐
        // 어느 계층이든 공급이 모자라 목표 학점을 못 채웠으면, 우선순위 순서 그대로 예산 없이 이어서 채운다.
        fillFrom(ownMajor + otherMajor + general)
    } else {
        fillFrom(applyTimePrefOrder(pool.shuffled(random), enforceTimeCap, timeOfDayPref))
    }

    return picked
}

/**
 * 실제 조건 기반 생성 알고리즘.
 *
 * 하드 제약(무조건 지킴, [relaxed]가 아닐 때):
 * - 이미 이수한 과목 제외 — 전공은 학과+이름이 같아야, 그 외엔 이름만 같아도 같은 과목으로 본다.
 * - 전공(전필/전선/전기)은 내 학년 미만 제외.
 * - 전공/교양 비율, 그리고 전공 중 주전공/타전공 비율이 정확히 0% 또는 100%면 그 카테고리만 남김.
 * - 공강 요일을 콕 집었으면 그 요일엔 아예 수업이 없는 과목만 후보로 남김.
 * - 캡스톤디자인은 내 학과 것만 — 학과를 확인할 수 없으면(인덱스 실패 등) 안전하게 제외한다.
 * - 오전/오후를 콕 집어 골랐으면, 그 선호를 벗어나는 과목은 시간표 하나에 최대 1개까지만 —
 *   정말 어쩔 수 없을 때 하나 정도는 봐주되 그 이상은 절대 넘지 않는다.
 *
 * 나머지는 가중치 스코어링(공강 조건이 가장 큰 가중치, 그다음 오전/오후 위반 여부, 전공/교양 및
 * 주전공/타전공 비율 근사치, 꿀강의(경쟁률) 선호, 전공 학년은 내 학년에 가까울수록 가점) — 완전
 * 탐색은 폰에서 느릴 수 있어, 무작위 재시작으로 여러 조합을 만든 뒤 점수가 가장 높은 서로 다른
 * [count]개를 고르는 방식으로 근사한다.
 *
 * [relaxed]는 위 하드 제약을 전부 만족하는 조합이 하나도 없을 때(즉 1차 시도가 빈 결과일 때)만
 * true로 다시 호출된다 — 하드 제약을 점수 가중치로 낮춰서라도 뭔가는 결과를 내놓는다.
 */
private fun generateTimetables(
    courses: List<Course>,
    excludedCourseNames: Set<String>,
    studentGrade: String?,
    studentDepartment: String?,
    courseDepartments: Map<String, String>,
    answers: AiTimetableAnswers,
    relaxed: Boolean = false,
    lockedKeysByPage: Map<Int, Set<String>> = emptyMap(),
    count: Int = 3,
    // "다시 주세요"를 반복해서 눌러도 매번 같은 250개 시드로 똑같은 최고점 조합만 나오는 걸
    // 막기 위한 오프셋 — 매 호출마다 다른 값을 주면 다른 표본을 탐색해 다른 결과가 나온다.
    seedOffset: Int = 0,
    // "다시 주세요"로 재생성할 때, 조건이 빡빡해서 최고점 조합이 매번 우연히 똑같이 나오는 걸
    // 막기 위해 지금 화면에 떠 있는 조합을 미리 넣어둔다 — 정말 대안이 하나도 없을 때만(조합
    // 250개를 다 뒤져도 이것뿐일 때만) 결국 같은 조합으로 되돌아온다.
    initialExcludeCombos: List<Set<String>> = emptyList()
): List<List<Course>> {
    val ownGrade = studentGrade?.let(::gradeDigits)?.toIntOrNull()
    val pool = buildPool(courses, excludedCourseNames, studentDepartment, courseDepartments, ownGrade, answers, relaxed)
    Log.d(
        TAG,
        "relaxed=$relaxed 입력과목=${courses.size} 제외과목명=${excludedCourseNames.size} 학과인덱스=${courseDepartments.size} " +
            "내학과=$studentDepartment 내학년=$ownGrade majorPercent=${answers.majorPercent} ownMajorPercent=${answers.ownMajorWithinMajorPercent} " +
            "ratioDoesNotMatter=${answers.ratioDoesNotMatter} timeOfDay=${answers.timeOfDayPref} " +
            "pool=${pool.size} pool중전공=${pool.count { it.category in MAJOR_CATEGORIES }} " +
            "pool중내학과전공=${pool.count { isOwnMajor(it, studentDepartment, courseDepartments) }}"
    )
    if (pool.isEmpty()) return emptyList()

    val targetCredits = answers.credits
    val majorRatioTarget = answers.majorPercent / 100.0
    val ownMajorRatioTarget = answers.ownMajorWithinMajorPercent / 100.0
    val scoreMajorRatio = !answers.ratioDoesNotMatter && (relaxed || answers.majorPercent in 1..99)
    val scoreOwnMajorRatio = !answers.ratioDoesNotMatter && (relaxed || answers.ownMajorWithinMajorPercent in 1..99)
    val specificDaysOff = if (answers.daysOffMode == DaysOffMode.SPECIFIC) answers.specificDaysOff else emptySet()
    val enforceTimeCap = answers.timeOfDayPref == TimeOfDayPref.MORNING || answers.timeOfDayPref == TimeOfDayPref.AFTERNOON

    // 비율을 신경 쓰는 경우, "주전공 먼저 최대한 채우고 그다음 타전공" 계층으로 채우기 위한 학점 예산.
    val tieredByMajor = !answers.ratioDoesNotMatter && !relaxed
    val majorCreditBudget = targetCredits * majorRatioTarget
    val ownMajorCreditBudget = majorCreditBudget * ownMajorRatioTarget
    val otherMajorCreditBudget = majorCreditBudget - ownMajorCreditBudget

    fun score(picked: List<Course>): Double {
        var s = 0.0
        val totalCredits = picked.sumOf { it.credit }
        s -= kotlin.math.abs(totalCredits - targetCredits) * 5.0

        if (answers.daysOffMode == DaysOffMode.ANY_N) {
            val off = daysOff(picked).size
            val shortfall = (answers.anyDaysOffCount - off).coerceAtLeast(0)
            s -= shortfall * 100.0
            s += off * 10.0
        }
        if (relaxed && specificDaysOff.isNotEmpty()) {
            val violatingSessions = picked.sumOf { c -> c.sessions.count { it.day in specificDaysOff } }
            s -= violatingSessions * 100.0
        }

        if (enforceTimeCap) {
            val violatingCourses = picked.count { courseViolatesTimePref(it, answers.timeOfDayPref) }
            s -= violatingCourses * 1000.0
        } else if (answers.timeOfDayPref == TimeOfDayPref.MIXED) {
            val sessions = picked.flatMap { it.sessions }
            if (sessions.isNotEmpty()) {
                val morningFraction = sessions.count { it.startMinutes < AFTERNOON_CUTOFF_MINUTES }.toDouble() / sessions.size
                s += (1.0 - kotlin.math.abs(morningFraction - 0.5) * 2.0) * 15.0
            }
        }

        if (scoreMajorRatio) {
            val majorCredits = picked.filter { it.category in MAJOR_CATEGORIES }.sumOf { it.credit }
            val actualRatio = if (totalCredits > 0) majorCredits / totalCredits else 0.0
            s -= kotlin.math.abs(actualRatio - majorRatioTarget) * 40.0
        }
        if (scoreOwnMajorRatio) {
            val majorCourses = picked.filter { it.category in MAJOR_CATEGORIES }
            val majorCredits = majorCourses.sumOf { it.credit }
            if (majorCredits > 0) {
                val ownMajorCredits = majorCourses.filter { isOwnMajor(it, studentDepartment, courseDepartments) }.sumOf { it.credit }
                val actualOwnRatio = ownMajorCredits / majorCredits
                s -= kotlin.math.abs(actualOwnRatio - ownMajorRatioTarget) * 40.0
            }
        }

        if (relaxed) {
            picked.forEach { course ->
                if (course.name.contains("캡스톤") && courseDepartments[course.courseKey] != studentDepartment) {
                    s -= 500.0
                }
            }
        }

        if (answers.preferEasyLectures && picked.isNotEmpty()) {
            s += picked.map { parseRatio(it.ratio) }.average() * 5.0
        }

        if (ownGrade != null) {
            picked.filter { it.category in MAJOR_CATEGORIES }.forEach { course ->
                val courseGrade = gradeDigits(course.grade).toIntOrNull()
                if (courseGrade != null) {
                    s -= if (courseGrade < ownGrade) (ownGrade - courseGrade) * 300.0 else (courseGrade - ownGrade) * 2.0
                }
            }
        }

        return s
    }

    // 잠긴(고정) 과목이 있는 페이지는 그 과목을 반드시 포함한 채로, 나머지 자리만 다시 채워서
    // 후보를 뽑는다 — "다시 주세요"로 특정 페이지 하나만 다시 뽑을 때도 이 함수를 그대로 재사용한다.
    fun generateOnePage(lockedCourses: List<Course>, excludeCombos: List<Set<String>>): List<Course> {
        val candidates = mutableListOf<Pair<Double, List<Course>>>()
        repeat(RESTART_COUNT) { i ->
            val picked = greedyPick(
                pool = pool,
                seed = seedOffset + i,
                targetCredits = targetCredits,
                enforceTimeCap = enforceTimeCap,
                timeOfDayPref = answers.timeOfDayPref,
                tieredByMajor = tieredByMajor,
                ownMajorCreditBudget = ownMajorCreditBudget,
                otherMajorCreditBudget = otherMajorCreditBudget,
                studentDepartment = studentDepartment,
                courseDepartments = courseDepartments,
                lockedCourses = lockedCourses
            )
            if (picked.isNotEmpty()) candidates += score(picked) to picked
        }
        val ranked = candidates.sortedByDescending { it.first }
        val seenKeys = mutableSetOf<Set<String>>()
        val distinctCandidates = ranked.mapNotNull { (_, picked) ->
            val key = picked.map { it.courseKey }.toSet()
            if (key in excludeCombos || !seenKeys.add(key)) null else picked
        }
        // 매번 무조건 1등만 고르면(특히 "다시 주세요"에서 현재 조합 하나만 제외) 거의 항상 바로
        // 다음 차선책 하나로만 수렴해서 다양성이 떨어진다 — 점수 상위권 후보 중에서 무작위로
        // 골라 다시 눌러도 매번 다른 결과가 나오게 한다.
        return if (distinctCandidates.isNotEmpty()) {
            distinctCandidates.take(5).random()
        } else {
            ranked.firstOrNull()?.second ?: emptyList()
        }
    }

    val results = mutableListOf<List<Course>>()
    val usedCombos = mutableListOf<Set<String>>().apply { addAll(initialExcludeCombos) }
    for (page in 0 until count) {
        val lockedCourses = courses.filter { it.courseKey in lockedKeysByPage[page].orEmpty() }
        val combo = generateOnePage(lockedCourses, usedCombos)
        if (combo.isNotEmpty()) {
            results += combo
            usedCombos += combo.map { it.courseKey }.toSet()
        }
    }

    results.forEachIndexed { index, combo ->
        Log.d(
            TAG,
            "결과[$index] 학점=${combo.sumOf { it.credit }} 과목=" +
                combo.joinToString(", ") { "${it.name}(${it.category}/dept=${courseDepartments[it.courseKey]})" }
        )
    }
    return results
}

private const val RESTART_COUNT = 250
