package com.daejin.woojintoday.ui.screens.login

import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.data.model.Notice
import com.daejin.woojintoday.data.network.NoticeClient
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary

/**
 * Sits in the empty space below the login button — a public notice board, no auth needed.
 *
 * By default owns its list/detail selection state and renders its own back-to-list row. Pass
 * [selectedPath]/[onSelectedPathChange] to hoist that state to a caller that provides its own
 * single back button (e.g. a host Dialog) — in that case set [showBackRow] to false so only one
 * back control exists on screen instead of two stacked ones.
 */
@Composable
fun NoticeBoardSection(
    modifier: Modifier = Modifier,
    selectedPath: String? = null,
    onSelectedPathChange: ((String?) -> Unit)? = null,
    showBackRow: Boolean = true
) {
    val viewModel: NoticeViewModel = viewModel()
    var internalSelectedPath by remember { mutableStateOf<String?>(null) }
    val path = if (onSelectedPathChange != null) selectedPath else internalSelectedPath
    val setPath = onSelectedPathChange ?: { value: String? -> internalSelectedPath = value }

    Column(modifier = modifier) {
        if (path == null) {
            when {
                viewModel.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                viewModel.errorMessage != null && viewModel.notices.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = viewModel.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.notices) { notice ->
                            NoticeRow(notice = notice, onClick = { setPath(notice.detailPath) })
                        }
                    }
                }
            }
        } else {
            if (showBackRow) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { setPath(null) }) {
                        IconArrowBack(tint = TextPrimary)
                    }
                    Text("목록으로", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                }
            }
            NoticeDetailWebView(
                url = NoticeClient.BASE_URL + path,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun NoticeRow(notice: Notice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (notice.isNew) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("new", style = MaterialTheme.typography.bodySmall, color = OnPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${notice.writer} · ${notice.date} · 조회 ${notice.views}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NoticeDetailWebView(url: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            }
        )
    }
}
