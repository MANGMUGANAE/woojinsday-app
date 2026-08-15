package com.daejin.woojintoday

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

/** 카카오맵 SDK는 지도를 그리기 전에 앱 전체에서 한 번 초기화돼야 해서 Application에서 한다.
 *  이 네이티브 앱 키가 인식되려면, 카카오 디벨로퍼스 콘솔의 해당 앱 > 플랫폼 > Android에
 *  이 빌드의 서명 키해시가 등록돼 있어야 한다(등록 안 돼 있으면 지도가 안 뜬다). */
class WoojinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoMapSdk.init(this, "16cdb450a835efc3b6388ccdc3016ec0")
    }
}
