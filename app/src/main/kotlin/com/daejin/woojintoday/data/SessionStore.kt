package com.daejin.woojintoday.data

import android.content.Context

/** Local storage for the WMONID/JSESSIONID cookies issued after a successful login. */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(wmonid: String?, jsessionId: String?) {
        prefs.edit()
            .putString(KEY_WMONID, wmonid)
            .putString(KEY_JSESSIONID, jsessionId)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun wmonid(): String? = prefs.getString(KEY_WMONID, null)
    fun jsessionId(): String? = prefs.getString(KEY_JSESSIONID, null)
    fun hasSession(): Boolean = wmonid() != null || jsessionId() != null

    companion object {
        private const val PREFS_NAME = "sugang_session"
        private const val KEY_WMONID = "wmonid"
        private const val KEY_JSESSIONID = "jsessionid"
    }
}
