package com.daejin.woojintoday.ui.screens.terms

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.daejin.woojintoday.data.TermsContent
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary

/** Scrollable 이용약관 body — shared by the "이용약관" detail dialog and the later review dialog
 *  so both always show the exact same text. The privacy-policy link, when shown, is the LAST
 *  item inside this same scrollable list (not pinned outside it) — that way it always scrolls
 *  into view with the rest of the text instead of fighting with dialog/system-bar insets. */
@Composable
fun TermsBodyList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    showPrivacyLink: Boolean = false
) {
    val context = LocalContext.current
    val paragraphs = remember(TermsContent.TERMS_TEXT) {
        TermsContent.TERMS_TEXT.split("\n\n").filter { it.isNotBlank() }
    }

    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        items(paragraphs) { paragraph ->
            if (paragraph.startsWith("### ")) {
                Text(
                    text = paragraph.removePrefix("### "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
            } else {
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        if (showPrivacyLink) {
            item {
                Text(
                    text = "개인정보 처리방침 보기",
                    style = MaterialTheme.typography.bodySmall,
                    color = Primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 4.dp)
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(TermsContent.PRIVACY_POLICY_URL))
                            )
                        }
                )
            }
        }
    }
}
