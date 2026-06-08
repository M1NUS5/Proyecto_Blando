package com.example.myapplication1

import android.content.Context

class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    fun saveUser(
        id: String,
        name: String,
        email: String,
        token: String
    ) {
        prefs.edit()
            .putString("id", id)
            .putString("name", name)
            .putString("email", email)
            .putString("token", token)

            .putString("bio_id", id)
            .putString("bio_name", name)
            .putString("bio_email", email)
            .putString("bio_token", token)
            .putBoolean("biometric_enabled", true)
            .apply()
    }

    fun getUserId(): String? = prefs.getString("id", null)

    fun getName(): String? = prefs.getString("name", null)

    fun getEmail(): String? = prefs.getString("email", null)

    fun getToken(): String? = prefs.getString("token", null)

    fun getBiometricEmail(): String? = prefs.getString("bio_email", null)

    fun getBiometricName(): String? = prefs.getString("bio_name", null)

    fun hasActiveSession(): Boolean {
        return !getToken().isNullOrBlank() &&
                !getUserId().isNullOrBlank()
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean("biometric_enabled", false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("biometric_enabled", enabled)
            .apply()
    }

    fun canUseBiometricLogin(): Boolean {
        val bioId = prefs.getString("bio_id", null)
        val bioName = prefs.getString("bio_name", null)
        val bioEmail = prefs.getString("bio_email", null)
        val bioToken = prefs.getString("bio_token", null)

        return isBiometricEnabled() &&
                !bioId.isNullOrBlank() &&
                !bioName.isNullOrBlank() &&
                !bioEmail.isNullOrBlank() &&
                !bioToken.isNullOrBlank()
    }

    fun restoreSessionFromBiometric(): Boolean {
        val bioId = prefs.getString("bio_id", null)
        val bioName = prefs.getString("bio_name", null)
        val bioEmail = prefs.getString("bio_email", null)
        val bioToken = prefs.getString("bio_token", null)

        if (
            bioId.isNullOrBlank() ||
            bioName.isNullOrBlank() ||
            bioEmail.isNullOrBlank() ||
            bioToken.isNullOrBlank()
        ) {
            return false
        }

        prefs.edit()
            .putString("id", bioId)
            .putString("name", bioName)
            .putString("email", bioEmail)
            .putString("token", bioToken)
            .apply()

        return true
    }

    fun saveTheme(theme: String) {
        prefs.edit()
            .putString("theme", theme)
            .apply()
    }

    fun getTheme(): String {
        return prefs.getString("theme", "Claro") ?: "Claro"
    }

    fun logout() {
        val savedTheme = getTheme()

        prefs.edit()
            .remove("id")
            .remove("name")
            .remove("email")
            .remove("token")
            .putString("theme", savedTheme)
            .apply()
    }

    fun logoutAndDisableBiometric() {
        val savedTheme = getTheme()

        prefs.edit()
            .clear()
            .putString("theme", savedTheme)
            .apply()
    }
}