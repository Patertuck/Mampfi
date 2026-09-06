package ch.mampfi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import ch.mampfi.app.data.MealApi
import ch.mampfi.app.data.MealDatabase
import ch.mampfi.app.data.MealRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

class MainActivity : ComponentActivity() {
    private val db by lazy {
        Room.databaseBuilder(applicationContext, MealDatabase::class.java, "mampfi.db").build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val endpointStore = remember { EndpointSettingsStore(applicationContext) }
            val settings by endpointStore.settings.collectAsState(initial = null)
            var baseUrl by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(settings) {
                baseUrl = if (settings?.isConfigured == true) {
                    ServerEndpointResolver.resolve(settings!!)
                } else {
                    null
                }
            }

            when {
                settings == null -> {
                    MampfiTheme { StartupLoadingScreen("Mampfi wird geladen …") }
                }
                !settings!!.isConfigured -> {
                    MampfiTheme { EndpointSetupScreen(endpointStore) }
                }
                baseUrl == null -> {
                    MampfiTheme { StartupLoadingScreen("Server wird verbunden …") }
                }
                else -> {
                    val api = remember(baseUrl) {
                        Retrofit.Builder()
                            .baseUrl(baseUrl!!)
                            .addConverterFactory(
                                Json { ignoreUnknownKeys = true }
                                    .asConverterFactory("application/json".toMediaType()),
                            )
                            .build()
                            .create(MealApi::class.java)
                    }
                    val vm: MealViewModel = viewModel(
                        key = baseUrl,
                        factory = MealViewModelFactory(MealRepository(api, db.meals(), baseUrl!!)),
                    )
                    MampfiApp(
                        vm,
                        connectedViaTailscale = baseUrl == settings!!.tailscaleBaseUrl,
                        endpointStore = endpointStore,
                    )
                }
            }
        }
    }
}

@Composable
private fun StartupLoadingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.mampfi_background)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.mampfi_splash_mascot),
                contentDescription = null,
                modifier = Modifier.size(220.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = colorResource(R.color.mampfi_loading_indicator))
            Spacer(Modifier.height(12.dp))
            Text(message, color = colorResource(R.color.mampfi_loading_text))
        }
    }
}
