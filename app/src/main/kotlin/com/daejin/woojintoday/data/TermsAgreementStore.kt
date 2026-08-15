package com.daejin.woojintoday.data

import android.content.Context

/** Local record of whether the user has agreed to the terms of service, and when. */
class TermsAgreementStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAgreed(): Boolean = prefs.getLong(KEY_AGREED_AT, -1L) != -1L

    fun agreedAtEpochMillis(): Long? =
        prefs.getLong(KEY_AGREED_AT, -1L).takeIf { it != -1L }

    fun save(agreedAtEpochMillis: Long) {
        prefs.edit().putLong(KEY_AGREED_AT, agreedAtEpochMillis).apply()
    }

    companion object {
        private const val PREFS_NAME = "sugang_terms_agreement"
        private const val KEY_AGREED_AT = "agreed_at"
    }
}
