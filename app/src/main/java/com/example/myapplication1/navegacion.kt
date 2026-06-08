package com.example.myapplication1

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun Navegacion(onThemeChange: (Boolean) -> Unit) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login"
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

        composable("settings") {
            SettingsScreen(
                navController = navController,
                onThemeChange = onThemeChange
            )
        }
    }
}