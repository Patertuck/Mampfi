package ch.mampfi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class ConnectionCheckState { IDLE, TESTING, CONNECTED, UNAVAILABLE, INVALID }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointSetupScreen(
    store: EndpointSettingsStore,
    configured: Boolean = false,
    updateState: UpdateState? = null,
    checkForUpdate: () -> Unit = {},
    downloadUpdate: () -> Unit = {},
    installUpdate: () -> Unit = {},
    saved: () -> Unit = {},
) {
    val settings by store.settings.collectAsState(initial = null)
    var lanUrl by remember { mutableStateOf("") }
    var tailscaleUrl by remember { mutableStateOf("") }
    var firstRaterName by remember { mutableStateOf(DEFAULT_FIRST_RATER_NAME) }
    var secondRaterName by remember { mutableStateOf(DEFAULT_SECOND_RATER_NAME) }
    var error by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var lanCheck by remember { mutableStateOf(ConnectionCheckState.IDLE) }
    var tailscaleCheck by remember { mutableStateOf(ConnectionCheckState.IDLE) }
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

    fun resetConnectionChecks() {
        lanCheck = ConnectionCheckState.IDLE
        tailscaleCheck = ConnectionCheckState.IDLE
    }
    suspend fun checkAddress(value: String): ConnectionCheckState {
        val normalized = runCatching { EndpointSettingsStore.normalizeEndpoint(value) }.getOrNull()
            ?: return ConnectionCheckState.INVALID
        return if (ServerEndpointResolver.check(normalized)) ConnectionCheckState.CONNECTED else ConnectionCheckState.UNAVAILABLE
    }
    fun testConnections() {
        scope.launch {
            lanCheck = ConnectionCheckState.TESTING
            tailscaleCheck = if (tailscaleUrl.isBlank()) ConnectionCheckState.IDLE else ConnectionCheckState.TESTING
            coroutineScope {
                val lanResult = async { checkAddress(lanUrl) }
                val tailscaleResult = tailscaleUrl.takeIf { it.isNotBlank() }?.let { value -> async { checkAddress(value) } }
                lanCheck = lanResult.await()
                tailscaleCheck = tailscaleResult?.await() ?: ConnectionCheckState.IDLE
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (configured) "Einstellungen" else "Mampfi einrichten", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (configured) "Passt Mampfi an eure Nutzung an."
            else "Gebt die Server-Adressen und eure Namen für dieses Telefon ein.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (configured) FormSection("Darstellung") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Hell", ThemeMode.DARK to "Dunkel").forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings?.themeMode == mode,
                        onClick = { scope.launch { store.setThemeMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                        label = { Text(label) },
                    )
                }
            }
        }

        FormSection("Personen") {
            Text("Diese Namen erscheinen bei Bewertungen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RaterNameField(firstRaterName, { firstRaterName = it; error = null }, "Person 1", error != null && firstRaterName.isBlank())
            RaterNameField(secondRaterName, { secondRaterName = it; error = null }, "Person 2", error != null && secondRaterName.isBlank())
        }

        FormSection("Verbindung") {
            OutlinedTextField(
                value = lanUrl,
                onValueChange = { lanUrl = it; error = null; resetConnectionChecks() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("LAN-Adresse") },
                placeholder = { Text("http://192.168.1.50:8080") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                isError = error != null || lanCheck == ConnectionCheckState.INVALID,
                supportingText = { ConnectionCheckText(lanCheck) },
            )
            OutlinedTextField(
                value = tailscaleUrl,
                onValueChange = { tailscaleUrl = it; error = null; resetConnectionChecks() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tailscale-Adresse (optional)") },
                placeholder = { Text("http://mampfi.tailnet.ts.net:8080") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                isError = tailscaleCheck == ConnectionCheckState.INVALID,
                supportingText = { ConnectionCheckText(tailscaleCheck) },
            )
            OutlinedButton(
                onClick = ::testConnections,
                enabled = lanCheck != ConnectionCheckState.TESTING && tailscaleCheck != ConnectionCheckState.TESTING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (lanCheck == ConnectionCheckState.TESTING || tailscaleCheck == ConnectionCheckState.TESTING) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Verbindung testen")
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    scope.launch {
                        error = runCatching { store.save(lanUrl, tailscaleUrl, firstRaterName, secondRaterName) }.exceptionOrNull()?.message
                        if (error == null) saved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (configured) "Verbindung und Namen speichern" else "Speichern und verbinden") }
        }

        if (configured) FormSection("App-Informationen") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Aktuelle Version", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(BuildConfig.VERSION_NAME, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            when (val state = updateState) {
                UpdateState.Checking -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Updates werden geprüft …", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is UpdateState.Available -> {
                    Text("Version ${state.update.version} ist verfügbar.", fontWeight = FontWeight.Medium)
                    state.update.changelog.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Button(downloadUpdate, Modifier.fillMaxWidth()) { Text("Herunterladen") }
                }
                is UpdateState.Downloading -> {
                    if (state.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(state.progress?.let { "Wird heruntergeladen: $it %" } ?: "Download wird vorbereitet …", style = MaterialTheme.typography.bodySmall)
                }
                is UpdateState.Ready -> Button(installUpdate, Modifier.fillMaxWidth()) { Text("Version ${state.update.version} installieren") }
                is UpdateState.Failed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = if (state.update == null) checkForUpdate else downloadUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Erneut versuchen") }
                }
                is UpdateState.UpToDate -> if (state.checked) Text("Mampfi ist aktuell.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                null -> Unit
            }
            if (updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading) {
                TextButton(checkForUpdate, Modifier.fillMaxWidth()) { Text("Nach Updates suchen") }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RaterNameField(value: String, update: (String) -> Unit, label: String, isError: Boolean) = OutlinedTextField(
    value = value,
    onValueChange = update,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(label) },
    singleLine = true,
    isError = isError,
)

@Composable
private fun ConnectionCheckText(state: ConnectionCheckState) {
    val message = when (state) {
        ConnectionCheckState.IDLE -> return
        ConnectionCheckState.TESTING -> "Verbindung wird geprüft …"
        ConnectionCheckState.CONNECTED -> "Verbindung erfolgreich"
        ConnectionCheckState.UNAVAILABLE -> "Server nicht erreichbar"
        ConnectionCheckState.INVALID -> "Ungültige Server-Adresse"
    }
    val color = when (state) {
        ConnectionCheckState.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionCheckState.UNAVAILABLE, ConnectionCheckState.INVALID -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            ConnectionCheckState.CONNECTED -> Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = color)
            ConnectionCheckState.UNAVAILABLE, ConnectionCheckState.INVALID -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = color)
            else -> Unit
        }
        if (state in setOf(ConnectionCheckState.CONNECTED, ConnectionCheckState.UNAVAILABLE, ConnectionCheckState.INVALID)) Spacer(Modifier.width(4.dp))
        Text(message, color = color)
    }
}
