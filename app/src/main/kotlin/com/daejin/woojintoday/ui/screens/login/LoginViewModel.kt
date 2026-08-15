package com.daejin.woojintoday.ui.screens.login

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.CredentialStore
import com.daejin.woojintoday.data.SessionStore
import com.daejin.woojintoday.data.StudentProfileStore
import com.daejin.woojintoday.data.network.DaejinAuthClient
import com.daejin.woojintoday.data.network.LoginResult
import com.daejin.woojintoday.data.network.StudentProfileClient
import com.daejin.woojintoday.data.network.StudentProfileResult
import kotlinx.coroutines.launch

class LoginViewModel(
    private val sessionStore: SessionStore,
    private val credentialStore: CredentialStore,
    private val studentProfileStore: StudentProfileStore,
    private val authClient: DaejinAuthClient = DaejinAuthClient(),
    private val profileClient: StudentProfileClient = StudentProfileClient()
) : ViewModel() {

    var studentNo by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isPasswordVisible by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var loginSucceeded by mutableStateOf(false)
        private set
    var profileFetchSucceeded by mutableStateOf<Boolean?>(null)
        private set

    fun onStudentNoChange(value: String) {
        studentNo = value.filter { it.isDigit() }
        errorMessage = null
    }

    fun onPasswordChange(value: String) {
        password = value
        errorMessage = null
    }

    fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
    }

    fun submit() {
        if (isLoading) return
        if (studentNo.isBlank() || password.isBlank()) {
            errorMessage = "학번과 비밀번호를 입력해주세요."
            return
        }
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            when (val result = authClient.login(studentNo, password)) {
                is LoginResult.Success -> {
                    sessionStore.save(result.session.wmonid, result.session.jsessionId)
                    credentialStore.save(studentNo, password, result.session.userId2)
                    val userId2 = result.session.userId2
                    profileFetchSucceeded = if (userId2 != null) {
                        when (val profileResult = profileClient.fetchProfile(userId2, password)) {
                            is StudentProfileResult.Success -> {
                                studentProfileStore.save(profileResult.profile)
                                true
                            }
                            is StudentProfileResult.NetworkError -> false
                        }
                    } else {
                        false
                    }
                    loginSucceeded = true
                }
                is LoginResult.Failed -> {
                    errorMessage = result.message
                }
                is LoginResult.NetworkError -> {
                    errorMessage = result.message
                }
            }
            isLoading = false
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(
                sessionStore = SessionStore(context),
                credentialStore = CredentialStore(context),
                studentProfileStore = StudentProfileStore(context)
            ) as T
        }
    }
}
