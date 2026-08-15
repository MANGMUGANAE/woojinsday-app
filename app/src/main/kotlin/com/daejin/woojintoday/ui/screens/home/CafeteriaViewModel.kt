package com.daejin.woojintoday.ui.screens.home

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daejin.woojintoday.data.network.CafeteriaClient
import com.daejin.woojintoday.data.network.CafeteriaDay
import com.daejin.woojintoday.data.network.CafeteriaWeekResult
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** Caches cafeteria data per week (keyed by that week's Monday) so swiping within a loaded week is instant. */
class CafeteriaViewModel(
    private val client: CafeteriaClient = CafeteriaClient()
) : ViewModel() {

    private val weekCache: SnapshotStateMap<LocalDate, List<CafeteriaDay>> = mutableStateMapOf()
    private val loadingWeeks: SnapshotStateMap<LocalDate, Boolean> = mutableStateMapOf()
    private val errorWeeks: SnapshotStateMap<LocalDate, String> = mutableStateMapOf()

    fun mondayOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun dayFor(date: LocalDate): CafeteriaDay? =
        weekCache[mondayOf(date)]?.getOrNull(date.dayOfWeek.value - 1)

    fun isLoading(date: LocalDate): Boolean = loadingWeeks[mondayOf(date)] == true

    fun errorFor(date: LocalDate): String? = errorWeeks[mondayOf(date)]

    fun ensureLoaded(date: LocalDate) {
        val monday = mondayOf(date)
        if (weekCache.containsKey(monday) || loadingWeeks[monday] == true) return
        loadingWeeks[monday] = true
        errorWeeks.remove(monday)
        viewModelScope.launch {
            when (val result = client.fetchWeek(monday)) {
                is CafeteriaWeekResult.Success -> weekCache[monday] = result.days
                is CafeteriaWeekResult.NetworkError -> errorWeeks[monday] = result.message
            }
            loadingWeeks.remove(monday)
        }
    }
}
