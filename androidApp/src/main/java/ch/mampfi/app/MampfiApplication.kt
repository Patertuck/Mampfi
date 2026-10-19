package ch.mampfi.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.util.concurrent.atomic.AtomicReference

class MampfiApplication : Application(), SingletonImageLoader.Factory {
    private val endpointSettings = AtomicReference<EndpointSettings?>(null)

    fun updateEndpointSettings(settings: EndpointSettings?) {
        endpointSettings.set(settings?.takeIf { it.isConfigured })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = authenticatedHttpClient(endpointSettings::get)))
        }
        .build()
}
