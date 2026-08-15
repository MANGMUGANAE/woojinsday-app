package com.daejin.woojintoday.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted local storage for the student's login credentials, used for auto-login on launch. */
class CredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(studentNo: String, password: String, userId2: String? = null) {
        prefs.edit()
            .putString(KEY_STUDENT_NO, studentNo)
            .putString(KEY_PASSWORD, password)
            .also { editor -> if (userId2 != null) editor.putString(KEY_USER_ID2, userId2) }
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun studentNo(): String? = prefs.getString(KEY_STUDENT_NO, null)
    fun password(): String? = prefs.getString(KEY_PASSWORD, null)
    fun userId2(): String? = prefs.getString(KEY_USER_ID2, null)
    fun hasCredentials(): Boolean = studentNo() != null && password() != null

    companion object {
        private const val PREFS_NAME = "sugang_credentials"
        private const val KEY_STUDENT_NO = "student_no"
        private const val KEY_PASSWORD = "password"
        private const val KEY_USER_ID2 = "user_id2"
    }
}
