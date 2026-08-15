package com.daejin.woojintoday.ui.screens.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.CredentialStore
import com.daejin.woojintoday.data.DepartmentPreferenceStore
import com.daejin.woojintoday.data.StudentProfileStore
import com.daejin.woojintoday.data.model.Department
import com.daejin.woojintoday.data.model.Departments
import com.daejin.woojintoday.data.network.DepartmentGradesResult
import com.daejin.woojintoday.data.network.MyFutureClient
import com.daejin.woojintoday.data.network.MyGradeResult
import kotlinx.coroutines.launch

private val GRADE_DIGIT_REGEX = Regex("""\d+""")

class RankViewModel(
    private val credentialStore: CredentialStore,
    private val departmentStore: DepartmentPreferenceStore,
    private val studentProfileStore: StudentProfileStore,
    private val client: MyFutureClient = MyFutureClient()
) : ViewModel() {

    var myDepartment by mutableStateOf<Department?>(null)
        private set
    var myGrade by mutableStateOf<Double?>(null)
        private set
    var departmentGrades by mutableStateOf<List<Double>>(emptyList())
        private set
    var totalCount by mutableStateOf(0)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingDepartment by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var loggedIn = false

    val gradeLabel: String? get() = studentProfileStore.get()?.grade?.trim()

    val average: Double? get() = departmentGrades.takeIf { it.isNotEmpty() }?.average()
    val rank: Int?
        get() {
            val mine = myGrade ?: return null
            if (departmentGrades.isEmpty()) return null
            return departmentGrades.count { it > mine } + 1
        }

    init {
        val saved = departmentStore.get()
        if (saved != null) {
            myDepartment = saved
            loadAll(saved)
        } else {
            val profileDeptName = studentProfileStore.get()?.department?.trim()
            val matched = profileDeptName?.let { name -> Departments.ALL.find { it.name == name } }
            if (matched != null) {
                departmentStore.save(matched)
                myDepartment = matched
                loadAll(matched)
            }
        }
    }

    fun selectDepartment(department: Department) {
        departmentStore.save(department)
        myDepartment = department
        loadAll(department)
    }

    private fun loadAll(department: Department) {
        val studentNo = credentialStore.studentNo()
        val password = credentialStore.password()
        if (studentNo == null || password == null) {
            errorMessage = "저장된 로그인 정보가 없습니다."
            return
        }
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            if (!loggedIn) {
                loggedIn = client.login(studentNo, password)
                if (!loggedIn) {
                    errorMessage = "성적 시스템 로그인에 실패했습니다."
                    isLoading = false
                    return@launch
                }
            }

            val shyrCd = studentProfileStore.get()?.grade?.let { GRADE_DIGIT_REGEX.find(it)?.value }

            when (val gradeResult = if (shyrCd != null) {
                client.fetchMyGrade(department.code, shyrCd)
            } else {
                client.fetchMyGrade(department.code)
            }) {
                is MyGradeResult.Success -> myGrade = gradeResult.gpa
                is MyGradeResult.NetworkError -> errorMessage = gradeResult.message
            }
            fetchDepartment(department, shyrCd)
            isLoading = false
        }
    }

    private suspend fun fetchDepartment(department: Department, shyrCd: String?) {
        isLoadingDepartment = true
        val result = if (shyrCd != null) {
            client.fetchDepartmentGrades(department.code, shyrCd)
        } else {
            client.fetchDepartmentGrades(department.code)
        }
        when (result) {
            is DepartmentGradesResult.Success -> {
                departmentGrades = result.grades
                totalCount = result.totalCount
            }
            is DepartmentGradesResult.NetworkError -> {
                if (errorMessage == null) errorMessage = result.message
            }
        }
        isLoadingDepartment = false
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RankViewModel(
                credentialStore = CredentialStore(context),
                departmentStore = DepartmentPreferenceStore(context),
                studentProfileStore = StudentProfileStore(context)
            ) as T
        }
    }
}
