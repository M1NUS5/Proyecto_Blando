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

    fun getDisplayName(): String? = prefs.getString("display_name", null)

    fun saveDisplayName(name: String) {
        prefs.edit().putString("display_name", name).apply()
    }

    fun getPhotoUri(): String? = prefs.getString("photo_uri", null)

    fun savePhotoUri(uri: String) {
        prefs.edit().putString("photo_uri", uri).apply()
    }

    // --- Datos corporales -------------------------------------------------
    // Son datos personales de salud, asi que se borran al cerrar sesion: de lo
    // contrario, quien iniciara sesion despues en el mismo telefono heredaria
    // el peso y la estatura del usuario anterior.

    fun getPerfilFisico(): PerfilFisico = PerfilFisico(
        edad = prefs.getInt("perfil_edad", 0),
        estaturaCm = prefs.getInt("perfil_estatura", 0),
        pesoKg = prefs.getFloat("perfil_peso", 0f).toDouble(),
        sexo = Sexo.desde(prefs.getString("perfil_sexo", null)),
        nivelActividad = NivelActividad.desde(prefs.getString("perfil_actividad", null)),
        objetivo = ObjetivoPeso.desde(prefs.getString("perfil_objetivo", null))
    )

    fun savePerfilFisico(perfil: PerfilFisico) {
        prefs.edit()
            .putInt("perfil_edad", perfil.edad)
            .putInt("perfil_estatura", perfil.estaturaCm)
            .putFloat("perfil_peso", perfil.pesoKg.toFloat())
            .putString("perfil_sexo", perfil.sexo.name)
            .putString("perfil_actividad", perfil.nivelActividad.name)
            .putString("perfil_objetivo", perfil.objetivo.name)
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
            .remove("perfil_edad")
            .remove("perfil_estatura")
            .remove("perfil_peso")
            .remove("perfil_sexo")
            .remove("perfil_actividad")
            .remove("perfil_objetivo")
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