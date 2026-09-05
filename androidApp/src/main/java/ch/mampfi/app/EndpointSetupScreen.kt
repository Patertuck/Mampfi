package ch.mampfi.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun EndpointSetupScreen(store: EndpointSettingsStore, configured: Boolean = false, saved: () -> Unit = {}) {
    val settings by store.settings.collectAsState(initial = null)
    var lanUrl by remember { mutableStateOf("") }
    var tailscaleUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(settings) {
        if (!initialized && settings != null) {
            lanUrl = settings!!.lanBaseUrl
            tailscaleUrl = settings!!.tailscaleBaseUrl
            initialized = true
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (configured) "Server-Verbindung" else "Mampfi einrichten", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (configured) "Ändere die Adressen, über die sich diese App mit deinem Server verbindet."
            else "Gib die Server-Adressen für dieses Telefon ein. Sie werden nur auf diesem Gerät gespeichert.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = lanUrl,
            onValueChange = { lanUrl = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("LAN-Adresse") },
            placeholder = { Text("http://192.168.1.50:8080") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            isError = error != null,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = tailscaleUrl,
            onValueChange = { tailscaleUrl = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tailscale-Adresse (optional)") },
            placeholder = { Text("http://mampfi.tailnet.ts.net:8080") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
        )
        error?.let { Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    error = runCatching { store.save(lanUrl, tailscaleUrl) }.exceptionOrNull()?.message
                    if (error == null) saved()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Speichern und verbinden") }
    }
}
