package com.example.myapplication1

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)

        return biometricManager.canAuthenticate(BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = context.findFragmentActivity()

        if (activity == null) {
            onError("No se pudo iniciar la autenticación biométrica.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Huella no reconocida. Intenta otra vez.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Entrar con huella")
            .setSubtitle("Confirma tu identidad para abrir la app")
            .setDescription("Usa tu huella o biometría configurada en el teléfono.")
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .setNegativeButtonText("Cancelar")
            .build()

        prompt.authenticate(promptInfo)
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var currentContext = this

    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) {
            return currentContext
        }

        currentContext = currentContext.baseContext
    }

    return null
}