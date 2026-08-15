package com.daejin.woojintoday.data.model

/** export.geojson의 building 폴리곤 하나(중심 좌표까지 계산해둔 것). [name]은 지오json엔 없어서
 *  카카오맵 좌표 검색으로 나중에 채워진다(비어있으면 아직 매칭 전). */
data class CampusBuilding(
    val id: String,
    val lat: Double,
    val lng: Double,
    val name: String? = null
)

/** 출발지→목적지 간 도보 경로 계산 결과. [path]는 지도에 폴리라인으로 그릴 좌표 목록(출발지 건물
 *  중심 → 보행로 스냅 지점들 → 목적지 건물 중심). */
data class CampusWalkRoute(
    val path: List<Pair<Double, Double>>,
    val distanceMeters: Double,
    val walkMinutes: Int
)
