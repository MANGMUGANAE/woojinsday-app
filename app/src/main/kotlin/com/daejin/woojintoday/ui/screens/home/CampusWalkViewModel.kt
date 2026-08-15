package com.daejin.woojintoday.ui.screens.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.daejin.woojintoday.data.campus.CampusBuildingCatalog
import com.daejin.woojintoday.data.campus.CampusRouteEngine
import com.daejin.woojintoday.data.model.CampusBuilding
import com.daejin.woojintoday.data.model.CampusWalkRoute

class CampusWalkViewModel(context: Context) : ViewModel() {
    private val routeEngine = CampusRouteEngine.load(context)

    val buildings: List<CampusBuilding> = CampusBuildingCatalog.ALL.sortedBy { it.name }

    var departure by mutableStateOf<CampusBuilding?>(null)
        private set
    var destination by mutableStateOf<CampusBuilding?>(null)
        private set
    var route by mutableStateOf<CampusWalkRoute?>(null)
        private set

    fun selectDeparture(building: CampusBuilding) {
        departure = building
        recomputeRoute()
    }

    fun selectDestination(building: CampusBuilding) {
        destination = building
        recomputeRoute()
    }

    fun reset() {
        departure = null
        destination = null
        route = null
    }

    private fun recomputeRoute() {
        val from = departure
        val to = destination
        route = when {
            from == null || to == null -> null
            // 출발지와 목적지가 같은 건물이면 그래프 스냅 오차 때문에 0이 아닌 값이 나올 수 있어 바로 0으로 처리한다.
            from.id == to.id -> CampusWalkRoute(path = listOf(from.lat to from.lng), distanceMeters = 0.0, walkMinutes = 0)
            else -> routeEngine.route(from.lat, from.lng, to.lat, to.lng)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CampusWalkViewModel(context) as T
        }
    }
}
