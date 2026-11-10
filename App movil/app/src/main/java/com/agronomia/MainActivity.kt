package com.agronomia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agronomia.data.remote.NetworkModule
import com.agronomia.ui.navigation.AppNavigation
import com.agronomia.ui.theme.AgronomIaTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NetworkModule.setUserToken(
            getSharedPreferences("agronom-ia-auth", MODE_PRIVATE).getString("token", null)
        )
        enableEdgeToEdge()
        setContent {
            AgronomIaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showStartup by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(900)
                        showStartup = false
                    }
                    if (showStartup) {
                        StartupScreen()
                    } else {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StartupScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(92.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                    strokeWidth = 3.dp
                )
                Text("🌱", style = MaterialTheme.typography.headlineLarge)
            }
            Text("Agronom-IA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Preparando tu espacio de cultivo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp).size(22.dp), strokeWidth = 2.dp)
        }
    }
}
