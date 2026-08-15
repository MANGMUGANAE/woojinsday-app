package com.daejin.woojintoday.ui.screens.terms

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.daejin.woojintoday.data.TermsAgreementStore
import com.daejin.woojintoday.data.TermsContent
import com.daejin.woojintoday.ui.icons.IconCheck
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary

/** First-run gate shown after the splash screen's permission checks and before login/home —
 *  the user must check every required item here before the app can be used at all. */
@Composable
fun TermsAgreementScreen(onAgree: () -> Unit) {
    val context = LocalContext.current
    var termsChecked by remember { mutableStateOf(false) }
    var privacyChecked by remember { mutableStateOf(false) }
    var showTermsDetail by remember { mutableStateOf(false) }
    val allChecked = termsChecked && privacyChecked

    if (showTermsDetail) {
        TermsDetailDialog(onDismiss = { showTermsDetail = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text(text = "이용약관 동의", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "서비스 이용을 위해 아래 약관에 모두 동의해주세요",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Surface)
                .clickable {
                    val next = !allChecked
                    termsChecked = next
                    privacyChecked = next
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConsentCheckbox(checked = allChecked)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "전체 동의합니다",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
        Spacer(modifier = Modifier.height(6.dp))

        AgreementRow(
            checked = termsChecked,
            label = "이용약관 동의 (필수)",
            onToggle = { termsChecked = !termsChecked },
            onViewClick = { showTermsDetail = true }
        )
        AgreementRow(
            checked = privacyChecked,
            label = "개인정보 처리방침 동의 (필수)",
            onToggle = { privacyChecked = !privacyChecked },
            onViewClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(TermsContent.PRIVACY_POLICY_URL))
                )
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                TermsAgreementStore(context).save(System.currentTimeMillis())
                onAgree()
            },
            enabled = allChecked,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = OnPrimary,
                disabledContainerColor = Surface,
                disabledContentColor = TextSecondary
            )
        ) {
            Text("동의하고 시작하기", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AgreementRow(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
    onViewClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConsentCheckbox(checked = checked)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "보기",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable { onViewClick() }
                .padding(4.dp)
        )
    }
}

@Composable
private fun ConsentCheckbox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Primary else Surface)
            .border(
                width = 1.5.dp,
                color = if (checked) Primary else Border,
                shape = RoundedCornerShape(6.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) IconCheck(tint = OnPrimary, size = 12.dp)
    }
}
