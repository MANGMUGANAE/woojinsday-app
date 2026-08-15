package com.daejin.woojintoday.data.campus

import android.content.Context
import com.daejin.woojintoday.data.model.CampusWalkRoute
import org.json.JSONObject
import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val ASSET_NAME = "campus_paths.geojson"
private const val EARTH_RADIUS_M = 6371000.0

/** 평균 도보 속도(m/s) — 지도 앱들이 흔히 쓰는 값(약 72m/분)에 맞췄다. 도보시간 = 거리 / 이 속도. */
private const val WALK_SPEED_MPS = 1.2

/** highway=steps 구간은 실제 거리보다 시간이 더 걸리므로, 시간 계산·최단경로 탐색 둘 다에서
 *  거리를 이 배수만큼 부풀린 "가중 거리"로 취급한다(실제 표시 거리는 그대로 둔다). */
private const val STEPS_TIME_MULTIPLIER = 2.0

internal fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dPhi = Math.toRadians(lat2 - lat1)
    val dLambda = Math.toRadians(lng2 - lng1)
    val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
    return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** 소수 7자리로 반올림한 "lat,lng" 문자열을 그래프 노드 키로 쓴다 — OSM은 같은 지점을 공유하는
 *  길들이 정확히 같은 좌표를 참조하므로, 반올림 정도만 맞추면 별도 스냅 로직 없이 자연히 이어진다. */
private fun nodeKey(lat: Double, lng: Double): String {
    val rl = Math.round(lat * 1e7) / 1e7
    val rg = Math.round(lng * 1e7) / 1e7
    return "$rl,$rg"
}

private data class Edge(val toKey: String, val meters: Double, val timeWeight: Double)

/**
 * export.geojson(overpass-turbo로 뽑은 대진대 캠퍼스 보행로)을 한 번 파싱해서 보행로 그래프를 만들어
 * 두고, 두 좌표 사이의 최단 도보 경로/시간을 계산해준다. 건물 좌표/이름은 [CampusBuildingCatalog]가
 * 따로 들고 있고, 여기는 순수하게 "좌표 → 좌표" 라우팅만 담당한다.
 */
class CampusRouteEngine private constructor(
    private val adjacency: Map<String, List<Edge>>,
    private val nodeCoords: Map<String, Pair<Double, Double>>
) {
    /** ([fromLat],[fromLng])에서 ([toLat],[toLng])까지 최단 도보 경로. 그래프가 끊겨 있어 경로가
     *  없으면 null. */
    fun route(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): CampusWalkRoute? {
        if (nodeCoords.isEmpty()) return null

        // 좌표에서 가장 가까운 보행로 노드로 "스냅"한 뒤 다익스트라를 돌린다.
        val (fromNodeKey, fromSnapMeters) = nearestNode(fromLat, fromLng)
        val (toNodeKey, toSnapMeters) = nearestNode(toLat, toLng)

        val (nodePath, networkMeters, networkTimeWeight) = shortestPath(fromNodeKey, toNodeKey) ?: return null

        val fullPath = buildList {
            add(fromLat to fromLng)
            nodePath.forEach { key -> nodeCoords[key]?.let { add(it) } }
            add(toLat to toLng)
        }
        // 표시용 거리는 실제 물리적 거리, 소요 시간은 계단 구간 가중치가 반영된 거리로 따로 계산한다.
        val totalMeters = fromSnapMeters + networkMeters + toSnapMeters
        val totalTimeWeight = fromSnapMeters + networkTimeWeight + toSnapMeters
        val walkMinutes = ceil((totalTimeWeight / WALK_SPEED_MPS) / 60.0).toInt().coerceAtLeast(1)
        return CampusWalkRoute(path = fullPath, distanceMeters = totalMeters, walkMinutes = walkMinutes)
    }

    private fun nearestNode(lat: Double, lng: Double): Pair<String, Double> {
        var bestKey = ""
        var bestDist = Double.MAX_VALUE
        nodeCoords.forEach { (key, coord) ->
            val d = haversineMeters(lat, lng, coord.first, coord.second)
            if (d < bestDist) {
                bestDist = d
                bestKey = key
            }
        }
        return bestKey to bestDist
    }

    /** 다익스트라 최단 경로 — 캠퍼스 규모(노드 수백 개)라 우선순위 큐만으로 충분하다. 계단 구간이
     *  더 오래 걸리는 걸 최단경로 탐색 자체에도 반영하려고, "가중 거리(timeWeight)" 기준으로
     *  경로를 찾고 실제 물리적 거리(meters)는 같은 간선을 타고 갈 때마다 같이 누적해서 돌려준다. */
    private fun shortestPath(startKey: String, endKey: String): Triple<List<String>, Double, Double>? {
        if (startKey == endKey) return Triple(listOf(startKey), 0.0, 0.0)
        val distWeight = mutableMapOf(startKey to 0.0)
        val distMeters = mutableMapOf(startKey to 0.0)
        val prev = mutableMapOf<String, String>()
        val visited = mutableSetOf<String>()
        val queue = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        queue.add(startKey to 0.0)

        while (queue.isNotEmpty()) {
            val (current, currentWeight) = queue.poll() ?: break
            if (current in visited) continue
            visited += current
            if (current == endKey) break

            adjacency[current]?.forEach { edge ->
                val newWeight = currentWeight + edge.timeWeight
                if (newWeight < (distWeight[edge.toKey] ?: Double.MAX_VALUE)) {
                    distWeight[edge.toKey] = newWeight
                    distMeters[edge.toKey] = (distMeters[current] ?: 0.0) + edge.meters
                    prev[edge.toKey] = current
                    queue.add(edge.toKey to newWeight)
                }
            }
        }

        if (distWeight[endKey] == null) return null
        val path = mutableListOf(endKey)
        var cursor = endKey
        while (cursor != startKey) {
            cursor = prev[cursor] ?: return null
            path.add(cursor)
        }
        path.reverse()
        return Triple(path, distMeters[endKey] ?: return null, distWeight[endKey] ?: return null)
    }

    companion object {
        private var cached: CampusRouteEngine? = null

        fun load(context: Context): CampusRouteEngine {
            cached?.let { return it }
            val json = context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val engine = parse(json)
            cached = engine
            return engine
        }

        private fun parse(json: String): CampusRouteEngine {
            val root = JSONObject(json)
            val features = root.getJSONArray("features")

            val adjacency = mutableMapOf<String, MutableList<Edge>>()
            val nodeCoords = mutableMapOf<String, Pair<Double, Double>>()

            fun addNode(lat: Double, lng: Double): String {
                val key = nodeKey(lat, lng)
                nodeCoords.putIfAbsent(key, lat to lng)
                return key
            }

            fun addEdge(aLat: Double, aLng: Double, bLat: Double, bLng: Double, isSteps: Boolean) {
                val aKey = addNode(aLat, aLng)
                val bKey = addNode(bLat, bLng)
                if (aKey == bKey) return
                val meters = haversineMeters(aLat, aLng, bLat, bLng)
                val timeWeight = if (isSteps) meters * STEPS_TIME_MULTIPLIER else meters
                adjacency.getOrPut(aKey) { mutableListOf() }.add(Edge(bKey, meters, timeWeight))
                adjacency.getOrPut(bKey) { mutableListOf() }.add(Edge(aKey, meters, timeWeight))
            }

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val properties = feature.optJSONObject("properties") ?: JSONObject()
                val geometry = feature.getJSONObject("geometry")
                val geometryType = geometry.getString("type")
                if (properties.has("highway") && geometryType == "LineString") {
                    val isSteps = properties.optString("highway") == "steps"
                    val coordinates = geometry.getJSONArray("coordinates")
                    var prevLat: Double? = null
                    var prevLng: Double? = null
                    for (p in 0 until coordinates.length()) {
                        val point = coordinates.getJSONArray(p)
                        val lng = point.getDouble(0)
                        val lat = point.getDouble(1)
                        if (prevLat != null && prevLng != null) {
                            addEdge(prevLat, prevLng, lat, lng, isSteps)
                        }
                        prevLat = lat
                        prevLng = lng
                    }
                }
            }

            return CampusRouteEngine(adjacency, nodeCoords)
        }
    }
}
