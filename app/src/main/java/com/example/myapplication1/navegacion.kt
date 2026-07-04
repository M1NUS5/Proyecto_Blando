package com.example.myapplication1

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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

        composable("login") {
            AuthScreen(
                isLogin = true,
                onSwitch = {
                    navController.navigate("register")
                },
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable("register") {
            AuthScreen(
                isLogin = false,
                onSwitch = {
                    navController.navigate("login")
                },
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("register") {
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