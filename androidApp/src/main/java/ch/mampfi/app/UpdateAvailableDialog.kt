package ch.mampfi.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun UpdateAvailableDialog(version: String, progress: Int?, dismiss: () -> Unit, download: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Update verfügbar") },
        text = {
            Column {
                Text("Version $version ist verfügbar.")
                progress?.let {
                    LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Wird heruntergeladen: $it %")
                }
            }
        },
        confirmButton = {
            if (progress == null) TextButton(onClick = download) { Text("Herunterladen") }
        },
        dismissButton = {
            if (progress == null) TextButton(onClick = dismiss) { Text("Später") }
        },
    )
}
