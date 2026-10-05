package ch.mampfi.app

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

const val DEFAULT_FIRST_RATER_NAME = "Person 1"
const val DEFAULT_SECOND_RATER_NAME = "Person 2"

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class EndpointSettings(
    val lanBaseUrl: String = "",
    val tailscaleBaseUrl: String = "",
    val firstRaterName: String = DEFAULT_FIRST_RATER_NAME,
    val secondRaterName: String = DEFAULT_SECOND_RATER_NAME,
    val themeMode: ThemeMode = ThemeMode.DARK,
) {
    val isConfigured: Boolean get() = lanBaseUrl.isNotBlank()
}

private val Context.endpointDataStore by preferencesDataStore(name = "endpoint_settings")

class EndpointSettingsStore(private val context: Context) {
    val settings: Flow<EndpointSettings> = context.endpointDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::settingsFrom)

    suspend fun save(lanBaseUrl: String, tailscaleBaseUrl: String, firstRaterName: String, secondRaterName: String) {
        val normalizedFirstRaterName = normalizeRaterName(firstRaterName)
        val normalizedSecondRaterName = normalizeRaterName(secondRaterName)
        context.endpointDataStore.edit { preferences ->
            preferences[LAN_BASE_URL] = normalizeEndpoint(lanBaseUrl)
            preferences[TAILSCALE_BASE_URL] = tailscaleBaseUrl.trim().takeIf { it.isNotEmpty() }?.let(::normalizeEndpoint).orEmpty()
            preferences[FIRST_RATER_NAME] = normalizedFirstRaterName
            preferences[SECOND_RATER_NAME] = normalizedSecondRaterName
        }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.endpointDataStore.edit { preferences -> preferences[THEME_MODE] = themeMode.name }
    }

    companion object {
        private val LAN_BASE_URL = stringPreferencesKey("lan_base_url")
        private val TAILSCALE_BASE_URL = stringPreferencesKey("tailscale_base_url")
        private val FIRST_RATER_NAME = stringPreferencesKey("first_rater_name")
        private val SECOND_RATER_NAME = stringPreferencesKey("second_rater_name")
        private val THEME_MODE = stringPreferencesKey("theme_mode")

        fun normalizeRaterName(value: String): String = value.trim().also {
            require(it.isNotEmpty()) { "Beide Namen müssen ausgefüllt sein" }
        }

        fun normalizeEndpoint(value: String): String {
            val uri = runCatching { URI(value.trim()) }.getOrNull()
                ?: throw IllegalArgumentException("Ungültige Server-Adresse")
            require(uri.scheme == "http" || uri.scheme == "https") { "Die Adresse muss mit http:// oder https:// beginnen" }
            require(!uri.host.isNullOrBlank()) { "Die Adresse benötigt einen Hostnamen oder eine IP-Adresse" }
            return uri.toString().trimEnd('/') + "/"
        }

        private fun settingsFrom(preferences: Preferences) = EndpointSettings(
            lanBaseUrl = preferences[LAN_BASE_URL].orEmpty(),
            tailscaleBaseUrl = preferences[TAILSCALE_BASE_URL].orEmpty(),
            firstRaterName = preferences[FIRST_RATER_NAME] ?: DEFAULT_FIRST_RATER_NAME,
            secondRaterName = preferences[SECOND_RATER_NAME] ?: DEFAULT_SECOND_RATER_NAME,
            themeMode = preferences[THEME_MODE]?.let { value -> runCatching { ThemeMode.valueOf(value) }.getOrNull() } ?: ThemeMode.DARK,
        )
    }
}
