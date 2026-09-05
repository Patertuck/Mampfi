package ch.mampfi.app

object ApiConfig {
    fun candidates(settings: EndpointSettings): List<String> = listOf(settings.lanBaseUrl, settings.tailscaleBaseUrl)
        .filter { it.isNotBlank() }
        .distinct()
}
