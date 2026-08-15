package com.daejin.woojintoday.ui.screens.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.daejin.woojintoday.ui.components.ToastHost
import com.daejin.woojintoday.ui.components.rememberToastState
import com.daejin.woojintoday.ui.icons.AppMark
import com.daejin.woojintoday.ui.icons.IconAlertCircle
import com.daejin.woojintoday.ui.icons.IconChevronDown
import com.daejin.woojintoday.ui.icons.IconEye
import com.daejin.woojintoday.ui.icons.IconEyeOff
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.Factory(context)
    )
    var showFindIdDialog by remember { mutableStateOf(false) }
    var showAccountInfo by remember { mutableStateOf(false) }
    val toastState = rememberToastState()
    val passwordFocusRequester = remember { FocusRequester() }
    val imeVisible = WindowInsets.isImeVisible

    LaunchedEffect(viewModel.profileFetchSucceeded) {
        when (viewModel.profileFetchSucceeded) {
            true -> toastState.show("학생 정보를 불러왔어요")
            false -> toastState.show("학생 정보를 불러오지 못했어요")
            null -> Unit
        }
    }

    LaunchedEffect(viewModel.loginSucceeded) {
        if (viewModel.loginSucceeded) {
            delay(900)
            onLoginSuccess()
        }
    }

    if (showFindIdDialog) {
        FindStudentIdDialog(
            onDismiss = { showFindIdDialog = false },
            onUseStudentNo = { studentNo -> viewModel.onStudentNoChange(studentNo) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        if (viewModel.loginSucceeded) {
            LoginSuccessContent()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 24.dp)
            ) {
                AnimatedVisibility(
                    visible = !imeVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(64.dp))
                        AppMark(height = 180.dp)
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
                Text(
                    text = "우진이의 하루!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(40.dp))

                LoginTextField(
                    value = viewModel.studentNo,
                    onValueChange = { newValue ->
                        viewModel.onStudentNoChange(newValue)
                        // 학번은 8자리(예: 20211476)라 다 입력되면 비밀번호 칸으로 자동으로 넘어간다.
                        if (newValue.length == 8) passwordFocusRequester.requestFocus()
                    },
                    placeholder = "학번",
                    keyboardType = KeyboardType.Number
                )

                Spacer(modifier = Modifier.height(12.dp))

                LoginTextField(
                    value = viewModel.password,
                    onValueChange = viewModel::onPasswordChange,
                    placeholder = "비밀번호",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    isPasswordVisible = viewModel.isPasswordVisible,
                    onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    onSubmit = viewModel::submit,
                    focusRequester = passwordFocusRequester
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "학번을 찾아드릴까요?",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.clickable { showFindIdDialog = true }
                    )
                }

                if (viewModel.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconAlertCircle(tint = ErrorRed, size = 16.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = viewModel.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = viewModel::submit,
                    enabled = !viewModel.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary,
                        disabledContainerColor = Primary,
                        disabledContentColor = OnPrimary
                    )
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = OnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(text = "시작해볼까요?", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Border)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAccountInfo = !showAccountInfo },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "개인정보는 어떻게 관리되나요?",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    IconChevronDown(
                        tint = TextSecondary,
                        size = 14.dp,
                        modifier = Modifier.rotate(if (showAccountInfo) 180f else 0f)
                    )
                }
                if (showAccountInfo) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "[우진이의 하루]는 대진대학교 서버와 직접 통신합니다. 별도의 서버가 존재하지 않아 민감정보(전화번호, 이름, 생년월일 학번, 비밀번호 등)등은 모두 휴대폰에 저장되고 활용되며 앱 삭제시 전부 폐기 처리됩니다. 따라서 앱을 삭제하거나 휴대폰을 변경하는 등의 경우 이전 앱의 정보는 공유되지 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        ToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun LoginSuccessContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppMark(height = 88.dp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "로그인되었습니다",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next,
    onSubmit: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (focusRequester != null) base.focusRequester(focusRequester) else base },
        placeholder = {
            Text(text = placeholder, color = TextPlaceholder, style = MaterialTheme.typography.bodyLarge)
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
        trailingIcon = if (isPassword && value.isNotEmpty()) {
            {
                IconButton(onClick = { onTogglePasswordVisibility?.invoke() }) {
                    if (isPasswordVisible) {
                        IconEyeOff(tint = TextSecondary)
                    } else {
                        IconEye(tint = TextSecondary)
                    }
                }
            }
        } else null,
        visualTransformation = if (isPassword && !isPasswordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onSubmit?.invoke() }
        ),
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
