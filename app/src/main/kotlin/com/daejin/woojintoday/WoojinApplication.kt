package com.daejin.woojintoday

import android.app.Application
import com.daejin.woojintoday.data.NoticeWatchStore
import com.daejin.woojintoday.schedule.NoticeWatchScheduler
import com.kakao.vectormap.KakaoMapSdk

/** 카카오맵 SDK는 지도를 그리기 전에 앱 전체에서 한 번 초기화돼야 해서 Application에서 한다.
 *  이 네이티브 앱 키가 인식되려면, 카카오 디벨로퍼스 콘솔의 해당 앱 > 플랫폼 > Android에
 *  이 빌드의 서명 키해시가 등록돼 있어야 한다(등록 안 돼 있으면 지도가 안 뜬다). */
class WoojinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoMapSdk.init(this, "16cdb450a835efc3b6388ccdc3016ec0")
        // "글 올라오면 알려드려요"가 기본으로 켜져있는데(NoticeWatchStore.isEnabled 기본값 true),
        // 실제 알람은 토글을 만졌을 때나 재부팅 시에만 걸린다 — 한 번도 안 만진 새 설치도 매번
        // 프로세스가 뜰 때마다 여기서 확실히 걸어준다(이미 걸려있어도 재예약은 그냥 덮어쓸 뿐이라 안전).
        if (NoticeWatchStore(this).anyEnabled()) {
            NoticeWatchScheduler.scheduleNext(this)
        }
    }
}
