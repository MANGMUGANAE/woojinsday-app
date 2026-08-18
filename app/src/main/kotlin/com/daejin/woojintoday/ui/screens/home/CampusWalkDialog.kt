package com.daejin.woojintoday.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.data.model.CampusBuilding
import com.daejin.woojintoday.data.model.CampusWalkRoute
import com.daejin.woojintoday.ui.components.ResponsiveContainer
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.icons.IconClose
import com.daejin.woojintoday.ui.icons.IconSearch
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
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.label.LabelTextStyle
import com.kakao.vectormap.route.RouteLineLayer
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles

private const val TAG = "CampusWalkDialog"

private enum class PickerTarget { DEPARTURE, DESTINATION }

/** 건물간 도보시간 화면 — 상단 출발지/목적지 선택 + 카카오맵 전체화면, 두 건물을 고르면 도보 경로와
 *  소요시간을 계산해 지도 위에 그려준다. */
@Composable
fun CampusWalkDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val viewModel: CampusWalkViewModel = viewModel(factory = CampusWalkViewModel.Factory(context))
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var infoBuilding by remember { mutableStateOf<CampusBuilding?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Background)) {
        ResponsiveContainer {
            CampusMapView(
                buildings = viewModel.buildings,
                departure = viewModel.departure,
                destination = viewModel.destination,
                route = viewModel.route,
                onMarkerClick = { infoBuilding = it },
                onMapBackgroundClick = { infoBuilding = null },
                modifier = Modifier.fillMaxSize()
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onDismiss, modifier = Modifier.padding(4.dp)) {
                    // 카카오맵 배경이 밝아서 앱 기본 흰색 화살표는 안 보인다 — 검정으로 고정.
                    IconArrowBack(tint = Color.Black)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BuildingSelector(
                        label = "출발지",
                        buildingName = viewModel.departure?.name,
                        modifier = Modifier.weight(1f),
                        onClick = { pickerTarget = PickerTarget.DEPARTURE }
                    )
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    BuildingSelector(
                        label = "목적지",
                        buildingName = viewModel.destination?.name,
                        modifier = Modifier.weight(1f),
                        onClick = { pickerTarget = PickerTarget.DESTINATION }
                    )
                    IconButton(onClick = { viewModel.reset() }, modifier = Modifier.size(36.dp)) {
                        IconClose(tint = ErrorRed, size = 16.dp)
                    }
                }
                viewModel.route?.let { route ->
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(50))
                            .background(Primary)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "도보 ${route.walkMinutes}분 · 약 ${route.distanceMeters.toInt()}m",
                            style = MaterialTheme.typography.labelLarge,
                            color = OnPrimary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = infoBuilding != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                // 카드 전체 크기 기준 약 30%만큼 더 위로 띄운다(화면 맨 아래에 딱 붙어 보이지 않도록).
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            ) {
                infoBuilding?.let { building ->
                    BuildingInfoCard(
                        building = building,
                        onDismiss = { infoBuilding = null },
                        onSelectDeparture = {
                            viewModel.selectDeparture(building)
                            infoBuilding = null
                        },
                        onSelectDestination = {
                            viewModel.selectDestination(building)
                            infoBuilding = null
                        }
                    )
                }
            }
        }
        }
    }

    pickerTarget?.let { target ->
        BuildingPickerDialog(
            buildings = viewModel.buildings,
            onSelect = { building ->
                if (target == PickerTarget.DEPARTURE) viewModel.selectDeparture(building) else viewModel.selectDestination(building)
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null }
        )
    }
}

@Composable
private fun BuildingSelector(
    label: String,
    buildingName: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = buildingName ?: "건물 선택",
            style = MaterialTheme.typography.labelLarge,
            color = if (buildingName != null) TextPrimary else TextPlaceholder,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 마커를 누르면 카카오맵처럼 하단에서 올라오는 위치 정보 카드 — 실제 사진/설명 데이터가 아직
 *  없어서 그 부분은 "준비 중" 문구로 남겨둔다. */
@Composable
private fun BuildingInfoCard(
    building: CampusBuilding,
    onDismiss: () -> Unit,
    onSelectDeparture: () -> Unit,
    onSelectDestination: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Background),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "사진 준비 중이에요", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                IconClose(tint = TextSecondary, size = 16.dp)
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = building.name.orEmpty(), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "장소 설명은 준비 중이에요", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onSelectDeparture,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("출발지로 선택", style = MaterialTheme.typography.labelMedium, color = Primary)
                }
                Button(
                    onClick = onSelectDestination,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary
                    )
                ) {
                    Text("도착지로 선택", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun CampusMapView(
    buildings: List<CampusBuilding>,
    departure: CampusBuilding?,
    destination: CampusBuilding?,
    route: CampusWalkRoute?,
    onMarkerClick: (CampusBuilding) -> Unit,
    onMapBackgroundClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    // 이름 라벨(항상 떠 있는 주황색 건물명)과 핀 라벨(출발/목적지)을 레이어를 분리해서, 출발/목적지가
    // 바뀔 때 핀만 지우고 다시 그리지 이름 라벨까지 같이 사라지지 않게 한다.
    var nameLabelLayer by remember { mutableStateOf<LabelLayer?>(null) }
    var pinLabelLayer by remember { mutableStateOf<LabelLayer?>(null) }
    var routeLineLayer by remember { mutableStateOf<RouteLineLayer?>(null) }
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapBackgroundClick by rememberUpdatedState(onMapBackgroundClick)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {}
                        override fun onMapError(error: Exception) {
                            Log.e(TAG, "지도 로드 실패", error)
                        }
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            kakaoMap = map
                            // 기본값이 이미 켜져 있는 경우가 많지만, 손가락으로 자유롭게 확대/축소/이동이
                            // 되도록 명시적으로 켜둔다.
                            map.setGestureEnable(GestureType.Zoom, true)
                            map.setGestureEnable(GestureType.OneFingerZoom, true)
                            map.setGestureEnable(GestureType.Pan, true)
                            map.setGestureEnable(GestureType.RotateZoom, true)
                            nameLabelLayer = map.labelManager?.addLayer(LabelLayerOptions.from("campus_names"))
                            pinLabelLayer = map.labelManager?.addLayer(LabelLayerOptions.from("campus_pins"))
                            routeLineLayer = map.routeLineManager?.addLayer()
                            map.setOnLabelClickListener { _, _, label ->
                                (label.tag as? CampusBuilding)?.let { currentOnMarkerClick(it) }
                                true
                            }
                            // 라벨(마커/이름)이 아닌 지도 빈 곳을 누르면 정보 카드를 닫는다 — 라벨 클릭은
                            // 위 리스너가 true(소비)를 반환해서 여기까진 안 넘어온다.
                            map.setOnMapClickListener { _, _, _, _ -> currentOnMapBackgroundClick() }
                            if (buildings.isNotEmpty()) {
                                val points = buildings.map { LatLng.from(it.lat, it.lng) }.toTypedArray()
                                map.moveCamera(CameraUpdateFactory.fitMapPoints(points, 80))
                            }
                        }
                    }
                )
            }
        },
        onRelease = { it.finish() }
    )

    LaunchedEffect(kakaoMap, buildings) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (buildings.isNotEmpty()) {
            val points = buildings.map { LatLng.from(it.lat, it.lng) }.toTypedArray()
            map.moveCamera(CameraUpdateFactory.fitMapPoints(points, 80))
        }
        // 등록된 건물 전체를 지도 위에 주황색 이름표로 항상 보여준다 — 하나만 만들면 되니 buildings가
        // 바뀔 때만(사실상 최초 한 번) 다시 그린다.
        nameLabelLayer?.removeAll()
        buildings.forEach { building ->
            val name = building.name ?: return@forEach
            val textStyle = LabelTextStyle.from(26, BUILDING_NAME_COLOR, 4, 0xFFFFFFFF.toInt())
            nameLabelLayer?.addLabel(
                LabelOptions.from(LatLng.from(building.lat, building.lng))
                    .setStyles(LabelStyle.from(textStyle))
                    .setTexts(LabelTextBuilder().setTexts(name))
                    .setClickable(true)
                    .setTag(building)
            )
        }
    }

    LaunchedEffect(kakaoMap, departure, destination, route) {
        val map = kakaoMap ?: return@LaunchedEffect
        pinLabelLayer?.removeAll()
        routeLineLayer?.removeAll()

        departure?.let { building ->
            pinLabelLayer?.addLabel(
                LabelOptions.from(LatLng.from(building.lat, building.lng))
                    .setStyles(LabelStyle.from(pinMarkerBitmap(START_MARKER_COLOR)).setAnchorPoint(0.5f, 1f))
                    .setClickable(true)
                    .setTag(building)
            )
        }
        destination?.let { building ->
            pinLabelLayer?.addLabel(
                LabelOptions.from(LatLng.from(building.lat, building.lng))
                    .setStyles(LabelStyle.from(pinMarkerBitmap(END_MARKER_COLOR)).setAnchorPoint(0.5f, 1f))
                    .setClickable(true)
                    .setTag(building)
            )
        }

        when {
            // 출발지=목적지면 path에 점이 하나뿐이라 선을 그릴 게 없다 — 그 위치로 확대만 해준다.
            route != null && route.path.size >= 2 -> {
                // 출발/목적지가 둘 다 있으면 경로 전체가 보이도록.
                val points = route.path.map { LatLng.from(it.first, it.second) }
                val style = RouteLineStyle.from(24f, ROUTE_LINE_COLOR)
                val styles = RouteLineStyles.from(style)
                val segment = RouteLineSegment.from(points, styles)
                routeLineLayer?.addRouteLine(RouteLineOptions.from(segment))
                map.moveCamera(CameraUpdateFactory.fitMapPoints(points.toTypedArray(), 140))
            }
            route != null -> {
                val (lat, lng) = route.path.first()
                map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(lat, lng), SINGLE_PICK_ZOOM_LEVEL))
            }
            // 하나만 골랐을 땐 그 건물 쪽으로 살짝 확대해서 바로 위치를 확인할 수 있게 한다.
            destination != null -> {
                map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(destination.lat, destination.lng), SINGLE_PICK_ZOOM_LEVEL))
            }
            departure != null -> {
                map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(departure.lat, departure.lng), SINGLE_PICK_ZOOM_LEVEL))
            }
        }
    }
}

private const val BUILDING_NAME_COLOR = 0xFFFF6A1A.toInt()
private const val START_MARKER_COLOR = 0xFF4C6FFF.toInt()
private const val END_MARKER_COLOR = 0xFFFF6A1A.toInt()
private const val ROUTE_LINE_COLOR = 0xFFFF6A1A.toInt()
private const val SINGLE_PICK_ZOOM_LEVEL = 18

/** 출발/목적지 핀 아이콘 — 별도 드로어블 없이 코드로 흔히 보는 "위치 핀" 모양(머리 원 + 아래로
 *  뾰족한 꼬리)을 그린다. 앵커를 꼬리 끝(하단 중앙)에 맞춰야 그 지점이 실제 좌표를 정확히 가리킨다. */
private fun pinMarkerBitmap(colorArgb: Int): Bitmap {
    val width = 64
    val height = 84
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f
    val headRadius = width / 2f - 5f
    val headCenterY = headRadius + 5f

    val pinPath = Path().apply {
        addCircle(cx, headCenterY, headRadius, Path.Direction.CW)
    }
    val tailPath = Path().apply {
        moveTo(cx - headRadius * 0.55f, headCenterY + headRadius * 0.65f)
        lineTo(cx, height - 2f)
        lineTo(cx + headRadius * 0.55f, headCenterY + headRadius * 0.65f)
        close()
    }
    pinPath.op(tailPath, Path.Op.UNION)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb }
    canvas.drawPath(pinPath, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawPath(pinPath, strokePaint)

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    canvas.drawCircle(cx, headCenterY, headRadius * 0.38f, dotPaint)

    return bitmap
}

@Composable
private fun BuildingPickerDialog(
    buildings: List<CampusBuilding>,
    onSelect: (CampusBuilding) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(buildings, query) {
        if (query.isBlank()) buildings else buildings.filter { it.name?.contains(query) == true }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Text(text = "건물 선택", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("건물 이름 검색", color = TextPlaceholder, style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { IconSearch(tint = TextSecondary) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Background,
                    unfocusedContainerColor = Background,
                    focusedBorderColor = BorderFocused,
                    unfocusedBorderColor = Border,
                    cursorColor = Primary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedPlaceholderColor = TextPlaceholder,
                    unfocusedPlaceholderColor = TextPlaceholder,
                    disabledTextColor = TextDisabled
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.height(320.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(filtered, key = { it.id }) { building ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(building) }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = building.name.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
