package ch.mampfi.app

import android.app.Application
import android.app.DownloadManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Checking : UpdateState
    data class UpToDate(val checked: Boolean = true) : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Int?) : UpdateState
    data class Ready(val update: AvailableUpdate) : UpdateState
    data class Failed(val update: AvailableUpdate?, val message: String) : UpdateState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updater = AppUpdater(application)
    private val preferences = application.getSharedPreferences("app_update", 0)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.UpToDate(checked = false))
    val state = _state.asStateFlow()
    private var monitorJob: Job? = null
    private var pendingInstall = false

    init { checkForUpdate() }

    fun checkForUpdate() {
        if (BuildConfig.UPDATE_METADATA_URL.isBlank()) {
            _state.value = UpdateState.UpToDate()
            return
        }
        viewModelScope.launch {
            _state.value = UpdateState.Checking
            updater.checkForUpdate(BuildConfig.UPDATE_METADATA_URL).fold(
                onSuccess = { update -> if (update == null) { clearDownload(); _state.value = UpdateState.UpToDate() } else restoreOrOffer(update) },
                onFailure = {
                    val persisted = persistedUpdate()
                    if (persisted == null) _state.value = UpdateState.Failed(null, "Update-Prüfung fehlgeschlagen")
                    else restoreOrOffer(persisted)
                },
            )
        }
    }

    fun download() {
        val update = when (val current = _state.value) {
            is UpdateState.Available -> current.update
            is UpdateState.Failed -> current.update
            else -> null
        } ?: return
        runCatching {
            val filename = filename(update)
            val id = updater.enqueue(update, filename)
            preferences.edit().putLong(DOWNLOAD_ID, id).putString(DOWNLOAD_VERSION, update.version)
                .putString(DOWNLOAD_URL, update.apkUrl).putString(DOWNLOAD_CHANGELOG, update.changelog)
                .putString(DOWNLOAD_FILENAME, filename).apply()
            _state.value = UpdateState.Downloading(update, null)
            monitor(id, update, filename)
        }.onFailure { _state.value = UpdateState.Failed(update, "Download konnte nicht gestartet werden") }
    }

    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        val filename = preferences.getString(DOWNLOAD_FILENAME, null) ?: filename(ready.update)
        runCatching { updater.install(filename) }
            .onSuccess { launched -> pendingInstall = !launched }
            .onFailure { _state.value = UpdateState.Failed(ready.update, "Installation konnte nicht geöffnet werden") }
    }

    fun onResume() {
        if (pendingInstall) {
            pendingInstall = false
            if (updater.canInstallPackages()) install()
        } else when (val current = _state.value) {
            is UpdateState.Downloading -> reconcile(current.update)
            is UpdateState.Ready -> reconcile(current.update)
            else -> Unit
        }
    }

    private fun restoreOrOffer(update: AvailableUpdate) {
        if (preferences.getString(DOWNLOAD_VERSION, null) != update.version) {
            clearDownload()
            _state.value = UpdateState.Available(update)
        } else reconcile(update)
    }

    private fun reconcile(update: AvailableUpdate) {
        val id = preferences.getLong(DOWNLOAD_ID, -1L)
        val filename = preferences.getString(DOWNLOAD_FILENAME, null) ?: filename(update)
        if (id < 0) {
            _state.value = UpdateState.Available(update)
            return
        }
        val snapshot = updater.query(id)
        when (snapshot?.status) {
            null -> _state.value = if (updater.apkExists(filename)) UpdateState.Ready(update) else UpdateState.Available(update)
            DownloadManager.STATUS_SUCCESSFUL -> _state.value = if (updater.apkExists(filename)) UpdateState.Ready(update) else UpdateState.Failed(update, "APK wurde nicht gefunden")
            DownloadManager.STATUS_FAILED -> _state.value = UpdateState.Failed(update, "Download fehlgeschlagen")
            else -> {
                _state.value = UpdateState.Downloading(update, snapshot.progress)
                monitor(id, update, filename)
            }
        }
    }

    private fun monitor(id: Long, update: AvailableUpdate, filename: String) {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val snapshot = updater.query(id) ?: break
                when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _state.value = if (updater.apkExists(filename)) UpdateState.Ready(update) else UpdateState.Failed(update, "APK wurde nicht gefunden")
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        _state.value = UpdateState.Failed(update, "Download fehlgeschlagen")
                        break
                    }
                    else -> _state.value = UpdateState.Downloading(update, snapshot.progress)
                }
            }
        }
    }

    private fun clearDownload() {
        monitorJob?.cancel()
        preferences.edit().clear().apply()
    }

    private fun persistedUpdate(): AvailableUpdate? {
        val version = preferences.getString(DOWNLOAD_VERSION, null) ?: return null
        val url = preferences.getString(DOWNLOAD_URL, null) ?: return null
        return AvailableUpdate(version, url, preferences.getString(DOWNLOAD_CHANGELOG, "").orEmpty())
    }

    private fun filename(update: AvailableUpdate) = "mampfi-${update.version.filter { it.isLetterOrDigit() || it == '.' }}"

    private companion object {
        const val DOWNLOAD_ID = "download_id"
        const val DOWNLOAD_VERSION = "download_version"
        const val DOWNLOAD_URL = "download_url"
        const val DOWNLOAD_CHANGELOG = "download_changelog"
        const val DOWNLOAD_FILENAME = "download_filename"
    }
}
