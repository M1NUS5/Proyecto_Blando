package com.example.myapplication1

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private val TealPrimary    = Color(0xFF26A69A)
private val TealMedium     = Color(0xFF4DB6AC)
private val TealLight      = Color(0xFF80CBC4)
private val TealPale       = Color(0xFFB2DFDB)
private val TealSurface    = Color(0xFFE0F2F1)

@Composable
fun AuthScreen(
    isLogin: Boolean,
    onSwitch: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(session.getBiometricEmail() ?: "") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val huellaDisponible = isLogin &&
            session.canUseBiometricLogin() &&
            BiometricAuthHelper.isBiometricAvailable(context)

    fun iniciarConHuella() {
        if (!huellaDisponible) {
            Toast.makeText(context, "Primero inicia sesión con correo y contraseña para activar la huella.", Toast.LENGTH_LONG).show()
            return
        }
        BiometricAuthHelper.authenticate(
            context = context,
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
            onError = { mensaje -> Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TealPrimary, TealSurface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Image(
                painter = painterResource(id = R.drawable.logo_alyra),
                contentDescription = "ALYRA logo",
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "ALYRA",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Entrenador inteligente de ritmo",
                fontSize = 14.sp,
                color = TealPale
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .background(TealMedium.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(4.dp)
            ) {
                Button(
                    onClick = { if (!isLogin) onSwitch() },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLogin) Color.White else Color.Transparent,
                        contentColor = if (isLogin) TealPrimary else Color.White
                    )
                ) { Text("Iniciar Sesión", fontWeight = FontWeight.Bold) }

                Button(
                    onClick = { if (isLogin) onSwitch() },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isLogin) Color.White else Color.Transparent,
                        contentColor = if (!isLogin) TealPrimary else Color.White
                    )
                ) { Text("Registrarse", fontWeight = FontWeight.Bold) }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isLogin) "Bienvenido" else "Crear cuenta",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )

                    if (huellaDisponible) {
                        Text(
                            text = "Puedes entrar con huella sin escribir contraseña.",
                            fontSize = 13.sp,
                            color = TealMedium
                        )
                    }

                    if (!isLogin) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Nombre") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TealPrimary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                unfocusedBorderColor = TealLight
                            )
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TealPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = TealLight
                        )
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TealPrimary) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = TealLight
                        )
                    )

                    if (!isLogin) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            placeholder = { Text("Confirmar contraseña") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TealPrimary) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                unfocusedBorderColor = TealLight
                            )
                        )
                    }

                    Button(
                        onClick = {
                            if (loading) return@Button
                            if (isLogin && huellaDisponible && password.isBlank()) { iniciarConHuella(); return@Button }
                            if (isLogin && huellaDisponible && email.isBlank()) { iniciarConHuella(); return@Button }
                            if (email.isBlank() || password.isBlank()) {
                                Toast.makeText(context, if (huellaDisponible) "Presiona Iniciar sesión para usar huella o escribe tu contraseña." else "Completa todos los campos", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!isLogin && name.isBlank()) { Toast.makeText(context, "Ingresa tu nombre", Toast.LENGTH_SHORT).show(); return@Button }
                            if (!isLogin && password != confirmPassword) { Toast.makeText(context, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show(); return@Button }
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
                                                } else { Toast.makeText(context, "Error en datos del servidor", Toast.LENGTH_SHORT).show() }
                                            } else { Toast.makeText(context, "Credenciales incorrectas", Toast.LENGTH_SHORT).show() }
                                        }
                                        override fun onFailure(call: Call<LoginResponse>, t: Throwable) { loading = false; Toast.makeText(context, "Error conexión: ${t.message}", Toast.LENGTH_SHORT).show() }
                                    })
                            } else {
                                RetrofitClient.instance.register(RegisterRequest(name.trim(), email.trim(), password))
                                    .enqueue(object : Callback<Map<String, String>> {
                                        override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                                            loading = false
                                            if (response.isSuccessful) { Toast.makeText(context, "Usuario registrado. Ahora inicia sesión.", Toast.LENGTH_SHORT).show(); onSwitch() }
                                            else { Toast.makeText(context, "Error al registrar", Toast.LENGTH_SHORT).show() }
                                        }
                                        override fun onFailure(call: Call<Map<String, String>>, t: Throwable) { loading = false; Toast.makeText(context, "Error conexión: ${t.message}", Toast.LENGTH_SHORT).show() }
                                    })
                            }
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.White)
                    ) {
                        Text(
                            text = when {
                                loading -> "Cargando..."
                                isLogin && huellaDisponible && password.isBlank() -> "Iniciar sesión con huella"
                                isLogin -> "Iniciar sesión"
                                else -> "Crear cuenta"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (huellaDisponible) {
                        OutlinedButton(
                            onClick = { iniciarConHuella() },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, TealPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = TealPrimary)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text(text = "Entrar con huella", color = TealPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row {
                Text(text = if (isLogin) "¿No tienes cuenta? " else "¿Ya tienes cuenta? ", color = TealPale)
                Text(
                    text = if (isLogin) "Regístrate" else "Iniciar sesión",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSwitch() }
                )
            }
        }
    }
}