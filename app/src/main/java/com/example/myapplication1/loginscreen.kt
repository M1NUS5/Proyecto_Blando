package com.example.myapplication1

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private val TealPrimary    = Color(0xFF26A69A)
private val TealMedium     = Color(0xFF4DB6AC)
private val TealLight      = Color(0xFF80CBC4)
private val TealPale       = Color(0xFFB2DFDB)
private val TealSurface    = Color(0xFFE0F2F1)

/** Longitud minima exigida al crear una cuenta nueva. */
private const val LARGO_MINIMO_PASSWORD = 6

private fun correoValido(correo: String): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()

/**
 * Extrae el mensaje que envia el backend en `{ "error": "..." }`.
 *
 * Sin esto la aplicacion mostraba siempre un texto generico y se perdia la
 * causa real del fallo (por ejemplo "Usuario ya existe" o "Contraseña
 * incorrecta"), que es justo lo que el usuario necesita saber para corregir.
 */
private fun mensajeDelServidor(cuerpo: ResponseBody?, porDefecto: String): String {
    val crudo = runCatching { cuerpo?.string() }.getOrNull().orEmpty()
    if (crudo.isBlank()) return porDefecto

    return runCatching {
        JSONObject(crudo).optString("error").ifBlank { porDefecto }
    }.getOrDefault(porDefecto)
}

@Composable
fun AuthScreen(
    isLogin: Boolean,
    /** Correo con el que llega la pantalla, por ejemplo tras crear una cuenta. */
    correoInicial: String = "",
    /** Cambia entre iniciar sesion y registrarse, arrastrando el correo si aplica. */
    onSwitch: (String?) -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }
    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    LaunchedEffect(Unit) { AppSettingsStore.cargar(context) }

    /** Verdadero cuando la pantalla se abrio justo despues de crear una cuenta. */
    val vieneDeRegistro = correoInicial.isNotBlank()

    var name by remember { mutableStateOf("") }

    // Prioridad del correo mostrado:
    //   1. El de la cuenta recien creada.
    //   2. El de la ultima sesion guardada, como comodidad al iniciar sesion.
    //   3. Vacio, al crear una cuenta nueva.
    var email by remember {
        mutableStateOf(
            when {
                vieneDeRegistro -> correoInicial
                isLogin -> session.getBiometricEmail().orEmpty()
                else -> ""
            }
        )
    }

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    var mostrarPassword by remember { mutableStateOf(false) }
    var errorServidor by remember { mutableStateOf<String?>(null) }

    // Validaciones en vivo: solo se senala el campo cuando el usuario ya escribio
    // algo, para no mostrarle errores en un formulario que apenas va empezando.
    val errorCorreo = email.isNotBlank() && !correoValido(email)
    val errorPassword = !isLogin && password.isNotBlank() && password.length < LARGO_MINIMO_PASSWORD
    val errorConfirmacion = !isLogin && confirmPassword.isNotBlank() && confirmPassword != password

    // El formulario solo se puede enviar cuando esta completo y sin errores. Asi
    // se evitan peticiones invalidas y, sobre todo, que un doble toque dispare
    // dos registros o dos inicios de sesion mientras el servidor responde.
    val formularioListo = if (isLogin) {
        email.isNotBlank() && password.isNotBlank() && !errorCorreo
    } else {
        name.isNotBlank() &&
            email.isNotBlank() && !errorCorreo &&
            password.length >= LARGO_MINIMO_PASSWORD &&
            confirmPassword == password
    }

    // La huella restaura la sesion guardada en el dispositivo. Si el usuario acaba
    // de crear una cuenta distinta, ofrecerla lo haria entrar con la cuenta
    // anterior, por eso en ese caso se oculta.
    val huellaDisponible = isLogin &&
            !vieneDeRegistro &&
            session.canUseBiometricLogin() &&
            BiometricAuthHelper.isBiometricAvailable(context)

    /**
     * Envia el formulario. Vive fuera del boton porque la tecla "Listo" del
     * teclado tambien debe poder dispararlo: antes esa tecla solo cerraba el
     * teclado y obligaba a buscar el boton con el dedo.
     *
     * Comprueba [formularioListo] por su cuenta en lugar de confiar en que el
     * boton este deshabilitado, ya que desde el teclado no existe esa proteccion.
     */
    fun enviarFormulario() {
        if (loading || !formularioListo) return

        errorServidor = null
        loading = true

        if (isLogin) {
            RetrofitClient.instance.login(LoginRequest(email.trim(), password))
                .enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                        loading = false
                        val data = response.body()

                        when {
                            response.isSuccessful && data != null -> {
                                session.saveUser(data.user._id, data.user.name, data.user.email, data.token)

                                // Carga el nombre y la foto de quien acaba de entrar.
                                PerfilStore.cargar(context)
                                Toast.makeText(context, "Bienvenido, ${data.user.name}", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            }
                            response.isSuccessful -> {
                                errorServidor = "El servidor respondió sin datos de usuario."
                            }
                            else -> {
                                errorServidor = mensajeDelServidor(
                                    response.errorBody(),
                                    "No se pudo iniciar sesión. Revisa tu correo y contraseña."
                                )
                            }
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        loading = false
                        errorServidor = "Sin conexión con el servidor. Verifica tu internet e intenta de nuevo."
                    }
                })
        } else {
            RetrofitClient.instance.register(RegisterRequest(name.trim(), email.trim(), password))
                .enqueue(object : Callback<Map<String, String>> {
                    override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                        loading = false

                        if (response.isSuccessful) {
                            Toast.makeText(context, "Cuenta creada. Ahora inicia sesión.", Toast.LENGTH_SHORT).show()
                            // Se arrastra el correo recien registrado para que el
                            // usuario no tenga que volver a escribirlo.
                            onSwitch(email.trim())
                        } else {
                            errorServidor = mensajeDelServidor(
                                response.errorBody(),
                                "No se pudo crear la cuenta."
                            )
                        }
                    }

                    override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                        loading = false
                        errorServidor = "Sin conexión con el servidor. Verifica tu internet e intenta de nuevo."
                    }
                })
        }
    }

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
                    PerfilStore.cargar(context)
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
                if (settings.temaOscuro)
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xFF0D2E2C), Color(0xFF0D1F1E))
                    )
                else
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(TealPrimary, TealSurface)
                    )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Sin desplazamiento vertical el formulario de registro no cabe
                // en pantallas pequenas ni con el tamano de letra del sistema en
                // grande: al abrirse el teclado, el boton de crear cuenta queda
                // fuera de vista y no habia manera de alcanzarlo. imePadding
                // ademas levanta el contenido por encima del teclado en lugar de
                // dejar que lo tape.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.White, CircleShape)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_alyra),
                    contentDescription = "ALYRA logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

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
                    onClick = { if (!isLogin) onSwitch(null) },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLogin) Color.White else Color.Transparent,
                        contentColor = if (isLogin) TealPrimary else Color.White
                    )
                ) { Text("Iniciar Sesión", fontWeight = FontWeight.Bold) }

                Button(
                    onClick = { if (isLogin) onSwitch(null) },
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
                colors = CardDefaults.cardColors(containerColor = uiColors.card),
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
                        color = uiColors.textSecondary
                    )

                    if (huellaDisponible) {
                        Text(
                            text = "Puedes entrar con huella sin escribir contraseña.",
                            fontSize = 13.sp,
                            color = uiColors.textMuted
                        )
                    }

                    if (!isLogin) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; errorServidor = null },
                            label = { Text("Nombre") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TealPrimary) },
                            // Era el unico campo sin singleLine: al pulsar Enter
                            // insertaba un salto de linea, el recuadro crecia y
                            // desacomodaba el formulario.
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                focusedTextColor = uiColors.textPrimary,
                                unfocusedTextColor = uiColors.textPrimary,
                                focusedContainerColor = uiColors.inputBackground,
                                unfocusedContainerColor = uiColors.inputBackground,
                                unfocusedBorderColor = TealLight
                            )
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorServidor = null },
                        label = { Text("Correo electrónico") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TealPrimary) },
                        singleLine = true,
                        isError = errorCorreo,
                        supportingText = if (errorCorreo) {
                            { Text("Escribe un correo válido, por ejemplo nombre@correo.com") }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            focusedTextColor = uiColors.textPrimary,
                            unfocusedTextColor = uiColors.textPrimary,
                            focusedContainerColor = uiColors.inputBackground,
                            unfocusedContainerColor = uiColors.inputBackground,
                            unfocusedBorderColor = TealLight
                        )
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorServidor = null },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TealPrimary) },
                        trailingIcon = {
                            IconButton(onClick = { mostrarPassword = !mostrarPassword }) {
                                Icon(
                                    imageVector = if (mostrarPassword) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (mostrarPassword) {
                                        "Ocultar contraseña"
                                    } else {
                                        "Mostrar contraseña"
                                    },
                                    tint = TealPrimary
                                )
                            }
                        },
                        visualTransformation = if (mostrarPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        isError = errorPassword,
                        supportingText = if (errorPassword) {
                            { Text("La contraseña debe tener al menos $LARGO_MINIMO_PASSWORD caracteres") }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (isLogin) ImeAction.Done else ImeAction.Next
                        ),
                        // Al iniciar sesion este es el ultimo campo, asi que la
                        // tecla "Listo" entra directamente sin buscar el boton.
                        keyboardActions = KeyboardActions(
                            onDone = { enviarFormulario() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            focusedTextColor = uiColors.textPrimary,
                            unfocusedTextColor = uiColors.textPrimary,
                            focusedContainerColor = uiColors.inputBackground,
                            unfocusedContainerColor = uiColors.inputBackground,
                            unfocusedBorderColor = TealLight
                        )
                    )

                    if (!isLogin) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; errorServidor = null },
                            label = { Text("Confirmar contraseña") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TealPrimary) },
                            visualTransformation = if (mostrarPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            singleLine = true,
                            isError = errorConfirmacion,
                            supportingText = if (errorConfirmacion) {
                                { Text("Las contraseñas no coinciden") }
                            } else null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            // Ultimo campo del registro: "Listo" crea la cuenta.
                            keyboardActions = KeyboardActions(
                                onDone = { enviarFormulario() }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                focusedTextColor = uiColors.textPrimary,
                                unfocusedTextColor = uiColors.textPrimary,
                                focusedContainerColor = uiColors.inputBackground,
                                unfocusedContainerColor = uiColors.inputBackground,
                                unfocusedBorderColor = TealLight
                            )
                        )
                    }

                    // Los errores del servidor se muestran fijos en pantalla y no
                    // como aviso pasajero, para que el usuario pueda leerlos con calma.
                    errorServidor?.let { mensaje ->
                        Text(
                            text = mensaje,
                            color = uiColors.dangerButton,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = { enviarFormulario() },
                        enabled = formularioListo && !loading,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = TealLight,
                            disabledContentColor = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Text(
                            text = when {
                                loading && isLogin -> "Iniciando sesión..."
                                loading -> "Creando cuenta..."
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
                Text(text = if (isLogin) "¿No tienes cuenta? " else "¿Ya tienes cuenta? ", color = uiColors.textSecondary)
                Text(
                    text = if (isLogin) "Regístrate" else "Iniciar sesión",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSwitch(null) }
                )
            }

            // Deja aire al final para que el ultimo elemento no quede pegado al
            // borde inferior cuando el formulario se desplaza.
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}