package com.daejin.woojintoday.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.data.model.Department
import com.daejin.woojintoday.data.model.Departments
import com.daejin.woojintoday.ui.components.ResponsiveContainer
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.icons.IconSearch
import com.daejin.woojintoday.ui.theme.AccentBlue
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
import kotlin.math.abs

private const val GPA_SCALE = 4.5

@Composable
fun RankDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val viewModel: RankViewModel = viewModel(factory = RankViewModel.Factory(context))
    var isChangingDepartment by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Background)) {
        ResponsiveContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    IconArrowBack(tint = TextPrimary)
                }
            }

            val myDepartment = viewModel.myDepartment
            when {
                myDepartment == null || isChangingDepartment -> {
                    DepartmentSearchScreen(
                        title = if (myDepartment == null) "학과를 알려주세요" else "학과 변경",
                        currentCode = myDepartment?.code,
                        onSelect = { department ->
                            viewModel.selectDepartment(department)
                            isChangingDepartment = false
                        }
                    )
                }
                viewModel.errorMessage != null && viewModel.myGrade == null && !viewModel.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        Text(
                            text = viewModel.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                else -> {
                    RankContent(
                        viewModel = viewModel,
                        department = myDepartment,
                        onRequestChangeDepartment = { isChangingDepartment = true }
                    )
                }
            }
        }
        }
        }
    }
}

@Composable
private fun DepartmentSearchScreen(title: String, currentCode: String?, onSelect: (Department) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) Departments.ALL else Departments.ALL.filter { it.name.contains(query) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("학과 검색", color = TextPlaceholder, style = MaterialTheme.typography.bodySmall)
                },
                leadingIcon = { IconSearch(tint = TextSecondary) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    disabledContainerColor = Surface,
                    focusedBorderColor = BorderFocused,
                    unfocusedBorderColor = Border,
                    disabledBorderColor = Border,
                    cursorColor = Primary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedPlaceholderColor = TextPlaceholder,
                    unfocusedPlaceholderColor = TextPlaceholder,
                    disabledTextColor = TextDisabled
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(filtered) { department ->
            DepartmentResultRow(
                department = department,
                selected = department.code == currentCode,
                onClick = { onSelect(department) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = "검색 결과가 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun RankContent(
    viewModel: RankViewModel,
    department: Department,
    onRequestChangeDepartment: () -> Unit
) {
    val myGrade = viewModel.myGrade
    val sortedGrades = remember(viewModel.departmentGrades) { viewModel.departmentGrades.sortedDescending() }
    val skeletonAlpha = rememberSkeletonAlpha()
    val gradesLoading = viewModel.isLoading || viewModel.isLoadingDepartment

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(text = "나는 학과에서 몇등일까요?", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = department.name, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Surface)
                        .clickable(onClick = onRequestChangeDepartment)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "학과 변경", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "내 성적",
                    value = myGrade?.let { "%.2f".format(it) },
                    isLoading = viewModel.isLoading,
                    skeletonAlpha = skeletonAlpha
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "${department.name} 평균",
                    value = viewModel.average?.let { "%.2f".format(it) },
                    isLoading = gradesLoading,
                    skeletonAlpha = skeletonAlpha
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            StatCard(
                modifier = Modifier.fillMaxWidth(),
                label = "",
                value = if (viewModel.rank != null && viewModel.totalCount > 0) {
                    "${viewModel.totalCount}명 중 ${viewModel.rank}등"
                } else {
                    null
                },
                isLoading = gradesLoading,
                skeletonAlpha = skeletonAlpha,
                highlight = true
            )

            Spacer(modifier = Modifier.height(28.dp))
            val gradeLabel = viewModel.gradeLabel
            Text(
                text = if (gradeLabel != null) "${department.name}(${gradeLabel}) 성적 분포" else "${department.name} 성적 분포",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (gradesLoading && sortedGrades.isEmpty()) {
            items(8) {
                SkeletonGradeBarRow(rank = it + 1, alpha = skeletonAlpha)
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            itemsIndexed(sortedGrades) { index, grade ->
                val isMine = myGrade != null && abs(grade - myGrade) < 0.001
                GradeBarRow(rank = index + 1, grade = grade, highlighted = isMine)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (sortedGrades.isEmpty()) {
                item {
                    Text(
                        text = "학과 성적 분포 정보가 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        item {
            // 성적 분포 리스트 맨 아래가 화면 끝에 딱 붙지 않도록, 요소 2개 정도 폭의 여백을 더 준다.
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
private fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "rankSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rankSkeletonAlpha"
    )
    return alpha
}

@Composable
private fun SkeletonGradeBarRow(rank: Int, alpha: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${rank}위",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(34.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Border.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Border.copy(alpha = alpha))
        )
    }
}

@Composable
private fun DepartmentResultRow(department: Department, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Primary else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = department.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) OnPrimary else TextPrimary
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String?,
    isLoading: Boolean = false,
    skeletonAlpha: Float = 0.5f,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlight) Primary else Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) Color.White.copy(alpha = 0.85f) else TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (value == null && isLoading) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background((if (highlight) Color.White else Border).copy(alpha = skeletonAlpha))
            )
        } else {
            Text(
                text = value ?: "-",
                style = MaterialTheme.typography.titleLarge,
                color = if (highlight) Color.White else TextPrimary
            )
        }
    }
}

@Composable
private fun GradeBarRow(rank: Int, grade: Double, highlighted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${rank}위",
            style = MaterialTheme.typography.bodySmall,
            color = if (highlighted) Primary else TextSecondary,
            modifier = Modifier.width(34.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface)
        ) {
            val fraction = (grade / GPA_SCALE).coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (highlighted) Primary else AccentBlue)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "%.2f".format(grade),
            style = MaterialTheme.typography.bodySmall,
            color = if (highlighted) Primary else TextPrimary,
            modifier = Modifier.width(40.dp)
        )
    }
}
