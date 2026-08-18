package com.daejin.woojintoday.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daejin.woojintoday.data.TermsAgreementStore
import com.daejin.woojintoday.ui.components.ResponsiveContainer
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.screens.terms.TermsBodyList
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Read-only review of the terms the user already agreed to, opened from the 우진이 menu. */
@Composable
fun TermsHistoryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val agreedAt = remember { TermsAgreementStore(context).agreedAtEpochMillis() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Background)) {
        ResponsiveContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    IconArrowBack(tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "이용약관", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }

            if (agreedAt != null) {
                Text(
                    text = "동의 일시: ${formatAgreedAt(agreedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 개인정보 처리방침 링크는 리스트의 마지막 항목으로 같이 스크롤되며, 아래쪽에
            // 넉넉한 여백을 둬서 하단 내비게이션 바/뒤로가기 버튼에 가리지 않게 한다.
            TermsBodyList(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 64.dp),
                showPrivacyLink = true
            )
        }
        }
        }
    }
}

private fun formatAgreedAt(epochMillis: Long): String =
    SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREAN).format(Date(epochMillis))
