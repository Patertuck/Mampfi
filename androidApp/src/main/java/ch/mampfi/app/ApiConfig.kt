package ch.mampfi.app

object ApiConfig {
    val lanBaseUrl: String = BuildConfig.MAMPFI_LAN_BASE_URL
    val tailscaleBaseUrl: String = BuildConfig.MAMPFI_TAILSCALE_BASE_URL
    val candidates: List<String> = listOf(lanBaseUrl, tailscaleBaseUrl).filter { it.isNotBlank() }.distinct()
}
