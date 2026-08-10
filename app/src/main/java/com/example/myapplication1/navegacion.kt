package com.example.myapplication1

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/** Pantalla raiz una vez que el usuario inicio sesion. */
const val RUTA_INICIO = "home"

/**
 * Navega entre las secciones de la barra inferior manteniendo una sola pantalla
 * sobre la raiz. Con `navigate()` a secas el historial creceria sin limite y el
 * boton Atras recorreria todas las visitas anteriores.
 */
fun NavController.irASeccion(ruta: String) {
    if (currentDestination?.route == ruta) return

    navigate(ruta) {
        popUpTo(RUTA_INICIO)
        launchSingleTop = true
    }
}

@Composable
fun Navegacion(onThemeChange: (Boolean) -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val session = SessionManager(context)

    val startDestination = if (session.hasActiveSession()) "home" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // El correo viaja como parametro opcional para que, al crear una cuenta,
        // la pantalla de inicio de sesion aparezca ya con ese correo escrito.
        composable(
            route = "login?email={email}",
            arguments = listOf(
                navArgument("email") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entrada ->
            AuthScreen(
                isLogin = true,
                correoInicial = entrada.arguments?.getString("email").orEmpty(),
                onSwitch = {
                    navController.navigate("register")
                },
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo(0) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable("register") {
            AuthScreen(
                isLogin = false,
                onSwitch = { correoRegistrado ->
                    val destino = if (correoRegistrado.isNullOrBlank()) {
                        "login"
                    } else {
                        "login?email=${Uri.encode(correoRegistrado)}"
                    }

                    navController.navigate(destino) {
                        popUpTo("register") {
                            inclusive = true
                        }
                    }
                },
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo(0) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(navController)
        }

        composable("profile") {
            ProfileScreen(navController)
        }

        composable("datos_corporales") {
            DatosCorporalesScreen(navController)
        }

        composable("IA") {
            IAScreen(navController)
        }

        composable("agenda") {
            AgendaScreen(navController)
        }

        composable("camera") {
            CameraScreen(navController)
        }

        composable("settings") {
            SettingsScreen(
                navController = navController,
                onThemeChange = onThemeChange
            )
        }
    }
}