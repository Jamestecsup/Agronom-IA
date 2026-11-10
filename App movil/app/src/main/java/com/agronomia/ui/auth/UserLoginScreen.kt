package com.agronomia.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.agronomia.data.remote.DjangoLoginRequest
import com.agronomia.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@Composable
fun UserLoginScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Agronom-IA", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Inicia sesión para guardar tus identificaciones y conversaciones en tu cuenta.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; error = "" },
                label = { Text("Usuario") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = "" },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (error.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank() || busy) return@Button
                    busy = true
                    error = ""
                    scope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                NetworkModule.djangoApiService.login(
                                    DjangoLoginRequest(username.trim(), password)
                                )
                            }
                            if (!response.success || response.token.isBlank()) {
                                error = response.error ?: "No se pudo iniciar sesión."
                            } else {
                                context.getSharedPreferences("agronom-ia-auth", 0)
                                    .edit().putString("token", response.token).apply()
                                NetworkModule.setUserToken(response.token)
                                onAuthenticated()
                            }
                        } catch (problem: HttpException) {
                            error = if (problem.code() == 401) {
                                "El usuario o la contraseña no son correctos."
                            } else {
                                "No pudimos iniciar sesión. Inténtalo de nuevo."
                            }
                        } catch (_: Exception) {
                            error = "No pudimos conectar. Revisa tu conexión e inténtalo de nuevo."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator()
                else Text("Iniciar sesión")
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Cuenta demo: agricultor / Campo12345. Puedes crear otras cuentas desde la web.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
