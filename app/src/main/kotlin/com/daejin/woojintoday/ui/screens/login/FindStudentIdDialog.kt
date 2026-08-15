package com.daejin.woojintoday.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daejin.woojintoday.data.network.FindStudentIdResult
import com.daejin.woojintoday.data.network.StudentIdFinderClient
import com.daejin.woojintoday.ui.components.WheelPicker
import com.daejin.woojintoday.ui.components.wheelRange
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.BorderFocused
import com.daejin.woojintoday.ui.theme.ErrorRed
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextDisabled
import com.daejin.woojintoday.ui.theme.TextPlaceholder
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class FindIdStep { INPUT, RESULT }

@Composable
fun FindStudentIdDialog(onDismiss: () -> Unit, onUseStudentNo: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val mobile2FocusRequester = remember { FocusRequester() }
    val mobile3FocusRequester = remember { FocusRequester() }
    val client = remember { StudentIdFinderClient() }

    var step by remember { mutableStateOf(FindIdStep.INPUT) }
    var name by remember { mutableStateOf("") }
    val currentYear = remember { LocalDate.now().year }
    var birthYear by remember { mutableIntStateOf(currentYear - 20) }
    var birthMonth by remember { mutableIntStateOf(1) }
    var birthDay by remember { mutableIntStateOf(1) }
    var mobile1 by remember { mutableStateOf("010") }
    var mobile2 by remember { mutableStateOf("") }
    var mobile3 by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var foundStudentNo by remember { mutableStateOf<String?>(null) }

    val years = remember { wheelRange(currentYear - 45, currentYear - 15).reversed() }
    val months = remember { wheelRange(1, 12) }
    val days = remember { wheelRange(1, 31) }

    val canSubmit = name.isNotBlank() && mobile1.length == 3 && mobile2.length == 4 && mobile3.length == 4

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (step == FindIdStep.RESULT) {
                            step = FindIdStep.INPUT
                            errorMessage = null
                        } else {
                            onDismiss()
                        }
                    }
                ) {
                    IconArrowBack(tint = TextPrimary)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                when (step) {
                    FindIdStep.INPUT -> {
                        Text(text = "학번을 찾아드릴게요!", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Spacer(modifier = Modifier.height(24.dp))

                        FindIdTextField(value = name, onValueChange = { name = it }, placeholder = "이름")

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(text = "휴대폰 번호", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FindIdTextField(
                                value = mobile1,
                                onValueChange = {
                                    if (it.length <= 3) {
                                        mobile1 = it.filter { c -> c.isDigit() }
                                        if (mobile1.length == 3) mobile2FocusRequester.requestFocus()
                                    }
                                },
                                placeholder = "010",
                                modifier = Modifier.weight(0.8f)
                            )
                            Text("-", color = TextSecondary)
                            FindIdTextField(
                                value = mobile2,
                                onValueChange = {
                                    if (it.length <= 4) {
                                        mobile2 = it.filter { c -> c.isDigit() }
                                        if (mobile2.length == 4) mobile3FocusRequester.requestFocus()
                                    }
                                },
                                placeholder = "0000",
                                modifier = Modifier.weight(1f).focusRequester(mobile2FocusRequester)
                            )
                            Text("-", color = TextSecondary)
                            FindIdTextField(
                                value = mobile3,
                                onValueChange = {
                                    if (it.length <= 4) {
                                        mobile3 = it.filter { c -> c.isDigit() }
                                        if (mobile3.length == 4) focusManager.clearFocus()
                                    }
                                },
                                placeholder = "0000",
                                modifier = Modifier.weight(1f).focusRequester(mobile3FocusRequester)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(text = "생년월일", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WheelPicker(
                                values = years,
                                selected = birthYear,
                                onSelectedChange = { birthYear = it },
                                modifier = Modifier.weight(1.2f),
                                format = { "${it}년" }
                            )
                            WheelPicker(
                                values = months,
                                selected = birthMonth,
                                onSelectedChange = { birthMonth = it },
                                modifier = Modifier.weight(1f),
                                format = { "${it}월" }
                            )
                            WheelPicker(
                                values = days,
                                selected = birthDay,
                                onSelectedChange = { birthDay = it },
                                modifier = Modifier.weight(1f),
                                format = { "${it}일" }
                            )
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = errorMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val result = client.findStudentId(
                                        name = name,
                                        birthYearYY = "%02d".format(birthYear % 100),
                                        birthMonth = "%02d".format(birthMonth),
                                        birthDay = "%02d".format(birthDay),
                                        mobile1 = mobile1,
                                        mobile2 = mobile2,
                                        mobile3 = mobile3
                                    )
                                    isLoading = false
                                    when (result) {
                                        is FindStudentIdResult.Found -> {
                                            foundStudentNo = result.studentNo
                                            step = FindIdStep.RESULT
                                        }
                                        is FindStudentIdResult.NotFound -> {
                                            foundStudentNo = null
                                            step = FindIdStep.RESULT
                                        }
                                        is FindStudentIdResult.NetworkError -> {
                                            errorMessage = result.message
                                        }
                                    }
                                }
                            },
                            enabled = canSubmit && !isLoading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor = OnPrimary,
                                disabledContainerColor = Primary,
                                disabledContentColor = OnPrimary
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp).width(20.dp),
                                    color = OnPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(text = "학번 찾기", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    FindIdStep.RESULT -> {
                        Spacer(modifier = Modifier.height(40.dp))
                        val studentNo = foundStudentNo
                        if (studentNo != null) {
                            Text(text = "찾았어요!", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Spacer(modifier = Modifier.height(20.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Surface)
                                    .padding(horizontal = 20.dp, vertical = 24.dp)
                            ) {
                                Text(text = "학번", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = studentNo,
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                                    color = Primary
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    onUseStudentNo(studentNo)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                            ) {
                                Text(text = "이 학번으로 로그인하기", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Text(text = "학번을 찾지 못했어요..", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "입력하신 정보를 다시 확인해주세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = { step = FindIdStep.INPUT },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Text(text = "다시 시도하기", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindIdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(text = placeholder, color = TextPlaceholder, style = MaterialTheme.typography.bodyLarge)
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
}
