package com.daejin.woojintoday.ui.screens.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.daejin.woojintoday.data.DepartmentPreferenceStore
import com.daejin.woojintoday.data.StudentProfileStore
import com.daejin.woojintoday.data.model.CourseFilters
import com.daejin.woojintoday.data.model.Department
import com.daejin.woojintoday.data.model.Departments
import com.daejin.woojintoday.data.model.FixedCourseCategories
import com.daejin.woojintoday.data.model.FixedCourseCredits
import com.daejin.woojintoday.data.model.FixedCourseGrades
import com.daejin.woojintoday.data.model.GenEdAreas
import com.daejin.woojintoday.data.model.TimeRangeFilter
import com.daejin.woojintoday.data.model.Weekday
import com.daejin.woojintoday.ui.icons.IconChevronDown
import com.daejin.woojintoday.ui.icons.IconClose
import com.daejin.woojintoday.ui.icons.IconSearch
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.BorderFocused
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextDisabled
import com.daejin.woojintoday.ui.theme.TextPlaceholder
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary

private const val TIME_MIN = 480 // 08:00
private const val TIME_MAX = 1320 // 22:00
private const val TIME_STEP = 30
private val WEEKDAY_FILTER_OPTIONS = listOf(Weekday.MON, Weekday.TUE, Weekday.WED, Weekday.THU, Weekday.FRI)
// Material3 DropdownMenuItem 기본 한 줄 높이(48dp) 기준 — maxVisibleItems만큼만 보이게 자를 때 쓴다.
private val DROPDOWN_ITEM_HEIGHT = 48.dp

private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/** Bubble row for every currently-active filter — tapping × on one removes just that value. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveFilterChips(
    filters: CourseFilters,
    onRemoveDepartment: () -> Unit,
    onRemoveRegion: (Int) -> Unit,
    onRemoveGrade: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onRemoveGenEdArea: (String) -> Unit,
    onRemoveCredit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (filters.isEmpty) return
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        filters.department?.let { FilterChip(it.name, onRemoveDepartment) }
        filters.regions.forEachIndexed { index, region ->
            val dayLabel = if (region.days.isEmpty()) "매일" else region.days.sortedBy { it.ordinal }.joinToString(",") { it.label.toString() }
            FilterChip("$dayLabel ${formatMinutes(region.timeRange.startMinutes)}~${formatMinutes(region.timeRange.endMinutes)}", { onRemoveRegion(index) })
        }
        filters.grades.forEach { grade -> FilterChip(grade, { onRemoveGrade(grade) }) }
        filters.categories.forEach { category -> FilterChip(category, { onRemoveCategory(category) }) }
        filters.genEdAreas.forEach { area -> FilterChip("교선($area)", { onRemoveGenEdArea(area) }) }
        if (filters.credits.isNotEmpty()) {
            val numbers = filters.credits.map { it.filter(Char::isDigit) }.sorted()
            FilterChip("${numbers.joinToString(",")}학점", onRemoveCredit)
        }
    }
}

@Composable
private fun FilterChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Primary)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnPrimary)
        Spacer(modifier = Modifier.width(4.dp))
        Box(modifier = Modifier.clickable(onClick = onRemove).padding(2.dp)) {
            IconClose(tint = OnPrimary, size = 12.dp)
        }
    }
}

/** Expandable panel below the search bar — a single horizontally-scrollable row of compact
 *  controls (전공/요일/시간/학년/이수구분/학점), with the 교수명 text field underneath. */
@Composable
fun CourseFilterPanel(
    filters: CourseFilters,
    onDepartmentClick: () -> Unit,
    onToggleDay: (Weekday) -> Unit,
    onTimeRangeChange: (TimeRangeFilter?) -> Unit,
    onToggleGrade: (String) -> Unit,
    onToggleCategory: (String) -> Unit,
    onToggleGenEdArea: (String) -> Unit,
    onToggleCredit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1줄: 드롭다운형 필터(전공/요일/학년/이수구분) — 화면 폭에 다 안 들어가면 가로로 스크롤한다.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (filters.department != null) Primary else Background)
                    .clickable(onClick = onDepartmentClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = filters.department?.name ?: "전공",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (filters.department != null) OnPrimary else TextPrimary
                )
            }
            MultiSelectDropdown(
                label = "요일",
                options = WEEKDAY_FILTER_OPTIONS,
                optionLabel = { it.label.toString() },
                selected = filters.regions.getOrNull(0)?.days ?: emptySet(),
                onToggle = onToggleDay
            )
            MultiSelectDropdown(
                label = "학년",
                options = FixedCourseGrades,
                optionLabel = { it },
                selected = filters.grades,
                onToggle = onToggleGrade
            )
            MultiSelectDropdown(
                label = "학점",
                options = FixedCourseCredits,
                optionLabel = { it },
                selected = filters.credits,
                onToggle = onToggleCredit
            )
            MultiSelectDropdown(
                label = "이수구분",
                options = FixedCourseCategories,
                optionLabel = { it },
                selected = filters.categories,
                onToggle = onToggleCategory
            )
            MultiSelectDropdown(
                label = "영역",
                options = GenEdAreas.ALL.map { it.label },
                optionLabel = { it },
                selected = filters.genEdAreas,
                onToggle = onToggleGenEdArea,
                // 이수구분에 "교선"을 고른 상태에서만 의미가 있는 필터라 그때만 활성화한다.
                enabled = "교선" in filters.categories,
                maxVisibleItems = 4
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        // 2줄: 시간
        TimeRangeControl(range = filters.regions.getOrNull(0)?.timeRange, onChange = onTimeRangeChange)
    }
}

@Composable
private fun <T> MultiSelectDropdown(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    enabled: Boolean = true,
    // null이면 기존처럼 제한 없음. 지정하면 그만큼만 보이고 나머지는 메뉴 내부 스크롤로 본다
    // (DropdownMenu 내부 Column은 기본적으로 스크롤 가능해서 높이만 제한하면 된다).
    maxVisibleItems: Int? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (!enabled) Background else if (selected.isNotEmpty()) Primary else Background)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selected.isEmpty()) label else "$label ${selected.size}",
                style = MaterialTheme.typography.bodySmall,
                color = if (!enabled) TextDisabled else if (selected.isNotEmpty()) OnPrimary else TextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconChevronDown(tint = if (!enabled) TextDisabled else if (selected.isNotEmpty()) OnPrimary else TextSecondary, size = 12.dp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = if (maxVisibleItems != null) Modifier.heightIn(max = DROPDOWN_ITEM_HEIGHT * maxVisibleItems) else Modifier
        ) {
            options.forEach { option ->
                val isSelected = option in selected
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) Primary else Background)
                                    .border(1.dp, if (isSelected) Primary else Border, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(optionLabel(option), color = TextPrimary)
                        }
                    },
                    // 메뉴를 닫지 않고 토글만 — 여러 개를 연속으로 고를 수 있어야 함
                    onClick = { onToggle(option) }
                )
            }
        }
    }
}

@Composable
private fun TimeRangeControl(range: TimeRangeFilter?, onChange: (TimeRangeFilter?) -> Unit) {
    val start = range?.startMinutes ?: TIME_MIN
    val end = range?.endMinutes ?: TIME_MAX
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("시간", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(52.dp))
        RangeSlider(
            value = start.toFloat()..end.toFloat(),
            onValueChange = { newRange ->
                val newStart = (newRange.start / TIME_STEP).toInt() * TIME_STEP
                val newEnd = (newRange.endInclusive / TIME_STEP).toInt() * TIME_STEP
                if (newStart <= TIME_MIN && newEnd >= TIME_MAX) {
                    onChange(null)
                } else {
                    onChange(TimeRangeFilter(newStart, newEnd))
                }
            },
            valueRange = TIME_MIN.toFloat()..TIME_MAX.toFloat(),
            steps = (TIME_MAX - TIME_MIN) / TIME_STEP - 1,
            modifier = Modifier.weight(1f).height(24.dp),
            // steps가 많아(30분 단위) 눈금 점이 촘촘히 찍히면 지저분해 보여서 눈금 표시만 끈다
            // (값을 정해진 간격으로 스냅하는 동작 자체는 steps로 그대로 유지됨).
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary,
                inactiveTrackColor = Border,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${formatMinutes(start)}~${formatMinutes(end)}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/** Search + list picker for the 전공(department) filter. "내 학과"가 있으면 전체 학과 바로 아래에
 *  고정으로 뜬다 — 학과등수 기능에서 직접 고른 값이 있으면 그걸 쓰고, 없으면 로그인할 때마다 자동으로
 *  갱신되는 학생 프로필의 학과명으로 대신 찾는다(로그인만 해도 항상 채워짐). */
@Composable
fun DepartmentPickerDialog(currentCode: String?, onSelect: (Department?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val myDepartment = remember {
        DepartmentPreferenceStore(context).get()
            ?: StudentProfileStore(context).get()?.department?.let { name ->
                Departments.ALL.firstOrNull { it.name == name }
            }
    }
    val filtered = remember(query) {
        val base = if (query.isBlank()) Departments.ALL else Departments.ALL.filter { it.name.contains(query) }
        base.filterNot { myDepartment != null && it.code == myDepartment.code }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Text(text = "전공 선택", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("학과명 검색", color = TextPlaceholder, style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { IconSearch(tint = TextSecondary) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Background,
                    unfocusedContainerColor = Background,
                    focusedBorderColor = BorderFocused,
                    unfocusedBorderColor = Border,
                    cursorColor = Primary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedPlaceholderColor = TextPlaceholder,
                    unfocusedPlaceholderColor = TextPlaceholder,
                    disabledTextColor = TextDisabled
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.height(320.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item {
                    DepartmentRow(name = "전체 학과", selected = currentCode == null, onClick = { onSelect(null) })
                }
                if (myDepartment != null) {
                    item {
                        DepartmentRow(
                            name = "${myDepartment.name} (내 학과)",
                            selected = currentCode == myDepartment.code,
                            onClick = { onSelect(myDepartment) }
                        )
                    }
                }
                items(filtered, key = { it.code }) { department ->
                    DepartmentRow(
                        name = department.name,
                        selected = department.code == currentCode,
                        onClick = { onSelect(department) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DepartmentRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Primary else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
