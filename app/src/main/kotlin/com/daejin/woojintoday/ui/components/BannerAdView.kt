package com.daejin.woojintoday.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val density = LocalDensity.current
    val horizontalMarginDp = 16
    val screenWidthDp = (context.resources.displayMetrics.widthPixels / density.density).toInt()
    val adWidthDp = screenWidthDp - horizontalMarginDp * 2

    AndroidView(
        modifier = modifier
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
