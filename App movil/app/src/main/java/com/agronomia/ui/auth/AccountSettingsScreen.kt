package com.agronomia.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agronomia.data.remote.NetworkModule

@Composable
fun AccountSettingsScreen() {
    val activity = LocalContext.current as? Activity
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cuenta", style = MaterialTheme.typography.headlineSmall)
        Text("Tus nuevas identificaciones y conversaciones se guardan en la cuenta activa.")
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                activity?.getSharedPreferences("agronom-ia-auth", 0)
                    ?.edit()?.remove("token")?.apply()
                NetworkModule.setUserToken(null)
                activity?.recreate()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión")
        }
    }
}
