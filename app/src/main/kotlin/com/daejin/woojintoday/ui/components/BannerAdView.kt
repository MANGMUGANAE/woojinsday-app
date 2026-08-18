package com.daejin.woojintoday.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.daejin.woojintoday.ui.theme.Surface
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private const val BANNER_AD_UNIT_ID = "ca-app-pub-2619759063555507/3158513318"

/** Bottom-pinned home-screen banner, framed to match the app's rounded-card look rather than
 *  sitting edge-to-edge like a raw ad strip. */
@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val horizontalMarginDp = 16

    // 기기 실제 화면 폭이 아니라 이 컴포저블에 실제로 배정된 폭을 써야 한다 — ResponsiveContainer로
    // 폭이 좁혀진 화면(태블릿 등)에서는 이 값이 기기 전체 폭보다 작아서, 광고가 배정된 영역보다
    // 넓게 요청되는 걸 막아준다. 휴대폰 세로 폭에서는 어차피 둘이 같아서 동작이 그대로다.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val adWidthDp = (maxWidth - horizontalMarginDp.dp * 2).value.toInt()

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalMarginDp.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface),
            factory = {
                AdView(context).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp))
                    adUnitId = BANNER_AD_UNIT_ID
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
