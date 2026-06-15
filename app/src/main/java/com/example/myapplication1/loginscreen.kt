package com.example.myapplication1

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun AuthScreen(
    isLogin: Boolean,
    onSwitch: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }

    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    var name            by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf(session.getBiometricEmail() ?: "") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading         by remember { mutableStateOf(false) }

    val huellaDisponible = isLogin &&
            session.canUseBiometricLogin() &&
            BiometricAuthHelper.isBiometricAvailable(context)

    fun iniciarConHuella() {
        if (!huellaDisponible) {
            Toast.makeText(
                context,
                "Primero inicia sesión con correo y contraseña para activar la huella.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        BiometricAuthHelper.authenticate(
            context  = context,
            onSuccess = {
                val restaurado = session.restoreSessionFromBiometric()
                if (restaurado) {
                    email = session.getEmail() ?: ""
                    Toast.makeText(context, "Huella verificada. Sesión restaurada.", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                } else {
                    Toast.makeText(context, "No se pudo restaurar la sesión guardada.", Toast.LENGTH_LONG).show()
                }
            },
            onError = { mensaje ->
                Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(uiColors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(uiColors.topBarStart, uiColors.topBarEnd)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color  = Color.White.copy(alpha = 0.20f),
                        shape  = RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector      = Icons.Default.DirectionsRun,
                    contentDescription = "ALYRA",
                    tint             = Color.White,
                    modifier         = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text       = "ALYRA",
                fontSize   = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = Color.White,
                letterSpacing = 4.sp
            )

            Text(
                text     = "Entrenador Inteligente de Ritmo",
                fontSize = 12.sp,
                color    = Color.White.copy(alpha = 0.80f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier
                    .background(
                        color = uiColors.cardSecondary,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(4.dp)
            ) {
                Button(
                    onClick = { if (!isLogin) onSwitch() },
                    shape  = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLogin) uiColors.primaryButton else Color.Transparent,
                        contentColor   = if (isLogin) uiColors.primaryButtonText else uiColors.textSecondary
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("Iniciar Sesión", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { if (isLogin) onSwitch() },
                    shape  = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isLogin) uiColors.primaryButton else Color.Transparent,
                        contentColor   = if (!isLogin) uiColors.primaryButtonText else uiColors.textSecondary
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("Registrarse", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape  = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = uiColors.card)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text       = if (isLogin) "Bienvenido de nuevo" else "Crea tu cuenta",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = uiColors.textPrimary
                    )

                    if (huellaDisponible) {
                        Text(
                            text     = "Puedes entrar con huella sin escribir contraseña.",
                            fontSize = 13.sp,
                            color    = uiColors.textMuted
                        )
                    }

                    if (!isLogin) {
                        OutlinedTextField(
                            value         = name,
                            onValueChange = { name = it },
                            label         = { Text("Nombre") },
                            leadingIcon   = {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = uiColors.primaryButton
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = alyraTextFieldColors(uiColors)
                        )
                    }

                    OutlinedTextField(
                        value         = email,
                        onValueChange = { email = it },
                        label         = { Text("Correo electrónico") },
                        leadingIcon   = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = uiColors.primaryButton
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = alyraTextFieldColors(uiColors)
                    )

                    OutlinedTextField(
                        value               = password,
                        onValueChange       = { password = it },
                        label               = { Text("Contraseña") },
                        leadingIcon         = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = uiColors.primaryButton
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = alyraTextFieldColors(uiColors)
                    )

                    if (!isLogin) {
                        OutlinedTextField(
                            value               = confirmPassword,
                            onValueChange       = { confirmPassword = it },
                            label               = { Text("Confirmar contraseña") },
                            leadingIcon         = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = uiColors.primaryButton
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = alyraTextFieldColors(uiColors)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (loading) return@Button

                            if (isLogin && huellaDisponible && password.isBlank()) {
                                iniciarConHuella(); return@Button
                            }
                            if (isLogin && huellaDisponible && email.isBlank()) {
                                iniciarConHuella(); return@Button
                            }
                            if (email.isBlank() || password.isBlank()) {
                                Toast.makeText(
                                    context,
                                    if (huellaDisponible) "Presiona Iniciar sesión para usar huella o escribe tu contraseña."
                                    else "Completa todos los campos",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            if (!isLogin && name.isBlank()) {
                                Toast.makeText(context, "Ingresa tu nombre", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!isLogin && password != confirmPassword) {
                                Toast.makeText(context, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            loading = true

                            if (isLogin) {
                                RetrofitClient.instance.login(LoginRequest(email.trim(), password))
                                    .enqueue(object : Callback<LoginResponse> {
                                        override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                                            loading = false
                                            if (response.isSuccessful) {
                                                val data = response.body()
                                                if (data != null) {
                                                    session.saveUser(data.user._id, data.user.name, data.user.email, data.token)
                                                    Toast.makeText(context, "Login exitoso. Huella activada.", Toast.LENGTH_SHORT).show()
                                                    onLoginSuccess()
                                                } else {
                                                    Toast.makeText(context, "Error en datos del servidor", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                                            loading = false
                                            Toast.makeText(context, "Error conexión: ${t.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                            } else {
                                RetrofitClient.instance.register(RegisterRequest(name.trim(), email.trim(), password))
                                    .enqueue(object : Callback<Map<String, String>> {
                                        override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                                            loading = false
                                            if (response.isSuccessful) {
                                                Toast.makeText(context, "Usuario registrado. Ahora inicia sesión.", Toast.LENGTH_SHORT).show()
                                                onSwitch()
                                            } else {
                                                Toast.makeText(context, "Error al registrar", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                                            loading = false
                                            Toast.makeText(context, "Error conexión: ${t.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                            }
                        },
                        shape  = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = uiColors.primaryButton,
                            contentColor   = uiColors.primaryButtonText
                        )
                    ) {
                        Text(
                            text = when {
                                loading -> "Cargando..."
                                isLogin && huellaDisponible && password.isBlank() -> "Iniciar sesión con huella"
                                isLogin -> "Iniciar sesión"
                                else    -> "Crear cuenta"
                            },
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (huellaDisponible) {
                        OutlinedButton(
                            onClick  = { iniciarConHuella() },
                            shape    = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp, uiColors.primaryButton
                            )
                        ) {
                            Icon(
                                imageVector      = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint             = uiColors.primaryButton,
                                modifier         = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.padding(5.dp))
                            Text(
                                text       = "Entrar con huella",
                                color      = uiColors.primaryButton,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row {
                Text(
                    text  = if (isLogin) "¿No tienes cuenta? " else "¿Ya tienes cuenta? ",
                    color = uiColors.textSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text       = if (isLogin) "Regístrate" else "Iniciar sesión",
                    color      = uiColors.primaryButton,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    modifier   = Modifier.clickable { onSwitch() }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun alyraTextFieldColors(uiColors: AppUiColors) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = uiColors.primaryButton,
    unfocusedBorderColor = uiColors.border,
    focusedLabelColor    = uiColors.primaryButton,
    unfocusedLabelColor  = uiColors.textMuted,
    cursorColor          = uiColors.primaryButton,
    focusedTextColor     = uiColors.textPrimary,
    unfocusedTextColor   = uiColors.textPrimary,
    focusedContainerColor   = uiColors.inputBackground,
    unfocusedContainerColor = uiColors.inputBackground
)