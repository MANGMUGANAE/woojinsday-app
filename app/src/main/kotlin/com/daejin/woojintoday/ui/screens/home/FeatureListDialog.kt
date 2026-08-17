package com.daejin.woojintoday.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.TextPrimary

@Composable
fun FeatureListDialog(
    features: List<HomeFeature>,
    onDismiss: () -> Unit,
    onFeatureClick: (HomeFeature) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    IconArrowBack(tint = TextPrimary)
                }
            }

            Text(
                text = "모아보기",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                // 맨 아래 카드가 화면 하단에 딱 붙지 않도록, 기능 카드 하나(140dp) 높이만큼 항상
                // 여유 스크롤 공간을 남겨둔다.
                contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp + 140.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(features) { feature ->
                    FeatureCard(
                        feature = feature,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onFeatureClick(feature) }
                    )
                }
            }
        }
    }
}
