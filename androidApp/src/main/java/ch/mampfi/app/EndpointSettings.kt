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
    val serverBaseUrl: String = "",
    val accessClientId: String = "",
    val accessClientSecret: String = "",
    val firstRaterName: String = DEFAULT_FIRST_RATER_NAME,
    val secondRaterName: String = DEFAULT_SECOND_RATER_NAME,
    val themeMode: ThemeMode = ThemeMode.DARK,
) {
    val isConfigured: Boolean
        get() = serverBaseUrl.isNotBlank() && accessClientId.isNotBlank() && accessClientSecret.isNotBlank()
}

private val Context.endpointDataStore by preferencesDataStore(name = "endpoint_settings")

class EndpointSettingsStore(private val context: Context) {
    private val secretStore = EncryptedSecretStore()

    val settings: Flow<EndpointSettings> = context.endpointDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::settingsFrom)

    suspend fun save(
        serverBaseUrl: String,
        accessClientId: String,
        accessClientSecret: String,
        firstRaterName: String,
        secondRaterName: String,
    ) {
        val normalizedUrl = normalizeEndpoint(serverBaseUrl)
        val normalizedClientId = accessClientId.trim().also {
            require(it.isNotEmpty()) { "Die Cloudflare Client-ID muss ausgefüllt sein" }
        }
        val normalizedSecret = accessClientSecret.trim().also {
            require(it.isNotEmpty()) { "Das Cloudflare Client-Secret muss ausgefüllt sein" }
        }
        val normalizedFirstRaterName = normalizeRaterName(firstRaterName)
        val normalizedSecondRaterName = normalizeRaterName(secondRaterName)
        context.endpointDataStore.edit { preferences ->
            preferences[SERVER_BASE_URL] = normalizedUrl
            preferences[ACCESS_CLIENT_ID] = normalizedClientId
            preferences[ACCESS_CLIENT_SECRET] = secretStore.encrypt(normalizedSecret)
            preferences[FIRST_RATER_NAME] = normalizedFirstRaterName
            preferences[SECOND_RATER_NAME] = normalizedSecondRaterName
        }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.endpointDataStore.edit { preferences -> preferences[THEME_MODE] = themeMode.name }
    }

    private fun settingsFrom(preferences: Preferences): EndpointSettings {
        val migratedUrl = preferences[SERVER_BASE_URL]
            ?: preferences[LEGACY_TAILSCALE_BASE_URL]?.takeIf(String::isNotBlank)
            ?: preferences[LEGACY_LAN_BASE_URL].orEmpty()
        val secret = preferences[ACCESS_CLIENT_SECRET]
            ?.let { encrypted -> runCatching { secretStore.decrypt(encrypted) }.getOrDefault("") }
            .orEmpty()
        return EndpointSettings(
            serverBaseUrl = migratedUrl,
            accessClientId = preferences[ACCESS_CLIENT_ID].orEmpty(),
            accessClientSecret = secret,
            firstRaterName = preferences[FIRST_RATER_NAME] ?: DEFAULT_FIRST_RATER_NAME,
            secondRaterName = preferences[SECOND_RATER_NAME] ?: DEFAULT_SECOND_RATER_NAME,
            themeMode = preferences[THEME_MODE]?.let { value -> runCatching { ThemeMode.valueOf(value) }.getOrNull() } ?: ThemeMode.DARK,
        )
    }

    companion object {
        private val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
        private val ACCESS_CLIENT_ID = stringPreferencesKey("cloudflare_access_client_id")
        private val ACCESS_CLIENT_SECRET = stringPreferencesKey("cloudflare_access_client_secret_encrypted")
        private val LEGACY_LAN_BASE_URL = stringPreferencesKey("lan_base_url")
        private val LEGACY_TAILSCALE_BASE_URL = stringPreferencesKey("tailscale_base_url")
        private val FIRST_RATER_NAME = stringPreferencesKey("first_rater_name")
        private val SECOND_RATER_NAME = stringPreferencesKey("second_rater_name")
        private val THEME_MODE = stringPreferencesKey("theme_mode")

        fun normalizeRaterName(value: String): String = value.trim().also {
            require(it.isNotEmpty()) { "Beide Namen müssen ausgefüllt sein" }
        }

        fun normalizeEndpoint(value: String): String {
            val uri = runCatching { URI(value.trim()) }.getOrNull()
                ?: throw IllegalArgumentException("Ungültige Server-Adresse")
            require(uri.scheme.equals("https", ignoreCase = true)) { "Die Adresse muss mit https:// beginnen" }
            require(!uri.host.isNullOrBlank()) { "Die Adresse benötigt einen Hostnamen" }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "Die Adresse darf keine Zugangsdaten, Abfrage oder Fragment enthalten"
            }
            require(uri.path.isNullOrBlank() || uri.path == "/") { "Die Adresse darf keinen Pfad enthalten" }
            return URI("https", null, uri.host.lowercase(), uri.port, "/", null, null).toString()
        }
    }
}
