package com.example.myapplication1

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.example.myapplication1.ui.theme.MyApplication1Theme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppSettingsStore.cargar(applicationContext)
        PerfilStore.cargar(applicationContext)

        // Se adelanta el arranque del servidor mientras el usuario navega, para
        // que la primera accion que dependa de la red no cargue con la espera.
        DespertadorServidor.despertar()

        setContent {
            val context = LocalContext.current
            val settings by AppSettingsStore.settings.collectAsState()

            LaunchedEffect(settings) {
                enviarConfiguracionAlReloj(
                    context = context,
                    settings = settings
                )
            }

            MyApplication1Theme(
                darkTheme = settings.temaOscuro
            ) {
                Navegacion(
                    onThemeChange = { isDark ->
                        val nuevaConfig = AppSettingsStore.settings.value.copy(
                            temaOscuro = isDark
                        )

                        AppSettingsStore.actualizar(
                            context = context,
                            nuevoValor = nuevaConfig
                        )

                        enviarConfiguracionAlReloj(
                            context = context,
                            settings = nuevaConfig
                        )

                        SessionManager(context).saveTheme(
                            if (isDark) "Oscuro" else "Claro"
                        )
                    }
                )
            }
        }
    }
}