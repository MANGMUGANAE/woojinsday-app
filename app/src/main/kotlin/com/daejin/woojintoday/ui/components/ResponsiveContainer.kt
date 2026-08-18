package com.daejin.woojintoday.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val MaxContentWidth = 640.dp

/** 화면 폭이 이 앱이 원래 쓰던 휴대폰 세로 폭(640dp 미만)이면 아무 효과가 없어 지금과 완전히
 *  동일하게 그려지고, 태블릿/폴더블/휴대폰 가로처럼 그보다 넓어지면 640dp로 잘라 가운데 정렬한다.
 *  개별 화면을 화면 크기별로 다시 설계하는 대신, 큰 화면에서 지금 레이아웃이 늘어나 이미지이
 *  카드 밖으로 밀려나거나 하는 걸 막는 공용 안전장치. content가 BoxScope를 받는 건, 감싸는
 *  화면들이 내부에서 `Modifier.align(...)`으로 바텀시트/오버레이를 띄우는 경우가 있어서다. */
@Composable
fun ResponsiveContainer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            // widthIn이 fillMaxWidth보다 앞(바깥쪽)에 와야 한다 — fillMaxWidth가 먼저 오면 폭을
            // 부모 최대치로 꽉 채우도록 min=max로 고정해버려서, 뒤이은 widthIn(max=...)이 그 min을
            // 못 줄이고 무시된다(캡이 전혀 안 걸리는 버그였다). widthIn을 바깥에 둬야 부모 제약을
            // 먼저 480dp로 좁힌 뒤 그 안에서 fillMaxWidth가 꽉 채운다.
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
            content = content
        )
    }
}
