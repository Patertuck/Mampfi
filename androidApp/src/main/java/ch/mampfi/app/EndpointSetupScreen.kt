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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun EndpointSetupScreen(store: EndpointSettingsStore, configured: Boolean = false, saved: () -> Unit = {}) {
    val settings by store.settings.collectAsState(initial = null)
    var lanUrl by remember { mutableStateOf("") }
    var tailscaleUrl by remember { mutableStateOf("") }
    var firstRaterName by remember { mutableStateOf(DEFAULT_FIRST_RATER_NAME) }
    var secondRaterName by remember { mutableStateOf(DEFAULT_SECOND_RATER_NAME) }
    var error by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(settings) {
        if (!initialized && settings != null) {
            lanUrl = settings!!.lanBaseUrl
            tailscaleUrl = settings!!.tailscaleBaseUrl
            firstRaterName = settings!!.firstRaterName
            secondRaterName = settings!!.secondRaterName
            initialized = true
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (configured) "Einstellungen" else "Mampfi einrichten", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (configured) "Ändere die Verbindung und die Namen für eure Bewertungen."
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
        Spacer(Modifier.height(24.dp))
        Text("Personen für Bewertungen", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = firstRaterName,
            onValueChange = { firstRaterName = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Person 1") },
            singleLine = true,
            isError = error != null && firstRaterName.isBlank(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = secondRaterName,
            onValueChange = { secondRaterName = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Person 2") },
            singleLine = true,
            isError = error != null && secondRaterName.isBlank(),
        )
        error?.let { Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    error = runCatching { store.save(lanUrl, tailscaleUrl, firstRaterName, secondRaterName) }.exceptionOrNull()?.message
                    if (error == null) saved()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Speichern und verbinden") }
        if (configured) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
