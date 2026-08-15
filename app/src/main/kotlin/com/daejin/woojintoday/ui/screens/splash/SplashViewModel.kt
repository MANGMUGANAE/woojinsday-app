package com.daejin.woojintoday.ui.screens.splash

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SplashDestination { LOGIN, TIMETABLE }

private const val MIN_SPLASH_MS = 900L

class SplashViewModel(
    private val credentialStore: CredentialStore,
    private val sessionStore: SessionStore,
    private val studentProfileStore: StudentProfileStore,
    private val authClient: DaejinAuthClient = DaejinAuthClient(),
    private val profileClient: StudentProfileClient = StudentProfileClient()
) : ViewModel() {

    var destination by mutableStateOf<SplashDestination?>(null)
        private set
    private var started = false

    /** Only call once required permissions are resolved — see [com.daejin.woojintoday.ui.screens.splash.SplashScreen]. */
    fun begin() {
        if (started) return
        started = true
        viewModelScope.launch {
            delay(MIN_SPLASH_MS)

            val studentNo = credentialStore.studentNo()
            val password = credentialStore.password()
            destination = if (studentNo != null && password != null) {
                when (val result = authClient.login(studentNo, password)) {
                    is LoginResult.Success -> {
                        sessionStore.save(result.session.wmonid, result.session.jsessionId)
                        credentialStore.save(studentNo, password, result.session.userId2)
                        val userId2 = result.session.userId2
                        if (userId2 != null) {
                            when (val profileResult = profileClient.fetchProfile(userId2, password)) {
                                is StudentProfileResult.Success -> studentProfileStore.save(profileResult.profile)
                                is StudentProfileResult.NetworkError -> Unit
                            }
                        }
                        SplashDestination.TIMETABLE
                    }
                    else -> SplashDestination.LOGIN
                }
            } else {
                SplashDestination.LOGIN
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SplashViewModel(
                credentialStore = CredentialStore(context),
                sessionStore = SessionStore(context),
                studentProfileStore = StudentProfileStore(context)
            ) as T
        }
    }
}
